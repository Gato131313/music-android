package io.github.gato131313.music.lyrics

import io.github.gato131313.music.AppGraph
import io.github.gato131313.music.BuildConfig
import io.github.gato131313.music.data.db.LyricsCache
import io.github.gato131313.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

data class LyricLine(val timeMs: Long, val text: String, val translation: String? = null)

data class LyricsData(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val plain: String?,
    /** La traducción mejorará más tarde (hoy no hubo cupo). */
    val better: Boolean = false,
)

/** Analizador LRC: varias marcas por línea, [offset:±ms], marcas por palabra <mm:ss.xx> (se quitan). */
object Lrc {
    private val STAMP = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val WORD = Regex("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>")
    private val OFFSET = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val META = Regex("^\\[(ar|ti|al|by|length|re|ve|au|#):.*]$", RegexOption.IGNORE_CASE)

    fun isSynced(raw: String) = STAMP.containsMatchIn(raw)

    fun parse(raw: String): List<LyricLine> {
        val offset = OFFSET.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LyricLine>()
        for (line in raw.lines()) {
            val l = line.trim()
            if (l.isEmpty() || META.matches(l) || OFFSET.matches(l)) continue
            val stamps = STAMP.findAll(l).toList()
            if (stamps.isEmpty()) continue
            val text = WORD.replace(STAMP.replace(l, ""), "").trim()
            for (m in stamps) {
                val (mm, ss, frac) = m.destructured
                val f = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                val t = mm.toLong() * 60_000 + ss.toLong() * 1000 + f - offset
                out += LyricLine(t.coerceAtLeast(0), text)
            }
        }
        out.sortBy { it.timeMs }
        // Huecos largos sin letra: "♪"
        val withGaps = ArrayList<LyricLine>(out.size + 8)
        for (i in out.indices) {
            val cur = out[i]
            if (cur.text.isEmpty()) { withGaps += cur.copy(text = "♪"); continue }
            withGaps += cur
        }
        return withGaps
    }

    /** Índice de la línea que suena en [posMs] (búsqueda binaria). */
    fun indexAt(lines: List<LyricLine>, posMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

@Serializable
private data class ApiResponse(
    val lyrics: String? = null,
    val synced: Boolean = false,
    val translation: Map<String, String>? = null,
    val translatedBy: String? = null,
    val cached: Boolean = false,
    val better: Boolean = false,
    val unavailable: Boolean = false,
)

/** Letras: primero la copia guardada en el teléfono; si no, el endpoint público de MUSIC (sin cuenta). */
class LyricsRepository(private val g: AppGraph) {
    private val http = OkHttpClient.Builder().callTimeout(65, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun deviceId(): String {
        val cur = g.settings.value.deviceId
        if (cur.isNotBlank()) return cur
        val id = UUID.randomUUID().toString().replace("-", "")
        g.settings.update { it.copy(deviceId = id) }
        return id
    }

    suspend fun load(t: Track, force: Boolean = false): Result<LyricsData?> = withContext(Dispatchers.IO) {
        val translate = g.settings.value.translateLyrics
        val cached = g.db.lyrics().get(t.key)
        val week = 7L * 86_400_000
        val fresh = cached != null && (cached.lyrics != null || System.currentTimeMillis() - cached.at < week)
        val needsBetter = cached?.translation?.startsWith("~") == true && System.currentTimeMillis() - cached.at > 6 * 3_600_000L
        if (!force && fresh && !(translate && cached!!.lyrics != null && (cached.translation == null || needsBetter))) {
            return@withContext Result.success(toData(cached!!))
        }
        runCatching {
            val url = BuildConfig.LYRICS_API.toHttpUrl().newBuilder()
                .addQueryParameter("title", t.title)
                .addQueryParameter("artist", t.artist ?: "")
                .addQueryParameter("album", t.album ?: "")
                .addQueryParameter("duration", (t.durationMs / 1000).toString())
                .addQueryParameter("device", deviceId())
                .addQueryParameter("translate", if (translate) "1" else "0")
                .build()
            http.newCall(Request.Builder().url(url).header("User-Agent", "MUSIC-Android/${BuildConfig.VERSION_NAME}").build()).execute().use { res ->
                if (res.code == 429) throw IllegalStateException("Límite de hoy alcanzado; vuelve a intentar mañana")
                if (!res.isSuccessful) throw IllegalStateException("Servicio de letras no disponible (${res.code})")
                val body = json.decodeFromString(ApiResponse.serializer(), res.body!!.string())
                if (body.unavailable) throw IllegalStateException("El servicio de letras no respondió")
                val tr = body.translation?.let { m -> (if (body.better) "~" else "") + json.encodeToString(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<String>()), m) }
                val entry = LyricsCache(t.key, body.lyrics, body.synced, tr ?: cached?.translation, offsetMs = cached?.offsetMs ?: 0)
                g.db.lyrics().put(entry)
                toData(entry)
            }
        }.recoverCatching { e -> if (cached != null) toData(cached) else throw e }
    }

    private fun toData(c: LyricsCache): LyricsData? {
        val raw = c.lyrics ?: return null
        val tmap: Map<String, String> = c.translation?.removePrefix("~")?.let {
            runCatching { json.decodeFromString(kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<String>()), it) }.getOrNull()
        } ?: emptyMap()
        val better = c.translation?.startsWith("~") == true
        return if (Lrc.isSynced(raw)) {
            val lines = Lrc.parse(raw).map { it.copy(timeMs = it.timeMs + c.offsetMs, translation = tmap[it.text]?.takeIf { tr -> tr != it.text }) }
            LyricsData(lines, true, null, better)
        } else {
            LyricsData(raw.lines().map { LyricLine(-1, it.trim(), tmap[it.trim()]?.takeIf { tr -> tr != it.trim() }) }, false, raw, better)
        }
    }

    suspend fun setOffset(t: Track, offsetMs: Long) {
        val c = g.db.lyrics().get(t.key) ?: return
        g.db.lyrics().put(c.copy(offsetMs = offsetMs))
    }
}

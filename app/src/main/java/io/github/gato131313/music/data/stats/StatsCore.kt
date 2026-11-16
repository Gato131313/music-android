package io.github.gato131313.music.data.stats

import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.db.PlayEvent
import io.github.gato131313.music.data.db.PlayStat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TopSong(val key: String, val title: String, val artist: String?, val plays: Int, val ms: Long, val move: String)
data class TopArtist(val name: String, val plays: Int, val ms: Long, val move: String)
data class Badge(val name: String, val desc: String, val progress: Float)

data class StatsResult(
    val minutes: Long,
    val plays: Int,
    val prevMinutes: Long,
    val prevPlays: Int,
    val topSongs: List<TopSong>,
    val topArtists: List<TopArtist>,
    val hours: IntArray,
    val peakHour: Int,
    val obsession: Triple<String, LocalDate, Int>?,
    val longestSessionMin: Long,
    val discoveries: Int,
    val monthly: FloatArray,
    val badges: List<Badge>,
    val imported: Int,
)

/** Cálculo puro de estadísticas (como statsCore de MUSIC web). */
object StatsCore {
    private fun songId(e: PlayEvent) = NameCleaner.titleKey(e.title) + "|" + NameCleaner.artistKey(e.artist)

    fun compute(
        all: List<PlayEvent>,
        stats: Collection<PlayStat>,
        from: Long,
        to: Long,
        prevFrom: Long,
        includeImported: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        titleOf: (String) -> Pair<String, String?>? = { null },
    ): StatsResult {
        val cur = all.filter { it.at in from until to }
        val prev = all.filter { it.at in prevFrom until from }
        val imported = if (includeImported) stats.sumOf { it.importedPlays } else 0

        fun songs(evs: List<PlayEvent>): Map<String, Triple<PlayEvent, Int, Long>> =
            evs.groupBy { it.key }.mapValues { (_, l) -> Triple(l.last(), l.size, l.sumOf { it.ms }) }

        val curSongs = songs(cur).toMutableMap()
        if (includeImported) {
            for (s in stats) if (s.importedPlays > 0) {
                val existing = curSongs[s.key]
                val name = titleOf(s.key)
                val ev = existing?.first ?: PlayEvent(key = s.key, at = 0, ms = 0, title = name?.first ?: s.key.substringAfterLast('/'), artist = name?.second)
                curSongs[s.key] = Triple(ev, (existing?.second ?: 0) + s.importedPlays, existing?.third ?: 0)
            }
        }
        val prevRank = songs(prev).entries.sortedByDescending { it.value.second }.mapIndexed { i, e -> e.key to i }.toMap()
        val topSongs = curSongs.entries.sortedWith(compareByDescending<Map.Entry<String, Triple<PlayEvent, Int, Long>>> { it.value.second }.thenByDescending { it.value.third })
            .take(10).mapIndexed { i, e ->
                val p = prevRank[e.key]
                val move = when { prev.isEmpty() -> ""; p == null -> "nueva"; p > i -> "↑${p - i}"; p < i -> "↓${i - p}"; else -> "=" }
                TopSong(e.key, e.value.first.title, e.value.first.artist, e.value.second, e.value.third, move)
            }

        fun artists(m: Map<String, Triple<PlayEvent, Int, Long>>): Map<String, Pair<Int, Long>> {
            val out = HashMap<String, Pair<Int, Long>>()
            val names = HashMap<String, String>()
            for ((_, v) in m) {
                val a = v.first.artist ?: continue
                for (name in NameCleaner.allArtists(a)) {
                    val k = NameCleaner.key(name)
                    if (k.isEmpty()) continue
                    names.putIfAbsent(k, name)
                    val o = out[k] ?: (0 to 0L)
                    out[k] = (o.first + v.second) to (o.second + v.third)
                }
            }
            return out.mapKeys { names[it.key] ?: it.key }
        }
        val curArt = artists(curSongs)
        val prevArtRank = artists(songs(prev)).entries.sortedByDescending { it.value.first }.mapIndexed { i, e -> NameCleaner.key(e.key) to i }.toMap()
        val topArtists = curArt.entries.sortedByDescending { it.value.first }.take(10).mapIndexed { i, e ->
            val p = prevArtRank[NameCleaner.key(e.key)]
            val move = when { prev.isEmpty() -> ""; p == null -> "nuevo"; p > i -> "↑${p - i}"; p < i -> "↓${i - p}"; else -> "=" }
            TopArtist(e.key, e.value.first, e.value.second, move)
        }

        val hours = IntArray(24)
        for (e in cur) hours[Instant.ofEpochMilli(e.at).atZone(zone).hour] += (e.ms / 60_000).toInt().coerceAtLeast(1)
        val peak = hours.indices.maxBy { hours[it] }

        val obsession = cur.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() to songId(it) }
            .maxByOrNull { it.value.size }?.let { (k, v) -> Triple(v.first().title, k.first, v.size) }

        var longest = 0L
        var sessionStart = -1L
        var lastEnd = -1L
        for (e in cur.sortedBy { it.at }) {
            val start = e.at - 30_000
            if (sessionStart < 0 || start - lastEnd > 10 * 60_000) sessionStart = start
            lastEnd = maxOf(lastEnd, e.at + e.ms)
            longest = maxOf(longest, lastEnd - sessionStart)
        }

        val firstPlay = all.groupBy { it.key }.mapValues { (_, l) -> l.minOf { it.at } }
        val discoveries = firstPlay.count { it.value in from until to }

        val year = Instant.ofEpochMilli(to - 1).atZone(zone).year
        val monthly = FloatArray(12)
        for (e in all) {
            val z = Instant.ofEpochMilli(e.at).atZone(zone)
            if (z.year == year) monthly[z.monthValue - 1] += e.ms / 3_600_000f
        }

        val totalMs = all.sumOf { it.ms }
        val maxSong = (all.groupingBy { it.key }.eachCount().values.maxOrNull() ?: 0) + 0
        val night = all.count { Instant.ofEpochMilli(it.at).atZone(zone).hour in 0..4 }
        val early = all.count { Instant.ofEpochMilli(it.at).atZone(zone).hour in 5..8 }
        val dayMax = all.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() to it.key }.values.maxOfOrNull { it.size } ?: 0
        val artistMax = artists(songs(all)).values.maxOfOrNull { it.first } ?: 0
        val distinct = all.map { it.key }.distinct().size
        val n = all.size.coerceAtLeast(1)
        val badges = listOf(
            Badge("Fan número 1", "100 reproducciones de una canción", maxSong / 100f),
            Badge("10 horas", "10 horas escuchadas", totalMs / 36_000_000f),
            Badge("50 horas", "50 horas escuchadas", totalMs / 180_000_000f),
            Badge("Búho", "20 % de tu música entre las 0 y las 5", (night.toFloat() / n) / 0.2f),
            Badge("Madrugador", "20 % de tu música entre las 5 y las 9", (early.toFloat() / n) / 0.2f),
            Badge("Obsesión", "10 veces la misma canción en un día", dayMax / 10f),
            Badge("Maratón", "Una sesión de 3 horas", longest / 10_800_000f),
            Badge("Explorador", "10 canciones nuevas este periodo", discoveries / 10f),
            Badge("Fan de verdad", "50 reproducciones de un artista", artistMax / 50f),
            Badge("Coleccionista", "100 canciones distintas", distinct / 100f),
        ).map { it.copy(progress = it.progress.coerceIn(0f, 1f)) }

        return StatsResult(
            minutes = cur.sumOf { it.ms } / 60_000,
            plays = cur.size + imported,
            prevMinutes = prev.sumOf { it.ms } / 60_000,
            prevPlays = prev.size,
            topSongs = topSongs,
            topArtists = topArtists,
            hours = hours,
            peakHour = peak,
            obsession = obsession?.takeIf { it.third >= 2 },
            longestSessionMin = longest / 60_000,
            discoveries = discoveries,
            monthly = monthly,
            badges = badges,
            imported = imported,
        )
    }
}

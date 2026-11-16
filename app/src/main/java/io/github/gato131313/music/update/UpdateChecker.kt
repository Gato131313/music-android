package io.github.gato131313.music.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.gato131313.music.AppGraph
import io.github.gato131313.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class Release(val version: String, val apkUrl: String, val notes: String, val size: Long)

@Serializable private data class GhAsset(val name: String, @SerialName("browser_download_url") val url: String, val size: Long = 0)
@Serializable private data class GhRelease(@SerialName("tag_name") val tag: String, val body: String? = null, val assets: List<GhAsset> = emptyList(), val draft: Boolean = false, val prerelease: Boolean = false)

/** Busca versiones nuevas en GitHub Releases (repositorio público, sin claves). */
class UpdateChecker(private val g: AppGraph) {
    private val http = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress

    companion object {
        /** "1.10.0" > "1.9.2". */
        fun newer(a: String, b: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
            val pa = parts(a); val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }

    suspend fun check(force: Boolean = false): Result<Release?> = withContext(Dispatchers.IO) {
        val s = g.settings.value
        if (!force && (!s.checkUpdates || System.currentTimeMillis() - s.lastUpdateCheck < 12 * 3_600_000L)) return@withContext Result.success(_available.value)
        runCatching {
            val req = Request.Builder().url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json").header("User-Agent", "MUSIC-Android").build()
            val rel = http.newCall(req).execute().use { res ->
                if (res.code == 404) return@use null
                if (!res.isSuccessful) throw IllegalStateException("GitHub respondió ${res.code}")
                json.decodeFromString(GhRelease.serializer(), res.body!!.string())
            }
            g.settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            val apk = rel?.assets?.firstOrNull { it.name.endsWith(".apk") }
            val r = if (rel != null && apk != null && !rel.draft && newer(rel.tag, BuildConfig.VERSION_NAME)) Release(rel.tag.removePrefix("v"), apk.url, rel.body.orEmpty(), apk.size) else null
            _available.value = r
            r
        }
    }

    fun dismiss() { _available.value = null }

    suspend fun downloadAndInstall(ctx: Context, r: Release): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val file = File(dir, "MUSIC-${r.version}.apk")
            _progress.value = 0f
            http.newCall(Request.Builder().url(r.apkUrl).header("User-Agent", "MUSIC-Android").build()).execute().use { res ->
                if (!res.isSuccessful) throw IllegalStateException("Descarga falló (${res.code})")
                val body = res.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: r.size
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) _progress.value = (done.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
            }
            _progress.value = null
            withContext(Dispatchers.Main) { install(ctx, file) }
        }.onFailure { _progress.value = null }
    }

    private fun install(ctx: Context, file: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Si el usuario volvió de dar el permiso de instalar, reintentar con el archivo ya bajado. */
    fun pendingFile(ctx: Context): File? = File(ctx.cacheDir, "updates").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
    fun installPending(ctx: Context) { pendingFile(ctx)?.let { install(ctx, it) } }
}

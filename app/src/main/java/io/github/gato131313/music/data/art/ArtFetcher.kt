package io.github.gato131313.music.data.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.pxOrElse
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.data.scan.MediaScanner
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/** Petición de portada: se usa como "model" en AsyncImage. */
data class ArtRequest(val id: Long, val isVideo: Boolean, val path: String) {
    companion object {
        fun of(t: Track) = ArtRequest(t.id, t.isVideo, t.path)
    }
}

class ArtKeyer : Keyer<ArtRequest> {
    override fun key(data: ArtRequest, options: Options) = "art:${data.id}:${data.isVideo}"
}

class ArtFetcher(
    private val data: ArtRequest,
    private val options: Options,
    private val ctx: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = "${data.id}_${if (data.isVideo) "v" else "a"}"
        if (key in missing) throw FileNotFoundException("sin portada")
        val w = options.size.width.pxOrElse { 512 }.coerceIn(64, 1024)
        val h = options.size.height.pxOrElse { 512 }.coerceIn(64, 1024)
        val bmp = load(key, maxOf(w, h)) ?: run {
            missing += key
            throw FileNotFoundException("sin portada")
        }
        return DrawableResult(BitmapDrawable(ctx.resources, bmp), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun load(key: String, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            return try {
                ctx.contentResolver.loadThumbnail(MediaScanner.contentUri(data.id, data.isVideo), Size(size, size), null)
            } catch (e: Exception) {
                null
            }
        }
        // Android 8–9: extraer y guardar en caché propia.
        val dir = File(ctx.cacheDir, "art").apply { mkdirs() }
        val f = File(dir, "$key.jpg")
        if (f.exists()) return if (f.length() == 0L) null else BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 1 })
        val bmp = extract(size)
        if (bmp == null) {
            runCatching { f.createNewFile() }
            return null
        }
        runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
        return bmp
    }

    private fun extract(size: Int): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(data.path)
            if (data.isVideo) {
                mmr.getFrameAtTime(5_000_000)?.let { scale(it, size) }
            } else {
                mmr.embeddedPicture?.let { bytes ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var sample = 1
                    while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun scale(b: Bitmap, size: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= size) return b
        val f = size.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }

    class Factory(private val ctx: Context) : Fetcher.Factory<ArtRequest> {
        override fun create(data: ArtRequest, options: Options, imageLoader: ImageLoader): Fetcher = ArtFetcher(data, options, ctx)
    }

    companion object {
        /** Archivos sin portada (para no reintentar en cada scroll). */
        val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}

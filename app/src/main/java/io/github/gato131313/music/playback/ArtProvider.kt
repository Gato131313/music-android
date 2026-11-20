package io.github.gato131313.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import io.github.gato131313.music.data.model.Library
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.data.scan.MediaScanner

/**
 * Portada SIEMPRE disponible como bitmap (notificación, panel de controles, pantalla de bloqueo, widget):
 *  1. la portada del archivo;
 *  2. si no tiene, un fotograma de su video emparejado (o del propio video);
 *  3. si tampoco, una portada generada: degradado con colores del nombre y la inicial.
 */
object ArtProvider {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun get(ctx: Context, t: Track, lib: Library?, size: Int = 512): Bitmap {
        val k = "${t.id}:${t.isVideo}:$size"
        cache.get(k)?.let { return it }
        val bmp = fromFile(ctx, t.id, t.isVideo, size)
            ?: t.pairId?.let { lib?.byId?.get(it) }?.takeIf { it.isVideo }?.let { fromFile(ctx, it.id, true, size) }
            ?: generated(t, size)
        cache.put(k, bmp)
        return bmp
    }

    /** Solo la portada real (sin la generada), para saber si existe. */
    fun real(ctx: Context, t: Track, lib: Library?, size: Int = 512): Bitmap? =
        fromFile(ctx, t.id, t.isVideo, size) ?: t.pairId?.let { lib?.byId?.get(it) }?.takeIf { it.isVideo }?.let { fromFile(ctx, it.id, true, size) }

    private fun fromFile(ctx: Context, id: Long, isVideo: Boolean, size: Int): Bitmap? {
        val content = MediaScanner.contentUri(id, isVideo)
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { ctx.contentResolver.loadThumbnail(content, Size(size, size), null) }.getOrNull()?.let { return it }
        }
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(ctx, content)
            val b = if (isVideo) mmr.getScaledFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
            else mmr.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            b
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** Degradado con dos colores derivados del nombre + la inicial grande. */
    fun generated(t: Track, size: Int = 512): Bitmap {
        val seed = (t.artist ?: t.title).hashCode()
        val h1 = ((seed ushr 1) % 360).toFloat()
        val h2 = (h1 + 40 + (seed ushr 9) % 80) % 360
        val c1 = Color.HSVToColor(floatArrayOf(h1, 0.65f, 0.80f))
        val c2 = Color.HSVToColor(floatArrayOf(h2, 0.75f, 0.32f))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        val letter = (t.title.firstOrNull { it.isLetterOrDigit() } ?: '♪').uppercaseChar().toString()
        p.color = Color.WHITE
        p.alpha = 235
        p.textSize = size * 0.46f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        cv.drawText(letter, size / 2f, y, p)
        return bmp
    }
}

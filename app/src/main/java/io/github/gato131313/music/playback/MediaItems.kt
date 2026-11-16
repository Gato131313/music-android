package io.github.gato131313.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.data.scan.MediaScanner
import java.io.IOException
import java.util.concurrent.Executors

const val ART_SCHEME = "musicart"

fun Track.artUri(): Uri = Uri.parse("$ART_SCHEME://${if (isVideo) "v" else "a"}/$id")

/** Convierte una pista en MediaItem. [videoUri] = usar el archivo de video (modo Video de una pareja). */
fun Track.toMediaItem(playVideo: Track? = null): MediaItem {
    val src = playVideo ?: this
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(MediaScanner.contentUri(src.id, src.isVideo))
        .setMimeType(src.mime)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(displayArtist)
                .setAlbumTitle(album)
                .setGenre(genre)
                .setArtworkUri(artUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(if (isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(android.os.Bundle().apply { putLong(EXTRA_PLAYING_ID, src.id) })
                .build(),
        )
        .build()
}

const val EXTRA_PLAYING_ID = "playingId"

/** Portadas para notificación, pantalla bloqueada y Bluetooth. */
@UnstableApi
class ArtBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val exec: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = exec.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("no se pudo decodificar")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme != ART_SCHEME) return Futures.immediateFailedFuture(IOException("esquema no soportado"))
        return exec.submit<Bitmap> {
            val isVideo = uri.host == "v"
            val id = uri.lastPathSegment?.toLongOrNull() ?: throw IOException("id")
            load(id, isVideo) ?: throw IOException("sin portada")
        }
    }

    private fun load(id: Long, isVideo: Boolean): Bitmap? {
        val content = MediaScanner.contentUri(id, isVideo)
        if (Build.VERSION.SDK_INT >= 29) {
            return runCatching { ctx.contentResolver.loadThumbnail(content, Size(512, 512), null) }.getOrNull()
        }
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(ctx, content)
            if (isVideo) mmr.getFrameAtTime(5_000_000) else mmr.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }
}

package io.github.gato131313.music.data.scan

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.gato131313.music.data.model.RawMedia
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** Lee TODO el teléfono vía MediaStore (audio y video). Nunca modifica archivos. */
class MediaScanner(private val ctx: Context) {

    fun hasAudioPermission(): Boolean = granted(audioPermission())
    fun hasVideoPermission(): Boolean = granted(videoPermission())

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun audioUri(): Uri = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    fun videoUri(): Uri = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    fun scan(): List<RawMedia> {
        val out = ArrayList<RawMedia>(2048)
        if (hasAudioPermission()) out += scanAudio()
        if (hasVideoPermission()) out += scanVideo()
        return out
    }

    private val skipFolders = Regex(
        "(?i)(voice notes|notas de voz|recordings|grabaciones|call ?recordings?|sound ?recorder|/ringtones/|/notifications/|/alarms/|android/data/|android/media/com\\.whatsapp/whatsapp/media/whatsapp voice)",
    )

    private fun scanAudio(): List<RawMedia> {
        val proj = buildList {
            add(MediaStore.Audio.Media._ID); add(MediaStore.Audio.Media.DATA); add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE); add(MediaStore.Audio.Media.ARTIST); add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID); add(MediaStore.Audio.Media.DURATION); add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED); add(MediaStore.Audio.Media.MIME_TYPE); add(MediaStore.Audio.Media.YEAR)
            if (Build.VERSION.SDK_INT >= 30) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()
        val sel = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        val genres = if (Build.VERSION.SDK_INT < 30) legacyGenres() else emptyMap()
        val list = ArrayList<RawMedia>()
        query(audioUri(), proj, sel) { c ->
            val path = c.str(MediaStore.Audio.Media.DATA) ?: return@query
            if (skipFolders.containsMatchIn(path)) return@query
            val id = c.long(MediaStore.Audio.Media._ID)
            list += RawMedia(
                id = id,
                isVideo = false,
                path = path,
                displayName = c.str(MediaStore.Audio.Media.DISPLAY_NAME) ?: File(path).name,
                folder = folderOf(path),
                title = c.str(MediaStore.Audio.Media.TITLE),
                artist = c.str(MediaStore.Audio.Media.ARTIST),
                album = c.str(MediaStore.Audio.Media.ALBUM),
                albumId = c.long(MediaStore.Audio.Media.ALBUM_ID),
                genre = if (Build.VERSION.SDK_INT >= 30) c.str(MediaStore.Audio.Media.GENRE) else genres[id],
                durationMs = c.long(MediaStore.Audio.Media.DURATION),
                size = c.long(MediaStore.Audio.Media.SIZE),
                dateAdded = c.long(MediaStore.Audio.Media.DATE_ADDED),
                mime = c.str(MediaStore.Audio.Media.MIME_TYPE),
                year = c.long(MediaStore.Audio.Media.YEAR).toInt(),
            )
        }
        return list
    }

    private fun scanVideo(): List<RawMedia> {
        val proj = buildList {
            add(MediaStore.Video.Media._ID); add(MediaStore.Video.Media.DATA); add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.TITLE); add(MediaStore.Video.Media.DURATION); add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED); add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.ARTIST); add(MediaStore.Video.Media.ALBUM)
        }.toTypedArray()
        val list = ArrayList<RawMedia>()
        query(videoUri(), proj, null) { c ->
            val path = c.str(MediaStore.Video.Media.DATA) ?: return@query
            if (path.contains("/Android/data/", true)) return@query
            list += RawMedia(
                id = c.long(MediaStore.Video.Media._ID),
                isVideo = true,
                path = path,
                displayName = c.str(MediaStore.Video.Media.DISPLAY_NAME) ?: File(path).name,
                folder = folderOf(path),
                title = c.str(MediaStore.Video.Media.TITLE),
                artist = c.str(MediaStore.Video.Media.ARTIST),
                album = null,
                albumId = 0,
                genre = null,
                durationMs = c.long(MediaStore.Video.Media.DURATION),
                size = c.long(MediaStore.Video.Media.SIZE),
                dateAdded = c.long(MediaStore.Video.Media.DATE_ADDED),
                mime = c.str(MediaStore.Video.Media.MIME_TYPE),
            )
        }
        return list
    }

    /** Antes de Android 11 no hay columna GENRE: se arma el mapa id -> género. */
    private fun legacyGenres(): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            val gUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            val genres = ArrayList<Pair<Long, String>>()
            query(gUri, arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null) { c ->
                val n = c.str(MediaStore.Audio.Genres.NAME) ?: return@query
                genres += c.long(MediaStore.Audio.Genres._ID) to n
            }
            for ((gid, name) in genres) {
                query(MediaStore.Audio.Genres.Members.getContentUri("external", gid), arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null) { c ->
                    map[c.long(MediaStore.Audio.Genres.Members.AUDIO_ID)] = name
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "géneros", e)
        }
        return map
    }

    private inline fun query(uri: Uri, proj: Array<String>, sel: String?, row: (Cursor) -> Unit) {
        try {
            ctx.contentResolver.query(uri, proj, sel, null, null)?.use { c ->
                while (c.moveToNext()) row(c)
            }
        } catch (e: Exception) {
            Log.w(TAG, "query $uri", e)
        }
    }

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.long(col: String): Long {
        val i = getColumnIndex(col)
        return if (i < 0 || isNull(i)) 0 else getLong(i)
    }

    private fun folderOf(path: String): String {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val dir = path.substringBeforeLast('/', "")
        return if (dir.startsWith(root)) dir.removePrefix(root).trimStart('/') + "/" else "$dir/"
    }

    /**
     * "Escanear medios": pide al sistema reindexar las carpetas típicas (descargas de Snaptube, WhatsApp, etc.)
     * para que aparezcan archivos que MediaStore aún no conoce.
     */
    suspend fun requestSystemScan() {
        val root = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_MOVIES,
            "Snaptube", "snaptube", "VidMate", "Telegram", "Android/media/com.whatsapp/WhatsApp/Media",
        ).map { File(root, it) }.filter { it.exists() }
        val files = ArrayList<String>()
        for (d in dirs) {
            d.walkTopDown().maxDepth(6).forEach { f ->
                if (f.isFile && MEDIA_EXT.containsMatchIn(f.name)) files += f.absolutePath
            }
        }
        if (files.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            var left = files.size
            MediaScannerConnection.scanFile(ctx, files.toTypedArray(), null) { _, _ ->
                left--
                if (left == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }

    companion object {
        private const val TAG = "MediaScanner"
        private val MEDIA_EXT = Regex("(?i)\\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma|mp4|m4v|mkv|webm|3gp)$")

        fun audioPermission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        fun videoPermission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

        fun contentUri(id: Long, isVideo: Boolean): Uri = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id,
        )
    }
}

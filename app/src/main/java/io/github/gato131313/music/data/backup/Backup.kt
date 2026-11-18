package io.github.gato131313.music.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import io.github.gato131313.music.AppGraph
import io.github.gato131313.music.data.db.ArtistAlias
import io.github.gato131313.music.data.db.ArtistReject
import io.github.gato131313.music.data.db.EqPreset
import io.github.gato131313.music.data.db.Favorite
import io.github.gato131313.music.data.db.Hidden
import io.github.gato131313.music.data.db.ImportManual
import io.github.gato131313.music.data.db.ImportUnmatched
import io.github.gato131313.music.data.db.PairOverride
import io.github.gato131313.music.data.db.PlayEvent
import io.github.gato131313.music.data.db.PlayStat
import io.github.gato131313.music.data.db.Playlist
import io.github.gato131313.music.data.db.PlaylistItem
import io.github.gato131313.music.data.db.SongFx
import io.github.gato131313.music.data.db.SplitDecision
import io.github.gato131313.music.data.db.TrackEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class BkFav(val key: String, val at: Long)
@Serializable data class BkStat(val key: String, val plays: Int, val imported: Int, val skips: Int, val last: Long, val ms: Long)
@Serializable data class BkEvent(val key: String, val at: Long, val ms: Long, val title: String, val artist: String? = null)
@Serializable data class BkPlaylist(val name: String, val createdAt: Long, val sort: Int, val keys: List<String>)
@Serializable data class BkEdit(val key: String, val title: String? = null, val artist: String? = null, val album: String? = null)
@Serializable data class BkAlias(val from: String, val to: String, val name: String? = null)
@Serializable data class BkPair(val a: String, val b: String)
@Serializable data class BkHidden(val path: String, val folder: Boolean)
@Serializable data class BkKV(val k: String, val v: String)

@Serializable
data class BackupFile(
    val app: String = "MUSIC Android",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val settings: String = "",
    val favorites: List<BkFav> = emptyList(),
    val stats: List<BkStat> = emptyList(),
    val events: List<BkEvent> = emptyList(),
    val playlists: List<BkPlaylist> = emptyList(),
    val edits: List<BkEdit> = emptyList(),
    val aliases: List<BkAlias> = emptyList(),
    val rejects: List<BkPair> = emptyList(),
    val splits: List<BkKV> = emptyList(),
    val pairs: List<BkPair> = emptyList(),
    val hidden: List<BkHidden> = emptyList(),
    val songFx: List<BkKV> = emptyList(),
    val eqPresets: List<BkKV> = emptyList(),
    val importManual: List<BkKV> = emptyList(),
)

/** Respaldo completo de los datos del usuario (no incluye los archivos de música). */
class BackupManager(private val g: AppGraph) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun build(): BackupFile = withContext(Dispatchers.IO) {
        val db = g.db
        val items = db.playlists().allItemsNow().groupBy { it.playlistId }
        BackupFile(
            settings = g.settings.export(),
            favorites = db.user().favorites().first().map { BkFav(it.key, it.addedAt) },
            stats = db.stats().statsNow().map { BkStat(it.key, it.plays, it.importedPlays, it.skips, it.lastPlayed, it.msPlayed) },
            events = db.stats().allEvents().map { BkEvent(it.key, it.at, it.ms, it.title, it.artist) },
            playlists = db.playlists().playlistsNow().map { p -> BkPlaylist(p.name, p.createdAt, p.sort, items[p.id].orEmpty().sortedBy { it.position }.map { it.key }) },
            edits = db.user().edits().first().map { BkEdit(it.key, it.title, it.artist, it.album) },
            aliases = db.user().aliases().first().map { BkAlias(it.fromKey, it.toKey, it.toName) },
            rejects = db.user().rejects().first().map { BkPair(it.a, it.b) },
            splits = db.user().splits().first().map { BkKV(it.key, it.split.toString()) },
            pairs = db.user().pairs().first().map { BkPair(it.audioKey, it.videoKey) },
            hidden = db.user().hidden().first().map { BkHidden(it.path, it.isFolder) },
            songFx = db.user().songFx().first().map { BkKV(it.key, it.preset) },
            eqPresets = db.user().eqPresets().first().map { BkKV(it.name, it.gains) },
            importManual = db.stats().manualNow().map { BkKV(it.rowId, it.key) },
        )
    }

    suspend fun exportTo(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val text = json.encodeToString(BackupFile.serializer(), build())
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }

    /** Restaura reemplazando todo lo actual. */
    suspend fun restoreFrom(ctx: Context, uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val b = json.decodeFromString(BackupFile.serializer(), text)
        require(b.app.startsWith("MUSIC")) { "No es un respaldo de MUSIC" }
        val db = g.db
        db.withTransaction {
            db.clearUserTables()
            b.favorites.forEach { db.user().upsertFavorite(Favorite(it.key, it.at)) }
            db.stats().upsertStats(b.stats.map { PlayStat(it.key, it.plays, it.imported, it.skips, it.last, it.ms) })
            db.stats().insertEvents(b.events.map { PlayEvent(key = it.key, at = it.at, ms = it.ms, title = it.title, artist = it.artist) })
            for (p in b.playlists) {
                val id = db.playlists().insert(Playlist(name = p.name, createdAt = p.createdAt, sort = p.sort))
                db.playlists().insertItems(p.keys.mapIndexed { i, k -> PlaylistItem(playlistId = id, key = k, position = i) })
            }
            b.edits.forEach { db.user().upsertEdit(TrackEdit(it.key, it.title, it.artist, it.album)) }
            b.aliases.forEach { db.user().upsertAlias(ArtistAlias(it.from, it.to, it.name)) }
            b.rejects.forEach { db.user().upsertReject(ArtistReject(it.a, it.b)) }
            b.splits.forEach { db.user().upsertSplit(SplitDecision(it.k, it.v.toBoolean())) }
            b.pairs.forEach { db.user().upsertPair(PairOverride(it.a, it.b)) }
            b.hidden.forEach { db.user().upsertHidden(Hidden(it.path, it.folder)) }
            b.songFx.forEach { db.user().upsertSongFx(SongFx(it.k, it.v)) }
            b.eqPresets.forEach { db.user().upsertEqPreset(EqPreset(it.k, it.v)) }
            b.importManual.forEach { db.stats().upsertManual(ImportManual(it.k, it.v)) }
        }
        if (b.settings.isNotBlank()) g.settings.import(b.settings)
        b
    }

    /**
     * Respaldo automático en Descargas/MUSIC (Android 10+): un archivo por día de la semana, así
     * siempre hay una copia en el teléfono aunque se desinstale la app.
     */
    suspend fun autoBackup(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        val b = build()
        if (b.favorites.isEmpty() && b.stats.isEmpty() && b.playlists.all { it.keys.isEmpty() }) return@withContext false
        val name = "respaldo-music-${java.time.LocalDate.now().dayOfWeek.value}.json"
        val resolver = ctx.contentResolver
        val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val existing = resolver.query(coll, arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?", arrayOf(name, "%MUSIC%"), null)
            ?.use { c -> if (c.moveToFirst()) android.content.ContentUris.withAppendedId(coll, c.getLong(0)) else null }
        val uri = existing ?: resolver.insert(coll, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MUSIC")
        }) ?: return@withContext false
        runCatching {
            resolver.openOutputStream(uri, "wt")!!.use { it.write(json.encodeToString(BackupFile.serializer(), b).toByteArray()) }
            true
        }.getOrDefault(false)
    }

    // ---------- Importar CSV de la app anterior ----------
    suspend fun importCsv(ctx: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val rows = ImportCore.parseCsv(text)
        require(rows.isNotEmpty()) { "el archivo no trae filas de canciones" }
        require(g.library.library.value.songs.isNotEmpty()) { "tu biblioteca aún está cargando, inténtalo en unos segundos" }
        val manual = g.db.stats().manualNow().associate { it.rowId to it.key }
        val res = ImportCore.match(rows, g.library.library.value.songs, manual)
        applyImport(res.plays, res.favorites)
        g.db.stats().clearUnmatched()
        g.db.stats().insertUnmatched(res.unmatched.map { ImportUnmatched(title = it.title, artist = it.artist, plays = it.plays, favorite = it.favorite) })
        res
    }

    /** Fija (no suma dos veces) las reproducciones importadas: reimportar el mismo archivo da lo mismo. */
    private suspend fun applyImport(plays: Map<String, Int>, favs: Set<String>) {
        val db = g.db
        db.withTransaction {
            val current = db.stats().statsNow().associateBy { it.key }
            db.stats().upsertStats(current.values.filter { it.importedPlays > 0 && it.key !in plays }.map { it.copy(importedPlays = 0) })
            db.stats().upsertStats(plays.map { (k, n) -> (current[k] ?: PlayStat(k)).copy(importedPlays = n) })
            favs.forEach { db.user().upsertFavorite(Favorite(it)) }
        }
    }

    /** Empatar a mano una fila que no se encontró. */
    suspend fun matchManually(row: ImportUnmatched, key: String) {
        val db = g.db
        db.withTransaction {
            val s = db.stats().stat(key) ?: PlayStat(key)
            db.stats().upsertStat(s.copy(importedPlays = s.importedPlays + row.plays))
            // Recordar la unión: si se importa el mismo archivo otra vez, esta fila ya cae aquí y no se duplica
            val r = CsvRow(row.title, row.artist, row.plays, row.favorite)
            val n = db.stats().manualNow().count { it.rowId.startsWith(ImportCore.rowId(r) + "#") }
            db.stats().upsertManual(ImportManual(ImportCore.manualId(r, n), key))
            if (row.favorite) db.user().upsertFavorite(Favorite(key))
            db.stats().deleteUnmatched(row.id)
        }
    }

    suspend fun dismissUnmatched(row: ImportUnmatched) = g.db.stats().deleteUnmatched(row.id)
}

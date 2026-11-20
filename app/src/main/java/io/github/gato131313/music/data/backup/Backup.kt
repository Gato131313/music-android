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
@Serializable data class BkLink(val id: String, val key: String, val title: String = "", val artist: String = "", val plays: Int = 0, val how: String = "MANO")

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
    val importLinks: List<BkLink> = emptyList(),
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
            importLinks = db.stats().manualNow().map { BkLink(it.rowId, it.key, it.title, it.artist, it.plays, it.how) },
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
            b.importLinks.forEach { db.stats().upsertManual(ImportManual(it.id, it.key, it.title, it.artist, it.plays, it.how)) }
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
    private fun tracks() = g.library.library.value.let { it.songs + it.allVideos }

    /** Clave de memoria de cada fila (la n-ésima fila idéntica lleva su número). */
    private fun ids(rows: List<CsvRow>, start: Map<String, Int> = emptyMap()): List<String> {
        val seen = HashMap(start)
        return rows.map { r -> val id = ImportCore.rowId(r); ImportCore.manualId(r, seen.merge(id, 1, Int::plus)!! - 1) }
    }

    private fun linkOf(id: String, l: Link) = ImportManual(id, l.track.key, l.row.title, l.row.artist, l.row.plays, l.how.name)

    suspend fun importCsv(ctx: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val rows = ImportCore.parseCsv(text)
        require(rows.isNotEmpty()) { "el archivo no trae filas de canciones" }
        val tracks = tracks()
        require(tracks.isNotEmpty()) { "tu biblioteca aún está cargando, inténtalo en unos segundos" }
        val manual = g.db.stats().manualNow().filter { it.how == How.MANO.name }.associate { it.rowId to it.key }
        val res = ImportCore.match(rows, tracks, manual)
        val rowIds = ids(rows)
        applyImport(res.plays, res.favorites)
        val db = g.db
        db.withTransaction {
            db.stats().clearAutoLinks()
            db.stats().upsertManuals(res.assignment.links.filter { it.how != How.MANO }.map { linkOf(rowIds[it.index], it) })
            db.stats().clearUnmatched()
            db.stats().insertUnmatched(res.assignment.discarded.map { d -> ImportUnmatched(title = d.row.title, artist = d.row.artist, plays = d.row.plays, favorite = d.row.favorite, reason = d.reason) })
        }
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

    data class Rematch(val total: Int, val linked: Int, val discarded: List<Pair<String, String>>)

    /**
     * Filas que quedaron pendientes en una importación de una versión anterior: se empatan solas, sin volver a
     * importar el archivo. Solo SUMA las de esas filas (lo ya importado no se toca) y cada fila se procesa una vez.
     */
    suspend fun rematchPending(): Rematch? = withContext(Dispatchers.IO) {
        val db = g.db
        val pend = db.stats().pendingNow()
        val tracks = tracks()
        if (pend.isEmpty() || tracks.isEmpty()) return@withContext null
        val rows = pend.map { CsvRow(it.title, it.artist, it.plays, it.favorite) }
        // 1 a 1: las canciones que ya recibieron reproducciones de la importación anterior no se ofrecen
        val taken = db.stats().statsNow().filter { it.importedPlays > 0 }.map { it.key }.toSet()
        val first = ImportMatcher.assign(rows, tracks, taken = taken)
        // Si alguna fila solo tenía canciones ya tomadas, a su más probable (no se deja pendiente)
        val again = first.discarded.map { it.index }
        val second = if (again.isEmpty()) null else ImportMatcher.assign(again.map { rows[it] }, tracks)
        val links = first.links + second?.links.orEmpty().map { it.copy(index = again[it.index], how = How.PROBABLE) }
        val discarded = second?.discarded.orEmpty().map { it.copy(index = again[it.index]) }
        db.withTransaction {
            val existing = db.stats().manualNow().groupingBy { it.rowId.substringBeforeLast('#') }.eachCount()
            val rowIds = ids(rows, existing)
            for (l in links) {
                val s = db.stats().stat(l.track.key) ?: PlayStat(l.track.key)
                db.stats().upsertStat(s.copy(importedPlays = s.importedPlays + l.row.plays))
                db.stats().upsertManual(linkOf(rowIds[l.index], l))
                if (l.row.favorite) db.user().upsertFavorite(Favorite(l.track.key))
                db.stats().deleteUnmatched(pend[l.index].id)
            }
            for (d in discarded) {
                db.stats().deleteUnmatched(pend[d.index].id)
                db.stats().insertUnmatched(listOf(pend[d.index].copy(id = 0, reason = d.reason)))
            }
        }
        Rematch(rows.size, links.size, discarded.map { it.row.title to it.reason })
    }

    /** Corregir una unión automática: mueve sus reproducciones a otra canción y la recuerda como elegida a mano. */
    suspend fun relink(link: ImportManual, key: String) {
        if (link.key == key) return
        val db = g.db
        db.withTransaction {
            val old = db.stats().stat(link.key)
            if (old != null) db.stats().upsertStat(old.copy(importedPlays = (old.importedPlays - link.plays).coerceAtLeast(0)))
            val s = db.stats().stat(key) ?: PlayStat(key)
            db.stats().upsertStat(s.copy(importedPlays = s.importedPlays + link.plays))
            db.stats().upsertManual(link.copy(key = key, how = How.MANO.name))
        }
    }

    /** Unir a mano una fila descartada (opcional). */
    suspend fun matchManually(row: ImportUnmatched, key: String) {
        val db = g.db
        db.withTransaction {
            val s = db.stats().stat(key) ?: PlayStat(key)
            db.stats().upsertStat(s.copy(importedPlays = s.importedPlays + row.plays))
            // Recordar la unión: si se importa el mismo archivo otra vez, esta fila ya cae aquí y no se duplica
            val r = CsvRow(row.title, row.artist, row.plays, row.favorite)
            val n = db.stats().manualNow().count { it.rowId.startsWith(ImportCore.rowId(r) + "#") }
            db.stats().upsertManual(ImportManual(ImportCore.manualId(r, n), key, row.title, row.artist, row.plays, How.MANO.name))
            if (row.favorite) db.user().upsertFavorite(Favorite(key))
            db.stats().deleteUnmatched(row.id)
        }
    }

    suspend fun dismissUnmatched(row: ImportUnmatched) = g.db.stats().deleteUnmatched(row.id)
}

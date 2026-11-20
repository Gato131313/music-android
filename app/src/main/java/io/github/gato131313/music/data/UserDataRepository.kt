package io.github.gato131313.music.data

import io.github.gato131313.music.data.db.AppDatabase
import io.github.gato131313.music.data.db.Favorite
import io.github.gato131313.music.data.db.PlayEvent
import io.github.gato131313.music.data.db.PlayStat
import io.github.gato131313.music.data.db.Playlist
import io.github.gato131313.music.data.db.PlaylistWithCount
import io.github.gato131313.music.data.db.RecentKey
import io.github.gato131313.music.data.model.Library
import io.github.gato131313.music.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Favoritos, reproducciones y playlists (todo local, en el teléfono). */
class UserDataRepository(private val db: AppDatabase, private val scope: CoroutineScope) {

    val favorites: StateFlow<Set<String>> = db.user().favorites().map { l -> l.map { it.key }.toCollection(LinkedHashSet()) }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val favoritesOrdered: StateFlow<List<String>> = db.user().favorites().map { l -> l.map { it.key } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<Map<String, PlayStat>> = db.stats().stats().map { l -> l.associateBy { it.key } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val recent: StateFlow<List<RecentKey>> = db.stats().recent().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val songFx: StateFlow<Map<String, String>> = db.user().songFx().map { l -> l.associate { it.key to it.preset } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val eqPresets: StateFlow<Map<String, List<Float>>> = db.user().eqPresets().map { l ->
        l.associate { p -> p.name to p.gains.split(",").mapNotNull { it.trim().toFloatOrNull() } }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    suspend fun saveEqPreset(name: String, bands: List<Float>) = db.user().upsertEqPreset(io.github.gato131313.music.data.db.EqPreset(name.trim(), bands.joinToString(",")))
    suspend fun deleteEqPreset(name: String) = db.user().deleteEqPreset(name)
    suspend fun setSongPreset(key: String, preset: String?) {
        if (preset == null) db.user().deleteSongFx(key) else db.user().upsertSongFx(io.github.gato131313.music.data.db.SongFx(key, preset))
    }

    val playlists: StateFlow<List<PlaylistWithCount>> = db.playlists().playlists().stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            if (db.playlists().playlistsNow().none { it.name == DEFAULT_PLAYLIST }) {
                db.playlists().insert(Playlist(name = DEFAULT_PLAYLIST, sort = -1))
            }
        }
    }

    fun isFavorite(key: String) = key in favorites.value

    suspend fun toggleFavorite(t: Track) {
        if (isFavorite(t.key)) db.user().deleteFavorite(t.key) else db.user().upsertFavorite(Favorite(t.key))
    }

    suspend fun setFavorite(key: String, fav: Boolean) {
        if (fav) db.user().upsertFavorite(Favorite(key)) else db.user().deleteFavorite(key)
    }

    /** Registra una escucha (se llama cuando cuenta como reproducción). */
    suspend fun recordPlay(t: Track, msPlayed: Long, at: Long = System.currentTimeMillis()) {
        val s = db.stats().stat(t.key) ?: PlayStat(t.key)
        db.stats().upsertStat(s.copy(plays = s.plays + 1, lastPlayed = at, msPlayed = s.msPlayed + msPlayed))
        db.stats().insertEvent(PlayEvent(key = t.key, at = at, ms = msPlayed, title = t.title, artist = t.artist))
    }

    suspend fun addListenTime(t: Track, extraMs: Long) {
        val s = db.stats().stat(t.key) ?: return
        db.stats().upsertStat(s.copy(msPlayed = s.msPlayed + extraMs))
        db.stats().addToLastEvent(t.key, extraMs)
    }

    suspend fun recordSkip(t: Track) {
        val s = db.stats().stat(t.key) ?: PlayStat(t.key)
        db.stats().upsertStat(s.copy(skips = s.skips + 1))
    }

    fun totalPlays(key: String): Int = stats.value[key]?.let { it.plays + it.importedPlays } ?: 0

    /**
     * Total de una canción: importadas del CSV + escuchadas aquí. Canción y su video emparejado cuentan como una
     * sola (da igual cuál de los dos se importó o se escucha).
     */
    fun playsOf(t: Track, lib: Library, s: Map<String, PlayStat> = stats.value): Int {
        fun one(k: String) = s[k]?.let { it.plays + it.importedPlays } ?: 0
        val pair = t.pairId?.let { lib.byId[it] }
        return one(t.key) + (pair?.let { one(it.key) } ?: 0)
    }

    /** Canción que representa a la pareja canción+video (la de audio). */
    private fun canonical(t: Track, lib: Library): Track = if (t.isVideo) t.pairId?.let { lib.byId[it] }?.takeIf { !it.isVideo } ?: t else t

    // ---------- Listas inteligentes ----------
    fun favoriteTracks(lib: Library): List<Track> = favoritesOrdered.value.mapNotNull { lib.byKey[it] }

    fun recentlyAdded(lib: Library): List<Track> {
        if (lib.songs.isEmpty()) return emptyList()
        val sorted = lib.songs.sortedByDescending { it.dateAdded }
        val limit = System.currentTimeMillis() / 1000 - 30L * 86400
        val recent = sorted.takeWhile { it.dateAdded >= limit }
        return if (recent.size >= 25) recent.take(300) else sorted.take(50)
    }

    fun recentlyPlayed(lib: Library): List<Track> = recent.value.mapNotNull { lib.byKey[it.key] }.filter { !it.isVideo || it.pairId == null }.take(500)

    /** Ordenadas por el total (importadas + nuevas), de mayor a menor; canción+video una sola vez. */
    fun mostPlayed(lib: Library): List<Track> {
        val s = stats.value
        return s.values.asSequence()
            .filter { it.plays + it.importedPlays > 0 }
            .mapNotNull { lib.byKey[it.key] }
            .map { canonical(it, lib) }
            .distinctBy { it.key }
            .map { it to playsOf(it, lib, s) }
            .sortedByDescending { it.second }
            .take(500).map { it.first }.toList()
    }

    // ---------- Playlists ----------
    suspend fun createPlaylist(name: String, keys: List<String> = emptyList()): Long {
        val id = db.playlists().insert(Playlist(name = name.trim()))
        if (keys.isNotEmpty()) db.playlists().add(id, keys)
        return id
    }

    suspend fun addToPlaylist(id: Long, keys: List<String>) = db.playlists().add(id, keys)
    suspend fun removeFromPlaylist(id: Long, key: String) = db.playlists().deleteKey(id, key)
    suspend fun renamePlaylist(id: Long, name: String) = db.playlists().rename(id, name.trim())
    suspend fun deletePlaylist(id: Long) = db.playlists().delete(id)
    suspend fun reorderPlaylist(id: Long, keys: List<String>) = db.playlists().reorder(id, keys)
    fun playlistItems(id: Long) = db.playlists().items(id)

    companion object {
        const val DEFAULT_PLAYLIST = "Predeterminado"
    }
}

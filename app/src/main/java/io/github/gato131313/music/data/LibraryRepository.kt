package io.github.gato131313.music.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.gato131313.music.data.clean.LibraryBuilder
import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.db.AppDatabase
import io.github.gato131313.music.data.db.ArtistAlias
import io.github.gato131313.music.data.db.ArtistReject
import io.github.gato131313.music.data.db.Hidden
import io.github.gato131313.music.data.db.MediaEntity
import io.github.gato131313.music.data.db.PairOverride
import io.github.gato131313.music.data.db.SplitDecision
import io.github.gato131313.music.data.db.TrackEdit
import io.github.gato131313.music.data.model.Library
import io.github.gato131313.music.data.model.RawMedia
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.data.model.TrackEditData
import io.github.gato131313.music.data.model.UserEdits
import io.github.gato131313.music.data.prefs.SettingsStore
import io.github.gato131313.music.data.scan.MediaScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class LibraryRepository(
    private val ctx: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    val scanner = MediaScanner(ctx)
    private val raws = MutableStateFlow<List<RawMedia>?>(null)
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val _hasPermission = MutableStateFlow(scanner.hasAudioPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission
    private val _lastScanMs = MutableStateFlow(0L)
    val lastScanMs: StateFlow<Long> = _lastScanMs
    private val scanMutex = Mutex()

    private val userEdits = combine(
        combine(db.user().edits(), db.user().aliases(), db.user().rejects()) { e, a, r -> Triple(e, a, r) },
        combine(db.user().splits(), db.user().hidden(), db.user().pairs()) { s, h, p -> Triple(s, h, p) },
        settings.flow.map { it.minDurationSec }.distinctUntilChanged(),
    ) { (e, a, r), (s, h, p), minDur ->
        UserEdits(
            edits = e.associate { it.key to TrackEditData(it.title, it.artist, it.album) },
            aliases = a.associate { it.fromKey to (it.toKey to it.toName) },
            rejected = r.map { it.a to it.b }.toSet(),
            splitDecisions = s.associate { it.key to it.split },
            hiddenPaths = h.filter { !it.isFolder }.map { it.path }.toSet(),
            hiddenFolders = h.filter { it.isFolder }.map { it.path }.toSet(),
            pairOverrides = p.associate { it.audioKey to it.videoKey },
            minDurationMs = minDur * 1000L,
        )
    }

    /** null = aún no se ha cargado nada (ni caché). */
    val libraryOrNull: StateFlow<Library?> = combine(raws.filterNotNull(), userEdits) { r, u ->
        val t0 = System.nanoTime()
        LibraryBuilder.build(r, u).also {
            Log.i(TAG, "biblioteca: ${it.songs.size} canciones, ${it.allVideos.size} videos en ${(System.nanoTime() - t0) / 1_000_000} ms")
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    val library: StateFlow<Library> = libraryOrNull.map { it ?: Library.EMPTY }
        .stateIn(scope, SharingStarted.Eagerly, Library.EMPTY)

    private var observerJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observerJob?.cancel()
            observerJob = scope.launch { delay(2000); refresh() }
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            val cached = db.media().all()
            if (cached.isNotEmpty()) raws.value = cached.map { it.toRaw() }
            if (scanner.hasAudioPermission() || scanner.hasVideoPermission()) refresh() else if (cached.isEmpty()) raws.value = emptyList()
        }
        runCatching {
            ctx.contentResolver.registerContentObserver(scanner.audioUri(), true, observer)
            ctx.contentResolver.registerContentObserver(scanner.videoUri(), true, observer)
        }
    }

    fun onPermissionsChanged() {
        _hasPermission.value = scanner.hasAudioPermission()
        scope.launch { refresh() }
    }

    /** Re-lee MediaStore. Con [systemScan] también pide al sistema reindexar carpetas (botón "Escanear medios"). */
    suspend fun refresh(systemScan: Boolean = false) = scanMutex.withLock {
        _scanning.value = true
        try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                if (systemScan) withTimeoutOrNull(90_000) { runCatching { scanner.requestSystemScan() } }
                val t0 = System.nanoTime()
                val list = scanner.scan()
                _lastScanMs.value = (System.nanoTime() - t0) / 1_000_000
                Log.i(TAG, "escaneo: ${list.size} archivos en ${_lastScanMs.value} ms")
                if (list != raws.value) {
                    raws.value = list
                    db.media().replaceAll(list.map { it.toEntity() })
                }
            }
        } finally {
            _scanning.value = false
        }
    }

    fun refreshAsync(systemScan: Boolean = false) = scope.launch { refresh(systemScan) }

    // ---------- Acciones de "Revisar nombres" y edición ----------
    suspend fun editTrack(track: Track, title: String?, artist: String?, album: String?) {
        val t = title?.trim()?.takeIf { it.isNotEmpty() && it != track.title }
        val a = artist?.trim()?.takeIf { it.isNotEmpty() && it != track.artist }
        val al = album?.trim()?.takeIf { it.isNotEmpty() && it != track.album }
        if (t == null && a == null && al == null) return
        db.user().upsertEdit(TrackEdit(track.key, t ?: track.title, a ?: track.artist, al ?: track.album))
    }

    suspend fun resetEdit(track: Track) = db.user().deleteEdit(track.key)

    /** Une el artista [fromKey] dentro de [toKey]; opcionalmente con un nombre corregido. */
    suspend fun mergeArtists(fromKey: String, toKey: String, name: String? = null) {
        db.user().upsertAlias(ArtistAlias(fromKey, toKey, name))
        if (name != null) db.user().upsertAlias(ArtistAlias(toKey, toKey, name))
    }

    suspend fun renameArtist(key: String, name: String) = db.user().upsertAlias(ArtistAlias(key, key, name.trim()))

    suspend fun rejectMerge(a: String, b: String) = db.user().upsertReject(ArtistReject(a, b))

    suspend fun decideSplit(track: Track, split: Boolean) = db.user().upsertSplit(SplitDecision(track.key, split))

    suspend fun setArtist(track: Track, artist: String) =
        db.user().upsertEdit(TrackEdit(track.key, track.title, artist.trim(), track.album))

    suspend fun hide(track: Track) = db.user().upsertHidden(Hidden(track.key, false))
    suspend fun hideFolder(absFolderPathLower: String) = db.user().upsertHidden(Hidden(absFolderPathLower, true))
    suspend fun unhide(path: String) = db.user().deleteHidden(path)
    fun hiddenFlow() = db.user().hidden()

    suspend fun setPair(audio: Track, video: Track?) =
        db.user().upsertPair(PairOverride(audio.key, video?.key ?: ""))
    suspend fun resetPair(audio: Track) = db.user().deletePair(audio.key)

    fun rawCount(): Int = raws.value?.size ?: 0

    companion object {
        private const val TAG = "Library"
        fun artistKeyOf(name: String) = NameCleaner.artistKey(name)
    }
}

private fun MediaEntity.toRaw() = RawMedia(id, isVideo, path, displayName, folder, title, artist, album, albumId, genre, durationMs, size, dateAdded, mime, year)
private fun RawMedia.toEntity() = MediaEntity(id, isVideo, path, displayName, folder, title, artist, album, albumId, genre, durationMs, size, dateAdded, mime, year)

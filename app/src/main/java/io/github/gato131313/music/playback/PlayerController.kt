package io.github.gato131313.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.gato131313.music.AppGraph
import io.github.gato131313.music.data.model.Library
import io.github.gato131313.music.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PlayerUi(
    val connected: Boolean = false,
    /** Canción (o video suelto) actual. */
    val current: Track? = null,
    /** Archivo que suena: el video de la pareja si está en modo Video. */
    val playingFile: Track? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0,
    /** Cola en orden de reproducción (respeta aleatorio). Pares (índice real, pista). */
    val queue: List<Pair<Int, Track>> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
) {
    val isVideoMode: Boolean get() = playingFile != null && playingFile.isVideo
}

/** Lado de la interfaz: se conecta al servicio por MediaController. */
@OptIn(UnstableApi::class)
class PlayerController(private val ctx: Context, private val g: AppGraph) {
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set
    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state
    private var restoreTried = false

    init {
        g.scope.launch { g.library.library.collect { refresh() } }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            refresh()
            restoreIfEmpty()
        }, ContextCompat.getMainExecutor(ctx))
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private fun lib(): Library = g.library.library.value

    fun refresh() {
        val c = controller ?: return
        val lib = lib()
        val item = c.currentMediaItem
        val cur = item?.mediaId?.toLongOrNull()?.let { lib.byId[it] }
        val playingId = item?.mediaMetadata?.extras?.getLong(EXTRA_PLAYING_ID, -1L) ?: -1L
        val playing = if (playingId > 0 && playingId != cur?.id) lib.byId[playingId] else cur
        _state.value = PlayerUi(
            connected = true,
            current = cur,
            playingFile = playing,
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (cur?.durationMs ?: 0),
            queue = playbackOrder(c, lib),
            currentIndex = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            hasPrev = c.mediaItemCount > 0,
            hasNext = c.hasNextMediaItem(),
        )
    }

    private var cachedOrder: List<Pair<Int, Track>> = emptyList()
    private var cachedKey: Any? = null

    /** Cola completa en el orden en que va a sonar. */
    private fun playbackOrder(c: Player, lib: Library): List<Pair<Int, Track>> {
        val tl = c.currentTimeline
        val key = Triple(System.identityHashCode(tl), c.shuffleModeEnabled, System.identityHashCode(lib))
        if (key == cachedKey) return cachedOrder
        val out = ArrayList<Pair<Int, Track>>(tl.windowCount)
        if (!tl.isEmpty) {
            var i = tl.getFirstWindowIndex(c.shuffleModeEnabled)
            var guard = 0
            while (i != C.INDEX_UNSET && guard++ < tl.windowCount) {
                val id = c.getMediaItemAt(i).mediaId.toLongOrNull()
                lib.byId[id]?.let { out += i to it }
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            }
        }
        cachedKey = key
        cachedOrder = out
        return out
    }

    fun position(): Long = controller?.currentPosition ?: 0
    fun bufferedPosition(): Long = controller?.bufferedPosition ?: 0

    private fun restoreIfEmpty() {
        val c = controller ?: return
        if (restoreTried || c.mediaItemCount > 0) return
        restoreTried = true
        g.scope.launch {
            val lib = g.library.libraryOrNull.filterNotNull().first()
            val q = g.hub.loadQueue() ?: return@launch
            val items = q.ids.mapNotNull { id -> lib.byId[id]?.let { t -> t.toMediaItem(q.videoIds[id]?.let { lib.byId[it] }) } }
            if (items.isEmpty() || c.mediaItemCount > 0) return@launch
            c.setMediaItems(items, q.index.coerceIn(0, items.size - 1), q.positionMs)
            c.repeatMode = q.repeat
            c.shuffleModeEnabled = q.shuffle
            c.prepare()
        }
    }

    // ---------- Acciones ----------
    fun play(queue: List<Track>, index: Int, shuffle: Boolean = false) {
        val c = controller ?: run { connect(); return }
        if (queue.isEmpty()) return
        val preferVideo = g.settings.value.preferVideo
        val lib = lib()
        val items = queue.map { t -> t.toMediaItem(if (preferVideo && t.pairId != null && !t.isVideo) lib.byId[t.pairId] else null) }
        c.setMediaItems(items, index.coerceIn(0, items.size - 1), 0)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
        if (shuffle) shuffleFromCurrent()
    }

    /** Aleatorio que empieza por la canción actual y no repite hasta acabar la lista. */
    private fun shuffleFromCurrent() {
        g.hub.post {
            val p = g.hub.player ?: return@post
            val n = p.mediaItemCount
            if (n < 2) return@post
            val cur = p.currentMediaItemIndex
            val rest = (0 until n).filter { it != cur }.shuffled(Random(System.nanoTime()))
            p.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder((listOf(cur) + rest).toIntArray(), System.nanoTime()))
        }
    }

    fun shuffleAll() {
        val songs = lib().songs
        if (songs.isNotEmpty()) play(songs, songs.indices.random(), shuffle = true)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) { c.seekToDefaultPosition(0); c.play(); return }
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun next() = controller?.seekToNextMediaItem()
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0, c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        if (c.shuffleModeEnabled) shuffleFromCurrent()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playAt(realIndex: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(realIndex)
        c.play()
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(tracks, 0)
        val at = if (c.shuffleModeEnabled) c.currentMediaItemIndex + 1 else c.currentMediaItemIndex + 1
        c.addMediaItems(at.coerceAtMost(c.mediaItemCount), tracks.map { it.toMediaItem() })
        if (c.shuffleModeEnabled) placeAfterCurrentInShuffle(at, tracks.size)
    }

    /** En aleatorio, "Reproducir siguiente" debe sonar justo después de la actual. */
    private fun placeAfterCurrentInShuffle(start: Int, count: Int) {
        g.hub.post {
            val p = g.hub.player ?: return@post
            val tl = p.currentTimeline
            val order = ArrayList<Int>()
            var i = tl.getFirstWindowIndex(true)
            while (i != C.INDEX_UNSET) { order += i; i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true) }
            val inserted = (start until start + count).toSet()
            val base = order.filter { it !in inserted }.toMutableList()
            val pos = base.indexOf(p.currentMediaItemIndex) + 1
            base.addAll(pos, (start until start + count).toList())
            p.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(base.toIntArray(), System.nanoTime()))
        }
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(tracks, 0)
        c.addMediaItems(tracks.map { it.toMediaItem() })
    }

    fun removeAt(realIndex: Int) = controller?.removeMediaItem(realIndex)
    fun move(from: Int, to: Int) = controller?.moveMediaItem(from, to)
    fun clearQueue() {
        controller?.let { c -> c.stop(); c.clearMediaItems() }
    }

    /** Cambia entre Canción y Video de una pareja manteniendo la posición. */
    fun switchVideoMode(toVideo: Boolean) {
        val c = controller ?: return
        val cur = _state.value.current ?: return
        val video = cur.pairId?.let { lib().byId[it] } ?: return
        val pos = c.currentPosition
        val idx = c.currentMediaItemIndex
        val wasPlaying = c.playWhenReady
        val item: MediaItem = cur.toMediaItem(if (toVideo) video else null)
        c.replaceMediaItem(idx, item)
        c.seekTo(idx, pos)
        c.playWhenReady = wasPlaying
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    @Suppress("unused")
    private fun Timeline.isEmptyCompat() = isEmpty
}

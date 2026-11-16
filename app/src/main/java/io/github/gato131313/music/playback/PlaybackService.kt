package io.github.gato131313.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.gato131313.music.MainActivity
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.R
import io.github.gato131313.music.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@OptIn(UnstableApi::class)
fun buildPlayer(ctx: Context, handleFocus: Boolean): ExoPlayer {
    val hub = MusicApp.graph.hub
    val renderers = object : DefaultRenderersFactory(ctx) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(*hub.processorsFactory()))
                .build()
    }.setEnableDecoderFallback(true)
    return ExoPlayer.Builder(ctx, renderers)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), handleFocus)
        .setHandleAudioBecomingNoisy(handleFocus)
        .setWakeMode(if (handleFocus) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val g get() = MusicApp.graph

    // Conteo de reproducciones
    private var curTrack: Track? = null
    private var accMs = 0L
    private var counted = false
    private var lastTick = 0L
    private var lastSave = 0L

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer(this, handleFocus = true)
        val s = g.settings.value
        player.playbackParameters = PlaybackParameters(s.speed, semitonesToPitch(s.pitchSemis))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "player").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setBitmapLoader(CacheBitmapLoader(ArtBitmapLoader(this)))
            .setCallback(SessionCallback())
            .build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_u_mark) })
        g.hub.player = player
        player.addListener(listener)
        // Velocidad / tono desde Ajustes
        scope.launch {
            g.settings.flow.map { it.speed to it.pitchSemis }.distinctUntilChanged().collect { (sp, st) ->
                val pp = PlaybackParameters(sp, semitonesToPitch(st))
                player.playbackParameters = pp
                crossfader.tail?.playbackParameters = pp
            }
        }
        handler.post(ticker)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueue()
        flushListen()
        handler.removeCallbacksAndMessages(null)
        crossfader.release()
        g.hub.player = null
        session?.release()
        session = null
        player.removeListener(listener)
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun trackOf(item: MediaItem?): Track? {
        val id = item?.mediaId?.toLongOrNull() ?: return null
        return g.library.library.value.byId[id]
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val prev = curTrack
            if (prev != null && !counted && accMs in 1..10_000 && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && !crossfader.autoTransition) {
                scope.launch { g.user.recordSkip(prev) }
            }
            flushListen()
            crossfader.autoTransition = false
            curTrack = trackOf(mediaItem)
            accMs = 0
            counted = false
            saveQueue()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            lastTick = System.currentTimeMillis()
            if (!isPlaying) {
                saveQueue()
                crossfader.onPaused()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) g.hub.onPausedAtEnd()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.w(TAG, "error de reproducción", error)
            // Saltar archivos dañados en lugar de detenerse.
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    /** Cuenta como reproducción a los 30 s (o 80 % si la canción es corta), como en MUSIC web. */
    private fun countsAt(durMs: Long): Long {
        if (durMs <= 0) return 30_000
        return min(30_000L, maxOf(1_000L, min(durMs - 1_500L, (durMs * 0.8).toLong())))
    }

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (player.isPlaying) {
                accMs += (now - lastTick).coerceIn(0, 2_000)
                val t = curTrack ?: trackOf(player.currentMediaItem).also { curTrack = it }
                if (t != null && !counted && accMs >= countsAt(player.duration.takeIf { it != C.TIME_UNSET } ?: t.durationMs)) {
                    counted = true
                    val ms = accMs
                    scope.launch { g.user.recordPlay(t, ms) }
                    accMs = 0
                }
                if (now - lastSave > 15_000) saveQueue()
            }
            lastTick = now
            crossfader.tick()
            handler.postDelayed(this, if (crossfader.busy) 40L else 250L)
        }
    }

    /** Suma el tiempo escuchado después de contar la reproducción. */
    private fun flushListen() {
        val t = curTrack ?: return
        if (counted && accMs > 0) {
            val ms = accMs
            scope.launch { g.user.addListenTime(t, ms) }
        }
        accMs = 0
    }

    private fun saveQueue() {
        lastSave = System.currentTimeMillis()
        val n = player.mediaItemCount
        if (n == 0) return
        val ids = ArrayList<Long>(n)
        val videos = HashMap<Long, Long>()
        for (i in 0 until n) {
            val it = player.getMediaItemAt(i)
            val id = it.mediaId.toLongOrNull() ?: continue
            ids += id
            val playing = it.mediaMetadata.extras?.getLong(EXTRA_PLAYING_ID, id) ?: id
            if (playing != id) videos[id] = playing
        }
        g.hub.saveQueue(SavedQueue(ids, videos, player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode))
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val lib = g.library.library.value
            val out = mediaItems.mapNotNull { mi ->
                if (mi.localConfiguration != null) mi else {
                    val t = mi.mediaId.toLongOrNull()?.let { lib.byId[it] }
                    t?.toMediaItem()
                }
            }.toMutableList()
            return Futures.immediateFuture(out)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val f = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val lib = g.library.libraryOrNull.filterNotNull().first()
                val q = g.hub.loadQueue()
                if (q == null || q.ids.isEmpty()) {
                    f.setException(UnsupportedOperationException("sin cola"))
                    return@launch
                }
                val items = q.ids.mapNotNull { id -> lib.byId[id]?.let { t -> t.toMediaItem(q.videoIds[id]?.let { lib.byId[it] }) } }
                f.set(MediaSession.MediaItemsWithStartPosition(items, q.index.coerceIn(0, (items.size - 1).coerceAtLeast(0)), q.positionMs))
            }
            return f
        }
    }

    // ---------- Fusión entre canciones (dos reproductores) ----------
    private val crossfader = Crossfader()

    private inner class Crossfader {
        var tail: ExoPlayer? = null
        var autoTransition = false
        private var state = 0 // 0 inactivo, 1 armado, 2 fundiendo
        private var armedId: String? = null
        private var fadeStartPos = 0L
        private var fadeLen = 0L
        private var fadeT0 = 0L
        val busy get() = state != 0

        fun tick() {
            val secs = g.settings.value.crossfadeSec
            when (state) {
                0 -> {
                    if (secs <= 0 || !player.isPlaying) return
                    if (player.repeatMode == Player.REPEAT_MODE_ONE || !player.hasNextMediaItem()) return
                    val dur = player.duration
                    if (dur == C.TIME_UNSET || dur < 20_000) return
                    val cur = player.currentMediaItem ?: return
                    val next = player.getMediaItemAt(player.nextMediaItemIndex)
                    if (isVideo(cur) || isVideo(next)) return
                    val speed = player.playbackParameters.speed
                    val len = min(secs * 1000L, dur / 3)
                    if (len < 300) return
                    val start = dur - (len * speed).toLong()
                    val pos = player.currentPosition
                    if (pos >= start - (2_500 * speed) && pos < start - 150) arm(cur, start, len)
                }
                1 -> {
                    if (player.currentMediaItem?.mediaId != armedId || !player.isPlaying) return disarm()
                    if (player.currentPosition >= fadeStartPos - 25) begin()
                }
                2 -> {
                    val x = ((System.currentTimeMillis() - fadeT0).toFloat() / fadeLen).coerceIn(0f, 1f)
                    val s = x * x * (3 - 2 * x)
                    val out = cos(s * PI / 2).pow(0.75).toFloat()
                    val inn = sin(s * PI / 2).pow(0.75).toFloat()
                    tail?.volume = out
                    player.volume = inn
                    if (x >= 1f) finish()
                }
            }
        }

        private fun isVideo(mi: MediaItem) = mi.mediaMetadata.mediaType == androidx.media3.common.MediaMetadata.MEDIA_TYPE_VIDEO ||
            (mi.mediaMetadata.extras?.getLong(EXTRA_PLAYING_ID, -1L)?.let { it != -1L && it.toString() != mi.mediaId } ?: false)

        private fun arm(cur: MediaItem, start: Long, len: Long) {
            val t = tail ?: buildPlayer(this@PlaybackService, handleFocus = false).also { tail = it }
            t.playbackParameters = player.playbackParameters
            t.volume = 1f
            t.setMediaItem(cur, start)
            t.playWhenReady = false
            t.prepare()
            armedId = cur.mediaId
            fadeStartPos = start
            fadeLen = len
            state = 1
        }

        private fun begin() {
            val t = tail ?: return disarm()
            if (t.playbackState != Player.STATE_READY) return disarm()
            t.play()
            autoTransition = true
            player.volume = 0f
            player.seekToNextMediaItem()
            fadeT0 = System.currentTimeMillis()
            state = 2
        }

        private fun finish() {
            tail?.stop()
            tail?.clearMediaItems()
            player.volume = 1f
            state = 0
            armedId = null
        }

        private fun disarm() {
            if (state == 2) return finish()
            tail?.stop()
            state = 0
            armedId = null
        }

        fun onPaused() {
            if (state == 2) finish() else if (state == 1) disarm()
        }

        fun release() {
            tail?.release()
            tail = null
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        fun semitonesToPitch(st: Float): Float = 2.0.pow(st / 12.0).toFloat()
    }
}

package io.github.gato131313.music.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedQueue(
    val ids: List<Long> = emptyList(),
    /** id canción -> id del video que se estaba viendo (modo Video). */
    val videoIds: Map<Long, Long> = emptyMap(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

/** Cosas de reproducción compartidas dentro del proceso (servicio <-> interfaz). */
class PlaybackHub(private val ctx: Context, private val scope: CoroutineScope) {
    private val main = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }
    private val queueFile = File(ctx.filesDir, "queue.json")

    /** Procesadores de audio (efectos) para cada reproductor nuevo. Lo configura FxEngine. */
    @Volatile
    var processorsFactory: () -> Array<androidx.media3.common.audio.AudioProcessor> = { emptyArray() }

    /** Reproductor activo (lo registra el servicio). */
    @Volatile
    var player: ExoPlayer? = null
        internal set

    // ---------- Cola guardada ----------
    fun saveQueue(q: SavedQueue) {
        runCatching { queueFile.writeText(json.encodeToString(SavedQueue.serializer(), q)) }
    }

    fun loadQueue(): SavedQueue? = runCatching {
        if (queueFile.exists()) json.decodeFromString(SavedQueue.serializer(), queueFile.readText()) else null
    }.getOrNull()

    // ---------- Temporizador para dormir ----------
    private val _sleepEndsAt = MutableStateFlow<Long?>(null)
    val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt
    private val _sleepEndOfTrack = MutableStateFlow(false)
    val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack
    private var sleepJob: Job? = null

    fun startSleep(minutes: Int) {
        cancelSleep()
        val end = System.currentTimeMillis() + minutes * 60_000L
        _sleepEndsAt.value = end
        sleepJob = scope.launch {
            while (isActive && System.currentTimeMillis() < end - FADE_MS) delay(1000)
            fadeOutAndPause()
            _sleepEndsAt.value = null
        }
    }

    fun startSleepEndOfTrack() {
        cancelSleep()
        _sleepEndOfTrack.value = true
        player?.pauseAtEndOfMediaItems = true
    }

    /** Lo llama el servicio cuando el reproductor se pausó al terminar la canción. */
    fun onPausedAtEnd() {
        if (_sleepEndOfTrack.value) {
            _sleepEndOfTrack.value = false
            player?.pauseAtEndOfMediaItems = false
        }
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepEndsAt.value = null
        if (_sleepEndOfTrack.value) {
            _sleepEndOfTrack.value = false
            player?.pauseAtEndOfMediaItems = false
        }
    }

    private suspend fun fadeOutAndPause() {
        val p = player ?: return
        val start = p.volume
        val steps = 40
        for (i in 1..steps) {
            p.volume = start * (1f - i / steps.toFloat())
            delay(FADE_MS / steps)
        }
        p.pause()
        p.volume = start
    }

    fun post(r: () -> Unit) = main.post(r)

    companion object {
        const val FADE_MS = 8_000L
    }
}

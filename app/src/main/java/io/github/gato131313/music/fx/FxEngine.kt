package io.github.gato131313.music.fx

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
data class FxParams(
    /** Interruptor del ecualizador. */
    val eqOn: Boolean = true,
    val preset: String = "Normal",
    /** 10 bandas en dB (−15..+15). */
    val bands: List<Float> = List(10) { 0f },
    /** Graves 0..1 (hasta +12 dB en 90 Hz). */
    val bass: Float = 0f,
    /** Virtualizador / 3D 0..1. */
    val virtualizer: Float = 0f,
    val reverb: String = "Ninguna",
    /** Volumen extra 0..1 (hasta +12 dB con limitador). */
    val boost: Float = 0f,
    val normalize: Boolean = false,
)

object FxPresets {
    val FREQS = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    val LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val BUILT_IN: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Normal" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Pop" to listOf(-1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f, -1f),
        "Rock" to listOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 4f, 5f, 5f),
        "K-pop" to listOf(3f, 4f, 2f, 0f, -1f, 1f, 3f, 4f, 4f, 3f),
        "Bajos fuertes" to listOf(8f, 7f, 6f, 4f, 1f, 0f, 0f, 0f, 0f, 0f),
        "Voces" to listOf(-2f, -2f, -1f, 1f, 4f, 5f, 4f, 2f, 0f, -1f),
        "Reggaetón" to listOf(6f, 5f, 3f, 0f, -1f, 0f, 1f, 2f, 3f, 3f),
        "Regional" to listOf(2f, 3f, 2f, 1f, 2f, 3f, 3f, 2f, 1f, 0f),
        "Electrónica" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f),
        "Hip hop" to listOf(6f, 5f, 2f, 3f, -1f, -1f, 1f, 0f, 2f, 3f),
        "Clásica" to listOf(4f, 3f, 2f, 1f, 0f, 0f, -1f, -1f, 2f, 3f),
        "Jazz" to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
        "Acústica" to listOf(3f, 3f, 2f, 1f, 1f, 1f, 2f, 2f, 3f, 2f),
        "Agudos fuertes" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 7f, 8f),
        "Noche (suave)" to listOf(1f, 1f, 0f, 0f, -1f, -2f, -3f, -4f, -5f, -6f),
    )
    /** room, damp, wet */
    val REVERBS: LinkedHashMap<String, Triple<Double, Double, Double>> = linkedMapOf(
        "Ninguna" to Triple(0.0, 0.0, 0.0),
        "Habitación pequeña" to Triple(0.62, 0.45, 0.18),
        "Habitación mediana" to Triple(0.74, 0.4, 0.24),
        "Sala grande" to Triple(0.84, 0.3, 0.3),
        "Auditorio" to Triple(0.9, 0.25, 0.34),
        "Iglesia" to Triple(0.95, 0.2, 0.4),
        "Plato" to Triple(0.8, 0.12, 0.28),
    )
}

/** Datos para el anillo de barras y el fondo que pulsa (calculados del audio real). */
class VizFrame(val bands: FloatArray, val energy: Float, val beat: Float)

/** Lo que el procesador necesita del motor (interfaz para poder probarlo sin Android). */
interface FxHost {
    val params: FxParams
    val version: Int
    val vizActive: Boolean
    var viz: VizFrame
}

/** Motor de efectos compartido por todos los reproductores (principal y el de la fusión). */
@OptIn(UnstableApi::class)
class FxEngine(private val ctx: Context, @Suppress("unused") private val scope: CoroutineScope) : FxHost {
    @Volatile override var params: FxParams = FxParams()
        private set
    @Volatile override var version = 0
        private set
    /** La interfaz lo activa solo cuando muestra el anillo/fondo (ahorra batería). */
    @Volatile override var vizActive = false
    @Volatile override var viz: VizFrame = VizFrame(FloatArray(BANDS), 0f, 0f)

    private val _output = MutableStateFlow(detectOutput())
    /** "audifonos", "bluetooth" o "bocina". */
    val output: StateFlow<String> = _output

    fun apply(p: FxParams) {
        if (p == params) return
        params = p
        version++
    }

    fun newProcessor(): AudioProcessor = FxProcessor(this)

    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { _output.value = detectOutput() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { _output.value = detectOutput() }
        }, null)
    }

    fun detectOutput(): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO) +
            (if (android.os.Build.VERSION.SDK_INT >= 31) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER) else emptySet())
        val wired = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)
        return when {
            devs.any { it.type in bt } -> "bluetooth"
            devs.any { it.type in wired } -> "audifonos"
            else -> "bocina"
        }
    }

    fun systemVolume(): Float = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun setSystemVolume(f: Float) {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).toInt().coerceIn(0, max), 0)
    }

    companion object {
        const val BANDS = 48
    }
}

/**
 * Procesador PCM 16 bits: EQ 10 bandas, graves, 3D, reverberación, volumen extra, normalizador y limitador.
 * Siempre activo (para poder cambiar efectos en vivo sin reconfigurar el reproductor).
 */
@OptIn(UnstableApi::class)
class FxProcessor(private val engine: FxHost) : BaseAudioProcessor() {
    private var ch = 2
    private var fs = 44100
    private var eq: Array<Biquad> = emptyArray()
    private var bassF: Biquad = Biquad(2)
    private var reverb: Reverb? = null
    private var seenVersion = -1
    private var width = 0.0
    private var gainTarget = 1.0
    private var gain = 1.0
    private var limEnv = 0.0
    private var limGain = 1.0
    private var limRelease = 0.9995
    // normalizador
    private var ms = 0.02
    private var normGain = 1.0
    private var normA = 0.0
    // visualizador
    private val fftN = 1024
    private val fftBuf = DoubleArray(fftN)
    private var fftPos = 0
    private val re = DoubleArray(fftN)
    private val im = DoubleArray(fftN)
    private val bandsSmooth = FloatArray(FxEngine.BANDS)
    private var lowAvg = 0.0
    private var beat = 0f
    private var energy = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        ch = inputAudioFormat.channelCount
        fs = inputAudioFormat.sampleRate
        eq = Array(10) { Biquad(ch) }
        bassF = Biquad(ch)
        reverb = Reverb(ch, fs)
        seenVersion = -1
        limRelease = exp(-1.0 / (fs * 0.25))
        normA = 1 - exp(-1.0 / (fs * 3.0))
        return inputAudioFormat
    }

    private fun updateParams() {
        val p = engine.params
        seenVersion = engine.version
        val pre: Double
        if (p.eqOn) {
            for (i in 0 until 10) eq[i].peaking(fs.toDouble(), FxPresets.FREQS[i], 1.41, p.bands.getOrElse(i) { 0f }.toDouble())
            pre = -max(0.0, p.bands.maxOrNull()?.toDouble() ?: 0.0) * 0.55
        } else {
            eq.forEach { it.peaking(fs.toDouble(), 1000.0, 1.0, 0.0) }
            pre = 0.0
        }
        bassF.lowShelf(fs.toDouble(), 90.0, p.bass * 12.0)
        width = if (ch == 2) p.virtualizer * 1.6 else 0.0
        val (room, damp, wet) = FxPresets.REVERBS[p.reverb] ?: Triple(0.0, 0.0, 0.0)
        reverb?.let { it.room = room; it.damp = damp; it.wet = wet }
        gainTarget = 10.0.pow((pre - p.bass * 4.0 + p.boost * 12.0) / 20.0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (seenVersion != engine.version) updateParams()
        val out = replaceOutputBuffer(remaining)
        val p = engine.params
        val frames = remaining / (2 * ch)
        val doEq = p.eqOn && eq.any { !it.bypass }
        val doBass = !bassF.bypass
        val rv = reverb?.takeIf { it.wet > 0 }
        val norm = p.normalize
        val viz = engine.vizActive
        val gainStep = (gainTarget - gain) / max(1, frames)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (f in 0 until frames) {
            var a = inputBuffer.short.toDouble() / 32768.0
            var b = if (ch == 2) inputBuffer.short.toDouble() / 32768.0 else a
            if (doBass) { a = bassF.process(a, 0); if (ch == 2) b = bassF.process(b, 1) }
            if (doEq) {
                for (i in 0 until 10) {
                    val q = eq[i]
                    if (q.bypass) continue
                    a = q.process(a, 0)
                    if (ch == 2) b = q.process(b, 1)
                }
            }
            if (width > 0) {
                val m = (a + b) * 0.5
                val s = (a - b) * 0.5 * (1 + width)
                a = m + s; b = m - s
            }
            if (rv != null) { a = rv.process(a, 0); if (ch == 2) b = rv.process(b, 1) }
            gain += gainStep
            var g = gain
            if (norm) {
                val e = (a * a + b * b) * 0.5
                ms += normA * (e - ms)
                val rms = sqrt(ms)
                if (rms > 0.004) {
                    val want = (0.13 / rms).coerceIn(0.4, 2.8)
                    normGain += (want - normGain) * 0.00003
                }
                g *= normGain
            }
            a *= g; b *= g
            // Limitador (enlazado estéreo) para que nada sature.
            val peak = max(abs(a), abs(b))
            limEnv = if (peak > limEnv) peak else limEnv * limRelease + peak * (1 - limRelease)
            val want = if (limEnv > 0.96) 0.96 / limEnv else 1.0
            limGain = if (want < limGain) want else limGain + (want - limGain) * 0.0008
            a *= limGain; b *= limGain
            out.putShort((a.coerceIn(-1.0, 1.0) * 32767).toInt().toShort())
            if (ch == 2) out.putShort((b.coerceIn(-1.0, 1.0) * 32767).toInt().toShort())
            if (viz) {
                fftBuf[fftPos++] = (a + b) * 0.5
                if (fftPos == fftN) { analyze(); fftPos = 0 }
            }
        }
        out.flip()
    }

    private fun analyze() {
        for (i in 0 until fftN) {
            val w = 0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * i / (fftN - 1))
            re[i] = fftBuf[i] * w; im[i] = 0.0
        }
        Fft.magnitudes(re, im)
        val n = FxEngine.BANDS
        val bands = FloatArray(n)
        val maxBin = fftN / 2
        val fMin = 40.0
        val fMax = min(16000.0, fs / 2.0)
        var low = 0.0
        var total = 0.0
        for (k in 0 until n) {
            val f0 = fMin * (fMax / fMin).pow(k / n.toDouble())
            val f1 = fMin * (fMax / fMin).pow((k + 1) / n.toDouble())
            val b0 = (f0 / fs * fftN).toInt().coerceIn(1, maxBin - 1)
            val b1 = (f1 / fs * fftN).toInt().coerceIn(b0 + 1, maxBin)
            var s = 0.0
            for (bin in b0 until b1) s = max(s, re[bin])
            val db = 20 * ln(s + 1e-9) / ln(10.0)
            val v = ((db + 52) / 52).coerceIn(0.0, 1.0).toFloat()
            bands[k] = v
            bandsSmooth[k] = if (v > bandsSmooth[k]) v else bandsSmooth[k] * 0.82f + v * 0.18f
            if (k < n / 6) low += v
            total += v
        }
        low /= (n / 6)
        energy = (energy * 0.8f + (total / n).toFloat() * 0.2f)
        if (low > lowAvg * 1.25 && low > 0.35) beat = 1f else beat *= 0.82f
        lowAvg = lowAvg * 0.9 + low * 0.1
        engine.viz = VizFrame(bandsSmooth.copyOf(), energy, beat)
    }

    override fun onFlush() {
        eq.forEach { it.reset() }
        bassF.reset()
        reverb?.reset()
        limEnv = 0.0
        limGain = 1.0
    }

    override fun onReset() {
        onFlush()
        seenVersion = -1
    }
}

package io.github.gato131313.music.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

/** Filtro biquad (RBJ cookbook), forma directa transpuesta II. Un estado por canal. */
class Biquad(private val channels: Int) {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)
    var bypass = true
        private set

    fun peaking(fs: Double, f0: Double, q: Double, gainDb: Double) {
        if (kotlin.math.abs(gainDb) < 0.05 || f0 >= fs * 0.49) { bypass = true; return }
        bypass = false
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * f0 / fs
        val alpha = sin(w) / (2 * q)
        val cw = cos(w)
        val a0 = 1 + alpha / a
        b0 = (1 + alpha * a) / a0; b1 = -2 * cw / a0; b2 = (1 - alpha * a) / a0
        a1 = -2 * cw / a0; a2 = (1 - alpha / a) / a0
    }

    fun lowShelf(fs: Double, f0: Double, gainDb: Double, s: Double = 0.9) {
        if (gainDb < 0.05) { bypass = true; return }
        bypass = false
        val a = 10.0.pow(gainDb / 40.0)
        val w = 2 * PI * f0 / fs
        val cw = cos(w)
        val alpha = sin(w) / 2 * sqrt((a + 1 / a) * (1 / s - 1) + 2)
        val sa = 2 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cw + sa
        b0 = a * ((a + 1) - (a - 1) * cw + sa) / a0
        b1 = 2 * a * ((a - 1) - (a + 1) * cw) / a0
        b2 = a * ((a + 1) - (a - 1) * cw - sa) / a0
        a1 = -2 * ((a - 1) + (a + 1) * cw) / a0
        a2 = ((a + 1) + (a - 1) * cw - sa) / a0
    }

    fun reset() { z1.fill(0.0); z2.fill(0.0) }

    fun process(x: Double, ch: Int): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }
}

/** Reverberación tipo Freeverb (ligera): 4 peines + 2 pasa-todo por canal. */
class Reverb(private val channels: Int, fs: Int) {
    private val scale = fs / 44100.0
    private val combLens = intArrayOf(1116, 1188, 1277, 1356)
    private val apLens = intArrayOf(556, 441)
    private val combs = Array(channels) { ch -> combLens.map { DoubleArray(((it + ch * 23) * scale).toInt().coerceAtLeast(8)) } }
    private val combIdx = Array(channels) { IntArray(4) }
    private val combStore = Array(channels) { DoubleArray(4) }
    private val aps = Array(channels) { ch -> apLens.map { DoubleArray(((it + ch * 23) * scale).toInt().coerceAtLeast(8)) } }
    private val apIdx = Array(channels) { IntArray(2) }
    var room = 0.7; var damp = 0.3; var wet = 0.0

    fun reset() {
        combs.forEach { c -> c.forEach { it.fill(0.0) } }
        aps.forEach { c -> c.forEach { it.fill(0.0) } }
        combStore.forEach { it.fill(0.0) }
    }

    fun process(x: Double, ch: Int): Double {
        if (wet <= 0.0) return x
        val input = x * 0.2
        var out = 0.0
        val cs = combs[ch]
        for (i in 0 until 4) {
            val buf = cs[i]
            val idx = combIdx[ch][i]
            val y = buf[idx]
            combStore[ch][i] = y * (1 - damp) + combStore[ch][i] * damp
            buf[idx] = input + combStore[ch][i] * room
            combIdx[ch][i] = if (idx + 1 >= buf.size) 0 else idx + 1
            out += y
        }
        val ap = aps[ch]
        for (i in 0 until 2) {
            val buf = ap[i]
            val idx = apIdx[ch][i]
            val bo = buf[idx]
            buf[idx] = out + bo * 0.5
            out = bo - out
            apIdx[ch][i] = if (idx + 1 >= buf.size) 0 else idx + 1
        }
        return x * (1 - wet * 0.35) + out * wet
    }
}

/** FFT radix-2 en sitio (para el anillo de barras). */
object Fft {
    fun magnitudes(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t; val u = im[i]; im[i] = im[j]; im[j] = u }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar; im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar; im[i + k] += ai
                    val ncr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
        for (k in 0 until n / 2) re[k] = sqrt(re[k] * re[k] + im[k] * im[k])
    }
}

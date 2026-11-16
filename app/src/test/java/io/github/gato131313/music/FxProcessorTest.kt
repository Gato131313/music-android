package io.github.gato131313.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import io.github.gato131313.music.fx.FxHost
import io.github.gato131313.music.fx.FxParams
import io.github.gato131313.music.fx.FxPresets
import io.github.gato131313.music.fx.FxProcessor
import io.github.gato131313.music.fx.VizFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class FxProcessorTest {
    private class Host(override var params: FxParams) : FxHost {
        override var version = 1
        override var vizActive = true
        override var viz = VizFrame(FloatArray(1), 0f, 0f)
    }

    /** Pasa un seno por el procesador y devuelve el RMS de salida (dBFS) tras estabilizarse. */
    private fun rmsDb(params: FxParams, freq: Double, amp: Double = 0.25, fs: Int = 44100, host: Host = Host(params)): Double {
        val p = FxProcessor(host)
        p.configure(AudioProcessor.AudioFormat(fs, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        val frames = fs // 1 s, en bloques de 1024 como el reproductor real
        var sum = 0.0
        var n = 0
        var peak = 0
        var i = 0
        while (i < frames) {
            val len = minOf(1024, frames - i)
            val buf = ByteBuffer.allocateDirect(len * 4).order(ByteOrder.nativeOrder())
            for (k in 0 until len) {
                val v = (sin(2 * PI * freq * (i + k) / fs) * amp * 32767).toInt().toShort()
                buf.putShort(v); buf.putShort(v)
            }
            buf.flip()
            p.queueInput(buf)
            val out = p.output.order(ByteOrder.nativeOrder())
            while (out.remaining() >= 2) {
                val s = out.short.toInt()
                if (i > frames / 2) { sum += s.toDouble() * s; n++ } // segunda mitad (estable)
                peak = maxOf(peak, kotlin.math.abs(s))
            }
            i += len
        }
        assertTrue("satura: pico $peak", peak <= 32767)
        return 20 * log10(sqrt(sum / n) / 32768.0)
    }

    @Test fun normalNoCambiaElSonido() {
        val a = rmsDb(FxParams(), 1000.0)
        val expected = 20 * log10(0.25 / sqrt(2.0))
        assertEquals(expected, a, 0.3)
    }

    @Test fun ecualizadorSubeYBajaBandas() {
        val flat = rmsDb(FxParams(), 1000.0)
        val up = rmsDb(FxParams(bands = List(10) { if (it == 5) 9f else 0f }), 1000.0)
        val down = rmsDb(FxParams(bands = List(10) { if (it == 5) -9f else 0f }), 1000.0)
        println("1 kHz: plano=$flat +9dB=$up -9dB=$down")
        assertTrue("subir 1k debe sonar más fuerte (con pre-ganancia): $up vs $flat", up > flat + 3)
        assertTrue("bajar 1k debe sonar más bajo: $down vs $flat", down < flat - 7)
        // Una banda no debe afectar mucho a otra frecuencia lejana
        val far = rmsDb(FxParams(bands = List(10) { if (it == 0) 12f else 0f }), 4000.0)
        assertEquals(flat - 12 * 0.55, far, 1.5)
    }

    @Test fun gravesRefuerzanLasBajas() {
        val bassOff = rmsDb(FxParams(), 60.0)
        val bassOn = rmsDb(FxParams(bass = 1f), 60.0)
        val highOn = rmsDb(FxParams(bass = 1f), 5000.0)
        val highOff = rmsDb(FxParams(), 5000.0)
        println("graves 60Hz: $bassOff -> $bassOn ; 5kHz: $highOff -> $highOn")
        assertTrue(bassOn - bassOff > 5)
        assertTrue(highOn - highOff < 0)
    }

    @Test fun volumeExtraConLimitadorNoSatura() {
        val loud = rmsDb(FxParams(boost = 1f, bands = FxPresets.BUILT_IN["Bajos fuertes"]!!), 62.0, amp = 0.9)
        println("volumen extra al máximo con seno fuerte: $loud dBFS")
        assertTrue(loud < 0.0)
    }

    @Test fun reverbYTresDNoRompen() {
        val r = rmsDb(FxParams(reverb = "Sala grande", virtualizer = 1f), 440.0)
        assertTrue(r.isFinite() && r > -40)
    }

    @Test fun visualizadorDetectaEnergia() {
        val host = Host(FxParams())
        rmsDb(host.params, 120.0, host = host)
        assertTrue("energía ${host.viz.energy}", host.viz.energy > 0.05f)
        val maxBand = host.viz.bands.indices.maxBy { host.viz.bands[it] }
        assertTrue("la banda más fuerte debe ser grave: $maxBand", maxBand < 16)
    }
}

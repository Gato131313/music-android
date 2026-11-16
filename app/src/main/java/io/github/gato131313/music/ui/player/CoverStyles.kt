package io.github.gato131313.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.fx.VizFrame
import io.github.gato131313.music.ui.components.ArtImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Reloj de animación + último cuadro del analizador. Activa el análisis solo mientras se ve. */
class VizClock {
    var t by mutableFloatStateOf(0f)
    var frame by mutableLongStateOf(0L)
    var viz: VizFrame = VizFrame(FloatArray(48), 0f, 0f)
    /** Bandas suavizadas para dibujar. */
    val bands = FloatArray(48)
    var pulse = 0f
    var energy = 0f
}

@Composable
fun rememberVizClock(playing: Boolean): VizClock {
    val fx = MusicApp.graph.fx
    val clock = remember { VizClock() }
    DisposableEffect(Unit) {
        fx.vizActive = true
        onDispose { fx.vizActive = false }
    }
    LaunchedEffect(playing) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val v = fx.viz
                clock.viz = v
                for (i in clock.bands.indices) {
                    val target = if (playing) v.bands.getOrElse(i) { 0f } else 0f
                    val cur = clock.bands[i]
                    clock.bands[i] = if (target > cur) cur + (target - cur) * 0.55f else cur + (target - cur) * 0.12f
                }
                clock.pulse = if (playing && v.beat > clock.pulse) v.beat else clock.pulse * kotlin.math.exp(-dt * 6.5f)
                clock.energy += ((if (playing) v.energy else 0f) - clock.energy) * 0.1f
                if (playing) clock.t += dt
                clock.frame = now
            }
        }
    }
    return clock
}

/** a) Disco circular que gira, con anillo de barras al ritmo real del audio. */
@Composable
fun DiscCover(track: Track?, playing: Boolean, palette: CoverPalette, clock: VizClock) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 460.dp)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") clock.frame
                val c = center
                val r0 = size.minDimension * 0.355f
                val maxLen = size.minDimension * 0.13f
                val n = 120
                for (i in 0 until n) {
                    // espejo: graves arriba y abajo, agudos a los lados
                    val half = n / 2
                    val k = if (i < half) i else n - 1 - i
                    val band = clock.bands[(k * clock.bands.size / half).coerceIn(0, clock.bands.size - 1)]
                    val len = maxLen * (0.08f + band * 0.92f) * (1f + 0.15f * clock.pulse)
                    val ang = (i.toFloat() / n) * 2 * PI.toFloat() - PI.toFloat() / 2
                    val dx = cos(ang); val dy = sin(ang)
                    val start = Offset(c.x + dx * (r0 + 6.dp.toPx()), c.y + dy * (r0 + 6.dp.toPx()))
                    val end = Offset(start.x + dx * len, start.y + dy * len)
                    drawLine(Color.White.copy(alpha = 0.55f + 0.45f * band), start, end, 3.2.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(palette.glow.copy(alpha = 0.18f + 0.25f * clock.pulse), r0 + 4.dp.toPx())
            }
            val discSize = side * 0.70f
            Box(
                Modifier.size(discSize).graphicsLayer {
                    rotationZ = (clock.t * 18f) % 360f
                    val s = 1f + 0.02f * clock.pulse
                    scaleX = s; scaleY = s
                },
            ) {
                ArtImage(track, Modifier.fillMaxSize(), CircleShape, 96.dp)
            }
        }
    }
}

/** b) Cuadrada como MUSIC; el fondo pulsa con la energía (lo dibuja LiveBackdrop). */
@Composable
fun SquareCover(track: Track?, playing: Boolean, clock: VizClock?) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp)
        val base = if (playing) 1f else 0.86f
        Box(
            Modifier.size(side).graphicsLayer {
                @Suppress("UNUSED_EXPRESSION") clock?.frame
                val s = base * (1f + 0.025f * (clock?.pulse ?: 0f))
                scaleX = s; scaleY = s
                shadowElevation = 40f; shape = RoundedCornerShape(14.dp); clip = false
            },
        ) { ArtImage(track, Modifier.fillMaxSize(), RoundedCornerShape(14.dp), 96.dp) }
    }
}

/** c) Aurora (nuevo): la portada flota sobre ondas de luz de graves, voces y agudos con chispas en cada golpe. */
@Composable
fun AuroraCover(track: Track?, playing: Boolean, palette: CoverPalette, clock: VizClock) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 460.dp)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") clock.frame
                val b = clock.bands
                fun avg(from: Int, to: Int): Float { var s = 0f; for (i in from until to) s += b[i]; return s / (to - from) }
                val levels = floatArrayOf(avg(0, 8), avg(12, 28), avg(30, 46))
                val cs = palette.colors
                for (layer in 0 until 3) {
                    val col = cs[layer % cs.size]
                    val amp = size.height * (0.04f + 0.16f * levels[layer]) * (1f + 0.3f * clock.pulse)
                    val yMid = size.height * (0.5f + (layer - 1) * 0.12f)
                    val path = Path()
                    val steps = 48
                    val ph = clock.t * (0.8f + layer * 0.45f) + layer * 2f
                    path.moveTo(0f, yMid)
                    for (i in 0..steps) {
                        val x = size.width * i / steps
                        val u = i / steps.toFloat()
                        path.lineTo(x, yMid + amp * sin(u * 2.6f * PI.toFloat() + ph) * (0.6f + 0.4f * sin(u * 7f + ph * 1.7f)))
                    }
                    for (i in steps downTo 0) {
                        val x = size.width * i / steps
                        val u = i / steps.toFloat()
                        path.lineTo(x, yMid + amp * 0.35f + amp * 0.8f * sin(u * 2.6f * PI.toFloat() + ph + 0.6f))
                    }
                    path.close()
                    drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = 0.55f + 0.35f * levels[layer]), Color.Transparent), yMid - amp * 1.5f, yMid + amp * 2f))
                }
                // chispas en los golpes
                val sparks = 26
                for (i in 0 until sparks) {
                    val a = i * 2.39996f + clock.t * 0.35f
                    val rr = size.minDimension * (0.3f + 0.2f * ((i * 37) % 11) / 11f) * (1f + 0.25f * clock.pulse)
                    val p = Offset(center.x + cos(a) * rr, center.y + sin(a) * rr * 0.7f)
                    drawCircle(Color.White.copy(alpha = (0.15f + 0.7f * clock.pulse) * (0.4f + 0.6f * ((i * 13) % 7) / 7f)), 1.6.dp.toPx() + 2.dp.toPx() * clock.pulse, p)
                }
            }
            val s = side * 0.62f
            Box(
                Modifier.size(s).graphicsLayer {
                    val wob = clock.t
                    rotationX = 6f * sin(wob * 0.7f)
                    rotationY = 8f * cos(wob * 0.5f)
                    translationY = -10.dp.toPx() * sin(wob * 0.9f)
                    val sc = (if (playing) 1f else 0.9f) * (1f + 0.03f * clock.pulse)
                    scaleX = sc; scaleY = sc
                    shadowElevation = 50f; shape = RoundedCornerShape(22.dp); clip = false
                    cameraDistance = 12f * density
                },
            ) { ArtImage(track, Modifier.fillMaxSize(), RoundedCornerShape(22.dp), 80.dp) }
        }
    }
}

@Suppress("unused")
private fun unused() = min(1, 2)

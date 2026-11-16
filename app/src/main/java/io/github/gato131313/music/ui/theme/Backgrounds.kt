package io.github.gato131313.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Fondo de la app según el estilo. Las ilustraciones se dibujan una sola vez (drawWithCache). */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    when {
        c.style == "paisaje" -> Landscape(c.background, modifier)
        c.style == "neon" -> Box(
            modifier.fillMaxSize().drawWithCache {
                val base = Brush.verticalGradient(listOf(Color(0xFF07070F), Color(0xFF0D0A24), Color(0xFF07070F)))
                val g1 = Brush.radialGradient(listOf(c.accent.copy(alpha = 0.28f), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.2f), size.width * 0.8f)
                val g2 = Brush.radialGradient(listOf(Color(0x55FF2D92), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.75f), size.width * 0.9f)
                onDrawBehind { drawRect(base); drawRect(g1); drawRect(g2) }
            },
        )
        else -> Box(modifier.fillMaxSize().background(c.bg))
    }
}

private class Palette(val sky: List<Color>, val sun: Color?, val layers: List<Color>, val trees: Color?, val stars: Boolean = false, val water: Color? = null)

private fun paletteFor(id: String) = when (id) {
    "bosque" -> Palette(
        listOf(Color(0xFF1E3B4A), Color(0xFF4F7C74), Color(0xFFB9C9A3)), Color(0x55FFF6D5),
        listOf(Color(0xFF3D5F5B), Color(0xFF2C4A47), Color(0xFF1C3432), Color(0xFF12221F)), Color(0xFF0B1714),
    )
    "noche" -> Palette(
        listOf(Color(0xFF05061A), Color(0xFF14183F), Color(0xFF2B2A63)), Color(0xFFF2EFD9),
        listOf(Color(0xFF232552), Color(0xFF191B3E), Color(0xFF10122B), Color(0xFF090A1C)), Color(0xFF06071A), stars = true,
    )
    "oceano" -> Palette(
        listOf(Color(0xFF0D3B66), Color(0xFF3D7EA6), Color(0xFFF4D6A8)), Color(0xFFFFE3A3),
        listOf(Color(0xFF2E5E83), Color(0xFF1D4A6E)), null, water = Color(0xFF0E3557),
    )
    "aurora" -> Palette(
        listOf(Color(0xFF020814), Color(0xFF0B2233), Color(0xFF123B45)), null,
        listOf(Color(0xFF173840), Color(0xFF102A31), Color(0xFF0A1D22), Color(0xFF051014)), Color(0xFF030B0D), stars = true,
    )
    "desierto" -> Palette(
        listOf(Color(0xFF3B2A5A), Color(0xFFD9707A), Color(0xFFFFC48A)), Color(0xFFFFE6A8),
        listOf(Color(0xFFC77A5E), Color(0xFFA25A47), Color(0xFF7A4035), Color(0xFF4F2A27)), null,
    )
    else -> Palette( // atardecer (como su app anterior)
        listOf(Color(0xFF3A3557), Color(0xFF7B4E6E), Color(0xFFE08A5B)), Color(0x66FFD7A0),
        listOf(Color(0xFF6A4A68), Color(0xFF52395A), Color(0xFF3C2C4C), Color(0xFF2A2140)), Color(0xFF1E1830),
    )
}

@Composable
private fun Landscape(id: String, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val p = paletteFor(id)
            val w = size.width
            val h = size.height
            val horizon = h * 0.62f
            val sky = Brush.verticalGradient(p.sky, 0f, horizon)
            val rnd = Random(id.hashCode())
            val stars = if (p.stars) List(140) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * horizon * 0.9f) to (0.6f + rnd.nextFloat() * 1.8f) } else emptyList()
            val mountains = p.layers.mapIndexed { i, col ->
                val base = horizon - h * 0.12f + i * h * 0.075f
                val amp = h * (0.07f - i * 0.008f)
                col to ridge(w, h, base, amp, seed = i * 13 + id.length, freq = 1.4f + i * 0.9f)
            }
            val trees = p.trees?.let { col ->
                val path = Path()
                val r2 = Random(7 + id.length)
                var x = -20f
                while (x < w + 40f) {
                    val th = h * (0.10f + r2.nextFloat() * 0.13f)
                    val baseY = h * (0.82f + r2.nextFloat() * 0.06f)
                    pine(path, x, baseY, th)
                    x += w * (0.05f + r2.nextFloat() * 0.09f)
                }
                col to path
            }
            val aurora = if (id == "aurora") listOf(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x6633FFB0), Color.Transparent), h * 0.08f, h * 0.45f),
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x447B61FF), Color.Transparent), h * 0.02f, h * 0.35f),
            ) else emptyList()
            val auroraPaths = aurora.mapIndexed { i, b -> b to ribbon(w, h * (0.16f + i * 0.08f), h * 0.12f, i) }
            val shade = Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000), Color(0x99000000)), h * 0.4f, h)
            onDrawBehind {
                drawRect(sky)
                stars.forEach { (o, r) -> drawCircle(Color.White.copy(alpha = 0.55f), r, o) }
                auroraPaths.forEach { (b, path) -> drawPath(path, b) }
                p.sun?.let { sun ->
                    val c = Offset(w * 0.68f, horizon - h * 0.2f)
                    drawCircle(Brush.radialGradient(listOf(sun.copy(alpha = 0.45f), Color.Transparent), c, w * 0.45f), w * 0.45f, c)
                    drawCircle(sun.copy(alpha = (sun.alpha * 1.6f).coerceAtMost(1f)), w * 0.09f, c)
                }
                if (p.water != null) {
                    mountains.forEach { (col, path) -> drawPath(path, col) }
                    drawRect(Brush.verticalGradient(listOf(p.water.copy(alpha = 0.9f), Color(0xFF061A2C)), horizon, h), Offset(0f, horizon), Size(w, h - horizon))
                    for (i in 0 until 26) {
                        val y = horizon + (h - horizon) * (i / 26f)
                        drawLine(Color.White.copy(alpha = 0.05f + 0.04f * (i % 3)), Offset(w * 0.45f - i * 6f, y), Offset(w * 0.9f + i * 3f, y), 2f)
                    }
                } else {
                    mountains.forEach { (col, path) -> drawPath(path, col) }
                }
                trees?.let { (col, path) -> drawPath(path, col) }
                drawRect(shade)
            }
        },
    )
}

private fun ridge(w: Float, h: Float, base: Float, amp: Float, seed: Int, freq: Float): Path {
    val path = Path()
    path.moveTo(0f, h)
    val steps = 60
    val ph1 = seed * 0.7f
    val ph2 = seed * 1.3f
    for (i in 0..steps) {
        val x = w * i / steps
        val t = i / steps.toFloat()
        val y = base - amp * (0.6f * sin((t * freq * 2 * PI + ph1)).toFloat() + 0.35f * sin((t * freq * 5.3 * PI + ph2)).toFloat() + 0.15f * sin((t * 17 * PI + seed)).toFloat())
        path.lineTo(x, y)
    }
    path.lineTo(w, h)
    path.close()
    return path
}

private fun pine(path: Path, x: Float, baseY: Float, height: Float) {
    val tiers = 4
    val wBase = height * 0.42f
    for (t in 0 until tiers) {
        val top = baseY - height + t * height * 0.18f
        val bottom = baseY - height * 0.25f * (tiers - 1 - t) / (tiers - 1) - height * 0.06f
        val half = wBase * (0.45f + 0.55f * (t + 1) / tiers) / 2
        path.moveTo(x, top)
        path.lineTo(x - half, bottom)
        path.lineTo(x + half, bottom)
        path.close()
    }
    path.addRect(androidx.compose.ui.geometry.Rect(x - height * 0.025f, baseY - height * 0.1f, x + height * 0.025f, baseY + 400f))
}

private fun ribbon(w: Float, y: Float, amp: Float, seed: Int): Path {
    val p = Path()
    val steps = 40
    p.moveTo(0f, y)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        p.lineTo(w * t, y + amp * sin(t * 2.4 * PI + seed).toFloat() * 0.6f)
    }
    for (i in steps downTo 0) {
        val t = i / steps.toFloat()
        p.lineTo(w * t, y + amp * 1.6f + amp * sin(t * 2.4 * PI + seed + 0.7).toFloat() * 0.5f)
    }
    p.close()
    return p
}

@Suppress("unused")
private fun DrawScope.noop() = Unit

package io.github.gato131313.music.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.gato131313.music.data.art.ArtRequest
import io.github.gato131313.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Immutable
data class CoverPalette(val colors: List<Color>, val base: Color, val glow: Color, val gray: Boolean) {
    val primary: Color get() = colors.first()

    companion object {
        fun fallback(seed: String): CoverPalette {
            val h = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
            val cs = listOf(0f, 38f, -30f, 70f).map { Color.hsl((h + it + 360) % 360, 0.7f, 0.5f) }
            return CoverPalette(cs, Color.hsl(h, 0.6f, 0.12f), Color.hsl(h, 0.9f, 0.58f), false)
        }
    }
}

private val cache = object : LinkedHashMap<Long, CoverPalette>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CoverPalette>?) = size > 120
}

/** Colores dominantes de la portada (como coverColors() de MUSIC web). */
fun paletteOf(bmp: Bitmap): CoverPalette {
    val small = Bitmap.createScaledBitmap(bmp.copy(Bitmap.Config.ARGB_8888, false), 28, 28, true)
    val buckets = DoubleArray(18)
    val sumS = DoubleArray(18)
    val sumL = DoubleArray(18)
    val hueX = DoubleArray(18)
    val hueY = DoubleArray(18)
    var total = 0.0
    var grayL = 0.0
    var grayN = 0
    val hsl = FloatArray(3)
    for (y in 0 until 28) for (x in 0 until 28) {
        val p = small.getPixel(x, y)
        androidx.core.graphics.ColorUtils.colorToHSL(p, hsl)
        val (h, s, l) = Triple(hsl[0], hsl[1], hsl[2])
        grayL += l; grayN++
        if (s < 0.18f || l < 0.1f || l > 0.92f) continue
        val w = s * (1 - abs(l - 0.5f))
        val b = ((h / 20f).toInt()).coerceIn(0, 17)
        buckets[b] += w
        sumS[b] += s * w
        sumL[b] += l * w
        hueX[b] += kotlin.math.cos(Math.toRadians(h.toDouble())) * w
        hueY[b] += kotlin.math.sin(Math.toRadians(h.toDouble())) * w
        total += w
    }
    if (total < 1e-3) {
        val l = (grayL / grayN.coerceAtLeast(1)).toFloat()
        val cs = listOf(0.42f, 0.3f, 0.55f, 0.22f).map { Color.hsl(0f, 0f, (it + l * 0.2f).coerceIn(0.1f, 0.8f)) }
        return CoverPalette(cs, Color(0xFF151515), Color(0xFF9A9A9A), true)
    }
    val order = buckets.indices.sortedByDescending { buckets[it] }.filter { buckets[it] / total >= 0.02 }.take(5)
    val colors = order.map { b ->
        val hue = ((Math.toDegrees(kotlin.math.atan2(hueY[b], hueX[b])) + 360) % 360).toFloat()
        val s = ((sumS[b] / buckets[b]) * 1.25).toFloat().coerceIn(0.55f, 0.95f)
        val l = (sumL[b] / buckets[b]).toFloat().coerceIn(0.36f, 0.58f)
        Triple(hue, s, l)
    }.toMutableList()
    if (colors.size == 1) {
        val (h, s, l) = colors[0]
        listOf(26f, -24f, 44f, -40f).forEach { d -> colors += Triple((h + d + 360) % 360, s, l) }
    }
    val cs = colors.map { (h, s, l) -> Color.hsl(h, s, l) }
    val (h0, s0, _) = colors[0]
    return CoverPalette(cs, Color.hsl(h0, (s0 * 0.8f).coerceAtMost(1f), 0.11f), Color.hsl(h0, maxOf(s0, 0.88f), 0.58f), false)
}

@Composable
fun rememberCoverPalette(track: Track?): CoverPalette {
    val ctx = LocalContext.current
    val key = track?.id ?: -1L
    val state by produceState(cache[key] ?: CoverPalette.fallback(track?.title ?: ""), key) {
        if (track == null) return@produceState
        cache[key]?.let { value = it; return@produceState }
        val p = withContext(Dispatchers.Default) {
            val req = ImageRequest.Builder(ctx).data(ArtRequest.of(track)).size(96).allowHardware(false).build()
            val res = ctx.imageLoader.execute(req)
            val bmp = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            bmp?.let { runCatching { paletteOf(it) }.getOrNull() }
        } ?: CoverPalette.fallback(track.title)
        cache[key] = p
        value = p
    }
    return state
}

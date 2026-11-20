package io.github.gato131313.music.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.palette.graphics.Palette
import io.github.gato131313.music.MainActivity
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.R
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.playback.ArtProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

/** Lo que muestra el widget (se arma una vez por actualización; nada se refresca cada segundo). */
data class WidgetState(
    val hasTrack: Boolean,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val cover: Bitmap?,
    /** Fondo ya pintado (estilo + color de la portada + opacidad). */
    val bg: Bitmap,
    val accent: Int,
    val posMs: Long,
    val durMs: Long,
    val shuffle: Boolean,
    val repeat: Int,
    val favorite: Boolean,
)

/**
 * Widget de la pantalla de inicio (Jetpack Glance), se agranda/achica arrastrando las orillas:
 *  - Chico (2x1–3x1): portada redondeada + play/pausa + siguiente.
 *  - Mediano (4x1–4x2): portada, título, artista, barra de progreso y anterior/play/siguiente.
 *  - Grande (4x3+): portada grande, título, artista, progreso con tiempos y todos los controles.
 * Estilos (Ajustes): "portada" (portada difuminada), "cristal" (color de la portada) y "minimo" (oscuro).
 */
class MusicWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(110.dp, 48.dp)
        val WIDE = DpSize(250.dp, 48.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 200.dp)

        suspend fun refresh(ctx: Context) = runCatching { MusicWidget().updateAll(ctx) }

        /** Arma el estado (también lo usan las pruebas para dibujar cada tamaño y estilo). */
        suspend fun load(ctx: Context, styleOverride: String? = null): WidgetState {
            val g = MusicApp.graph
            data class P(val id: Long?, val playing: Boolean, val pos: Long, val dur: Long, val shuffle: Boolean, val repeat: Int)
            val p = withContext(Dispatchers.Main) {
                val pl = g.hub.player
                if (pl != null && pl.currentMediaItem != null) {
                    P(pl.currentMediaItem?.mediaId?.toLongOrNull(), pl.isPlaying, pl.currentPosition, pl.duration.takeIf { it > 0 } ?: 0, pl.shuffleModeEnabled, pl.repeatMode)
                } else {
                    // Sin música sonando: la última canción (play la continúa)
                    val q = g.hub.loadQueue()
                    P(q?.ids?.getOrNull(q.index), false, q?.positionMs ?: 0, 0, q?.shuffle ?: false, q?.repeat ?: 0)
                }
            }
            val lib = withTimeoutOrNull(4000) { g.library.libraryOrNull.filterNotNull().first() }
            val t: Track? = p.id?.let { lib?.byId?.get(it) }
            val s = g.settings.value
            val style = styleOverride ?: s.widgetStyle
            return withContext(Dispatchers.IO) {
                val cover = t?.let { ArtProvider.get(ctx, it, lib, 360) }
                val swatch = cover?.let { c -> runCatching { Palette.from(c).maximumColorCount(12).generate() }.getOrNull() }
                val base = swatch?.let { it.getVibrantColor(it.getDominantColor(0xFF3A2A4A.toInt())) } ?: 0xFF3A2A4A.toInt()
                val bg = Backgrounds.render(style, cover, base, s.widgetOpacity.coerceIn(0.3f, 1f))
                WidgetState(
                    hasTrack = t != null,
                    title = t?.title ?: "MUSIC",
                    artist = t?.displayArtist ?: "Toca para abrir",
                    playing = p.playing,
                    cover = cover,
                    bg = bg,
                    accent = Backgrounds.accentFor(style, base),
                    posMs = p.pos,
                    durMs = if (p.dur > 0) p.dur else t?.durationMs ?: 0,
                    shuffle = p.shuffle,
                    repeat = p.repeat,
                    favorite = t?.let { g.user.isFavorite(it.key) } ?: false,
                )
            }
        }
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = load(context)
        provideContent { WidgetContent(context, s) }
    }
}

private val WHITE = ColorProvider(Color.White)
private val WHITE2 = ColorProvider(Color(0xCCFFFFFF))
private val WHITE3 = ColorProvider(Color(0x99FFFFFF))

@Composable
fun WidgetContent(ctx: Context, s: WidgetState) {
    val size = LocalSize.current
    val radius = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius) else GlanceModifier.cornerRadius(22.dp)
    val open = actionStartActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().then(radius).clickable(open)) {
        Image(ImageProvider(s.bg), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        when {
            size.height >= MusicWidget.LARGE.height -> Large(ctx, s)
            size.height >= MusicWidget.MEDIUM.height -> Medium(ctx, s, twoRows = true)
            size.width >= MusicWidget.WIDE.width -> Medium(ctx, s, twoRows = false)
            else -> Small(ctx, s)
        }
    }
}

@Composable
private fun Cover(ctx: Context, s: WidgetState, sizeDp: Int, corner: Int) {
    val toPlayer = actionStartActivity(
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "player")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
    val m = GlanceModifier.size(sizeDp.dp).cornerRadius(corner.dp).clickable(toPlayer)
    if (s.cover != null) Image(ImageProvider(s.cover), "Abrir reproductor", m, contentScale = ContentScale.Crop)
    else Box(m.then(GlanceModifier.size(sizeDp.dp)), contentAlignment = Alignment.Center) { Image(ImageProvider(R.drawable.ic_u_mark), "MUSIC", GlanceModifier.size((sizeDp / 2).dp)) }
}

@Composable
private fun Small(ctx: Context, s: WidgetState) {
    val size = LocalSize.current
    val cover = min(size.height.value - 10, size.width.value - 84).toInt().coerceIn(30, 64)
    val btn = if (size.width.value < 150) 34 else 40
    Row(GlanceModifier.fillMaxSize().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(ctx, s, cover, 12)
        Spacer(GlanceModifier.defaultWeight())
        PlayPause(ctx, s, btn)
        Spacer(GlanceModifier.width(2.dp))
        MediaBtn(ctx, R.drawable.ic_w_next, "Siguiente", KeyEvent.KEYCODE_MEDIA_NEXT, btn - 4)
    }
}

@Composable
private fun Medium(ctx: Context, s: WidgetState, twoRows: Boolean) {
    val h = LocalSize.current.height.value
    val cover = if (twoRows) (h - 20).toInt().coerceIn(56, 96) else (h - 12).toInt().coerceIn(36, 60)
    Row(GlanceModifier.fillMaxSize().padding(if (twoRows) 10.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(ctx, s, cover, if (twoRows) 16 else 12)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(s.title, maxLines = 1, style = TextStyle(color = WHITE, fontSize = if (twoRows) 16.sp else 14.sp, fontWeight = FontWeight.Bold))
            Text(s.artist, maxLines = 1, style = TextStyle(color = WHITE2, fontSize = if (twoRows) 13.sp else 12.sp))
            if (twoRows) {
                Spacer(GlanceModifier.height(6.dp))
                Progress(s)
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MediaBtn(ctx, R.drawable.ic_w_prev, "Anterior", KeyEvent.KEYCODE_MEDIA_PREVIOUS, 34)
                    Spacer(GlanceModifier.width(10.dp))
                    PlayPause(ctx, s, 40)
                    Spacer(GlanceModifier.width(10.dp))
                    MediaBtn(ctx, R.drawable.ic_w_next, "Siguiente", KeyEvent.KEYCODE_MEDIA_NEXT, 34)
                }
            } else {
                Spacer(GlanceModifier.height(3.dp))
                Progress(s)
            }
        }
        if (!twoRows) {
            Spacer(GlanceModifier.width(6.dp))
            MediaBtn(ctx, R.drawable.ic_w_prev, "Anterior", KeyEvent.KEYCODE_MEDIA_PREVIOUS, 32)
            PlayPause(ctx, s, 38)
            MediaBtn(ctx, R.drawable.ic_w_next, "Siguiente", KeyEvent.KEYCODE_MEDIA_NEXT, 32)
        }
    }
}

@Composable
private fun Large(ctx: Context, s: WidgetState) {
    val size = LocalSize.current
    val tall = size.height.value >= 280
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        if (tall) {
            // Portada grande arriba
            val cover = min(size.width.value - 24, size.height.value - 24 - 44 - 26 - 50 - 12).toInt().coerceIn(80, 300)
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Cover(ctx, s, cover, 20)
            }
            Spacer(GlanceModifier.height(8.dp))
            TitleRow(s)
        } else {
            // Portada a la izquierda, título, artista y "me gusta" a la derecha
            val cover = (size.height.value - 24 - 26 - 50 - 8).toInt().coerceIn(56, 140)
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Cover(ctx, s, cover, 18)
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) { TitleRow(s) }
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Progress(s)
        Chrono(s)
        Row(GlanceModifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Toggle(if (s.shuffle) R.drawable.ic_n_shuffle_on else R.drawable.ic_n_shuffle, if (s.shuffle) "Aleatorio activado" else "Aleatorio", WidgetAction.SHUFFLE)
            Spacer(GlanceModifier.defaultWeight())
            MediaBtn(ctx, R.drawable.ic_w_prev, "Anterior", KeyEvent.KEYCODE_MEDIA_PREVIOUS, 40)
            Spacer(GlanceModifier.width(6.dp))
            PlayPause(ctx, s, 48)
            Spacer(GlanceModifier.width(6.dp))
            MediaBtn(ctx, R.drawable.ic_w_next, "Siguiente", KeyEvent.KEYCODE_MEDIA_NEXT, 40)
            Spacer(GlanceModifier.defaultWeight())
            Toggle(
                when (s.repeat) { 1 -> R.drawable.ic_w_repeat_one; 2 -> R.drawable.ic_w_repeat_on; else -> R.drawable.ic_w_repeat },
                when (s.repeat) { 1 -> "Repetir una"; 2 -> "Repetir todo"; else -> "Repetir" }, WidgetAction.REPEAT,
            )
        }
    }
}

@Composable
private fun TitleRow(s: WidgetState) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(s.title, maxLines = 2, style = TextStyle(color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Text(s.artist, maxLines = 1, style = TextStyle(color = WHITE2, fontSize = 13.sp))
        }
        Toggle(if (s.favorite) R.drawable.ic_n_heart_on else R.drawable.ic_n_heart, if (s.favorite) "Quitar de me gusta" else "Me gusta", WidgetAction.LIKE)
    }
}

@Composable
private fun Progress(s: WidgetState) {
    val f = if (s.durMs > 0) (s.posMs.toFloat() / s.durMs).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = f,
        modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
        color = ColorProvider(Color(s.accent)),
        backgroundColor = ColorProvider(Color(0x33FFFFFF)),
    )
}

/** Tiempo transcurrido: lo cuenta el propio sistema (Chronometer), sin despertar la app cada segundo. */
@Composable
private fun Chrono(s: WidgetState) {
    val rv = RemoteViews(MusicApp.graph.app.packageName, R.layout.widget_chrono).apply {
        setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - s.posMs, "%s / " + fmt(s.durMs), s.playing)
    }
    AndroidRemoteViews(rv)
}

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
private fun PlayPause(ctx: Context, s: WidgetState, sizeDp: Int) {
    if (s.playing) MediaBtn(ctx, R.drawable.ic_w_pause, "Pausa", KeyEvent.KEYCODE_MEDIA_PAUSE, sizeDp, filled = true)
    else MediaBtn(ctx, R.drawable.ic_w_play, "Reproducir", KeyEvent.KEYCODE_MEDIA_PLAY, sizeDp, filled = true)
}

@Composable
private fun MediaBtn(ctx: Context, icon: Int, desc: String, key: Int, sizeDp: Int, filled: Boolean = false) {
    // Botón multimedia: si la app está cerrada, Android la abre y continúa la última canción
    val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        .setComponent(ComponentName(ctx, androidx.media3.session.MediaButtonReceiver::class.java))
        .setData(Uri.parse("music://widget/$key"))
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, key))
    Box(
        GlanceModifier.size(sizeDp.dp).cornerRadius((sizeDp / 2).dp)
            .then(if (filled) GlanceModifier.background(ImageProvider(R.drawable.widget_btn_bg)) else GlanceModifier)
            .clickable(actionSendBroadcast(intent)),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(icon), desc, GlanceModifier.size((sizeDp * 0.56f).toInt().dp)) }
}

@Composable
private fun Toggle(icon: Int, desc: String, what: String) {
    Box(
        GlanceModifier.size(36.dp).cornerRadius(18.dp).clickable(actionRunCallback<WidgetAction>(actionParametersOf(WidgetAction.KEY to what))),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(icon), desc, GlanceModifier.size(22.dp)) }
}


/** Aleatorio, repetir y "me gusta" desde el widget. */
class WidgetAction : ActionCallback {
    companion object {
        val KEY = ActionParameters.Key<String>("w")
        const val SHUFFLE = "shuffle"
        const val REPEAT = "repeat"
        const val LIKE = "like"
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val g = MusicApp.graph
        when (parameters[KEY]) {
            SHUFFLE -> withContext(Dispatchers.Main) { g.hub.player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
            REPEAT -> withContext(Dispatchers.Main) { g.hub.player?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
            LIKE -> {
                val id = withContext(Dispatchers.Main) { g.hub.player?.currentMediaItem?.mediaId?.toLongOrNull() } ?: g.hub.loadQueue()?.let { q -> q.ids.getOrNull(q.index) }
                val lib = withTimeoutOrNull(4000) { g.library.libraryOrNull.filterNotNull().first() }
                id?.let { lib?.byId?.get(it) }?.let { g.user.toggleFavorite(it) }
            }
        }
        MusicWidget.refresh(context)
    }
}

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}

/** Fondos del widget pintados como imagen (degradado tipo cristal, portada difuminada u oscuro). */
object Backgrounds {
    private const val W = 400
    private const val H = 300

    fun accentFor(style: String, base: Int): Int =
        if (style == "minimo") 0xFFFA2D48.toInt() else lighten(base)

    /** Color legible para texto blanco: oscurece si la portada es muy clara. */
    private fun legible(c: Int, maxV: Float = 0.42f): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[2] = min(hsv[2], maxV)
        hsv[1] = min(1f, hsv[1] * 1.05f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun lighten(c: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[2] = max(hsv[2], 0.85f)
        hsv[1] = min(hsv[1], 0.7f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

    fun render(style: String, cover: Bitmap?, base: Int, opacity: Float): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (style) {
            "portada" -> {
                if (cover != null) {
                    // Difuminado barato: achicar mucho y volver a agrandar con filtro
                    val tiny = Bitmap.createScaledBitmap(cover, 12, 12, true)
                    val mid = Bitmap.createScaledBitmap(tiny, 48, 36, true)
                    p.alpha = (opacity * 255).toInt()
                    cv.drawBitmap(mid, Rect(0, 0, mid.width, mid.height), RectF(0f, 0f, W.toFloat(), H.toFloat()), p)
                    p.alpha = 255
                } else {
                    cv.drawColor(withAlpha(legible(base), opacity))
                }
                // Velo oscuro para que el texto siempre se lea
                p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), withAlpha(0xFF000000.toInt(), 0.30f * opacity), withAlpha(0xFF000000.toInt(), 0.55f * opacity), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            }
            "minimo" -> {
                p.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), withAlpha(0xFF1A1A1F.toInt(), opacity), withAlpha(0xFF0E0E11.toInt(), opacity), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            }
            else -> { // cristal
                val a = legible(base, 0.46f)
                val b = legible(base, 0.20f)
                p.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), withAlpha(a, opacity), withAlpha(b, opacity), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
                // Brillo suave tipo cristal arriba a la izquierda
                p.shader = RadialGradient(W * 0.15f, H * 0.0f, W * 0.8f, withAlpha(0xFFFFFFFF.toInt(), 0.16f * opacity), 0x00FFFFFF, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
                p.shader = null
                p.color = withAlpha(0xFFFFFFFF.toInt(), 0.10f * opacity)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f
                cv.drawRect(1f, 1f, W - 1f, H - 1f, p)
            }
        }
        return bmp
    }
}

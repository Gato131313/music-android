package io.github.gato131313.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.playback.PlayerUi
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.formatDuration
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Posición actual que se refresca sola mientras se ve la pantalla. */
@Composable
fun rememberPosition(intervalMs: Long = 250): Long {
    val pc = MusicApp.graph.player
    var pos by remember { mutableLongStateOf(pc.position()) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = pc.position()
            delay(intervalMs)
        }
    }
    return pos
}

data class PlayerCallbacks(
    val onClose: () -> Unit,
    val onEffects: () -> Unit,
    val onMenu: (Track) -> Unit,
    val onLyrics: () -> Unit = {},
)

@Composable
fun PlayerScreen(cb: PlayerCallbacks) {
    val g = MusicApp.graph
    val ui by g.player.state.collectAsState()
    val settings by g.settings.flow.collectAsState()
    val favs by g.user.favorites.collectAsState()
    val sleepAt by g.hub.sleepEndsAt.collectAsState()
    val sleepEnd by g.hub.sleepEndOfTrack.collectAsState()
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val track = ui.current
    val palette = rememberCoverPalette(track)
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(g.settings.value.showLyricsDefault) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val clock = rememberVizClock(ui.isPlaying)
    BackHandler { if (showQueue) showQueue = false else cb.onClose() }

    Box(
        Modifier.fillMaxSize().offset { IntOffset(0, dragY.roundToInt().coerceAtLeast(0)) }.background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { if (dragY > 220) cb.onClose(); dragY = 0f }, onDragCancel = { dragY = 0f }) { _, d -> dragY += d }
            },
    ) {
        if (ui.isVideoMode || track?.isVideo == true) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            LiveBackdrop(palette, ui.isPlaying && settings.style != "solido", Modifier.fillMaxSize()) { (clock.energy * 0.7f + clock.pulse * 0.5f).coerceAtMost(1f) }
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            // Arriba: cerrar, título/artista, efectos y cola
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = cb.onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(track?.title ?: "Nada sonando", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.displayArtist ?: "", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = cb.onEffects) { Icon(Icons.Rounded.GraphicEq, "Efectos de sonido", tint = Color.White) }
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Cola", tint = if (showQueue) palette.glow else Color.White)
                }
            }
            // Centro: portada / video / cola
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(if (showQueue) 1 else if (showLyrics && track != null && !ui.isVideoMode && !track.isVideo) 2 else 0, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "centro") { q ->
                    when (q) {
                        1 -> QueuePanel(ui, onPlay = { g.player.playAt(it) })
                        2 -> LyricsPanel(track!!, palette.glow)
                        else -> Stage(ui, track, palette, settings.coverStyle, clock)
                    }
                }
            }
            // Fila: favorito, tono/velocidad, repetir, temporizador, menú
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val fav = track != null && track.key in favs
                IconButton(onClick = { track?.let { t -> scope.launch { g.user.toggleFavorite(t) } } }) {
                    Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Quitar de favoritos" else "Agregar a favoritos", tint = if (fav) c.accent else Color.White)
                }
                IconButton(onClick = { showSpeed = true }) { Icon(Icons.Rounded.Tune, "Tono y velocidad", tint = Color.White) }
                IconButton(onClick = { g.player.cycleRepeat() }) {
                    Icon(
                        if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        when (ui.repeat) { Player.REPEAT_MODE_ONE -> "Repetir una"; Player.REPEAT_MODE_ALL -> "Repetir todo"; else -> "Repetir: no" },
                        tint = if (ui.repeat == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.55f) else palette.glow,
                    )
                }
                IconButton(onClick = { showLyrics = !showLyrics; showQueue = false }) {
                    Icon(Icons.Rounded.Lyrics, if (showLyrics) "Ocultar letra" else "Letra", tint = if (showLyrics) palette.glow else Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (track?.hasVideo == true) VideoSwitch(ui.isVideoMode) { g.player.switchVideoMode(it) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Rounded.Alarm, "Temporizador para dormir", tint = if (sleepAt != null || sleepEnd) palette.glow else Color.White)
                }
                IconButton(onClick = { track?.let(cb.onMenu) }) { Icon(Icons.Rounded.MoreVert, "Más opciones", tint = Color.White) }
            }
            // Progreso
            ProgressBar(ui, settings.speed, palette.glow)
            // Controles
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                CtrlButton(Icons.Rounded.Shuffle, if (ui.shuffle) "Aleatorio activado" else "Aleatorio", 26.dp, tint = if (ui.shuffle) palette.glow else Color.White.copy(alpha = 0.7f)) { g.player.toggleShuffle() }
                CtrlButton(Icons.Rounded.Replay10, "Atrasar 10 segundos", 28.dp) { g.player.seekBy(-10_000) }
                CtrlButton(Icons.Rounded.SkipPrevious, "Anterior", 40.dp) { g.player.previous() }
                Box(
                    Modifier.size(70.dp).clip(CircleShape).background(Color.White).clickable { g.player.playPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.isPlaying) "Pausa" else "Reproducir", tint = Color.Black, modifier = Modifier.size(42.dp))
                }
                CtrlButton(Icons.Rounded.SkipNext, "Siguiente", 40.dp) { g.player.next() }
                CtrlButton(Icons.Rounded.Forward10, "Adelantar 10 segundos", 28.dp) { g.player.seekBy(10_000) }
                CtrlButton(Icons.Rounded.Tune, "Ecualizador", 26.dp) { cb.onEffects() }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (showSpeed) SpeedPitchSheet { showSpeed = false }
    if (showSleep) SleepTimerDialog { showSleep = false }
}

@Composable
private fun CtrlButton(icon: ImageVector, desc: String, size: Dp, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(size + 18.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size))
    }
}

/** Switch Canción / Video (solo si hay pareja). */
@Composable
fun VideoSwitch(video: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (video) 1f else 0f, tween(380), label = "switch")
    Box(Modifier.width(112.dp).height(38.dp).clip(RoundedCornerShape(19.dp)).background(Color.White.copy(alpha = 0.13f)).padding(3.dp)) {
        Box(
            Modifier.width(53.dp).height(32.dp).graphicsLayer { translationX = x * 53.dp.toPx() }
                .clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.3f)),
        )
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().clickable { onChange(false) }, contentAlignment = Alignment.Center) {
                Text("Canción", color = Color.White.copy(alpha = if (video) 0.55f else 1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.weight(1f).fillMaxSize().clickable { onChange(true) }, contentAlignment = Alignment.Center) {
                Text("Video", color = Color.White.copy(alpha = if (video) 1f else 0.55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProgressBar(ui: PlayerUi, speed: Float, accent: Color) {
    val pc = MusicApp.graph.player
    val pos = rememberPosition()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = ui.durationMs.coerceAtLeast(1)
    val frac = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(30.dp).pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging?.let { pc.seekTo((it * dur).toLong()) }; dragging = null },
                    onDragCancel = { dragging = null },
                ) { ch, _ -> dragging = (ch.position.x / size.width).coerceIn(0f, 1f) }
            }.pointerInput(dur) {
                detectTapGestures { pc.seekTo(((it.x / size.width).coerceIn(0f, 1f) * dur).toLong()) }
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            val h = if (dragging != null) 8.dp else 4.dp
            Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.24f)))
            Box(Modifier.fillMaxWidth(frac).height(h).clip(RoundedCornerShape(4.dp)).background(accent))
            Box(Modifier.offset(x = maxWidth * frac - 8.dp).size(16.dp).clip(CircleShape).background(Color.White))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(((dragging ?: -1f).takeIf { it >= 0 }?.times(dur))?.toLong() ?: pos), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (speed != 1f) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.22f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Speed, null, tint = accent, modifier = Modifier.size(13.dp))
                    Text(" " + fmtSpeed(speed), color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(formatDuration(ui.durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

fun fmtSpeed(s: Float): String {
    val r = (s * 100).roundToInt()
    val txt = when {
        r % 100 == 0 -> "${r / 100}.0"
        r % 10 == 0 -> "${r / 100}.${(r % 100) / 10}"
        else -> "${r / 100}.${(r % 100).toString().padStart(2, '0')}"
    }
    return txt + "x"
}

/** Portada o video en el centro. */
@OptIn(UnstableApi::class)
@Composable
private fun Stage(ui: PlayerUi, track: Track?, palette: CoverPalette, style: String, clock: VizClock) {
    if (ui.isVideoMode || track?.isVideo == true) {
        val controller = MusicApp.graph.player.controller
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = controller
                }
            },
            update = { it.player = controller },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        return
    }
    when (style) {
        "cuadrada" -> SquareCover(track, ui.isPlaying, clock)
        "aurora" -> AuroraCover(track, ui.isPlaying, palette, clock)
        else -> DiscCover(track, ui.isPlaying, palette, clock)
    }
}

@Composable
fun CoverArt(track: Track?, playing: Boolean, palette: CoverPalette, style: String) {
    val scale by animateFloatAsState(if (playing) 1f else 0.86f, tween(450), label = "cover")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 420.dp)
        Box(Modifier.size(side).scale(scale), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSizeShadow(palette.base))
            ArtImage(track, Modifier.fillMaxSize(), RoundedCornerShape(14.dp), 96.dp)
        }
    }
}

private fun Modifier.matchParentSizeShadow(@Suppress("UNUSED_PARAMETER") base: Color): Modifier =
    this.fillMaxSize().graphicsLayer { shadowElevation = 40f; shape = RoundedCornerShape(14.dp); clip = false }

/** Fondo vivo con los colores de la portada (manchas que orbitan). Quieto si está en pausa. */
@Composable
fun LiveBackdrop(palette: CoverPalette, animate: Boolean, modifier: Modifier = Modifier, energy: () -> Float = { 0f }) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += ((now - last) / 1e9f) * (0.35f + 1.2f * energy())
                last = now
            }
        }
    }
    val cs = palette.colors
    Canvas(modifier) {
        drawRect(palette.base)
        val r = size.maxDimension
        for (i in cs.indices) {
            val a = t * (0.21f + i * 0.07f) + i * 1.7f
            val cx = size.width * (0.5f + 0.38f * cos(a))
            val cy = size.height * (0.38f + 0.3f * sin(a * 1.13f))
            val rad = r * (0.48f + (i % 3) * 0.08f) * (1f + 0.25f * energy())
            drawCircle(Brush.radialGradient(listOf(cs[i].copy(alpha = 0.85f), Color.Transparent), Offset(cx, cy), rad), rad, Offset(cx, cy))
        }
        val sa = -t * 0.17f
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), Offset(size.width * (0.5f + 0.4f * cos(sa)), size.height * (0.6f + 0.25f * sin(sa))), r * 0.5f),
            r * 0.5f, Offset(size.width * (0.5f + 0.4f * cos(sa)), size.height * (0.6f + 0.25f * sin(sa))),
        )
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.6f))))
    }
}

@Suppress("unused")
private val keep = Modifier.widthIn(max = 500.dp)

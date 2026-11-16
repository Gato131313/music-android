package io.github.gato131313.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import io.github.gato131313.music.data.art.ArtRequest
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.ui.theme.AppBackground
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Espacio inferior ocupado por el mini reproductor + barra de navegación. */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

/** Panel translúcido sin desenfoque (para tarjetas dentro de listas: barato al hacer scroll). */
@Composable
fun Modifier.glassPanel(shape: Shape = RoundedCornerShape(18.dp), strong: Boolean = false): Modifier {
    val c = LocalAppColors.current
    val fill = if (c.illustrated || !c.isDark) c.glass else c.bg2.copy(alpha = if (strong) 1f else 0.72f)
    return this.clip(shape).background(fill).border(0.5.dp, Brush.linearGradient(listOf(c.glassSpec.copy(alpha = c.glassSpec.alpha * 0.6f), c.glassEdge, c.glassEdge)), shape)
}

/** Cristal líquido real: desenfoca lo que hay detrás ([state] = fuente). */
@Composable
fun Modifier.glass(state: HazeState, shape: Shape = RoundedCornerShape(24.dp), tint: Color? = null): Modifier {
    val c = LocalAppColors.current
    val t = tint ?: c.glass
    val base = this.clip(shape)
    val blurred = if (c.blur) base.hazeChild(
        state,
        HazeStyle(backgroundColor = Color.Transparent, tint = HazeTint(t), blurRadius = 26.dp, noiseFactor = 0.06f, fallbackTint = HazeTint(t.copy(alpha = (t.alpha + 0.35f).coerceAtMost(0.96f)))),
    ) else base.background(t.copy(alpha = 1f))
    return blurred.border(0.6.dp, Brush.linearGradient(listOf(c.glassSpec, c.glassEdge, c.glassEdge.copy(alpha = c.glassEdge.alpha * 0.5f))), shape)
}

/** Color de respaldo estable por canción (para las que no tienen portada). */
fun placeholderBrush(seed: String): Brush {
    val h = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    val a = Color.hsv(h, 0.55f, 0.62f)
    val b = Color.hsv((h + 38f) % 360f, 0.6f, 0.38f)
    return Brush.linearGradient(listOf(a, b))
}

@Composable
fun ArtImage(track: Track?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp), iconSize: Dp = 22.dp) {
    Box(modifier.clip(shape).background(placeholderBrush(track?.title ?: "")), contentAlignment = Alignment.Center) {
        Icon(if (track?.isVideo == true) Icons.Rounded.OndemandVideo else Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(iconSize))
        if (track != null) {
            AsyncImage(model = ArtRequest.of(track), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onMenu).padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else ArtImage(track, Modifier.size(50.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, color = if (playing) c.accent else c.text, fontSize = 16.sp,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.hasVideo) {
                    Icon(Icons.Rounded.Videocam, "Tiene video", tint = c.accent, modifier = Modifier.size(15.dp).padding(end = 3.dp))
                }
                Text(
                    subtitle ?: "${track.displayArtist} · ${formatDuration(track.durationMs)}", color = c.text2, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Opciones", tint = c.text2) }
    }
}

/** Barra superior de cristal para pantallas internas. */
@Composable
fun GlassScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    navIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val c = LocalAppColors.current
    val haze = remember { HazeState() }
    var barH by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().haze(haze)) {
            AppBackground()
            content(PaddingValues(top = with(density) { barH.toDp() }, bottom = LocalBottomInset.current))
        }
        Row(
            Modifier.fillMaxWidth().glass(haze, RoundedCornerShape(0.dp)).windowInsetsPadding(WindowInsets.statusBars)
                .height(58.dp).onSizeChanged { barH = it.height + 0 }.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(navIcon, "Atrás", tint = c.text) } else Spacer(Modifier.width(12.dp))
            if (titleContent != null) titleContent() else Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
            actions()
        }
    }
}

/** Barra de desplazamiento rápido con burbuja de letra (para listas de miles). */
@Composable
fun BoxScope.FastScroller(state: LazyListState, count: Int, letterAt: (Int) -> String, topPad: Dp = 0.dp, bottomPad: Dp = 0.dp) {
    if (count < 30) return
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var trackH by remember { mutableFloatStateOf(1f) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val first = state.firstVisibleItemIndex
    val shown = if (dragging) fraction else (first.toFloat() / (count - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val thumbH = 44.dp
    val density = LocalDensity.current
    val thumbPx = with(density) { thumbH.toPx() }
    Box(
        Modifier.align(Alignment.TopEnd).padding(top = topPad + 8.dp, bottom = bottomPad + 8.dp).fillMaxHeight().width(28.dp)
            .onSizeChanged { trackH = it.height.toFloat() }
            .pointerInput(count) {
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        fraction = (o.y / trackH).coerceIn(0f, 1f)
                        scope.launch { state.scrollToItem((fraction * (count - 1)).roundToInt()) }
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    fraction = (change.position.y / trackH).coerceIn(0f, 1f)
                    scope.launch { state.scrollToItem((fraction * (count - 1)).roundToInt()) }
                }
            },
    ) {
        val y = ((trackH - thumbPx) * shown).roundToInt()
        AnimatedVisibility(state.isScrollInProgress || dragging, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.offset { IntOffset(0, y) }) {
            Box(Modifier.padding(start = 12.dp).size(5.dp, thumbH).clip(CircleShape).background(c.text2))
        }
        if (dragging) {
            Box(
                Modifier.offset { IntOffset(with(density) { (-72).dp.roundToPx() }, (y - with(density) { 14.dp.roundToPx() })) }
                    .size(64.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(letterAt((fraction * (count - 1)).roundToInt()), color = c.onAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun firstLetter(s: String): String {
    val ch = s.trim().firstOrNull { it.isLetterOrDigit() } ?: return "#"
    if (ch.isDigit()) return "#"
    val n = java.text.Normalizer.normalize(ch.toString(), java.text.Normalizer.Form.NFD).first()
    return n.uppercase()
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalAppColors.current
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = c.text3, modifier = Modifier.size(56.dp))
        Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = c.text2, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val c = LocalAppColors.current
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

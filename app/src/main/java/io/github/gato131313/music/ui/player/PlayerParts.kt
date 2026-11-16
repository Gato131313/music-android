package io.github.gato131313.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.playback.PlayerUi
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.formatDuration
import io.github.gato131313.music.ui.components.glass
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Cola editable: tocar para saltar, arrastrar el asa para mover, X para quitar. */
@Composable
fun QueuePanel(ui: PlayerUi, onPlay: (Int) -> Unit) {
    val pc = MusicApp.graph.player
    val q = ui.queue
    val curPos = q.indexOfFirst { it.first == ui.currentIndex }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = curPos)
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val itemH = 64.dp
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A continuación · ${q.size}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (ui.shuffle) Text("Aleatorio", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            itemsIndexed(q, key = { i, it -> "${it.first}_${it.second.id}_$i" }) { i, (real, t) ->
                val isCur = real == ui.currentIndex
                val dragging = dragFrom == i
                Row(
                    Modifier.fillMaxWidth().height(itemH)
                        .graphicsLayer { if (dragging) { translationY = dragDy; shadowElevation = 12f } }
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCur) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onPlay(real) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(t, Modifier.size(46.dp), RoundedCornerShape(6.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.title, color = Color.White, fontSize = 15.sp, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.displayArtist + " · " + formatDuration(t.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
                    }
                    if (!isCur) IconButton(onClick = { pc.removeAt(real) }) { Icon(Icons.Rounded.Close, "Quitar de la cola", tint = Color.White.copy(alpha = 0.6f)) }
                    if (!ui.shuffle) {
                        Icon(
                            Icons.Rounded.DragHandle, "Mover", tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(40.dp).padding(8.dp).pointerInput(i, q.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragFrom = i; dragDy = 0f },
                                    onDragEnd = {
                                        val steps = (dragDy / itemH.toPx()).roundToInt()
                                        val to = (i + steps).coerceIn(0, q.size - 1)
                                        if (to != i) pc.move(q[i].first, q[to].first)
                                        dragFrom = -1; dragDy = 0f
                                    },
                                    onDragCancel = { dragFrom = -1; dragDy = 0f },
                                ) { ch, d -> ch.consume(); dragDy += d.y }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Tono (semitonos) y velocidad (0.5x–2x) independientes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedPitchSheet(onDismiss: () -> Unit) {
    val g = MusicApp.graph
    val s by g.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    val c = LocalAppColors.current
    fun set(speed: Float = s.speed, pitch: Float = s.pitchSemis) = scope.launch {
        g.settings.update { it.copy(speed = (speed * 100).roundToInt() / 100f, pitchSemis = pitch) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF2B2D36), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            val track = Brush.horizontalGradient(listOf(Color(0xFF2EE04A), Color(0xFF9A7A12), Color(0xFFE5232B)))
            Text("Tono: ${if (s.pitchSemis > 0) "+" else ""}${s.pitchSemis.roundToInt()}", color = Color.White, fontSize = 17.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientSlider(s.pitchSemis, -12f, 12f, 23, track, Modifier.weight(1f)) { set(pitch = it.roundToInt().toFloat()) }
                IconButton(onClick = { set(pitch = 0f) }) { Icon(Icons.Rounded.Refresh, "Restablecer tono", tint = Color.White) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Velocidad: ${fmtSpeed(s.speed).removeSuffix("x")} x", color = Color.White, fontSize = 17.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientSlider(s.speed, 0.5f, 2f, 29, track, Modifier.weight(1f)) { set(speed = it) }
                IconButton(onClick = { set(speed = 1f) }) { Icon(Icons.Rounded.Refresh, "Restablecer velocidad", tint = Color.White) }
            }
            Row(Modifier.fillMaxWidth().padding(end = 48.dp)) {
                Text("Lento", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("Normal", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("Rápido", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(0.5f, 1f, 1.5f, 2f).forEach { v ->
                    val sel = kotlin.math.abs(s.speed - v) < 0.001f
                    Box(
                        Modifier.width(66.dp).height(50.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (sel) c.accent else Color.White.copy(alpha = 0.08f)).clickable { set(speed = v) },
                        contentAlignment = Alignment.Center,
                    ) { Text(fmtSpeed(v), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun GradientSlider(value: Float, min: Float, max: Float, steps: Int, brush: Brush, modifier: Modifier, onChange: (Float) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(brush))
        Slider(
            value, onChange, valueRange = min..max, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF5A70), activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** Temporizador para dormir: por tiempo o al terminar la canción. */
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val hub = MusicApp.graph.hub
    val endsAt by hub.sleepEndsAt.collectAsState()
    val endTrack by hub.sleepEndOfTrack.collectAsState()
    val c = LocalAppColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) { while (endsAt != null) { now = System.currentTimeMillis(); delay(1000) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg2,
        title = { Text("Temporizador para dormir", color = c.text) },
        text = {
            Column {
                when {
                    endsAt != null -> Text("Se detendrá en ${formatDuration(endsAt!! - now)}", color = c.accent, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                    endTrack -> Text("Se detendrá al terminar la canción", color = c.accent, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                listOf(5, 10, 15, 20, 30, 45, 60, 90, 120).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(c.text.copy(alpha = 0.08f))
                                    .clickable { hub.startSleep(m); onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) { Text(if (m < 60) "$m min" else "${m / 60} h" + if (m % 60 > 0) " ${m % 60}" else "", color = c.text, fontSize = 15.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(c.text.copy(alpha = 0.08f))
                        .clickable { hub.startSleepEndOfTrack(); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text("Al terminar la canción", color = c.text, fontSize = 15.sp) }
            }
        },
        confirmButton = {
            if (endsAt != null || endTrack) TextButton(onClick = { hub.cancelSleep(); onDismiss() }) { Text("Apagar temporizador", color = c.accent) }
            else TextButton(onClick = onDismiss) { Text("Cerrar", color = c.text2) }
        },
    )
}

/** Mini reproductor de cristal (abajo). Tocar = abrir; deslizar arriba = abrir. */
@Composable
fun MiniPlayer(haze: HazeState, onOpen: () -> Unit, onQueue: () -> Unit, modifier: Modifier = Modifier) {
    val g = MusicApp.graph
    val ui by g.player.state.collectAsState()
    val t = ui.current ?: return
    val c = LocalAppColors.current
    val pos = rememberPosition(500)
    val frac = if (ui.durationMs > 0) (pos.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).glass(haze, RoundedCornerShape(18.dp))
            .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d < -12) onOpen() } }
            .clickable(onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(c.accent))
        }
        Row(Modifier.fillMaxWidth().height(62.dp).padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtImage(t, Modifier.size(48.dp), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.displayArtist, color = c.text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { g.player.playPause() }) {
                Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.isPlaying) "Pausa" else "Reproducir", tint = c.text, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { g.player.next() }) { Icon(Icons.Rounded.SkipNext, "Siguiente", tint = c.text, modifier = Modifier.size(30.dp)) }
            IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Cola", tint = c.text) }
        }
    }
}

@Suppress("unused")
private val circle = CircleShape

package io.github.gato131313.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.lyrics.LyricsData
import io.github.gato131313.music.lyrics.Lrc
import kotlinx.coroutines.launch

private sealed interface LyricsUi {
    data object Loading : LyricsUi
    data object None : LyricsUi
    data class Error(val msg: String) : LyricsUi
    data class Ready(val data: LyricsData) : LyricsUi
}

/** Letra sincronizada con traducción al español debajo de cada línea. Tocar una línea = ir a ese momento. */
@Composable
fun LyricsPanel(track: Track, accent: Color) {
    val g = MusicApp.graph
    var state by remember(track.id) { mutableStateOf<LyricsUi>(LyricsUi.Loading) }
    var retry by remember { mutableLongStateOf(0L) }
    LaunchedEffect(track.id, retry) {
        state = LyricsUi.Loading
        state = g.lyrics.load(track, force = retry > 0).fold(
            onSuccess = { if (it == null || it.lines.isEmpty()) LyricsUi.None else LyricsUi.Ready(it) },
            onFailure = { LyricsUi.Error(it.message ?: "Sin conexión") },
        )
    }
    when (val s = state) {
        LyricsUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        LyricsUi.None -> Message("No encontré la letra de esta canción.", "Buscar otra vez") { retry = System.currentTimeMillis() }
        is LyricsUi.Error -> Message(s.msg, "Reintentar") { retry = System.currentTimeMillis() }
        is LyricsUi.Ready -> if (s.data.synced) SyncedLyrics(s.data, accent) else PlainLyrics(s.data)
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp, textAlign = TextAlign.Center)
        Text(action, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp).clickable(onClick = onAction).padding(10.dp))
    }
}

@Composable
private fun SyncedLyrics(data: LyricsData, accent: Color) {
    val pc = MusicApp.graph.player
    val pos = rememberPosition(120)
    val idx = Lrc.indexAt(data.lines, pos + 250)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var userScrollAt by remember { mutableLongStateOf(0L) }
    var auto by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { if (it && !auto) userScrollAt = System.currentTimeMillis() }
    }
    LaunchedEffect(idx) {
        if (idx >= 0 && System.currentTimeMillis() - userScrollAt > 3500) {
            val h = list.layoutInfo.viewportSize.height
            auto = true
            try { list.animateScrollToItem(idx, -(h / 3).coerceAtLeast(0)) } finally { auto = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 40.dp, bottom = with(density) { 260.dp })) {
        itemsIndexed(data.lines, key = { i, l -> "$i-${l.timeMs}" }) { i, line ->
            val active = i == idx
            val col by animateColorAsState(if (active) Color.White else Color.White.copy(alpha = if (i < idx) 0.38f else 0.5f), label = "linea")
            Column(Modifier.fillMaxWidth().clickable { scope.launch { pc.seekTo(line.timeMs) } }.padding(vertical = 9.dp)) {
                Text(line.text, color = col, fontSize = if (active) 27.sp else 24.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                if (line.translation != null) {
                    Text(line.translation, color = if (active) accent.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.4f), fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (data.better) item {
            Text("La traducción se mejorará pronto.", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
        }
        item { Text("Letra: LRCLIB", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp)) }
    }
}

@Composable
private fun PlainLyrics(data: LyricsData) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        itemsIndexed(data.lines) { _, line ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(line.text, color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (line.translation != null) Text(line.translation, color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
            }
        }
        item { Text("Letra sin tiempos · LRCLIB", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp)) }
    }
}

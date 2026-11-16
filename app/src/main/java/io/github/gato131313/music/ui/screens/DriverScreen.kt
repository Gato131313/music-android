package io.github.gato131313.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.player.rememberCoverPalette
import io.github.gato131313.music.ui.player.rememberPosition
import kotlinx.coroutines.launch

/** Modo conductor: botones enormes, pantalla siempre encendida, deslizar para cambiar de canción. */
@Composable
fun DriverScreen(a: UiActions) {
    val g = MusicApp.graph
    val ui by g.player.state.collectAsState()
    val favs by g.user.favorites.collectAsState()
    val scope = rememberCoroutineScope()
    val t = ui.current
    val pal = rememberCoverPalette(t)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var drag by remember { mutableFloatStateOf(0f) }
    val pos = rememberPosition(500)
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = {
                if (drag < -120) g.player.next() else if (drag > 120) g.player.previous()
                drag = 0f
            }) { _, d -> drag += d }
        },
    ) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(pal.base, Color.Black))))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Modo conductor", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { a.back() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Salir del modo conductor", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            ArtImage(t, Modifier.size(170.dp), RoundedCornerShape(18.dp), 70.dp)
            Spacer(Modifier.height(18.dp))
            Text(t?.title ?: "Nada sonando", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 38.sp)
            Text(t?.displayArtist ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(if (ui.durationMs > 0) (pos.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f).height(8.dp).background(pal.glow))
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Big(Icons.Rounded.SkipPrevious, "Anterior", 110.dp) { g.player.previous() }
                Big(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.isPlaying) "Pausa" else "Reproducir", 150.dp, filled = true) { g.player.playPause() }
                Big(Icons.Rounded.SkipNext, "Siguiente", 110.dp) { g.player.next() }
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val fav = t != null && t.key in favs
                Big(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Quitar de favoritos" else "Favorito", 84.dp, tint = if (fav) pal.glow else Color.White) {
                    t?.let { scope.launch { g.user.toggleFavorite(it) } }
                }
                Big(Icons.Rounded.Shuffle, "Aleatorio", 84.dp, tint = if (ui.shuffle) pal.glow else Color.White) { g.player.toggleShuffle() }
            }
            Spacer(Modifier.height(10.dp))
            Text("Desliza a los lados para cambiar de canción", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun Big(icon: ImageVector, desc: String, size: Dp, filled: Boolean = false, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (filled) Color.White else Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (filled) Color.Black else tint, modifier = Modifier.size(size * 0.55f)) }
}

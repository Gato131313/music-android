package io.github.gato131313.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.model.MergeSuggestion
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.EmptyState
import io.github.gato131313.music.ui.components.GlassScaffold
import io.github.gato131313.music.ui.components.glassPanel
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Box(
        modifier.height(36.dp).let { if (selected) it.glassPanel(RoundedCornerShape(18.dp), strong = true) else it.glassPanel(RoundedCornerShape(18.dp)) }
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.accent else c.text2, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun SmallButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.height(36.dp).let {
            if (primary) it.glassPanel(RoundedCornerShape(10.dp), strong = true) else it.glassPanel(RoundedCornerShape(10.dp))
        }.clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (primary) c.accent else c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

/** "Revisar nombres": uniones dudosas, separaciones dudosas y canciones sin artista. */
@Composable
fun ReviewScreen(a: UiActions) {
    val lib = libraryState()
    val c = LocalAppColors.current
    val repo = MusicApp.graph.library
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var renameFor by remember { mutableStateOf<MergeSuggestion?>(null) }
    var artistFor by remember { mutableStateOf<Track?>(null) }
    val r = lib.review
    GlassScaffold("Revisar nombres", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                Text(
                    "Solo cambia cómo se ven los nombres dentro de MUSIC. Tus archivos nunca se modifican.",
                    color = c.text2, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Artistas parecidos (${r.merges.size})", tab == 0, { tab = 0 })
                    Chip("Separar artista (${r.splits.size})", tab == 1, { tab = 1 })
                    Chip("Sin artista (${r.unknownArtist.size})", tab == 2, { tab = 2 })
                    Chip("Todos los artistas", tab == 3, { tab = 3 })
                }
            }
            when (tab) {
                0 -> {
                    if (r.merges.isEmpty()) item { Done() }
                    items(r.merges, key = { it.aKey + "|" + it.bKey }) { m ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).glassPanel().padding(14.dp)) {
                            Text("¿Son el mismo artista?", color = c.text2, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("${m.aName}  (${m.aCount})", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${m.bName}  (${m.bCount})", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton("Unir como \"${m.bName}\"", primary = true) { scope.launch { repo.mergeArtists(m.aKey, m.bKey) } }
                                SmallButton("Unir como \"${m.aName}\"") { scope.launch { repo.mergeArtists(m.bKey, m.aKey) } }
                                SmallButton("Corregir…") { renameFor = m }
                                SmallButton("No son el mismo") { scope.launch { repo.rejectMerge(m.aKey, m.bKey) } }
                            }
                        }
                    }
                }
                1 -> {
                    if (r.splits.isEmpty()) item { Done() }
                    items(r.splits, key = { "s" + it.track.id }) { s ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).glassPanel().padding(14.dp)) {
                            Text("Archivo: ${s.track.fileName}", color = c.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("¿\"${s.artist}\" es el artista y \"${s.title}\" la canción?", color = c.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
                            Text("Ahora se muestra: ${s.track.title} — ${s.track.displayArtist}", color = c.text2, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallButton("Sí, separar", primary = true) { scope.launch { repo.decideSplit(s.track, true) } }
                                SmallButton("No, dejar así") { scope.launch { repo.decideSplit(s.track, false) } }
                                SmallButton("Editar…") { a.menu(s.track) }
                            }
                        }
                    }
                }
                2 -> {
                    if (r.unknownArtist.isEmpty()) item { Done() }
                    items(r.unknownArtist, key = { "u" + it.id }) { t ->
                        Row(Modifier.fillMaxWidth().clickable { artistFor = t }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArtImage(t, Modifier.size(46.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(t.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.fileName, color = c.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Rounded.Edit, "Poner artista", tint = c.accent)
                        }
                    }
                }
                else -> {
                    items(lib.artists, key = { "a" + it.key }) { ar ->
                        Row(Modifier.fillMaxWidth().clickable {
                            renameFor = MergeSuggestion(ar.key, ar.name, ar.tracks.size, ar.key, ar.name, ar.tracks.size)
                        }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ar.name, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text("${ar.tracks.size}", color = c.text2, fontSize = 13.sp)
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Rounded.Edit, "Corregir nombre", tint = c.text2, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    renameFor?.let { m ->
        TextInputDialog("Nombre correcto del artista", m.bName, onDismiss = { renameFor = null }) { name ->
            renameFor = null
            scope.launch {
                if (m.aKey == m.bKey) repo.renameArtist(m.aKey, name) else repo.mergeArtists(m.aKey, m.bKey, name)
            }
        }
    }
    artistFor?.let { t ->
        TextInputDialog("Artista de \"${t.title}\"", "", onDismiss = { artistFor = null }, suggestions = lib.artists.take(400).map { it.name }) { name ->
            artistFor = null
            scope.launch { repo.setArtist(t, name) }
        }
    }
}

@Composable
private fun Done() {
    EmptyState(Icons.Rounded.CheckCircle, "Todo en orden", "No hay nada que revisar aquí.")
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    suggestions: List<String> = emptyList(),
    onOk: (String) -> Unit,
) {
    val c = LocalAppColors.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg2,
        title = { Text(title, color = c.text, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (suggestions.isNotEmpty() && text.length >= 2) {
                    val f = io.github.gato131313.music.data.clean.NameCleaner.fold(text)
                    suggestions.filter { io.github.gato131313.music.data.clean.NameCleaner.fold(it).contains(f) }.take(5).forEach { s ->
                        Text(s, color = c.accent, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { text = s }.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) { Text("Guardar", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = c.text2) } },
    )
}

/** Edición manual de título, artista y álbum (solo en la app). */
@Composable
fun EditTrackDialog(track: Track, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val repo = MusicApp.graph.library
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist ?: "") }
    var album by remember { mutableStateOf(track.album ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg2,
        title = { Text("Editar nombre", color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Archivo: ${track.fileName}", color = c.text3, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (track.edited) {
                    Text("Restaurar nombre automático", color = c.accent, fontSize = 14.sp, modifier = Modifier.clickable {
                        scope.launch { repo.resetEdit(track) }
                        onDismiss()
                    }.padding(vertical = 6.dp))
                }
                Text("No se modifica el archivo, solo cómo se ve en MUSIC.", color = c.text3, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { repo.editTrack(track, title, artist, album) }
                onDismiss()
            }) { Text("Guardar", color = c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = c.text2) } },
    )
}

@Suppress("unused")
private val unused = Color.Transparent

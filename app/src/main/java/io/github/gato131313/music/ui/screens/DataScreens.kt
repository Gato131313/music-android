package io.github.gato131313.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.backup.CsvRow
import io.github.gato131313.music.data.backup.ImportCore
import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.db.ImportUnmatched
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.GlassScaffold
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (enabled) c.accent else c.text3).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ImportScreen(a: UiActions) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val unmatched by g.db.stats().unmatched().collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<ImportUnmatched?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            summary = runCatching { g.backup.importCsv(ctx, uri) }.fold(
                { r -> "Listo: ${r.rows} filas · ${r.plays.size} canciones con ${r.plays.values.sum()} reproducciones · ${r.favorites.size} favoritos · ${r.unmatched.size} sin empatar" },
                { e -> "No se pudo leer el archivo: ${e.message}" },
            )
            busy = false
        }
    }
    GlassScaffold("Importar datos", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card("Reproducciones de tu app anterior") {
                    Text(
                        "Elige un archivo CSV con las columnas titulo, artista, reproducciones (y opcional favorito). " +
                            "Se empatan con tus canciones aunque cambien mayúsculas, acentos o traigan \"- Topic\" u \"(Official Video)\". " +
                            "Las reproducciones se suman a tus estadísticas y a \"Más reproducidas\". Si importas el mismo archivo otra vez, no se duplica.",
                        color = c.text2, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("  Importando…", color = c.text)
                    } else PrimaryButton("Elegir archivo CSV") { launcher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) }
                    summary?.let { Text(it, color = c.accent, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)) }
                }
            }
            if (unmatched.isNotEmpty()) {
                item {
                    Text("Sin empatar (${unmatched.size}) · toca para elegir la canción", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
                items(unmatched, key = { it.id }) { u ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pick = u }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${u.artist.ifBlank { "—" }} · ${u.plays} reproducciones" + if (u.favorite) " · favorito" else "", color = c.text2, fontSize = 12.sp)
                        }
                        SmallButton("Unir") { pick = u }
                    }
                }
            } else if (summary != null) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
                    Text("  Todas las filas quedaron empatadas.", color = c.text)
                }
            }
        }
    }
    pick?.let { u -> MatchDialog(u) { pick = null } }
}

@Composable
private fun MatchDialog(u: ImportUnmatched, onDismiss: () -> Unit) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val lib = libraryState()
    var q by remember { mutableStateOf("") }
    val sugg = remember(u, q, lib) {
        if (q.isBlank()) ImportCore.suggestions(CsvRow(u.title, u.artist, u.plays, u.favorite), lib.songs)
        else { val f = NameCleaner.fold(q); lib.songs.filter { it.search.contains(f) }.take(30) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg2,
        title = { Text("¿Cuál es \"${u.title}\"?", color = c.text, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Buscar canción…") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sugg, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth().clickable { scope.launch { g.backup.matchManually(u, t.key) }; onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArtImage(t, Modifier.size(40.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(t.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.displayArtist, color = c.text2, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { scope.launch { g.backup.dismissUnmatched(u) }; onDismiss() }) { Text("Descartar fila", color = c.text2) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = c.text2) } },
    )
}

@Composable
fun BackupScreen(a: UiActions) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            msg = runCatching { g.backup.exportTo(ctx, uri) }.fold({ "Respaldo guardado." }, { "No se pudo guardar: ${it.message}" })
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmRestore = uri }
    GlassScaffold("Respaldo y restauración", onBack = { a.back() }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding() + 8.dp, start = 14.dp, end = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card("Guardar respaldo") {
                Text("Guarda favoritos, playlists, reproducciones, estadísticas, nombres corregidos, efectos y temas en un archivo. Puedes guardarlo en el teléfono o en Google Drive (elige Drive en la ventana que se abre).", color = c.text2, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Guardar respaldo…") { save.launch("respaldo-music-$stamp.json") }
                Text("Además, MUSIC guarda solo una copia diaria en Descargas/MUSIC.", color = c.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Card("Restaurar") {
                Text("Reemplaza tus datos actuales con los del respaldo. Tus archivos de música no se tocan.", color = c.text2, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Elegir respaldo…") { open.launch(arrayOf("application/json", "*/*")) }
            }
            msg?.let { Text(it, color = c.accent, fontSize = 15.sp) }
        }
    }
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            containerColor = c.bg2,
            title = { Text("¿Restaurar este respaldo?", color = c.text) },
            text = { Text("Se reemplazarán tus favoritos, playlists y estadísticas actuales.", color = c.text2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        msg = runCatching { g.backup.restoreFrom(ctx, uri) }.fold(
                            { b -> "Restaurado: ${b.favorites.size} favoritos, ${b.playlists.size} playlists, ${b.events.size} escuchas." },
                            { "No se pudo restaurar: ${it.message}" },
                        )
                    }
                }) { Text("Restaurar", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancelar", color = c.text2) } },
        )
    }
}

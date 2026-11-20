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
import io.github.gato131313.music.data.backup.How
import io.github.gato131313.music.data.backup.ImportCore
import io.github.gato131313.music.data.backup.ImportMatcher
import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.db.ImportManual
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

/** Texto del resultado de importar (también lo usa el aviso tras actualizar la app). */
fun importSummary(total: Int, linked: Int, discarded: List<Pair<String, String>>): String {
    if (discarded.isEmpty()) return "Se empataron todas: $linked de $total canciones ya tienen sus reproducciones de tu app anterior."
    val names = discarded.take(4).joinToString(", ") { "\"${it.first}\"" } + if (discarded.size > 4) "…" else ""
    return "Se empataron $linked de $total. Se descartaron ${discarded.size} porque no hay en el teléfono ninguna canción " +
        "con un nombre parecido (por ejemplo notas de voz): $names."
}

@Composable
fun ImportScreen(a: UiActions) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val discarded by g.db.stats().unmatched().collectAsState(initial = emptyList())
    val links by g.db.stats().links().collectAsState(initial = emptyList())
    val lib = libraryState()
    var busy by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<ImportUnmatched?>(null) }
    var fix by remember { mutableStateOf<ImportManual?>(null) }
    var showLinks by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            summary = runCatching { g.backup.importCsv(ctx, uri) }.fold(
                { r -> "Listo. " + importSummary(r.rows, r.assignment.links.size, r.assignment.discarded.map { it.row.title to it.reason }) },
                { e -> "No se pudo leer el archivo: ${e.message}" },
            )
            busy = false
        }
    }
    val probable = links.filter { it.how == How.PROBABLE.name }
    GlassScaffold("Importar datos", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card("Reproducciones de tu app anterior") {
                    Text(
                        "Elige un archivo CSV con las columnas titulo, artista, reproducciones (y opcional favorito). " +
                            "Cada fila se une sola con una canción de tu teléfono, aunque el título venga cortado con \"…\" o el artista diga \"<unknown>\". " +
                            "Las reproducciones se suman a las que ya llevas. Si importas el mismo archivo otra vez, no se duplica.",
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
            if (links.isNotEmpty() || discarded.isNotEmpty()) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
                    Text(
                        if (discarded.isEmpty()) "  Se empataron todas las canciones" else "  Se empataron todas menos ${discarded.size} (sin ninguna canción parecida)",
                        color = c.text, fontSize = 15.sp,
                    )
                }
            }
            if (discarded.isNotEmpty()) {
                item {
                    Column {
                        Text("Descartadas (${discarded.size})", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        Text(discarded.first().reason ?: ImportMatcher.NO_CANDIDATE, color = c.text2, fontSize = 13.sp)
                        Text("No hace falta hacer nada. Si alguna sí está en tu teléfono, toca Unir.", color = c.text3, fontSize = 12.sp)
                    }
                }
                items(discarded, key = { "d" + it.id }) { u ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { pick = u }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${u.artist.ifBlank { "—" }} · ${u.plays} reproducciones", color = c.text2, fontSize = 12.sp)
                        }
                        SmallButton("Unir") { pick = u }
                    }
                }
            }
            if (probable.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showLinks = !showLinks }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Revisar uniones (opcional)", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${probable.size} se eligieron entre varias canciones parecidas. Si alguna quedó mal, tócala para cambiarla.", color = c.text2, fontSize = 13.sp)
                        }
                        Text(if (showLinks) "Ocultar" else "Ver", color = c.accent, fontSize = 14.sp)
                    }
                }
                if (showLinks) items(probable, key = { "l" + it.rowId }) { l ->
                    val t = lib.byKey[l.key]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { fix = l }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "→ ${t?.title ?: l.key.substringAfterLast('/')}" + (t?.let { " · " + it.displayArtist + if (it.isVideo) " · video" else "" } ?: "") + " · ${l.plays}",
                                color = c.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SmallButton("Cambiar") { fix = l }
                    }
                }
            }
        }
    }
    pick?.let { u ->
        MatchDialog(CsvRow(u.title, u.artist, u.plays, u.favorite), onPick = { k -> scope.launch { g.backup.matchManually(u, k) } }, onDiscard = { scope.launch { g.backup.dismissUnmatched(u) } }) { pick = null }
    }
    fix?.let { l -> MatchDialog(CsvRow(l.title, l.artist, l.plays, false), onPick = { k -> scope.launch { g.backup.relink(l, k) } }, onDiscard = null) { fix = null } }
}

@Composable
private fun MatchDialog(row: CsvRow, onPick: (String) -> Unit, onDiscard: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val lib = libraryState()
    var q by remember { mutableStateOf("") }
    val all = remember(lib) { lib.songs + lib.allVideos }
    val sugg = remember(row, q, all) {
        if (q.isBlank()) ImportCore.suggestions(row, all)
        else { val f = NameCleaner.fold(q); all.filter { it.search.contains(f) }.take(30) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg2,
        title = { Text("¿Cuál es \"${row.title}\"?", color = c.text, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Buscar canción…") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sugg, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(t.key); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArtImage(t, Modifier.size(40.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(t.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.displayArtist + if (t.isVideo) " · video" else "", color = c.text2, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { if (onDiscard != null) TextButton(onClick = { onDiscard(); onDismiss() }) { Text("Quitar de la lista", color = c.text2) } },
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

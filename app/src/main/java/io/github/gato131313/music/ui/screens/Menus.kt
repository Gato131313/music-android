package io.github.gato131313.music.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.UserDataRepository
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.data.scan.MediaScanner
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.formatDuration
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun MenuItem(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) androidx.compose.ui.graphics.Color(0xFFFF453A) else c.text, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, color = if (danger) androidx.compose.ui.graphics.Color(0xFFFF453A) else c.text, fontSize = 16.sp)
    }
}

/** Contexto donde se abrió el menú (para "Quitar de esta playlist"). */
data class MenuContext(val playlistId: Long? = null)

/** Menú ⋮ de una canción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(track: Track, ctxInfo: MenuContext, a: UiActions, onDismiss: () -> Unit) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by g.user.favorites.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(ctx, "Borrada", Toast.LENGTH_SHORT).show()
            g.library.refreshAsync()
        }
        onDismiss()
    }
    if (edit) { EditTrackDialog(track) { edit = false; onDismiss() }; return }
    if (addTo) { AddToPlaylistDialog(listOf(track)) { addTo = false; onDismiss() }; return }
    if (info) { TrackInfoDialog(track) { info = false; onDismiss() }; return }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.bg2,
            title = { Text("¿Borrar del teléfono?", color = c.text) },
            text = { Text("Se borrará el archivo \"${track.fileName}\" de tu teléfono. No se puede deshacer.", color = c.text2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleteFile(ctx, track) { req -> deleteLauncher.launch(req) } ?: run { g.library.refreshAsync(); onDismiss() }
                }) { Text("Borrar", color = androidx.compose.ui.graphics.Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = c.text2) } },
        )
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.bg2) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtImage(track, Modifier.size(54.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text(track.title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.displayArtist + " · " + formatDuration(track.durationMs), color = c.text2, fontSize = 14.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.size(6.dp))
            MenuItem(Icons.Rounded.SkipNext, "Reproducir siguiente") { g.player.playNext(listOf(track)); toast(ctx, "Sonará después de la actual"); onDismiss() }
            MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Agregar a la cola") { g.player.addToQueue(listOf(track)); toast(ctx, "Agregada a la cola"); onDismiss() }
            MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Agregar a playlist") { addTo = true }
            val fav = track.key in favs
            MenuItem(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Quitar de favoritos" else "Agregar a favoritos") {
                scope.launch { g.user.toggleFavorite(track) }; onDismiss()
            }
            if (ctxInfo.playlistId != null) MenuItem(Icons.Rounded.RemoveCircleOutline, "Quitar de esta playlist") {
                scope.launch { g.user.removeFromPlaylist(ctxInfo.playlistId, track.key) }; onDismiss()
            }
            if (track.artist != null) MenuItem(Icons.Rounded.Person, "Ir al artista") { onDismiss(); a.navigate("artist/" + enc(track.artistKey)) }
            if (track.album != null) MenuItem(Icons.Rounded.Album, "Ir al álbum") {
                val al = g.library.library.value.albums.firstOrNull { al -> al.tracks.any { it.id == track.id } }
                onDismiss(); al?.let { a.navigate("album/" + enc(it.key)) }
            }
            MenuItem(Icons.Rounded.Share, "Compartir") { share(ctx, track); onDismiss() }
            MenuItem(Icons.Rounded.Edit, "Editar nombre") { edit = true }
            if (!track.isVideo) MenuItem(Icons.Rounded.NotificationsActive, "Poner como tono") { setRingtone(ctx, track); onDismiss() }
            if (track.hasVideo) MenuItem(Icons.Rounded.LinkOff, "Separar canción y video") {
                scope.launch { g.library.setPair(track, null) }; toast(ctx, "Ya no están unidos"); onDismiss()
            }
            MenuItem(Icons.Rounded.Equalizer, "Efectos para esta canción") { onDismiss(); a.navigate("effects") }
            MenuItem(Icons.Rounded.Info, "Detalles del archivo") { info = true }
            MenuItem(Icons.Rounded.VisibilityOff, "Ocultar") {
                scope.launch { g.library.hide(track) }; toast(ctx, "Oculta. Puedes verla en Archivos ocultos."); onDismiss()
            }
            MenuItem(Icons.Rounded.Delete, "Borrar", danger = true) { confirmDelete = true }
        }
    }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

private fun share(ctx: Context, t: Track) {
    val uri = MediaScanner.contentUri(t.id, t.isVideo)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = t.mime ?: if (t.isVideo) "video/*" else "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "${t.title} — ${t.displayArtist}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Compartir \"${t.title}\"").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun setRingtone(ctx: Context, t: Track) {
    if (!Settings.System.canWrite(ctx)) {
        toast(ctx, "Permite a MUSIC cambiar ajustes del sistema y vuelve a intentarlo")
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    try {
        RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, MediaScanner.contentUri(t.id, false))
        toast(ctx, "\"${t.title}\" es tu tono de llamada")
    } catch (e: Exception) {
        toast(ctx, "No se pudo poner como tono")
    }
}

/** Borra con la confirmación del sistema (Android 11+). Devuelve null si se borró directo. */
private fun deleteFile(ctx: Context, t: Track, launch: (IntentSenderRequest) -> Unit): Unit? {
    val uri = ContentUris.withAppendedId(if (t.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.id)
    return try {
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(uri))
            launch(IntentSenderRequest.Builder(pi.intentSender).build())
            Unit
        } else {
            ctx.contentResolver.delete(uri, null, null)
            null
        }
    } catch (e: android.app.RecoverableSecurityException) {
        if (Build.VERSION.SDK_INT >= 29) launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
        Unit
    } catch (e: Exception) {
        toast(ctx, "No se pudo borrar: ${e.message}")
        null
    }
}

@Composable
fun AddToPlaylistDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pls by g.user.playlists.collectAsState()
    var newName by remember { mutableStateOf(false) }
    if (newName) {
        TextInputDialog("Nueva playlist", "", onDismiss = { newName = false }) { name ->
            scope.launch { g.user.createPlaylist(name, tracks.map { it.key }) }
            toast(ctx, "Agregada a \"$name\"")
            onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg2,
        title = { Text("Agregar a playlist", color = c.text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { newName = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = c.accent)
                        Spacer(Modifier.width(12.dp))
                        Text("Nueva playlist…", color = c.accent, fontSize = 16.sp)
                    }
                }
                items(pls, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clickable {
                        scope.launch { g.user.addToPlaylist(p.id, tracks.map { it.key }) }
                        toast(ctx, "Agregada a \"${p.name}\"")
                        onDismiss()
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlaylistPlay, null, tint = c.text2)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${p.count}", color = c.text2, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = c.text2) } },
    )
}

@Composable
fun TrackInfoDialog(t: Track, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val plays = MusicApp.graph.user.totalPlays(t.key)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bg2,
        title = { Text("Detalles", color = c.text) },
        text = {
            Column {
                listOf(
                    "Título" to t.title, "Artista" to t.displayArtist, "Álbum" to (t.album ?: "—"), "Género" to (t.genre ?: "—"),
                    "Duración" to formatDuration(t.durationMs), "Reproducciones" to plays.toString(),
                    "Archivo" to t.fileName, "Carpeta" to t.folder, "Tamaño" to "%.1f MB".format(t.size / 1_048_576.0),
                    "Nombre original" to listOfNotNull(t.rawArtist, t.rawTitle).joinToString(" — ").ifBlank { "—" },
                ).forEach { (k, v) ->
                    Text(k, color = c.text2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(v, color = c.text, fontSize = 15.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = c.accent) } },
    )
}

/** Menú ⋮ de una lista de Inicio (inteligente o playlist). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListMenuSheet(entry: ListEntry, tracks: List<Track>, onDismiss: () -> Unit) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rename by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val isUser = entry.playlistId != null
    val isDefault = entry.name == UserDataRepository.DEFAULT_PLAYLIST
    if (rename) {
        TextInputDialog("Renombrar playlist", entry.name, onDismiss = { rename = false; onDismiss() }) { n ->
            scope.launch { g.user.renamePlaylist(entry.playlistId!!, n) }; onDismiss()
        }
        return
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = c.bg2,
            title = { Text("¿Eliminar \"${entry.name}\"?", color = c.text) },
            text = { Text("Se borra la playlist, no las canciones.", color = c.text2) },
            confirmButton = { TextButton(onClick = { scope.launch { g.user.deletePlaylist(entry.playlistId!!) }; onDismiss() }) { Text("Eliminar", color = androidx.compose.ui.graphics.Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar", color = c.text2) } },
        )
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.bg2) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(entry.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            MenuItem(Icons.Rounded.PlayArrow, "Reproducir") { if (tracks.isNotEmpty()) g.player.play(tracks, 0); onDismiss() }
            MenuItem(Icons.Rounded.Shuffle, "Aleatorio") { if (tracks.isNotEmpty()) g.player.play(tracks, tracks.indices.random(), true); onDismiss() }
            MenuItem(Icons.Rounded.SkipNext, "Reproducir siguiente") { g.player.playNext(tracks); onDismiss() }
            MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Agregar a la cola") { g.player.addToQueue(tracks); toast(ctx, "${tracks.size} canciones agregadas"); onDismiss() }
            if (isUser && !isDefault) MenuItem(Icons.Rounded.Edit, "Renombrar") { rename = true }
            if (isUser) MenuItem(Icons.Rounded.DeleteOutline, if (isDefault) "Vaciar" else "Eliminar playlist", danger = true) {
                if (isDefault) { scope.launch { g.user.reorderPlaylist(entry.playlistId!!, emptyList()) }; onDismiss() } else confirm = true
            }
        }
    }
}

@Suppress("unused")
private val shape = RoundedCornerShape(8.dp)

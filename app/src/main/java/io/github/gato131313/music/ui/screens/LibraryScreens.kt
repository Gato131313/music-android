package io.github.gato131313.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.model.Album
import io.github.gato131313.music.data.model.Artist
import io.github.gato131313.music.data.model.Library
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.EmptyState
import io.github.gato131313.music.ui.components.FastScroller
import io.github.gato131313.music.ui.components.GlassScaffold
import io.github.gato131313.music.ui.components.TrackRow
import io.github.gato131313.music.ui.components.firstLetter
import io.github.gato131313.music.ui.components.formatDuration
import io.github.gato131313.music.ui.components.glassPanel
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Acciones comunes que las pantallas piden a la app (reproducir, menú, navegar). */
interface UiActions {
    fun play(queue: List<Track>, index: Int, shuffle: Boolean = false)
    fun menu(track: Track, context: List<Track> = emptyList(), playlistId: Long? = null)
    fun navigate(route: String)
    fun back()
    fun currentId(): Long? = null
}

@Composable
fun libraryState(): Library {
    val lib by MusicApp.graph.library.library.collectAsState()
    return lib
}

@Composable
fun PlayShuffleRow(tracks: List<Track>, a: UiActions) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple(Icons.Rounded.PlayArrow, "Reproducir", false), Triple(Icons.Rounded.Shuffle, "Aleatorio", true)).forEach { (icon, label, sh) ->
            Row(
                Modifier.weight(1f).height(46.dp).glassPanel(RoundedCornerShape(14.dp), strong = true)
                    .clickable(enabled = tracks.isNotEmpty()) { a.play(tracks, if (sh) tracks.indices.random() else 0, sh) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = c.accent)
                Spacer(Modifier.width(6.dp))
                Text(label, color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/** Lista de canciones virtualizada con barra rápida. */
@Composable
fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    a: UiActions,
    header: (@Composable () -> Unit)? = null,
    showSort: Boolean = false,
    numbered: Boolean = false,
    playlistId: Long? = null,
) {
    val c = LocalAppColors.current
    val settings by MusicApp.graph.settings.flow.collectAsState()
    var sortOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sorted = remember(tracks, settings.songSort, showSort) {
        if (!showSort) tracks else when (settings.songSort) {
            "artista" -> tracks.sortedWith(compareBy({ NameCleaner.fold(it.displayArtist) }, { NameCleaner.fold(it.title) }))
            "reciente" -> tracks.sortedByDescending { it.dateAdded }
            "duracion" -> tracks.sortedByDescending { it.durationMs }
            else -> tracks
        }
    }
    GlassScaffold(title, onBack = { a.back() }, actions = {
        IconButton(onClick = { a.navigate("search") }) { Icon(Icons.Rounded.Search, "Buscar", tint = c.text) }
        if (showSort) Box {
            IconButton(onClick = { sortOpen = true }) { Icon(Icons.Rounded.Sort, "Ordenar", tint = c.text) }
            DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                listOf("titulo" to "Título", "artista" to "Artista", "reciente" to "Añadidas recientemente", "duracion" to "Duración").forEach { (k, l) ->
                    DropdownMenuItem(text = { Text(l, fontWeight = if (settings.songSort == k) FontWeight.Bold else null) }, onClick = {
                        sortOpen = false
                        scope.launch { MusicApp.graph.settings.update { it.copy(songSort = k) } }
                    })
                }
            }
        }
    }) { pad ->
        val state = rememberLazyListState()
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.LibraryMusic, "Nada por aquí", "No hay canciones en esta sección.")
            }
        } else {
            val current = a.currentId()
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp)) {
                header?.let { h -> item(key = "header") { h() } }
                item(key = "play") { PlayShuffleRow(sorted, a) }
                items(sorted.size, key = { sorted[it].id }, contentType = { "t" }) { i ->
                    val t = sorted[i]
                    TrackRow(
                        t, onClick = { a.play(sorted, i) }, onMenu = { a.menu(t, sorted, playlistId) }, playing = t.id == current,
                        leading = if (numbered) ({
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text("${i + 1}", color = c.text2, fontSize = 15.sp) }
                        }) else null,
                    )
                }
            }
            val offset = (if (header != null) 1 else 0) + 1
            FastScroller(state, sorted.size + offset, { idx ->
                val t = sorted.getOrNull(idx - offset) ?: return@FastScroller ""
                if (settings.songSort == "artista" && showSort) firstLetter(t.displayArtist) else firstLetter(t.title)
            }, topPad = pad.calculateTopPadding(), bottomPad = pad.calculateBottomPadding())
        }
    }
}

@Composable
fun SongsScreen(a: UiActions) {
    val lib = libraryState()
    TrackListScreen("Canciones (${lib.songs.size})", lib.songs, a, showSort = true)
}

@Composable
fun VideosScreen(a: UiActions) {
    val lib = libraryState()
    TrackListScreen("Video (${lib.videos.size})", lib.videos, a, showSort = true)
}

@Composable
fun AlbumsScreen(a: UiActions) {
    val lib = libraryState()
    val c = LocalAppColors.current
    GlassScaffold("Álbumes (${lib.albums.size})", onBack = { a.back() }) { pad ->
        if (lib.albums.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.Album, "Sin álbumes", "Las canciones descargadas no suelen traer álbum. Aparecerán aquí las que sí lo tengan.")
            }
            return@GlassScaffold
        }
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(lib.albums, key = { it.key }) { al ->
                Column(Modifier.clickable { a.navigate("album/" + enc(al.key)) }) {
                    ArtImage(al.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(10.dp), 40.dp)
                    Text(al.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text("${al.artist} · ${al.tracks.size}", color = c.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun CollectionHeader(track: Track?, title: String, subtitle: String, round: Boolean = false) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtImage(track, Modifier.size(190.dp), if (round) CircleShape else RoundedCornerShape(14.dp), 64.dp)
        Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, start = 24.dp, end = 24.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = c.text2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

fun totalTime(ts: List<Track>): String {
    val min = ts.sumOf { it.durationMs } / 60000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

@Composable
fun AlbumDetail(key: String, a: UiActions) {
    val lib = libraryState()
    val al: Album = lib.albums.firstOrNull { it.key == key } ?: return TrackListScreen("Álbum", emptyList(), a)
    TrackListScreen(al.name, al.tracks, a, header = { CollectionHeader(al.tracks.first(), al.name, "${al.artist} · ${al.tracks.size} canciones · ${totalTime(al.tracks)}") })
}

@Composable
fun ArtistsScreen(a: UiActions) {
    val lib = libraryState()
    val c = LocalAppColors.current
    GlassScaffold("Artistas (${lib.artists.size})", onBack = { a.back() }, actions = {
        IconButton(onClick = { a.navigate("review") }) { Icon(Icons.Rounded.Person, "Revisar nombres", tint = c.text) }
    }) { pad ->
        val state = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp)) {
            items(lib.artists, key = { it.key }) { ar -> ArtistRow(ar) { a.navigate("artist/" + enc(ar.key)) } }
        }
        FastScroller(state, lib.artists.size, { firstLetter(lib.artists.getOrNull(it)?.name ?: "") }, pad.calculateTopPadding(), pad.calculateBottomPadding())
    }
}

@Composable
fun ArtistRow(ar: Artist, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtImage(ar.tracks.first(), Modifier.size(52.dp), CircleShape)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(ar.name, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ar.tracks.size} canciones" + if (ar.albumCount > 0) " · ${ar.albumCount} álbumes" else "", color = c.text2, fontSize = 13.sp)
        }
    }
}

@Composable
fun ArtistDetail(key: String, a: UiActions) {
    val lib = libraryState()
    val ar = lib.artists.firstOrNull { it.key == key } ?: return TrackListScreen("Artista", emptyList(), a)
    TrackListScreen(ar.name, ar.tracks, a, header = { CollectionHeader(ar.tracks.first(), ar.name, "${ar.tracks.size} canciones · ${totalTime(ar.tracks)}", round = true) })
}

@Composable
fun FoldersScreen(a: UiActions) {
    val lib = libraryState()
    val c = LocalAppColors.current
    GlassScaffold("Carpetas (${lib.folders.size})", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp)) {
            items(lib.folders, key = { it.path }) { f ->
                Row(Modifier.fillMaxWidth().clickable { a.navigate("folder/" + enc(f.path)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).glassPanel(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, tint = c.accent, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(f.name, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val parts = buildList {
                            if (f.tracks.isNotEmpty()) add("${f.tracks.size} canciones")
                            if (f.videos.isNotEmpty()) add("${f.videos.size} videos")
                        }
                        Text(parts.joinToString(" · ") + " · " + f.path.trimEnd('/'), color = c.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun FolderDetail(path: String, a: UiActions) {
    val lib = libraryState()
    val f = lib.folders.firstOrNull { it.path == path } ?: return TrackListScreen("Carpeta", emptyList(), a)
    TrackListScreen(f.name, f.tracks + f.videos, a, showSort = true)
}

@Composable
fun GenresScreen(a: UiActions) {
    val lib = libraryState()
    val c = LocalAppColors.current
    GlassScaffold("Género (${lib.genres.size})", onBack = { a.back() }) { pad ->
        if (lib.genres.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.LibraryMusic, "Sin géneros", "Tus archivos no traen género en sus etiquetas.")
            }
            return@GlassScaffold
        }
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(lib.genres, key = { it.key }) { g ->
                Box(Modifier.fillMaxWidth().height(92.dp).glassPanel(RoundedCornerShape(16.dp)).clickable { a.navigate("genre/" + enc(g.key)) }.padding(14.dp)) {
                    Text(g.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${g.tracks.size} canciones", color = c.text2, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
    }
}

@Composable
fun GenreDetail(key: String, a: UiActions) {
    val lib = libraryState()
    val g = lib.genres.firstOrNull { it.key == key } ?: return TrackListScreen("Género", emptyList(), a)
    TrackListScreen(g.name, g.tracks, a, showSort = true)
}

private data class SearchResult(val songs: List<Track>, val artists: List<Artist>, val albums: List<Album>, val videos: List<Track>)

/** Búsqueda instantánea (en memoria, sin acentos ni mayúsculas). */
@Composable
fun SearchScreen(a: UiActions) {
    val lib = libraryState()
    val c = LocalAppColors.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val result by produceState(SearchResult(emptyList(), emptyList(), emptyList(), emptyList()), q, lib) {
        val words = NameCleaner.fold(q).split(' ').filter { it.isNotBlank() }
        value = if (words.isEmpty()) SearchResult(emptyList(), emptyList(), emptyList(), emptyList()) else withContext(Dispatchers.Default) {
            fun match(s: String) = words.all { s.contains(it) }
            SearchResult(
                lib.songs.filter { match(it.search) }.take(300),
                lib.artists.filter { match(NameCleaner.fold(it.name)) }.take(20),
                lib.albums.filter { match(NameCleaner.fold(it.name + " " + it.artist)) }.take(20),
                lib.allVideos.filter { it.pairId == null && match(it.search) }.take(100),
            )
        }
    }
    GlassScaffold("", onBack = { a.back() }, titleContent = {
        Row(Modifier.weight(1f).padding(end = 8.dp).height(40.dp).glassPanel(RoundedCornerShape(20.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = c.text2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Canciones, artistas, álbumes…", color = c.text3, fontSize = 16.sp)
                BasicTextField(
                    q, { q = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (q.isNotEmpty()) Icon(Icons.Rounded.Close, "Borrar", tint = c.text2, modifier = Modifier.size(20.dp).clickable { q = "" })
        }
    }) { pad ->
        val r = result
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp)) {
            if (r.artists.isNotEmpty()) {
                item { Header("Artistas") }
                items(r.artists, key = { "ar" + it.key }) { ar -> ArtistRow(ar) { a.navigate("artist/" + enc(ar.key)) } }
            }
            if (r.albums.isNotEmpty()) {
                item { Header("Álbumes") }
                items(r.albums, key = { "al" + it.key }) { al ->
                    Row(Modifier.fillMaxWidth().clickable { a.navigate("album/" + enc(al.key)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtImage(al.tracks.first(), Modifier.size(52.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(al.name, color = c.text, fontSize = 16.sp, maxLines = 1)
                            Text(al.artist, color = c.text2, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
            if (r.songs.isNotEmpty()) {
                item { Header("Canciones (${r.songs.size})") }
                items(r.songs.size, key = { "s" + r.songs[it].id }) { i -> TrackRow(r.songs[i], { a.play(r.songs, i) }, { a.menu(r.songs[i], r.songs) }) }
            }
            if (r.videos.isNotEmpty()) {
                item { Header("Videos (${r.videos.size})") }
                items(r.videos.size, key = { "v" + r.videos[it].id }) { i -> TrackRow(r.videos[i], { a.play(r.videos, i) }, { a.menu(r.videos[i], r.videos) }) }
            }
            if (q.isNotBlank() && r.songs.isEmpty() && r.artists.isEmpty() && r.albums.isEmpty() && r.videos.isEmpty()) {
                item { EmptyState(Icons.Rounded.Search, "Sin resultados", "No encontré \"$q\".") }
            }
        }
    }
}

@Composable
private fun Header(t: String) {
    val c = LocalAppColors.current
    Text(t, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
fun dec(s: String): String = java.net.URLDecoder.decode(s, "UTF-8")

@Suppress("unused")
private fun durationLabel(t: Track) = formatDuration(t.durationMs)

@Suppress("unused")
private val placeholderMod = Modifier.background(androidx.compose.ui.graphics.Color.Transparent)

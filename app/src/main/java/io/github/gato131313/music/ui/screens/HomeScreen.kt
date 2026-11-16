package io.github.gato131313.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.UserDataRepository
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.LocalBottomInset
import io.github.gato131313.music.ui.components.glass
import io.github.gato131313.music.ui.components.glassPanel
import io.github.gato131313.music.ui.theme.AppBackground
import io.github.gato131313.music.ui.theme.LocalAppColors

/** Una fila de "Listado": lista inteligente o playlist del usuario. */
data class ListEntry(
    val route: String,
    val name: String,
    val count: Int,
    val icon: ImageVector?,
    val cover: Track?,
    val playlistId: Long? = null,
)

@Composable
fun homeLists(): List<ListEntry> {
    val g = MusicApp.graph
    val lib = libraryState()
    val favs by g.user.favoritesOrdered.collectAsState()
    val stats by g.user.stats.collectAsState()
    val recent by g.user.recent.collectAsState()
    val pls by g.user.playlists.collectAsState()
    return remember(lib, favs, stats, recent, pls) {
        val fixed = listOf(
            ListEntry("smart/favoritos", "Favoritos", g.user.favoriteTracks(lib).size, Icons.Rounded.Favorite, null),
            ListEntry("smart/anadidas", "Añadidas recientemente", g.user.recentlyAdded(lib).size, Icons.Rounded.NewReleases, null),
            ListEntry("smart/recientes", "Reproducidas recientemente", g.user.recentlyPlayed(lib).size, Icons.Rounded.History, null),
            ListEntry("smart/mas", "Más reproducidas", g.user.mostPlayed(lib).size, Icons.Rounded.TrendingUp, null),
        )
        val user = pls.map { p ->
            val isDefault = p.name == UserDataRepository.DEFAULT_PLAYLIST
            ListEntry("playlist/${p.id}", p.name, p.count, if (isDefault) Icons.AutoMirrored.Rounded.PlaylistPlay else null, p.firstKey?.let { lib.byKey[it] }, p.id)
        }
        fixed + user
    }
}

@Composable
fun HomeScreen(a: UiActions, openDrawer: () -> Unit, onNewPlaylist: () -> Unit, onListMenu: (ListEntry) -> Unit) {
    val c = LocalAppColors.current
    val g = MusicApp.graph
    val lib = libraryState()
    val loaded by g.library.libraryOrNull.collectAsState()
    val scanning by g.library.scanning.collectAsState()
    val lists = homeLists()
    val haze = remember { HazeState() }
    var barH by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val bottom = LocalBottomInset.current

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().haze(haze)) {
            AppBackground()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = with(density) { barH.toDp() }, bottom = bottom + 96.dp),
            ) {
                item(key = "grid") {
                    val tiles = listOf(
                        Triple(Icons.Rounded.MusicNote, "Canciones", lib.songs.size) to "songs",
                        Triple(Icons.Rounded.Album, "Álbumes", lib.albums.size) to "albums",
                        Triple(Icons.Rounded.Person, "Artistas", lib.artists.size) to "artists",
                        Triple(Icons.Rounded.Folder, "Carpetas", lib.folders.size) to "folders",
                        Triple(Icons.Rounded.Piano, "Género", lib.genres.size) to "genres",
                        Triple(Icons.Rounded.Videocam, "Video", lib.videos.size) to "videos",
                    )
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tiles.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (t, route) ->
                                    CategoryTile(t.first, t.second, t.third, Modifier.weight(1f)) { a.navigate(route) }
                                }
                            }
                        }
                    }
                }
                if (lib.review.total > 0) item(key = "review") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glassPanel(RoundedCornerShape(14.dp))
                            .clickable { a.navigate("review") }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = c.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Revisar nombres: ${lib.review.total} por confirmar", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = c.text2, modifier = Modifier.size(14.dp))
                    }
                }
                if (loaded == null || (scanning && lib.songs.isEmpty())) item(key = "loading") {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Buscando música en tu teléfono…", color = c.text2)
                    }
                }
                item(key = "listado") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Listado (${lists.size})", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onNewPlaylist) { Icon(Icons.Rounded.Add, "Nueva playlist", tint = c.text) }
                        IconButton(onClick = { a.navigate("playlists") }) { Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, "Ver todas", tint = c.text, modifier = Modifier.size(18.dp)) }
                    }
                }
                items(lists, key = { it.route }) { e -> ListRow(e, { a.navigate(e.route) }, { onListMenu(e) }) }
            }
        }
        // Barra superior de cristal: ☰  Música  🔍
        Row(
            Modifier.fillMaxWidth().onSizeChanged { barH = it.height }.glass(haze, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .windowInsetsPadding(WindowInsets.statusBars).height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = openDrawer) { Icon(Icons.Rounded.Menu, "Menú", tint = c.text) }
            Text("Música", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (scanning) CircularProgressIndicator(color = c.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = { a.navigate("search") }) { Icon(Icons.Rounded.Search, "Buscar", tint = c.text) }
        }
        // Aleatorio flotante
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = bottom + 18.dp).size(62.dp)
                .glass(haze, CircleShape, tint = c.accent.copy(alpha = 0.88f))
                .clickable(enabled = lib.songs.isNotEmpty()) { a.play(lib.songs, lib.songs.indices.random(), shuffle = true) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Shuffle, "Reproducción aleatoria", tint = c.onAccent, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun CategoryTile(icon: ImageVector, label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier.aspectRatio(1.05f).glassPanel(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label ($count)" }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text("($count)", color = c.text2, fontSize = 12.sp)
    }
}

@Composable
fun ListRow(e: ListEntry, onClick: () -> Unit, onMenu: () -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (e.icon != null) {
            Box(Modifier.size(56.dp).glassPanel(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(e.icon, null, tint = if (e.route == "smart/favoritos") c.accent else c.text, modifier = Modifier.size(28.dp))
            }
        } else {
            ArtImage(e.cover, Modifier.size(56.dp), RoundedCornerShape(12.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(e.name, color = c.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.count} canciones", color = c.text2, fontSize = 13.sp)
        }
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Opciones de ${e.name}", tint = c.text2) }
    }
}

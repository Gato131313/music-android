package io.github.gato131313.music.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.R
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.ui.components.LocalBottomInset
import io.github.gato131313.music.ui.components.glassPanel
import io.github.gato131313.music.ui.screens.AlbumDetail
import io.github.gato131313.music.ui.screens.AlbumsScreen
import io.github.gato131313.music.ui.screens.ArtistDetail
import io.github.gato131313.music.ui.screens.ArtistsScreen
import io.github.gato131313.music.ui.screens.EditTrackDialog
import io.github.gato131313.music.ui.screens.FolderDetail
import io.github.gato131313.music.ui.screens.FoldersScreen
import io.github.gato131313.music.ui.screens.GenreDetail
import io.github.gato131313.music.ui.screens.GenresScreen
import io.github.gato131313.music.ui.screens.HomeScreen
import io.github.gato131313.music.ui.screens.PermissionScreen
import io.github.gato131313.music.ui.screens.ReviewScreen
import io.github.gato131313.music.ui.screens.SearchScreen
import io.github.gato131313.music.ui.screens.SongsScreen
import io.github.gato131313.music.ui.screens.TextInputDialog
import io.github.gato131313.music.ui.screens.TrackListScreen
import io.github.gato131313.music.ui.screens.UiActions
import io.github.gato131313.music.ui.screens.VideosScreen
import io.github.gato131313.music.ui.screens.dec
import io.github.gato131313.music.ui.screens.libraryState
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import io.github.gato131313.music.ui.player.MiniPlayer
import io.github.gato131313.music.ui.player.PlayerCallbacks
import io.github.gato131313.music.ui.player.PlayerScreen
import io.github.gato131313.music.ui.player.SleepTimerDialog

/** Peticiones de navegación desde fuera de Compose (notificación, widgets). */
object NavRequests { val route = MutableStateFlow<String?>(null) }

data class DrawerItem(val icon: ImageVector, val label: String, val route: String)

val DRAWER_ITEMS = listOf(
    DrawerItem(Icons.Rounded.Equalizer, "Ecualizador", "effects"),
    DrawerItem(Icons.Rounded.Palette, "Temas", "themes"),
    DrawerItem(Icons.Rounded.Widgets, "Widgets", "widgets"),
    DrawerItem(Icons.Rounded.Shuffle, "Reproducción aleatoria", "action/shuffle"),
    DrawerItem(Icons.Rounded.Bedtime, "Temporizador para dormir", "action/sleep"),
    DrawerItem(Icons.Rounded.Refresh, "Escanear medios", "action/scan"),
    DrawerItem(Icons.Rounded.DirectionsCar, "Modo conductor", "driver"),
    DrawerItem(Icons.Rounded.VisibilityOff, "Archivos ocultos", "hidden"),
    DrawerItem(Icons.Rounded.DriveFileRenameOutline, "Revisar nombres", "review"),
    DrawerItem(Icons.Rounded.BarChart, "Estadísticas", "stats"),
    DrawerItem(Icons.Rounded.Download, "Importar datos", "import"),
    DrawerItem(Icons.Rounded.Settings, "Ajustes", "settings"),
)

@Composable
fun AppRoot(startRoute: String? = null) {
    val g = MusicApp.graph
    val ctx = LocalContext.current
    val hasPerm by g.library.hasPermission.collectAsState()
    if (!hasPerm) {
        PermissionScreen()
        return
    }
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<Pair<Track, io.github.gato131313.music.ui.screens.MenuContext>?>(null) }
    var listMenuFor by remember { mutableStateOf<io.github.gato131313.music.ui.screens.ListEntry?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    val lib = libraryState()
    val playerOpen = rememberSaveable { mutableStateOf(startRoute == "player") }
    val sleepOpen = remember { mutableStateOf(false) }
    val ui by g.player.state.collectAsState()
    val rootHaze = remember { HazeState() }
    var miniH by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(Unit) { g.player.connect() }
    LaunchedEffect(Unit) {
        if (!io.github.gato131313.music.BuildConfig.DEBUG) g.updates.check()
        kotlinx.coroutines.delay(20_000)
        val prefs = ctx.getSharedPreferences("auto", android.content.Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("backup", 0) > 20 * 3_600_000L && g.backup.autoBackup(ctx)) prefs.edit().putLong("backup", System.currentTimeMillis()).apply()
    }
    val update by g.updates.available.collectAsState()
    val updProgress by g.updates.progress.collectAsState()

    val actions = remember(nav) {
        object : UiActions {
            override fun play(queue: List<Track>, index: Int, shuffle: Boolean) {
                g.player.play(queue, index, shuffle)
                if (queue.getOrNull(index)?.isVideo == true) playerOpen.value = true
            }
            override fun currentId(): Long? = g.player.state.value.current?.id
            override fun menu(track: Track, context: List<Track>, playlistId: Long?) { menuFor = track to io.github.gato131313.music.ui.screens.MenuContext(playlistId) }
            override fun navigate(route: String) {
                when (route) {
                    "player" -> playerOpen.value = true
                    "action/shuffle" -> g.player.shuffleAll()
                    "action/sleep" -> sleepOpen.value = true
                    else -> handleRoute(route, nav, ctx) { scope.launch { drawer.close() } }
                }
            }
            override fun back() { if (!nav.popBackStack()) Unit }
        }
    }

    // Atrás con el menú lateral abierto: cerrar el menú (no salir de la app)
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = { AppDrawer { item -> scope.launch { drawer.close() }; actions.navigate(item.route) } },
    ) {
        val bottomInset = if (ui.current != null) with(density) { miniH.toDp() } + navBottom + 8.dp else navBottom + 8.dp
        Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomInset provides bottomInset) {
            NavHost(nav, startDestination = startRoute?.takeIf { it != "player" } ?: "home", modifier = Modifier.fillMaxSize().background(LocalAppColors.current.bg).haze(rootHaze)) {
                composable("home") {
                    HomeScreen(actions, openDrawer = { scope.launch { drawer.open() } }, onNewPlaylist = { newPlaylist = true }, onListMenu = { listMenuFor = it })
                }
                composable("songs") { SongsScreen(actions) }
                composable("albums") { AlbumsScreen(actions) }
                composable("album/{key}") { AlbumDetail(dec(it.arguments?.getString("key") ?: ""), actions) }
                composable("artists") { ArtistsScreen(actions) }
                composable("artist/{key}") { ArtistDetail(dec(it.arguments?.getString("key") ?: ""), actions) }
                composable("folders") { FoldersScreen(actions) }
                composable("folder/{path}") { FolderDetail(dec(it.arguments?.getString("path") ?: ""), actions) }
                composable("genres") { GenresScreen(actions) }
                composable("genre/{key}") { GenreDetail(dec(it.arguments?.getString("key") ?: ""), actions) }
                composable("videos") { VideosScreen(actions) }
                composable("search") { SearchScreen(actions) }
                composable("review") { ReviewScreen(actions) }
                composable("smart/{id}") {
                    val id = it.arguments?.getString("id") ?: ""
                    val tracks = when (id) {
                        "favoritos" -> g.user.favoriteTracks(lib)
                        "anadidas" -> g.user.recentlyAdded(lib)
                        "recientes" -> g.user.recentlyPlayed(lib)
                        else -> g.user.mostPlayed(lib)
                    }
                    val title = mapOf("favoritos" to "Favoritos", "anadidas" to "Añadidas recientemente", "recientes" to "Reproducidas recientemente", "mas" to "Más reproducidas")[id] ?: ""
                    TrackListScreen(title, tracks, actions, numbered = id == "mas")
                }
                composable("playlist/{id}") {
                    val id = it.arguments?.getString("id")?.toLongOrNull() ?: 0
                    val items by g.user.playlistItems(id).collectAsState(initial = emptyList())
                    val pls by g.user.playlists.collectAsState()
                    TrackListScreen(pls.firstOrNull { p -> p.id == id }?.name ?: "Playlist", items.mapNotNull { i -> lib.byKey[i.key] }, actions, playlistId = id)
                }
                composable("effects") { io.github.gato131313.music.ui.screens.EffectsScreen(actions) }
                composable("themes") { io.github.gato131313.music.ui.screens.ThemesScreen(actions) }
                composable("settings") { io.github.gato131313.music.ui.screens.SettingsScreen(actions) }
                composable("stats") { io.github.gato131313.music.ui.screens.StatsScreen(actions) }
                composable("driver") { io.github.gato131313.music.ui.screens.DriverScreen(actions) }
                composable("widgets") { io.github.gato131313.music.ui.screens.WidgetsScreen(actions) }
                composable("import") { io.github.gato131313.music.ui.screens.ImportScreen(actions) }
                composable("backup") { io.github.gato131313.music.ui.screens.BackupScreen(actions) }
                composable("hidden") { io.github.gato131313.music.ui.screens.HiddenScreen(actions) }
                composable("playlists") { io.github.gato131313.music.ui.screens.PlaylistsScreen(actions, onNew = { newPlaylist = true }, onMenu = { listMenuFor = it }) }
                composable("soon/{name}") { ComingSoon(dec(it.arguments?.getString("name") ?: ""), actions) }
            }
        }
        MiniPlayer(
            rootHaze, onOpen = { playerOpen.value = true }, onQueue = { playerOpen.value = true },
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 6.dp).onSizeChanged { miniH = it.height },
        )
        AnimatedVisibility(playerOpen.value, enter = slideInVertically(tween(320)) { it }, exit = slideOutVertically(tween(260)) { it }) {
            PlayerScreen(PlayerCallbacks(onClose = { playerOpen.value = false }, onEffects = { playerOpen.value = false; actions.navigate("effects") }, onMenu = { t -> menuFor = t to io.github.gato131313.music.ui.screens.MenuContext() }))
        }
        }
    }
    LaunchedEffect(Unit) {
        NavRequests.route.collect { r -> if (r != null) { NavRequests.route.value = null; actions.navigate(r) } }
    }
    if (sleepOpen.value) SleepTimerDialog { sleepOpen.value = false }
    update?.let { r ->
        val c = LocalAppColors.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (updProgress == null) g.updates.dismiss() },
            containerColor = c.bg2,
            title = { Text("Nueva versión ${r.version}", color = c.text) },
            text = {
                Column {
                    Text(r.notes.ifBlank { "Mejoras y correcciones." }.take(600), color = c.text2, fontSize = 14.sp)
                    updProgress?.let { p ->
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { p }, color = c.accent, modifier = Modifier.fillMaxWidth())
                        Text("Descargando ${(p * 100).toInt()}%", color = c.text2, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = updProgress == null, onClick = {
                    g.scope.launch { g.updates.downloadAndInstall(ctx, r).onFailure { Toast.makeText(ctx, "No se pudo descargar: ${it.message}", Toast.LENGTH_LONG).show() } }
                }) { Text("Actualizar", color = c.accent) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { g.updates.dismiss() }) { Text("Ahora no", color = c.text2) } },
        )
    }
    menuFor?.let { (t, mc) -> io.github.gato131313.music.ui.screens.TrackMenuSheet(t, mc, actions) { menuFor = null } }
    listMenuFor?.let { e ->
        val tracks = listTracks(e, lib)
        io.github.gato131313.music.ui.screens.ListMenuSheet(e, tracks) { listMenuFor = null }
    }
    if (newPlaylist) TextInputDialog("Nueva playlist", "", onDismiss = { newPlaylist = false }) { name ->
        newPlaylist = false
        scope.launch { g.user.createPlaylist(name) }
    }
}

private fun handleRoute(route: String, nav: NavHostController, ctx: android.content.Context, closeDrawer: () -> Unit) {
    val g = MusicApp.graph
    when (route) {
        "action/scan" -> {
            Toast.makeText(ctx, "Escaneando medios…", Toast.LENGTH_SHORT).show()
            g.library.refreshAsync(systemScan = true)
        }
        "action/update" -> {
            Toast.makeText(ctx, "Buscando actualizaciones…", Toast.LENGTH_SHORT).show()
            g.scope.launch {
                val r = g.updates.check(force = true)
                r.onSuccess { if (it == null) Toast.makeText(ctx, "Ya tienes la versión más nueva", Toast.LENGTH_SHORT).show() }
                r.onFailure { Toast.makeText(ctx, "No se pudo revisar: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
        else -> nav.navigate(route) { launchSingleTop = true }
    }
    closeDrawer()
}

@Composable
private fun listTracks(e: io.github.gato131313.music.ui.screens.ListEntry, lib: io.github.gato131313.music.data.model.Library): List<Track> {
    val g = MusicApp.graph
    val items by (e.playlistId?.let { g.user.playlistItems(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    return when (e.route) {
        "smart/favoritos" -> g.user.favoriteTracks(lib)
        "smart/anadidas" -> g.user.recentlyAdded(lib)
        "smart/recientes" -> g.user.recentlyPlayed(lib)
        "smart/mas" -> g.user.mostPlayed(lib)
        else -> items.mapNotNull { lib.byKey[it.key] }
    }
}

@Composable
private fun ComingSoon(name: String, a: UiActions) {
    TrackListScreen(name, emptyList(), a)
}

@Composable
fun AppDrawer(onItem: (DrawerItem) -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxHeight().width(300.dp).glassPanel(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp), strong = true)
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.padding(start = 22.dp, top = 26.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Color.Black, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_u_mark), null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("MUSIC", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        DRAWER_ITEMS.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onItem(item) }.padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, null, tint = c.text, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(22.dp))
                Text(item.label, color = c.text, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Suppress("unused")
private val q = Icons.AutoMirrored.Rounded.QueueMusic

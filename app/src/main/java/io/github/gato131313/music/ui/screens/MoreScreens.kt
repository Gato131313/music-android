package io.github.gato131313.music.ui.screens

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.BuildConfig
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.prefs.Settings
import io.github.gato131313.music.ui.components.EmptyState
import io.github.gato131313.music.ui.components.GlassScaffold
import io.github.gato131313.music.ui.components.glassPanel
import io.github.gato131313.music.ui.theme.ACCENTS
import io.github.gato131313.music.ui.theme.AppBackground
import io.github.gato131313.music.ui.theme.BACKGROUNDS
import io.github.gato131313.music.ui.theme.LocalAppColors
import io.github.gato131313.music.ui.theme.STYLES
import io.github.gato131313.music.ui.theme.musicDark
import io.github.gato131313.music.ui.theme.musicLight
import io.github.gato131313.music.ui.theme.neon
import io.github.gato131313.music.ui.theme.paisaje
import io.github.gato131313.music.ui.theme.solido
import kotlinx.coroutines.launch

@Composable
fun Card(title: String? = null, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().glassPanel().padding(14.dp)) {
        if (title != null) Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = c.text2, fontSize = 13.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
fun LinkRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = c.text2, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = c.text3, modifier = Modifier.size(14.dp))
    }
}

/** Elegir el estilo del widget: Portada, Cristal o Mínimo. */
@Composable
fun WidgetStyleRow(current: String, onChange: (String) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("Estilo", color = c.text, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("portada", "Portada", "Portada difuminada"),
                Triple("cristal", "Cristal", "Color de la portada"),
                Triple("minimo", "Mínimo", "Oscuro"),
            ).forEach { (k, name, hint) ->
                val sel = current == k
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) c.accent.copy(alpha = 0.22f) else c.text.copy(alpha = 0.06f))
                        .clickable { onChange(k) }.padding(vertical = 10.dp, horizontal = 8.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "Estilo $name" + if (sel) ", elegido" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(name, color = if (sel) c.accent else c.text, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    Text(hint, color = c.text2, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: String, onChange: (Float) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(title, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(label, color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value, onChange, valueRange = range, steps = steps, colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.text.copy(alpha = 0.15f)))
    }
}

// ---------------- Temas ----------------
@Composable
fun ThemesScreen(a: UiActions) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val s by g.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    fun set(f: (Settings) -> Settings) = scope.launch { g.settings.update(f) }
    val accentColor = Color(s.accent.toInt())
    GlassScaffold("Temas", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card("Estilo") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        STYLES.forEach { (id, name) ->
                            val preview = when (id) {
                                "paisaje" -> paisaje(Color(0xFFFF4F6D), s.background)
                                "neon" -> neon(Color(0xFF00E5FF))
                                "solido" -> solido(accentColor, true)
                                else -> if (s.mode == "light") musicLight(accentColor) else musicDark(accentColor)
                            }
                            StylePreview(name, s.style == id, preview) { set { it.copy(style = id) } }
                        }
                    }
                }
            }
            item {
                Card("Fondo") {
                    if (s.style == "music" || s.style == "solido") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("dark" to "Negro", "light" to "Blanco", "auto" to "Automático").forEach { (k, l) ->
                                Chip(l, s.mode == k, { set { it.copy(mode = k) } })
                            }
                        }
                    } else if (s.style == "paisaje") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BACKGROUNDS.forEach { (id, name) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp).clickable { set { it.copy(background = id) } }) {
                                    Box(
                                        Modifier.width(92.dp).aspectRatio(0.56f).clip(RoundedCornerShape(12.dp))
                                            .border(if (s.background == id) 3.dp else 0.dp, c.accent, RoundedCornerShape(12.dp)),
                                    ) {
                                        CompositionLocalProvider(LocalAppColors provides paisaje(c.accent, id)) { AppBackground() }
                                    }
                                    Text(name, color = c.text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    } else {
                        Text("El estilo Neón usa su propio fondo oscuro con luces.", color = c.text2, fontSize = 14.sp)
                    }
                }
            }
            item {
                Card("Color de acento") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ACCENTS.forEach { col ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(col.toInt()))
                                    .border(2.dp, if (s.accent == col) c.text else Color.Transparent, CircleShape)
                                    .clickable { set { it.copy(accent = col) } },
                                contentAlignment = Alignment.Center,
                            ) { if (s.accent == col) Icon(Icons.Rounded.Check, "Elegido", tint = if (col == 0xFFFFFFFF) Color.Black else Color.White) }
                        }
                    }
                    SwitchRow("Colores de la portada", "El reproductor toma los colores de cada canción", s.coverColors) { v -> set { it.copy(coverColors = v) } }
                }
            }
            item {
                Card("Estilo de portada en el reproductor") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("disco" to "Disco", "cuadrada" to "Cuadrada", "aurora" to "Aurora").forEach { (k, l) ->
                            Chip(l, s.coverStyle == k, { set { it.copy(coverStyle = k) } })
                        }
                    }
                    Text(
                        when (s.coverStyle) {
                            "disco" -> "Disco circular que gira con un anillo de barras al ritmo real del audio."
                            "cuadrada" -> "Portada cuadrada como MUSIC, con fondo de colores que pulsa."
                            else -> "Nuevo: la portada flota sobre ondas de luz que reaccionan a graves, voces y agudos."
                        },
                        color = c.text2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StylePreview(name: String, selected: Boolean, colors: io.github.gato131313.music.ui.theme.AppColors, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.width(118.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(118.dp).aspectRatio(0.56f).clip(RoundedCornerShape(14.dp)).border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.divider, RoundedCornerShape(14.dp))) {
            CompositionLocalProvider(LocalAppColors provides colors) {
                AppBackground()
                Column(Modifier.padding(8.dp)) {
                    Text("Música", color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            Box(Modifier.weight(1f).aspectRatio(1f).glassPanel(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MusicNote, null, tint = colors.accent, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    repeat(4) {
                        Box(Modifier.fillMaxWidth().height(14.dp).padding(vertical = 2.dp).clip(RoundedCornerShape(3.dp)).background(colors.text.copy(alpha = 0.12f)))
                    }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(20.dp).clip(CircleShape).background(colors.accent))
            }
        }
        Text(name, color = c.text, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ---------------- Ajustes ----------------
@Composable
fun SettingsScreen(a: UiActions) {
    val g = MusicApp.graph
    val s by g.settings.flow.collectAsState()
    val scope = rememberCoroutineScope()
    val c = LocalAppColors.current
    fun set(f: (Settings) -> Settings) = scope.launch { g.settings.update(f) }
    GlassScaffold("Ajustes", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card("Reproducción") {
                    SliderRow("Fusión entre canciones", s.crossfadeSec.toFloat(), 0f..12f, 11, if (s.crossfadeSec == 0) "No" else "${s.crossfadeSec} s") { v -> set { it.copy(crossfadeSec = v.toInt()) } }
                    SwitchRow("Abrir en modo Video", "Si una canción tiene video, empezar mostrando el video", s.preferVideo) { v -> set { it.copy(preferVideo = v) } }
                    LinkRow("Efectos de sonido", "Ecualizador, graves, 3D, reverberación") { a.navigate("effects") }
                }
            }
            item {
                Card("Biblioteca") {
                    SwitchRow("Mostrar reproducciones 🎧", "El número de veces que escuchaste cada canción, a la derecha de cada fila", s.showPlays) { v -> set { it.copy(showPlays = v) } }
                    SliderRow("Ignorar audios cortos", s.minDurationSec.toFloat(), 0f..120f, 11, if (s.minDurationSec == 0) "No" else "< ${s.minDurationSec} s") { v -> set { it.copy(minDurationSec = (v / 10).toInt() * 10) } }
                    LinkRow("Escanear medios", "Buscar canciones y videos nuevos en todo el teléfono") { a.navigate("action/scan") }
                    LinkRow("Revisar nombres", "Unir artistas repetidos y corregir nombres") { a.navigate("review") }
                    LinkRow("Archivos ocultos", "Canciones y carpetas que no quieres ver") { a.navigate("hidden") }
                }
            }
            item {
                Card("Letras") {
                    SwitchRow("Traducir al español", "Muestra la traducción debajo de cada línea", s.translateLyrics) { v -> set { it.copy(translateLyrics = v) } }
                }
            }
            item {
                Card("Widget de pantalla de inicio") {
                    WidgetStyleRow(s.widgetStyle) { v -> set { it.copy(widgetStyle = v) } }
                    SliderRow("Opacidad del fondo", s.widgetOpacity, 0.3f..1f, 13, "${(s.widgetOpacity * 100).toInt()} %") { v -> set { it.copy(widgetOpacity = v) } }
                    LinkRow("Agregar widget", "Se puede agrandar o achicar arrastrando sus orillas") { a.navigate("widgets") }
                }
            }
            item {
                Card("Tus datos") {
                    LinkRow("Importar datos", "Reproducciones de tu app anterior (archivo CSV)") { a.navigate("import") }
                    LinkRow("Respaldo y restauración", "Guarda favoritos, playlists y estadísticas") { a.navigate("backup") }
                    LinkRow("Estadísticas de escucha") { a.navigate("stats") }
                }
            }
            item {
                Card("Aplicación") {
                    SwitchRow("Buscar actualizaciones al abrir", checked = s.checkUpdates) { v -> set { it.copy(checkUpdates = v) } }
                    LinkRow("Buscar actualizaciones ahora", "Versión ${BuildConfig.VERSION_NAME}") { a.navigate("action/update") }
                    Text("MUSIC para Android · sin cuenta y sin nube. Todo vive en tu teléfono.", color = c.text3, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

// ---------------- Archivos ocultos ----------------
@Composable
fun HiddenScreen(a: UiActions) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val hidden by g.library.hiddenFlow().collectAsState(initial = emptyList())
    val lib = libraryState()
    val scope = rememberCoroutineScope()
    var pickFolder by remember { mutableStateOf(false) }
    GlassScaffold("Archivos ocultos", onBack = { a.back() }, actions = {
        androidx.compose.material3.IconButton(onClick = { pickFolder = true }) { Icon(Icons.Rounded.CreateNewFolder, "Ocultar una carpeta", tint = c.text) }
    }) { pad ->
        if (hidden.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.Visibility, "Nada oculto", "Usa \"Ocultar\" en el menú ⋮ de una canción, o el botón de arriba para ocultar una carpeta completa.")
            }
            return@GlassScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp)) {
            items(hidden, key = { it.path }) { h ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (h.isFolder) Icons.Rounded.Folder else Icons.Rounded.MusicNote, null, tint = c.text2)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(h.path.trimEnd('/').substringAfterLast('/'), color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (h.isFolder) "Carpeta completa" else h.path.substringBeforeLast('/'), color = c.text3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SmallButton("Mostrar") { scope.launch { g.library.unhide(h.path) } }
                }
            }
        }
    }
    if (pickFolder) {
        AlertDialog(
            onDismissRequest = { pickFolder = false },
            containerColor = c.bg2,
            title = { Text("Ocultar carpeta", color = c.text) },
            text = {
                LazyColumn(Modifier.height(380.dp)) {
                    items(lib.folders, key = { it.path }) { f ->
                        Row(Modifier.fillMaxWidth().clickable {
                            val any = (f.tracks + f.videos).firstOrNull()
                            val abs = any?.path?.substringBeforeLast('/')?.lowercase()
                            if (abs != null) scope.launch { g.library.hideFolder("$abs/") }
                            pickFolder = false
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Folder, null, tint = c.accent)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(f.name, color = c.text, fontSize = 15.sp)
                                Text("${f.tracks.size + f.videos.size} archivos · ${f.path}", color = c.text3, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFolder = false }) { Text("Cancelar", color = c.text2) } },
        )
    }
}

// ---------------- Todas las listas ----------------
@Composable
fun PlaylistsScreen(a: UiActions, onNew: () -> Unit, onMenu: (ListEntry) -> Unit) {
    val c = LocalAppColors.current
    val lists = homeLists()
    GlassScaffold("Listado (${lists.size})", onBack = { a.back() }, actions = {
        androidx.compose.material3.IconButton(onClick = onNew) { Icon(Icons.Rounded.Add, "Nueva playlist", tint = c.text) }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp)) {
            items(lists, key = { it.route }) { e -> ListRow(e, { a.navigate(e.route) }, { onMenu(e) }) }
        }
    }
}

// ---------------- Widgets ----------------
@Composable
fun WidgetsScreen(a: UiActions) {
    val c = LocalAppColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    GlassScaffold("Widgets", onBack = { a.back() }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding() + 12.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card("Reproductor en tu pantalla de inicio") {
                Row(
                    Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xE6111114)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) {
                        Icon(androidx.compose.ui.res.painterResource(io.github.gato131313.music.R.drawable.ic_u_mark), null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Tu canción", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Anterior · Reproducir · Siguiente", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }
                Text(
                    "Agrándalo o achícalo arrastrando sus orillas: chico (portada y play), mediano (con título y progreso) o grande " +
                        "(con aleatorio, repetir y me gusta). El fondo toma el color de la portada; el estilo y la transparencia se eligen en Ajustes.",
                    color = c.text2, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.accent).clickable {
                        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
                        val cn = android.content.ComponentName(ctx, io.github.gato131313.music.widget.MusicWidgetReceiver::class.java)
                        if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(cn, null, null)
                        else toast(ctx, "Mantén presionada la pantalla de inicio › Widgets › MUSIC")
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("Agregar a la pantalla de inicio", color = c.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

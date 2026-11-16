package io.github.gato131313.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.fx.FxParams
import io.github.gato131313.music.fx.FxPresets
import io.github.gato131313.music.ui.components.GlassScaffold
import io.github.gato131313.music.ui.components.glassPanel
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val OUTPUT_NAMES = listOf("audifonos" to "Audífonos", "bocina" to "Bocina del teléfono", "bluetooth" to "Bluetooth")

@Composable
fun EffectsScreen(a: UiActions) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    val s by g.settings.flow.collectAsState()
    val userPresets by g.user.eqPresets.collectAsState()
    val songFx by g.user.songFx.collectAsState()
    val output by g.fx.output.collectAsState()
    val ui by g.player.state.collectAsState()
    val scope = rememberCoroutineScope()
    val fx = s.fx
    var saveDialog by remember { mutableStateOf(false) }
    val allPresets = FxPresets.BUILT_IN.keys.toList() + userPresets.keys.sorted()
    fun update(f: (FxParams) -> FxParams) = scope.launch { g.settings.update { it.copy(fx = f(it.fx)) } }
    fun bandsOf(name: String) = FxPresets.BUILT_IN[name] ?: userPresets[name]
    val matching = allPresets.firstOrNull { bandsOf(it) == fx.bands }
    val override = when {
        ui.current?.key?.let { songFx[it] } != null -> "Esta canción usa el preset \"${songFx[ui.current!!.key]}\"."
        s.fxByOutput[output]?.isNotBlank() == true -> "Con ${OUTPUT_NAMES.first { it.first == output }.second} se usa el preset \"${s.fxByOutput[output]}\"."
        else -> null
    }

    GlassScaffold("Efectos de sonido", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp, start = 14.dp, end = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().glassPanel().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ECUALIZADOR", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Switch(fx.eqOn, { on -> update { it.copy(eqOn = on) } }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent), modifier = Modifier.semantics { contentDescription = "Ecualizador encendido" })
                    }
                    if (override != null) Text(override, color = c.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PresetPicker(matching ?: "Personalizado", allPresets, Modifier.weight(1f)) { name ->
                            bandsOf(name)?.let { b -> update { it.copy(bands = b, preset = name, eqOn = true) } }
                        }
                        IconButton(onClick = { saveDialog = true }) { Icon(Icons.Rounded.Save, "Guardar preset propio", tint = c.accent) }
                        if (matching != null && matching in userPresets) {
                            IconButton(onClick = { scope.launch { g.user.deleteEqPreset(matching) } }) { Icon(Icons.Rounded.Delete, "Borrar preset", tint = c.text2) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    EqBands(fx.bands, enabled = fx.eqOn) { i, v -> update { p -> p.copy(bands = p.bands.toMutableList().also { it[i] = v }, preset = "Personalizado") } }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glassPanel().padding(14.dp)) {
                    var open by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Reverberación", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(fx.reverb, color = c.accent, fontSize = 15.sp)
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = c.text2)
                        DropdownMenu(open, { open = false }) {
                            FxPresets.REVERBS.keys.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { open = false; update { it.copy(reverb = r) } }) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Knob("Graves", fx.bass) { v -> update { it.copy(bass = v) } }
                        Knob("Virtualizador 3D", fx.virtualizer) { v -> update { it.copy(virtualizer = v) } }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glassPanel().padding(14.dp)) {
                    var vol by remember { mutableFloatStateOf(g.fx.systemVolume()) }
                    LaunchedEffect(Unit) { while (true) { delay(1500); vol = g.fx.systemVolume() } }
                    LabeledSlider("Volumen", vol, "${(vol * 100).roundToInt()}%") { vol = it; g.fx.setSystemVolume(it) }
                    LabeledSlider("Volumen extra", fx.boost, "+${(fx.boost * 12).roundToInt()} dB") { v -> update { it.copy(boost = v) } }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Normalizador", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Iguala el volumen entre canciones", color = c.text2, fontSize = 13.sp)
                        }
                        Switch(fx.normalize, { on -> update { it.copy(normalize = on) } }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glassPanel().padding(14.dp)) {
                    Text("Presets por salida", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Ahora suena por: ${OUTPUT_NAMES.first { it.first == output }.second}", color = c.text2, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    OUTPUT_NAMES.forEach { (k, label) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            PresetPicker(s.fxByOutput[k]?.takeIf { it.isNotBlank() } ?: "Igual que arriba", listOf("Igual que arriba") + allPresets, Modifier.width(190.dp)) { name ->
                                scope.launch { g.settings.update { it.copy(fxByOutput = it.fxByOutput + (k to if (name == "Igual que arriba") "" else name)) } }
                            }
                        }
                    }
                    ui.current?.let { t ->
                        Spacer(Modifier.height(8.dp))
                        Text("Solo para \"${t.title}\"", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        PresetPicker(songFx[t.key] ?: "Sin preset propio", listOf("Sin preset propio") + allPresets, Modifier.fillMaxWidth().padding(top = 4.dp)) { name ->
                            scope.launch { g.user.setSongPreset(t.key, if (name == "Sin preset propio") null else name) }
                        }
                    }
                }
            }
        }
    }
    if (saveDialog) TextInputDialog("Nombre del preset", "Mi preset", onDismiss = { saveDialog = false }) { name ->
        saveDialog = false
        scope.launch { g.user.saveEqPreset(name, fx.bands); g.settings.update { it.copy(fx = it.fx.copy(preset = name)) } }
    }
}

@Composable
private fun PresetPicker(current: String, options: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    val c = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip10().background(c.text.copy(alpha = 0.08f)).clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, "Elegir preset", tint = c.text2)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o, fontWeight = if (o == current) FontWeight.Bold else null) }, onClick = { open = false; onPick(o) }) }
        }
    }
}

private fun Modifier.clip10() = this.clip(RoundedCornerShape(10.dp))

/** 10 barras verticales con la curva de respuesta detrás. */
@Composable
private fun EqBands(bands: List<Float>, enabled: Boolean, onChange: (Int, Float) -> Unit) {
    val c = LocalAppColors.current
    val alpha = if (enabled) 1f else 0.4f
    Column {
        Row(Modifier.fillMaxWidth()) {
            bands.forEachIndexed { _, v ->
                Text((if (v > 0) "+" else "") + v.roundToInt(), color = c.text.copy(alpha = alpha), fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Box(Modifier.fillMaxWidth().height(230.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = bands.size
                val colW = size.width / n
                fun y(v: Float) = size.height * (0.5f - v / 30f)
                drawLine(c.text.copy(alpha = 0.15f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f)
                val path = Path()
                val fill = Path()
                for (i in 0 until n) {
                    val x = colW * (i + 0.5f)
                    if (i == 0) { path.moveTo(0f, y(bands[0])); path.lineTo(x, y(bands[0])) } else {
                        val px = colW * (i - 0.5f)
                        path.cubicTo((px + x) / 2, y(bands[i - 1]), (px + x) / 2, y(bands[i]), x, y(bands[i]))
                    }
                }
                path.lineTo(size.width, y(bands.last()))
                fill.addPath(path); fill.lineTo(size.width, size.height); fill.lineTo(0f, size.height); fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.35f * alpha), Color.Transparent)))
                drawPath(path, c.accent.copy(alpha = alpha), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                for (i in 0 until n) {
                    val x = colW * (i + 0.5f)
                    drawLine(c.text.copy(alpha = 0.18f), Offset(x, 8f), Offset(x, size.height - 8f), 4.dp.toPx(), StrokeCap.Round)
                    drawCircle(Color.White.copy(alpha = alpha), 9.dp.toPx(), Offset(x, y(bands[i])))
                    drawCircle(c.accent.copy(alpha = alpha), 5.dp.toPx(), Offset(x, y(bands[i])))
                }
            }
            Row(Modifier.fillMaxSize()) {
                bands.forEachIndexed { i, _ ->
                    var h by remember { mutableFloatStateOf(1f) }
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .semantics { contentDescription = "Banda ${FxPresets.LABELS[i]} Hz" }
                            .pointerInput(enabled) {
                                h = size.height.toFloat()
                                if (!enabled) return@pointerInput
                                detectVerticalDragGestures(onDragStart = { o -> onChange(i, ((0.5f - o.y / h) * 30f).coerceIn(-15f, 15f).roundToInt().toFloat()) }) { ch, _ ->
                                    onChange(i, ((0.5f - ch.position.y / h) * 30f).coerceIn(-15f, 15f).roundToInt().toFloat())
                                }
                            },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            FxPresets.LABELS.forEach { l -> Text(l, color = c.text2, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

/** Perilla giratoria (arrastrar hacia arriba/derecha para subir). */
@Composable
private fun Knob(label: String, value: Float, onChange: (Float) -> Unit) {
    val c = LocalAppColors.current
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(118.dp).semantics { contentDescription = "$label ${(v * 100).roundToInt()}%" }.pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onChange(v) }) { ch, d ->
                    ch.consume()
                    v = (v + (d.x - d.y) / 400f).coerceIn(0f, 1f)
                    onChange(v)
                }
            },
        ) {
            val r = size.minDimension / 2
            val start = 135f
            drawArc(c.text.copy(alpha = 0.15f), start, 270f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round), topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()))
            drawArc(c.accent, start, 270f * v, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round), topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF4A4A50), Color(0xFF1E1E22)), center, r * 0.72f), r * 0.72f)
            drawCircle(Color.White.copy(alpha = 0.08f), r * 0.72f, style = Stroke(1.dp.toPx()))
            val ang = Math.toRadians((start + 270f * v).toDouble())
            val dot = Offset(center.x + (r * 0.52f) * cos(ang).toFloat(), center.y + (r * 0.52f) * sin(ang).toFloat())
            drawCircle(c.accent, 6.dp.toPx(), dot)
        }
        Text(label, color = c.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Text("${(v * 100).roundToInt()}%", color = c.text2, fontSize = 12.sp)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, valueText: String, onChange: (Float) -> Unit) {
    val c = LocalAppColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueText, color = c.text2, fontSize = 14.sp)
        }
        Slider(value, onChange, colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.text.copy(alpha = 0.15f)))
    }
}

@Suppress("unused")
private val pi = PI
@Suppress("unused")
private val circ = CircleShape

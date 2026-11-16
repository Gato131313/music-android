package io.github.gato131313.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gato131313.music.MusicApp
import io.github.gato131313.music.data.stats.StatsCore
import io.github.gato131313.music.data.stats.StatsResult
import io.github.gato131313.music.ui.components.ArtImage
import io.github.gato131313.music.ui.components.GlassScaffold
import io.github.gato131313.music.ui.components.glassPanel
import io.github.gato131313.music.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PERIODS = listOf("Este mes", "Mes anterior", "Este año", "Todo")

@Composable
fun StatsScreen(a: UiActions) {
    val g = MusicApp.graph
    val c = LocalAppColors.current
    var period by remember { mutableIntStateOf(0) }
    val statsMap by g.user.stats.collectAsState()
    val lib = libraryState()
    val res by produceState<StatsResult?>(null, period, statsMap) {
        value = withContext(Dispatchers.Default) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val (from, to, prevFrom) = when (period) {
                0 -> Triple(today.withDayOfMonth(1), today.plusDays(1), today.withDayOfMonth(1).minusMonths(1))
                1 -> Triple(today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1), today.withDayOfMonth(1).minusMonths(2))
                2 -> Triple(today.withDayOfYear(1), today.plusDays(1), today.withDayOfYear(1).minusYears(1))
                else -> Triple(LocalDate.of(2000, 1, 1), today.plusDays(1), LocalDate.of(1999, 1, 1))
            }
            fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val events = g.db.stats().allEvents()
            StatsCore.compute(events, statsMap.values, ms(from), ms(to), ms(prevFrom), includeImported = period == 3) { key ->
                lib.byKey[key]?.let { it.title to it.artist }
            }
        }
    }
    GlassScaffold("Estadísticas", onBack = { a.back() }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PERIODS.forEachIndexed { i, p -> Chip(p, period == i, { period = i }) }
                }
            }
            val r = res ?: return@LazyColumn
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Big("${r.minutes}", "minutos", delta(r.minutes, r.prevMinutes, period), Modifier.weight(1f))
                    Big("${r.plays}", "reproducciones", delta(r.plays.toLong(), r.prevPlays.toLong(), period), Modifier.weight(1f))
                }
            }
            if (r.imported > 0) item { Text("Incluye ${r.imported} reproducciones importadas de tu app anterior.", color = c.text2, fontSize = 13.sp) }
            item {
                Card("Canciones más escuchadas") {
                    if (r.topSongs.isEmpty()) Text("Aún no hay escuchas en este periodo.", color = c.text2)
                    r.topSongs.forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = c.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                            ArtImage(lib.byKey[s.key], Modifier.size(42.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(lib.byKey[s.key]?.title ?: s.title, color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text((lib.byKey[s.key]?.displayArtist ?: s.artist ?: "") + " · ${s.plays} veces", color = c.text2, fontSize = 12.sp, maxLines = 1)
                            }
                            Text(s.move, color = if (s.move.startsWith("↓")) c.text3 else c.accent, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Card("Artistas más escuchados") {
                    r.topArtists.forEachIndexed { i, ar ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = c.accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                            Text(ar.name, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${ar.plays} · ${ar.ms / 60000} min", color = c.text2, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(ar.move, color = c.accent, fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Card("¿A qué hora escuchas?") {
                    Bars(r.hours.map { it.toFloat() }, labels = listOf("0", "6", "12", "18", "23"))
                    Text("Hora pico: ${r.peakHour}:00", color = c.text2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Big("${r.longestSessionMin}", "min · sesión más larga", null, Modifier.weight(1f))
                    Big("${r.discoveries}", "descubrimientos", null, Modifier.weight(1f))
                }
            }
            r.obsession?.let { (title, day, n) ->
                item {
                    Card("Obsesión") {
                        Text("\"$title\" sonó $n veces el ${day.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es")))}.", color = c.text, fontSize = 15.sp)
                    }
                }
            }
            item {
                Card("Horas por mes (${LocalDate.now().year})") {
                    Bars(r.monthly.toList(), labels = listOf("E", "M", "M", "J", "S", "D"))
                }
            }
            item {
                Card("Logros") {
                    r.badges.forEach { b ->
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Row {
                                Text((if (b.progress >= 1f) "★ " else "") + b.name, color = if (b.progress >= 1f) c.accent else c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${(b.progress * 100).toInt()}%", color = c.text2, fontSize = 12.sp)
                            }
                            Text(b.desc, color = c.text2, fontSize = 12.sp)
                            Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.text.copy(alpha = 0.1f))) {
                                Box(Modifier.fillMaxWidth(b.progress).height(5.dp).background(c.accent))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun delta(cur: Long, prev: Long, period: Int): String? {
    if (period == 3 || prev == 0L) return null
    val d = cur - prev
    return (if (d >= 0) "+$d" else "$d") + " vs. periodo anterior"
}

@Composable
private fun Big(value: String, label: String, sub: String?, modifier: Modifier) {
    val c = LocalAppColors.current
    Column(modifier.glassPanel().padding(14.dp)) {
        Text(value, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c.text2, fontSize = 13.sp)
        if (sub != null) Text(sub, color = c.accent, fontSize = 12.sp)
    }
}

@Composable
private fun Bars(values: List<Float>, labels: List<String>) {
    val c = LocalAppColors.current
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1e-3f)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * (v / max)
            drawRoundRect(c.accent.copy(alpha = 0.35f + 0.65f * (v / max)), Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h.coerceAtLeast(2f)), CornerRadius(4f, 4f))
        }
    }
    Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, color = c.text3, fontSize = 11.sp, modifier = Modifier.weight(1f)) } }
}

@Suppress("unused")
private fun unusedClick() = Modifier.clickable { }

package io.github.gato131313.music

import android.content.Intent
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Recorre la app en el emulador, guarda capturas en /data/local/tmp/capturas y mide fluidez (gfxinfo).
 * La ejecuta el workflow de GitHub Actions con la biblioteca de prueba (>1 700 archivos sucios).
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class CapturasTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val d = UiDevice.getInstance(inst)
    private val pkg = inst.targetContext.packageName
    private val out = "/data/local/tmp/capturas"
    private val log = StringBuilder()

    private fun note(s: String) {
        Log.i("CAPTURAS", s)
        log.append(s).append('\n')
    }

    private fun shot(name: String, wait: Long = 900) {
        Thread.sleep(wait)
        dismissSystemDialogs()
        d.waitForIdle(3000)
        d.executeShellCommand("screencap -p $out/$name.png")
        note("captura $name")
    }

    private fun click(sel: BySelector, timeout: Long = 8000): Boolean {
        dismissSystemDialogs()
        val o = d.wait(Until.findObject(sel), timeout)
        if (o == null) {
            note("NO ENCONTRADO: $sel")
            return false
        }
        o.click()
        return true
    }

    private fun tile(name: String) = By.descStartsWith("$name (")

    /** Cierra diálogos del sistema ("X no responde") que en emuladores lentos tapan la pantalla. */
    private fun dismissSystemDialogs() {
        repeat(3) {
            val anr = d.findObject(By.textContains("isn't responding")) ?: d.findObject(By.textContains("no responde")) ?: return
            note("diálogo del sistema: ${anr.text}")
            (d.findObject(By.text("Wait")) ?: d.findObject(By.text("Esperar")))?.click()
            Thread.sleep(800)
        }
    }

    private fun step(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            note("ERROR en $name: $t")
        }
    }

    private fun launch(extraRoute: String? = null) {
        val intent = inst.targetContext.packageManager.getLaunchIntentForPackage(pkg)!!
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (extraRoute != null) intent.putExtra("route", extraRoute)
        inst.targetContext.startActivity(intent)
        d.wait(Until.hasObject(By.pkg(pkg).depth(0)), 15000)
        dismissSystemDialogs()
    }

    private fun scrollPerf(name: String) {
        d.executeShellCommand("dumpsys gfxinfo $pkg reset")
        val w = d.displayWidth
        val h = d.displayHeight
        repeat(12) {
            d.swipe(w / 2, (h * 0.82).toInt(), w / 2, (h * 0.22).toInt(), 6)
            Thread.sleep(250)
        }
        repeat(4) {
            d.swipe(w / 2, (h * 0.25).toInt(), w / 2, (h * 0.85).toInt(), 6)
            Thread.sleep(250)
        }
        val info = d.executeShellCommand("dumpsys gfxinfo $pkg")
        val keep = info.lines().filter {
            it.contains("Total frames") || it.contains("Janky frames") || it.contains("percentile") || it.contains("Slow UI") || it.contains("Frame deadline")
        }.take(12)
        note("RENDIMIENTO $name:\n" + keep.joinToString("\n"))
    }

    @Test
    fun a_recorrido() {
        d.executeShellCommand("mkdir -p $out")
        val t0 = System.currentTimeMillis()
        launch()
        // Esperar a que la biblioteca esté cargada (contador con 3+ cifras).
        val ok = d.wait(Until.hasObject(By.text(SessionProbe.COUNT)), 120_000)
        note("biblioteca visible: $ok en ${System.currentTimeMillis() - t0} ms")
        Thread.sleep(3000)
        shot("01_inicio")
        step("menu") { click(By.desc("Menú")); shot("02_menu"); d.pressBack() }
        step("canciones") {
            click(tile("Canciones"))
            shot("03_canciones")
            scrollPerf("canciones")
            shot("03b_canciones_scroll", 400)
            d.pressBack()
        }
        step("artistas") { click(tile("Artistas")); shot("04_artistas"); scrollPerf("artistas"); d.pressBack() }
        step("albumes") { click(tile("Álbumes")); shot("05_albumes"); d.pressBack() }
        step("carpetas") { click(tile("Carpetas")); shot("06_carpetas"); d.pressBack() }
        step("genero") { click(tile("Género")); shot("07_genero"); d.pressBack() }
        step("video") { click(tile("Video")); shot("08_video"); d.pressBack() }
        step("buscar") {
            click(By.desc("Buscar"))
            Thread.sleep(600)
            d.executeShellCommand("input text amor")
            shot("09_buscar")
            d.pressBack(); d.pressBack()
        }
        step("revisar") {
            launch("review")
            shot("10_revisar_artistas", 2500)
            click(By.textStartsWith("Separar artista")); shot("11_revisar_separar")
            click(By.textStartsWith("Sin artista")); shot("12_revisar_sin_artista")
        }
        step("editar") {
            launch("songs")
            Thread.sleep(2500)
            val more = d.wait(Until.findObjects(By.desc("Opciones")), 5000)
            more?.firstOrNull()?.click()
            shot("13_menu_cancion")
            d.pressBack()
        }
        step("logs") {
            val lc = d.executeShellCommand("logcat -d -s Library:I MediaScanner:W AndroidRuntime:E")
            note("LOGCAT:\n" + lc.lines().takeLast(40).joinToString("\n"))
        }
        saveLog("informe1.txt")
    }

    private fun saveLog(name: String) {
        val f = java.io.File(inst.targetContext.getExternalFilesDir(null), name)
        f.writeText(log.toString())
        d.executeShellCommand("cp ${f.absolutePath} $out/$name")
    }

    private fun session() = SessionProbe.read(d, pkg)

    @Test
    fun b_reproduccion() {
        d.executeShellCommand("mkdir -p $out")
        launch("songs")
        SessionProbe.waitList(d)
        Thread.sleep(2500)
        step("tocar cancion") {
            val rows = d.wait(Until.findObjects(By.text(SessionProbe.ROW)), 8000)
            rows?.getOrNull(2)?.click()
            Thread.sleep(3000)
            note("tras tocar: " + session())
            shot("20_mini_reproductor")
        }
        step("abrir reproductor") {
            d.executeShellCommand("am start -n $pkg/io.github.gato131313.music.MainActivity --es route player")
            shot("21_reproductor", 2500)
            note("en reproductor: " + session())
        }
        step("cola") { click(By.desc("Cola")); shot("22_cola"); click(By.desc("Cola")) }
        step("tono velocidad") {
            click(By.desc("Tono y velocidad")); Thread.sleep(800)
            click(By.text("1.5x")); shot("23_tono_velocidad"); click(By.text("1.0x")); d.pressBack()
        }
        step("temporizador") { click(By.desc("Temporizador para dormir")); shot("24_temporizador"); d.pressBack() }
        step("botones -10 +10") {
            val a = session()
            click(By.desc("Adelantar 10 segundos")); click(By.desc("Adelantar 10 segundos"))
            Thread.sleep(800)
            note("adelantar 20 s: antes=$a despues=${session()}")
        }
        step("segundo plano") {
            d.pressHome()
            Thread.sleep(6000)
            note("en segundo plano (6 s): " + session())
            d.openNotification()
            shot("25_notificacion", 2000)
            d.pressBack()
        }
        step("audifonos") {
            val a = session()
            d.executeShellCommand("input keyevent 87") // MEDIA_NEXT, como el botón de audífonos Bluetooth
            Thread.sleep(2500)
            val b = session()
            note("boton siguiente de audifonos: antes=$a despues=$b")
            d.executeShellCommand("input keyevent 127") // MEDIA_PAUSE
            Thread.sleep(1500)
            note("boton pausa de audifonos: " + session())
            d.executeShellCommand("input keyevent 126") // MEDIA_PLAY
            Thread.sleep(1500)
            note("boton play de audifonos: " + session())
        }
        step("pantalla bloqueada") {
            d.sleep(); Thread.sleep(2000); d.wakeUp(); Thread.sleep(2000)
            shot("26_pantalla_bloqueada", 500)
            note("con pantalla bloqueada: " + session())
            d.executeShellCommand("wm dismiss-keyguard"); Thread.sleep(1500)
        }
        step("fusion") {
            d.executeShellCommand("am start -n $pkg/io.github.gato131313.music.MainActivity --es route player")
            Thread.sleep(2500)
            val a = session()
            d.executeShellCommand("logcat -c")
            inst.runOnMainSync {
                val c = MusicApp.graph.player.controller
                if (c != null && c.duration > 20_000) c.seekTo(c.duration - 9_000)
            }
            Thread.sleep(3500)
            shot("27_durante_fusion", 200)
            Thread.sleep(9000)
            val log = d.executeShellCommand("logcat -d -s PlaybackService:I").lines().filter { it.contains("fusión") }
            note("fusion: antes=$a despues=${session()} registro=${log.joinToString(" | ") { it.substringAfter("PlaybackService").trim() }}")
        }
        step("logs2") {
            val lc = d.executeShellCommand("logcat -d -s PlaybackService:W AndroidRuntime:E ExoPlayerImplInternal:E")
            note("LOGCAT2:\n" + lc.lines().takeLast(30).joinToString("\n"))
        }
        saveLog("informe2.txt")
    }

    @Test
    fun c_efectos() {
        d.executeShellCommand("mkdir -p $out")
        launch("effects")
        Thread.sleep(2500)
        shot("30_efectos")
        step("preset rock") {
            click(By.desc("Elegir preset"))
            click(By.text("Rock"))
            shot("31_efectos_rock")
            note("sesion con efectos: " + session())
        }
        step("perillas") {
            d.swipe(d.displayWidth / 2, (d.displayHeight * 0.8).toInt(), d.displayWidth / 2, (d.displayHeight * 0.3).toInt(), 10)
            shot("32_efectos_abajo")
        }
        saveLog("informe3.txt")
    }

    private fun settings(block: (io.github.gato131313.music.data.prefs.Settings) -> io.github.gato131313.music.data.prefs.Settings) =
        kotlinx.coroutines.runBlocking { MusicApp.graph.settings.update(block) }

    @Test
    fun d_fase4() {
        d.executeShellCommand("mkdir -p $out")
        launch()
        d.wait(Until.hasObject(By.text(SessionProbe.COUNT)), 60_000)
        Thread.sleep(1500)
        step("menu lista") { val m = d.wait(Until.findObjects(By.desc("Opciones de Favoritos")), 5000); m?.firstOrNull()?.click(); shot("40_menu_lista"); d.pressBack() }
        step("menu cancion") {
            launch("songs"); Thread.sleep(2500)
            d.wait(Until.findObjects(By.desc("Opciones")), 5000)?.getOrNull(1)?.click()
            shot("41_menu_cancion_completo"); d.pressBack()
        }
        step("temas") { launch("themes"); shot("42_temas", 2000) }
        step("ajustes") { launch("settings"); shot("43_ajustes", 2000) }
        step("ocultos") { launch("hidden"); shot("44_ocultos", 1500) }
        step("paisaje") {
            settings { it.copy(style = "paisaje", background = "atardecer") }
            launch(); Thread.sleep(3000); shot("45_inicio_paisaje")
            click(By.desc("Menú")); shot("46_menu_paisaje"); d.pressBack()
            settings { it.copy(style = "music", mode = "light") }
            launch(); Thread.sleep(3000); shot("47_inicio_blanco")
            settings { it.copy(style = "neon") }
            launch(); Thread.sleep(3000); shot("48_inicio_neon")
            settings { it.copy(style = "music", mode = "dark") }
        }
        saveLog("informe4.txt")
    }

    @Test
    fun e_fase5() {
        d.executeShellCommand("mkdir -p $out")
        val lib = SessionProbe.awaitLibrary()
        val paired = lib.songs.filter { it.hasVideo }
        note("parejas cancion+video: ${paired.size} de ${lib.songs.size} canciones; videos sueltos: ${lib.videos.size}; videos totales: ${lib.allVideos.size}")
        note("artistas: ${lib.artists.size}; dudas para revisar: ${lib.review.total} (uniones ${lib.review.merges.size}, separar ${lib.review.splits.size}, sin artista ${lib.review.unknownArtist.size})")
        note("ejemplos de nombres limpios: " + lib.songs.shuffled(java.util.Random(1)).take(12).joinToString(" | ") { "${it.fileName} => ${it.title} — ${it.displayArtist}" })
        step("estilos de portada") {
            val t = paired.firstOrNull() ?: lib.songs.first()
            d.executeShellCommand("am start -n $pkg/io.github.gato131313.music.MainActivity")
            Thread.sleep(1500)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { MusicApp.graph.player.play(listOf(t) + lib.songs.take(20), 0) }
            Thread.sleep(2500)
            settings { it.copy(coverStyle = "disco") }
            d.executeShellCommand("am start -n $pkg/io.github.gato131313.music.MainActivity --es route player")
            shot("50_portada_disco", 3500)
            settings { it.copy(coverStyle = "cuadrada") }
            shot("51_portada_cuadrada", 2500)
            settings { it.copy(coverStyle = "aurora") }
            shot("52_portada_aurora", 2500)
            settings { it.copy(coverStyle = "disco") }
        }
        step("video") {
            if (paired.isNotEmpty()) {
                var before = 0L
                inst.runOnMainSync { before = MusicApp.graph.player.position() }
                inst.runOnMainSync { MusicApp.graph.player.switchVideoMode(true) }
                shot("53_modo_video", 4000)
                val ui = MusicApp.graph.player.state.value
                note("modo video: isVideoMode=${ui.isVideoMode} archivo=${ui.playingFile?.fileName} posicion antes=$before " + session())
                inst.runOnMainSync { MusicApp.graph.player.switchVideoMode(false) }
                Thread.sleep(2000)
                note("de vuelta a canción: isVideoMode=${MusicApp.graph.player.state.value.isVideoMode} " + session())
            }
        }
        step("letra") {
            val real = lib.songs.firstOrNull { it.title == "Tu Falta De Querer" }
            if (real != null) inst.runOnMainSync { MusicApp.graph.player.play(listOf(real), 0) }
            Thread.sleep(2500)
            inst.runOnMainSync { MusicApp.graph.player.seekTo(60_000) }
            click(By.desc("Letra")); shot("54_letra", 8000)
            note("letra de: ${real?.title} — ${real?.displayArtist} (tiene video: ${real?.hasVideo})")
            click(By.desc("Ocultar letra"))
        }
        step("conductor") { launch("driver"); shot("55_conductor", 2500) }
        step("widgets") { launch("widgets"); shot("56_widgets", 1500) }
        saveLog("informe5.txt")
    }

    /** Total (propias + importadas) leído directo de la base. */
    private fun total(key: String): Int = kotlinx.coroutines.runBlocking {
        MusicApp.graph.db.stats().stat(key)?.let { it.plays + it.importedPlays } ?: 0
    }

    private fun check(ok: Boolean, what: String) = note((if (ok) "OK    " else "FALLA ") + what)

    /**
     * Imita la biblioteca y el CSV del hermano: 980 filas tomadas de canciones y videos del teléfono, como las
     * mostraba la app vieja (etiqueta o nombre de archivo, cortado con "…" en ~618 filas, artista o "<unknown>"),
     * con audio+video de la misma canción, grupos que se ven iguales ("Rauw Alejandro - Topic…") y 3 notas de voz.
     */
    private fun importarCsvComoAppVieja(lib: io.github.gato131313.music.data.model.Library) {
        val ctx = inst.targetContext
        val g = MusicApp.graph
        val all = (lib.songs + lib.allVideos).filter { !it.path.contains("/DCIM/") }
        val pick = all.shuffled(java.util.Random(11)).take(977)
        fun shown(t: io.github.gato131313.music.data.model.Track) = t.rawTitle?.takeIf { it.isNotBlank() } ?: t.fileName.substringBeforeLast('.')
        val lens = pick.map { shown(it).length }.sorted()
        val cut = lens[lens.size - 618]
        fun oldTitle(t: io.github.gato131313.music.data.model.Track): String { val s = shown(t); return if (s.length >= cut) s.take(cut - 2).trimEnd() + "…" else s }
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("titulo,artista,reproducciones\n")
        pick.forEachIndexed { i, t -> sb.append(q(oldTitle(t))).append(',').append(q(t.rawArtist?.takeIf { it.isNotBlank() } ?: "<unknown>")).append(',').append(985 - i).append('\n') }
        for (n in listOf(101, 100, 99)) sb.append("grabando #$n,<unknown>,1\n")
        val csv = sb.toString()
        val rows = io.github.gato131313.music.data.backup.ImportCore.parseCsv(csv)
        val looks = rows.groupBy { it.title.lowercase() + "|" + it.artist.lowercase() }.filter { it.value.size > 1 }
        note("CSV simulado: filas=${rows.size} cortadas=${rows.count { it.title.endsWith("…") }} grupos_que_se_ven_iguales=${looks.size} (${looks.values.sumOf { it.size }} filas), canciones+videos en el teléfono=${all.size}")
        looks.keys.take(6).forEach { note("  se ven iguales: $it ×${looks[it]!!.size}") }

        // 1) Antes de importar, algunas canciones ya tienen reproducciones propias en la app nueva
        val own = pick.filter { !it.isVideo }.take(5)
        kotlinx.coroutines.runBlocking { own.forEach { t -> repeat(3) { g.user.recordPlay(t, 60_000) } } }
        val before = all.associate { it.key to total(it.key) }

        // 2) Guardar el CSV en Descargas e importarlo por la pantalla, con el selector de archivos
        val resolver = ctx.contentResolver
        val coll = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(coll, android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "reproducciones_prueba.csv")
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv")
        })!!
        resolver.openOutputStream(uri, "wt")!!.use { it.write(csv.toByteArray()) }
        launch("import"); shot("60_importar_antes", 2000)
        var viaUi = false
        if (click(By.text("Elegir archivo CSV"))) {
            Thread.sleep(2500)
            var f = d.wait(Until.findObject(By.textStartsWith("reproducciones_prueba")), 5000)
            if (f == null) {
                (d.findObject(By.desc("Show roots")) ?: d.findObject(By.desc("Mostrar raíces")))?.click()
                Thread.sleep(1200)
                (d.findObject(By.text("Downloads")) ?: d.findObject(By.text("Descargas")))?.click()
                f = d.wait(Until.findObject(By.textStartsWith("reproducciones_prueba")), 6000)
            }
            if (f != null) {
                f.click()
                viaUi = d.wait(Until.hasObject(By.textStartsWith("Listo.")), 60000)
            } else { shot("60b_selector_sin_archivo", 300); d.pressBack() }
        }
        note("importado por la pantalla con el selector de Android: $viaUi")
        if (!viaUi) kotlinx.coroutines.runBlocking { g.backup.importCsv(ctx, uri) }
        d.findObject(By.textStartsWith("Listo."))?.let { note("mensaje en pantalla: ${it.text}") }
        shot("61_importar_resultado", 1500)

        val t0 = System.currentTimeMillis()
        val r1 = kotlinx.coroutines.runBlocking { g.backup.importCsv(ctx, uri) }
        val a = r1.assignment
        val how = a.links.groupingBy { it.how }.eachCount()
        val exact = a.links.count { l -> pick.getOrNull(l.index)?.key == l.track.key }
        val pairSwap = a.links.count { l -> val tr = pick.getOrNull(l.index); tr != null && tr.key != l.track.key && (tr.pairId == l.track.id || l.track.pairId == tr.id) }
        val sameLook = a.links.count { l -> val tr = pick.getOrNull(l.index); tr != null && tr.key != l.track.key && !(tr.pairId == l.track.id || l.track.pairId == tr.id) && shown(tr).take(cut - 2) == shown(l.track).take(cut - 2) }
        val shared = a.links.groupBy { it.track.key }.count { it.value.size > 1 }
        note("EMPATE: filas=${r1.rows} empatadas=${a.links.size} SIN_EMPATAR=${a.discarded.size} ${a.discarded.map { it.row.title }} (tardó ${System.currentTimeMillis() - t0} ms)")
        note("  seguras=${how[io.github.gato131313.music.data.backup.How.SEGURO]} probables=${how[io.github.gato131313.music.data.backup.How.PROBABLE]} compartidas=$shared")
        note("  a su archivo exacto=$exact · audio/video de la misma canción intercambiados=$pairSwap · nombres idénticos intercambiados=$sameLook · otra canción=${a.links.size - exact - pairSwap - sameLook}")
        a.links.filter { l -> val tr = pick.getOrNull(l.index); tr != null && tr.key != l.track.key && !(tr.pairId == l.track.id || l.track.pairId == tr.id) }.take(15)
            .forEach { l -> note("    \"${l.row.title}\" -> ${l.track.fileName} (era ${pick[l.index].fileName})") }
        check(a.discarded.map { it.row.title } == listOf("grabando #101", "grabando #100", "grabando #99"), "solo se descartan las 3 notas de voz")
        check(a.links.size == 977 && shared == 0, "las 977 canciones quedan empatadas, 1 a 1")
        val after1 = all.associate { it.key to total(it.key) }
        val expected = all.associate { t -> t.key to (before[t.key] ?: 0) + (r1.plays[t.key] ?: 0) }
        check(after1 == expected, "se SUMAN a las propias (ej. ${own[0].title}: ${before[own[0].key]} propias -> ${after1[own[0].key]})")

        // 3) Importar el mismo archivo otra vez: no se duplica
        kotlinx.coroutines.runBlocking { g.backup.importCsv(ctx, uri) }
        check(all.associate { it.key to total(it.key) } == after1, "reimportar el mismo archivo no duplica")

        // 4) Su caso: importó con la versión anterior y le quedaron 80 sin empatar. Al actualizar se empatan solas.
        val db = g.db
        val probables = a.links.filter { it.how == io.github.gato131313.music.data.backup.How.PROBABLE }
        val pend = (probables + a.links.filter { it.how != io.github.gato131313.music.data.backup.How.PROBABLE && it.row.title.endsWith("…") }).take(80)
        kotlinx.coroutines.runBlocking {
            db.withTransaction {
                for (l in pend) {
                    val s = db.stats().stat(l.track.key)!!
                    db.stats().upsertStat(s.copy(importedPlays = s.importedPlays - l.row.plays))
                }
                db.stats().clearAutoLinks()
                db.stats().clearUnmatched()
                db.stats().insertUnmatched(pend.map { io.github.gato131313.music.data.db.ImportUnmatched(title = it.row.title, artist = it.row.artist, plays = it.row.plays, favorite = false) })
            }
        }
        val sumBefore = all.sumOf { total(it.key) }
        note("simulado: versión anterior con ${pend.size} filas sin empatar (${pend.sumOf { it.row.plays }} reproducciones pendientes)")
        launch(); Thread.sleep(4000)
        val dlg = d.wait(Until.findObject(By.textContains("ya se revisaron solas")), 20000)
        note("aviso al abrir: ${dlg?.text}")
        shot("62_aviso_tras_actualizar", 500)
        check(dlg?.text?.contains("Se empataron todas") == true, "el aviso dice \"Se empataron todas\"")
        val left = kotlinx.coroutines.runBlocking { db.stats().pendingNow() }
        val sumAfter = all.sumOf { total(it.key) }
        check(left.isEmpty(), "no queda ninguna pendiente (${left.size})")
        check(sumAfter - sumBefore == pend.sumOf { it.row.plays }, "se sumaron exactamente las ${pend.sumOf { it.row.plays }} reproducciones pendientes (${sumAfter - sumBefore})")
        click(By.text("Listo"))
        launch(); Thread.sleep(5000)
        check(d.findObject(By.textContains("ya se revisaron solas")) == null && all.sumOf { total(it.key) } == sumAfter, "al abrir otra vez no se repite ni se duplica")

        // 5) Corregir (opcional) una unión automática
        launch("import"); Thread.sleep(2000)
        click(By.text("Ver")); shot("63_revisar_uniones", 1500)
        val links = kotlinx.coroutines.runBlocking { db.stats().manualNow() }.filter { it.how == "PROBABLE" }
        if (links.isNotEmpty()) {
            val l = links.maxBy { it.plays }
            val sug = io.github.gato131313.music.data.backup.ImportCore.suggestions(io.github.gato131313.music.data.backup.CsvRow(l.title, l.artist, l.plays, false), lib.songs + lib.allVideos).first { it.key != l.key }
            val a0 = total(l.key); val b0 = total(sug.key)
            kotlinx.coroutines.runBlocking { g.backup.relink(l, sug.key) }
            check(total(l.key) == a0 - l.plays && total(sug.key) == b0 + l.plays, "corregir mueve las ${l.plays} reproducciones a la otra canción")
            kotlinx.coroutines.runBlocking { g.backup.relink(l.copy(key = sug.key, how = "MANO"), l.key) }
        }
        launch("smart/mas"); shot("64_mas_reproducidas", 2500)
    }

    /** Dibuja el widget fuera de la pantalla de inicio a un tamaño dado (dp) y lo devuelve como imagen. */
    @OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
    private fun renderWidget(state: io.github.gato131313.music.widget.WidgetState, wDp: Int, hDp: Int): android.graphics.Bitmap {
        val ctx = inst.targetContext
        val rv = kotlinx.coroutines.runBlocking {
            androidx.glance.appwidget.GlanceRemoteViews().compose(ctx, androidx.compose.ui.unit.DpSize(wDp.dp, hDp.dp)) {
                io.github.gato131313.music.widget.WidgetContent(ctx, state)
            }.remoteViews
        }
        val dens = ctx.resources.displayMetrics.density
        val w = (wDp * dens).toInt(); val h = (hDp * dens).toInt()
        var bmp: android.graphics.Bitmap? = null
        inst.runOnMainSync {
            val parent = android.widget.FrameLayout(ctx)
            val v = rv.apply(ctx, parent)
            parent.addView(v, android.widget.FrameLayout.LayoutParams(w, h))
            parent.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, w, h)
            val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            // Las esquinas redondeadas del sistema se aplican al recortar: las imitamos aquí
            val path = android.graphics.Path().apply { addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 22 * dens, 22 * dens, android.graphics.Path.Direction.CW) }
            cv.clipPath(path)
            parent.draw(cv)
            bmp = b
        }
        return bmp!!
    }

    /** Hoja con todos los tamaños de un estilo, sobre un fondo de pantalla de inicio. */
    private fun widgetSheet(style: String, name: String) {
        val ctx = inst.targetContext
        val g = MusicApp.graph
        val state = kotlinx.coroutines.runBlocking { io.github.gato131313.music.widget.MusicWidget.load(ctx, style) }
        val sizes = listOf("2x1" to (130 to 60), "3x1" to (200 to 60), "4x1" to (290 to 60), "4x2" to (290 to 130), "4x3" to (290 to 210), "4x4" to (290 to 300))
        val dens = ctx.resources.displayMetrics.density
        val pad = (16 * dens).toInt()
        val bitmaps = sizes.map { (n, s) -> n to renderWidget(state, s.first, s.second) }
        val sheetW = (290 * dens).toInt() + pad * 2
        val sheetH = bitmaps.sumOf { it.second.height + pad + (18 * dens).toInt() } + pad
        val sheet = android.graphics.Bitmap.createBitmap(sheetW, sheetH, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(sheet)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, sheetH.toFloat(), 0xFF3B5B7A.toInt(), 0xFF1D2A3A.toInt(), android.graphics.Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, sheetW.toFloat(), sheetH.toFloat(), p)
        p.shader = null; p.color = android.graphics.Color.WHITE; p.textSize = 13 * dens
        var y = pad
        for ((n, b) in bitmaps) {
            cv.drawText("$name · $n", pad.toFloat(), y + 12 * dens, p)
            y += (18 * dens).toInt()
            cv.drawBitmap(b, pad.toFloat(), y.toFloat(), null)
            y += b.height + pad
        }
        val f = java.io.File(ctx.getExternalFilesDir("widget"), "widget_$style.png")
        f.outputStream().use { sheet.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        d.executeShellCommand("cp ${f.absolutePath} $out/7${"pcm".indexOf(style.first())}_widget_$style.png")
        note("widget $style: ${state.title} / ${state.artist} · sonando=${state.playing} · tamaños ${sizes.joinToString { it.first }} -> ${f.name}")
    }

    @Test
    fun g_fase7() {
        d.executeShellCommand("mkdir -p $out")
        val ctx = inst.targetContext
        val g = MusicApp.graph
        val lib = SessionProbe.awaitLibrary()
        // Canción sin portada propia (para comprobar el fotograma del video o la portada generada)
        val noArt = lib.songs.filter { it.pairId == null }.firstOrNull { io.github.gato131313.music.playback.ArtProvider.real(ctx, it, lib, 128) == null }
        val withVideo = lib.songs.firstOrNull { it.hasVideo && io.github.gato131313.music.playback.ArtProvider.real(ctx, it.copy(pairId = null), lib, 128) == null }
        note("sin portada: ${noArt?.fileName} · sin portada pero con video: ${withVideo?.fileName}")

        step("contador en listas y en vivo") {
            val t = noArt ?: lib.songs.first()
            val n0 = g.user.playsOf(t, lib)
            launch("songs"); shot("70_canciones_contador", 2500)
            inst.runOnMainSync { g.player.play(listOf(t), 0) }
            Thread.sleep(2500)
            launch("smart/mas"); shot("71_mas_reproducidas_antes", 1500)
            Thread.sleep(33_000) // escuchar de verdad: cuenta a los 30 s
            shot("72_mas_reproducidas_despues", 1000)
            val n1 = kotlinx.coroutines.runBlocking { kotlinx.coroutines.delay(500); g.user.playsOf(t, lib, g.db.stats().statsNow().associateBy { it.key }) }
            check(n1 == n0 + 1, "al escuchar 30 s el contador sube solo: ${t.title} $n0 -> $n1")
            val shownText = d.findObject(By.desc("$n1 reproducciones"))
            check(shownText != null, "la fila muestra 🎧 $n1 en vivo")
            val mas = g.user.mostPlayed(lib).take(30).map { g.user.playsOf(it, lib) }
            check(mas == mas.sortedDescending(), "Más reproducidas va de mayor a menor (${mas.take(8)})")
            kotlinx.coroutines.runBlocking { g.settings.update { it.copy(showPlays = false) } }
            launch("songs"); shot("73_canciones_sin_contador", 2000)
            check(d.findObject(By.descContains(" reproducciones")) == null, "el interruptor de Ajustes oculta el contador")
            kotlinx.coroutines.runBlocking { g.settings.update { it.copy(showPlays = true) } }
            launch("settings"); d.wait(Until.findObject(By.textContains("Mostrar reproducciones")), 5000); shot("74_ajustes_contador", 1000)
        }

        step("notificacion con portada y botones") {
            val t = withVideo ?: noArt ?: lib.songs.first()
            inst.runOnMainSync { g.player.play(listOf(t, lib.songs[1], lib.songs[2]), 0) }
            Thread.sleep(3000)
            d.pressHome(); Thread.sleep(1500)
            d.openNotification(); Thread.sleep(2500)
            shot("75_notificacion", 500)
            val like = d.findObject(By.desc("Me gusta"))
            note("botón Me gusta en la notificación: ${like != null} · aleatorio: ${d.findObject(By.descStartsWith("Aleatorio")) != null}")
            val fav0 = g.user.isFavorite(t.key)
            like?.click(); Thread.sleep(1500)
            check(like != null && g.user.isFavorite(t.key) != fav0, "\"Me gusta\" desde la notificación marca la canción")
            d.findObject(By.descStartsWith("Aleatorio"))?.click(); Thread.sleep(1200)
            check(kotlinx.coroutines.runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { g.hub.player?.shuffleModeEnabled } } == true, "aleatorio desde la notificación")
            shot("76_notificacion_tras_botones", 800)
            d.pressBack()
            d.openQuickSettings(); Thread.sleep(2500); shot("77_panel_controles", 500); d.pressBack(); d.pressBack()
            // Pantalla de bloqueo
            d.sleep(); Thread.sleep(1500); d.wakeUp(); Thread.sleep(2500)
            shot("78_pantalla_bloqueo", 500)
            d.executeShellCommand("wm dismiss-keyguard"); Thread.sleep(1500)
            val meta = d.executeShellCommand("dumpsys media_session").lines().filter { it.contains("metadata") || it.contains("description=") }.take(4)
            note("sesión del sistema: " + meta.joinToString(" | ").take(400))
        }

        step("widget tamaños y estilos") {
            for ((st, name) in listOf("portada" to "Portada", "cristal" to "Cristal", "minimo" to "Mínimo")) widgetSheet(st, name)
            // Sin música sonando: muestra la última canción
            inst.runOnMainSync { g.hub.player?.pause() }
            Thread.sleep(1500)
            val idle = kotlinx.coroutines.runBlocking { io.github.gato131313.music.widget.MusicWidget.load(ctx) }
            check(idle.hasTrack && !idle.playing, "en pausa el widget muestra la última canción (${idle.title}) con botón de play")
            launch("settings"); d.wait(Until.findObject(By.text("Widget de pantalla de inicio")), 5000)
            d.findObject(By.text("Widget de pantalla de inicio"))?.let { d.findObject(By.scrollable(true))?.let { s -> androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true)).scrollTextIntoView("Opacidad del fondo") } }
            shot("79_ajustes_widget", 1200)
            // Ponerlo de verdad en la pantalla de inicio
            launch("widgets"); click(By.text("Agregar a la pantalla de inicio")); Thread.sleep(2500)
            shot("7a_pin_dialogo", 300)
            (d.findObject(By.text("Add")) ?: d.findObject(By.text("Agregar")) ?: d.findObject(By.textContains("Add automatically")))?.click()
            Thread.sleep(2500); d.pressHome(); Thread.sleep(3000)
            shot("7b_inicio_con_widget", 500)
            val ids = kotlinx.coroutines.runBlocking { androidx.glance.appwidget.GlanceAppWidgetManager(ctx).getGlanceIds(io.github.gato131313.music.widget.MusicWidget::class.java) }
            note("widgets puestos en la pantalla de inicio: ${ids.size}")
        }
        saveLog("informe7.txt")
    }

    @Test
    fun f_fase6() {
        d.executeShellCommand("mkdir -p $out")
        val ctx = inst.targetContext
        val g = MusicApp.graph
        val lib = SessionProbe.awaitLibrary()
        step("importar csv") { importarCsvComoAppVieja(lib) }
        step("respaldo") {
            val f = java.io.File(ctx.cacheDir, "respaldo.json")
            kotlinx.coroutines.runBlocking { g.backup.exportTo(ctx, android.net.Uri.fromFile(f)) }
            note("respaldo: ${f.length()} bytes")
            val b = kotlinx.coroutines.runBlocking { g.backup.restoreFrom(ctx, android.net.Uri.fromFile(f)) }
            note("restaurado: favoritos=${b.favorites.size} estadisticas=${b.stats.size} playlists=${b.playlists.size}")
            launch("backup"); shot("63_respaldo", 1500)
        }
        step("memoria") {
            val mi = d.executeShellCommand("dumpsys meminfo $pkg").lines().filter { l -> listOf("Java Heap", "Native Heap", "Code:", "Graphics", "Private Other", "TOTAL PSS", "TOTAL RSS").any { l.contains(it) } }
            note("memoria:\n" + mi.joinToString("\n"))
        }
        saveLog("informe6.txt")
    }
}

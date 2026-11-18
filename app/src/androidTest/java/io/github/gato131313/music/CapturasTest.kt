package io.github.gato131313.music

import android.content.Intent
import android.util.Log
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
     * Simula el CSV sacado del video de la app vieja: título tal cual lo mostraba (etiqueta o nombre de archivo),
     * cortado con "…" si era largo, artista de la etiqueta o "<unknown>". Lo importa por la pantalla real
     * (selector de archivos de Android), reimporta, une a mano y comprueba que las sumas no se dupliquen.
     */
    private fun importarCsvComoAppVieja(lib: io.github.gato131313.music.data.model.Library) {
        val ctx = inst.targetContext
        val g = MusicApp.graph
        val pick = lib.songs.shuffled(java.util.Random(11)).take(60)
        fun oldTitle(t: io.github.gato131313.music.data.model.Track): String {
            val raw = t.rawTitle?.takeIf { it.isNotBlank() } ?: t.fileName.substringBeforeLast('.')
            return if (raw.length > 24) raw.take(22).trimEnd() + "…" else raw
        }
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("titulo,artista,reproducciones\n")
        pick.forEachIndexed { i, t ->
            val art = t.rawArtist?.takeIf { it.isNotBlank() } ?: "<unknown>"
            sb.append(q(oldTitle(t))).append(',').append(q(art)).append(',').append(300 - i * 4).append('\n')
        }
        // La misma fila vista dos veces con otro número (audio y video, o dos canciones con el mismo título cortado)
        val twin = pick[10]
        sb.append(q(oldTitle(twin))).append(',').append(q(twin.rawArtist?.takeIf { it.isNotBlank() } ?: "<unknown>")).append(",7\n")
        sb.append("Cancion Inventada,Nadie,9\nOtra Que No Existe…,<unknown>,4\n")
        val csv = sb.toString()
        note("CSV de prueba (${pick.size + 2} filas), primeras líneas:\n" + csv.lines().take(12).joinToString("\n"))

        // 1) Antes de importar, algunas canciones ya tienen reproducciones propias en la app nueva
        val own = pick.take(5)
        kotlinx.coroutines.runBlocking { own.forEach { t -> repeat(3) { g.user.recordPlay(t, 60_000) } } }
        val before = pick.associate { it.key to total(it.key) }

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
                shot("60b_selector", 300)
                f.click()
                viaUi = d.wait(Until.hasObject(By.textStartsWith("Listo:")), 20000)
            } else {
                shot("60b_selector_sin_archivo", 300)
                d.pressBack()
            }
        }
        note("importado por la pantalla con el selector de Android: $viaUi")
        if (!viaUi) kotlinx.coroutines.runBlocking { g.backup.importCsv(ctx, uri) }
        d.findObject(By.textStartsWith("Listo:"))?.let { note("mensaje en pantalla: ${it.text}") }
        shot("61_importar_resultado", 1500)

        val r1 = kotlinx.coroutines.runBlocking { g.backup.importCsv(ctx, uri) }
        note("importar: filas=${r1.rows} empatadas=${r1.rows - r1.unmatched.size} canciones=${r1.plays.size} sin_empatar=${r1.unmatched.size}")
        r1.unmatched.forEach { u -> note("  sin empatar: ${u.title} / ${u.artist}") }
        val after1 = pick.associate { it.key to total(it.key) }
        val expected = pick.associate { t -> t.key to (before[t.key] ?: 0) + (r1.plays[t.key] ?: 0) }
        check(after1 == expected, "se SUMAN a las propias (ej. ${own[0].title}: ${before[own[0].key]} propias + ${r1.plays[own[0].key]} importadas = ${after1[own[0].key]})")
        val wrong = pick.filterIndexed { i, t -> r1.plays[t.key] != null && r1.plays[t.key] != 300 - i * 4 }
        check(r1.plays[twin.key] == null && r1.unmatched.count { it.plays == 7 || it.plays == 260 } == 2, "filas que se ven iguales no se suman solas: quedan las 2 para unir a mano")
        check(wrong.isEmpty(), "cada canción recibió el número de SU fila (mal: ${wrong.map { it.title }})")

        // 3) Importar el mismo archivo otra vez: no se duplica
        kotlinx.coroutines.runBlocking { g.backup.importCsv(ctx, uri) }
        val after2 = pick.associate { it.key to total(it.key) }
        check(after2 == after1, "reimportar el mismo archivo no duplica")

        // 4) Después sigue contando normal
        kotlinx.coroutines.runBlocking { g.user.recordPlay(own[0], 60_000) }
        check(total(own[0].key) == after2[own[0].key]!! + 1, "una reproducción nueva suma 1 (${after2[own[0].key]} -> ${total(own[0].key)})")

        // 5) Unir a mano una pendiente desde la pantalla y reimportar
        launch("import"); shot("62_pendientes", 2000)
        val pend = kotlinx.coroutines.runBlocking { g.db.stats().unmatchedNow() }
            .filter { it.title != "Cancion Inventada" && !it.title.startsWith("Otra Que") }
        if (pend.isNotEmpty()) {
            val u = pend.first()
            d.wait(Until.findObject(By.text(u.title)), 5000)?.click()
            shot("63_unir_dialogo", 1500)
            val target = io.github.gato131313.music.data.backup.ImportCore.suggestions(
                io.github.gato131313.music.data.backup.CsvRow(u.title, u.artist, u.plays, false), g.library.library.value.songs,
            ).first()
            val opt = d.wait(Until.findObject(By.text(target.title)), 4000)
            val t0 = total(target.key)
            if (opt != null) opt.click() else kotlinx.coroutines.runBlocking { g.backup.matchManually(u, target.key) }
            Thread.sleep(1500)
            note("unida a mano: \"${u.title}\" -> ${target.title} (+${u.plays}) por pantalla=${opt != null}")
            val t1 = total(target.key)
            check(t1 == t0 + u.plays, "unir a mano suma sus reproducciones ($t0 -> $t1)")
            val r3 = kotlinx.coroutines.runBlocking { g.backup.importCsv(ctx, uri) }
            check(total(target.key) == t1, "tras reimportar, la unida a mano conserva su suma sin duplicar ($t1 -> ${total(target.key)})")
            check(r3.unmatched.size == r1.unmatched.size - 1, "la unida a mano ya no sale como pendiente (${r1.unmatched.size} -> ${r3.unmatched.size})")
            launch("import"); shot("64_tras_unir", 2000)
        } else note("no hubo pendientes reales para unir a mano")
        launch("smart/mas"); shot("65_mas_reproducidas", 2500)
        launch("stats"); Thread.sleep(2000); click(By.text("Todo")); shot("66_estadisticas", 2500)
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

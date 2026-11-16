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
        d.waitForIdle(3000)
        d.executeShellCommand("screencap -p $out/$name.png")
        note("captura $name")
    }

    private fun click(sel: BySelector, timeout: Long = 8000): Boolean {
        val o = d.wait(Until.findObject(sel), timeout)
        if (o == null) {
            note("NO ENCONTRADO: $sel")
            return false
        }
        o.click()
        return true
    }

    private fun tile(name: String) = By.text(Pattern.compile("^" + Pattern.quote(name) + "(\\s.*)?$", Pattern.DOTALL))

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
        val ok = d.wait(Until.hasObject(By.text(Pattern.compile("\\(\\d{3,}\\)"))), 120_000)
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
            val w = d.displayWidth
            val clock = d.findObject(By.text(SessionProbe.CLOCK))
            if (clock != null) d.click((w * 0.955).toInt(), clock.visibleBounds.top - 28)
            var players = ""
            repeat(12) {
                Thread.sleep(1000)
                val n = d.executeShellCommand("dumpsys audio").lines().count { it.contains("USAGE_MEDIA") && it.contains("started") }
                players += "$n "
            }
            note("fusion: antes=$a despues=${session()} reproductores_por_segundo=[$players]")
            shot("27_tras_fusion", 300)
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
}

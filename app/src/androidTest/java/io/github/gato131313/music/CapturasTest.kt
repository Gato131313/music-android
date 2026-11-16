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
    fun recorrido() {
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
            click(By.text("Canciones"))
            shot("03_canciones")
            scrollPerf("canciones")
            shot("03b_canciones_scroll", 400)
            d.pressBack()
        }
        step("artistas") { click(By.text("Artistas")); shot("04_artistas"); scrollPerf("artistas"); d.pressBack() }
        step("albumes") { click(By.text("Álbumes")); shot("05_albumes"); d.pressBack() }
        step("carpetas") { click(By.text("Carpetas")); shot("06_carpetas"); d.pressBack() }
        step("genero") { click(By.text("Género")); shot("07_genero"); d.pressBack() }
        step("video") { click(By.text("Video")); shot("08_video"); d.pressBack() }
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
        val f = java.io.File(inst.targetContext.getExternalFilesDir(null), "informe.txt")
        f.writeText(log.toString())
        d.executeShellCommand("cp ${f.absolutePath} $out/informe.txt")
    }
}

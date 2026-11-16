package io.github.gato131313.music

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Ayudas para leer el estado de la sesión multimedia desde el sistema (dumpsys). */
object SessionProbe {
    fun read(d: UiDevice, pkg: String): String {
        val s = d.executeShellCommand("dumpsys media_session")
        val i = s.indexOf(pkg)
        val part = if (i >= 0) s.substring(i, minOf(s.length, i + 2500)) else s.take(1500)
        val state = Regex("state=PlaybackState \\{state=(\\w+)").find(part)?.groupValues?.get(1)
            ?: Regex("state=(\\d+)").find(part)?.groupValues?.get(1)
        val desc = Regex("description=([^\\n]*)").find(part)?.groupValues?.get(1)
        val pos = Regex("position=(\\d+)").find(part)?.groupValues?.get(1)
        return "estado=$state pos=$pos cancion=$desc"
    }

    val TIME: Pattern = Pattern.compile(".*\\d\\d:\\d\\d.*", Pattern.DOTALL)
    val ROW: Pattern = Pattern.compile(".* · \\d\\d:\\d\\d.*", Pattern.DOTALL)
    val COUNT: Pattern = Pattern.compile(".*\\(\\d{3,}\\).*", Pattern.DOTALL)
    val CLOCK: Pattern = Pattern.compile("\\d\\d:\\d\\d")

    fun awaitLibrary(): io.github.gato131313.music.data.model.Library = runBlocking {
        withTimeout(90_000) { MusicApp.graph.library.libraryOrNull.filterNotNull().first { it.songs.isNotEmpty() } }
    }

    fun waitList(d: UiDevice) = d.wait(Until.hasObject(By.text(TIME)), 60_000)
}

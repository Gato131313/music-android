package io.github.gato131313.music

import io.github.gato131313.music.data.clean.NameCleaner
import org.junit.Test

class PerfProbeTest {
    @Test fun etapas() {
        val names = (1..3000).map { "Artista Raro $it - Canción Rara $it Amor (Official Music Video) [HD].mp3" }
        fun t(label: String, f: () -> Unit) { val a = System.nanoTime(); f(); println("$label: ${(System.nanoTime() - a) / 1_000_000} ms") }
        t("cleanTitle") { names.forEach { NameCleaner.cleanTitle(it + "x") } }
        t("cleanArtist") { names.forEach { NameCleaner.cleanArtist("Artista $it - Topic") } }
        t("clean") { names.forEach { NameCleaner.clean(it, null, null, null) } }
        t("titleKey") { names.forEach { NameCleaner.titleKey("T $it (feat. X)") } }
        t("key") { names.forEach { NameCleaner.key("K $it") } }
        t("fold") { names.forEach { NameCleaner.fold(it) } }
        t("levenshtein") { for (i in 0 until 300) for (j in 0 until 300) NameCleaner.levenshtein("artistanumero$i", "artistanumero$j", 2) }
    }
}

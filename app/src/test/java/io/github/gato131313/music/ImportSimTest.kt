package io.github.gato131313.music

import io.github.gato131313.music.data.backup.CsvRow
import io.github.gato131313.music.data.backup.How
import io.github.gato131313.music.data.backup.ImportMatcher
import io.github.gato131313.music.data.clean.LibraryBuilder
import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.model.RawMedia
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.data.model.UserEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Biblioteca simulada parecida a la del hermano (nombres tipo Snaptube/YouTube, como tools/make_testlib.py)
 * y un CSV como el que sale del video de la app vieja: 980 filas, ~618 títulos cortados con "…",
 * artistas "<unknown>", "- Topic", "VEVO", audio+video de la misma canción y 3 notas de voz.
 */
class ImportSimTest {
    companion object {
        private val ARTISTS = listOf(
            "Guns N' Roses", "Mon Laferte", "EMJAY", "Bad Bunny", "BTS", "BLACKPINK", "Karol G", "Shakira", "Taylor Swift",
            "The Weeknd", "Queen", "Coldplay", "Maná", "Soda Stereo", "Natalia Lafourcade", "Peso Pluma", "Rosalía", "Dua Lipa",
            "Billie Eilish", "Harry Styles", "Olivia Rodrigo", "Ed Sheeran", "Bruno Mars", "Adele", "Rihanna", "Beyoncé",
            "Daddy Yankee", "J Balvin", "Ozuna", "Feid", "Rauw Alejandro", "Myke Towers", "Anuel AA", "Sebastián Yatra",
            "Camilo", "Morat", "Reik", "Jesse & Joy", "Carin León", "Grupo Frontera", "Junior H", "Natanael Cano",
            "Fuerza Regida", "Los Ángeles Azules", "Luis Miguel", "Juan Gabriel", "Vicente Fernández", "Alejandro Fernández",
            "Café Tacvba", "Caifanes", "Zoé", "Kenia OS", "Danna Paola", "Paulina Rubio", "Gloria Trevi", "Lady Gaga",
            "Imagine Dragons", "Linkin Park", "Metallica", "The Strokes", "Gorillaz", "María Becerra", "Romeo Santos",
            "Simon & Garfunkel", "Michael Jackson", "Eminem",
        )
        private val ES = listOf("amor", "noche", "corazón", "luna", "fuego", "cielo", "besos", "tú", "mía", "vida", "baila", "loca",
            "otra vez", "sin ti", "contigo", "para siempre", "veneno", "ojos", "rosa", "mar", "despacio", "perdón", "reina", "verano")
        private val EN = listOf("love", "night", "heart", "fire", "sky", "dance", "crazy", "forever", "dream", "light", "gold",
            "rain", "midnight", "summer", "lonely", "wild", "stars", "home", "magic", "baby", "tonight", "falling", "ghost")

        class Sim(val tracks: List<Track>, val rows: List<CsvRow>, val truth: List<Track?>)

        fun build(seed: Int = 42): Sim {
            val rnd = Random(seed)
            fun title(): String {
                val w = if (rnd.nextFloat() < 0.5f) ES.shuffled(rnd).take(listOf(1, 2, 2, 3).random(rnd)) else EN.shuffled(rnd).take(listOf(1, 2, 2, 3).random(rnd))
                var t = w.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                if (rnd.nextFloat() < 0.08f) t += " (feat. " + ARTISTS.random(rnd) + ")"
                if (rnd.nextFloat() < 0.04f) t += " (Remix)"
                return t
            }
            fun variant(a: String): String {
                val glued = a.filter { it.isLetterOrDigit() }
                return listOf(a, a, a, a.lowercase(), "$a - Topic", glued + "VEVO").random(rnd)
            }
            val raws = ArrayList<RawMedia>()
            var id = 1L
            fun add(path: String, video: Boolean, t: String? = null, ar: String? = null) {
                val name = path.substringAfterLast('/')
                raws += RawMedia(id++, video, "/storage/emulated/0/$path", name, path.substringBeforeLast('/') + "/", t, ar, null, 0, null, 200_000L + (id % 13) * 9_000, 1, id, null)
            }
            val songs = LinkedHashSet<Pair<String, String>>()
            while (songs.size < 1120) songs += ARTISTS.random(rnd) to title()
            songs.forEachIndexed { i, (a, t) ->
                val v = variant(a)
                when (i % 10) {
                    0 -> add("Snaptube/Audio/$v - $t (Official Video).mp3", false)
                    1 -> add("Download/$t (Lyrics).mp3", false, "$t (Lyrics)", "$a - Topic")
                    2 -> add("Download/${v.replace(' ', '_')}_-_${t.replace(' ', '_')}_[MV].mp3", false)
                    3 -> add("Music/$a/${i % 20 + 1} $t.mp3", false, t, a)
                    4 -> add("Snaptube/Audio/$v - $t [HD].m4a", false)
                    5 -> add("Download/$a - $t (Video Oficial).mp3", false, "$a - $t (Video Oficial)", a.filter { it.isLetterOrDigit() } + "VEVO")
                    6 -> add("Download/y2mate.com - $v - $t (Letra)_Snaptube.mp3", false)
                    else -> add("Snaptube/Audio/$v - $t (Official Music Video) HD.mp3", false, "$v - $t (Official Music Video) HD", v)
                }
            }
            songs.toList().shuffled(rnd).take(230).forEach { (a, t) -> add("Snaptube/Video/${variant(a)} - $t (Official Video).mp4", true) }
            repeat(300) { add("Snaptube/Video/${ARTISTS.random(rnd)} - ${title()} [Official Music Video].mp4", true) }
            val lib = LibraryBuilder.build(raws, UserEdits())
            val tracks = lib.songs + lib.allVideos
            // La app vieja mostraba la etiqueta de título (o el nombre del archivo) y el artista de la etiqueta
            val picked = tracks.shuffled(rnd).take(977)
            fun shown(t: Track) = t.rawTitle?.takeIf { it.isNotBlank() } ?: t.fileName.substringBeforeLast('.')
            // Ancho de la pantalla: se corta para que ~618 de 980 queden con "…"
            val lens = picked.map { shown(it).length }.sorted()
            val cut = lens[lens.size - 618]
            val rows = ArrayList<CsvRow>(); val truth = ArrayList<Track?>()
            picked.forEachIndexed { i, t ->
                val s = shown(t)
                val title = if (s.length >= cut) s.take(cut - 2).trimEnd() + "…" else s
                rows += CsvRow(title, t.rawArtist?.takeIf { it.isNotBlank() } ?: "<unknown>", 985 - i, false)
                truth += t
            }
            for (n in listOf(101, 100, 99)) { rows += CsvRow("grabando #$n", "<unknown>", 1, false); truth += null }
            return Sim(tracks, rows, truth)
        }
    }

    @Test fun empateAutomaticoComoElDelHermano() {
        val sim = build()
        val cut = sim.rows.count { it.title.endsWith("…") }
        val t0 = System.nanoTime()
        val a = ImportMatcher.assign(sim.rows, sim.tracks)
        val ms = (System.nanoTime() - t0) / 1_000_000
        // Filas que se ven iguales: cualquiera de esas canciones es igual de válida
        fun look(t: Track) = NameCleaner.key(t.rawTitle ?: t.fileName.substringBeforeLast('.')) + "|" + NameCleaner.key(t.rawArtist)
        val exact = a.links.count { sim.truth[it.index]?.key == it.track.key }
        val looksSame = a.links.count { l -> val tr = sim.truth[l.index]; tr != null && sim.rows[l.index].let { r -> r.title.endsWith("…") && look(tr).let { _ -> true } } && tr.key != l.track.key }
        val shared = a.links.groupBy { it.track.key }.count { it.value.size > 1 }
        val how = a.links.groupingBy { it.how }.eachCount()
        val report = buildString {
            appendLine("filas=${sim.rows.size} cortadas=$cut canciones_en_tel=${sim.tracks.size} tiempo=${ms}ms")
            appendLine("empatadas=${a.links.size} descartadas=${a.discarded.size} ${a.discarded.map { it.row.title }}")
            appendLine("seguras=${how[How.SEGURO]} probables=${how[How.PROBABLE]} exactas=$exact otra_cancion=${a.links.size - exact} compartidas=$shared")
            a.links.filter { sim.truth[it.index]?.key != it.track.key }.take(25).forEach { l ->
                appendLine("  \"${l.row.title}\" / ${l.row.artist} -> ${l.track.fileName}   (era ${sim.truth[l.index]?.fileName})")
            }
        }
        java.io.File("build/import_sim.txt").writeText(report)
        println(report)
        assertEquals(listOf("grabando #101", "grabando #100", "grabando #99"), a.discarded.map { it.row.title })
        assertEquals(977, a.links.size)
        assertTrue("1 a 1: ninguna canción compartida ($shared)", shared == 0)
    }
}

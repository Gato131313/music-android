package io.github.gato131313.music

import io.github.gato131313.music.data.backup.ImportCore
import io.github.gato131313.music.data.clean.LibraryBuilder
import io.github.gato131313.music.data.model.RawMedia
import io.github.gato131313.music.data.model.UserEdits
import io.github.gato131313.music.lyrics.Lrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndLrcTest {
    private fun raw(id: Long, name: String, title: String? = null, artist: String? = null) =
        RawMedia(id, false, "/storage/emulated/0/Download/$name", name, "Download/", title, artist, null, 0, null, 200_000, 1, 0, null)

    private val lib = LibraryBuilder.build(
        listOf(
            raw(1, "Guns N' Roses - Patience (Official Music Video).mp3"),
            raw(2, "x.mp3", "Tu Falta De Querer", "Mon Laferte - Topic"),
            raw(3, "EMJAY - Xtraparticular (Video Oficial).mp3"),
            raw(4, "Reino De Amor.mp3", "Reino De Amor", "Mon Laferte"),
            raw(5, "Tame Impala - Let It Happen (Official Video).mp3", "Tame Impala - Let It Happen (Official Video)", "tameimpalaVEVO"),
            raw(6, "SIAMÉS \"No Lullaby\" [Official Video].mp3", "SIAMÉS \"No Lullaby\" [Official Video]", "<unknown>"),
            raw(7, "Nunca Jamás Ft. San Pascualito Rey.mp3", "Nunca Jamás Ft. San Pascualito Rey", "Mon Laferte"),
            raw(8, "Mon Laferte - Amor Completo.mp3"),
            raw(9, "Mon Laferte - Amor Sin Fin.mp3"),
        ),
        UserEdits(),
    )

    @Test fun leeCsvConEncabezadoYComillas() {
        val csv = "﻿Título;Artista;Reproducciones;Favorito\n\"Patience\";\"guns n roses\";120;sí\n\"Tu falta de querer (Official Video)\";Mon Laferte;\"1,234\";no\r\n"
        val rows = ImportCore.parseCsv(csv)
        assertEquals(2, rows.size)
        assertEquals(120, rows[0].plays)
        assertTrue(rows[0].favorite)
        assertEquals(1234, rows[1].plays)
    }

    @Test fun empataIgnorandoMayusculasAcentosYSufijos() {
        val csv = """titulo,artista,reproducciones,favorito
patience,Guns N' Roses - Topic,50,1
"Tu Falta De Querer (Official Video)",MON LAFERTE,30,
XTRAPARTICULAR,EmjayVEVO,7,
"Mon Laferte - Reino de amor",,4,x
Cancion Que No Existe,Nadie,9,
"""
        val r = ImportCore.match(ImportCore.parseCsv(csv), lib.songs)
        assertEquals(5, r.rows)
        assertEquals(4, r.plays.size)
        assertEquals(1, r.unmatched.size)
        assertEquals("Cancion Que No Existe", r.unmatched[0].title)
        assertEquals(2, r.favorites.size)
        val patience = lib.songs.first { it.title == "Patience" }
        assertEquals(50, r.plays[patience.key])
        assertTrue(ImportCore.suggestions(r.unmatched[0], lib.songs).isNotEmpty())
    }

    @Test fun empataTitulosCortadosDeLaAppAnterior() {
        val csv = """titulo,artista,reproducciones
"Tame Impala - Let It H…",tameimpalaVEVO,186
"SIAMÉS ""No Lullaby"" [...",<unknown>,181
"Nunca Jamás Ft. San P...",Mon Laferte,526
"Mon Laferte - Amor ...",<unknown>,5
"""
        val r = ImportCore.match(ImportCore.parseCsv(csv), lib.songs)
        assertEquals(4, r.plays.size)
        assertEquals(186, r.plays[lib.songs.first { it.fileName.startsWith("Tame") }.key])
        assertEquals(181, r.plays[lib.songs.first { it.fileName.startsWith("SIAM") }.key])
        assertEquals(526, r.plays[lib.songs.first { it.fileName.startsWith("Nunca") }.key])
        // "Amor ..." podría ser dos canciones: ya no queda pendiente, va a la más probable
        assertTrue(r.unmatched.isEmpty())
        val amor = r.assignment.links.first { it.row.title.startsWith("Mon Laferte - Amor") }
        assertTrue(amor.track.title in setOf("Amor Completo", "Amor Sin Fin"))
        assertEquals(io.github.gato131313.music.data.backup.How.PROBABLE, amor.how)
        val sug = ImportCore.suggestions(amor.row, lib.songs).take(2).map { it.title }.toSet()
        assertEquals(setOf("Amor Completo", "Amor Sin Fin"), sug)
    }

    @Test fun unionManualSeRecuerdaAlReimportar() {
        val rows = ImportCore.parseCsv("titulo,artista,reproducciones\n\"Mon Laferte - Amor ...\",<unknown>,5\n")
        val target = lib.songs.first { it.title == "Amor Sin Fin" }
        val manual = mapOf(ImportCore.manualId(rows[0], 0) to target.key)
        val r1 = ImportCore.match(rows, lib.songs, manual)
        val r2 = ImportCore.match(rows, lib.songs, manual)
        assertEquals(mapOf(target.key to 5), r1.plays)
        assertEquals(r1.plays, r2.plays)
        assertTrue(r2.unmatched.isEmpty())
    }

    @Test fun filasIdenticasVanUnaACadaCancionYRespetanLasElegidasAMano() {
        val rows = ImportCore.parseCsv("titulo,artista,reproducciones\n\"Mon Laferte - Amor …\",Mon Laferte,52\n\"Mon Laferte - Amor …\",Mon Laferte,52\n")
        val a = lib.songs.first { it.title == "Amor Completo" }
        val b = lib.songs.first { it.title == "Amor Sin Fin" }
        // 1 a 1: dos filas iguales y dos canciones posibles -> una a cada una, nada pendiente
        assertEquals(mapOf(a.key to 52, b.key to 52), ImportCore.match(rows, lib.songs).plays)
        // Si una se eligió a mano, la otra toma la que queda
        val uno = ImportCore.match(rows, lib.songs, mapOf(ImportCore.manualId(rows[0], 0) to b.key))
        assertEquals(mapOf(a.key to 52, b.key to 52), uno.plays)
        assertEquals(io.github.gato131313.music.data.backup.How.SEGURO, uno.assignment.links.first { it.index == 1 }.how)
        // Notas de voz que la app no muestra: se descartan solas, con motivo
        val voz = ImportCore.match(ImportCore.parseCsv("titulo,artista,reproducciones\ngrabando #99,<unknown>,1\n"), lib.songs)
        assertEquals(listOf("grabando #99"), voz.unmatched.map { it.title })
        assertEquals(io.github.gato131313.music.data.backup.ImportMatcher.NO_CANDIDATE, voz.assignment.discarded[0].reason)
    }

    @Test fun csvSinEncabezado() {
        val r = ImportCore.match(ImportCore.parseCsv("Patience,Guns N Roses,3\n"), lib.songs)
        assertEquals(1, r.plays.size)
    }

    @Test fun lrcConVariasMarcasYOffset() {
        val lrc = "[ar:Yo]\n[offset:+500]\n[00:01.50][00:30.00]Hola <00:02.00>mundo\n[00:05.2]Adiós\n\n[01:02.345]Fin"
        val l = Lrc.parse(lrc)
        assertEquals(4, l.size)
        assertEquals(1000L, l[0].timeMs)
        assertEquals("Hola mundo", l[0].text)
        assertEquals(4700L, l[1].timeMs)
        assertEquals(61845L, l.last().timeMs)
        assertEquals(1, Lrc.indexAt(l, 5000))
        assertEquals(-1, Lrc.indexAt(l, 500))
    }
}

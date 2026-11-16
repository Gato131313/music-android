package io.github.gato131313.music

import io.github.gato131313.music.data.clean.LibraryBuilder
import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.model.RawMedia
import io.github.gato131313.music.data.model.UserEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameCleanerTest {

    private fun c(file: String, title: String? = null, artist: String? = null) =
        NameCleaner.clean(file, title, artist, null)

    @Test fun quitaRuidoTipico() {
        val cases = mapOf(
            "Sweet Child O' Mine (Official Music Video)" to "Sweet Child O' Mine",
            "Tu Falta De Querer (Video Oficial)" to "Tu Falta De Querer",
            "Xtraparticular [Official Video]" to "Xtraparticular",
            "Reino De Amor (Lyrics)" to "Reino De Amor",
            "Dynamite [MV]" to "Dynamite",
            "Song Name HD" to "Song Name",
            "Song Name (Official Audio) [HD]" to "Song Name",
            "Song Name (Letra)" to "Song Name",
            "Song Name (Lyric Video)" to "Song Name",
            "Song_Name_Snaptube" to "Song Name",
            "Song Name (Official Video) (4K Remaster)" to "Song Name (4K Remaster)",
            "Song Name | Official Music Video" to "Song Name",
            "Song Name - Official Video" to "Song Name",
            "Song Name (Sub. Español)" to "Song Name",
            "Song Name (Sub Español + Lyrics)" to "Song Name",
            "Song Name【MV】" to "Song Name",
            "Song Name (feat. Otro) (Official Video)" to "Song Name (feat. Otro)",
            "Song Name (Remix)" to "Song Name (Remix)",
            "Song Name (En Vivo)" to "Song Name (En Vivo)",
            "01 Song Name" to "Song Name",
            "1. Song Name" to "Song Name",
            "7 Rings" to "7 Rings",
            "22" to "22",
            "Video Games" to "Video Games",
            "y2mate.com - Song Name" to "Song Name",
            "Song Name (1080p)" to "Song Name",
            "'Song Name' M/V" to "Song Name",
        )
        for ((input, expected) in cases) {
            assertEquals("entrada: $input", expected, NameCleaner.cleanTitle(input))
        }
    }

    @Test fun separaArtistaCancion() {
        val r = c("Guns N' Roses - Sweet Child O' Mine (Official Music Video).mp3")
        assertEquals("Sweet Child O' Mine", r.title)
        assertEquals("Guns N' Roses", r.artist)
        assertTrue(r.didSplit)

        val r2 = c("EMJAY - Xtraparticular (Video Oficial).m4a", "EMJAY - Xtraparticular (Video Oficial)", "EMJAYVEVO")
        assertEquals("Xtraparticular", r2.title)
        assertEquals("EMJAY", r2.artist)

        val r3 = c("x.mp3", "Mon Laferte - Tu Falta De Querer", "Mon Laferte")
        assertEquals("Tu Falta De Querer", r3.title)
        assertEquals("Mon Laferte", r3.artist)

        // Artista de canal con "- Topic"
        val r4 = c("x.mp3", "Guns N' Roses - Patience", "Guns N' Roses - Topic")
        assertEquals("Patience", r4.title)
        assertEquals("Guns N' Roses", r4.artist)

        // Sello discográfico como "artista": separa
        val r5 = c("x.mp3", "Bad Bunny - Tití Me Preguntó", "Rimas Entertainment")
        assertEquals("Tití Me Preguntó", r5.title)
        assertEquals("Bad Bunny", r5.artist)

        // Etiqueta con otro artista real: no adivinar, pedir revisión
        val r6 = c("x.mp3", "Remastered - Live", "Queen")
        assertEquals("Queen", r6.artist)
        assertNotNull(r6.splitCandidate)

        // Coreano con comillas
        val r7 = c("BLACKPINK - '뚜두뚜두 (DDU-DU DDU-DU)' M_V.mp4")
        assertEquals("BLACKPINK", r7.artist)
        assertEquals("뚜두뚜두 (DDU-DU DDU-DU)", r7.title)

        // Decisión guardada del usuario: no separar
        val r8 = NameCleaner.clean("x.mp3", "Remastered - Live", "Queen", null, decision = false)
        assertNull(r8.splitCandidate)
        assertEquals("Remastered - Live", r8.title)
    }

    @Test fun limpiaArtista() {
        assertEquals("Guns N' Roses", NameCleaner.cleanArtist("Guns N' Roses - Topic"))
        assertEquals("Guns N Roses", NameCleaner.cleanArtist("GunsNRosesVEVO"))
        assertEquals("Taylor Swift", NameCleaner.cleanArtist("TaylorSwiftVEVO"))
        assertEquals("IVE", NameCleaner.cleanArtist("IVE 아이브"))
        assertEquals("tripleS", NameCleaner.cleanArtist("tripleS(트리플에스)"))
        assertEquals("Mon Laferte", NameCleaner.cleanArtist("Mon Laferte Official"))
        assertNull(NameCleaner.cleanArtist("<unknown>"))
    }

    @Test fun clavesUnenVariantes() {
        val k = NameCleaner.key("Guns N' Roses")
        assertEquals(k, NameCleaner.key("guns n roses"))
        assertEquals(k, NameCleaner.key("GUNS N' ROSES"))
        assertEquals(k, NameCleaner.key("Guns and Roses"))
        assertEquals(k, NameCleaner.key(NameCleaner.cleanArtist("Guns N' Roses - Topic")))
        assertEquals(k, NameCleaner.key(NameCleaner.cleanArtist("GunsNRosesVEVO")))
        assertEquals(NameCleaner.key("Beyoncé"), NameCleaner.key("Beyonce"))
        assertEquals(NameCleaner.key("Simon & Garfunkel"), NameCleaner.key("Simon and Garfunkel"))
        assertEquals(NameCleaner.key("The Beatles"), NameCleaner.key("Beatles"))
        assertEquals("Bad Bunny", NameCleaner.primaryArtist("Bad Bunny feat. Jhay Cortez"))
        assertEquals("Bad Bunny", NameCleaner.primaryArtist("Bad Bunny, Jhay Cortez"))
        assertEquals("Simon & Garfunkel", NameCleaner.primaryArtist("Simon & Garfunkel"))
    }

    private fun raw(id: Long, name: String, title: String? = null, artist: String? = null, dur: Long = 200_000, video: Boolean = false) =
        RawMedia(id, video, "/storage/emulated/0/Download/$name", name, "Download/", title, artist, null, 0, null, dur, 1000, 0, null)

    @Test fun bibliotecaUneArtistasYEmpareja() {
        val lib = LibraryBuilder.build(
            listOf(
                raw(1, "Guns N' Roses - Patience.mp3"),
                raw(2, "guns n roses - november rain (official video).mp3"),
                raw(3, "x.mp3", "Sweet Child O' Mine", "Guns N' Roses - Topic"),
                raw(4, "GunsNRosesVEVO - Don't Cry.mp3"),
                raw(5, "Guns N' Roses - Patience (Official Music Video).mp4", dur = 210_000, video = true),
                raw(6, "Otro - Video suelto.mp4", dur = 100_000, video = true),
                raw(7, "Mon Laferte - Tu Falta De Querer.mp3"),
                raw(8, "Mon Lafert - Amárrame.mp3"),
                raw(9, "nota de voz.mp3", dur = 5_000),
            ),
            UserEdits(),
        )
        assertEquals(6, lib.songs.size) // la nota de voz (<30 s) no cuenta
        val gnr = lib.artists.first { it.key == NameCleaner.key("Guns N' Roses") }
        assertEquals("Guns N' Roses", gnr.name)
        assertEquals(4, gnr.tracks.size)
        assertTrue(lib.songs.all { it.artist != "guns n roses" })
        // Pareja canción+video
        val patience = lib.songs.first { it.title == "Patience" }
        assertEquals(5L, patience.pairId)
        assertEquals(1, lib.videos.size) // solo el suelto
        // Duda: Mon Laferte vs Mon Lafert
        assertTrue(lib.review.merges.any { setOf(it.aName, it.bName) == setOf("Mon Laferte", "Mon Lafert") })
    }

    @Test fun aliasYEdicionesSeRespetan() {
        val raws = listOf(raw(7, "Mon Laferte - Tu Falta De Querer.mp3"), raw(8, "Mon Lafert - Amárrame.mp3"))
        val lib = LibraryBuilder.build(
            raws,
            UserEdits(aliases = mapOf(NameCleaner.key("Mon Lafert") to (NameCleaner.key("Mon Laferte") to null))),
        )
        assertEquals(1, lib.artists.size)
        assertEquals("Mon Laferte", lib.artists[0].name)
        assertTrue(lib.review.merges.isEmpty())
    }

    @Test fun rendimientoConMilesDeArchivos() {
        val raws = (1..3000L).map { raw(it, "Artista ${it % 400} - Canción número $it (Official Video) [HD].mp3") }
        val t0 = System.nanoTime()
        val lib = LibraryBuilder.build(raws, UserEdits())
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("3000 archivos en $ms ms")
        assertEquals(3000, lib.songs.size)
        assertTrue("tardó $ms ms", ms < 3000)
    }
}

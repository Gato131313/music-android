package io.github.gato131313.music.data.backup

import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.model.Track

data class CsvRow(val title: String, val artist: String, val plays: Int, val favorite: Boolean)

data class ImportResult(
    /** clave de canción -> reproducciones importadas (sumadas si hay filas repetidas). */
    val plays: Map<String, Int>,
    val favorites: Set<String>,
    val unmatched: List<CsvRow>,
    val rows: Int,
    val assignment: Assignment = Assignment(emptyList(), emptyList()),
)

/** Importar reproducciones de la app anterior desde CSV (titulo, artista, reproducciones[, favorito]). */
object ImportCore {

    fun parseCsv(text: String): List<CsvRow> {
        val clean = text.removePrefix("﻿")
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val sep = listOf(';', ',', '\t').maxBy { s -> firstLine.count { it == s } }
        val records = splitRecords(clean, sep)
        if (records.isEmpty()) return emptyList()
        val header = records.first().map { NameCleaner.key(it) }
        fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h == it || h.startsWith(it) } }
        var ti = col("titulo", "title", "cancion", "song", "nombre", "name")
        var ar = col("artista", "artist", "interprete")
        var pl = col("reproducciones", "plays", "playcount", "veces", "conteo", "count", "escuchas")
        var fv = col("favorito", "favorite", "fav", "megusta", "like")
        val hasHeader = ti >= 0 || ar >= 0 || pl >= 0
        if (!hasHeader) { ti = 0; ar = 1; pl = 2; fv = 3 }
        if (ti < 0) ti = 0
        if (ar < 0) ar = 1
        val body = if (hasHeader) records.drop(1) else records
        return body.mapNotNull { r ->
            val title = r.getOrNull(ti)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val plays = r.getOrNull(pl)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val favRaw = NameCleaner.fold(r.getOrNull(fv)?.trim().orEmpty())
            val fav = favRaw in setOf("si", "sí", "yes", "true", "1", "x", "fav", "favorito", "verdadero", "s", "y")
            CsvRow(title, r.getOrNull(ar)?.trim().orEmpty(), plays, fav)
        }
    }

    /** CSV con comillas ("a, b", comillas dobles escapadas) y saltos de línea dentro de comillas. */
    private fun splitRecords(text: String, sep: Char): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                sep -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); if (row.any { it.isNotBlank() }) out += row; row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) out += row
        return out
    }

    /** Clave estable de una fila (para recordar uniones hechas a mano). */
    fun rowId(r: CsvRow): String = NameCleaner.key(r.title) + "|" + NameCleaner.key(artistOf(r)) + "|" + r.plays

    /** Clave de la n-ésima fila idéntica (dos canciones distintas pueden verse igual si el título venía cortado). */
    fun manualId(r: CsvRow, occurrence: Int): String = rowId(r) + "#" + occurrence

    /** La app anterior corta los títulos largos con "…": se empatan por el principio. */
    fun isTruncated(s: String): Boolean = s.trimEnd().let { it.endsWith("…") || it.endsWith("...") }
    private fun stripDots(s: String): String = s.trimEnd().trimEnd('…', '.', ' ')
    private fun artistOf(r: CsvRow): String = if (NameCleaner.isUnknown(r.artist)) "" else r.artist.trim()

    /**
     * Empata filas con canciones (1 a 1, automático: ver [ImportMatcher]).
     * [manual] = uniones elegidas a mano (manualId -> clave de canción): se respetan al reimportar.
     */
    fun match(rows: List<CsvRow>, songs: List<Track>, manual: Map<String, String> = emptyMap(), taken: Set<String> = emptySet()): ImportResult {
        val a = ImportMatcher.assign(rows, songs, manual, taken)
        val favs = a.links.filter { it.row.favorite }.map { it.track.key }.toSet()
        return ImportResult(a.plays, favs, a.discarded.map { it.row }, rows.size, a)
    }

    fun rowKeys(r: CsvRow): Pair<String, String> {
        // El título del CSV también puede traer "Artista - Canción" o ruido de YouTube
        val title = stripDots(r.title)
        val art = artistOf(r)
        val c = NameCleaner.clean(title, title, art.ifBlank { null }, null)
        val artist = c.artist ?: art
        return NameCleaner.titleKey(c.title) to NameCleaner.artistKey(NameCleaner.cleanArtist(artist) ?: artist)
    }

    /** Sugerencias para empatar a mano una fila (las 5 más parecidas). */
    fun suggestions(r: CsvRow, songs: List<Track>): List<Track> {
        val (tk, ak) = rowKeys(r)
        val cut = isTruncated(r.title)
        return songs.asSequence().map { t ->
            val tt = if (cut && t.titleKey.length > tk.length) t.titleKey.substring(0, tk.length) else t.titleKey
            var score = NameCleaner.levenshtein(tt, tk, 12).toFloat()
            if (t.titleKey.contains(tk) || tk.contains(t.titleKey)) score -= 4
            if (ak.isNotEmpty() && t.artistKey == ak) score -= 5
            t to score
        }.sortedBy { it.second }.take(5).map { it.first }.toList()
    }
}

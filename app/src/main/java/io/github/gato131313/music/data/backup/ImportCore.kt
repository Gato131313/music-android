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

    /** Empata filas con canciones usando nombres limpios (sin mayúsculas, acentos ni sufijos tipo "- Topic"). */
    fun match(rows: List<CsvRow>, songs: List<Track>): ImportResult {
        val byFull = HashMap<String, MutableList<Track>>()
        val byTitle = HashMap<String, MutableList<Track>>()
        for (t in songs) {
            byFull.getOrPut(t.titleKey + "|" + t.artistKey) { ArrayList() } += t
            byTitle.getOrPut(t.titleKey) { ArrayList() } += t
        }
        val plays = HashMap<String, Int>()
        val favs = HashSet<String>()
        val unmatched = ArrayList<CsvRow>()
        for (r in rows) {
            val t = find(r, byFull, byTitle, songs)
            if (t == null) { unmatched += r; continue }
            if (r.plays > 0) plays.merge(t.key, r.plays, Int::plus)
            if (r.favorite) favs += t.key
        }
        return ImportResult(plays, favs, unmatched, rows.size)
    }

    fun rowKeys(r: CsvRow): Pair<String, String> {
        // El título del CSV también puede traer "Artista - Canción" o ruido de YouTube
        val c = NameCleaner.clean(r.title, r.title, r.artist.ifBlank { null }, null)
        val artist = c.artist ?: r.artist
        return NameCleaner.titleKey(c.title) to NameCleaner.artistKey(NameCleaner.cleanArtist(artist) ?: artist)
    }

    private fun find(r: CsvRow, byFull: Map<String, List<Track>>, byTitle: Map<String, List<Track>>, songs: List<Track>): Track? {
        val (tk, ak) = rowKeys(r)
        byFull["$tk|$ak"]?.let { return it.first() }
        val sameTitle = byTitle[tk].orEmpty()
        if (ak.isNotEmpty()) {
            sameTitle.firstOrNull { it.artistKey.isNotEmpty() && (it.artistKey.contains(ak) || ak.contains(it.artistKey)) }?.let { return it }
            // Tolerar una errata en el título del mismo artista
            if (tk.length >= 6) songs.firstOrNull { it.artistKey == ak && NameCleaner.levenshtein(it.titleKey, tk, 2) <= 2 }?.let { return it }
        }
        if (sameTitle.size == 1 && (ak.isEmpty() || sameTitle[0].artistKey.isEmpty())) return sameTitle[0]
        return null
    }

    /** Sugerencias para empatar a mano una fila (las 5 más parecidas). */
    fun suggestions(r: CsvRow, songs: List<Track>): List<Track> {
        val (tk, ak) = rowKeys(r)
        return songs.asSequence().map { t ->
            var score = NameCleaner.levenshtein(t.titleKey, tk, 12).toFloat()
            if (t.titleKey.contains(tk) || tk.contains(t.titleKey)) score -= 4
            if (ak.isNotEmpty() && t.artistKey == ak) score -= 5
            t to score
        }.sortedBy { it.second }.take(5).map { it.first }.toList()
    }
}

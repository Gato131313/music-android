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

    /** Clave estable de una fila (para recordar uniones hechas a mano). */
    fun rowId(r: CsvRow): String = NameCleaner.key(r.title) + "|" + NameCleaner.key(artistOf(r)) + "|" + r.plays

    private fun lookId(r: CsvRow): String = NameCleaner.key(r.title) + "|" + NameCleaner.key(artistOf(r))

    /** Clave de la n-ésima fila idéntica (dos canciones distintas pueden verse igual si el título venía cortado). */
    fun manualId(r: CsvRow, occurrence: Int): String = rowId(r) + "#" + occurrence

    /** La app anterior corta los títulos largos con "…": se empatan por el principio. */
    fun isTruncated(s: String): Boolean = s.trimEnd().let { it.endsWith("…") || it.endsWith("...") }
    private fun stripDots(s: String): String = s.trimEnd().trimEnd('…', '.', ' ')
    private fun artistOf(r: CsvRow): String = if (NameCleaner.isUnknown(r.artist)) "" else r.artist.trim()

    /**
     * Empata filas con canciones usando nombres limpios (sin mayúsculas, acentos ni sufijos tipo "- Topic").
     * [manual] = uniones hechas a mano antes (rowId -> clave de canción): se respetan al reimportar.
     * Si varias filas caen en la misma canción se suman (eran archivos distintos en la app anterior).
     */
    fun match(rows: List<CsvRow>, songs: List<Track>, manual: Map<String, String> = emptyMap()): ImportResult {
        val ix = Index(songs)
        val byKey = songs.associateBy { it.key }
        val plays = HashMap<String, Int>()
        val favs = HashSet<String>()
        val unmatched = ArrayList<CsvRow>()
        val seen = HashMap<String, Int>()
        val twins = rows.groupingBy { lookId(it) }.eachCount()
        for (r in rows) {
            val id = rowId(r)
            val manualKey = manual[manualId(r, seen.merge(id, 1, Int::plus)!! - 1)]
            // Filas que se ven iguales (mismo título cortado y artista) pueden ser canciones distintas
            // o el audio y el video de una: no adivinar, se unen a mano
            val t = manualKey?.let { byKey[it] } ?: if ((twins[lookId(r)] ?: 0) > 1) null else find(r, ix)
            if (t == null) { unmatched += r; continue }
            if (r.plays > 0) plays.merge(t.key, r.plays, Int::plus)
            if (r.favorite) favs += t.key
        }
        return ImportResult(plays, favs, unmatched, rows.size)
    }

    fun rowKeys(r: CsvRow): Pair<String, String> {
        // El título del CSV también puede traer "Artista - Canción" o ruido de YouTube
        val title = stripDots(r.title)
        val art = artistOf(r)
        val c = NameCleaner.clean(title, title, art.ifBlank { null }, null)
        val artist = c.artist ?: art
        return NameCleaner.titleKey(c.title) to NameCleaner.artistKey(NameCleaner.cleanArtist(artist) ?: artist)
    }

    private class Index(val songs: List<Track>) {
        val byFull = HashMap<String, MutableList<Track>>()
        val byTitle = HashMap<String, MutableList<Track>>()
        /** Nombres tal cual los veía la app anterior (etiqueta o nombre de archivo), en clave. */
        val rawKeys: List<List<String>>
        val rawArtist: List<String>
        init {
            for (t in songs) {
                byFull.getOrPut(t.titleKey + "|" + t.artistKey) { ArrayList() } += t
                byTitle.getOrPut(t.titleKey) { ArrayList() } += t
            }
            rawKeys = songs.map { t ->
                val file = t.fileName.substringBeforeLast('.')
                listOfNotNull(t.rawTitle, file, t.title, t.artist?.let { "$it - ${t.title}" })
                    .map { NameCleaner.key(it) }.filter { it.isNotEmpty() }.distinct()
            }
            rawArtist = songs.map { NameCleaner.key(it.rawArtist) }
        }
    }

    private fun sameArtist(t: Track, ak: String, rawAk: String, rawArtist: String): Boolean =
        ak.isEmpty() || (t.artistKey.isNotEmpty() && (t.artistKey.contains(ak) || ak.contains(t.artistKey))) ||
            (rawAk.isNotEmpty() && rawArtist == rawAk)

    private fun find(r: CsvRow, ix: Index): Track? {
        val (tk, ak) = rowKeys(r)
        val rawTitle = stripDots(r.title)
        val rawK = NameCleaner.key(rawTitle)
        val rawAk = NameCleaner.key(artistOf(r))
        val songs = ix.songs
        if (!isTruncated(r.title)) {
            ix.byFull["$tk|$ak"]?.let { return it.first() }
            val sameTitle = ix.byTitle[tk].orEmpty()
            if (ak.isNotEmpty()) {
                sameTitle.firstOrNull { it.artistKey.isNotEmpty() && (it.artistKey.contains(ak) || ak.contains(it.artistKey)) }?.let { return it }
            }
            // Mismo nombre que veía la app anterior (etiqueta o archivo)
            val raw = songs.indices.filter { rawK.isNotEmpty() && rawK in ix.rawKeys[it] }
            pick(raw.filter { sameArtist(songs[it], ak, rawAk, ix.rawArtist[it]) }.map { songs[it] })?.let { return it }
            if (ak.isNotEmpty() && tk.length >= 6) songs.firstOrNull { it.artistKey == ak && NameCleaner.levenshtein(it.titleKey, tk, 2) <= 2 }?.let { return it }
            if (sameTitle.size == 1 && (ak.isEmpty() || sameTitle[0].artistKey.isEmpty())) return sameTitle[0]
            pick(raw.map { songs[it] })?.let { return it }
            return null
        }
        // Título cortado: buscar canciones cuyo nombre empiece igual
        if (rawK.length < 4 && tk.length < 4) return null
        val cands = songs.indices.filter { i ->
            ix.rawKeys[i].any { (rawK.length >= 4 && it.startsWith(rawK)) } ||
                (tk.length >= 4 && songs[i].titleKey.startsWith(tk) && (ak.isEmpty() || songs[i].artistKey == ak))
        }
        val withArtist = cands.filter { sameArtist(songs[it], ak, rawAk, ix.rawArtist[it]) }.map { songs[it] }
        return pick(withArtist) ?: if (rawAk.isEmpty()) null else pick(cands.map { songs[it] }.filter { it.artistKey.isEmpty() })
    }

    /** Una sola canción (o varias copias del mismo título y artista): esa. Si hay duda, ninguna. */
    private fun pick(c: List<Track>): Track? {
        if (c.isEmpty()) return null
        val first = c.first()
        return if (c.all { it.titleKey == first.titleKey && it.artistKey == first.artistKey }) first else null
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

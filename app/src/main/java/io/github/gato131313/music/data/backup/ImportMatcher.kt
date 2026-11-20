package io.github.gato131313.music.data.backup

import io.github.gato131313.music.data.clean.NameCleaner
import io.github.gato131313.music.data.model.Track

/** Cómo se decidió una unión fila del CSV -> canción. */
enum class How {
    /** La eligió la persona. */
    MANO,
    /** Una sola canción posible (directo o tras quitar las ya empatadas). */
    SEGURO,
    /** Varias posibles: la mejor según la asignación global. Se puede corregir. */
    PROBABLE,
}

data class Link(val index: Int, val row: CsvRow, val track: Track, val how: How, val score: Float)
data class Discard(val index: Int, val row: CsvRow, val reason: String)
data class Assignment(val links: List<Link>, val discarded: List<Discard>) {
    val plays: Map<String, Int> get() = HashMap<String, Int>().also { m -> links.forEach { l -> if (l.row.plays > 0) m.merge(l.track.key, l.row.plays, Int::plus) } }
}

/**
 * Empate 100 % automático de filas de la app anterior con canciones del teléfono.
 * Cada fila es UNA canción distinta (1 a 1):
 *  1. Para cada fila, las canciones posibles con su puntaje (título o su principio si venía cortado con "…",
 *     artista de la etiqueta, "- Topic"/"VEVO", nombre de archivo, audio/video, parecido de texto).
 *  2. Eliminación: la fila con una sola posible se queda con ella y esa canción sale de las demás; se repite.
 *  3. Lo que siga con varias posibles se resuelve con asignación óptima global (húngaro) por grupos conectados.
 *  4. Si una fila se queda sin canción libre, va a su más probable aunque la comparta.
 *  5. Solo se descartan filas sin ninguna canción posible (p. ej. notas de voz que la app no muestra).
 */
object ImportMatcher {
    const val NO_CANDIDATE = "No hay en el teléfono ninguna canción con un nombre parecido"

    private const val MIN_TITLE = 0.8f

    private class Cand(val track: Track) {
        val names: List<String>
        val rawArtist = NameCleaner.key(track.rawArtist?.takeUnless { NameCleaner.isUnknown(it) })
        val artistKey = track.artistKey
        val folderKey = NameCleaner.key(track.folder)
        init {
            val file = track.fileName.substringBeforeLast('.')
            names = listOfNotNull(
                track.rawTitle, file, track.title,
                track.artist?.let { "$it - ${track.title}" },
                track.rawArtist?.let { "$it - ${track.title}" },
            ).map { NameCleaner.key(it) }.filter { it.isNotEmpty() }.distinct()
        }
    }

    private class RowInfo(val r: CsvRow) {
        val trunc = ImportCore.isTruncated(r.title)
        val shown = r.title.trimEnd().trimEnd('…', '.', ' ')
        val dk = NameCleaner.key(shown)
        val rawAk: String
        val tk: String
        val ak: String
        init {
            val art = if (NameCleaner.isUnknown(r.artist)) "" else r.artist.trim()
            rawAk = NameCleaner.key(art)
            val (t, a) = ImportCore.rowKeys(r)
            tk = t; ak = a
        }
    }

    private fun sim(a: String, b: String): Float {
        val n = maxOf(a.length, b.length)
        if (n == 0) return 0f
        val max = (n * 0.25f).toInt() + 1
        val d = NameCleaner.levenshtein(a, b, max)
        return if (d > max) 0f else 1f - d.toFloat() / n
    }

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    /** Puntaje de la canción para la fila; < 0 si no es posible. */
    private fun score(ri: RowInfo, c: Cand): Float {
        val dk = ri.dk
        var title = 0f
        if (dk.isNotEmpty()) for (n in c.names) {
            val s = if (ri.trunc) {
                when {
                    n.startsWith(dk) -> 1f
                    dk.length >= 6 && commonPrefix(n, dk) >= 2 && n.length >= dk.length - 2 -> sim(n.take(dk.length), dk) * 0.95f
                    else -> 0f
                }
            } else {
                when {
                    n == dk -> 1f
                    minOf(n.length, dk.length) >= 4 && (n.startsWith(dk) || dk.startsWith(n)) -> 0.86f
                    dk.length >= 5 && commonPrefix(n, dk) >= 2 -> sim(n, dk) * 0.95f
                    else -> 0f
                }
            }
            if (s > title) title = s
        }
        val tt = c.track.titleKey
        if (ri.tk.length >= 3) {
            if (!ri.trunc && tt == ri.tk) title = maxOf(title, 0.95f)
            if (ri.trunc && tt.startsWith(ri.tk)) title = maxOf(title, 0.9f)
        }
        if (title < MIN_TITLE) return -1f
        var s = title
        // Artista
        if (ri.rawAk.isNotEmpty()) {
            s += when {
                c.rawArtist == ri.rawAk -> 0.5f
                ri.ak.isNotEmpty() && c.artistKey.isNotEmpty() && (c.artistKey == ri.ak || c.artistKey.contains(ri.ak) || ri.ak.contains(c.artistKey)) -> 0.4f
                c.names.any { it.contains(ri.rawAk) } || (ri.ak.length >= 3 && c.names.any { it.contains(ri.ak) }) -> 0.3f
                c.folderKey.contains(ri.rawAk) -> 0.25f
                else -> -0.35f
            }
        } else if (c.rawArtist.isEmpty()) s += 0.15f
        // La app anterior era un reproductor de música: un poco más probable el audio que el video
        if (!c.track.isVideo) s += 0.03f
        // Nombres que coinciden completos (no solo el principio) son un poco más probables
        if (!ri.trunc && c.names.any { it == dk }) s += 0.05f
        return s
    }

    fun assign(
        rows: List<CsvRow>,
        tracks: List<Track>,
        /** manualId -> clave de canción (uniones elegidas por la persona: se respetan). */
        manual: Map<String, String> = emptyMap(),
        /** Canciones que ya tienen fila (de una importación anterior): no se ofrecen otra vez. */
        taken: Set<String> = emptySet(),
    ): Assignment {
        val byKey = tracks.associateBy { it.key }
        val cands = tracks.filter { it.key !in taken }.map { Cand(it) }
        val links = ArrayList<Link>()
        val discarded = ArrayList<Discard>()
        val used = HashSet<String>(taken)

        // Uniones a mano primero
        val seen = HashMap<String, Int>()
        val free = ArrayList<Int>()
        rows.forEachIndexed { i, r ->
            val id = ImportCore.rowId(r)
            val k = manual[ImportCore.manualId(r, seen.merge(id, 1, Int::plus)!! - 1)]
            val t = k?.let { byKey[it] }
            if (t != null) { links += Link(i, r, t, How.MANO, 9f); used += t.key } else free += i
        }

        // Índice por primeras letras para no comparar cada fila con todo
        val byStart = HashMap<String, MutableList<Int>>()
        cands.forEachIndexed { ci, c ->
            val starts = HashSet<String>()
            c.names.forEach { if (it.length >= 2) starts += it.take(2) }
            if (c.track.titleKey.length >= 2) starts += c.track.titleKey.take(2)
            starts.forEach { byStart.getOrPut(it) { ArrayList() } += ci }
        }
        val infos = HashMap<Int, RowInfo>()
        // fila -> (candidato -> puntaje)
        val options = HashMap<Int, MutableMap<Int, Float>>()
        for (i in free) {
            val ri = RowInfo(rows[i]); infos[i] = ri
            val pool = HashSet<Int>()
            if (ri.dk.length >= 2) byStart[ri.dk.take(2)]?.let { pool += it }
            if (ri.tk.length >= 2) byStart[ri.tk.take(2)]?.let { pool += it }
            val m = HashMap<Int, Float>()
            for (ci in pool) {
                if (cands[ci].track.key in used) continue
                val s = score(ri, cands[ci])
                if (s >= 0f) m[ci] = s
            }
            options[i] = m
        }
        val allCands = HashMap(options.mapValues { HashMap(it.value) })
        for (i in free) if (options[i].isNullOrEmpty()) discarded += Discard(i, rows[i], NO_CANDIDATE)
        val open = free.filter { !options[it].isNullOrEmpty() }.toMutableSet()

        // 2) Eliminación: una sola posible -> segura; sale de las demás; repetir
        var changed = true
        while (changed) {
            changed = false
            for (i in open.toList()) {
                val m = options[i]!!
                m.keys.removeAll { cands[it].track.key in used }
                if (m.size == 1) {
                    val ci = m.keys.first()
                    links += Link(i, rows[i], cands[ci].track, How.SEGURO, m[ci]!!)
                    used += cands[ci].track.key
                    open -= i; changed = true
                }
            }
        }
        open.forEach { i -> options[i]!!.keys.removeAll { cands[it].track.key in used } }

        // 3) Asignación óptima global por grupos conectados
        val pending = open.filter { options[it]!!.isNotEmpty() }
        val parent = HashMap<Int, Int>()
        fun find(x: Int): Int { var a = x; while (parent[a] != null && parent[a] != a) a = parent[a]!!; return a }
        fun union(a: Int, b: Int) { val x = find(a); val y = find(b); if (x != y) parent[x] = y }
        val candOwner = HashMap<Int, Int>()
        for (i in pending) { parent.putIfAbsent(i, i); for (ci in options[i]!!.keys) { val o = candOwner[ci]; if (o == null) candOwner[ci] = i else union(i, o) } }
        val groups = pending.groupBy { find(it) }.values
        val unassigned = ArrayList<Int>()
        for (g in groups) {
            val cols = g.flatMap { options[it]!!.keys }.distinct()
            val res = if (g.size * cols.size <= 250_000) hungarian(g, cols) { i, ci -> options[i]!![ci] } else greedy(g) { options[it]!! }
            for ((k, i) in g.withIndex()) {
                val ci = res[k]
                if (ci == null) unassigned += i
                else links += Link(i, rows[i], cands[ci].track, How.PROBABLE, options[i]!![ci]!!)
            }
        }
        // 4) Sin canción libre: a la más probable aunque se comparta
        for (i in unassigned + open.filter { options[it]!!.isEmpty() }) {
            val best = allCands[i]!!.maxByOrNull { it.value }
            if (best != null) links += Link(i, rows[i], cands[best.key].track, How.PROBABLE, best.value)
            else discarded += Discard(i, rows[i], NO_CANDIDATE)
        }
        return Assignment(links.sortedBy { it.index }, discarded.sortedBy { it.index })
    }

    private fun greedy(rows: List<Int>, opts: (Int) -> Map<Int, Float>): List<Int?> {
        val taken = HashSet<Int>()
        val out = arrayOfNulls<Int>(rows.size)
        val all = rows.withIndex().flatMap { (k, i) -> opts(i).map { Triple(k, it.key, it.value) } }.sortedByDescending { it.third }
        for ((k, ci, _) in all) if (out[k] == null && ci !in taken) { out[k] = ci; taken += ci }
        return out.toList()
    }

    /**
     * Húngaro (máximo puntaje total). Filas = [rows]; columnas = [cols] + una "sin canción" por fila
     * (muy cara), así toda fila recibe canción si hay suficientes.
     */
    private fun hungarian(rows: List<Int>, cols: List<Int>, w: (Int, Int) -> Float?): List<Int?> {
        val n = rows.size
        val m = cols.size + n
        val NONE = 1e6
        val BIG = 1e3
        fun cost(r: Int, c: Int): Double {
            if (c >= cols.size) return BIG
            val s = w(rows[r], cols[c]) ?: return NONE
            return 10.0 - s
        }
        val u = DoubleArray(n + 1); val v = DoubleArray(m + 1)
        val p = IntArray(m + 1); val way = IntArray(m + 1)
        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(m + 1) { Double.MAX_VALUE }
            val usedC = BooleanArray(m + 1)
            do {
                usedC[j0] = true
                val i0 = p[j0]
                var delta = Double.MAX_VALUE
                var j1 = 0
                for (j in 1..m) if (!usedC[j]) {
                    val cur = cost(i0 - 1, j - 1) - u[i0] - v[j]
                    if (cur < minv[j]) { minv[j] = cur; way[j] = j0 }
                    if (minv[j] < delta) { delta = minv[j]; j1 = j }
                }
                for (j in 0..m) if (usedC[j]) { u[p[j]] += delta; v[j] -= delta } else minv[j] -= delta
                j0 = j1
            } while (p[j0] != 0)
            do { val j1 = way[j0]; p[j0] = p[j1]; j0 = j1 } while (j0 != 0)
        }
        val out = arrayOfNulls<Int>(n)
        for (j in 1..m) if (p[j] != 0 && j - 1 < cols.size && w(rows[p[j] - 1], cols[j - 1]) != null) out[p[j] - 1] = cols[j - 1]
        return out.toList()
    }
}

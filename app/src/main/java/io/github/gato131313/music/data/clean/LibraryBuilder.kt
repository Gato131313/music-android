package io.github.gato131313.music.data.clean

import io.github.gato131313.music.data.model.Album
import io.github.gato131313.music.data.model.Artist
import io.github.gato131313.music.data.model.Folder
import io.github.gato131313.music.data.model.Genre
import io.github.gato131313.music.data.model.Library
import io.github.gato131313.music.data.model.MergeSuggestion
import io.github.gato131313.music.data.model.RawMedia
import io.github.gato131313.music.data.model.ReviewData
import io.github.gato131313.music.data.model.SplitSuggestion
import io.github.gato131313.music.data.model.Track
import io.github.gato131313.music.data.model.UserEdits
import kotlin.math.abs

/**
 * Convierte las filas de MediaStore en la biblioteca que ve el usuario.
 * Puro y determinista: se puede probar en JVM y se recalcula entero (es rápido: ~2 000 filas en ms).
 */
object LibraryBuilder {

    private class Pre(
        val raw: RawMedia,
        val title: String,
        val artist: String?,
        val album: String?,
        val primaryKey: String,
        val edited: Boolean,
        val split: Pair<String, String>?,
    )

    fun pathKey(path: String) = path.lowercase()

    fun build(raws: List<RawMedia>, user: UserEdits): Library {
        var hidden = 0
        val pres = ArrayList<Pre>(raws.size)
        for (r in raws) {
            val pk = pathKey(r.path)
            if (pk in user.hiddenPaths || user.hiddenFolders.any { pk.startsWith(it) }) { hidden++; continue }
            if (!r.isVideo && r.durationMs in 1 until user.minDurationMs) continue
            if (r.isVideo && r.durationMs in 1 until user.minVideoDurationMs) continue
            val c = NameCleaner.clean(r.displayName, r.title, r.artist, r.album, user.splitDecisions[pk])
            val e = user.edits[pk]
            val title = e?.title?.takeIf { it.isNotBlank() } ?: c.title
            val artist = e?.artist?.takeIf { it.isNotBlank() } ?: c.artist
            val album = e?.album?.takeIf { it.isNotBlank() } ?: c.album
            val pkey = resolveAlias(NameCleaner.artistKey(artist), user.aliases)
            pres += Pre(r, title, artist, album, pkey, e != null, if (e?.artist != null) null else c.splitCandidate)
        }

        // 1) Nombre canónico por artista (variantes unidas por clave).
        val variants = HashMap<String, HashMap<String, Int>>()
        for (p in pres) {
            val a = p.artist ?: continue
            val prim = NameCleaner.primaryArtist(a)
            variants.getOrPut(p.primaryKey) { HashMap() }.merge(prim, 1, Int::plus)
        }
        val canonical = HashMap<String, String>(variants.size)
        for ((k, vs) in variants) canonical[k] = bestName(vs)
        for ((from, to) in user.aliases) {
            val name = to.second ?: continue
            canonical[to.first] = name
            if (from == to.first) canonical[from] = name
        }

        // 2) Pistas finales.
        val audio = ArrayList<Track>()
        val video = ArrayList<Track>()
        val splits = ArrayList<SplitSuggestion>()
        for (p in pres) {
            val r = p.raw
            val artist = p.artist?.let { full ->
                val canon = canonical[p.primaryKey] ?: return@let full
                val prim = NameCleaner.primaryArtist(full)
                if (prim == full) canon else canon + full.substring(prim.length.coerceAtMost(full.length))
            }
            val albumKey = if (p.album != null) NameCleaner.key(p.album) else ""
            val t = Track(
                id = r.id,
                key = pathKey(r.path),
                isVideo = r.isVideo,
                path = r.path,
                folder = r.folder,
                fileName = r.displayName,
                title = p.title,
                artist = artist,
                album = p.album,
                genre = cleanGenre(r.genre),
                durationMs = r.durationMs,
                dateAdded = r.dateAdded,
                albumId = r.albumId,
                size = r.size,
                mime = r.mime,
                artistKey = p.primaryKey,
                albumKey = albumKey,
                titleKey = NameCleaner.titleKey(p.title),
                rawTitle = r.title,
                rawArtist = r.artist,
                edited = p.edited,
                search = NameCleaner.fold(p.title + " " + (artist ?: "") + " " + (p.album ?: "") + " " + r.displayName),
            )
            if (r.isVideo) video += t else audio += t
            if (p.split != null && !r.isVideo) splits += SplitSuggestion(t, p.split.first, p.split.second)
        }

        // 3) Canción + Video.
        val paired = pair(audio, video, user.pairOverrides)
        val songs = paired.first.sortedWith(compareBy({ NameCleaner.fold(it.title) }, { it.id }))
        val allVideos = paired.second.sortedWith(compareBy({ NameCleaner.fold(it.title) }, { it.id }))
        val looseVideos = allVideos.filter { it.pairId == null }

        // 4) Agrupaciones.
        val artists = songs.filter { it.artist != null }.groupBy { it.artistKey }.map { (k, ts) ->
            Artist(k, canonical[k] ?: NameCleaner.primaryArtist(ts.first().artist!!), ts, ts.mapNotNull { it.album }.distinct().size)
        }.sortedBy { NameCleaner.fold(it.name) }

        val albums = buildAlbums(songs, canonical)
        val folders = (songs + allVideos).groupBy { it.folder }.map { (f, ts) ->
            Folder(f, f.trimEnd('/').substringAfterLast('/').ifBlank { "Almacenamiento" }, ts.filter { !it.isVideo }, ts.filter { it.isVideo })
        }.sortedBy { NameCleaner.fold(it.name) }
        val genres = songs.filter { it.genre != null }.groupBy { NameCleaner.key(it.genre) }.map { (k, ts) ->
            Genre(k, bestName(ts.groupingBy { it.genre!! }.eachCount()), ts)
        }.sortedBy { NameCleaner.fold(it.name) }

        val review = ReviewData(
            merges = mergeSuggestions(variants, canonical, user),
            splits = splits.sortedBy { NameCleaner.fold(it.track.title) },
            unknownArtist = songs.filter { it.artist == null },
        )
        return Library(songs, looseVideos, allVideos, albums, artists, folders, genres, review, hidden)
    }

    private fun resolveAlias(key: String, aliases: Map<String, Pair<String, String?>>): String {
        var k = key
        var guard = 0
        while (guard++ < 8) {
            val next = aliases[k]?.first ?: return k
            if (next == k) return k
            k = next
        }
        return k
    }

    /** Elige la mejor forma de escribir un nombre entre variantes. */
    fun bestName(vs: Map<String, Int>): String = vs.maxWith(
        compareBy<Map.Entry<String, Int>> { score(it.key, it.value) }.thenBy { it.key },
    ).key

    private fun score(s: String, count: Int): Int {
        var sc = count * 4
        val hasLower = s.any { it.isLowerCase() }
        val hasUpper = s.any { it.isUpperCase() }
        if (hasLower && hasUpper) sc += 6
        if (!hasLower && hasUpper && s.length <= 6) sc += 4 // BTS, IVE, ABBA
        if (s.any { it == '\'' || it == '’' || it.code > 127 }) sc += 2
        if (s.contains(' ')) sc += 1
        if (s.firstOrNull()?.isLowerCase() == true) sc -= 3
        return sc
    }

    private fun cleanGenre(g: String?): String? {
        if (g == null) return null
        var s = g.trim()
        if (s.isBlank() || NameCleaner.isUnknown(s)) return null
        s = s.replace(Regex("^\\(\\d+\\)"), "").trim()
        if (s.all { it.isDigit() } || s.isBlank()) return null
        if (NameCleaner.key(s) in setOf("other", "otro", "otros", "genre", "genero")) return null
        return s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
    }

    private fun buildAlbums(songs: List<Track>, canonical: Map<String, String>): List<Album> {
        val withAlbum = songs.filter { it.album != null }
        // Agrupar por nombre + artista dominante (evita mezclar dos "Greatest Hits").
        val firstPass = withAlbum.groupBy { it.albumKey + "|" + it.albumId }
        val dominant = HashMap<String, String>()
        for ((k, ts) in firstPass) {
            val counts = ts.groupingBy { it.artistKey }.eachCount()
            dominant[k] = if (counts.size > 2) "varios" else counts.maxBy { it.value }.key
        }
        return withAlbum.groupBy { it.albumKey + "|" + dominant[it.albumKey + "|" + it.albumId] }.map { (k, ts) ->
            val dk = k.substringAfter('|')
            val name = bestName(ts.groupingBy { it.album!! }.eachCount())
            val artist = if (dk == "varios") "Varios artistas" else canonical[dk] ?: ts.first().displayArtist
            Album(k, name, artist, ts.first().id, ts.sortedBy { NameCleaner.fold(it.title) })
        }.sortedBy { NameCleaner.fold(it.name) }
    }

    /** Une canción y video del mismo título y artista (ya limpios) con duración parecida. */
    fun pair(audio: List<Track>, video: List<Track>, overrides: Map<String, String>): Pair<List<Track>, List<Track>> {
        val byKey = HashMap<String, MutableList<Int>>()
        audio.forEachIndexed { i, t -> if (t.artistKey.isNotEmpty() && t.titleKey.isNotEmpty()) byKey.getOrPut(t.artistKey + "|" + t.titleKey) { ArrayList() } += i }
        val videoByKey = video.withIndex().associate { it.value.key to it.index }
        val audioPair = arrayOfNulls<Long>(audio.size)
        val videoPair = arrayOfNulls<Long>(video.size)
        val forcedNone = HashSet<String>()
        // Uniones manuales primero.
        audio.forEachIndexed { i, a ->
            val o = overrides[a.key] ?: return@forEachIndexed
            if (o.isEmpty()) { forcedNone += a.key; return@forEachIndexed }
            val vi = videoByKey[o] ?: return@forEachIndexed
            if (videoPair[vi] == null) { audioPair[i] = video[vi].id; videoPair[vi] = a.id }
        }
        val forcedVideos = overrides.values.filter { it.isNotEmpty() }.toHashSet()
        // Automáticas: el video más cercano en duración gana.
        data class Cand(val ai: Int, val vi: Int, val diff: Long)
        val cands = ArrayList<Cand>()
        video.forEachIndexed { vi, v ->
            if (videoPair[vi] != null || v.key in forcedVideos) return@forEachIndexed
            val list = byKey[v.artistKey + "|" + v.titleKey] ?: return@forEachIndexed
            for (ai in list) {
                val a = audio[ai]
                if (audioPair[ai] != null || a.key in forcedNone) continue
                val diff = abs(a.durationMs - v.durationMs)
                val tol = maxOf(15_000L, (maxOf(a.durationMs, v.durationMs) * 0.12).toLong())
                if (a.durationMs <= 0 || v.durationMs <= 0 || diff <= tol) cands += Cand(ai, vi, diff)
            }
        }
        cands.sortBy { it.diff }
        for (c in cands) {
            if (audioPair[c.ai] != null || videoPair[c.vi] != null) continue
            audioPair[c.ai] = video[c.vi].id
            videoPair[c.vi] = audio[c.ai].id
        }
        val a2 = audio.mapIndexed { i, t -> if (audioPair[i] != null) t.copy(pairId = audioPair[i]) else t }
        val v2 = video.mapIndexed { i, t -> if (videoPair[i] != null) t.copy(pairId = videoPair[i]) else t }
        return a2 to v2
    }

    /** Artistas que probablemente son el mismo pero no se unieron solos. */
    fun mergeSuggestions(
        variants: Map<String, Map<String, Int>>,
        canonical: Map<String, String>,
        user: UserEdits,
    ): List<MergeSuggestion> {
        val keys = variants.keys.filter { it.length >= 3 }.sortedBy { it.length }
        val counts = variants.mapValues { it.value.values.sum() }
        val out = ArrayList<MergeSuggestion>()
        for (i in keys.indices) {
            val a = keys[i]
            for (j in i + 1 until keys.size) {
                val b = keys[j]
                if (b.length - a.length > 8) break
                if ((a to b) in user.rejected || (b to a) in user.rejected) continue
                val similar = when {
                    a.length >= 9 && b.length - a.length <= 2 -> NameCleaner.levenshtein(a, b, 2) <= 2
                    a.length >= 5 && b.length - a.length <= 1 -> NameCleaner.levenshtein(a, b, 1) <= 1
                    else -> false
                } || (a.length >= 5 && (b.startsWith(a) || b.endsWith(a)) && b.length - a.length <= 8)
                if (!similar) continue
                val ca = counts[a] ?: 0
                val cb = counts[b] ?: 0
                // El que más canciones tiene queda como destino ("b").
                out += if (ca > cb) MergeSuggestion(b, canonical[b] ?: b, cb, a, canonical[a] ?: a, ca)
                else MergeSuggestion(a, canonical[a] ?: a, ca, b, canonical[b] ?: b, cb)
            }
        }
        return out.sortedByDescending { it.aCount + it.bCount }
    }
}

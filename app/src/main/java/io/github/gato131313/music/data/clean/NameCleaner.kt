package io.github.gato131313.music.data.clean

import java.text.Normalizer

/**
 * Limpia nombres sucios (Snaptube / YouTube) SOLO para mostrarlos en la app.
 * Nunca toca los archivos. Todo es Kotlin puro para poder probarlo en JVM.
 */
object NameCleaner {

    data class Result(
        val title: String,
        val artist: String?,
        val album: String?,
        /** El título traía "A - B" pero el artista de la etiqueta no coincide: duda para "Revisar nombres". */
        val splitCandidate: Pair<String, String>? = null,
        /** Se separó "Artista - Canción" automáticamente. */
        val didSplit: Boolean = false,
    )

    private val UNKNOWN = setOf(
        "", "<unknown>", "unknown", "unknown artist", "desconocido", "artista desconocido",
        "null", "none", "n/a", "na", "-", "various", "artist", "track", "audio",
    )

    // Palabras de "ruido" que pueden ir entre paréntesis/corchetes.
    private const val TAG =
        "(?:" +
            "official(?:\\s+(?:music|lyrics?|audio|video|visuali[sz]er|mv|m\\s*/\\s*v|hd|4k|performance|clip|channel|trailer))*(?:\\s+(?:video|audio|mv|clip|visuali[sz]er))?" +
            "|oficial|video\\s*oficial|audio\\s*oficial|videoclip\\s*oficial|clip\\s*officiel|v[ií]deo\\s*oficial" +
            "|music\\s*video|lyrics?\\s*video|lyric\\s*visuali[sz]er|lyrics?|letras?|con\\s*letras?|en\\s*letra" +
            "|(?:sub\\.?(?:t[ií]tulos?|titulad[oa])?|traducid[oa]|traducci[oó]n)(?:\\s*(?:al|en|a))?(?:\\s*(?:espa[nñ]ol|ingl[eé]s|english|esp|eng))?" +
            "|eng\\s*sub|sub\\s*esp|esp\\s*sub|color\\s*coded(?:\\s*lyrics)?|han\\s*/?\\s*rom\\s*/?\\s*eng" +
            "|m\\s*[/ ]?\\s*v\\b|mv|audio|visuali[sz]er|video\\s*clip|videoclip|video|v[ií]deo" +
            "|hd|hq|4k|8k|uhd|full\\s*hd|\\d{3,4}\\s*p(?:\\s*\\d{2})?|60\\s*fps|\\d{2,3}\\s*kbps|\\d{2,3}\\s*k" +
            "|high\\s*quality|best\\s*quality|audio\\s*only|full\\s*audio|explicit|clean|snaptube|y2mate(?:\\.com)?|topic|vevo" +
            ")"

    private val ONLY_TAGS = Regex("^\\s*$TAG(?:\\s*[-/,&|+.:]*\\s*$TAG)*\\s*[.!]*\\s*$", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("[(\\[{]([^()\\[\\]{}]*)[)\\]}]")
    private val CJK_BRACKETS = Regex("[【「『〖]([^】」』〗]*)[】」』〗]")

    // Ruido que puede ir al final SIN paréntesis (más estricto para no comer títulos reales).
    private const val SAFE_TRAIL =
        "(?:official(?:\\s+(?:music|lyrics?|audio))?\\s+(?:video|audio|mv|visuali[sz]er)|official|oficial" +
            "|video\\s*oficial|audio\\s*oficial|v[ií]deo\\s*oficial|music\\s*video|lyrics?\\s*video|lyrics|con\\s*letra|letra" +
            "|m\\s*[/ ]?\\s*v\\b|mv|hd|hq|4k|\\d{3,4}p|visuali[sz]er|snaptube|vevo)"
    private val TRAILING = Regex("(?:\\s*[-–—|:~•·]\\s*|\\s+)$SAFE_TRAIL\\s*[.!]*\\s*$", RegexOption.IGNORE_CASE)
    private val TRAILING_TOPIC = Regex("\\s*[-–—|:]\\s*topic\\s*$", RegexOption.IGNORE_CASE)

    private val LEADING_SITE = Regex(
        "^\\s*(?:y2mate(?:\\.(?:com|is|mate))?|snaptube|savefrom(?:\\.net)?|ytmp3(?:\\.cc)?|www\\.[\\w.-]+|[\\w-]+\\.(?:com|net|org|cc|io))\\s*[-–—_|:]+\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val SNAPTUBE_ANY = Regex("[\\s_\\-.(\\[]*\\bsnap\\s*tube\\b[\\s_\\-.)\\]]*", RegexOption.IGNORE_CASE)
    private val TRACK_NUM = listOf(
        Regex("^0\\d\\s*[-._)]?\\s+(?=\\S)"),          // "01 Canción", "01 - Canción"
        Regex("^\\d{1,3}\\s*[.)]\\s+(?=\\S)"),          // "1. Canción", "12) Canción"
        Regex("^\\d{1,3}\\s*[-_]\\s+(?=.+\\s[-–—]\\s)"), // "03 - Artista - Canción"
        Regex("^\\d{1,3}_+(?=\\D)"),                    // "03_Canción"
    )
    private val SPACES = Regex("\\s+")
    private val DASH_SPLIT = Regex("\\s+[-–—]\\s+|\\s+[-–—](?=\\S)|(?<=\\S)[–—]\\s+|\\s*｜\\s*")
    private val QUOTED = Regex("[\"'‘’“”「『«]([^\"'‘’“”「」『』«»]{1,120})[\"'‘’“”」』»]")
    private val WRAP_QUOTES = Regex("^[\"'‘“「『«]+(.*?)[\"'’”」』»]+$")
    private val EDGE = Regex("^[\\s\\-–—_|·:,~•]+|[\\s\\-–—_|·:,~•]+$")
    private val EXT = Regex("\\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma|amr|mp4|m4v|mkv|webm|3gp|avi|mov|ts)$", RegexOption.IGNORE_CASE)

    private val ARTIST_TOPIC = Regex("\\s*[-–—]?\\s*topic\\s*$", RegexOption.IGNORE_CASE)
    private val ARTIST_VEVO = Regex("\\s*vevo\\s*$", RegexOption.IGNORE_CASE)
    private val ARTIST_OFFICIAL = Regex(
        "\\s*[-–—(\\[]?\\s*(?:official(?:\\s+(?:youtube\\s+)?channel)?|oficial|canal\\s+oficial|official\\s+artist\\s+channel)\\s*[)\\]]?\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val CHANNEL_LIKE = Regex(
        "(?:records|recordings|music|m[uú]sica|entertainment|label|\\btv\\b|channel|canal|oficial|official|vevo|topic|lyrics|letras|hits|studios?|media|producciones|network)",
        RegexOption.IGNORE_CASE,
    )
    private val FEAT_SPLIT = Regex("\\s*(?:\\(|\\[)?\\s*\\b(?:feat\\.?|ft\\.?|featuring|con|with|prod\\.?(?:\\s+by)?)\\s+.*$", RegexOption.IGNORE_CASE)
    private val ARTIST_LIST_SPLIT = Regex("\\s*(?:,|;|/|\\s+x\\s+|\\s+&\\s+(?=.*,))\\s*", RegexOption.IGNORE_CASE)
    private val HAS_LATIN = Regex("[A-Za-z]")
    private val CJK_WORD = Regex("[\\p{IsHangul}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]")

    fun isUnknown(s: String?): Boolean = s == null || s.trim().lowercase() in UNKNOWN

    /** Quita la extensión y caracteres de relleno del nombre del archivo. */
    fun baseName(fileName: String): String = EXT.replace(fileName.trim(), "")

    /** Limpieza del título (sin separar artista). */
    fun cleanTitle(input: String): String {
        var s = input.replace('_', ' ').replace('\u00A0', ' ')
        s = LEADING_SITE.replace(s, "")
        s = SNAPTUBE_ANY.replace(s, " ")
        s = SPACES.replace(s, " ").trim()
        for (r in TRACK_NUM) s = r.replace(s, "")
        s = CJK_BRACKETS.replace(s) { m -> if (ONLY_TAGS.matches(m.groupValues[1]) || m.groupValues[1].isBlank()) " " else m.value }
        // Paréntesis/corchetes que solo contienen ruido (repetir por anidados).
        repeat(3) {
            s = BRACKETS.replace(s) { m ->
                val inner = m.groupValues[1]
                if (inner.isBlank() || ONLY_TAGS.matches(inner)) " " else m.value
            }
        }
        s = SPACES.replace(s, " ").trim()
        repeat(4) {
            val before = s
            s = TRAILING.replace(s, "")
            s = TRAILING_TOPIC.replace(s, "")
            s = EDGE.replace(s, "")
            if (before == s) return@repeat
        }
        s = WRAP_QUOTES.find(s)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: s
        s = SPACES.replace(s, " ")
        s = EDGE.replace(s, "").trim()
        return s.ifBlank { input.trim() }
    }

    /** Limpieza de artista: "- Topic", "VEVO", "Official", paréntesis, mezcla latino/coreano. */
    fun cleanArtist(input: String?): String? {
        if (isUnknown(input)) return null
        var s = input!!.replace('_', ' ').trim()
        s = SNAPTUBE_ANY.replace(s, " ").trim()
        var wasVevo = false
        repeat(3) {
            val b = s
            s = ARTIST_TOPIC.replace(s, "")
            val v = ARTIST_VEVO.replace(s, "")
            if (v != s) { wasVevo = true; s = v }
            s = ARTIST_OFFICIAL.replace(s, "")
            s = EDGE.replace(s, "").trim()
            if (b == s) return@repeat
        }
        // Quitar paréntesis (p. ej. "tripleS(트리플에스)") salvo que todo sea paréntesis.
        val noParen = BRACKETS.replace(s, " ").let { SPACES.replace(it, " ").trim() }
        if (noParen.isNotBlank()) s = noParen
        // Mezcla de palabras latinas y CJK: conservar las latinas ("IVE 아이브" -> "IVE").
        val words = s.split(' ')
        if (words.size > 1 && words.any { HAS_LATIN.containsMatchIn(it) && !CJK_WORD.containsMatchIn(it) } &&
            words.any { CJK_WORD.containsMatchIn(it) && !HAS_LATIN.containsMatchIn(it) }
        ) {
            s = words.filter { !(CJK_WORD.containsMatchIn(it) && !HAS_LATIN.containsMatchIn(it)) }.joinToString(" ")
        }
        if (wasVevo && !s.contains(' ')) s = splitCamel(s)
        s = SPACES.replace(s, " ").trim()
        return s.ifBlank { null }
    }

    /** "GunsNRoses" -> "Guns N Roses"; "TaylorSwift" -> "Taylor Swift". */
    fun splitCamel(s: String): String {
        if (s.length < 4) return s
        val sb = StringBuilder()
        for (i in s.indices) {
            val c = s[i]
            if (i > 0) {
                val p = s[i - 1]
                val n = s.getOrNull(i + 1)
                val lowerToUpper = p.isLowerCase() && c.isUpperCase()
                val acronymEnd = p.isUpperCase() && c.isUpperCase() && n != null && n.isLowerCase()
                if (lowerToUpper || acronymEnd) sb.append(' ')
            }
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Limpieza completa de un archivo.
     * @param decision decisión guardada del usuario sobre separar "A - B" (null = automático).
     */
    fun clean(
        fileName: String,
        tagTitle: String?,
        tagArtist: String?,
        tagAlbum: String?,
        decision: Boolean? = null,
    ): Result {
        val base = baseName(fileName)
        val rawTitle = if (isUnknown(tagTitle) || looksLikeFileName(tagTitle!!)) base else tagTitle
        val rawArtistTag = tagArtist?.trim()
        val channelLike = rawArtistTag != null && !isUnknown(rawArtistTag) &&
            (CHANNEL_LIKE.containsMatchIn(rawArtistTag) || ARTIST_TOPIC.containsMatchIn(rawArtistTag))
        val artistTag = cleanArtist(rawArtistTag)
        val album = cleanAlbum(tagAlbum, artistTag)

        var title = cleanTitle(rawTitle)
        val parts = splitDash(title)
        if (parts != null) {
            val (left, right) = parts
            val l = cleanArtist(left) ?: left
            val r = cleanTitle(right)
            val auto = when {
                decision == false -> false
                decision == true -> true
                artistTag == null -> true
                channelLike -> true
                key(l) == key(artistTag) -> true
                key(primaryArtist(l)) == key(primaryArtist(artistTag)) -> true
                else -> null
            }
            when {
                auto == true -> return Result(r.ifBlank { title }, l.ifBlank { artistTag }, album, didSplit = true)
                artistTag != null && key(r) == key(artistTag) -> return Result(cleanTitle(left), artistTag, album)
                auto == null -> {
                    // Quitar el prefijo si es el mismo artista; si no, preguntar en "Revisar nombres".
                    return Result(title, artistTag, album, splitCandidate = l to r)
                }
                else -> return Result(title, artistTag, album)
            }
        }
        if (artistTag == null) {
            // "Artista 'Canción' MV"
            val q = QUOTED.find(title)
            if (q != null) {
                val before = title.substring(0, q.range.first).let { EDGE.replace(it, "").trim() }
                if (before.isNotBlank() && before.length <= 40) {
                    return Result(cleanTitle(q.groupValues[1]), cleanArtist(before), album, didSplit = true)
                }
            }
        }
        title = title.ifBlank { base }
        return Result(title, artistTag, album)
    }

    private fun looksLikeFileName(t: String): Boolean = EXT.containsMatchIn(t.trim())

    private fun cleanAlbum(a: String?, artist: String?): String? {
        if (isUnknown(a)) return null
        val c = cleanTitle(a!!)
        // Snaptube/Android suele poner la carpeta como álbum ("Download", "Snaptube", "Music").
        val k = key(c)
        if (k in setOf("download", "downloads", "descargas", "music", "musica", "snaptube", "audio", "audios", "0", "sdcard", "whatsappaudio", "telegramaudio", "youtube")) return null
        return c
    }

    /** Separa "Artista - Canción" en el PRIMER guion con espacios. */
    fun splitDash(s: String): Pair<String, String>? {
        val m = DASH_SPLIT.find(s) ?: return null
        val left = s.substring(0, m.range.first).trim()
        val right = s.substring(m.range.last + 1).trim()
        if (left.isEmpty() || right.isEmpty()) return null
        if (left.length > 60) return null
        // "Pt. 2 - ..." o números sueltos no son artistas.
        if (left.all { it.isDigit() || it.isWhitespace() }) return null
        return left to right
    }

    /** Primer artista para agrupar ("A feat. B" -> "A", "A, B" -> "A"). "A & B" se respeta. */
    fun primaryArtist(artist: String): String {
        var s = FEAT_SPLIT.replace(artist, "").trim()
        s = ARTIST_LIST_SPLIT.split(s).firstOrNull()?.trim().orEmpty()
        return s.ifBlank { artist.trim() }
    }

    /** Todos los artistas de un crédito ("A feat. B & C" -> [A, B, C]). */
    fun allArtists(artist: String): List<String> =
        artist.split(Regex("\\s*(?:,|;|/|\\bfeat\\.?|\\bft\\.?|featuring|\\s+x\\s+|\\s+&\\s+|\\s+y\\s+|\\s+and\\s+|\\bwith\\b)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim().trim('(', ')', '[', ']') }
            .filter { it.isNotBlank() }

    /** Quita acentos y pasa a minúsculas. */
    fun fold(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(n.length)
        for (c in n) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    /** Clave para comparar artistas/títulos: sin mayúsculas, acentos, signos ni sufijos. */
    fun key(s: String?): String {
        if (s == null) return ""
        var t = fold(s)
        t = t.replace('$', 's').replace("&", " n ").replace("+", " n ")
        t = t.replace(Regex("^the\\s+"), "")
        t = t.replace(Regex("\\s(and|y|e|n|'n'|'n|n')\\s"), " n ")
        val sb = StringBuilder(t.length)
        for (c in t) if (c.isLetterOrDigit()) sb.append(c)
        return sb.toString()
    }

    /** Clave de título para emparejar canción/video e importar CSV: sin (feat.), sin paréntesis, sin "- Remastered". */
    fun titleKey(title: String): String {
        var t = cleanTitle(title)
        t = FEAT_SPLIT.replace(t, "")
        t = BRACKETS.replace(t, " ")
        t = CJK_BRACKETS.replace(t, " ")
        t = t.replace(Regex("\\s+[-–—]\\s+(?:.*\\b(?:remaster(?:ed)?|version|versi[oó]n|edit|mix|live|en vivo|ao vivo)\\b.*)$", RegexOption.IGNORE_CASE), "")
        val k = key(t)
        return k.ifBlank { key(title) }
    }

    fun artistKey(artist: String?): String = if (artist == null) "" else key(primaryArtist(artist))

    /** Distancia de edición (para uniones dudosas). */
    fun levenshtein(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}

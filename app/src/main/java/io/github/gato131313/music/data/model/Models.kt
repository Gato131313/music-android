package io.github.gato131313.music.data.model

import androidx.compose.runtime.Immutable

/** Fila tal cual sale de MediaStore. */
data class RawMedia(
    val id: Long,
    val isVideo: Boolean,
    val path: String,
    val displayName: String,
    val folder: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val genre: String?,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val mime: String?,
    val year: Int = 0,
)

@Immutable
data class Track(
    val id: Long,
    /** Clave estable (ruta en minúsculas): favoritos, ediciones, estadísticas. */
    val key: String,
    val isVideo: Boolean,
    val path: String,
    val folder: String,
    val fileName: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val durationMs: Long,
    val dateAdded: Long,
    val albumId: Long,
    val size: Long,
    val mime: String?,
    val artistKey: String,
    val albumKey: String,
    val titleKey: String,
    val rawTitle: String?,
    val rawArtist: String?,
    /** id del video pareja (si es canción) o de la canción pareja (si es video). */
    val pairId: Long? = null,
    val edited: Boolean = false,
    val search: String = "",
) {
    val displayArtist: String get() = artist ?: "Artista desconocido"
    val hasVideo: Boolean get() = !isVideo && pairId != null
}

@Immutable
data class Album(val key: String, val name: String, val artist: String, val coverId: Long, val tracks: List<Track>)

@Immutable
data class Artist(val key: String, val name: String, val tracks: List<Track>, val albumCount: Int)

@Immutable
data class Folder(val path: String, val name: String, val tracks: List<Track>, val videos: List<Track>)

@Immutable
data class Genre(val key: String, val name: String, val tracks: List<Track>)

@Immutable
data class MergeSuggestion(
    val aKey: String, val aName: String, val aCount: Int,
    val bKey: String, val bName: String, val bCount: Int,
)

@Immutable
data class SplitSuggestion(val track: Track, val artist: String, val title: String)

@Immutable
data class ReviewData(
    val merges: List<MergeSuggestion> = emptyList(),
    val splits: List<SplitSuggestion> = emptyList(),
    val unknownArtist: List<Track> = emptyList(),
) {
    val total: Int get() = merges.size + splits.size + unknownArtist.size
}

@Immutable
class Library(
    val songs: List<Track>,
    /** Videos sin pareja (sección Video). */
    val videos: List<Track>,
    val allVideos: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val folders: List<Folder>,
    val genres: List<Genre>,
    val review: ReviewData,
    val hiddenCount: Int = 0,
) {
    val byId: Map<Long, Track> = HashMap<Long, Track>(songs.size + allVideos.size).apply {
        songs.forEach { put(it.id, it) }
        allVideos.forEach { put(it.id, it) }
    }
    val byKey: Map<String, Track> = HashMap<String, Track>(songs.size + allVideos.size).apply {
        allVideos.forEach { put(it.key, it) }
        songs.forEach { put(it.key, it) }
    }

    companion object {
        val EMPTY = Library(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), ReviewData())
    }
}

/** Datos del usuario que modifican cómo se ve la biblioteca. */
data class UserEdits(
    val edits: Map<String, TrackEditData> = emptyMap(),
    /** artistKey -> (artistKey destino, nombre a mostrar opcional) */
    val aliases: Map<String, Pair<String, String?>> = emptyMap(),
    val rejected: Set<Pair<String, String>> = emptySet(),
    val splitDecisions: Map<String, Boolean> = emptyMap(),
    val hiddenPaths: Set<String> = emptySet(),
    val hiddenFolders: Set<String> = emptySet(),
    /** clave canción -> clave video ("" = nunca unir) */
    val pairOverrides: Map<String, String> = emptyMap(),
    val minDurationMs: Long = 30_000,
    val minVideoDurationMs: Long = 5_000,
)

data class TrackEditData(val title: String?, val artist: String?, val album: String?)

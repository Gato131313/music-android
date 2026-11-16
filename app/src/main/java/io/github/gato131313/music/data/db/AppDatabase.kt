package io.github.gato131313.music.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ---------- Caché del escaneo (arranque instantáneo) ----------
@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: Long,
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
    val year: Int,
)

// ---------- Datos del usuario (todos por clave = ruta en minúsculas) ----------
@Entity(tableName = "track_edit")
data class TrackEdit(@PrimaryKey val key: String, val title: String?, val artist: String?, val album: String?)

@Entity(tableName = "artist_alias")
data class ArtistAlias(@PrimaryKey val fromKey: String, val toKey: String, val toName: String?)

@Entity(tableName = "artist_reject", primaryKeys = ["a", "b"])
data class ArtistReject(val a: String, val b: String)

@Entity(tableName = "split_decision")
data class SplitDecision(@PrimaryKey val key: String, val split: Boolean)

@Entity(tableName = "pair_override")
data class PairOverride(@PrimaryKey val audioKey: String, val videoKey: String)

@Entity(tableName = "hidden")
data class Hidden(@PrimaryKey val path: String, val isFolder: Boolean, val at: Long = System.currentTimeMillis())

@Entity(tableName = "favorite")
data class Favorite(@PrimaryKey val key: String, val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "play_stat")
data class PlayStat(
    @PrimaryKey val key: String,
    val plays: Int = 0,
    val importedPlays: Int = 0,
    val skips: Int = 0,
    val lastPlayed: Long = 0,
    val msPlayed: Long = 0,
)

@Entity(tableName = "play_event", indices = [Index("at"), Index("key")])
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val at: Long,
    val ms: Long,
    val title: String,
    val artist: String?,
)

@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sort: Int = 0,
)

@Entity(tableName = "playlist_item", indices = [Index("playlistId")])
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val key: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics_cache")
data class LyricsCache(
    @PrimaryKey val key: String,
    val lyrics: String?,
    val synced: Boolean,
    /** JSON {original: español} */
    val translation: String?,
    val at: Long = System.currentTimeMillis(),
    val offsetMs: Long = 0,
)

@Entity(tableName = "eq_preset")
data class EqPreset(@PrimaryKey val name: String, val gains: String, val at: Long = System.currentTimeMillis())

@Entity(tableName = "song_fx")
data class SongFx(@PrimaryKey val key: String, val preset: String)

@Entity(tableName = "import_unmatched")
data class ImportUnmatched(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val plays: Int,
    val favorite: Boolean,
)

data class RecentKey(val key: String, val at: Long)

data class PlaylistWithCount(val id: Long, val name: String, val createdAt: Long, val count: Int, val firstKey: String?)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media") suspend fun all(): List<MediaEntity>
    @Query("DELETE FROM media") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<MediaEntity>)
    @Transaction suspend fun replaceAll(items: List<MediaEntity>) { clear(); insertAll(items) }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM track_edit") fun edits(): Flow<List<TrackEdit>>
    @Upsert suspend fun upsertEdit(e: TrackEdit)
    @Query("DELETE FROM track_edit WHERE `key` = :key") suspend fun deleteEdit(key: String)

    @Query("SELECT * FROM artist_alias") fun aliases(): Flow<List<ArtistAlias>>
    @Upsert suspend fun upsertAlias(a: ArtistAlias)
    @Query("DELETE FROM artist_alias WHERE fromKey = :fromKey") suspend fun deleteAlias(fromKey: String)

    @Query("SELECT * FROM artist_reject") fun rejects(): Flow<List<ArtistReject>>
    @Upsert suspend fun upsertReject(r: ArtistReject)

    @Query("SELECT * FROM split_decision") fun splits(): Flow<List<SplitDecision>>
    @Upsert suspend fun upsertSplit(s: SplitDecision)

    @Query("SELECT * FROM pair_override") fun pairs(): Flow<List<PairOverride>>
    @Upsert suspend fun upsertPair(p: PairOverride)
    @Query("DELETE FROM pair_override WHERE audioKey = :key") suspend fun deletePair(key: String)

    @Query("SELECT * FROM hidden ORDER BY at DESC") fun hidden(): Flow<List<Hidden>>
    @Upsert suspend fun upsertHidden(h: Hidden)
    @Query("DELETE FROM hidden WHERE path = :path") suspend fun deleteHidden(path: String)

    @Query("SELECT * FROM favorite ORDER BY addedAt DESC") fun favorites(): Flow<List<Favorite>>
    @Upsert suspend fun upsertFavorite(f: Favorite)
    @Query("DELETE FROM favorite WHERE `key` = :key") suspend fun deleteFavorite(key: String)

    @Query("SELECT * FROM song_fx") fun songFx(): Flow<List<SongFx>>
    @Upsert suspend fun upsertSongFx(s: SongFx)
    @Query("DELETE FROM song_fx WHERE `key` = :key") suspend fun deleteSongFx(key: String)

    @Query("SELECT * FROM eq_preset ORDER BY at") fun eqPresets(): Flow<List<EqPreset>>
    @Upsert suspend fun upsertEqPreset(p: EqPreset)
    @Query("DELETE FROM eq_preset WHERE name = :name") suspend fun deleteEqPreset(name: String)
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM play_stat") fun stats(): Flow<List<PlayStat>>
    @Query("SELECT * FROM play_stat") suspend fun statsNow(): List<PlayStat>
    @Query("SELECT * FROM play_stat WHERE `key` = :key") suspend fun stat(key: String): PlayStat?
    @Upsert suspend fun upsertStat(s: PlayStat)
    @Upsert suspend fun upsertStats(s: List<PlayStat>)
    @Insert suspend fun insertEvent(e: PlayEvent)
    @Query("UPDATE play_event SET ms = ms + :extra WHERE id = (SELECT MAX(id) FROM play_event WHERE `key` = :key)") suspend fun addToLastEvent(key: String, extra: Long)
    @Insert suspend fun insertEvents(e: List<PlayEvent>)
    @Query("SELECT * FROM play_event WHERE at >= :from AND at < :to ORDER BY at") suspend fun events(from: Long, to: Long): List<PlayEvent>
    @Query("SELECT * FROM play_event ORDER BY at") suspend fun allEvents(): List<PlayEvent>
    @Query("SELECT `key` AS `key`, MAX(at) AS at FROM play_event GROUP BY `key` ORDER BY at DESC LIMIT 1000") fun recent(): Flow<List<RecentKey>>
    @Query("SELECT * FROM import_unmatched ORDER BY plays DESC") fun unmatched(): Flow<List<ImportUnmatched>>
    @Query("SELECT * FROM import_unmatched") suspend fun unmatchedNow(): List<ImportUnmatched>
    @Insert suspend fun insertUnmatched(u: List<ImportUnmatched>)
    @Query("DELETE FROM import_unmatched WHERE id = :id") suspend fun deleteUnmatched(id: Long)
    @Query("DELETE FROM import_unmatched") suspend fun clearUnmatched()
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, p.createdAt, COUNT(i.id) AS count, " +
            "(SELECT i2.`key` FROM playlist_item i2 WHERE i2.playlistId = p.id ORDER BY i2.position LIMIT 1) AS firstKey " +
            "FROM playlist p LEFT JOIN playlist_item i ON i.playlistId = p.id GROUP BY p.id ORDER BY p.sort, p.createdAt",
    )
    fun playlists(): Flow<List<PlaylistWithCount>>
    @Query("SELECT * FROM playlist") suspend fun playlistsNow(): List<Playlist>
    @Insert suspend fun insert(p: Playlist): Long
    @Query("UPDATE playlist SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM playlist WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_item WHERE playlistId = :id") suspend fun clearItems(id: Long)
    @Query("SELECT * FROM playlist_item WHERE playlistId = :id ORDER BY position") fun items(id: Long): Flow<List<PlaylistItem>>
    @Query("SELECT * FROM playlist_item ORDER BY playlistId, position") suspend fun allItemsNow(): List<PlaylistItem>
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_item WHERE playlistId = :id") suspend fun maxPos(id: Long): Int
    @Insert suspend fun insertItems(items: List<PlaylistItem>)
    @Query("DELETE FROM playlist_item WHERE id = :itemId") suspend fun deleteItem(itemId: Long)
    @Query("DELETE FROM playlist_item WHERE playlistId = :pid AND `key` = :key") suspend fun deleteKey(pid: Long, key: String)

    @Transaction
    suspend fun delete(id: Long) { clearItems(id); deletePlaylist(id) }

    @Transaction
    suspend fun add(id: Long, keys: List<String>) {
        var pos = maxPos(id) + 1
        insertItems(keys.map { PlaylistItem(playlistId = id, key = it, position = pos++) })
    }

    @Transaction
    suspend fun reorder(id: Long, keys: List<String>) {
        clearItems(id)
        insertItems(keys.mapIndexed { i, k -> PlaylistItem(playlistId = id, key = k, position = i) })
    }
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE `key` = :key") suspend fun get(key: String): LyricsCache?
    @Upsert suspend fun put(l: LyricsCache)
}

@Dao
interface ResetDao {
    @Query("DELETE FROM favorite") suspend fun c0()
    @Query("DELETE FROM play_stat") suspend fun c1()
    @Query("DELETE FROM play_event") suspend fun c2()
    @Query("DELETE FROM playlist") suspend fun c3()
    @Query("DELETE FROM playlist_item") suspend fun c4()
    @Query("DELETE FROM track_edit") suspend fun c5()
    @Query("DELETE FROM artist_alias") suspend fun c6()
    @Query("DELETE FROM artist_reject") suspend fun c7()
    @Query("DELETE FROM split_decision") suspend fun c8()
    @Query("DELETE FROM pair_override") suspend fun c9()
    @Query("DELETE FROM hidden") suspend fun c10()
    @Query("DELETE FROM song_fx") suspend fun c11()
    @Query("DELETE FROM eq_preset") suspend fun c12()
    @Query("DELETE FROM import_unmatched") suspend fun c13()
}

@Database(
    entities = [
        MediaEntity::class, TrackEdit::class, ArtistAlias::class, ArtistReject::class, SplitDecision::class,
        PairOverride::class, Hidden::class, Favorite::class, PlayStat::class, PlayEvent::class, Playlist::class,
        PlaylistItem::class, LyricsCache::class, EqPreset::class, SongFx::class, ImportUnmatched::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun user(): UserDao
    abstract fun stats(): StatsDao
    abstract fun playlists(): PlaylistDao
    abstract fun lyrics(): LyricsDao
    abstract fun reset(): ResetDao

    /** Borra los datos del usuario (para restaurar un respaldo). Llamar dentro de una transacción. */
    suspend fun clearUserTables() {
        val r = reset()
        r.c0()
        r.c1()
        r.c2()
        r.c3()
        r.c4()
        r.c5()
        r.c6()
        r.c7()
        r.c8()
        r.c9()
        r.c10()
        r.c11()
        r.c12()
        r.c13()
    }

    companion object {
        fun create(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "music.db").build()
    }
}

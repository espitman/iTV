package app.itv.prototype.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series ORDER BY addedAt DESC")
    suspend fun all(): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: Long): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun byId(id: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE sourceKind = :kind AND sourceId = :sourceId LIMIT 1")
    suspend fun bySource(kind: String, sourceId: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE importState IN (:states)")
    suspend fun byStates(states: List<String>): List<SeriesEntity>

    @Insert
    suspend fun insert(entity: SeriesEntity): Long

    @Update
    suspend fun update(entity: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons ORDER BY sourceOrder ASC")
    fun observeAll(): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY sourceOrder ASC")
    fun observeForSeries(seriesId: Long): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY sourceOrder ASC")
    suspend fun forSeries(seriesId: Long): List<SeasonEntity>

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId AND seasonNumber = :seasonNumber LIMIT 1")
    suspend fun byNumber(seriesId: Long, seasonNumber: Int): SeasonEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SeasonEntity): Long

    @Update
    suspend fun update(entity: SeasonEntity)

    @Query("DELETE FROM seasons WHERE seriesId = :seriesId AND seasonNumber NOT IN (:keep)")
    suspend fun deleteMissing(seriesId: Long, keep: List<Int>)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes ORDER BY sourceOrder ASC")
    fun observeAll(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY sourceOrder ASC")
    fun observeForSeries(seriesId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY sourceOrder ASC")
    suspend fun forSeries(seriesId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun byId(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND sourceEpisodeId = :sourceEpisodeId LIMIT 1")
    suspend fun bySource(seriesId: Long, sourceEpisodeId: String): EpisodeEntity?

    @Insert
    suspend fun insert(entity: EpisodeEntity): Long

    @Update
    suspend fun update(entity: EpisodeEntity)

    @Query(
        """
        UPDATE episodes SET lastPositionMs = :position, isWatched = :watched, durationMs = :duration
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(id: Long, position: Long, watched: Boolean, duration: Long)

    @Query("UPDATE episodes SET sourcePresent = 0 WHERE seriesId = :seriesId AND sourceEpisodeId NOT IN (:keep)")
    suspend fun hideMissing(seriesId: Long, keep: List<String>)
}

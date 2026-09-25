package app.itv.prototype.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series",
    indices = [Index(value = ["sourceKind", "sourceId"], unique = true)],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKind: String,
    val sourceId: String,
    val sourceTitle: String,
    val localTitle: String?,
    val posterUrl: String?,
    val description: String?,
    val addedAt: Long,
    val refreshedAt: Long,
    val importState: String,
    val importError: String?,
    @ColumnInfo(defaultValue = "0") val isMovie: Boolean = false,
    @ColumnInfo(defaultValue = "0") val durationMinutes: Int = 0,
    val backdropUrl: String? = null,
)

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seriesId"),
        Index(value = ["seriesId", "seasonNumber"], unique = true),
        Index(value = ["seriesId", "sourceOrder"]),
    ],
)
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val seasonNumber: Int,
    val sourceOrder: Int,
    val title: String?,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["id"],
            childColumns = ["seasonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("seasonId"),
        Index(value = ["seriesId", "sourceEpisodeId"], unique = true),
        Index(value = ["seriesId", "sourceOrder"]),
        Index(value = ["seriesId", "seasonId", "sourceOrder"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val seasonId: Long,
    val sourceEpisodeId: String,
    val sourceOrder: Int,
    val displayNumber: String?,
    val title: String,
    val imageUrl: String?,
    val firstTitrajeStartSec: Int,
    val firstTitrajeEndSec: Int,
    val lastTitrajeStartSec: Int,
    val sourcePresent: Boolean,
    val lastPositionMs: Long,
    val isWatched: Boolean,
    val durationMs: Long,
)

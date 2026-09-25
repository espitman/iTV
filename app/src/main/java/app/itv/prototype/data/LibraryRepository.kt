package app.itv.prototype.data

import app.itv.prototype.core.EpisodeProgress
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.IncomingEpisode
import app.itv.prototype.core.IncomingSeason
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeason
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.MergeSnapshot
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.SourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LibraryRepository(private val db: ItvDatabase) {
    private val progressWriter = SerialProgressWriter(
        persist = { id, positionMs, durationMs -> saveProgress(id, positionMs, durationMs) },
    )
    fun observeLibrary(): Flow<List<LibrarySeries>> =
        combine(
            db.series().observeAll(),
            db.seasons().observeAll(),
            db.episodes().observeAll(),
        ) { series, seasons, episodes ->
            series.map { row ->
                toLibrary(
                    row,
                    seasons.filter { it.seriesId == row.id }.sortedBy { it.sourceOrder },
                    episodes.filter { it.seriesId == row.id }.sortedBy { it.sourceOrder },
                )
            }
        }

    fun observeSeries(id: Long): Flow<LibrarySeries?> =
        combine(
            db.series().observeById(id),
            db.seasons().observeForSeries(id),
            db.episodes().observeForSeries(id),
        ) { series, seasons, episodes ->
            series?.let { toLibrary(it, seasons, episodes) }
        }

    suspend fun library(): List<LibrarySeries> =
        db.series().all().map { toLibrary(it, db.seasons().forSeries(it.id), db.episodes().forSeries(it.id)) }

    suspend fun series(id: Long): LibrarySeries? {
        val series = db.series().byId(id) ?: return null
        return toLibrary(series, db.seasons().forSeries(id), db.episodes().forSeries(id))
    }

    suspend fun findBySource(kind: SourceKind, sourceId: String): SeriesEntity? =
        db.series().bySource(kind.name, sourceId)

    suspend fun insertSeries(
        kind: SourceKind,
        sourceId: String,
        title: String,
        posterUrl: String?,
        description: String?,
        now: Long,
        isMovie: Boolean = false,
        durationMinutes: Int = 0,
        backdropUrl: String? = null,
    ): Long {
        return db.series().insert(
            SeriesEntity(
                sourceKind = kind.name,
                sourceId = sourceId,
                sourceTitle = title,
                localTitle = null,
                posterUrl = posterUrl,
                description = description,
                addedAt = now,
                refreshedAt = 0L,
                importState = ImportState.QUEUED.name,
                importError = null,
                isMovie = isMovie,
                durationMinutes = durationMinutes,
                backdropUrl = backdropUrl,
            ),
        )
    }

    suspend fun setImportState(id: Long, state: ImportState, error: String? = null) {
        val series = db.series().byId(id) ?: return
        db.series().update(
            series.copy(
                importState = state.name,
                importError = error,
            ),
        )
    }

    suspend fun updateSeriesMeta(
        id: Long,
        title: String?,
        posterUrl: String?,
        description: String?,
        refreshedAt: Long,
        isMovie: Boolean = false,
        durationMinutes: Int = 0,
        backdropUrl: String? = null,
    ) {
        val series = db.series().byId(id) ?: return
        db.series().update(
            series.copy(
                sourceTitle = title?.ifBlank { series.sourceTitle } ?: series.sourceTitle,
                posterUrl = posterUrl ?: series.posterUrl,
                description = description ?: series.description,
                refreshedAt = refreshedAt,
                isMovie = isMovie,
                durationMinutes = durationMinutes,
                backdropUrl = backdropUrl ?: series.backdropUrl,
            ),
        )
    }

    suspend fun rename(id: Long, localTitle: String?) {
        val series = db.series().byId(id) ?: return
        db.series().update(series.copy(localTitle = localTitle?.ifBlank { null }))
    }

    suspend fun deleteSeries(id: Long) {
        db.series().delete(id)
    }

    suspend fun pendingImports(): List<SeriesEntity> =
        db.series().byStates(listOf(ImportState.QUEUED.name, ImportState.RUNNING.name))

    suspend fun markStaleRunningAsQueued() {
        db.series().byStates(listOf(ImportState.RUNNING.name)).forEach { series ->
            db.series().update(series.copy(importState = ImportState.QUEUED.name))
        }
    }

    suspend fun episode(id: Long): LibraryEpisode? {
        val entity = db.episodes().byId(id) ?: return null
        val season = db.seasons().forSeries(entity.seriesId).firstOrNull { it.id == entity.seasonId }
        return entity.toLibrary(season?.seasonNumber ?: 0)
    }

    suspend fun saveProgress(id: Long, positionMs: Long, durationMs: Long) {
        val watched = PlaybackRules.isWatched(positionMs, durationMs)
        val stored = if (watched) 0L else PlaybackRules.persistablePosition(positionMs, durationMs)
        val alreadyWatched = db.episodes().byId(id)?.isWatched == true
        db.episodes().updateProgress(id, if (alreadyWatched) 0L else stored, watched || alreadyWatched, durationMs.coerceAtLeast(0L))
    }

    suspend fun setWatched(id: Long, watched: Boolean) {
        progressWriter.flush()
        val episode = db.episodes().byId(id) ?: return
        db.episodes().updateProgress(id, 0L, watched, episode.durationMs)
    }

    fun submitProgress(id: Long, positionMs: Long, durationMs: Long) {
        progressWriter.submit(id, positionMs, durationMs)
    }

    fun flushProgress() {
        progressWriter.flushBlocking()
    }

    suspend fun commitPage(seriesId: Long, seasons: List<IncomingSeason>, episodes: List<IncomingEpisode>) {
        seasons.forEach { incoming ->
            val existing = db.seasons().byNumber(seriesId, incoming.seasonNumber)
            if (existing == null) {
                db.seasons().insert(
                    SeasonEntity(
                        seriesId = seriesId,
                        seasonNumber = incoming.seasonNumber,
                        sourceOrder = incoming.sourceOrder,
                        title = incoming.title,
                    ),
                )
            } else if (existing.sourceOrder != incoming.sourceOrder || existing.title != incoming.title) {
                db.seasons().update(
                    existing.copy(sourceOrder = incoming.sourceOrder, title = incoming.title ?: existing.title),
                )
            }
        }
        val seasonIds = db.seasons().forSeries(seriesId).associate { it.seasonNumber to it.id }
        episodes.forEach { incoming ->
            val seasonId = seasonIds[incoming.seasonNumber] ?: return@forEach
            val existing = db.episodes().bySource(seriesId, incoming.sourceEpisodeId)
            if (existing == null) {
                db.episodes().insert(
                    EpisodeEntity(
                        seriesId = seriesId,
                        seasonId = seasonId,
                        sourceEpisodeId = incoming.sourceEpisodeId,
                        sourceOrder = incoming.sourceOrder,
                        displayNumber = incoming.displayNumber,
                        title = incoming.title,
                        imageUrl = incoming.imageUrl,
                        firstTitrajeStartSec = incoming.firstTitrajeStartSec,
                        firstTitrajeEndSec = incoming.firstTitrajeEndSec,
                        lastTitrajeStartSec = incoming.lastTitrajeStartSec,
                        sourcePresent = true,
                        lastPositionMs = 0L,
                        isWatched = false,
                        durationMs = 0L,
                    ),
                )
            } else {
                db.episodes().update(
                    existing.copy(
                        seasonId = seasonId,
                        sourceOrder = incoming.sourceOrder,
                        displayNumber = incoming.displayNumber,
                        title = incoming.title.ifBlank { existing.title },
                        imageUrl = incoming.imageUrl ?: existing.imageUrl,
                        firstTitrajeStartSec = incoming.firstTitrajeStartSec,
                        firstTitrajeEndSec = incoming.firstTitrajeEndSec,
                        lastTitrajeStartSec = incoming.lastTitrajeStartSec,
                        sourcePresent = true,
                    ),
                )
            }
        }
    }

    suspend fun finishRefresh(seriesId: Long, keepEpisodeIds: List<String>) {
        if (keepEpisodeIds.isEmpty()) {
            db.episodes().forSeries(seriesId).forEach { episode ->
                if (episode.sourcePresent) {
                    db.episodes().update(episode.copy(sourcePresent = false))
                }
            }
        } else {
            db.episodes().hideMissing(seriesId, keepEpisodeIds)
        }
    }

    suspend fun snapshots(seriesId: Long): List<MergeSnapshot> =
        db.episodes().forSeries(seriesId).map { episode ->
            MergeSnapshot(
                sourceEpisodeId = episode.sourceEpisodeId,
                progress = EpisodeProgress(episode.lastPositionMs, episode.isWatched, episode.durationMs),
                sourcePresent = episode.sourcePresent,
            )
        }

    fun toLibrary(series: SeriesEntity, seasons: List<SeasonEntity>, episodes: List<EpisodeEntity>): LibrarySeries {
        val seasonNumberById = seasons.associate { it.id to it.seasonNumber }
        return LibrarySeries(
            id = series.id,
            kind = SourceKind.fromStorage(series.sourceKind),
            sourceId = series.sourceId,
            title = series.localTitle?.ifBlank { null } ?: series.sourceTitle,
            sourceTitle = series.sourceTitle,
            localTitle = series.localTitle,
            posterUrl = series.posterUrl,
            description = series.description,
            addedAt = series.addedAt,
            refreshedAt = series.refreshedAt,
            importState = ImportState.fromStorage(series.importState),
            importError = series.importError,
            isMovie = series.isMovie,
            durationMinutes = series.durationMinutes,
            backdropUrl = series.backdropUrl,
            seasons = seasons.sortedBy { it.sourceOrder }.map {
                LibrarySeason(it.id, it.seriesId, it.seasonNumber, it.sourceOrder, it.title)
            },
            episodes = episodes.sortedBy { it.sourceOrder }.map { it.toLibrary(seasonNumberById[it.seasonId] ?: 0) },
        )
    }

    private fun EpisodeEntity.toLibrary(seasonNumber: Int) = LibraryEpisode(
        id = id,
        seriesId = seriesId,
        seasonId = seasonId,
        sourceEpisodeId = sourceEpisodeId,
        sourceOrder = sourceOrder,
        seasonNumber = seasonNumber,
        displayNumber = displayNumber,
        title = title,
        imageUrl = imageUrl,
        firstTitrajeStartSec = firstTitrajeStartSec,
        firstTitrajeEndSec = firstTitrajeEndSec,
        lastTitrajeStartSec = lastTitrajeStartSec,
        sourcePresent = sourcePresent,
        lastPositionMs = lastPositionMs,
        isWatched = isWatched,
        durationMs = durationMs,
    )
}

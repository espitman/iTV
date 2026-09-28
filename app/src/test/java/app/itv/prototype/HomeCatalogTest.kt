package app.itv.prototype

import app.itv.prototype.core.HomeCatalog
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCatalogTest {
    @Test
    fun homeShowsEightNewestPerTypeWhileGridKeepsAll() {
        val items = (1L..15L).flatMap { number ->
            listOf(series(number, false), series(number + 100, true, number))
        }.reversed()

        assertEquals((15L downTo 8L).toList(), HomeCatalog.recent(items, false).map { it.id })
        assertEquals((115L downTo 108L).toList(), HomeCatalog.recent(items, true).map { it.id })
        assertEquals(8, HomeCatalog.recent(items, false).size)
        assertEquals(15, HomeCatalog.all(items, false).size)
        assertEquals(15, HomeCatalog.all(items, true).size)
        assertEquals((15L downTo 12L).toList(), HomeCatalog.recent(items, false, 4).map { it.id })
    }

    @Test
    fun featuredPrefersContinueWatchingThenNewestSeries() {
        val movie = series(9, true)
        val unstarted = series(1, false)
        val partial = series(2, false).copy(episodes = listOf(episode(2, 15_000, false)))
        assertEquals(2L, HomeCatalog.featured(listOf(movie, unstarted, partial))?.id)
        assertEquals(1L, HomeCatalog.featured(listOf(movie, unstarted))?.id)
        assertEquals(9L, HomeCatalog.featured(listOf(movie))?.id)
    }

    @Test
    fun searchMatchesLocalAndSourceTitles() {
        val first = series(1, false).copy(sourceTitle = "Shahrzad", localTitle = "شهرزاد")
        val second = series(2, true).copy(title = "فیلم نمونه")
        assertEquals(listOf(1L), HomeCatalog.search(listOf(first, second), "شهر").map { it.id })
        assertEquals(listOf(2L, 1L), HomeCatalog.search(listOf(first, second), "").map { it.id })
    }

    @Test
    fun continueWatchingIncludesStartedUnfinishedSeriesOnly() {
        val unstarted = series(1, false).copy(episodes = listOf(episode(1, 0, false)))
        val partial = series(2, false).copy(episodes = listOf(episode(2, 15_000, false)))
        val watchedOne = series(3, false).copy(episodes = listOf(episode(3, 0, true), episode(3, 0, false)))
        val finished = series(4, false).copy(episodes = listOf(episode(4, 0, true)))
        val movie = series(5, true).copy(episodes = listOf(episode(5, 15_000, false)))
        assertEquals(listOf(3L, 2L), HomeCatalog.continueWatching(listOf(unstarted, partial, watchedOne, finished, movie)).map { it.id })
    }

    private fun episode(seriesId: Long, positionMs: Long, watched: Boolean) = LibraryEpisode(
        id = seriesId,
        seriesId = seriesId,
        seasonId = 1,
        sourceEpisodeId = "episode-$seriesId",
        sourceOrder = 0,
        seasonNumber = 1,
        displayNumber = "1",
        title = "قسمت ۱",
        imageUrl = null,
        firstTitrajeStartSec = 0,
        firstTitrajeEndSec = 0,
        lastTitrajeStartSec = 0,
        sourcePresent = true,
        lastPositionMs = positionMs,
        isWatched = watched,
        durationMs = 60_000,
    )

    private fun series(id: Long, movie: Boolean, addedAt: Long = id) = LibrarySeries(
        id = id,
        kind = SourceKind.PRODUCT,
        sourceId = id.toString(),
        title = id.toString(),
        sourceTitle = id.toString(),
        localTitle = null,
        posterUrl = null,
        description = null,
        addedAt = addedAt,
        refreshedAt = 0L,
        importState = ImportState.DONE,
        importError = null,
        seasons = emptyList(),
        episodes = emptyList(),
        isMovie = movie,
    )
}

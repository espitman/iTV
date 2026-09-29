package app.itv.prototype

import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.SeriesDetail
import app.itv.prototype.ui.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesDetailTest {
    @Test
    fun playActionUsesResumeThresholdAndMovieFlag() {
        val fresh = episode()
        val resumed = episode(position = PlaybackRules.RESUME_THRESHOLD_MS + 1)
        assertEquals(SeriesDetail.PlayAction.PLAY_FIRST, SeriesDetail.playAction(false, null))
        assertEquals(SeriesDetail.PlayAction.PLAY_EPISODE, SeriesDetail.playAction(false, fresh))
        assertEquals(SeriesDetail.PlayAction.CONTINUE_EPISODE, SeriesDetail.playAction(false, resumed))
        assertEquals(SeriesDetail.PlayAction.PLAY_MOVIE, SeriesDetail.playAction(true, fresh))
        assertEquals(SeriesDetail.PlayAction.CONTINUE_EPISODE, SeriesDetail.playAction(true, resumed))
    }

    @Test
    fun selectedSeasonPrefersCurrentThenContinueThenFirst() {
        val seasons = listOf(1, 2, 3)
        assertEquals(2, SeriesDetail.selectedSeason(seasons, 2, 3))
        assertEquals(3, SeriesDetail.selectedSeason(seasons, 9, 3))
        assertEquals(1, SeriesDetail.selectedSeason(seasons, null, null))
        assertEquals(4, SeriesDetail.selectedSeason(listOf(4), 1, 2))
        assertNull(SeriesDetail.selectedSeason(emptyList(), 1, 1))
    }

    @Test
    fun progressAndOverlayUseCatalogDurationAndWatchState() {
        val fresh = episode(duration = 100_000L)
        val mid = episode(position = 80_000L, duration = 100_000L)
        val done = episode(position = 95_000L, duration = 100_000L, watched = true)
        assertEquals(0f, SeriesDetail.progressFraction(fresh), 0f)
        assertEquals(0.8f, SeriesDetail.progressFraction(mid), 0f)
        assertEquals(1f, SeriesDetail.progressFraction(done), 0f)
        assertEquals(100_000L, SeriesDetail.overlayRemainingMs(fresh))
        assertEquals(20_000L, SeriesDetail.overlayRemainingMs(mid))
        assertNull(SeriesDetail.overlayRemainingMs(done))
    }

    @Test
    fun seasonChipsShowForAnySeriesButNeverForMovies() {
        assertEquals(true, SeriesDetail.showSeasonChips(false, 1))
        assertEquals(true, SeriesDetail.showSeasonChips(false, 2))
        assertEquals(false, SeriesDetail.showSeasonChips(false, 0))
        assertEquals(false, SeriesDetail.showSeasonChips(true, 1))
        assertEquals(false, SeriesDetail.showSeasonChips(true, 2))
    }

    @Test
    fun rtlStartScrollAccountsForStartPaddingSoFirstCaptionStaysOnScreen() {
        val startPad = 40
        val endPad = 16
        val viewport = 960
        val row = 26 * (160 + 8)
        val scroll = HomeLayout.rtlStartScrollX(row, viewport, endPad, startPad)
        val childRight = row - scroll
        assertEquals(true, childRight <= viewport - startPad)
        assertEquals(true, childRight >= viewport - startPad - endPad)
    }

    @Test
    fun episodeTilesFitFiveToSixOnSixteenByNineTv() {
        val width = 160
        val thumb = 90
        val gap = 8
        val startPad = 40
        val endPad = 16
        assertEquals(0.167f, SeriesDetail.widthFraction(width), 0.01f)
        assertEquals(0.167f, SeriesDetail.heightFraction(thumb), 0.01f)
        val visible = SeriesDetail.visibleEpisodeCount(SeriesDetail.TV_WIDTH_DP, startPad, endPad, width, gap)
        assertEquals(true, visible >= 5f)
        assertEquals(true, visible < 6.3f)
    }

    @Test
    fun heroStaysUpperLeftAndEpisodeRowSitsNearSeventyPercent() {
        assertEquals(0.604f, SeriesDetail.widthFraction(SeriesDetail.HERO_WIDTH_DP), 0.02f)
        assertEquals(0.50f, SeriesDetail.heightFraction(SeriesDetail.HERO_HEIGHT_DP), 0.03f)
        assertEquals(true, SeriesDetail.heightFraction(SeriesDetail.HERO_HEIGHT_DP) <= 0.52f)
        val thumbTop = SeriesDetail.episodeThumbTopDp(
            thumbHeightDp = 90,
            focusPadDp = 3,
        )
        assertEquals(0.70f, SeriesDetail.heightFraction(thumbTop), 0.02f)
        assertEquals(0.167f, SeriesDetail.widthFraction(160), 0.01f)
        assertEquals(0.167f, SeriesDetail.heightFraction(90), 0.01f)
    }

    private fun episode(
        position: Long = 0L,
        duration: Long = 0L,
        watched: Boolean = false,
    ) = LibraryEpisode(
        id = 1L,
        seriesId = 1L,
        seasonId = 1L,
        sourceEpisodeId = "e1",
        sourceOrder = 0,
        seasonNumber = 1,
        displayNumber = "15",
        title = "قسمت ۱۵",
        imageUrl = null,
        firstTitrajeStartSec = 0,
        firstTitrajeEndSec = 0,
        lastTitrajeStartSec = 0,
        sourcePresent = true,
        lastPositionMs = position,
        isWatched = watched,
        durationMs = duration,
    )
}

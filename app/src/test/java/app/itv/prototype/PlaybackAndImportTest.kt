package app.itv.prototype

import app.itv.prototype.core.EpisodeProgress
import app.itv.prototype.core.ImportMerge
import app.itv.prototype.core.IncomingEpisode
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.MergeSnapshot
import app.itv.prototype.core.NextCountdown
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.data.CatalogMapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAndImportTest {
    @Test
    fun dpadSeekStartsAtTenSecondsAndSpeedsUpWhenHeld() {
        assertEquals(10_000L, PlaybackRules.dpadSeekStep(0L))
        assertEquals(10_000L, PlaybackRules.dpadSeekStep(2_000L))
        assertEquals(20_000L, PlaybackRules.dpadSeekStep(2_001L))
    }

    @Test
    fun keepsDuplicateDisplayNumbersAndSourceGaps() {
        val json = JSONArray()
        listOf("۲۶" to "a", "26" to "b", "31" to "c", "31" to "d", "33" to "e").forEach { (title, id) ->
            json.put(JSONObject().put("EpisodeID", id).put("title", "قسمت $title"))
        }
        val mapped = CatalogMapper.programEpisodes(json, 0)
        assertEquals(listOf("26", "26", "31", "31", "33"), mapped.map { it.displayNumber })
        assertEquals(listOf(0, 1, 2, 3, 4), mapped.map { it.sourceOrder })
        assertEquals(listOf("a", "b", "c", "d", "e"), mapped.map { it.sourceEpisodeId })
        assertFalse(mapped.any { it.displayNumber == "32" })
        assertEquals(5, mapped.size)
    }

    @Test
    fun nextEpisodeUsesSourceOrderNotDisplayNumber() {
        val rows = listOf(
            episode("a", 0, "26"),
            episode("b", 1, "26"),
            episode("c", 2, "31"),
        )
        assertEquals("b", PlaybackRules.nextPresent(rows[0], rows)?.sourceEpisodeId)
        assertEquals("c", PlaybackRules.nextPresent(rows[1], rows)?.sourceEpisodeId)
        assertNull(PlaybackRules.nextPresent(rows[2], rows))
    }

    @Test
    fun continueUsesHighestPartiallyWatchedEpisode() {
        val rows = (1..9).map { number ->
            episode("e$number", 10 - number, number.toString()).copy(
                lastPositionMs = if (number == 4 || number == 8) 60_000L else 0L,
                isWatched = number < 4,
            )
        }
        assertEquals("e8", PlaybackRules.firstUnwatchedOrContinue(rows)?.sourceEpisodeId)
    }

    @Test
    fun continueUsesEpisodeAfterLastWatchedWhenNoneIsPartial() {
        val rows = (1..5).map { number ->
            episode("e$number", number, number.toString()).copy(isWatched = number <= 3)
        }
        assertEquals("e4", PlaybackRules.firstUnwatchedOrContinue(rows)?.sourceEpisodeId)
        assertEquals("e1", PlaybackRules.firstUnwatchedOrContinue(rows.map { it.copy(isWatched = false) })?.sourceEpisodeId)
    }

    @Test
    fun skipCreditsOnlyFromPositiveMarkers() {
        assertFalse(PlaybackRules.skipIntroActive(15_000, 0, 20))
        assertFalse(PlaybackRules.skipIntroActive(15_000, 10, 0))
        assertTrue(PlaybackRules.skipIntroActive(15_000, 10, 20))
        assertFalse(PlaybackRules.skipOutroActive(1_000, 0, 120_000))
        assertTrue(PlaybackRules.skipOutroActive(100_000, 90, 120_000))
        assertEquals(0, PlaybackRules.positiveMarker(0))
        assertEquals(12, PlaybackRules.positiveMarker(12))
    }

    @Test
    fun refreshKeepsProgressAndHidesMissing() {
        val existing = listOf(
            MergeSnapshot("keep", EpisodeProgress(12_000, false, 90_000), true),
            MergeSnapshot("gone", EpisodeProgress(4_000, true, 80_000), true),
        )
        val incoming = listOf(
            IncomingEpisode("keep", 0, 1, "1", "one", null),
            IncomingEpisode("new", 1, 1, "2", "two", null),
        )
        val merged = ImportMerge.apply(existing, incoming)
        val keep = merged.first { it.incoming.sourceEpisodeId == "keep" }
        val gone = merged.first { it.incoming.sourceEpisodeId == "gone" }
        val added = merged.first { it.incoming.sourceEpisodeId == "new" }
        assertEquals(12_000L, keep.progress.lastPositionMs)
        assertTrue(keep.sourcePresent)
        assertFalse(gone.sourcePresent)
        assertTrue(gone.progress.isWatched)
        assertTrue(added.sourcePresent)
        assertEquals(0L, added.progress.lastPositionMs)
        assertEquals(setOf(1, 2), ImportMerge.retainedSeasonNumbers(listOf(1, 2), listOf(1)))
    }

    @Test
    fun rejectsFabricatedHostAndAcceptsTelewebionHttps() {
        assertNull(PlaybackRules.acceptHlsUrl("https://evil.test/episode/0x1/playlist.m3u8"))
        assertNull(PlaybackRules.acceptHlsUrl("http://cdna.telewebion.net/ifilm/episode/0x1/playlist.m3u8"))
        assertEquals(
            "https://cdna.telewebion.net/ifilm/episode/0xff56cd3/playlist.m3u8",
            PlaybackRules.acceptHlsUrl("https://cdna.telewebion.net/ifilm/episode/0xff56cd3/playlist.m3u8"),
        )
    }

    @Test
    fun nextCountdownStartsAtTenAndAutoplaysAtZeroOncePerTick() {
        var state = NextCountdown().onWindow(false)
        assertFalse(state.started)
        state = state.onWindow(true)
        assertTrue(state.started)
        assertEquals(10, state.remainingSec)
        assertFalse(state.shouldAutoPlay)
        repeat(9) { state = state.tick() }
        assertEquals(1, state.remainingSec)
        assertFalse(state.shouldAutoPlay)
        state = state.tick()
        assertEquals(0, state.remainingSec)
        assertTrue(state.shouldAutoPlay)
        state = state.tick()
        assertEquals(0, state.remainingSec)
        assertTrue(state.shouldAutoPlay)
    }

    @Test
    fun nextCountdownCancelBlocksAutoAdvanceAndResetsOnEpisodeChange() {
        var state = NextCountdown().onWindow(true).tick().cancel()
        assertTrue(state.cancelled)
        assertFalse(state.started)
        assertFalse(state.shouldAutoPlay)
        state = state.onWindow(true).tick()
        assertTrue(state.cancelled)
        assertFalse(state.shouldAutoPlay)
        assertFalse(PlaybackRules.shouldAutoAdvanceOnEnded(hasNext = true, cancelled = true))
        assertTrue(PlaybackRules.shouldAutoAdvanceOnEnded(hasNext = true, cancelled = false))
        assertFalse(PlaybackRules.shouldAutoAdvanceOnEnded(hasNext = false, cancelled = false))
        state = state.reset()
        assertFalse(state.cancelled)
        assertEquals(10, state.remainingSec)
        assertTrue(PlaybackRules.nextLockActive(true, false, 100_000, 80_000))
        assertFalse(PlaybackRules.nextLockActive(true, true, 100_000, 80_000))
        assertFalse(PlaybackRules.nextLockActive(false, false, 100_000, 80_000))
    }

    @Test
    fun nextCountdownResetsWhenLeavingTheLastThirtySeconds() {
        var state = NextCountdown().onWindow(true).tick().tick()
        assertEquals(8, state.remainingSec)
        state = state.onWindow(false)
        assertFalse(state.started)
        assertEquals(10, state.remainingSec)
        state = state.onWindow(true)
        assertEquals(10, state.remainingSec)
        assertTrue(state.started)
    }

    @Test
    fun watchedThresholdAndNearEndReset() {
        assertTrue(PlaybackRules.isWatched(95_000, 100_000))
        assertFalse(PlaybackRules.isWatched(10_000, 20_000))
        assertEquals(0L, PlaybackRules.persistablePosition(95_000, 100_000))
        assertEquals(20_000L, PlaybackRules.persistablePosition(20_000, 100_000))
        assertTrue(PlaybackRules.shouldPromptResume(11_000))
        assertFalse(PlaybackRules.shouldPromptResume(9_000))
    }

    private fun episode(id: String, order: Int, display: String) = LibraryEpisode(
        id = order.toLong(),
        seriesId = 1,
        seasonId = 1,
        sourceEpisodeId = id,
        sourceOrder = order,
        seasonNumber = 1,
        displayNumber = display,
        title = "قسمت $display",
        imageUrl = null,
        firstTitrajeStartSec = 0,
        firstTitrajeEndSec = 0,
        lastTitrajeStartSec = 0,
        sourcePresent = true,
        lastPositionMs = 0,
        isWatched = false,
        durationMs = 0,
    )
}

package app.itv.prototype

import app.itv.prototype.core.ImportState
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.PresentationOrder
import app.itv.prototype.core.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PresentationOrderTest {
    @Test
    fun descendingProgramRowsDisplayFromOneToLastWithoutFabricatingGaps() {
        val source = descendingProgramLike0x1b29939()
        assertEquals("35", source.first().displayNumber)
        assertEquals("1", source.last().displayNumber)
        assertEquals(36, source.size)

        val visible = PresentationOrder.present(source)
        assertEquals(36, visible.size)
        assertEquals("1", visible.first().displayNumber)
        assertEquals("35", visible.last().displayNumber)
        assertEquals(
            listOf("26", "26"),
            visible.mapNotNull { it.displayNumber }.filter { it == "26" },
        )
        assertEquals(
            listOf("31", "31"),
            visible.mapNotNull { it.displayNumber }.filter { it == "31" },
        )
        assertFalse(visible.any { it.displayNumber == "32" })
        assertEquals(source.map { it.sourceEpisodeId }.toSet(), visible.map { it.sourceEpisodeId }.toSet())
        assertEquals(
            listOf(9, 10),
            visible.filter { it.displayNumber == "26" }.map { it.sourceOrder },
        )
    }

    @Test
    fun missingDisplayNumbersStayAfterNumberedRowsAndKeepSourceOrder() {
        val rows = listOf(
            episode("late", 5, "3"),
            episode("no-number-first", 1, null),
            episode("no-number-second", 4, ""),
            episode("early", 2, "1"),
            episode("mid", 3, "2"),
        )
        val visible = PresentationOrder.present(rows)
        assertEquals(
            listOf("early", "mid", "late", "no-number-first", "no-number-second"),
            visible.map { it.sourceEpisodeId },
        )
    }

    @Test
    fun nextAndContinueFollowVisibleOrderForDuplicatesAndGaps() {
        val rows = descendingProgramLike0x1b29939()
        val visible = PresentationOrder.present(rows)
        val first = visible.first()
        val second = visible[1]
        val first26 = visible.first { it.displayNumber == "26" }
        val second26 = visible.last { it.displayNumber == "26" }
        val first31 = visible.first { it.displayNumber == "31" }
        val after31 = visible[visible.indexOfFirst { it.sourceEpisodeId == first31.sourceEpisodeId } + 1]
        val last = visible.last()

        assertEquals("1", first.displayNumber)
        assertEquals("2", PlaybackRules.nextPresent(first, rows)?.displayNumber)
        assertEquals(second.sourceEpisodeId, PlaybackRules.nextPresent(first, rows)?.sourceEpisodeId)
        assertEquals(second26.sourceEpisodeId, PlaybackRules.nextPresent(first26, rows)?.sourceEpisodeId)
        assertEquals("31", after31.displayNumber)
        assertEquals("33", PlaybackRules.nextPresent(visible.last { it.displayNumber == "31" }, rows)?.displayNumber)
        assertNull(PlaybackRules.nextPresent(last, rows))

        val watchedThroughFirst26 = rows.map { row ->
            val visibleIndex = visible.indexOfFirst { it.sourceEpisodeId == row.sourceEpisodeId }
            val first26Index = visible.indexOfFirst { it.sourceEpisodeId == first26.sourceEpisodeId }
            row.copy(isWatched = visibleIndex >= 0 && visibleIndex <= first26Index)
        }
        assertEquals(second26.sourceEpisodeId, PlaybackRules.firstUnwatchedOrContinue(watchedThroughFirst26)?.sourceEpisodeId)

        val resumeOnSecond = rows.map { row ->
            if (row.sourceEpisodeId == second.sourceEpisodeId) {
                row.copy(lastPositionMs = 12_000L)
            } else {
                row
            }
        }
        assertEquals(second.sourceEpisodeId, PlaybackRules.firstUnwatchedOrContinue(resumeOnSecond)?.sourceEpisodeId)
        assertEquals(first.sourceEpisodeId, LibrarySeries(episodes = rows).continueEpisode?.sourceEpisodeId)
        assertEquals("1", LibrarySeries(episodes = rows).presentEpisodes.first().displayNumber)
    }

    @Test
    fun seasonsStayGroupedAndProgressStaysOnSourceEpisodeId() {
        val rows = listOf(
            episode("s2e1", 0, "1", season = 2),
            episode("s1e2", 1, "2", season = 1),
            episode("s1e1", 2, "1", season = 1),
        )
        val visible = PresentationOrder.present(rows)
        assertEquals(listOf("s1e1", "s1e2", "s2e1"), visible.map { it.sourceEpisodeId })
        assertEquals("s1e2", PlaybackRules.nextPresent(visible[0], rows)?.sourceEpisodeId)
        assertEquals("s2e1", PlaybackRules.nextPresent(visible[1], rows)?.sourceEpisodeId)
        val resumed = rows.map {
            if (it.sourceEpisodeId == "s1e2") it.copy(lastPositionMs = 20_000L) else it
        }
        assertEquals("s1e2", PlaybackRules.firstUnwatchedOrContinue(resumed)?.sourceEpisodeId)
    }

    private fun descendingProgramLike0x1b29939(): List<LibraryEpisode> {
        val display = (35 downTo 1).flatMap { number ->
            when (number) {
                32 -> emptyList()
                26, 31 -> listOf(number, number)
                else -> listOf(number)
            }
        }
        check(display.size == 36)
        check(display.count { it == 26 } == 2)
        check(display.count { it == 31 } == 2)
        check(32 !in display)
        return display.mapIndexed { index, number ->
            episode("src-$index", index, number.toString())
        }
    }

    private fun episode(
        id: String,
        order: Int,
        display: String?,
        season: Int = 1,
        present: Boolean = true,
    ) = LibraryEpisode(
        id = order.toLong() + 1,
        seriesId = 1,
        seasonId = season.toLong(),
        sourceEpisodeId = id,
        sourceOrder = order,
        seasonNumber = season,
        displayNumber = display,
        title = display?.let { "قسمت $it" } ?: id,
        imageUrl = null,
        firstTitrajeStartSec = 0,
        firstTitrajeEndSec = 0,
        lastTitrajeStartSec = 0,
        sourcePresent = present,
        lastPositionMs = 0,
        isWatched = false,
        durationMs = 0,
    )

    private fun LibrarySeries(episodes: List<LibraryEpisode>) = LibrarySeries(
        id = 1,
        kind = SourceKind.PROGRAM,
        sourceId = "0x1b29939",
        title = "روشن‌تر از خاموشی",
        sourceTitle = "روشن‌تر از خاموشی",
        localTitle = null,
        posterUrl = null,
        description = null,
        addedAt = 0,
        refreshedAt = 0,
        importState = ImportState.DONE,
        importError = null,
        seasons = emptyList(),
        episodes = episodes,
    )
}

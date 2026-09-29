package app.itv.prototype

import app.itv.prototype.ui.ImageSize
import app.itv.prototype.ui.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun rtlStartKeepsFirstCardOnTheRight() {
        assertEquals(400, HomeLayout.rtlStartScrollX(rowWidth = 1200, viewportWidth = 800))
        assertEquals(0, HomeLayout.rtlStartScrollX(rowWidth = 400, viewportWidth = 800))
        assertEquals(420, HomeLayout.rtlStartScrollX(rowWidth = 1200, viewportWidth = 800, paddingLeft = 10, paddingRight = 10))
    }

    @Test
    fun homeShelfCapsAtEightAndKeepsMoreOnScreen() {
        val poster = 176
        val gap = 16
        val pad = 8
        val more = 176
        val tv1080 = 1792
        val tv720 = 1152
        val eightPlusMore = HomeLayout.shelfRowWidth(8, poster, gap, more)
        assertEquals(8, HomeLayout.shelfVisibleCount(tv1080, poster, gap, pad, pad, more, 8))
        assertEquals(8, HomeLayout.shelfVisibleCount(tv1080, poster, gap, pad, pad, more, 12))
        assertTrue(eightPlusMore <= tv1080 - 2 * pad)
        assertTrue(HomeLayout.shelfRowWidth(9, poster, gap, more) > tv1080 - 2 * pad)
        assertEquals(4, HomeLayout.shelfVisibleCount(tv720, poster, gap, pad, pad, more, 8))
        val fourPlusMore = HomeLayout.shelfRowWidth(4, poster, gap, more)
        assertTrue(fourPlusMore <= tv720 - 2 * pad)
        assertTrue(HomeLayout.shelfRowWidth(5, poster, gap, more) > tv720 - 2 * pad)
        assertEquals(0, HomeLayout.shelfVisibleCount(20, poster, gap, pad, pad, more, 8))
        assertEquals(3, HomeLayout.shelfVisibleCount(tv1080, poster, gap, pad, pad, more, 3))
    }

    @Test
    fun returningToTheRtlStartCardRestoresStartPadding() {
        val row = 4200
        val viewport = 3840
        val endPad = 64
        val startPad = 160
        val start = HomeLayout.rtlStartScrollX(row, viewport, endPad, startPad)
        val flushWithEdge = (start - startPad).coerceAtLeast(0)
        assertEquals(
            start,
            HomeLayout.rtlFocusScrollX(
                scrollX = flushWithEdge,
                rowWidth = row,
                viewportWidth = viewport,
                paddingLeft = endPad,
                paddingRight = startPad,
                pinToStart = false,
                focusedAtRtlStart = true,
            ),
        )
        assertEquals(
            flushWithEdge,
            HomeLayout.rtlFocusScrollX(
                scrollX = flushWithEdge,
                rowWidth = row,
                viewportWidth = viewport,
                paddingLeft = endPad,
                paddingRight = startPad,
                pinToStart = false,
                focusedAtRtlStart = false,
            ),
        )
    }

    @Test
    fun revealDeltaOnlyMovesWhenTheCardIsClipped() {
        assertEquals(0, HomeLayout.revealDelta(childStart = 400, childEnd = 500, viewportStart = 400, viewportEnd = 800))
        assertEquals(-40, HomeLayout.revealDelta(childStart = 360, childEnd = 460, viewportStart = 400, viewportEnd = 800))
        assertEquals(50, HomeLayout.revealDelta(childStart = 760, childEnd = 850, viewportStart = 400, viewportEnd = 800))
    }

    @Test
    fun libraryScrollTopPinsContinueBelowHeroCopy() {
        val heroCopy = 246
        val heading = 30
        val continueRow = 78
        assertEquals(heroCopy, HomeLayout.libraryScrollTop(heroCopy, heading, continueRow, hasContinue = false))
        assertEquals(354, HomeLayout.libraryScrollTop(heroCopy, heading, continueRow, hasContinue = true))
        assertEquals(0, HomeLayout.sectionSnapY(0))
        assertEquals(176, HomeLayout.sectionSnapY(176))
        assertEquals(0, HomeLayout.sectionSnapY(-12))
    }

    @Test
    fun libraryDrawClipFollowsScrollYInContentCoordinates() {
        val viewportH = 744
        assertEquals(0, HomeLayout.libraryDrawClipTop(0))
        assertEquals(viewportH, HomeLayout.libraryDrawClipBottom(0, viewportH))
        assertEquals(148, HomeLayout.libraryDrawClipTop(148))
        assertEquals(148 + viewportH, HomeLayout.libraryDrawClipBottom(148, viewportH))
        assertEquals(0, HomeLayout.libraryDrawClipTop(-12))
        assertEquals(viewportH, HomeLayout.libraryDrawClipBottom(-12, viewportH))
    }

    @Test
    fun progressFillDoesNotDependOnViewWidth() {
        assertEquals(0f, HomeLayout.progressFill(0, 1000), 0f)
        assertEquals(0.25f, HomeLayout.progressFill(250, 1000), 0f)
        assertEquals(1f, HomeLayout.progressFill(2000, 1000), 0f)
        assertEquals(0f, HomeLayout.progressFill(50, 0), 0f)
    }

    @Test
    fun catalogSignatureChangesWhenContinueProgressChanges() {
        val base = HomeLayout.catalogSignature(listOf(1L), listOf(2L), listOf(3L), listOf("DONE:0:poster"))
        val moved = HomeLayout.catalogSignature(listOf(1L), listOf(2L), listOf(3L), listOf("DONE:15000:poster"))
        assertEquals(false, base == moved)
        assertEquals(base, HomeLayout.catalogSignature(listOf(1L), listOf(2L), listOf(3L), listOf("DONE:0:poster")))
    }

    @Test
    fun coverPrefersPortraitArt() {
        val poster = ImageSize(273, 410)
        val wide = ImageSize(1920, 900)
        assertEquals("p", HomeLayout.coverUrl("p", "b", poster, wide))
        assertEquals("b", HomeLayout.coverUrl("p", "b", wide, poster))
        assertEquals(true, HomeLayout.coverFitsFrame(poster))
        assertEquals(false, HomeLayout.coverFitsFrame(wide))
        assertEquals(false, HomeLayout.coverNeedsFill(poster))
        assertEquals(true, HomeLayout.coverNeedsFill(wide))
        assertEquals(true, HomeLayout.coverNeedsFill(ImageSize(440, 248)))
        assertEquals(false, HomeLayout.coverNeedsFill(null))
    }

    @Test
    fun heroPrefersWidestLandscapeAndDoesNotSideCropIt() {
        val still = ImageSize(1920, 900)
        val titleCard = ImageSize(440, 248)
        val portrait = ImageSize(273, 410)
        assertEquals(
            "wide",
            HomeLayout.heroUrl("wide", "ep", "poster", still, titleCard, portrait),
        )
        val place = HomeLayout.heroPlacement(1920, 1080, still)
        assertEquals(true, place.fullBleed)
        assertEquals(1f, place.scale, 0.001f)
        assertEquals(0f, place.translateX, 0f)
        val shortHero = HomeLayout.heroPlacement(1920, 660, still)
        assertEquals(1f, shortHero.scale, 0.001f)
        assertEquals(0f, shortHero.translateX, 0f)
        assertEquals(0f, shortHero.translateY, 0f)
        val fallback = HomeLayout.heroPlacement(1920, 1080, portrait)
        assertEquals(false, fallback.fullBleed)
        assertEquals(true, fallback.scale < 3f)
    }
}

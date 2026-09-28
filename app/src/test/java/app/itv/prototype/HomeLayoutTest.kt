package app.itv.prototype

import app.itv.prototype.ui.ImageSize
import app.itv.prototype.ui.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun rtlStartKeepsFirstCardOnTheRight() {
        assertEquals(400, HomeLayout.rtlStartScrollX(rowWidth = 1200, viewportWidth = 800))
        assertEquals(0, HomeLayout.rtlStartScrollX(rowWidth = 400, viewportWidth = 800))
        assertEquals(420, HomeLayout.rtlStartScrollX(rowWidth = 1200, viewportWidth = 800, paddingLeft = 10, paddingRight = 10))
    }

    @Test
    fun revealDeltaOnlyMovesWhenTheCardIsClipped() {
        assertEquals(0, HomeLayout.revealDelta(childStart = 400, childEnd = 500, viewportStart = 400, viewportEnd = 800))
        assertEquals(-40, HomeLayout.revealDelta(childStart = 360, childEnd = 460, viewportStart = 400, viewportEnd = 800))
        assertEquals(50, HomeLayout.revealDelta(childStart = 760, childEnd = 850, viewportStart = 400, viewportEnd = 800))
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

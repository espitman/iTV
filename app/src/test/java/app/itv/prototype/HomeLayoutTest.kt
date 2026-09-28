package app.itv.prototype

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
}

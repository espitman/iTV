package app.itv.prototype

import app.itv.prototype.ui.LibraryGridLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGridLayoutTest {
    @Test
    fun gridNeverExceedsSixColumns() {
        val wide = LibraryGridLayout.metrics(
            availableWidth = 4000,
            minColumnWidth = 120,
            titleBlock = 44,
            focusPad = 3,
        )
        assertEquals(6, wide.columns)
        assertTrue(wide.gap > 0)
        assertTrue(wide.columnWidth >= 120)
    }

    @Test
    fun narrowWidthDropsColumnsInsteadOfShrinkingBelowMinimum() {
        val narrow = LibraryGridLayout.metrics(
            availableWidth = 400,
            minColumnWidth = 120,
            titleBlock = 44,
            focusPad = 3,
        )
        assertTrue(narrow.columns in 1..3)
        assertTrue(narrow.columnWidth >= 120 || narrow.columns == 1)
    }

    @Test
    fun posterFrameStaysTwoByThreeAfterFocusPad() {
        val focusPad = 3
        val columnWidth = 132
        val height = LibraryGridLayout.posterFrameHeight(columnWidth, focusPad)
        val innerWidth = columnWidth - 2 * focusPad
        assertEquals(innerWidth * 3 / 2 + 2 * focusPad, height)
        val metrics = LibraryGridLayout.metrics(880, 120, 44, focusPad)
        assertEquals(LibraryGridLayout.posterFrameHeight(metrics.columnWidth, focusPad), metrics.posterHeight)
        assertEquals(metrics.posterHeight + 44, metrics.itemHeight)
        assertTrue(metrics.columns <= 6)
    }

    @Test
    fun gapsGrowWhenTheSameColumnCountGetsMoreRoom() {
        val compact = LibraryGridLayout.metrics(880, 120, 44, 3)
        val roomy = LibraryGridLayout.metrics(1100, 120, 44, 3)
        assertEquals(compact.columns, roomy.columns)
        assertTrue(roomy.gap > compact.gap)
        assertTrue(roomy.columnWidth > compact.columnWidth)
    }

    @Test
    fun catalogCoverUsesPosterUrlOnly() {
        assertEquals("poster", LibraryGridLayout.coverUrl("poster", "backdrop"))
        assertNull(LibraryGridLayout.coverUrl(null, "backdrop"))
        assertEquals("poster", LibraryGridLayout.coverUrl("poster", null))
    }

    @Test
    fun metaUsesRealDurationAndCountsOnly() {
        assertEquals(82, LibraryGridLayout.movieDurationMinutes(82))
        assertNull(LibraryGridLayout.movieDurationMinutes(0))
        assertEquals(true, LibraryGridLayout.hasSeriesMeta(1, 40))
        assertEquals(false, LibraryGridLayout.hasSeriesMeta(0, 0))
    }

    @Test
    fun sixColumnsFitWithoutOverflowOnTypicalCatalogWidths() {
        val mdpi = LibraryGridLayout.metrics(1840, 120, 44, 3)
        val xhdpi = LibraryGridLayout.metrics(1760, 240, 44, 6)
        assertEquals(6, mdpi.columns)
        assertEquals(6, xhdpi.columns)
        assertTrue(LibraryGridLayout.usedWidth(mdpi.columns, mdpi.columnWidth, mdpi.gap) <= 1840)
        assertTrue(LibraryGridLayout.usedWidth(xhdpi.columns, xhdpi.columnWidth, xhdpi.gap) <= 1760)
        assertEquals(LibraryGridLayout.posterFrameHeight(xhdpi.columnWidth, 6), xhdpi.posterHeight)
        assertEquals(mdpi.contentWidth + mdpi.leftGutter + mdpi.rightGutter, 1840)
        assertEquals(xhdpi.contentWidth + xhdpi.leftGutter + xhdpi.rightGutter, 1760)
    }

    @Test
    fun outerGuttersStayEqualIncludingPartialFinalRow() {
        val metrics = LibraryGridLayout.metrics(1760, 240, 44, 6)
        val bounds = LibraryGridLayout.contentBounds(1760, metrics.columns, metrics.columnWidth, metrics.gap)
        assertEquals(metrics.leftGutter, bounds.leftGutter)
        assertEquals(metrics.rightGutter, bounds.rightGutter)
        assertTrue(kotlin.math.abs(bounds.leftGutter - bounds.rightGutter) <= 1)
        assertEquals(bounds.leftGutter + bounds.contentWidth + bounds.rightGutter, 1760)

        val fullRow = (0 until metrics.columns).map { index ->
            LibraryGridLayout.columnLeft(index, metrics.columnWidth, metrics.gap, bounds, rtl = true)
        }
        val partialRow = (0 until 4).map { index ->
            LibraryGridLayout.columnLeft(index, metrics.columnWidth, metrics.gap, bounds, rtl = true)
        }
        assertEquals(fullRow.take(4), partialRow)
        assertEquals(bounds.right - metrics.columnWidth, fullRow.first())
        assertEquals(bounds.left, fullRow.last())
        assertEquals(bounds.rightGutter, 1760 - (partialRow.first() + metrics.columnWidth))
        assertEquals(fullRow[3], partialRow.last())
        assertEquals(bounds.left, LibraryGridLayout.columnLeft(metrics.columns - 1, metrics.columnWidth, metrics.gap, bounds, rtl = true))
        assertEquals(bounds.left, LibraryGridLayout.columnLeft(0, metrics.columnWidth, metrics.gap, bounds, rtl = false))
    }
}

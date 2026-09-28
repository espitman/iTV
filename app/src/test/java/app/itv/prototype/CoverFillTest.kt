package app.itv.prototype

import app.itv.prototype.ui.CoverFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFillTest {
    @Test
    fun downscaleKeepsAspectAndCapsTheLongSide() {
        assertEquals(48 to 27, CoverFill.downscaleSize(1920, 1080, 48))
        assertEquals(32 to 48, CoverFill.downscaleSize(273, 410, 48))
        assertEquals(24 to 24, CoverFill.downscaleSize(24, 24, 48))
        assertEquals(1 to 1, CoverFill.downscaleSize(0, 0, 48))
    }

    @Test
    fun uniformColorSurvivesBoxBlur() {
        val color = 0xFFCC3344.toInt()
        val pixels = IntArray(64) { color }
        CoverFill.blurArgb(pixels, 8, 8, 3)
        pixels.forEach { pixel ->
            assertEquals(0xFF, pixel ushr 24)
            assertEquals(0xCC, (pixel shr 16) and 0xFF)
            assertEquals(0x33, (pixel shr 8) and 0xFF)
            assertEquals(0x44, pixel and 0xFF)
        }
    }

    @Test
    fun blurMixesNeighboringPixels() {
        val pixels = IntArray(16) { if (it % 4 < 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        CoverFill.blurArgb(pixels, 4, 4, 1)
        val mixed = pixels[1]
        assertTrue((mixed shr 16) and 0xFF in 1..254)
        assertTrue(mixed and 0xFF in 1..254)
    }
}

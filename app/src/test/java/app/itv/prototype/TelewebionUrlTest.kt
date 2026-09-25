package app.itv.prototype

import app.itv.prototype.core.SourceKind
import app.itv.prototype.core.TelewebionUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelewebionUrlTest {
    @Test
    fun acceptsProgramAndProduct() {
        val program = TelewebionUrl.parse("https://telewebion.net/program/0x296b3ea").getOrThrow()
        val product = TelewebionUrl.parse("https://telewebion.net/product/0xc83b6d3").getOrThrow()
        assertEquals(SourceKind.PROGRAM, program.kind)
        assertEquals("0x296b3ea", program.sourceId)
        assertEquals(SourceKind.PRODUCT, product.kind)
        assertEquals("0xc83b6d3", product.sourceId)
    }

    @Test
    fun stripsArchiveQueryAndPersianDigits() {
        val parsed = TelewebionUrl.parse(
            "https://telewebion.net/program/۰x1b29939/archive?foo=1",
        ).getOrThrow()
        assertEquals("0x1b29939", parsed.sourceId)
        assertEquals(SourceKind.PROGRAM, parsed.kind)
    }

    @Test
    fun rejectsOtherHostsAndSchemes() {
        assertTrue(TelewebionUrl.parse("http://telewebion.net/program/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://evil.test/program/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("file:///program/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://192.168.1.8/program/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://telewebion.net/live/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://telewebion.net/program/123").isFailure)
    }

    @Test
    fun acceptsTrailingSlashAndDropsFragment() {
        val program = TelewebionUrl.parse("https://telewebion.net/program/0x296b3ea/").getOrThrow()
        val product = TelewebionUrl.parse("https://telewebion.net/product/0xc83b6d3/#clip").getOrThrow()
        assertEquals("0x296b3ea", program.sourceId)
        assertEquals("0xc83b6d3", product.sourceId)
    }

    @Test
    fun rejectsExtraSegmentsArchiveInMiddlePortAndUserInfo() {
        assertTrue(TelewebionUrl.parse("https://telewebion.net/program/0x1/extra").isFailure)
        assertTrue(TelewebionUrl.parse("https://telewebion.net/program/0x1/archive/extra").isFailure)
        assertTrue(TelewebionUrl.parse("https://telewebion.net/program/archive/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://telewebion.net/archive/program/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://user@telewebion.net/program/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://telewebion.net:8080/program/0x1").isFailure)
        assertTrue(TelewebionUrl.parse("https://telewebion.net:80/program/0x1").isFailure)
    }
}

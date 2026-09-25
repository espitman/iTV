package app.itv.prototype

import app.itv.prototype.admin.AsyncStartGate
import app.itv.prototype.data.SerialProgressWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerialProgressWriterTest {
    @Test
    fun latestWriteForAnEpisodeWinsAndFlushWaits() = runTest {
        val written = mutableListOf<Triple<Long, Long, Long>>()
        val writer = SerialProgressWriter(
            persist = { id, position, duration ->
                delay(10)
                written += Triple(id, position, duration)
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        writer.submit(1, 100, 1_000)
        writer.submit(1, 250, 1_000)
        writer.submit(2, 40, 900)
        writer.flush()
        assertEquals(listOf(Triple(1L, 250L, 1_000L), Triple(2L, 40L, 900L)), written)
        writer.close()
    }

    @Test
    fun olderEpisodeWriteDoesNotOverwriteNewerProgress() = runTest {
        val written = mutableListOf<Pair<Long, Long>>()
        val writer = SerialProgressWriter(
            persist = { id, position, _ ->
                delay(5)
                written += id to position
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        writer.submit(7, 10, 500)
        writer.submit(7, 80, 500)
        writer.submit(7, 120, 500)
        writer.flush()
        assertEquals(listOf(7L to 120L), written)
        writer.close()
    }

    @Test
    fun startGateStopInvalidatesInFlightStart() {
        val gate = AsyncStartGate()
        val first = gate.beginStart()
        assertNotNull(first)
        assertTrue(gate.isCurrent(first!!))
        assertTrue(gate.beginStart() == null)
        gate.stop()
        assertFalse(gate.isCurrent(first))
        val second = gate.beginStart()
        assertNotNull(second)
        assertNotEquals(first, second)
        assertTrue(gate.isCurrent(second!!))
        gate.markFailed(second)
        assertFalse(gate.isCurrent(second))
        assertNotNull(gate.beginStart())
    }
}

package app.itv.prototype.admin

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lets an async start be invalidated by a later stop so the work cannot
 * publish a server after the process has already gone to background.
 */
class AsyncStartGate {
    private val generation = AtomicInteger(0)
    private val started = AtomicBoolean(false)

    fun beginStart(): Int? {
        if (!started.compareAndSet(false, true)) return null
        return generation.incrementAndGet()
    }

    fun isCurrent(token: Int): Boolean = started.get() && generation.get() == token

    fun markFailed(token: Int) {
        if (generation.get() == token) started.set(false)
    }

    fun stop(): Int {
        started.set(false)
        return generation.incrementAndGet()
    }
}

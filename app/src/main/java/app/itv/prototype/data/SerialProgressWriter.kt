package app.itv.prototype.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Serializes progress writes on a dedicated scope so Activity cancellation
 * cannot drop the last save. Newer writes for an episode replace older ones.
 */
class SerialProgressWriter(
    private val persist: suspend (episodeId: Long, positionMs: Long, durationMs: Long) -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mailbox = Channel<Op>(Channel.UNLIMITED)
    private val newest = ConcurrentHashMap<Long, Snapshot>()
    private val seq = AtomicLong(0L)

    init {
        scope.launch {
            for (op in mailbox) {
                when (op) {
                    is Op.Write -> {
                        val latest = newest[op.episodeId]
                        if (latest != null && latest.seq == op.seq) {
                            persist(latest.episodeId, latest.positionMs, latest.durationMs)
                        }
                    }
                    is Op.Flush -> op.done.complete(Unit)
                }
            }
        }
    }

    fun submit(episodeId: Long, positionMs: Long, durationMs: Long) {
        val snapshot = Snapshot(episodeId, positionMs, durationMs, seq.incrementAndGet())
        newest[episodeId] = snapshot
        mailbox.trySend(Op.Write(episodeId, snapshot.seq))
    }

    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        mailbox.send(Op.Flush(done))
        done.await()
    }

    fun flushBlocking(timeoutMs: Long = 3_000L) {
        runBlocking {
            withTimeoutOrNull(timeoutMs) { flush() }
        }
    }

    fun close() {
        mailbox.close()
        scope.cancel()
    }

    private data class Snapshot(
        val episodeId: Long,
        val positionMs: Long,
        val durationMs: Long,
        val seq: Long,
    )

    private sealed class Op {
        data class Write(val episodeId: Long, val seq: Long) : Op()
        data class Flush(val done: CompletableDeferred<Unit>) : Op()
    }
}

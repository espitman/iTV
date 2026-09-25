package app.itv.prototype

import app.itv.prototype.admin.PairingStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingStoreTest {
    @Test
    fun tokensRemainValidAcrossPinRotationTimeAndStoreRecreation() {
        var now = 0L
        var saved = emptySet<String>()
        fun store() = PairingStore(
            now = { now },
            readTokens = { saved },
            writeTokens = { saved = it },
        )

        val first = store()
        val token = first.pair(first.pin)
        first.rotate()
        now += 365L * 24 * 60 * 60 * 1000
        assertTrue(first.accepts(token))
        assertTrue(store().accepts(token))
    }

    @Test
    fun logoutRevokesOnlyTheCurrentSession() {
        var saved = emptySet<String>()
        val store = PairingStore(readTokens = { saved }, writeTokens = { saved = it })
        val first = store.pair(store.pin)
        val second = store.pair(store.pin)

        store.revoke(first)

        assertFalse(store.accepts(first))
        assertTrue(store.accepts(second))
        assertFalse(PairingStore(readTokens = { saved }).accepts(first))
        assertTrue(PairingStore(readTokens = { saved }).accepts(second))
    }
}

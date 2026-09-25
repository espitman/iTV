package app.itv.prototype.admin

import java.security.SecureRandom
import java.util.UUID

class PairingStore(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val readTokens: () -> Set<String> = { emptySet() },
    private val writeTokens: (Set<String>) -> Unit = {},
) {
    private val random = SecureRandom()
    var pin: String = newPin()
        private set
    private val tokens = readTokens().toMutableSet()
    private val pairAttempts = ArrayDeque<Long>()

    @Synchronized fun rotate() {
        pin = newPin()
    }

    @Synchronized fun pair(candidate: String): String {
        val nowMs = now()
        while (pairAttempts.isNotEmpty() && nowMs - pairAttempts.first() > 60_000L) pairAttempts.removeFirst()
        if (pairAttempts.size >= 8) error("تعداد تلاش برای اتصال بیش از حد است")
        pairAttempts.addLast(nowMs)
        if (candidate.trim() != pin) error("کد اتصال نادرست است")
        val token = UUID.randomUUID().toString().replace("-", "")
        writeTokens(tokens + token)
        tokens.add(token)
        return token
    }

    @Synchronized fun accepts(candidate: String?): Boolean {
        val value = candidate?.trim().orEmpty()
        return value.isNotEmpty() && value in tokens
    }

    @Synchronized fun revoke(candidate: String?) {
        val value = candidate?.trim().orEmpty()
        if (value in tokens) {
            writeTokens(tokens - value)
            tokens.remove(value)
        }
    }

    private fun newPin(): String = "%04d".format(random.nextInt(10_000))

}

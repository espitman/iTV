package app.itv.prototype.core

object ProductCoverRefresh {
    fun isPending(kind: SourceKind, sourceId: String, processed: Set<String>): Boolean =
        kind == SourceKind.PRODUCT && sourceId.isNotBlank() && sourceId !in processed

    fun posterAfterRefresh(stored: String?, incoming: String?): String? {
        val next = incoming?.trim()?.takeIf { it.isNotEmpty() } ?: return stored
        return next
    }
}

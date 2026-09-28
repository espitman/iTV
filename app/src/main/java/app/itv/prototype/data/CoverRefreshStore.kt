package app.itv.prototype.data

import android.content.Context

class CoverRefreshStore(
    private val read: () -> Set<String>,
    private val write: (Set<String>) -> Unit,
) {
    @Synchronized
    fun processed(): Set<String> = HashSet(read())

    @Synchronized
    fun remember(sourceId: String) {
        val id = sourceId.trim()
        if (id.isEmpty()) return
        write(processed() + id)
    }

    companion object {
        private const val PREFS = "itv_cover_refresh"
        private const val KEY = "product_v2"

        fun from(context: Context): CoverRefreshStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return CoverRefreshStore(
                read = { prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet() },
                write = { ids -> prefs.edit().putStringSet(KEY, ids).apply() },
            )
        }
    }
}

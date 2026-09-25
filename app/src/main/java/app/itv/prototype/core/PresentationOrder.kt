package app.itv.prototype.core

object PresentationOrder {
    fun sorted(episodes: List<LibraryEpisode>): List<LibraryEpisode> =
        episodes.sortedWith(
            compareBy<LibraryEpisode> { it.seasonNumber }
                .thenBy { displayNumberKey(it.displayNumber) }
                .thenBy { it.sourceOrder },
        )

    fun present(episodes: List<LibraryEpisode>): List<LibraryEpisode> =
        sorted(episodes.filter { it.sourcePresent })

    fun nextAfter(current: LibraryEpisode, episodes: List<LibraryEpisode>): LibraryEpisode? {
        val ordered = present(episodes)
        val index = ordered.indexOfFirst { it.sourceEpisodeId == current.sourceEpisodeId }
        if (index < 0) return null
        return ordered.getOrNull(index + 1)
    }

    fun displayNumberKey(displayNumber: String?): Int {
        val token = displayNumber?.let(::firstNumberToken) ?: return Int.MAX_VALUE
        return token.toIntOrNull() ?: Int.MAX_VALUE
    }
}

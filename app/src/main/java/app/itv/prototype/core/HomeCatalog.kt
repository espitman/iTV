package app.itv.prototype.core

object HomeCatalog {
    const val HOME_LIMIT = 8

    fun continueWatching(items: List<LibrarySeries>): List<LibrarySeries> =
        items.asSequence()
            .filter { !it.isMovie && it.presentEpisodes.any { episode -> episode.lastPositionMs > 0L || episode.isWatched } }
            .filterNot { PlaybackRules.seriesWatched(it.episodes) }
            .sortedWith(compareByDescending<LibrarySeries> { it.addedAt }.thenByDescending { it.id })
            .toList()

    fun all(items: List<LibrarySeries>, movies: Boolean): List<LibrarySeries> =
        items.asSequence()
            .filter { it.isMovie == movies }
            .sortedWith(newestFirst)
            .toList()

    fun recent(items: List<LibrarySeries>, movies: Boolean, limit: Int = HOME_LIMIT): List<LibrarySeries> =
        all(items, movies).take(limit.coerceAtLeast(0))

    fun featured(items: List<LibrarySeries>): LibrarySeries? =
        continueWatching(items).firstOrNull()
            ?: recent(items, false).firstOrNull()
            ?: recent(items, true).firstOrNull()
            ?: items.firstOrNull()

    fun search(items: List<LibrarySeries>, query: String): List<LibrarySeries> {
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) {
            items
        } else {
            items.filter { series ->
                series.title.contains(needle, ignoreCase = true) ||
                    series.sourceTitle.contains(needle, ignoreCase = true) ||
                    series.localTitle.orEmpty().contains(needle, ignoreCase = true)
            }
        }
        return filtered.sortedWith(newestFirst)
    }

    private val newestFirst = compareByDescending<LibrarySeries> { it.addedAt }.thenByDescending { it.id }
}

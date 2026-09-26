package app.itv.prototype.core

object HomeCatalog {
    const val HOME_LIMIT = 10

    fun continueWatching(items: List<LibrarySeries>): List<LibrarySeries> =
        items.asSequence()
            .filter { !it.isMovie && it.presentEpisodes.any { episode -> episode.lastPositionMs > 0L || episode.isWatched } }
            .filterNot { PlaybackRules.seriesWatched(it.episodes) }
            .sortedWith(compareByDescending<LibrarySeries> { it.addedAt }.thenByDescending { it.id })
            .toList()

    fun all(items: List<LibrarySeries>, movies: Boolean): List<LibrarySeries> =
        items.asSequence()
            .filter { it.isMovie == movies }
            .sortedWith(compareByDescending<LibrarySeries> { it.addedAt }.thenByDescending { it.id })
            .toList()

    fun recent(items: List<LibrarySeries>, movies: Boolean): List<LibrarySeries> =
        all(items, movies).take(HOME_LIMIT)
}

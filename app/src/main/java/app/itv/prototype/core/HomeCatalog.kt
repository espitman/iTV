package app.itv.prototype.core

object HomeCatalog {
    const val HOME_LIMIT = 10

    fun all(items: List<LibrarySeries>, movies: Boolean): List<LibrarySeries> =
        items.asSequence()
            .filter { it.isMovie == movies }
            .sortedWith(compareByDescending<LibrarySeries> { it.addedAt }.thenByDescending { it.id })
            .toList()

    fun recent(items: List<LibrarySeries>, movies: Boolean): List<LibrarySeries> =
        all(items, movies).take(HOME_LIMIT)
}

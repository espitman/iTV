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
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return items.sortedWith(newestFirst)
        val needle = normalizeSearchKey(trimmed)
        if (needle.isEmpty()) return emptyList()
        return items.filter { series ->
            searchTitles(series).any { normalizeSearchKey(it).startsWith(needle) }
        }.sortedWith(newestFirst)
    }

    private fun searchTitles(series: LibrarySeries): Sequence<String> = sequenceOf(
        series.title,
        series.sourceTitle,
        series.localTitle.orEmpty(),
    ).filter { it.isNotBlank() }

    private fun normalizeSearchKey(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                'ي', 'ى' -> append('ی')
                'ك' -> append('ک')
                'أ', 'إ', 'ٱ' -> append('ا')
                'ة' -> append('ه')
                'ؤ' -> append('و')
                '\u200C', '\u200D', '\u0640', '\uFEFF' -> Unit
                else -> if (!char.isWhitespace()) append(char.lowercaseChar())
            }
        }
    }

    private val newestFirst = compareByDescending<LibrarySeries> { it.addedAt }.thenByDescending { it.id }
}

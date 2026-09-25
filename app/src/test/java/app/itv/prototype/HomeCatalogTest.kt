package app.itv.prototype

import app.itv.prototype.core.HomeCatalog
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCatalogTest {
    @Test
    fun homeShowsTenNewestPerTypeWhileGridKeepsAll() {
        val items = (1L..15L).flatMap { number ->
            listOf(series(number, false), series(number + 100, true, number))
        }.reversed()

        assertEquals((15L downTo 6L).toList(), HomeCatalog.recent(items, false).map { it.id })
        assertEquals((115L downTo 106L).toList(), HomeCatalog.recent(items, true).map { it.id })
        assertEquals(15, HomeCatalog.all(items, false).size)
        assertEquals(15, HomeCatalog.all(items, true).size)
    }

    private fun series(id: Long, movie: Boolean, addedAt: Long = id) = LibrarySeries(
        id = id,
        kind = SourceKind.PRODUCT,
        sourceId = id.toString(),
        title = id.toString(),
        sourceTitle = id.toString(),
        localTitle = null,
        posterUrl = null,
        description = null,
        addedAt = addedAt,
        refreshedAt = 0L,
        importState = ImportState.DONE,
        importError = null,
        seasons = emptyList(),
        episodes = emptyList(),
        isMovie = movie,
    )
}

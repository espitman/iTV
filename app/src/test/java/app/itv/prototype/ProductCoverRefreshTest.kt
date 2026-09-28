package app.itv.prototype

import app.itv.prototype.core.ImportState
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.ProductCoverRefresh
import app.itv.prototype.core.SourceKind
import app.itv.prototype.data.CatalogMapper
import app.itv.prototype.data.CoverRefreshStore
import app.itv.prototype.data.ProductCoverMigrator
import app.itv.prototype.data.TelewebionClient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCoverRefreshTest {
    @Test
    fun productMetaPrefersVerticalPosterOverPersistedHorizontal() {
        val content = JSONObject()
            .put("persian_title", "بسوی افتخار")
            .put(
                "media",
                JSONObject()
                    .put("vertical_poster", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                    .put("horizontal_big_poster", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            )
        val incoming = CatalogMapper.productMeta(content).second
        val stored = "https://static.telewebion.net/vodBannerImages/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/default"
        assertEquals(
            "https://static.telewebion.net/vodBannerImages/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/default",
            incoming,
        )
        assertEquals(incoming, ProductCoverRefresh.posterAfterRefresh(stored, incoming))
        assertEquals(stored, ProductCoverRefresh.posterAfterRefresh(stored, null))
        assertEquals(stored, ProductCoverRefresh.posterAfterRefresh(stored, "  "))
    }

    @Test
    fun onlyUnprocessedProductSeriesArePending() {
        val done = setOf("0xc594990")
        assertTrue(ProductCoverRefresh.isPending(SourceKind.PRODUCT, "0xc20b845", done))
        assertFalse(ProductCoverRefresh.isPending(SourceKind.PRODUCT, "0xc594990", done))
        assertFalse(ProductCoverRefresh.isPending(SourceKind.PROGRAM, "0x1b29939", emptySet()))
        assertFalse(ProductCoverRefresh.isPending(SourceKind.PRODUCT, "  ", emptySet()))
    }

    @Test
    fun migratorReplacesHorizontalProductPosterOnceAndKeepsLocalTitleProgress() = runBlocking {
        val stored = series(
            id = 21,
            sourceId = "0xc594990",
            kind = SourceKind.PRODUCT,
            posterUrl = "https://static.telewebion.net/vodBannerImages/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb/default",
            localTitle = "عنوان محلی",
            positionMs = 1_045_000L,
        )
        val program = series(
            id = 7,
            sourceId = "0x1b29939",
            kind = SourceKind.PROGRAM,
            posterUrl = "https://static.telewebion.net/programImages/old/default",
        )
        val rows = mutableListOf(stored, program)
        val processed = mutableSetOf<String>()
        var previewCalls = 0
        val vertical = "https://static.telewebion.net/vodBannerImages/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/default"
        val backdrop = "https://static.telewebion.net/vodBannerImages/cccccccccccccccc-cccc-cccc-cccc-cccccccccccc/default"
        val client = TelewebionClient { url ->
            previewCalls += 1
            assertTrue(url.contains("0xc594990"))
            JSONObject().put(
                "body",
                JSONObject().put(
                    "content",
                    JSONArray().put(
                        JSONObject()
                            .put("content_id", "0xc594990")
                            .put("content_type", "SERIAL")
                            .put("persian_title", "بسوی افتخار")
                            .put(
                                "media",
                                JSONObject()
                                    .put("vertical_poster", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                                    .put("horizontal_big_poster", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                                    .put("main_big_poster", "cccccccccccccccc-cccc-cccc-cccc-cccccccccccc"),
                            ),
                    ),
                ),
            )
        }
        val store = CoverRefreshStore(read = { processed.toSet() }, write = { processed.clear(); processed.addAll(it) })
        val migrator = ProductCoverMigrator(
            load = { rows.toList() },
            preview = { source -> client.preview(source) },
            updateCover = { id, posterUrl, backdropUrl, isMovie, durationMinutes ->
                val index = rows.indexOfFirst { it.id == id }
                val current = rows[index]
                rows[index] = current.copy(
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl,
                    isMovie = isMovie,
                    durationMinutes = durationMinutes,
                )
            },
            processed = { store.processed() },
            remember = { store.remember(it) },
        )
        assertEquals(1, migrator.run())
        assertEquals(vertical, rows[0].posterUrl)
        assertEquals(backdrop, rows[0].backdropUrl)
        assertEquals("عنوان محلی", rows[0].localTitle)
        assertEquals("بسوی افتخار", rows[0].sourceTitle)
        assertEquals(1_045_000L, rows[0].episodes.single().lastPositionMs)
        assertEquals("https://static.telewebion.net/programImages/old/default", rows[1].posterUrl)
        assertEquals(setOf("0xc594990"), processed)
        assertEquals(1, previewCalls)

        assertEquals(0, migrator.run())
        assertEquals(1, previewCalls)
        assertEquals(vertical, rows[0].posterUrl)
        assertEquals(1_045_000L, rows[0].episodes.single().lastPositionMs)
    }

    @Test
    fun migratorLeavesCoverUnchangedWhenPreviewFailsSoItCanRetry() = runBlocking {
        val stored = series(
            id = 3,
            sourceId = "0xc20b845",
            kind = SourceKind.PRODUCT,
            posterUrl = "https://static.telewebion.net/vodBannerImages/horizontal/default",
        )
        val rows = mutableListOf(stored)
        val processed = mutableSetOf<String>()
        val migrator = ProductCoverMigrator(
            load = { rows.toList() },
            preview = { error("offline") },
            updateCover = { _, _, _, _, _ -> error("must not write") },
            processed = { processed },
            remember = { processed += it },
        )
        assertEquals(0, migrator.run())
        assertEquals("https://static.telewebion.net/vodBannerImages/horizontal/default", rows[0].posterUrl)
        assertTrue(processed.isEmpty())
    }

    private fun series(
        id: Long,
        sourceId: String,
        kind: SourceKind,
        posterUrl: String?,
        localTitle: String? = null,
        positionMs: Long = 0L,
    ) = LibrarySeries(
        id = id,
        kind = kind,
        sourceId = sourceId,
        title = localTitle ?: "بسوی افتخار",
        sourceTitle = "بسوی افتخار",
        localTitle = localTitle,
        posterUrl = posterUrl,
        description = "keep-me",
        addedAt = 99L,
        refreshedAt = 1L,
        importState = ImportState.DONE,
        importError = null,
        seasons = emptyList(),
        episodes = listOf(
            LibraryEpisode(
                id = id * 10,
                seriesId = id,
                seasonId = 1,
                sourceEpisodeId = "0xep",
                sourceOrder = 0,
                seasonNumber = 1,
                displayNumber = "1",
                title = "E1",
                imageUrl = null,
                firstTitrajeStartSec = 0,
                firstTitrajeEndSec = 0,
                lastTitrajeStartSec = 0,
                sourcePresent = true,
                lastPositionMs = positionMs,
                isWatched = false,
                durationMs = 2_512_000L,
            ),
        ),
        backdropUrl = null,
    )
}

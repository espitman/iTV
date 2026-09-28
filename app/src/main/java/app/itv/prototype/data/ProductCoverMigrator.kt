package app.itv.prototype.data

import app.itv.prototype.core.AllowedSource
import app.itv.prototype.core.CatalogPreview
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.ProductCoverRefresh
import kotlinx.coroutines.CancellationException

class ProductCoverMigrator(
    private val load: suspend () -> List<LibrarySeries>,
    private val preview: (AllowedSource) -> CatalogPreview,
    private val updateCover: suspend (
        id: Long,
        posterUrl: String?,
        backdropUrl: String?,
        isMovie: Boolean,
        durationMinutes: Int,
    ) -> Unit,
    private val processed: () -> Set<String>,
    private val remember: (String) -> Unit,
) {
    constructor(
        repository: LibraryRepository,
        client: TelewebionClient,
        store: CoverRefreshStore,
        now: () -> Long = { System.currentTimeMillis() },
    ) : this(
        load = { repository.library() },
        preview = { source -> client.preview(source) },
        updateCover = { id, posterUrl, backdropUrl, isMovie, durationMinutes ->
            repository.updateSeriesMeta(
                id = id,
                title = null,
                posterUrl = posterUrl,
                description = null,
                refreshedAt = now(),
                isMovie = isMovie,
                durationMinutes = durationMinutes,
                backdropUrl = backdropUrl,
            )
        },
        processed = { store.processed() },
        remember = { store.remember(it) },
    )

    suspend fun run(): Int {
        val done = processed().toMutableSet()
        var updated = 0
        load().forEach { series ->
            if (!ProductCoverRefresh.isPending(series.kind, series.sourceId, done)) return@forEach
            val page = try {
                preview(AllowedSource(series.kind, series.sourceId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@forEach
            }
            val poster = ProductCoverRefresh.posterAfterRefresh(series.posterUrl, page.posterUrl)
            val backdrop = page.backdropUrl ?: series.backdropUrl
            if (poster != series.posterUrl || backdrop != series.backdropUrl) {
                updateCover(series.id, poster, backdrop, page.isMovie, page.durationMinutes)
                updated += 1
            }
            remember(series.sourceId)
            done.add(series.sourceId)
        }
        return updated
    }
}

package app.itv.prototype.data

import app.itv.prototype.core.AllowedSource
import app.itv.prototype.core.CatalogPaging
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.IncomingEpisode
import app.itv.prototype.core.IncomingSeason
import app.itv.prototype.core.SourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

class ImportCoordinator(
    private val repository: LibraryRepository,
    private val client: TelewebionClient = TelewebionClient(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val mutex = Mutex()
    private val starts = ArrayDeque<Long>()
    private var runningJob: Job? = null
    @Volatile var foreground: Boolean = false

    suspend fun preview(source: AllowedSource) = withContext(Dispatchers.IO) {
        client.preview(source)
    }

    suspend fun add(source: AllowedSource): Long {
        if (!allowStart()) error("تعداد درخواست‌های ورود بیش از حد است")
        val existing = repository.findBySource(source.kind, source.sourceId)
        if (existing != null) {
            repository.setImportState(existing.id, ImportState.QUEUED)
            resumePending()
            return existing.id
        }
        val preview = client.preview(source)
        val id = repository.insertSeries(
            kind = source.kind,
            sourceId = source.sourceId,
            title = preview.title,
            posterUrl = preview.posterUrl,
            description = preview.description,
            now = now(),
            isMovie = preview.isMovie,
            durationMinutes = preview.durationMinutes,
            backdropUrl = preview.backdropUrl,
        )
        resumePending()
        return id
    }

    suspend fun refresh(id: Long) {
        if (!allowStart()) error("تعداد درخواست‌های نوسازی بیش از حد است")
        repository.setImportState(id, ImportState.QUEUED)
        resumePending()
    }

    fun onForeground() {
        foreground = true
        scope.launch {
            repository.markStaleRunningAsQueued()
            resumePending()
        }
    }

    fun onBackground() {
        foreground = false
        runningJob?.cancel()
        runningJob = null
        scope.launch {
            repository.pendingImports().forEach { series ->
                if (series.importState == ImportState.RUNNING.name) {
                    repository.setImportState(series.id, ImportState.QUEUED)
                }
            }
        }
    }

    fun shutdown() {
        foreground = false
        runningJob?.cancel()
        job.cancel()
    }

    private fun resumePending() {
        if (!foreground) return
        if (runningJob?.isActive == true) return
        runningJob = scope.launch {
            mutex.withLock {
                while (foreground) {
                    val next = repository.pendingImports().firstOrNull() ?: break
                    runCatching { importSeries(next.id) }
                        .onFailure { error ->
                            repository.setImportState(
                                next.id,
                                ImportState.ERROR,
                                error.message?.ifBlank { "ورود ناتمام ماند" } ?: "ورود ناتمام ماند",
                            )
                        }
                }
            }
        }
    }

    private suspend fun importSeries(id: Long) {
        val series = repository.series(id) ?: return
        repository.setImportState(id, ImportState.RUNNING)
        val seenEpisodes = linkedSetOf<String>()
        val seenSeasons = linkedSetOf<Int>()
        try {
            when (series.kind) {
                SourceKind.PROGRAM -> importProgram(id, series.sourceId, seenEpisodes, seenSeasons)
                SourceKind.PRODUCT -> importProduct(id, series.sourceId, seenEpisodes, seenSeasons)
            }
            if (seenEpisodes.isEmpty()) error("پاسخ منبع خالی است")
            repository.finishRefresh(id, seenEpisodes.toList())
            repository.setImportState(id, ImportState.DONE)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            repository.setImportState(id, ImportState.QUEUED)
            throw cancelled
        }
    }

    private suspend fun importProgram(
        seriesId: Long,
        sourceId: String,
        seenEpisodes: MutableSet<String>,
        seenSeasons: MutableSet<Int>,
    ) {
        var offset = 0
        var first = true
        while (foreground) {
            val page = client.loadProgramPage(sourceId, offset)
            if (page.finished) {
                if (first) error("پاسخ منبع خالی است")
                break
            }
            if (first) {
                repository.updateSeriesMeta(seriesId, page.title, page.posterUrl, page.description, now())
                first = false
            }
            commit(seriesId, page.seasons, page.episodes, seenEpisodes, seenSeasons)
            offset = CatalogPaging.nextOffset(offset, page.rowCount) ?: break
        }
        if (!foreground) repository.setImportState(seriesId, ImportState.QUEUED)
    }

    private suspend fun importProduct(
        seriesId: Long,
        sourceId: String,
        seenEpisodes: MutableSet<String>,
        seenSeasons: MutableSet<Int>,
    ) {
        val (preview, seasons) = client.loadProductSeasons(sourceId)
        repository.updateSeriesMeta(seriesId, preview.title, preview.posterUrl, preview.description, now(), preview.isMovie, preview.durationMinutes, preview.backdropUrl)
        if (preview.isMovie) {
            val movie = client.loadMovie(sourceId)
            commit(seriesId, listOf(IncomingSeason(1, 0, null)), listOf(movie), seenEpisodes, seenSeasons)
            return
        }
        if (seasons.isEmpty()) error("فصلی در منبع پیدا نشد")
        var globalOrder = 0
        seasons.forEach { season ->
            var offset = 0
            var seasonHasRows = false
            while (foreground) {
                val page = client.loadProductSeasonPage(sourceId, season.seasonNumber, offset)
                if (page.finished) break
                seasonHasRows = true
                val ordered = page.episodes.mapIndexed { index, item ->
                    item.copy(sourceOrder = globalOrder + index)
                }
                globalOrder += ordered.size
                commit(seriesId, listOf(season), ordered, seenEpisodes, seenSeasons)
                offset = CatalogPaging.nextOffset(offset, page.rowCount) ?: break
            }
            if (seasonHasRows) seenSeasons += season.seasonNumber
        }
        if (!foreground) repository.setImportState(seriesId, ImportState.QUEUED)
    }

    private suspend fun commit(
        seriesId: Long,
        seasons: List<IncomingSeason>,
        episodes: List<IncomingEpisode>,
        seenEpisodes: MutableSet<String>,
        seenSeasons: MutableSet<Int>,
    ) {
        seenSeasons += seasons.map { it.seasonNumber }
        seenEpisodes += episodes.map { it.sourceEpisodeId }
        repository.commitPage(seriesId, seasons, episodes)
    }

    private fun allowStart(): Boolean {
        val nowMs = now()
        while (starts.isNotEmpty() && nowMs - starts.first() > 60_000L) starts.removeFirst()
        if (starts.size >= 8) return false
        starts.addLast(nowMs)
        return true
    }
}

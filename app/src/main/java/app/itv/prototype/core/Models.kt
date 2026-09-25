package app.itv.prototype.core

enum class SourceKind(val path: String, val label: String) {
    PROGRAM("program", "برنامه"),
    PRODUCT("product", "محصول");

    companion object {
        fun fromStorage(value: String): SourceKind =
            entries.firstOrNull { it.name == value || it.path == value } ?: PROGRAM
    }
}

enum class ImportState {
    QUEUED, RUNNING, ERROR, DONE;

    companion object {
        fun fromStorage(value: String): ImportState =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DONE
    }
}

data class IncomingSeason(
    val seasonNumber: Int,
    val sourceOrder: Int,
    val title: String?,
)

data class IncomingEpisode(
    val sourceEpisodeId: String,
    val sourceOrder: Int,
    val seasonNumber: Int,
    val displayNumber: String?,
    val title: String,
    val imageUrl: String?,
    val firstTitrajeStartSec: Int = 0,
    val firstTitrajeEndSec: Int = 0,
    val lastTitrajeStartSec: Int = 0,
)

data class CatalogPreview(
    val kind: SourceKind,
    val sourceId: String,
    val title: String,
    val posterUrl: String?,
    val description: String?,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val isMovie: Boolean = false,
    val durationMinutes: Int = 0,
    val backdropUrl: String? = null,
)

data class CatalogPage(
    val title: String,
    val posterUrl: String?,
    val description: String?,
    val seasons: List<IncomingSeason>,
    val episodes: List<IncomingEpisode>,
    val finished: Boolean,
    val rowCount: Int = episodes.size,
)

data class CatalogRows(
    val episodes: List<IncomingEpisode>,
    val rowCount: Int,
) {
    val finished: Boolean get() = rowCount == 0
}

data class EpisodeProgress(
    val lastPositionMs: Long,
    val isWatched: Boolean,
    val durationMs: Long,
)

data class MergeSnapshot(
    val sourceEpisodeId: String,
    val progress: EpisodeProgress,
    val sourcePresent: Boolean,
)

data class ResolvedStream(
    val url: String,
    val firstTitrajeStartSec: Int = 0,
    val firstTitrajeEndSec: Int = 0,
    val lastTitrajeStartSec: Int = 0,
)

package app.itv.prototype.core

object ImportMerge {
    fun apply(
        existing: List<MergeSnapshot>,
        incoming: List<IncomingEpisode>,
    ): List<MergedEpisode> {
        val previous = existing.associateBy { it.sourceEpisodeId }
        val seen = incoming.map { it.sourceEpisodeId }.toSet()
        val upserts = incoming.map { item ->
            val kept = previous[item.sourceEpisodeId]
            MergedEpisode(
                incoming = item,
                progress = kept?.progress ?: EpisodeProgress(0L, false, 0L),
                sourcePresent = true,
            )
        }
        val hidden = existing
            .filter { it.sourceEpisodeId !in seen }
            .map { leftover ->
                MergedEpisode(
                    incoming = IncomingEpisode(
                        sourceEpisodeId = leftover.sourceEpisodeId,
                        sourceOrder = Int.MAX_VALUE,
                        seasonNumber = 0,
                        displayNumber = null,
                        title = "",
                        imageUrl = null,
                    ),
                    progress = leftover.progress,
                    sourcePresent = false,
                )
            }
        return upserts + hidden
    }

    fun retainedSeasonNumbers(existing: Collection<Int>, incoming: Collection<Int>): Set<Int> =
        existing.toSet() + incoming.toSet()
}

data class MergedEpisode(
    val incoming: IncomingEpisode,
    val progress: EpisodeProgress,
    val sourcePresent: Boolean,
)

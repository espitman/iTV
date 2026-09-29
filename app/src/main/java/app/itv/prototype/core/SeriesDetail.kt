package app.itv.prototype.core

object SeriesDetail {
    enum class PlayAction { PLAY_FIRST, CONTINUE_EPISODE, PLAY_EPISODE, PLAY_MOVIE }

    fun playAction(isMovie: Boolean, episode: LibraryEpisode?): PlayAction {
        if (episode == null) return PlayAction.PLAY_FIRST
        if (isMovie) {
            return if (episode.lastPositionMs > PlaybackRules.RESUME_THRESHOLD_MS) {
                PlayAction.CONTINUE_EPISODE
            } else {
                PlayAction.PLAY_MOVIE
            }
        }
        return if (episode.lastPositionMs > PlaybackRules.RESUME_THRESHOLD_MS) {
            PlayAction.CONTINUE_EPISODE
        } else {
            PlayAction.PLAY_EPISODE
        }
    }

    fun selectedSeason(seasonNumbers: List<Int>, current: Int?, continueSeason: Int?): Int? {
        if (seasonNumbers.size <= 1) return seasonNumbers.singleOrNull()
        if (current != null && current in seasonNumbers) return current
        if (continueSeason != null && continueSeason in seasonNumbers) return continueSeason
        return seasonNumbers.firstOrNull()
    }

    fun progressFraction(episode: LibraryEpisode): Float {
        if (episode.isWatched) return 1f
        if (episode.durationMs <= 0L || episode.lastPositionMs <= 0L) return 0f
        return (episode.lastPositionMs.toFloat() / episode.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun overlayRemainingMs(episode: LibraryEpisode): Long? {
        if (episode.isWatched || episode.durationMs <= 0L) return null
        if (episode.lastPositionMs <= 0L) return episode.durationMs
        return (episode.durationMs - episode.lastPositionMs).coerceAtLeast(0L)
    }

    fun showSeasonChips(isMovie: Boolean, seasonCount: Int): Boolean =
        !isMovie && seasonCount >= 1

    /** 16:9 TV reference frame used for series-detail card proportions. */
    const val TV_WIDTH_DP = 960
    const val TV_HEIGHT_DP = 540
    const val HERO_WIDTH_DP = 580
    const val HERO_HEIGHT_DP = 270
    const val EPISODE_ROW_TOP_FRACTION = 0.70f
    const val EPISODE_TITLE_BLOCK_DP = 22
    const val EPISODE_BOTTOM_PAD_DP = 44

    fun widthFraction(sizeDp: Int, screenDp: Int = TV_WIDTH_DP): Float =
        if (screenDp <= 0) 0f else sizeDp.toFloat() / screenDp.toFloat()

    fun heightFraction(sizeDp: Int, screenDp: Int = TV_HEIGHT_DP): Float =
        if (screenDp <= 0) 0f else sizeDp.toFloat() / screenDp.toFloat()

    fun episodeThumbTopDp(
        screenHeightDp: Int = TV_HEIGHT_DP,
        bottomPadDp: Int = EPISODE_BOTTOM_PAD_DP,
        titleBlockDp: Int = EPISODE_TITLE_BLOCK_DP,
        thumbHeightDp: Int,
        focusPadDp: Int,
    ): Int = screenHeightDp - bottomPadDp - titleBlockDp - thumbHeightDp - 2 * focusPadDp.coerceAtLeast(0)

    fun visibleEpisodeCount(
        screenWidthDp: Int,
        startPadDp: Int,
        endPadDp: Int,
        episodeWidthDp: Int,
        gapDp: Int,
    ): Float {
        val inner = (screenWidthDp - startPadDp.coerceAtLeast(0) - endPadDp.coerceAtLeast(0)).coerceAtLeast(0)
        val stride = episodeWidthDp.coerceAtLeast(1) + gapDp.coerceAtLeast(0)
        return inner.toFloat() / stride.toFloat()
    }
}

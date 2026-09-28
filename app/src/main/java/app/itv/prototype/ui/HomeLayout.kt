package app.itv.prototype.ui

data class ImageSize(val width: Int, val height: Int) {
    val aspect: Float get() = if (height <= 0) 0f else width.toFloat() / height.toFloat()
}

data class HeroPlacement(
    val scale: Float,
    val translateX: Float,
    val translateY: Float,
    val fullBleed: Boolean,
)

object HomeLayout {
    const val LANDSCAPE_MIN_ASPECT = 1.45f
    const val PORTRAIT_MAX_ASPECT = 0.82f

    fun rtlStartScrollX(
        rowWidth: Int,
        viewportWidth: Int,
        paddingLeft: Int = 0,
        paddingRight: Int = 0,
    ): Int = (rowWidth - (viewportWidth - paddingLeft - paddingRight)).coerceAtLeast(0)

    fun shelfRowWidth(
        posterCount: Int,
        posterWidth: Int,
        gap: Int,
        moreWidth: Int,
        includeMore: Boolean = true,
    ): Int {
        val posters = posterCount.coerceAtLeast(0)
        val cards = posters + if (includeMore) 1 else 0
        if (cards == 0) return 0
        val posterSpan = posters * posterWidth.coerceAtLeast(0)
        val moreSpan = if (includeMore) moreWidth.coerceAtLeast(0) else 0
        return posterSpan + moreSpan + cards * gap.coerceAtLeast(0)
    }

    fun shelfVisibleCount(
        availableWidth: Int,
        posterWidth: Int,
        gap: Int,
        paddingStart: Int,
        paddingEnd: Int,
        moreWidth: Int,
        maxCount: Int = 8,
    ): Int {
        val inner = (availableWidth - paddingStart.coerceAtLeast(0) - paddingEnd.coerceAtLeast(0)).coerceAtLeast(0)
        var count = maxCount.coerceAtLeast(0)
        while (count > 0 && shelfRowWidth(count, posterWidth, gap, moreWidth) > inner) {
            count--
        }
        return count
    }

    fun revealDelta(childStart: Int, childEnd: Int, viewportStart: Int, viewportEnd: Int): Int = when {
        childStart < viewportStart -> childStart - viewportStart
        childEnd > viewportEnd -> childEnd - viewportEnd
        else -> 0
    }

    fun libraryScrollTop(heroCopy: Int, heading: Int, continueRow: Int, hasContinue: Boolean): Int {
        val pinned = if (hasContinue) heading.coerceAtLeast(0) + continueRow.coerceAtLeast(0) else 0
        return heroCopy.coerceAtLeast(0) + pinned
    }

    fun sectionSnapY(headingTop: Int): Int = headingTop.coerceAtLeast(0)

    /**
     * Vertical clip for Home library drawing in content coordinates. The
     * ScrollView canvas is already translated by -scrollY, so clipping at
     * 0..height only keeps the first page of content and lets earlier rows
     * paint over pinned hero/continue. Horizontal slop stays on the caller.
     */
    fun libraryDrawClipTop(scrollY: Int): Int = scrollY.coerceAtLeast(0)

    fun libraryDrawClipBottom(scrollY: Int, height: Int): Int =
        libraryDrawClipTop(scrollY) + height.coerceAtLeast(0)

    fun progressFill(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    fun catalogSignature(continueIds: List<Long>, seriesIds: List<Long>, movieIds: List<Long>, extras: List<String>): String =
        "c:${continueIds.joinToString(",")}|s:${seriesIds.joinToString(",")}|m:${movieIds.joinToString(",")}|x:${extras.joinToString(",")}"

    fun isLandscape(size: ImageSize?): Boolean = size != null && size.width > 0 && size.aspect >= LANDSCAPE_MIN_ASPECT

    fun isPortrait(size: ImageSize?): Boolean = size != null && size.height > 0 && size.aspect <= PORTRAIT_MAX_ASPECT

    fun coverUrl(posterUrl: String?, backdropUrl: String?, poster: ImageSize?, backdrop: ImageSize?): String? = when {
        isPortrait(poster) -> posterUrl
        isPortrait(backdrop) -> backdropUrl
        posterUrl != null -> posterUrl
        else -> backdropUrl
    }

    fun heroUrl(
        backdropUrl: String?,
        episodeUrl: String?,
        posterUrl: String?,
        backdrop: ImageSize?,
        episode: ImageSize?,
        poster: ImageSize?,
    ): String? {
        val candidates = listOf(
            backdropUrl to backdrop,
            episodeUrl to episode,
            posterUrl to poster,
        ).filter { !it.first.isNullOrBlank() }
        candidates.filter { isLandscape(it.second) }
            .maxByOrNull { it.second?.aspect ?: 0f }
            ?.first
            ?.let { return it }
        return candidates.firstOrNull { it.second == null }?.first
            ?: candidates.firstOrNull()?.first
    }

    fun heroPlacement(viewW: Int, viewH: Int, image: ImageSize?): HeroPlacement {
        if (viewW <= 0 || viewH <= 0 || image == null || image.width <= 0 || image.height <= 0) {
            return HeroPlacement(1f, 0f, 0f, false)
        }
        if (!isLandscape(image)) {
            val targetH = viewH * 0.62f
            val scale = targetH / image.height.toFloat()
            return HeroPlacement(scale, viewW * 0.06f, viewH * 0.10f, false)
        }
        val fitWidth = viewW / image.width.toFloat()
        return HeroPlacement(fitWidth, 0f, 0f, true)
    }

    fun coverFitsFrame(size: ImageSize?): Boolean = isPortrait(size)

    fun coverNeedsFill(size: ImageSize?): Boolean =
        size != null && size.width > 0 && size.height > 0 && !coverFitsFrame(size)
}

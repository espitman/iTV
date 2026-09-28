package app.itv.prototype.ui

object LibraryGridLayout {
    const val MAX_COLUMNS = 6
    const val GAP_RATIO = 0.18f

    data class Metrics(
        val columns: Int,
        val columnWidth: Int,
        val gap: Int,
        val posterHeight: Int,
        val itemHeight: Int,
        val contentWidth: Int,
        val leftGutter: Int,
        val rightGutter: Int,
    )

    data class ContentBounds(
        val availableWidth: Int,
        val contentWidth: Int,
        val leftGutter: Int,
        val rightGutter: Int,
    ) {
        val left: Int get() = leftGutter
        val right: Int get() = availableWidth - rightGutter
    }

    fun metrics(
        availableWidth: Int,
        minColumnWidth: Int,
        titleBlock: Int,
        focusPad: Int,
        maxColumns: Int = MAX_COLUMNS,
        gapRatio: Float = GAP_RATIO,
    ): Metrics {
        val width = availableWidth.coerceAtLeast(1)
        val cap = maxColumns.coerceAtLeast(1)
        var columns = cap
        while (columns > 1 && columnWidth(width, columns, gapRatio) < minColumnWidth) {
            columns--
        }
        val columnWidth = columnWidth(width, columns, gapRatio).coerceAtLeast(1)
        val gap = if (columns <= 1) 0 else ((width - columnWidth * columns) / (columns - 1)).coerceAtLeast(0)
        val posterHeight = posterFrameHeight(columnWidth, focusPad)
        val bounds = contentBounds(width, columns, columnWidth, gap)
        return Metrics(
            columns = columns,
            columnWidth = columnWidth,
            gap = gap,
            posterHeight = posterHeight,
            itemHeight = posterHeight + titleBlock.coerceAtLeast(0),
            contentWidth = bounds.contentWidth,
            leftGutter = bounds.leftGutter,
            rightGutter = bounds.rightGutter,
        )
    }

    fun usedWidth(columns: Int, columnWidth: Int, gap: Int): Int {
        val count = columns.coerceAtLeast(1)
        return count * columnWidth.coerceAtLeast(0) + (count - 1).coerceAtLeast(0) * gap.coerceAtLeast(0)
    }

    fun contentBounds(availableWidth: Int, columns: Int, columnWidth: Int, gap: Int): ContentBounds {
        val width = availableWidth.coerceAtLeast(0)
        val contentWidth = usedWidth(columns, columnWidth, gap).coerceAtMost(width)
        val leftover = (width - contentWidth).coerceAtLeast(0)
        val leftGutter = leftover / 2
        return ContentBounds(
            availableWidth = width,
            contentWidth = contentWidth,
            leftGutter = leftGutter,
            rightGutter = leftover - leftGutter,
        )
    }

    fun columnLeft(
        indexFromStart: Int,
        columnWidth: Int,
        gap: Int,
        bounds: ContentBounds,
        rtl: Boolean,
    ): Int {
        val index = indexFromStart.coerceAtLeast(0)
        val stride = columnWidth.coerceAtLeast(0) + gap.coerceAtLeast(0)
        return if (rtl) {
            bounds.right - columnWidth.coerceAtLeast(0) - index * stride
        } else {
            bounds.left + index * stride
        }
    }

    fun columnWidth(availableWidth: Int, columns: Int, gapRatio: Float = GAP_RATIO): Int {
        val count = columns.coerceAtLeast(1)
        val gapWeight = gapRatio.coerceAtLeast(0f) * (count - 1)
        return (availableWidth.coerceAtLeast(1) / (count + gapWeight)).toInt()
    }

    fun posterFrameHeight(columnWidth: Int, focusPad: Int): Int {
        val pad = focusPad.coerceAtLeast(0)
        val posterWidth = (columnWidth - 2 * pad).coerceAtLeast(1)
        return posterWidth * 3 / 2 + 2 * pad
    }

    @Suppress("UNUSED_PARAMETER")
    fun coverUrl(posterUrl: String?, backdropUrl: String? = null): String? = posterUrl

    fun movieDurationMinutes(durationMinutes: Int): Int? =
        durationMinutes.takeIf { it > 0 }

    fun hasSeriesMeta(seasonCount: Int, episodeCount: Int): Boolean =
        seasonCount > 0 || episodeCount > 0
}

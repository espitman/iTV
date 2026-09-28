package app.itv.prototype.ui

object HomeLayout {
    fun rtlStartScrollX(
        rowWidth: Int,
        viewportWidth: Int,
        paddingLeft: Int = 0,
        paddingRight: Int = 0,
    ): Int = (rowWidth - (viewportWidth - paddingLeft - paddingRight)).coerceAtLeast(0)

    fun revealDelta(childStart: Int, childEnd: Int, viewportStart: Int, viewportEnd: Int): Int = when {
        childStart < viewportStart -> childStart - viewportStart
        childEnd > viewportEnd -> childEnd - viewportEnd
        else -> 0
    }

    fun progressFill(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    fun catalogSignature(continueIds: List<Long>, seriesIds: List<Long>, movieIds: List<Long>, extras: List<String>): String =
        "c:${continueIds.joinToString(",")}|s:${seriesIds.joinToString(",")}|m:${movieIds.joinToString(",")}|x:${extras.joinToString(",")}"
}

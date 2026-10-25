package app.itv.prototype.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Small helpers that compose [SkeletonBlockView] placeholders into RTL rows and catalog cells. */
object SkeletonViews {
    const val POSTER_RADIUS_DP = 10f

    fun block(context: Context, widthPx: Int, heightPx: Int, radiusDp: Float): SkeletonBlockView =
        SkeletonBlockView(context).apply {
            setRadiusDp(radiusDp)
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
        }

    fun block(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        radiusDp: Float,
        topMarginPx: Int,
    ): SkeletonBlockView = block(context, widthPx, heightPx, radiusDp).apply {
        (layoutParams as LinearLayout.LayoutParams).topMargin = topMarginPx
    }

    /** RTL horizontal strip of [count] identical blocks with [gap] between them. */
    fun row(context: Context, count: Int, itemWidth: Int, itemHeight: Int, gap: Int, radiusDp: Float): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            clipChildren = false
            clipToPadding = false
        }
        repeat(count) { index ->
            val item = block(context, itemWidth, itemHeight, radiusDp)
            if (index > 0) (item.layoutParams as LinearLayout.LayoutParams).marginStart = gap
            row.addView(item)
        }
        return row
    }

    /** Poster cell mirroring the catalog/search item: artwork block plus title and meta lines. */
    fun catalogCell(context: Context, columnWidth: Int, posterHeight: Int): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        cell.addView(block(context, columnWidth, posterHeight, POSTER_RADIUS_DP))
        cell.addView(
            block(context, (columnWidth * 0.72f).toInt().coerceAtLeast(dp(24f)), dp(12f), 4f, dp(8f)),
        )
        cell.addView(
            block(context, (columnWidth * 0.45f).toInt().coerceAtLeast(dp(18f)), dp(9f), 3f, dp(4f)),
        )
        return cell
    }
}

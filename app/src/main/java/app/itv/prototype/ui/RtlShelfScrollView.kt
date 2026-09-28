package app.itv.prototype.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.HorizontalScrollView

/**
 * RTL row scroller that keeps a stable start offset when images load or children
 * receive focus. Default HorizontalScrollView re-pins and auto-scrolls using
 * scaled/focus rectangles, which jumps the whole Home shelf.
 */
class RtlShelfScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {
    var pinToStart = true
    private var allowAutoScroll = false

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        isNestedScrollingEnabled = false
        layoutDirection = LAYOUT_DIRECTION_RTL
    }

    fun reveal(child: View, immediate: Boolean = true) {
        if (child.width <= 0 || width <= 0) return
        val rect = Rect(0, 0, child.width, child.height)
        offsetDescendantRectToMyCoords(child, rect)
        val delta = HomeLayout.revealDelta(
            rect.left,
            rect.right,
            scrollX + paddingLeft,
            scrollX + width - paddingRight,
        )
        if (delta == 0) return
        pinToStart = false
        allowAutoScroll = true
        try {
            if (immediate) scrollBy(delta, 0) else smoothScrollBy(delta, 0)
        } finally {
            allowAutoScroll = false
        }
    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        val focused = findFocus()
        if (focused != null) reveal(focused, immediate) else reveal(child, immediate)
        return true
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        reveal(focused, true)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (!allowAutoScroll) return 0
        return super.computeScrollDeltaToGetChildRectOnScreen(rect)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val keepX = scrollX
        super.onLayout(changed, left, top, right, bottom)
        val childWidth = getChildAt(0)?.width ?: 0
        val start = HomeLayout.rtlStartScrollX(childWidth, width, paddingLeft, paddingRight)
        val target = when {
            pinToStart && layoutDirection == LAYOUT_DIRECTION_RTL -> start
            pinToStart -> 0
            else -> keepX.coerceIn(0, start)
        }
        if (scrollX != target) scrollTo(target, 0)
    }
}

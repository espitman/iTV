package app.itv.prototype.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView

/**
 * RTL row scroller that keeps the start card on the padded inset.
 *
 * HorizontalScrollView handles D-pad keys itself, before focus navigation.
 * [arrowScroll] then smooth-scrolls with a viewport that ignores padding. At the
 * padded start that remainder is exactly `-paddingRight`, so the animation
 * finishes with the first card on the screen edge. The scroller keeps running
 * after [reveal] has already restored the inset, which is why one early frame
 * can look correct and a settled frame does not.
 */
class RtlShelfScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {
    var pinToStart = true

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
        if (isRtlStartItem(child)) {
            pinToStart = true
            scrollToRtlStart()
            return
        }
        pinToStart = false
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
        scrollByAmount(delta, immediate)
    }

    private fun scrollToRtlStart() {
        val row = getChildAt(0) ?: return
        if (row.width <= 0 || width <= 0) {
            post {
                if (pinToStart) scrollToRtlStart()
            }
            return
        }
        val start = HomeLayout.rtlStartScrollX(row.width, width, paddingLeft, paddingRight)
        if (scrollX != start) scrollTo(start, 0)
    }

    private fun isRtlStartItem(focused: View): Boolean {
        val row = getChildAt(0) as? ViewGroup ?: return false
        var node = focused
        while (node.parent is View && node.parent !== row && node.parent !== this) {
            node = node.parent as View
        }
        return node.parent === row && row.indexOfChild(node) == 0
    }

    private fun focusedRtlStartScroll(): Int? {
        if (layoutDirection != LAYOUT_DIRECTION_RTL) return null
        val focused = findFocus() ?: return null
        if (!isRtlStartItem(focused)) return null
        val row = getChildAt(0) ?: return null
        if (row.width <= 0 || width <= 0) return null
        return HomeLayout.rtlStartScrollX(row.width, width, paddingLeft, paddingRight)
    }

    private fun scrollByAmount(delta: Int, immediate: Boolean) {
        if (immediate) scrollBy(delta, 0) else smoothScrollBy(delta, 0)
    }

    override fun executeKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> FOCUS_RIGHT
            else -> return false
        }
        return moveFocusWithoutFrameworkScroll(direction)
    }

    override fun arrowScroll(direction: Int): Boolean = moveFocusWithoutFrameworkScroll(direction)

    private fun moveFocusWithoutFrameworkScroll(direction: Int): Boolean {
        val current = findFocus()?.takeIf { it !== this }
        val next = FocusFinder.getInstance().findNextFocus(this, current, direction)
        if (next == null || next === this || next === current) return false
        return next.requestFocus(direction)
    }

    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean {
        val focused = findFocus()
        if (focused != null) reveal(focused, true) else reveal(child, true)
        return true
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        reveal(focused, true)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int = 0

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        val pinned = focusedRtlStartScroll()
        if (pinned != null) {
            super.onOverScrolled(pinned, scrollY, true, clampedY)
        } else {
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        val pinned = focusedRtlStartScroll() ?: return
        if (scrollX != pinned) scrollTo(pinned, 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val keepX = scrollX
        val focusedAtStart = findFocus()?.let(::isRtlStartItem) == true
        super.onLayout(changed, left, top, right, bottom)
        val childWidth = getChildAt(0)?.width ?: 0
        val target = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            HomeLayout.rtlFocusScrollX(
                keepX,
                childWidth,
                width,
                paddingLeft,
                paddingRight,
                pinToStart,
                focusedAtStart,
            )
        } else if (pinToStart) {
            0
        } else {
            keepX.coerceAtLeast(0)
        }
        if (scrollX != target) scrollTo(target, 0)
    }
}

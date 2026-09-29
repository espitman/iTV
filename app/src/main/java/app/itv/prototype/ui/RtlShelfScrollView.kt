package app.itv.prototype.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import kotlin.math.abs

/**
 * RTL row scroller that keeps the start card on the padded inset.
 *
 * HorizontalScrollView handles D-pad keys itself, before focus navigation.
 * [arrowScroll] then smooth-scrolls with a viewport that ignores padding. At the
 * padded start that remainder is exactly `-paddingRight`, so the animation
 * finishes with the first card on the screen edge. The scroller keeps running
 * after [reveal] has already restored the inset, which is why one early frame
 * can look correct and a settled frame does not.
 *
 * Home opts into [animateFocusScroll]. That path uses one reused animator to an
 * absolute [HomeLayout.shelfScrollTarget] and does not call [smoothScrollBy].
 * [settling] keeps [computeScroll] from pinning the start card mid-flight.
 */
class RtlShelfScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {
    var pinToStart = true
    var animateFocusScroll = false

    private val scrollAnimator = ValueAnimator.ofInt(0, 0).apply {
        interpolator = DecelerateInterpolator(1.6f)
    }
    private var settling = false
    private var scrollGeneration = 0
    private var scrollTarget = Int.MIN_VALUE

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
        val animate = animateFocusScroll && !immediate
        if (isRtlStartItem(child)) {
            pinToStart = true
            scrollToRtlStart(animate)
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
        if (delta == 0) {
            if (animate) cancelScrollAnimation()
            return
        }
        scrollByAmount(delta, immediate)
    }

    private fun scrollToRtlStart(animate: Boolean) {
        val row = getChildAt(0) ?: return
        if (row.width <= 0 || width <= 0) {
            post {
                if (pinToStart) scrollToRtlStart(animate)
            }
            return
        }
        val start = HomeLayout.rtlStartScrollX(row.width, width, paddingLeft, paddingRight)
        val target = HomeLayout.shelfScrollTarget(scrollX, 0, start, focusedAtRtlStart = true)
        if (animate) {
            animateScrollTo(target)
        } else if (scrollX != target) {
            cancelScrollAnimation()
            scrollTo(target, 0)
        }
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
        if (immediate || !animateFocusScroll) {
            if (immediate) scrollBy(delta, 0) else smoothScrollBy(delta, 0)
            return
        }
        val row = getChildAt(0)
        val limit = if (row != null && row.width > 0 && width > 0) {
            HomeLayout.rtlStartScrollX(row.width, width, paddingLeft, paddingRight)
        } else {
            Int.MAX_VALUE
        }
        animateScrollTo(HomeLayout.shelfScrollTarget(scrollX, delta, limit, focusedAtRtlStart = false))
    }

    private fun animateScrollTo(target: Int) {
        if (settling && scrollTarget == target) return
        scrollAnimator.removeAllUpdateListeners()
        scrollAnimator.removeAllListeners()
        scrollAnimator.cancel()
        scrollTarget = target
        if (scrollX == target) {
            settling = false
            return
        }
        settling = true
        val generation = ++scrollGeneration
        val from = scrollX
        scrollAnimator.setIntValues(from, target)
        scrollAnimator.duration = (150L + abs(target - from) / 10L).coerceIn(160L, 220L)
        scrollAnimator.addUpdateListener { animator ->
            if (generation != scrollGeneration) return@addUpdateListener
            scrollTo(animator.animatedValue as Int, 0)
        }
        scrollAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (generation != scrollGeneration) return
                settling = false
                val pinned = focusedRtlStartScroll()
                if (pinned != null && scrollX != pinned) scrollTo(pinned, 0)
            }

            override fun onAnimationCancel(animation: Animator) {
                if (generation != scrollGeneration) return
                settling = false
            }
        })
        scrollAnimator.start()
    }

    private fun cancelScrollAnimation() {
        scrollGeneration++
        scrollAnimator.removeAllUpdateListeners()
        scrollAnimator.removeAllListeners()
        scrollAnimator.cancel()
        settling = false
        scrollTarget = scrollX
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
        val revealNow = !animateFocusScroll
        if (focused != null) reveal(focused, revealNow) else reveal(child, revealNow)
        return true
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        reveal(focused, immediate = !animateFocusScroll)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int = 0

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        if (settling) {
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
            return
        }
        val pinned = focusedRtlStartScroll()
        if (pinned != null) {
            super.onOverScrolled(pinned, scrollY, true, clampedY)
        } else {
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        }
    }

    override fun computeScroll() {
        super.computeScroll()
        if (settling) return
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
        if (scrollX != target) {
            cancelScrollAnimation()
            scrollTo(target, 0)
        }
    }
}

package app.itv.prototype.ui

import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.TextView
import app.itv.prototype.R

private val cardFocusInterpolator = DecelerateInterpolator()

fun View.bindFocusTint() {
    isFocusable = true
    isFocusableInTouchMode = true
    isClickable = true
    setOnFocusChangeListener { view, focused ->
        val button = view
        val primary = button.id in setOf(R.id.heroPlay, R.id.play, R.id.resumePlay, R.id.nextPlay)
        (button as? TextView)?.setTextColor(context.getColor(if (primary) R.color.bg else R.color.text))
        button.animate().scaleX(1f).scaleY(1f)
            .setDuration(110).start()
    }
}

fun View.bindCardFocus() {
    isFocusable = true
    isFocusableInTouchMode = true
    isClickable = true
    if (this is ViewGroup) descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    setOnFocusChangeListener { view, focused ->
        view.applyCinematicFocus(focused, 1f)
    }
}

fun View.applyCinematicFocus(focused: Boolean, scale: Float = 1.06f) {
    if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
    if (width > 0 && height > 0) {
        pivotX = width / 2f
        pivotY = height / 2f
    }
    elevation = 0f
    animate()
        .scaleX(if (focused) scale else 1f)
        .scaleY(if (focused) scale else 1f)
        .translationZ(if (focused) 8f else 0f)
        .setDuration(180)
        .setInterpolator(cardFocusInterpolator)
        .start()
    if (Build.VERSION.SDK_INT >= 28) {
        outlineSpotShadowColor = context.getColor(R.color.cyan)
        outlineAmbientShadowColor = context.getColor(R.color.focus_glow)
    }
}

fun View.ensureFocusId(): Int {
    if (id == View.NO_ID) id = View.generateViewId()
    return id
}

fun HorizontalScrollView.disableSelfFocus() {
    isFocusable = false
    isFocusableInTouchMode = false
    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
}

fun wireRtlRow(views: List<View>) {
    views.forEach { it.ensureFocusId() }
    views.forEachIndexed { index, view ->
        val previous = views.getOrNull(index - 1)
        val next = views.getOrNull(index + 1)
        view.nextFocusLeftId = next?.id ?: View.NO_ID
        view.nextFocusRightId = previous?.id ?: View.NO_ID
    }
}

fun View.isShownClickableFocus(): Boolean =
    isShown && isEnabled && isClickable && isFocusable

fun View.clipRound(radiusDp: Float) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val width = view.width.takeIf { it > 0 } ?: view.measuredWidth
            val height = view.height.takeIf { it > 0 } ?: view.measuredHeight
            if (width <= 0 || height <= 0) {
                outline.setEmpty()
                return
            }
            outline.setRoundRect(0, 0, width, height, radiusDp * resources.displayMetrics.density)
        }
    }
}

fun View.clipOval() {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val width = view.width.takeIf { it > 0 } ?: view.measuredWidth
            val height = view.height.takeIf { it > 0 } ?: view.measuredHeight
            if (width <= 0 || height <= 0) {
                outline.setEmpty()
                return
            }
            outline.setOval(0, 0, width, height)
        }
    }
}

fun TextView.setFocusedText(focused: Boolean) {
    setTextColor(context.getColor(if (focused) R.color.bg else R.color.text))
}

package app.itv.prototype.ui

import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.TextView
import app.itv.prototype.R

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
        view.animate().scaleX(1f).scaleY(1f)
            .setDuration(120).start()
        view.elevation = if (focused) 18f else 0f
        if (focused) view.requestRectangleOnScreen(android.graphics.Rect(), false)
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
            outline.setRoundRect(0, 0, view.width, view.height, radiusDp * resources.displayMetrics.density)
        }
    }
}

fun TextView.setFocusedText(focused: Boolean) {
    setTextColor(context.getColor(if (focused) R.color.bg else R.color.text))
}

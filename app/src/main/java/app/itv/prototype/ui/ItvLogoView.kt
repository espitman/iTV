package app.itv.prototype.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.itv.prototype.R

/**
 * Home iTV mark: cyan rounded square with i, then white TV. `logoSize` is the mark edge.
 *
 * The full logo stays on screen. About every 10 seconds, while this view is attached
 * and visible, it plays one short reveal: the mark collapses to a cyan dot, grows
 * back into the i tile, then TV writes on. Scale, alpha, corner radius, and clip
 * stay inside the existing bounds, so the logo does not move or change focus.
 */
class ItvLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val mark: TextView
    private val wordmark: TextView
    private val tile = GradientDrawable()
    private val letterRgb = context.getColor(R.color.bg) and 0x00FFFFFF
    private val clip = Rect()
    private var edgePx = 1
    private var armed = false
    private var playGeneration = 0

    private val revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = REVEAL_MS
    }

    private val nextReveal = Runnable { playReveal() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LTR
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val typed = context.obtainStyledAttributes(attrs, R.styleable.ItvLogoView)
        val size = typed.getDimensionPixelSize(
            R.styleable.ItvLogoView_logoSize,
            resources.getDimensionPixelSize(R.dimen.logo_size_home),
        )
        typed.recycle()
        mark = TextView(context).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(context.getColor(R.color.bg))
            text = "i"
            background = tile
        }
        wordmark = TextView(context).apply {
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            text = "TV"
        }
        addView(mark)
        addView(wordmark)
        applySize(size)
    }

    fun setLogoSize(sizePx: Int) {
        applySize(sizePx)
        if (revealAnimator.isRunning) showCompleteLogo()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncPlayback()
    }

    override fun onDetachedFromWindow() {
        stopAndReset()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) arm() else stopAndReset()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (Build.VERSION.SDK_INT >= 24) return
        syncPlayback()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (Build.VERSION.SDK_INT >= 24) return
        syncPlayback()
    }

    private fun syncPlayback() {
        if (isAttachedToWindow && isShown) arm() else stopAndReset()
    }

    private fun arm() {
        if (!isAttachedToWindow || !isShown || armed || revealAnimator.isRunning) return
        armed = true
        postDelayed(nextReveal, INTERVAL_MS)
    }

    private fun stopAndReset() {
        playGeneration++
        armed = false
        removeCallbacks(nextReveal)
        revealAnimator.cancel()
        showCompleteLogo()
    }

    private fun playReveal() {
        armed = false
        if (!isAttachedToWindow || !isShown) return
        if (mark.width <= 0 || wordmark.width <= 0) {
            postDelayed(nextReveal, 100)
            return
        }
        val generation = ++playGeneration
        revealAnimator.cancel()
        revealAnimator.removeAllUpdateListeners()
        revealAnimator.removeAllListeners()
        mark.pivotX = mark.width / 2f
        mark.pivotY = mark.height / 2f
        revealAnimator.addUpdateListener { animator ->
            if (generation != playGeneration) return@addUpdateListener
            renderReveal(animator.animatedValue as Float)
        }
        revealAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (generation != playGeneration) return
                showCompleteLogo()
                arm()
            }
        })
        revealAnimator.start()
    }

    private fun renderReveal(t: Float) {
        val collapse = phase(t, 0f, 0.30f)
        val form = phase(t, 0.36f, 0.64f)
        val tileRound = if (t < 0.36f) collapse else 1f - form
        val tileScale = if (t < 0.36f) lerp(1f, DOT_SCALE, collapse) else lerp(DOT_SCALE, 1f, form)
        mark.scaleX = tileScale
        mark.scaleY = tileScale
        applyTile(tileRound)
        val letter = if (t < 0.36f) 1f - phase(t, 0f, 0.16f) else phase(t, 0.52f, 0.72f)
        setLetterAlpha(letter)
        val written = if (t < 0.68f) 1f - phase(t, 0.02f, 0.28f) else phase(t, 0.70f, 1f)
        revealWordmark(written)
    }

    private fun showCompleteLogo() {
        mark.scaleX = 1f
        mark.scaleY = 1f
        if (mark.width > 0) {
            mark.pivotX = mark.width / 2f
            mark.pivotY = mark.height / 2f
        }
        applyTile(0f)
        setLetterAlpha(1f)
        wordmark.alpha = 1f
        wordmark.clipBounds = null
    }

    private fun applySize(sizePx: Int) {
        edgePx = sizePx.coerceAtLeast(1)
        mark.layoutParams = LayoutParams(edgePx, edgePx)
        mark.setTextSize(TypedValue.COMPLEX_UNIT_PX, edgePx * 17f / 24f)
        tile.setColor(context.getColor(R.color.cyan))
        applyTile(0f)
        wordmark.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = (edgePx * 2f / 24f).toInt().coerceAtLeast(1)
        }
        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_PX, edgePx * 22f / 24f)
        setLetterAlpha(1f)
    }

    private fun applyTile(round: Float) {
        tile.cornerRadius = lerp(edgePx * 7f / 24f, edgePx / 2f, round.coerceIn(0f, 1f))
    }

    private fun setLetterAlpha(alpha: Float) {
        val channel = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        mark.setTextColor(channel shl 24 or letterRgb)
    }

    private fun revealWordmark(fraction: Float) {
        val width = wordmark.width
        val height = wordmark.height
        if (width <= 0 || height <= 0 || fraction >= 1f) {
            wordmark.clipBounds = null
            return
        }
        clip.set(0, 0, (width * fraction.coerceAtLeast(0f)).toInt().coerceIn(0, width), height)
        wordmark.clipBounds = clip
    }

    private companion object {
        const val INTERVAL_MS = 10_000L
        const val REVEAL_MS = 1_700L
        const val DOT_SCALE = 0.26f

        fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

        fun phase(t: Float, start: Float, end: Float): Float {
            if (t <= start) return 0f
            if (t >= end) return 1f
            val u = (t - start) / (end - start)
            return u * u * (3f - 2f * u)
        }
    }
}

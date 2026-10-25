package app.itv.prototype.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import app.itv.prototype.R

/**
 * Rounded placeholder block with a moving shimmer band, drawn while real
 * content is still loading. Blocks are inert: never focusable, never clickable
 * and hidden from accessibility, so D-pad navigation skips them.
 *
 * The gradient is built once per size and moved with a reused [Matrix], so a
 * shimmering screen allocates nothing per frame. All blocks animate from one
 * shared [SkeletonClock], keeping the sweep in sync. In an RTL layout the band
 * travels right to left to match the layout direction.
 */
class SkeletonBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.skeleton_base)
    }
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private val highlight = context.getColor(R.color.skeleton_highlight)
    private val transparent = highlight and 0x00FFFFFF
    private var shimmerShader: LinearGradient? = null
    private var radiusPx = 0f

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
        val typed = context.obtainStyledAttributes(attrs, R.styleable.SkeletonBlockView)
        radiusPx = typed.getDimension(R.styleable.SkeletonBlockView_skeletonRadius, 0f)
        typed.recycle()
    }

    fun setRadiusDp(radiusDp: Float) {
        radiusPx = radiusDp * resources.displayMetrics.density
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT < 24) syncClock()
    }

    override fun onDetachedFromWindow() {
        SkeletonClock.unregister(this)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val widthPx = width.toFloat()
        if (widthPx <= 0f) {
            shimmerShader = null
            shimmerPaint.shader = null
            return
        }
        val band = widthPx * BAND_RATIO
        shimmerShader = LinearGradient(
            -band,
            0f,
            band,
            0f,
            intArrayOf(transparent, highlight, transparent),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        shimmerPaint.shader = shimmerShader
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        // API 24+ aggregated visibility already folds in the window state, so trust it
        // directly. isShown() alone stays true while the window is hidden, which would
        // otherwise keep the shared clock running in the background.
        if (Build.VERSION.SDK_INT >= 24) {
            if (isVisible) SkeletonClock.register(this) else SkeletonClock.unregister(this)
        }
    }

    // API 23 has no aggregated callback; mirror it from the legacy visibility callbacks.
    @Suppress("DEPRECATION")
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (Build.VERSION.SDK_INT < 24) syncClock()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (Build.VERSION.SDK_INT < 24) syncClock()
    }

    /** Register only while this block can actually be seen, so hidden screens stop the shared clock. */
    private fun syncClock() {
        val visible = isAttachedToWindow && isShown && getWindowVisibility() == VISIBLE
        if (visible) SkeletonClock.register(this) else SkeletonClock.unregister(this)
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        val radius = radiusPx.coerceIn(0f, minOf(width, height) / 2f)
        rect.set(0f, 0f, width, height)
        canvas.drawRoundRect(rect, radius, radius, basePaint)

        val shader = shimmerShader ?: return
        val band = width * BAND_RATIO
        val travel = width + 2f * band
        val phase = SkeletonClock.phase
        val center = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            width + band - travel * phase
        } else {
            -band + travel * phase
        }
        shaderMatrix.setTranslate(center, 0f)
        shader.setLocalMatrix(shaderMatrix)
        canvas.drawRoundRect(rect, radius, radius, shimmerPaint)
    }

    private companion object {
        const val BAND_RATIO = 0.45f
    }
}

/**
 * Drives every visible [SkeletonBlockView] with one linear sweep. The clock
 * runs only while at least one block is registered and stops itself when the
 * last placeholder is detached or hidden, so screens with no skeleton cost
 * nothing.
 */
internal object SkeletonClock {
    var phase: Float = 0f
        private set

    private val views = LinkedHashSet<SkeletonBlockView>()
    private var animator: ValueAnimator? = null

    fun register(view: SkeletonBlockView) {
        if (views.add(view)) ensureRunning()
    }

    fun unregister(view: SkeletonBlockView) {
        views.remove(view)
        if (views.isEmpty()) stop()
    }

    private fun ensureRunning() {
        if (animator?.isRunning == true) return
        val running = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SHIMMER_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animation ->
                phase = animation.animatedFraction
                views.forEach { view -> if (view.isShown) view.invalidate() }
            }
        }
        animator = running
        running.start()
    }

    private fun stop() {
        animator?.cancel()
        animator = null
        phase = 0f
    }

    private const val SHIMMER_MS = 1500L
}

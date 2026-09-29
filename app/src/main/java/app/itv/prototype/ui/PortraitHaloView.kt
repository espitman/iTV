package app.itv.prototype.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import app.itv.prototype.R

/**
 * Soft cyan corona around a focused circular portrait. Drawn larger than the
 * 84dp frame so the blur is not clipped; the crisp ring stays on bg_credit_focus.
 */
class PortraitHaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val portraitRadius = resources.getDimension(R.dimen.credit_portrait) / 2f
    private val bloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.cyan)
        alpha = 72
        strokeWidth = 10f * density
        maskFilter = BlurMaskFilter(12f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.cyan)
        alpha = 150
        strokeWidth = 3.5f * density
        maskFilter = BlurMaskFilter(6f * density, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        if (!haloVisible()) return
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, portraitRadius, bloom)
        canvas.drawCircle(cx, cy, portraitRadius, glow)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    private fun haloVisible(): Boolean {
        if (isSelected || isFocused) return true
        val state = drawableState
        for (flag in state) {
            if (flag == android.R.attr.state_selected || flag == android.R.attr.state_focused) return true
        }
        return false
    }
}

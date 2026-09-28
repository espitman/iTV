package app.itv.prototype.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import app.itv.prototype.R

/** Home iTV mark: cyan rounded square with i, then white TV. `logoSize` is the mark edge. */
class ItvLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val mark: TextView
    private val wordmark: TextView

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
    }

    private fun applySize(sizePx: Int) {
        val edge = sizePx.coerceAtLeast(1)
        mark.layoutParams = LayoutParams(edge, edge)
        mark.setTextSize(TypedValue.COMPLEX_UNIT_PX, edge * 17f / 24f)
        mark.background = GradientDrawable().apply {
            setColor(context.getColor(R.color.cyan))
            cornerRadius = edge * 7f / 24f
        }
        wordmark.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = (edge * 2f / 24f).toInt().coerceAtLeast(1)
        }
        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_PX, edge * 22f / 24f)
    }
}

package app.itv.prototype.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Bottom-aligned hero wash. Multi-stop ease avoids the 3-color XML center kink. */
class HeroBottomFadeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat(),
            intArrayOf(
                0x00080F14.toInt(),
                0x0A080F14.toInt(),
                0x1F080F14.toInt(),
                0x47080F14.toInt(),
                0x7A080F14.toInt(),
                0xB3080F14.toInt(),
                0xE0080F14.toInt(),
                0xF7080F14.toInt(),
                0xFF080F14.toInt(),
            ),
            floatArrayOf(0f, 0.10f, 0.22f, 0.38f, 0.54f, 0.70f, 0.84f, 0.93f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

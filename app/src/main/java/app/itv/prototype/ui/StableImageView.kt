package app.itv.prototype.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView

/** ImageView that never remeasures after the first layout, so bitmap loads cannot shift shelves. */
class StableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ImageView(context, attrs, defStyleAttr) {
    init {
        adjustViewBounds = false
    }

    override fun requestLayout() {
        if (isLaidOut && width > 0 && height > 0) {
            invalidate()
            return
        }
        super.requestLayout()
    }
}

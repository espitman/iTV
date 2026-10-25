package app.itv.prototype.ui

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import app.itv.prototype.Pictures
import app.itv.prototype.R

/**
 * Loads a cover bitmap behind a shimmer block. The block is shown until the
 * bitmap is ready and hidden both on success and on a missing/failed image, so
 * the item's own placeholder or fallback art is revealed instead of a stuck
 * skeleton. The overlay is reused when a GridView recycles its item view.
 */
object CoverSkeleton {
    fun load(
        url: String?,
        target: ImageView,
        placeholder: Int,
        radiusDp: Float = 8f,
        onReady: ((Bitmap) -> Unit)? = null,
    ) {
        val overlay = obtainOverlay(target, radiusDp)
        val resolved = Pictures.episodeUrl(url)
        // A recycled view may still hold the previous item's bitmap. Reset it to
        // the placeholder whenever the requested cover changed, so a failed load
        // reveals the fallback instead of the stale image.
        if (resolved == null || target.tag != resolved) {
            target.setImageResource(placeholder)
        }
        overlay?.visibility = if (resolved != null) View.VISIBLE else View.GONE
        Pictures.load(
            url,
            target,
            placeholder,
            onReady = { bitmap ->
                overlay?.visibility = View.GONE
                onReady?.invoke(bitmap)
            },
            onUnavailable = {
                overlay?.visibility = View.GONE
                target.setImageResource(placeholder)
            },
        )
    }

    private fun obtainOverlay(target: ImageView, radiusDp: Float): SkeletonBlockView? {
        val parent = target.parent as? ViewGroup ?: return null
        parent.findViewById<SkeletonBlockView>(R.id.coverSkeleton)?.let { return it }
        val overlay = SkeletonBlockView(target.context).apply {
            id = R.id.coverSkeleton
            setRadiusDp(radiusDp)
        }
        parent.addView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return overlay
    }
}

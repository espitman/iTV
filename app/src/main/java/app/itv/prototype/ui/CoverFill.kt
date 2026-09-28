package app.itv.prototype.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Fills letterboxed Home poster cards with a darkened, blurred copy of the
 * same loaded bitmap. Portrait covers that already fit the 2:3 frame skip this.
 */
object CoverFill {
    const val MAX_SIDE = 48
    const val BLUR_RADIUS = 7
    const val GPU_BLUR_RADIUS = 16f
    const val DIM = 0.72f

    private val worker by lazy { Executors.newFixedThreadPool(2) }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val blurred by lazy { LruCache<String, Bitmap>(48) }
    private val dimFilter by lazy {
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    DIM, 0f, 0f, 0f, 0f,
                    0f, DIM, 0f, 0f, 0f,
                    0f, 0f, DIM, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    fun downscaleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val longest = maxOf(w, h)
        if (longest <= maxSide) return w to h
        val scale = maxSide.toFloat() / longest
        return (w * scale).roundToInt().coerceAtLeast(1) to
            (h * scale).roundToInt().coerceAtLeast(1)
    }

    fun clear(fill: ImageView?) {
        fill ?: return
        fill.tag = null
        fill.colorFilter = null
        if (Build.VERSION.SDK_INT >= 31) fill.setRenderEffect(null)
        fill.setImageDrawable(null)
        fill.visibility = View.GONE
    }

    fun bind(fill: ImageView?, source: Bitmap, token: Any?) {
        val key = token as? String
        if (fill == null || key.isNullOrBlank() || source.isRecycled) {
            clear(fill)
            return
        }
        fill.tag = key
        if (Build.VERSION.SDK_INT >= 31) {
            show(fill, source, gpu = true)
            return
        }
        blurred.get(key)?.takeUnless { it.isRecycled }?.let {
            show(fill, it, gpu = false)
            return
        }
        worker.execute {
            val prepared = runCatching { blurDownscaled(source) }.getOrNull()
            if (prepared != null) blurred.put(key, prepared)
            main.post {
                if (fill.tag != key) return@post
                show(fill, prepared ?: source, gpu = false)
            }
        }
    }

    private fun show(fill: ImageView, bitmap: Bitmap, gpu: Boolean) {
        if (bitmap.isRecycled) return
        fill.adjustViewBounds = false
        fill.scaleType = ImageView.ScaleType.CENTER_CROP
        fill.colorFilter = dimFilter
        if (Build.VERSION.SDK_INT >= 31) {
            fill.setRenderEffect(
                if (gpu) {
                    RenderEffect.createBlurEffect(GPU_BLUR_RADIUS, GPU_BLUR_RADIUS, Shader.TileMode.CLAMP)
                } else {
                    null
                },
            )
        }
        fill.setImageBitmap(bitmap)
        fill.visibility = View.VISIBLE
    }

    private fun blurDownscaled(source: Bitmap): Bitmap {
        val (width, height) = downscaleSize(source.width, source.height)
        val scaled = try {
            Bitmap.createScaledBitmap(source, width, height, true)
        } catch (_: RuntimeException) {
            val copy = source.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("software copy")
            Bitmap.createScaledBitmap(copy, width, height, true)
        }
        val mutable = if (scaled !== source && scaled.isMutable && scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, true) ?: throw IllegalStateException("mutable copy")
        }
        blurBitmap(mutable, BLUR_RADIUS)
        return mutable
    }

    internal fun blurArgb(pixels: IntArray, width: Int, height: Int, radius: Int) {
        if (radius < 1 || width <= 0 || height <= 0 || pixels.size < width * height) return
        val scratch = IntArray(width * height)
        blurHorizontal(pixels, scratch, width, height, radius)
        blurVertical(scratch, pixels, width, height, radius)
        blurHorizontal(pixels, scratch, width, height, radius)
        blurVertical(scratch, pixels, width, height, radius)
    }

    private fun blurBitmap(bitmap: Bitmap, radius: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        blurArgb(pixels, width, height, radius)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun blurHorizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val div = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                val left = x - radius
                val right = x + radius
                for (xx in left..right) {
                    val color = src[row + xx.coerceIn(0, width - 1)]
                    a += color ushr 24
                    r += (color shr 16) and 0xFF
                    g += (color shr 8) and 0xFF
                    b += color and 0xFF
                }
                dst[row + x] = (a / div shl 24) or (r / div shl 16) or (g / div shl 8) or (b / div)
            }
        }
    }

    private fun blurVertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
        val div = radius * 2 + 1
        val lastRow = height - 1
        for (x in 0 until width) {
            for (y in 0 until height) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                val top = y - radius
                val bottom = y + radius
                for (yy in top..bottom) {
                    val color = src[yy.coerceIn(0, lastRow) * width + x]
                    a += color ushr 24
                    r += (color shr 16) and 0xFF
                    g += (color shr 8) and 0xFF
                    b += color and 0xFF
                }
                dst[y * width + x] = (a / div shl 24) or (r / div shl 16) or (g / div shl 8) or (b / div)
            }
        }
    }
}

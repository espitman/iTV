package app.itv.prototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import app.itv.prototype.data.TelewebionClient
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Pictures {
    private val cache = LruCache<String, Bitmap>(64)
    private val sizes = LruCache<String, Pair<Int, Int>>(128)
    private val worker = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private var diskDir: File? = null

    fun install(context: Context) {
        diskDir = File(context.cacheDir, "pictures").also { it.mkdirs() }
    }

    fun episodeUrl(image: String?): String? {
        val id = image?.trim().orEmpty()
        if (id.isEmpty() || id == "null") return null
        if (id.startsWith("http")) return id
        return "https://static.telewebion.net/episodeImages/$id/default"
    }

    fun sizeOf(url: String?): Pair<Int, Int>? {
        val resolved = episodeUrl(url) ?: return null
        cache.get(resolved)?.let { return it.width to it.height }
        return sizes.get(resolved)
    }

    fun load(url: String?, target: ImageView, placeholder: Int? = null, onReady: ((Bitmap) -> Unit)? = null) {
        val resolved = episodeUrl(url)
        if (resolved == null) {
            target.tag = null
            placeholder?.let { target.setImageResource(it) } ?: target.setImageDrawable(null)
            return
        }
        fun apply(bitmap: Bitmap) {
            remember(resolved, bitmap)
            if (target.tag == resolved) {
                target.setImageBitmap(bitmap)
                onReady?.invoke(bitmap)
            }
        }
        if (target.tag == resolved && target.drawable != null && onReady == null) return
        target.tag = resolved
        cache.get(resolved)?.let {
            apply(it)
            return
        }
        if (target.drawable == null) {
            placeholder?.let { target.setImageResource(it) }
        }
        worker.execute {
            val bitmap = runCatching { read(resolved) }.getOrNull() ?: return@execute
            cache.put(resolved, bitmap)
            remember(resolved, bitmap)
            main.post { apply(bitmap) }
        }
    }

    private fun remember(url: String, bitmap: Bitmap) {
        sizes.put(url, bitmap.width to bitmap.height)
    }

    private fun read(url: String): Bitmap {
        val file = diskDir?.let { File(it, url.hashCode().toUInt().toString()) }
        if (file != null && file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        }
        val bitmap = download(url)
        remember(url, bitmap)
        if (file != null) runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        return bitmap
    }

    private fun download(url: String): Bitmap {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", TelewebionClient.USER_AGENT)
        try {
            check(connection.responseCode in 200..299)
            return connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream) ?: error("decode")
            }
        } finally {
            connection.disconnect()
        }
    }
}

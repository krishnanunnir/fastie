package app.kino

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Posters and backdrops: a memory LRU over a disk cache in cacheDir (Android trims it when space runs low). */
object Images {
    private val mem = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val io = Executors.newFixedThreadPool(4)

    /**
     * Decodes [url] at about [width] px into [view]; [alpha] keeps transparency (logos), else RGB_565 halves memory.
     * [transform] runs on the decode thread before caching (under [variant] in the cache key), such as baking scrims in.
     */
    fun load(
        view: ImageView, url: String?, width: Int, alpha: Boolean = false,
        transform: ((Bitmap) -> Bitmap)? = null, variant: String = "", done: ((Boolean) -> Unit)? = null,
    ) {
        view.tag = url
        if (url == null) { view.setImageDrawable(null); done?.invoke(false); return }
        val key = "$url@$width$variant"
        mem.get(key)?.let { view.setImageBitmap(it); done?.invoke(true); return }
        view.setImageDrawable(null)
        val ctx = view.context.applicationContext
        io.execute {
            val bmp = runCatching { decode(ctx, url, width, alpha)?.let { transform?.invoke(it) ?: it } }.getOrNull()
            if (bmp != null) mem.put(key, bmp)
            Net.main.post {
                if (view.tag != url) return@post
                if (bmp != null) {
                    view.alpha = 0f
                    view.setImageBitmap(bmp)
                    view.animate().alpha(1f).setDuration(180).start()
                }
                done?.invoke(bmp != null)
            }
        }
    }

    private fun decode(ctx: Context, url: String, width: Int, alpha: Boolean): Bitmap? {
        val dir = File(ctx.cacheDir, "img").apply { mkdirs() }
        val file = File(dir, Integer.toHexString(url.hashCode()) + url.length)
        if (!file.exists()) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            try {
                if (c.responseCode >= 400) return null
                val tmp = File(dir, file.name + ".part")
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            } finally {
                c.disconnect()
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (alpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }) ?: return null
        return if (alpha) trim(bmp) else bmp
    }

    /** Crops fully transparent margins, so a logo's ink starts where the text column starts. */
    private fun trim(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] ushr 24 > 32) { // ignore faint halos and shadows
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (right < left || (left == 0 && top == 0 && right == w - 1 && bottom == h - 1)) return b
        return Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1)
    }
}

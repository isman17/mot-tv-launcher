package com.mottv.launcher

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Tiny async image loader with an in-memory cache (badges, league logos,
 * result thumbnails). No library — just HttpURLConnection + BitmapFactory.
 * Failures leave the caller's placeholder in place; nothing is faked.
 */
object ImgCache {
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, Bitmap>()

    fun load(url: String?, into: ImageView) {
        if (url.isNullOrBlank()) return
        synchronized(cache) {
            cache[url]?.let { into.setImageBitmap(it); return }
        }
        val ref = WeakReference(into)
        into.tag = url
        io.execute {
            val bmp = Net.getBitmap(url)
            if (bmp != null) {
                synchronized(cache) { cache[url] = bmp }
                main.post {
                    val view = ref.get() ?: return@post
                    // Only apply if the view hasn't been recycled for another URL.
                    if (view.tag == url) view.setImageBitmap(bmp)
                }
            }
        }
    }
}

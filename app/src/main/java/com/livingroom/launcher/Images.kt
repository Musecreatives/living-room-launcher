package com.livingroom.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL

// ponytail: in-memory only; a few dozen artworks fit easily. Add a disk cache if boot-time loads feel slow.
private val cache =
    object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(
            key: String,
            value: Bitmap,
        ) = value.byteCount
    }

/** Downloads artwork (blocking): call off the main thread. Returns null on any failure. */
fun loadImage(url: String?): Bitmap? {
    if (url == null) return null
    cache.get(url)?.let { return it }
    return try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 6000
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) }?.also { cache.put(url, it) }
    } catch (ex: Exception) {
        Log.w("Images", "Artwork failed: ${ex.message}")
        null
    }
}

/**
 * The "glass" wallpaper: Android 10 has no live blur, so blur once by shrinking to a few dozen pixels
 * and letting bilinear filtering spread it back out. Cheap enough for this box's CPU.
 */
fun blurred(source: Bitmap): Bitmap {
    val small = Bitmap.createScaledBitmap(source, 32, (32f * source.height / source.width).toInt().coerceAtLeast(1), true)
    val mid = Bitmap.createScaledBitmap(small, 160, (160f * source.height / source.width).toInt().coerceAtLeast(1), true)
    return Bitmap.createScaledBitmap(mid, 640, (640f * source.height / source.width).toInt().coerceAtLeast(1), true)
}

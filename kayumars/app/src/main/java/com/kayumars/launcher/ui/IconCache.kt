package com.kayumars.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Two-level high-performance icon cache:
 * 1. In-memory LruCache (~8 MB)
 * 2. Disk cache in private storage
 *
 * Heavy decoding is strictly offloaded to Dispatchers.IO.
 */
class IconCache(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val diskDir = File(context.cacheDir, "icons").apply { mkdirs() }

    // Memory cache sized to ~8MB (approx 128 icons @ 64KB each)
    private val memCache = object : LruCache<String, Bitmap>(128) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return 1
        }
    }

    fun loadIconAsync(packageName: String, onLoaded: (Bitmap) -> Unit) {
        // Fast path: memory hit
        val cached = memCache.get(packageName)
        if (cached != null) {
            onLoaded(cached)
            return
        }

        scope.launch {
            // Check disk cache
            val diskFile = File(diskDir, "$packageName.png")
            if (diskFile.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    memCache.put(packageName, bmp)
                    withContext(Dispatchers.Main) {
                        onLoaded(bmp)
                    }
                    return@launch
                }
            }

            // Extract and render icon from PackageManager
            val resolvedBmp = extractAppIcon(packageName)
            if (resolvedBmp != null) {
                memCache.put(packageName, resolvedBmp)
                saveToDisk(diskFile, resolvedBmp)
                withContext(Dispatchers.Main) {
                    onLoaded(resolvedBmp)
                }
            }
        }
    }

    private fun extractAppIcon(packageName: String): Bitmap? {
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            renderDrawableToBitmap(drawable, 96, 96)
        } catch (_: Exception) {
            null
        }
    }

    private fun renderDrawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun saveToDisk(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (_: Exception) {
            // Non-critical cache failure
        }
    }
}

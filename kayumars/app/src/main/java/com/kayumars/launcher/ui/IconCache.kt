package com.kayumars.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Two-level icon cache that also normalises the silhouette.
 *
 * Every icon is flattened (adaptive fg over bg) and masked with the iOS
 * continuous-corner squircle before it reaches a view, so a circle-masked
 * Chrome and a square legacy icon render with the same shape everywhere.
 * Masked bitmaps are cached in memory and on disk, keyed by shape.
 */
class IconCache(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    // v2: bitmaps are masked here, so pre-mask files from older builds must not
    // be reused (they were clipped by the view outline instead).
    private val diskDir = File(context.cacheDir, "icons_v2").apply { mkdirs() }

    init {
        File(context.cacheDir, "icons").deleteRecursively()
    }

    @Volatile
    private var shape = "squircle"

    // Memory cache sized to ~8MB (approx 128 icons @ 64KB each)
    private val memCache = object : LruCache<String, Bitmap>(160) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return 1
        }
    }

    fun setShape(shape: String) {
        if (this.shape == shape) return
        this.shape = shape
        memCache.evictAll()
    }

    fun loadIconAsync(packageName: String, onLoaded: (Bitmap) -> Unit) {
        val key = "$packageName@$shape"
        val cached = memCache.get(key)
        if (cached != null) {
            onLoaded(cached)
            return
        }

        scope.launch {
            val diskFile = File(diskDir, "$packageName-$shape.png")
            if (diskFile.exists() && !isStale(packageName, diskFile)) {
                val bmp = android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    memCache.put(key, bmp)
                    withContext(Dispatchers.Main) { onLoaded(bmp) }
                    return@launch
                }
            }

            val resolvedBmp = extractAppIcon(packageName)
            if (resolvedBmp != null) {
                memCache.put(key, resolvedBmp)
                saveToDisk(diskFile, resolvedBmp)
                withContext(Dispatchers.Main) { onLoaded(resolvedBmp) }
            }
        }
    }

    fun invalidate(packageName: String) {
        val keys = memCache.snapshot().keys.filter { it.startsWith("$packageName@") }
        keys.forEach { memCache.remove(it) }
        diskDir.listFiles()?.forEach {
            if (it.name.startsWith("$packageName-")) it.delete()
        }
    }

    private fun isStale(packageName: String, diskFile: File): Boolean {
        return try {
            val updated = context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            updated > diskFile.lastModified()
        } catch (_: Exception) {
            true
        }
    }

    private fun extractAppIcon(packageName: String): Bitmap? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = (context.resources.displayMetrics.density * 72f).toInt().coerceIn(96, 216)
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val cc = Canvas(content)
            if (drawable is AdaptiveIconDrawable && shape != "system") {
                // Draw the layers ourselves: AdaptiveIconDrawable.draw() applies the
                // OEM mask, which is why Chrome read as a circle and Files as a square.
                val bg = drawable.background
                val fg = drawable.foreground
                if (bg != null) {
                    bg.setBounds(0, 0, size, size)
                    bg.draw(cc)
                }
                if (fg != null) {
                    fg.setBounds(0, 0, size, size)
                    fg.draw(cc)
                }
                if (bg == null && fg == null) {
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(cc)
                }
            } else {
                drawable.setBounds(0, 0, size, size)
                drawable.draw(cc)
            }
            canvas.drawBitmap(content, 0f, 0f, null)
            content.recycle()
            mask(canvas, size)
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun mask(canvas: Canvas, size: Int) {
        when (shape) {
            "system" -> return
            "circle" -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            }
            "rounded_square" -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), size * 0.16f, size * 0.16f, paint)
            }
            else -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                canvas.drawPath(Squircle.path(size.toFloat()), paint)
            }
        }
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

package com.kayumars.launcher.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.ImageView
import kotlin.math.max

/**
 * Backdrop blur for the overlay surfaces (Spotlight, folders, menus, overview,
 * widget picker).
 *
 * API 31+: the home workspace itself gets a single RenderEffect blur — the real
 *          thing, no snapshot cost.
 * API < 31: one snapshot of the workspace, downscaled and box-blurred, shown in
 *          its place under the overlay tint.
 *
 * Callers only need [show] / [hide]; overlapping overlays are balanced by the
 * owner (MainActivity.refreshBackdrop), not here.
 */
class BackdropBlur(
    private val activity: Activity,
    private val source: View,
    private val target: ImageView
) {
    private var active = false

    fun show() {
        if (active) return
        active = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = 22f * activity.resources.displayMetrics.density
            source.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            return
        }
        val snapshot = captureAndBlur()
        if (snapshot == null) {
            active = false
            return
        }
        target.setImageBitmap(snapshot)
        target.visibility = View.VISIBLE
        source.visibility = View.INVISIBLE
    }

    fun hide() {
        if (!active) return
        active = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            source.setRenderEffect(null)
        } else {
            target.visibility = View.GONE
            target.setImageDrawable(null)
            source.visibility = View.VISIBLE
        }
    }

    private fun captureAndBlur(): Bitmap? {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return null
        val scale = 1f / 5f
        val sw = max(1, (w * scale).toInt())
        val sh = max(1, (h * scale).toInt())
        return try {
            val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(small)
            canvas.scale(sw.toFloat() / w, sh.toFloat() / h)
            source.draw(canvas)
            boxBlur(small, 3)
            boxBlur(small, 3)
            small
        } catch (_: Exception) {
            null
        }
    }

    /** In-place separable box blur; alpha-weighted so icon edges do not smear grey. */
    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)

        fun pass(input: IntArray, output: IntArray, horizontal: Boolean) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                for (i in -radius..radius) {
                    val idx = if (horizontal) o * w + i.coerceIn(0, inner - 1)
                    else i.coerceIn(0, inner - 1) * w + o
                    val c = input[idx]
                    a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                    count++
                }
                for (i in 0 until inner) {
                    val outIdx = if (horizontal) o * w + i else i * w + o
                    output[outIdx] = ((a / count) shl 24) or ((r / count) shl 16) or
                        ((g / count) shl 8) or (b / count)
                    val add = (i + radius + 1).coerceAtMost(inner - 1)
                    val remove = (i - radius).coerceAtLeast(0)
                    val addIdx = if (horizontal) o * w + add else add * w + o
                    val remIdx = if (horizontal) o * w + remove else remove * w + o
                    val ac = input[addIdx]; val rc = input[remIdx]
                    a += (ac ushr 24) - (rc ushr 24)
                    r += ((ac shr 16) and 0xFF) - ((rc shr 16) and 0xFF)
                    g += ((ac shr 8) and 0xFF) - ((rc shr 8) and 0xFF)
                    b += (ac and 0xFF) - (rc and 0xFF)
                }
            }
        }

        pass(src, tmp, true)
        pass(tmp, src, false)
        bitmap.setPixels(src, 0, w, 0, 0, w, h)
    }
}

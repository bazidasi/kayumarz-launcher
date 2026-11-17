package com.kayumars.launcher.ui

import android.graphics.Path

/**
 * Apple continuous-corner ("squircle") path.
 *
 * |x|^n + |y|^n = 1 with n≈5 matches the iOS icon silhouette far better than a
 * circular-arc rounded rect. Sampled as a polygon: at icon sizes the error is
 * sub-pixel and the path stays cheap.
 */
object Squircle {

    fun path(size: Float, n: Float = 5f, segments: Int = 96): Path {
        val path = Path()
        val r = size / 2f
        val steps = segments.coerceAtLeast(16)
        for (i in 0..steps) {
            val t = (Math.PI / 2) * i / steps
            val cos = Math.cos(t).toFloat()
            val sin = Math.sin(t).toFloat()
            val x = r * Math.pow(cos.toDouble(), 2.0 / n).toFloat()
            val y = r * Math.pow(sin.toDouble(), 2.0 / n).toFloat()
            when (i) {
                0 -> path.moveTo(r + x, r - y)
                else -> path.lineTo(r + x, r - y)
            }
        }
        // Mirror the sampled quadrant around the four axes.
        for (i in steps downTo 0) {
            val t = (Math.PI / 2) * i / steps
            val cos = Math.cos(t).toFloat()
            val sin = Math.sin(t).toFloat()
            val x = r * Math.pow(cos.toDouble(), 2.0 / n).toFloat()
            val y = r * Math.pow(sin.toDouble(), 2.0 / n).toFloat()
            path.lineTo(r - x, r - y)
        }
        for (i in 0..steps) {
            val t = (Math.PI / 2) * i / steps
            val cos = Math.cos(t).toFloat()
            val sin = Math.sin(t).toFloat()
            val x = r * Math.pow(cos.toDouble(), 2.0 / n).toFloat()
            val y = r * Math.pow(sin.toDouble(), 2.0 / n).toFloat()
            path.lineTo(r - x, r + y)
        }
        for (i in steps downTo 0) {
            val t = (Math.PI / 2) * i / steps
            val cos = Math.cos(t).toFloat()
            val sin = Math.sin(t).toFloat()
            val x = r * Math.pow(cos.toDouble(), 2.0 / n).toFloat()
            val y = r * Math.pow(sin.toDouble(), 2.0 / n).toFloat()
            path.lineTo(r + x, r + y)
        }
        path.close()
        return path
    }
}

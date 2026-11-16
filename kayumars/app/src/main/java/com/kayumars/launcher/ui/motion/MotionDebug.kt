package com.kayumars.launcher.ui.motion

import android.view.Choreographer
import android.view.View
import android.widget.TextView

/**
 * MotionDebug — Lightweight Choreographer.FrameCallback frame-time HUD.
 * Dormant with zero overhead unless explicitly enabled in Settings.
 */
class MotionDebug(private val hudView: TextView?) : Choreographer.FrameCallback {

    private var enabled = false
    private var lastFrameNs = 0L
    private var droppedFrames = 0
    private var frameCount = 0

    fun setEnabled(enable: Boolean) {
        if (enabled == enable) return
        enabled = enable
        if (hudView == null) return
        if (enable) {
            lastFrameNs = 0L
            droppedFrames = 0
            frameCount = 0
            hudView.visibility = View.VISIBLE
            hudView.text = "120Hz HUD · 0 drop"
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            Choreographer.getInstance().removeFrameCallback(this)
            hudView.visibility = View.GONE
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!enabled) return
        if (lastFrameNs != 0L) {
            val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000f
            frameCount++
            if (deltaMs > 16.9f) {
                droppedFrames++
            }
            if (frameCount % 6 == 0 && hudView != null) {
                hudView.text = String.format("%.1fms | drop:%d", deltaMs, droppedFrames)
            }
        }
        lastFrameNs = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        enabled = false
        Choreographer.getInstance().removeFrameCallback(this)
    }
}

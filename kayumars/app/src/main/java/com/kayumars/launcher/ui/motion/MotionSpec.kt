package com.kayumars.launcher.ui.motion

import android.animation.Animator
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import java.lang.ref.WeakReference

/**
 * MotionSpec — Central timing, easing, stagger, and accessibility token registry
 * for Kayumars (کیومرث) 120 Hz physics-driven UI.
 */
object MotionSpec {

    // Duration tokens (ms) — total choreography never exceeds 450 ms
    const val DURATION_INSTANT_MS = 80L
    const val DURATION_FAST_MS = 140L
    const val DURATION_TAB_PILL_MS = 180L
    const val DURATION_STANDARD_MS = 200L
    const val DURATION_EMPHASIZED_MS = 280L
    const val DURATION_MAX_CHOREOGRAPHY_MS = 450L
    const val DURATION_SHIMMER_CYCLE_MS = 600L

    // Stagger tokens
    const val STAGGER_STEP_MS = 15L
    const val STAGGER_MAX_ITEMS = 12
    const val STAGGER_MAX_SPAN_MS = 150L
    const val FIRST_PAINT_CAP_MS = 300L

    // Spatial distance tokens (dp)
    const val RISE_DISTANCE_DP = 12f
    const val CLOCK_DROP_DP = 8f
    const val TAB_SHIFT_DP = 8f
    const val BLUR_RADIUS_DP = 16f

    // Jiggle Edit-Mode Tokens
    const val JIGGLE_ANGLE_DEG = 1.5f
    const val JIGGLE_PERIOD_MS = 350L
    const val JIGGLE_STAGGER_MS = 40L

    // Material 3 / Physics easing curves
    val STANDARD: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    val EMPHASIZED_DECELERATE: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val EMPHASIZED_ACCELERATE: Interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    private val activeAnimators = ArrayList<WeakReference<Animator>>(16)

    @JvmStatic
    fun dp(context: Context, valueDp: Float): Float {
        return valueDp * context.resources.displayMetrics.density
    }

    @JvmStatic
    fun isReducedMotion(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale <= 0f
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun isPowerSaveMode(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun track(animator: Animator) {
        pruneFinished()
        activeAnimators.add(WeakReference(animator))
    }

    @JvmStatic
    fun cancelAll() {
        for (ref in activeAnimators) {
            ref.get()?.cancel()
        }
        activeAnimators.clear()
        SpringPreset.cancelAll()
    }

    private fun pruneFinished() {
        val it = activeAnimators.iterator()
        while (it.hasNext()) {
            val anim = it.next().get()
            if (anim == null || !anim.isRunning) {
                it.remove()
            }
        }
    }
}

package com.kayumars.launcher.ui.motion

import android.view.View
import android.view.ViewGroup

/**
 * GridStaggerAnimator — Staggered 12 dp rise + alpha reveal for grid and list children.
 * Respects reduced motion and caps total stagger at 12 items / 150 ms.
 */
object GridStaggerAnimator {

    @JvmStatic
    fun animateEntrance(
        container: ViewGroup,
        riseDp: Float = MotionSpec.RISE_DISTANCE_DP,
        durationMs: Long = MotionSpec.DURATION_STANDARD_MS,
        baseDelayMs: Long = 0L
    ) {
        val count = container.childCount
        if (count == 0) return

        val reduced = MotionSpec.isReducedMotion(container.context)
        val risePx = MotionSpec.dp(container.context, riseDp)
        val stepCount = minOf(count, MotionSpec.STAGGER_MAX_ITEMS)

        for (i in 0 until count) {
            val child = container.getChildAt(i) ?: continue
            child.animate().cancel()
            if (reduced) {
                child.alpha = 1f
                child.translationY = 0f
                continue
            }

            val stagger = if (i < stepCount) {
                minOf(i * MotionSpec.STAGGER_STEP_MS, MotionSpec.STAGGER_MAX_SPAN_MS)
            } else {
                MotionSpec.STAGGER_MAX_SPAN_MS
            }

            child.alpha = 0f
            child.translationY = risePx
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(baseDelayMs + stagger)
                .setDuration(durationMs)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .start()
        }
    }

    @JvmStatic
    fun animateTabSwitch(
        container: ViewGroup,
        swapContent: Runnable
    ) {
        val context = container.context
        if (MotionSpec.isReducedMotion(context)) {
            swapContent.run()
            return
        }

        val shiftPx = MotionSpec.dp(context, MotionSpec.TAB_SHIFT_DP)
        val count = container.childCount

        for (i in 0 until count) {
            val child = container.getChildAt(i) ?: continue
            child.animate().cancel()
            child.animate()
                .alpha(0f)
                .translationY(-shiftPx)
                .setDuration(MotionSpec.DURATION_FAST_MS)
                .setInterpolator(MotionSpec.EMPHASIZED_ACCELERATE)
                .start()
        }

        container.postDelayed({
            swapContent.run()
            val newCount = container.childCount
            for (i in 0 until newCount) {
                val child = container.getChildAt(i) ?: continue
                child.animate().cancel()
                child.alpha = 0f
                child.translationY = shiftPx
                val delay = minOf(i * MotionSpec.STAGGER_STEP_MS, MotionSpec.STAGGER_MAX_SPAN_MS)
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(MotionSpec.DURATION_STANDARD_MS)
                    .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                    .start()
            }
        }, 60L)
    }
}

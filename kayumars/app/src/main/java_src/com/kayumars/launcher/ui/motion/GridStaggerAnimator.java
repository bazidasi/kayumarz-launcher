package com.kayumars.launcher.ui.motion;

import android.view.View;
import android.view.ViewGroup;

/**
 * GridStaggerAnimator — Staggered 12 dp rise + alpha reveal for grid and list children.
 */
public final class GridStaggerAnimator {

    private GridStaggerAnimator() {}

    public static void animateEntrance(
            ViewGroup container,
            float riseDp,
            long durationMs,
            long baseDelayMs) {
        if (container == null) return;
        int count = container.getChildCount();
        if (count == 0) return;

        boolean reduced = MotionSpec.isReducedMotion(container.getContext());
        float risePx = MotionSpec.dp(container.getContext(), riseDp);
        int stepCount = Math.min(count, MotionSpec.STAGGER_MAX_ITEMS);

        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (child == null) continue;
            child.animate().cancel();
            if (reduced) {
                child.setAlpha(1f);
                child.setTranslationY(0f);
                continue;
            }
            long stagger = (i < stepCount)
                    ? Math.min(i * MotionSpec.STAGGER_STEP_MS, MotionSpec.STAGGER_MAX_SPAN_MS)
                    : MotionSpec.STAGGER_MAX_SPAN_MS;
            child.setAlpha(0f);
            child.setTranslationY(risePx);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(baseDelayMs + stagger)
                    .setDuration(durationMs)
                    .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                    .start();
        }
    }

    public static void animateTabSwitch(final ViewGroup container, final Runnable swapContent) {
        if (container == null || swapContent == null) return;
        if (MotionSpec.isReducedMotion(container.getContext())) {
            swapContent.run();
            return;
        }

        final float shiftPx = MotionSpec.dp(container.getContext(), MotionSpec.TAB_SHIFT_DP);
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (child == null) continue;
            child.animate().cancel();
            child.animate()
                    .alpha(0f)
                    .translationY(-shiftPx)
                    .setDuration(MotionSpec.DURATION_FAST_MS)
                    .setInterpolator(MotionSpec.EMPHASIZED_ACCELERATE)
                    .start();
        }

        container.postDelayed(new Runnable() {
            @Override
            public void run() {
                swapContent.run();
                container.post(new Runnable() {
                    @Override
                    public void run() {
                        int newCount = container.getChildCount();
                        for (int i = 0; i < newCount; i++) {
                            View child = container.getChildAt(i);
                            if (child == null) continue;
                            child.animate().cancel();
                            child.setAlpha(0f);
                            child.setTranslationY(shiftPx);
                            long delay = Math.min(i * MotionSpec.STAGGER_STEP_MS, MotionSpec.STAGGER_MAX_SPAN_MS);
                            child.animate()
                                    .alpha(1f)
                                    .translationY(0f)
                                    .setStartDelay(delay)
                                    .setDuration(MotionSpec.DURATION_STANDARD_MS)
                                    .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                                    .start();
                        }
                    }
                });
            }
        }, 60L);
    }
}

package com.kayumars.launcher.ui.motion;

import android.animation.Animator;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * MotionSpec — Central timing, easing, stagger, and accessibility token registry
 * for Kayumars (کیومرث) 120 Hz physics-driven UI.
 */
public final class MotionSpec {

    public static final long DURATION_INSTANT_MS = 80L;
    public static final long DURATION_FAST_MS = 140L;
    public static final long DURATION_TAB_PILL_MS = 180L;
    public static final long DURATION_STANDARD_MS = 200L;
    public static final long DURATION_EMPHASIZED_MS = 280L;
    public static final long DURATION_MAX_CHOREOGRAPHY_MS = 450L;
    public static final long DURATION_SHIMMER_CYCLE_MS = 600L;

    public static final long STAGGER_STEP_MS = 15L;
    public static final int STAGGER_MAX_ITEMS = 12;
    public static final long STAGGER_MAX_SPAN_MS = 150L;
    public static final long FIRST_PAINT_CAP_MS = 300L;

    public static final float RISE_DISTANCE_DP = 12f;
    public static final float CLOCK_DROP_DP = 8f;
    public static final float TAB_SHIFT_DP = 8f;
    public static final float BLUR_RADIUS_DP = 16f;

    // iOS Material Blur Radii & Jiggle Edit-Mode Tokens
    public static final float BLUR_ULTRA_THIN_DP = 24f;
    public static final float BLUR_CHROME_DP = 30f;
    public static final float BLUR_REGULAR_DP = 18f;
    public static final float BLUR_MENU_DP = 20f;
    public static final float BLUR_THIN_DP = 16f;
    public static final float JIGGLE_ANGLE_DEG = 1.5f;
    public static final long JIGGLE_PERIOD_MS = 350L;
    public static final long JIGGLE_STAGGER_MS = 40L;

    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private static final List<WeakReference<Animator>> ACTIVE_ANIMATORS =
            new ArrayList<WeakReference<Animator>>(16);

    private MotionSpec() {}

    public static float dp(Context context, float valueDp) {
        return valueDp * context.getResources().getDisplayMetrics().density;
    }

    public static boolean isReducedMotion(Context context) {
        try {
            float scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f);
            return scale <= 0f;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPowerSaveMode(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        } catch (Exception e) {
            return false;
        }
    }

    public static void track(Animator animator) {
        if (animator == null) return;
        pruneFinished();
        ACTIVE_ANIMATORS.add(new WeakReference<Animator>(animator));
    }

    public static void cancelAll() {
        for (WeakReference<Animator> ref : ACTIVE_ANIMATORS) {
            Animator anim = ref.get();
            if (anim != null) {
                anim.cancel();
            }
        }
        ACTIVE_ANIMATORS.clear();
        SpringPreset.cancelAll();
    }

    private static void pruneFinished() {
        Iterator<WeakReference<Animator>> it = ACTIVE_ANIMATORS.iterator();
        while (it.hasNext()) {
            Animator anim = it.next().get();
            if (anim == null || !anim.isRunning()) {
                it.remove();
            }
        }
    }
}

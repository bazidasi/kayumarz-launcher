package com.kayumars.launcher.ui.motion;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

/**
 * BlurGate — Cached device capability check for single-pass Android 12+ RenderEffect blur.
 */
public final class BlurGate {

    private static final String PREFS_NAME = "kayumars_prefs";
    private static final String PREF_BLUR_ENABLED = "drawer_blur_enabled";

    private static volatile Boolean cachedCapable = null;

    private BlurGate() {}

    public static boolean isDeviceCapable(Context context) {
        if (context == null) return false;
        Boolean cur = cachedCapable;
        if (cur != null) return cur;

        boolean capable = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                boolean lowRam = am != null && am.isLowRamDevice();
                capable = !lowRam;
            }
        } catch (Throwable ignored) {
            capable = false;
        }
        cachedCapable = capable;
        return capable;
    }

    public static boolean isBlurAllowed(Context context) {
        if (context == null) return false;
        if (!isDeviceCapable(context)) return false;
        if (MotionSpec.isPowerSaveMode(context)) return false;
        return isUserBlurEnabled(context);
    }

    public static boolean isUserBlurEnabled(Context context) {
        if (context == null) return true;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_BLUR_ENABLED, true);
    }

    public static void setUserBlurEnabled(Context context, boolean enabled) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_BLUR_ENABLED, enabled)
                .apply();
    }

    public static void applySinglePassBlur(View target, float radiusDp) {
        if (target == null) return;
        if (radiusDp > 0f && !Float.isNaN(radiusDp) && !Float.isInfinite(radiusDp)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && isBlurAllowed(target.getContext())) {
            float px = Math.max(1f, Math.min(48f, MotionSpec.dp(target.getContext(), radiusDp)));
            Api31BlurHelper.setBlur(target, px);
        } else {
            clearBlur(target);
        }
    }

    public static void applyUltraThin(View target) {
        applySinglePassBlur(target, MotionSpec.BLUR_ULTRA_THIN_DP);
    }

    public static void applyChrome(View target) {
        applySinglePassBlur(target, MotionSpec.BLUR_CHROME_DP);
    }

    public static void applyRegular(View target) {
        applySinglePassBlur(target, MotionSpec.BLUR_REGULAR_DP);
    }

    public static void applyMenu(View target) {
        applySinglePassBlur(target, MotionSpec.BLUR_MENU_DP);
    }

    public static void applyThin(View target) {
        applySinglePassBlur(target, MotionSpec.BLUR_THIN_DP);
    }

    public static void applyWallpaperBlur(View target, int blurDp) {
        if (blurDp <= 0) {
            clearBlur(target);
        } else {
            applySinglePassBlur(target, Math.min(16f, (float) blurDp));
        }
    }

    public static void clearBlur(View target) {
        if (target == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Api31BlurHelper.clearBlur(target);
        }
    }

    public static void invalidateCache() {
        cachedCapable = null;
    }

    private static final class Api31BlurHelper {
        static void setBlur(View v, float radiusPx) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (!Float.isNaN(radiusPx) && !Float.isInfinite(radiusPx) && radiusPx > 0.5f) {
                        v.setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP));
                    }
                } catch (Throwable ignored) {}
            }
        }

        static void clearBlur(View v) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    v.setRenderEffect(null);
                } catch (Throwable ignored) {}
            }
        }
    }
}

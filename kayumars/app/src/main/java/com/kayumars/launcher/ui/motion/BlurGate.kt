package com.kayumars.launcher.ui.motion

import android.app.ActivityManager
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * BlurGate — Cached device capability check for single-pass Android 12+ RenderEffect blur.
 *
 * Rules:
 * - Requires API >= 31, non-low-RAM device, not in Battery Saver, and user toggle enabled.
 * - Blur is applied ONCE when opening the drawer; only View.alpha is animated afterward.
 * - Falls back to clean 90% scrim when gated off.
 */
object BlurGate {

    private const val PREFS_NAME = "kayumars_prefs"
    private const val PREF_BLUR_ENABLED = "drawer_blur_enabled"

    @Volatile
    private var cachedCapable: Boolean? = null

    @JvmStatic
    fun isDeviceCapable(context: Context): Boolean {
        cachedCapable?.let { return it }
        val capable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            false
        } else {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val lowRam = am?.isLowRamDevice == true
            !lowRam
        }
        cachedCapable = capable
        return capable
    }

    @JvmStatic
    fun isBlurAllowed(context: Context): Boolean {
        if (!isDeviceCapable(context)) return false
        if (MotionSpec.isPowerSaveMode(context)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_BLUR_ENABLED, true)
    }

    @JvmStatic
    fun setUserBlurEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_BLUR_ENABLED, enabled)
            .apply()
    }

    @JvmStatic
    fun isUserBlurEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_BLUR_ENABLED, true)
    }

    @JvmStatic
    fun applySinglePassBlur(target: View, radiusDp: Float = MotionSpec.BLUR_RADIUS_DP) {
        if (radiusDp > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isBlurAllowed(target.context)) {
            val radiusPx = MotionSpec.dp(target.context, radiusDp).coerceAtLeast(1f)
            target.setRenderEffect(
                RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            )
        } else {
            clearBlur(target)
        }
    }

    @JvmStatic fun applyUltraThin(target: View) = applySinglePassBlur(target, MotionSpec.BLUR_ULTRA_THIN_DP)
    @JvmStatic fun applyChrome(target: View) = applySinglePassBlur(target, MotionSpec.BLUR_CHROME_DP)
    @JvmStatic fun applyRegular(target: View) = applySinglePassBlur(target, MotionSpec.BLUR_REGULAR_DP)
    @JvmStatic fun applyMenu(target: View) = applySinglePassBlur(target, MotionSpec.BLUR_MENU_DP)
    @JvmStatic fun applyThin(target: View) = applySinglePassBlur(target, MotionSpec.BLUR_THIN_DP)

    @JvmStatic
    fun applyWallpaperBlur(target: View, blurDp: Int) {
        if (blurDp <= 0) clearBlur(target)
        else applySinglePassBlur(target, blurDp.coerceAtMost(16).toFloat())
    }

    @JvmStatic
    fun clearBlur(target: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.setRenderEffect(null)
        }
    }

    @JvmStatic
    fun invalidateCache() {
        cachedCapable = null
    }
}

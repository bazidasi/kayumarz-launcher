package com.kayumars.launcher.ui.motion

import android.app.ActivityManager
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * BlurGate — user preference + platform check for material blur.
 *
 * Rules:
 * - The preference gates both the API 31+ RenderEffect and the API < 31 snapshot fallback.
 * - Battery Saver disables blur to protect frame time.
 * - [applySinglePassBlur] additionally requires API 31.
 * - Falls back to clean translucent scrims when gated off.
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
        return isUserBlurEnabled(context) && !MotionSpec.isPowerSaveMode(context)
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

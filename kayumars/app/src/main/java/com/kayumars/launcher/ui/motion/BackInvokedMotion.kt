package com.kayumars.launcher.ui.motion

import android.app.Activity
import android.os.Build
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * BackInvokedMotion — Predictive Back handler for Android 13/14+ (API 33/34+).
 *
 * Scales active front surface 1.0 -> 0.90 + fades 1.0 -> 0.72 during back gesture,
 * commits close with SNAP on completion, or restores to 1.0 on cancel.
 * Silently no-ops on API < 33 so legacy onBackPressed handles navigation.
 */
class BackInvokedMotion(
    private val activity: Activity,
    private val surfaceProvider: () -> View?,
    private val hasActiveOverlay: () -> Boolean,
    private val onCommitBack: () -> Unit
) {

    private var registeredCallback: Any? = null

    fun register() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        unregister()

        val dispatcher = activity.onBackInvokedDispatcher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cb = object : OnBackAnimationCallback {
                override fun onBackStarted(backEvent: BackEvent) {
                    if (!hasActiveOverlay()) return
                    surfaceProvider()?.let { v ->
                        v.pivotX = v.width * 0.5f
                        v.pivotY = v.height * 0.5f
                    }
                }

                override fun onBackProgressed(backEvent: BackEvent) {
                    if (!hasActiveOverlay()) return
                    val progress = backEvent.progress.coerceIn(0f, 1f)
                    surfaceProvider()?.let { v ->
                        val scale = 1f - 0.10f * progress
                        v.scaleX = scale
                        v.scaleY = scale
                        v.alpha = 1f - 0.28f * progress
                    }
                }

                override fun onBackInvoked() {
                    if (hasActiveOverlay()) {
                        onCommitBack()
                    }
                }

                override fun onBackCancelled() {
                    surfaceProvider()?.let { v ->
                        SpringPreset.animateScale(v, 1f, SpringPreset.SNAP)
                        v.animate()
                            .alpha(1f)
                            .setDuration(MotionSpec.DURATION_FAST_MS)
                            .setInterpolator(MotionSpec.STANDARD)
                            .start()
                    }
                }
            }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            registeredCallback = cb
        } else {
            val cb = OnBackInvokedCallback {
                if (hasActiveOverlay()) {
                    onCommitBack()
                }
            }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            registeredCallback = cb
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val cb = registeredCallback as? OnBackInvokedCallback ?: return
        try {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb)
        } catch (_: Exception) {
        }
        registeredCallback = null
    }
}

package com.kayumars.launcher.history

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import com.kayumars.launcher.bridge.Core

/**
 * Handles launching the selected application and recording the event in the Rust core history log,
 * including Scene F ActivityOptions launch handoff transitions.
 */
object LaunchLogger {
    @JvmOverloads
    fun launchApp(
        context: Context,
        packageName: String,
        sourceView: View? = null,
        useClipReveal: Boolean = false
    ): Boolean {
        Core.logLaunch(packageName, System.currentTimeMillis())

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return try {
            if (sourceView != null && sourceView.width > 0 && sourceView.height > 0) {
                val opts = if (useClipReveal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ActivityOptions.makeClipRevealAnimation(
                        sourceView, 0, 0, sourceView.width, sourceView.height
                    )
                } else {
                    ActivityOptions.makeScaleUpAnimation(
                        sourceView, 0, 0, sourceView.width, sourceView.height
                    )
                }
                context.startActivity(launchIntent, opts.toBundle())
            } else {
                context.startActivity(launchIntent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

package com.kayumars.launcher.pkg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for package additions, updates, or uninstalls. Registered dynamically by
 * MainActivity only; the callback owns the re-scan so exactly one scan runs per event.
 */
class PackageChangeReceiver(
    private val onPackageChanged: ((String) -> Unit)? = null
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                intent.data?.schemeSpecificPart?.let { onPackageChanged?.invoke(it) }
            }
        }
    }
}

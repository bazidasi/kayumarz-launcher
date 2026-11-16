package com.kayumars.launcher.pkg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for package additions, updates, or uninstalls and initiates background re-indexing.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                AppIndexer.scanAsync(context)
            }
        }
    }
}

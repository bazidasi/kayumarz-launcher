package com.kayumars.launcher.pkg

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.kayumars.launcher.bridge.AppEntry
import com.kayumars.launcher.bridge.Core
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scans installed launchable applications off the UI thread and pushes them into the Rust core index.
 */
object AppIndexer {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun scanAsync(context: Context, onComplete: ((List<AppEntry>) -> Unit)? = null) {
        scope.launch {
            val apps = scanInstalled(context.applicationContext)
            Core.replaceIndex(apps)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(apps)
            }
        }
    }

    fun scanInstalled(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(0L)
        } else {
            0
        }

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, flags as PackageManager.ResolveInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val myPackage = context.packageName
        val result = ArrayList<AppEntry>(resolved.size)

        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            // Skip the launcher itself
            if (pkg == myPackage) continue

            val label = ri.loadLabel(pm)?.toString() ?: pkg
            result.add(
                AppEntry(
                    pkg = pkg,
                    label = label,
                    userSerial = 0,
                    flags = ri.activityInfo.applicationInfo.flags
                )
            )
        }

        return result
    }
}

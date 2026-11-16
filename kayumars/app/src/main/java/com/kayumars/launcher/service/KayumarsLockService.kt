package com.kayumars.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class KayumarsLockService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero polling or tracking.
    }

    override fun onInterrupt() {
        instanceRef = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef = null
    }

    companion object {
        @Volatile
        private var instanceRef: WeakReference<KayumarsLockService>? = null

        @JvmStatic
        fun isEnabled(context: Context): Boolean {
            if (instanceRef?.get() != null) return true
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val cn = ComponentName(context, KayumarsLockService::class.java)
                enabled.contains(cn.flattenToString()) || enabled.contains(KayumarsLockService::class.java.simpleName)
            } catch (_: Exception) {
                false
            }
        }

        @JvmStatic
        fun lockScreenNow(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            val svc = instanceRef?.get() ?: return false
            return try {
                svc.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } catch (_: Exception) {
                false
            }
        }
    }
}

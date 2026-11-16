package com.kayumars.launcher.notify

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

class KayumarsNotificationListener : NotificationListenerService() {

    fun interface BadgeUpdateListener {
        fun onBadgesUpdated()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        rebuildCounts()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        rebuildCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        rebuildCounts()
    }

    private fun rebuildCounts() {
        try {
            val active = activeNotifications
            val next = HashMap<String, Int>()
            if (active != null) {
                for (sbn in active) {
                    if (sbn == null || sbn.isOngoing) continue
                    val pkg = sbn.packageName ?: continue
                    if (pkg.isEmpty()) continue
                    next[pkg] = (next[pkg] ?: 0) + 1
                }
            }
            counts.clear()
            counts.putAll(next)
            listener?.onBadgesUpdated()
        } catch (_: Exception) {}
    }

    companion object {
        private val counts = ConcurrentHashMap<String, Int>()
        @Volatile
        private var listener: BadgeUpdateListener? = null

        @JvmStatic
        fun setBadgeUpdateListener(l: BadgeUpdateListener?) {
            listener = l
        }

        @JvmStatic
        fun getBadgeCount(pkg: String?, badgesEnabled: Boolean, mutedPkgs: Set<String>?): Int {
            if (!badgesEnabled || pkg == null) return 0
            if (mutedPkgs != null && mutedPkgs.contains(pkg)) return 0
            return (counts[pkg] ?: 0).coerceAtLeast(0)
        }

        @JvmStatic
        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                val cn = ComponentName(context, KayumarsNotificationListener::class.java)
                flat.contains(cn.flattenToString()) || flat.contains(context.packageName)
            } catch (_: Exception) {
                false
            }
        }
    }
}

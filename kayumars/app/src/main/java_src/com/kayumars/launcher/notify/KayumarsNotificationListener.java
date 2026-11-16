package com.kayumars.launcher.notify;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KayumarsNotificationListener — In-memory local notification counter for iOS 18dp badges.
 * Zero persistence, zero network permissions, zero telemetry.
 */
public class KayumarsNotificationListener extends NotificationListenerService {

    public interface BadgeUpdateListener {
        void onBadgesUpdated();
    }

    private static final ConcurrentHashMap<String, Integer> COUNTS = new ConcurrentHashMap<String, Integer>();
    private static volatile BadgeUpdateListener listener = null;

    public static void setBadgeUpdateListener(BadgeUpdateListener l) {
        listener = l;
    }

    public static int getBadgeCount(String pkg, boolean badgesEnabled, Set<String> mutedPkgs) {
        if (!badgesEnabled || pkg == null) return 0;
        if (mutedPkgs != null && mutedPkgs.contains(pkg)) return 0;
        Integer count = COUNTS.get(pkg);
        return count != null ? Math.max(0, count) : 0;
    }

    public static boolean isNotificationAccessGranted(Context context) {
        try {
            String flat = Settings.Secure.getString(
                    context.getContentResolver(),
                    "enabled_notification_listeners");
            if (flat == null || flat.isEmpty()) return false;
            ComponentName cn = new ComponentName(context, KayumarsNotificationListener.class);
            return flat.contains(cn.flattenToString()) || flat.contains(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        rebuildCounts();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        rebuildCounts();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        rebuildCounts();
    }

    private void rebuildCounts() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            ConcurrentHashMap<String, Integer> next = new ConcurrentHashMap<String, Integer>();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (sbn == null || sbn.isOngoing()) continue;
                    String pkg = sbn.getPackageName();
                    if (pkg == null || pkg.isEmpty()) continue;
                    Integer cur = next.get(pkg);
                    next.put(pkg, cur == null ? 1 : cur + 1);
                }
            }
            COUNTS.clear();
            for (Map.Entry<String, Integer> e : next.entrySet()) {
                COUNTS.put(e.getKey(), e.getValue());
            }
            BadgeUpdateListener l = listener;
            if (l != null) {
                l.onBadgesUpdated();
            }
        } catch (Exception ignored) {}
    }
}

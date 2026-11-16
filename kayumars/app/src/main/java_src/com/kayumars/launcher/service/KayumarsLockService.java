package com.kayumars.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import java.lang.ref.WeakReference;

/**
 * KayumarsLockService — Optional AccessibilityService for double-tap Home lock screen (API 28+).
 */
public class KayumarsLockService extends AccessibilityService {

    private static volatile WeakReference<KayumarsLockService> instanceRef = null;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instanceRef = new WeakReference<KayumarsLockService>(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Zero background work; strictly invoked on explicit double-tap empty Home gesture.
    }

    @Override
    public void onInterrupt() {
        instanceRef = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instanceRef = null;
    }

    public static boolean isEnabled(Context context) {
        if (instanceRef != null && instanceRef.get() != null) return true;
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            ComponentName cn = new ComponentName(context, KayumarsLockService.class);
            return enabled.contains(cn.flattenToString()) || enabled.contains(KayumarsLockService.class.getSimpleName());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean lockScreenNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        KayumarsLockService svc = instanceRef != null ? instanceRef.get() : null;
        if (svc == null) return false;
        try {
            return svc.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (Exception e) {
            return false;
        }
    }
}

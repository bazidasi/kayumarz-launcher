package com.kayumars.launcher.history;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import com.kayumars.launcher.bridge.Core;

/**
 * LaunchLogger - Safe app launcher helper with guarded Core FFI logging,
 * local frequency/recency fallback persistence, and Scene F ActivityOptions transitions.
 */
public final class LaunchLogger {
    public static final String PREFS_HISTORY = "kayumars_history";
    private static final String KEY_TOTAL = "_total_launches";

    public static boolean launchApp(Context context, String pkg) {
        return launchAppWithTransition(context, null, pkg, false);
    }

    public static boolean launchAppWithTransition(
            Context context,
            View sourceView,
            String pkg,
            boolean useClipReveal) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (sourceView != null && sourceView.getWidth() > 0 && sourceView.getHeight() > 0) {
                    ActivityOptions opts;
                    if (useClipReveal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        opts = ActivityOptions.makeClipRevealAnimation(
                                sourceView, 0, 0, sourceView.getWidth(), sourceView.getHeight());
                    } else {
                        opts = ActivityOptions.makeScaleUpAnimation(
                                sourceView, 0, 0, sourceView.getWidth(), sourceView.getHeight());
                    }
                    context.startActivity(intent, opts.toBundle());
                } else {
                    context.startActivity(intent);
                }
                long now = System.currentTimeMillis();
                recordLocalLaunch(context, pkg, now);
                Core.logLaunch(pkg, now);
                return true;
            }
        } catch (Exception e) {
            // Safe ignore if app was just uninstalled or restricted
        }
        return false;
    }

    public static void recordOnly(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        long now = System.currentTimeMillis();
        recordLocalLaunch(context, pkg, now);
        Core.logLaunch(pkg, now);
    }

    private static void recordLocalLaunch(Context context, String pkg, long timestampMs) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE);
        int count = sp.getInt("cnt_" + pkg, 0) + 1;
        long total = sp.getLong(KEY_TOTAL, 0L) + 1L;
        sp.edit()
            .putInt("cnt_" + pkg, count)
            .putLong("ts_" + pkg, timestampMs)
            .putLong(KEY_TOTAL, total)
            .apply();
    }

    public static void resetHistory(Context context) {
        context.getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}

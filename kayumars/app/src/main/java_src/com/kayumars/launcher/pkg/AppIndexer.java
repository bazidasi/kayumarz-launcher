package com.kayumars.launcher.pkg;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AppIndexer - Asynchronously indexes installed apps off the UI thread.
 * Deduplicates multi-launcher activities and guards against null labels.
 */
public final class AppIndexer {
    public interface Callback {
        void onScanComplete(List<AppModel> apps);
    }

    public static class AppModel {
        public final String pkg;
        public final String label;

        public AppModel(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    public static void scanAsync(final Context context, final Callback callback) {
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppModel> list = new ArrayList<AppModel>();
                try {
                    PackageManager pm = appContext.getPackageManager();
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                    List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);
                    Set<String> seenPackages = new HashSet<String>();
                    String selfPkg = appContext.getPackageName();

                    if (activities != null) {
                        for (ResolveInfo info : activities) {
                            if (info.activityInfo == null) continue;
                            String pkg = info.activityInfo.packageName;
                            if (pkg == null || pkg.equals(selfPkg) || seenPackages.contains(pkg)) continue;
                            seenPackages.add(pkg);
                            CharSequence rawLabel = info.loadLabel(pm);
                            String label = (rawLabel != null && rawLabel.length() > 0) ? rawLabel.toString() : pkg;
                            list.add(new AppModel(pkg, label));
                        }
                    }
                } catch (Exception ignored) {
                    // Never crash background indexing thread
                }

                if (callback != null) {
                    callback.onScanComplete(list);
                }
            }
        }, "Kayumars-Indexer").start();
    }
}

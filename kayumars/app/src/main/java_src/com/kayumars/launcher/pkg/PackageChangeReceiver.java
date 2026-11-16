package com.kayumars.launcher.pkg;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * PackageChangeReceiver - Listens for app installations and removals.
 * Notifies an optional UI listener and triggers background re-indexing.
 */
public class PackageChangeReceiver extends BroadcastReceiver {
    public interface Listener {
        void onPackagesChanged();
    }

    private final Listener listener;

    public PackageChangeReceiver() {
        this.listener = null;
    }

    public PackageChangeReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
            Intent.ACTION_PACKAGE_REMOVED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            if (listener != null) {
                listener.onPackagesChanged();
            } else {
                AppIndexer.scanAsync(context, null);
            }
        }
    }
}

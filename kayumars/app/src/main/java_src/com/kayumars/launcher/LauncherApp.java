package com.kayumars.launcher;

import android.app.Application;

/**
 * LauncherApp - Zero-overhead Application class.
 * Satisfies the < 200 ms cold start performance budget by deliberately doing nothing in onCreate.
 */
public class LauncherApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Zero work here. Everything defer-loaded or background-initialized.
    }
}

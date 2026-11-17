package com.kayumars.launcher

import android.app.Application

/**
 * Minimal Application: only installs the crash reporter so field crashes
 * can be copied and reported without adb.
 */
class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}

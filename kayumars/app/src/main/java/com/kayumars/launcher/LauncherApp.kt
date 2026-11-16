package com.kayumars.launcher

import android.app.Application

/**
 * Deliberately empty Application class to guarantee sub-200ms cold start.
 * No SDK initialization, no dependency injection graph, no heavy work.
 */
class LauncherApp : Application()

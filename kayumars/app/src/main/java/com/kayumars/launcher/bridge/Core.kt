package com.kayumars.launcher.bridge

import android.content.Context
import androidx.annotation.WorkerThread
import java.io.File

/**
 * Data structures mirrored from Rust core via UniFFI.
 */
enum class SearchResultKind {
    APP,
    CONTACT,
    SETTING
}

data class AppEntry(
    val pkg: String,
    val label: String,
    val userSerial: Int = 0,
    val flags: Int = 0
)

data class SearchResult(
    val pkg: String,
    val label: String,
    val score: Float,
    val kind: SearchResultKind = SearchResultKind.APP
)

enum class GestureKind {
    SINGLE_TAP,
    DOUBLE_TAP,
    LONG_PRESS,
    LONG_PRESS_DRAG,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    PINCH_IN,
    PINCH_OUT,
    TWO_FINGER_SWIPE_UP,
    TWO_FINGER_SWIPE_DOWN
}

enum class SurfaceZone {
    HOME_SCREEN,
    APP_ICON,
    APP_DRAWER,
    SEARCH_OVERLAY,
    SETTINGS_SHEET
}

enum class GestureProfilePreset {
    BALANCED_DEFAULT,
    BATTERY_SAVER,
    ONE_HANDED,
    POWER_USER
}

data class DispatchedCommand(
    val recognized: Boolean,
    val gesture: GestureKind? = null,
    val actionType: String = "NONE",
    val targetPackage: String? = null,
    val shortcutId: String? = null,
    val shortcutLabel: String? = null
)

/**
 * Singleton bridge interface encapsulating the native Rust core library.
 */
object Core {
    private var isNativeLoaded = false
    private var nativeHandle: Long = 0L

    fun init(context: Context) {
        if (isNativeLoaded) return
        val configDir = File(context.filesDir, "kayumars").apply { mkdirs() }.absolutePath
        try {
            System.loadLibrary("kayumars_core")
            nativeHandle = nativeInit(configDir)
            isNativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
        }
    }

    fun search(query: String, maxResults: Int = 12): List<SearchResult> {
        if (!isNativeLoaded || nativeHandle == 0L) return emptyList()
        return nativeSearch(nativeHandle, query, maxResults)
    }

    fun topApps(maxResults: Int = 12): List<SearchResult> {
        if (!isNativeLoaded || nativeHandle == 0L) return emptyList()
        return nativeTopApps(nativeHandle, maxResults)
    }

    @WorkerThread
    fun replaceIndex(apps: List<AppEntry>) {
        if (!isNativeLoaded || nativeHandle == 0L) return
        nativeReplaceIndex(nativeHandle, apps)
    }

    fun logLaunch(pkg: String, timestampMs: Long) {
        if (!isNativeLoaded || nativeHandle == 0L) return
        nativeLogLaunch(nativeHandle, pkg, timestampMs)
    }

    @WorkerThread
    fun saveNow() {
        if (!isNativeLoaded || nativeHandle == 0L) return
        nativeSaveNow(nativeHandle)
    }

    fun processGestureTouch(touchEventJson: String): String {
        if (!isNativeLoaded || nativeHandle == 0L) return ""
        return try {
            nativeProcessGestureTouch(nativeHandle, touchEventJson)
        } catch (_: UnsatisfiedLinkError) {
            ""
        }
    }

    fun setGestureProfile(preset: GestureProfilePreset) {
        if (!isNativeLoaded || nativeHandle == 0L) return
        try {
            nativeSetGestureProfile(nativeHandle, preset.name)
        } catch (_: UnsatisfiedLinkError) {}
    }

    private external fun nativeInit(configDir: String): Long
    private external fun nativeSearch(handle: Long, query: String, maxResults: Int): List<SearchResult>
    private external fun nativeTopApps(handle: Long, maxResults: Int): List<SearchResult>
    private external fun nativeReplaceIndex(handle: Long, apps: List<AppEntry>)
    private external fun nativeLogLaunch(handle: Long, pkg: String, timestampMs: Long)
    private external fun nativeSaveNow(handle: Long)
    private external fun nativeProcessGestureTouch(handle: Long, touchEventJson: String): String
    private external fun nativeSetGestureProfile(handle: Long, profileName: String)
}

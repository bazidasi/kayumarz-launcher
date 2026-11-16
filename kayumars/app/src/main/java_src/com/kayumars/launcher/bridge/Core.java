package com.kayumars.launcher.bridge;

import android.content.Context;
import java.io.File;

/**
 * Core - Guarded JNI bridge between Java shell and Rust kayumars_core engine.
 * Includes both Search/Rank and Hybrid Gesture Engine FFI entry points.
 * All native calls are guarded against UnsatisfiedLinkError so a missing or
 * mismatched .so never crashes the launcher process.
 */
public final class Core {
    private static volatile boolean isLoaded = false;

    public static synchronized void init(Context context) {
        if (isLoaded) return;
        try {
            System.loadLibrary("kayumars_core");
            File storePath = new File(context.getFilesDir(), "kayumars_store.bin");
            nativeInit(storePath.getAbsolutePath());
            isLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            isLoaded = false;
        } catch (Throwable t) {
            isLoaded = false;
        }
    }

    public static boolean isNativeLoaded() {
        return isLoaded;
    }

    public static String search(String query, int maxResults) {
        if (!isLoaded) return "";
        try {
            return nativeSearch(query, maxResults);
        } catch (UnsatisfiedLinkError e) {
            isLoaded = false;
            return "";
        }
    }

    public static String topApps(int maxResults) {
        if (!isLoaded) return "";
        try {
            return nativeTopApps(maxResults);
        } catch (UnsatisfiedLinkError e) {
            isLoaded = false;
            return "";
        }
    }

    public static void replaceIndex(String jsonApps) {
        if (!isLoaded) return;
        try {
            nativeReplaceIndex(jsonApps);
        } catch (UnsatisfiedLinkError e) {
            isLoaded = false;
        }
    }

    public static void logLaunch(String pkg, long timestampMs) {
        if (!isLoaded) return;
        try {
            nativeLogLaunch(pkg, timestampMs);
        } catch (UnsatisfiedLinkError e) {
            isLoaded = false;
        }
    }

    public static void saveNow() {
        if (!isLoaded) return;
        try {
            nativeSaveNow();
        } catch (UnsatisfiedLinkError e) {
            isLoaded = false;
        }
    }

    /**
     * Guarded wrapper for Rust GestureEngine.process_touch_event().
     * Returns empty string when running in pure Java fallback mode.
     */
    public static String processGestureTouch(String touchEventJson) {
        if (!isLoaded) return "";
        try {
            String out = nativeProcessTouchEvent(touchEventJson);
            return out != null ? out : "";
        } catch (UnsatisfiedLinkError e) {
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setGestureProfile(String profileName) {
        if (!isLoaded) return;
        try {
            nativeSetGestureProfile(profileName);
        } catch (UnsatisfiedLinkError ignored) {
        } catch (Throwable ignored) {}
    }

    public static void setIconGesture(String pkg, String gestureKind, String actionJson) {
        if (!isLoaded) return;
        try {
            nativeSetIconGesture(pkg, gestureKind, actionJson);
        } catch (UnsatisfiedLinkError ignored) {
        } catch (Throwable ignored) {}
    }

    private static native void nativeInit(String storePath);
    private static native String nativeSearch(String query, int maxResults);
    private static native String nativeTopApps(int maxResults);
    private static native void nativeReplaceIndex(String jsonApps);
    private static native void nativeLogLaunch(String pkg, long timestampMs);
    private static native void nativeSaveNow();
    private static native String nativeProcessTouchEvent(String touchEventJson);
    private static native void nativeSetGestureProfile(String profileName);
    private static native void nativeSetIconGesture(String pkg, String gestureKind, String actionJson);
}

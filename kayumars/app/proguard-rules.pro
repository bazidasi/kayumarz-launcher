# Kayumars R8 / ProGuard rules

# Keep UniFFI and JNA classes
-keep class com.kayumars.launcher.bridge.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.auth.**

# Strip all logging and assertion overhead in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Optimize RecyclerView holders
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    public <init>(...);
}

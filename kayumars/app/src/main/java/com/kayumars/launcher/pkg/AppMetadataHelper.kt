package com.kayumars.launcher.pkg

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserManager
import androidx.palette.graphics.Palette
import com.kayumars.launcher.R
import com.kayumars.launcher.bridge.AppEntry

object AppMetadataHelper {

    enum class LibraryCategory(val key: String, val titleRes: Int) {
        SUGGESTIONS("SUGGESTIONS", R.string.lib_cat_suggestions),
        PRODUCTIVITY("PRODUCTIVITY", R.string.lib_cat_productivity),
        SOCIAL("SOCIAL", R.string.lib_cat_social),
        CREATIVITY("CREATIVITY", R.string.lib_cat_creativity),
        UTILITIES("UTILITIES", R.string.lib_cat_utilities),
        OTHER("OTHER", R.string.lib_cat_other);

        companion object {
            fun fromKey(key: String): LibraryCategory =
                entries.firstOrNull { it.key == key } ?: OTHER
        }
    }

    data class ShortcutEntry(
        val rawInfo: ShortcutInfo,
        val id: String,
        val label: String,
        val icon: Drawable?
    )

    @JvmStatic
    fun queryShortcuts(context: Context, pkg: String, maxCount: Int = 4): List<ShortcutEntry> {
        val out = mutableListOf<ShortcutEntry>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return out
        try {
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return out
            if (!la.hasShortcutHostPermission()) return out
            val q = LauncherApps.ShortcutQuery().apply {
                setPackage(pkg)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val list = la.getShortcuts(q, Process.myUserHandle()) ?: return out
            for (si in list) {
                if (!si.isEnabled) continue
                val lbl = (si.shortLabel ?: si.longLabel)?.toString()?.takeIf { it.isNotEmpty() } ?: continue
                val icon = try {
                    la.getShortcutIconDrawable(si, context.resources.displayMetrics.densityDpi)
                } catch (_: Exception) {
                    null
                }
                out.add(ShortcutEntry(si, si.id, lbl, icon))
                if (out.size >= maxCount) break
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
        return out
    }

    @JvmStatic
    fun launchShortcut(context: Context, entry: ShortcutEntry): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        return try {
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            la?.startShortcut(entry.rawInfo, null, null)
            la != null
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun detectWorkProfilePackages(context: Context): Set<String> {
        val out = mutableSetOf<String>()
        try {
            val um = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return out
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return out
            val me = Process.myUserHandle()
            for (uh in um.userProfiles) {
                if (uh == null || uh == me) continue
                for (info in la.getActivityList(null, uh)) {
                    info.applicationInfo?.packageName?.let { out.add(it) }
                }
            }
        } catch (_: Exception) {}
        return out
    }

    @JvmStatic
    fun classifyApp(app: AppEntry): LibraryCategory {
        val low = "${app.pkg} ${app.label}".lowercase()
        return when {
            low.contains("telegram") || low.contains("whatsapp") || low.contains("instagram") ||
                low.contains("discord") || low.contains("twitter") || low.contains("messag") ||
                low.contains("dialer") || low.contains("phone") || low.contains("eitaa") ||
                low.contains("bale") || low.contains("rubika") || low.contains("signal") ||
                low.contains("contact") || low.contains("slack") || low.contains("meet") -> LibraryCategory.SOCIAL
            low.contains("mail") || low.contains("gmail") || low.contains("docs") ||
                low.contains("drive") || low.contains("calendar") || low.contains("notion") ||
                low.contains("obsidian") || low.contains("keep") || low.contains("bank") ||
                low.contains("wallet") || low.contains("stripe") || low.contains("pdf") ||
                low.contains("office") || low.contains("github") || low.contains("termux") -> LibraryCategory.PRODUCTIVITY
            low.contains("camera") || low.contains("photo") || low.contains("gallery") ||
                low.contains("music") || low.contains("spotify") || low.contains("youtube") ||
                low.contains("vlc") || low.contains("video") || low.contains("studio") ||
                low.contains("paint") || low.contains("draw") || low.contains("edit") ||
                low.contains("netflix") || low.contains("telewebion") -> LibraryCategory.CREATIVITY
            low.contains("calc") || low.contains("clock") || low.contains("setting") ||
                low.contains("files") || low.contains("vpn") || low.contains("chrome") ||
                low.contains("firefox") || low.contains("edge") || low.contains("browser") ||
                low.contains("maps") || low.contains("weather") || low.contains("compass") ||
                low.contains("qr") || low.contains("bitwarden") -> LibraryCategory.UTILITIES
            else -> LibraryCategory.OTHER
        }
    }

    @JvmStatic
    fun sampleWallpaperAccent(wallpaperDrawable: Drawable?, fallbackColor: Int): Int {
        if (wallpaperDrawable == null) return fallbackColor
        return try {
            val bmp = if (wallpaperDrawable is BitmapDrawable && wallpaperDrawable.bitmap != null) {
                Bitmap.createScaledBitmap(wallpaperDrawable.bitmap, 24, 24, false)
            } else {
                Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).also {
                    val c = Canvas(it)
                    wallpaperDrawable.setBounds(0, 0, 24, 24)
                    wallpaperDrawable.draw(c)
                }
            }
            val palette = Palette.from(bmp).generate()
            palette.getVibrantColor(palette.getDominantColor(fallbackColor))
        } catch (_: Exception) {
            fallbackColor
        }
    }
}

package com.kayumars.launcher.pkg;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import com.kayumars.launcher.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AppMetadataHelper — Queries LauncherApps quick actions, work-profile badges,
 * App Library category classification, and lightweight wallpaper luminance sampling.
 */
public final class AppMetadataHelper {

    public enum LibraryCategory {
        SUGGESTIONS("SUGGESTIONS", R.string.lib_cat_suggestions),
        PRODUCTIVITY("PRODUCTIVITY", R.string.lib_cat_productivity),
        SOCIAL("SOCIAL", R.string.lib_cat_social),
        CREATIVITY("CREATIVITY", R.string.lib_cat_creativity),
        UTILITIES("UTILITIES", R.string.lib_cat_utilities),
        OTHER("OTHER", R.string.lib_cat_other);

        public final String key;
        public final int titleRes;

        LibraryCategory(String key, int titleRes) {
            this.key = key;
            this.titleRes = titleRes;
        }

        public static LibraryCategory fromKey(String key) {
            for (LibraryCategory c : values()) {
                if (c.key.equals(key)) return c;
            }
            return OTHER;
        }
    }

    public static final class ShortcutEntry {
        public final ShortcutInfo rawInfo;
        public final String id;
        public final String label;
        public final Drawable icon;

        public ShortcutEntry(ShortcutInfo rawInfo, String id, String label, Drawable icon) {
            this.rawInfo = rawInfo;
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }

    private AppMetadataHelper() {}

    public static List<ShortcutEntry> queryShortcuts(Context context, String pkg, int maxCount) {
        List<ShortcutEntry> out = new ArrayList<ShortcutEntry>();
        if (pkg == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return out;
        try {
            LauncherApps la = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (la == null || !la.hasShortcutHostPermission()) return out;
            LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
            q.setPackage(pkg);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> list = la.getShortcuts(q, Process.myUserHandle());
            if (list != null) {
                for (ShortcutInfo si : list) {
                    if (si == null || !si.isEnabled()) continue;
                    CharSequence lbl = si.getShortLabel();
                    if (lbl == null || lbl.length() == 0) lbl = si.getLongLabel();
                    if (lbl == null || lbl.length() == 0) continue;
                    Drawable icon = null;
                    try {
                        icon = la.getShortcutIconDrawable(si, context.getResources().getDisplayMetrics().densityDpi);
                    } catch (Exception ignored) {}
                    out.add(new ShortcutEntry(si, si.getId(), lbl.toString(), icon));
                    if (out.size() >= maxCount) break;
                }
            }
        } catch (SecurityException ignored) {
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean launchShortcut(Context context, ShortcutEntry entry) {
        if (entry == null || entry.rawInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return false;
        }
        try {
            LauncherApps la = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (la != null) {
                la.startShortcut(entry.rawInfo, null, null);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static Set<String> detectWorkProfilePackages(Context context) {
        Set<String> workPkgs = new HashSet<String>();
        try {
            UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
            LauncherApps la = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (um == null || la == null) return workPkgs;
            UserHandle myHandle = Process.myUserHandle();
            List<UserHandle> profiles = um.getUserProfiles();
            if (profiles == null) return workPkgs;
            for (UserHandle uh : profiles) {
                if (uh == null || uh.equals(myHandle)) continue;
                List<LauncherActivityInfo> acts = la.getActivityList(null, uh);
                if (acts != null) {
                    for (LauncherActivityInfo info : acts) {
                        if (info != null && info.getApplicationInfo() != null) {
                            workPkgs.add(info.getApplicationInfo().packageName);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return workPkgs;
    }

    public static LibraryCategory classifyApp(AppIndexer.AppModel app) {
        if (app == null) return LibraryCategory.OTHER;
        String low = (app.pkg + " " + app.label).toLowerCase();
        if (low.contains("telegram") || low.contains("whatsapp") || low.contains("instagram")
                || low.contains("discord") || low.contains("twitter") || low.contains("messag")
                || low.contains("dialer") || low.contains("phone") || low.contains("eitaa")
                || low.contains("bale") || low.contains("rubika") || low.contains("signal")
                || low.contains("contact") || low.contains("slack") || low.contains("meet")) {
            return LibraryCategory.SOCIAL;
        }
        if (low.contains("mail") || low.contains("gmail") || low.contains("docs")
                || low.contains("drive") || low.contains("calendar") || low.contains("notion")
                || low.contains("obsidian") || low.contains("keep") || low.contains("bank")
                || low.contains("wallet") || low.contains("stripe") || low.contains("pdf")
                || low.contains("office") || low.contains("github") || low.contains("termux")) {
            return LibraryCategory.PRODUCTIVITY;
        }
        if (low.contains("camera") || low.contains("photo") || low.contains("gallery")
                || low.contains("music") || low.contains("spotify") || low.contains("youtube")
                || low.contains("vlc") || low.contains("video") || low.contains("studio")
                || low.contains("paint") || low.contains("draw") || low.contains("edit")
                || low.contains("netflix") || low.contains("telewebion")) {
            return LibraryCategory.CREATIVITY;
        }
        if (low.contains("calc") || low.contains("clock") || low.contains("setting")
                || low.contains("files") || low.contains("vpn") || low.contains("chrome")
                || low.contains("firefox") || low.contains("edge") || low.contains("browser")
                || low.contains("maps") || low.contains("weather") || low.contains("compass")
                || low.contains("qr") || low.contains("bitwarden")) {
            return LibraryCategory.UTILITIES;
        }
        return LibraryCategory.OTHER;
    }

    /**
     * Extracts average wallpaper accent color without allocating large bitmaps.
     */
    public static int sampleWallpaperAccent(Drawable wallpaperDrawable, int fallbackColor) {
        if (wallpaperDrawable == null) return fallbackColor;
        try {
            Bitmap bmp;
            if (wallpaperDrawable instanceof BitmapDrawable && ((BitmapDrawable) wallpaperDrawable).getBitmap() != null) {
                bmp = Bitmap.createScaledBitmap(((BitmapDrawable) wallpaperDrawable).getBitmap(), 12, 12, false);
            } else {
                bmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                wallpaperDrawable.setBounds(0, 0, 12, 12);
                wallpaperDrawable.draw(c);
            }
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < bmp.getHeight(); y++) {
                for (int x = 0; x < bmp.getWidth(); x++) {
                    int px = bmp.getPixel(x, y);
                    r += Color.red(px);
                    g += Color.green(px);
                    b += Color.blue(px);
                    n++;
                }
            }
            if (n > 0) {
                return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
            }
        } catch (Exception ignored) {}
        return fallbackColor;
    }
}

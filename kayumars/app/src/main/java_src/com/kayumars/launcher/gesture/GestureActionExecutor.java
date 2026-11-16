package com.kayumars.launcher.gesture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import com.kayumars.launcher.gesture.GestureModels.ActionType;
import com.kayumars.launcher.gesture.GestureModels.GestureCommand;
import com.kayumars.launcher.history.LaunchLogger;
import java.lang.reflect.Method;

/**
 * Strictly decoupled Action Execution Layer for Kayumars Gesture Engine.
 * Converts semantic GestureCommands emitted by the engine into safe Android OS
 * calls, intents, and launcher navigation callbacks without leaking OS APIs into Core.
 */
public final class GestureActionExecutor {

    public interface LauncherNavigationHost {
        void onOpenDrawer();
        void onOpenSearch();
        void onNextPage();
        void onPreviousPage();
        void onOpenOverview();
        void onReturnHome();
        void onOpenSettings();
        void onOpenEditMenu(String targetPackage);
        void onMoveIcon(String targetPackage, float dxDp, float dyDp);
        void onScreenLockRequested();
        boolean onLaunchTarget(String targetPackage);
    }

    private final Context context;
    private final LauncherNavigationHost host;

    public GestureActionExecutor(Context context, LauncherNavigationHost host) {
        this.context = context;
        this.host = host;
    }

    public void execute(GestureCommand cmd) {
        if (cmd == null || !cmd.recognized) return;

        switch (cmd.action) {
            case LAUNCH_TARGET:
                if (cmd.targetPackage != null) {
                    if (host == null || !host.onLaunchTarget(cmd.targetPackage)) {
                        LaunchLogger.launchApp(context, cmd.targetPackage);
                    }
                }
                break;

            case LAUNCH_SHORTCUT:
                executeAppShortcut(cmd.targetPackage, cmd.shortcutId, cmd.shortcutLabel);
                break;

            case MOVE_ICON:
                if (host != null && cmd.targetPackage != null) {
                    host.onMoveIcon(cmd.targetPackage, cmd.dragDxDp, cmd.dragDyDp);
                }
                break;

            case LOCK_SCREEN:
                executeLockScreen();
                break;

            case EXPAND_NOTIFICATIONS:
                expandStatusBarMethod("expandNotificationsPanel");
                break;

            case EXPAND_QUICK_SETTINGS:
                expandStatusBarMethod("expandSettingsPanel");
                break;

            case OPEN_APP_DRAWER:
                if (host != null) host.onOpenDrawer();
                break;

            case OPEN_SEARCH:
                if (host != null) host.onOpenSearch();
                break;

            case NEXT_PAGE:
                if (host != null) host.onNextPage();
                break;

            case PREVIOUS_PAGE:
                if (host != null) host.onPreviousPage();
                break;

            case OPEN_OVERVIEW:
                if (host != null) host.onOpenOverview();
                break;

            case RETURN_HOME:
                if (host != null) host.onReturnHome();
                break;

            case OPEN_LAUNCHER_SETTINGS:
                if (host != null) host.onOpenSettings();
                break;

            case OPEN_EDIT_MENU:
                if (host != null) host.onOpenEditMenu(cmd.targetPackage);
                break;

            case NONE:
            default:
                break;
        }
    }

    private void executeAppShortcut(String pkg, String shortcutId, String label) {
        if (pkg == null) return;
        // Telegram deep-link actions with safe fallback to main Telegram activity
        if ("org.telegram.messenger".equals(pkg) && shortcutId != null) {
            String uriStr = null;
            if ("tg_saved_messages".equals(shortcutId)) uriStr = "tg://resolve?domain=me";
            else if ("tg_new_message".equals(shortcutId)) uriStr = "tg://msg";
            else if ("tg_search".equals(shortcutId)) uriStr = "tg://search";
            else if ("tg_contacts".equals(shortcutId)) uriStr = "tg://contacts";
            else if ("tg_settings".equals(shortcutId)) uriStr = "tg://settings";

            if (uriStr != null) {
                try {
                    Intent deep = new Intent(Intent.ACTION_VIEW, Uri.parse(uriStr));
                    deep.setPackage(pkg);
                    deep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(deep);
                    LaunchLogger.recordOnly(context, pkg);
                    return;
                } catch (Exception ignored) {}
            }
        }
        if (label != null && !label.isEmpty()) {
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show();
        }
        LaunchLogger.launchApp(context, pkg);
    }

    private void executeLockScreen() {
        if (host != null) {
            host.onScreenLockRequested();
        }
    }

    @SuppressLint("WrongConstant")
    private void expandStatusBarMethod(String methodName) {
        try {
            Object statusBarService = context.getSystemService("statusbar");
            if (statusBarService != null) {
                Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
                Method method = statusBarManager.getMethod(methodName);
                method.invoke(statusBarService);
                return;
            }
        } catch (Exception ignored) {}

        // Safe fallback if OEM restricts StatusBarManager reflection
        try {
            if ("expandSettingsPanel".equals(methodName)) {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        } catch (Exception ignored) {}
    }
}

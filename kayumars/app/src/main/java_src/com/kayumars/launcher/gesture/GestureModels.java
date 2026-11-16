package com.kayumars.launcher.gesture;

/**
 * Pure data models for the Kayumars Hybrid Gesture Engine (v1.0 - 12 Gestures).
 * Decoupled from Android framework APIs; matches Rust `gesture_engine`.
 */
public final class GestureModels {

    public enum GestureKind {
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

    public enum SurfaceZone {
        HOME_SCREEN,
        APP_ICON,
        APP_DRAWER,
        SEARCH_OVERLAY,
        SETTINGS_SHEET
    }

    public enum ProfilePreset {
        BALANCED_DEFAULT,
        BATTERY_SAVER,
        ONE_HANDED,
        POWER_USER
    }

    public enum ActionType {
        NONE,
        LAUNCH_TARGET,
        LAUNCH_SHORTCUT,
        MOVE_ICON,
        LOCK_SCREEN,
        EXPAND_NOTIFICATIONS,
        EXPAND_QUICK_SETTINGS,
        OPEN_APP_DRAWER,
        OPEN_SEARCH,
        NEXT_PAGE,
        PREVIOUS_PAGE,
        OPEN_OVERVIEW,
        RETURN_HOME,
        OPEN_LAUNCHER_SETTINGS,
        OPEN_EDIT_MENU
    }

    public static final class GestureRule {
        public final GestureKind kind;
        public boolean enabled;
        public float sensitivity;
        public float minDistanceDp;
        public long timeoutMs;
        public ActionType action;

        public GestureRule(
                GestureKind kind,
                boolean enabled,
                float sensitivity,
                float minDistanceDp,
                long timeoutMs,
                ActionType action) {
            this.kind = kind;
            this.enabled = enabled;
            this.sensitivity = sensitivity;
            this.minDistanceDp = minDistanceDp;
            this.timeoutMs = timeoutMs;
            this.action = action;
        }

        public float effectiveDistanceDp() {
            float s = Math.max(0.4f, Math.min(2.5f, sensitivity));
            return Math.max(4f, minDistanceDp / s);
        }
    }

    public static final class IconShortcutAction {
        public final ActionType actionType;
        public final String targetPackage;
        public final String shortcutId;
        public final String label;

        public IconShortcutAction(
                ActionType actionType,
                String targetPackage,
                String shortcutId,
                String label) {
            this.actionType = actionType;
            this.targetPackage = targetPackage;
            this.shortcutId = shortcutId;
            this.label = label;
        }
    }

    public static final class GestureCommand {
        public final boolean recognized;
        public final GestureKind gesture;
        public final ActionType action;
        public final SurfaceZone zone;
        public final String targetPackage;
        public final String shortcutId;
        public final String shortcutLabel;
        public final float dragDxDp;
        public final float dragDyDp;

        public GestureCommand(
                boolean recognized,
                GestureKind gesture,
                ActionType action,
                SurfaceZone zone,
                String targetPackage,
                String shortcutId,
                String shortcutLabel,
                float dragDxDp,
                float dragDyDp) {
            this.recognized = recognized;
            this.gesture = gesture;
            this.action = action;
            this.zone = zone;
            this.targetPackage = targetPackage;
            this.shortcutId = shortcutId;
            this.shortcutLabel = shortcutLabel;
            this.dragDxDp = dragDxDp;
            this.dragDyDp = dragDyDp;
        }

        public static GestureCommand none(SurfaceZone zone) {
            return new GestureCommand(false, null, ActionType.NONE, zone, null, null, null, 0f, 0f);
        }
    }
}

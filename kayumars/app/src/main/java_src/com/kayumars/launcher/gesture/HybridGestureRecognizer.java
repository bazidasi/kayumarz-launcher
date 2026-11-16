package com.kayumars.launcher.gesture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import com.kayumars.launcher.bridge.Core;
import com.kayumars.launcher.gesture.GestureModels.ActionType;
import com.kayumars.launcher.gesture.GestureModels.GestureCommand;
import com.kayumars.launcher.gesture.GestureModels.GestureKind;
import com.kayumars.launcher.gesture.GestureModels.GestureRule;
import com.kayumars.launcher.gesture.GestureModels.IconShortcutAction;
import com.kayumars.launcher.gesture.GestureModels.ProfilePreset;
import com.kayumars.launcher.gesture.GestureModels.SurfaceZone;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Event-driven Hybrid Gesture Recognizer.
 * Routes MotionEvents to the Rust Core FFI when exported, with an exact deterministic
 * local finite-state-machine fallback covering all 12 v1.0 gestures + per-icon gestures.
 *
 * Battery rules:
 * - Zero background polling threads or continuous sensors.
 * - Multi-touch detectors (Pinch, 2-Finger Swipes) strictly gated to HOME_SCREEN.
 * - Single Handler timer posted ONLY while waiting for LongPress or DoubleTap expiry.
 */
public final class HybridGestureRecognizer {

    public interface Listener {
        void onGestureCommand(GestureCommand command);
    }

    private static final String PREF_PROFILE = "gesture_profile_preset";
    private static final String PREF_RULE_PREFIX = "gesture_rule_";
    private static final String PREF_ICON_PREFIX = "gesture_icon_";

    private static final float TOUCH_SLOP_DP = 12f;
    private static final float DOUBLE_TAP_SLOP_DP = 36f;

    private final float density;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final EnumMap<GestureKind, GestureRule> rules = new EnumMap<GestureKind, GestureRule>(GestureKind.class);
    private final Map<String, EnumMap<GestureKind, IconShortcutAction>> iconGestures =
            new HashMap<String, EnumMap<GestureKind, IconShortcutAction>>();
    private final Listener listener;

    private ProfilePreset activePreset = ProfilePreset.BALANCED_DEFAULT;
    private boolean homeOnlyMultitouch = true;

    // Active touch session state (zero per-move allocations)
    private SurfaceZone activeZone = SurfaceZone.HOME_SCREEN;
    private String activePackage = null;
    private long downTimeMs = 0L;
    private int primaryPointerId = -1;
    private float startXdp = 0f;
    private float startYdp = 0f;
    private float lastXdp = 0f;
    private float lastYdp = 0f;
    private int maxPointerCount = 0;
    private float multiStartSpanDp = 0f;
    private float multiStartCxDp = 0f;
    private float multiStartCyDp = 0f;
    private boolean multiTracking = false;
    private boolean multiGestureEmitted = false;
    private boolean longPressTriggered = false;
    private boolean dragStarted = false;

    private boolean hasPendingTap = false;
    private long pendingTapTimeMs = 0L;
    private float pendingTapXdp = 0f;
    private float pendingTapYdp = 0f;
    private SurfaceZone pendingTapZone = SurfaceZone.HOME_SCREEN;
    private String pendingTapPkg = null;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            GestureRule lpRule = rules.get(GestureKind.LONG_PRESS);
            if (lpRule == null || !lpRule.enabled || maxPointerCount != 1 || dragStarted) return;
            float dx = lastXdp - startXdp;
            float dy = lastYdp - startYdp;
            if ((dx * dx + dy * dy) <= (TOUCH_SLOP_DP * TOUCH_SLOP_DP)) {
                longPressTriggered = true;
                clearPendingTap();
                emit(GestureKind.LONG_PRESS, activeZone, activePackage, 0f, 0f);
            }
        }
    };

    private final Runnable singleTapCommitRunnable = new Runnable() {
        @Override
        public void run() {
            if (!hasPendingTap) return;
            SurfaceZone z = pendingTapZone;
            String pkg = pendingTapPkg;
            clearPendingTap();
            GestureRule tapRule = rules.get(GestureKind.SINGLE_TAP);
            if (tapRule != null && tapRule.enabled) {
                emit(GestureKind.SINGLE_TAP, z, pkg, 0f, 0f);
            }
        }
    };

    public HybridGestureRecognizer(Context context, SharedPreferences prefs, Listener listener) {
        this.density = Math.max(1f, context.getResources().getDisplayMetrics().density);
        this.prefs = prefs;
        this.listener = listener;
        loadFromPrefs();
    }

    public ProfilePreset getActivePreset() {
        return activePreset;
    }

    public Map<GestureKind, GestureRule> getRules() {
        return rules;
    }

    public EnumMap<GestureKind, IconShortcutAction> getIconGestures(String pkg) {
        return iconGestures.get(pkg);
    }

    public void applyPreset(ProfilePreset preset) {
        this.activePreset = preset;
        initDefaultRules(preset);
        prefs.edit().putString(PREF_PROFILE, preset.name()).apply();
        Core.setGestureProfile(preset.name());
    }

    public void updateRule(GestureKind kind, boolean enabled, float sensitivity, float minDistDp, long timeoutMs, ActionType action) {
        GestureRule r = new GestureRule(kind, enabled, sensitivity, minDistDp, timeoutMs, action);
        rules.put(kind, r);
        prefs.edit()
                .putString(PREF_RULE_PREFIX + kind.name(),
                        enabled + "|" + sensitivity + "|" + minDistDp + "|" + timeoutMs + "|" + action.name())
                .apply();
    }

    public void setIconGesture(String pkg, GestureKind kind, ActionType actionType, String shortcutId, String label) {
        EnumMap<GestureKind, IconShortcutAction> map = iconGestures.get(pkg);
        if (map == null) {
            map = new EnumMap<GestureKind, IconShortcutAction>(GestureKind.class);
            iconGestures.put(pkg, map);
        }
        IconShortcutAction binding = new IconShortcutAction(actionType, pkg, shortcutId, label);
        map.put(kind, binding);
        prefs.edit()
                .putString(PREF_ICON_PREFIX + pkg + "_" + kind.name(),
                        actionType.name() + "|" + (shortcutId == null ? "" : shortcutId) + "|" + (label == null ? "" : label))
                .apply();
        Core.setIconGesture(pkg, kind.name(), actionType.name());
    }

    private void loadFromPrefs() {
        String presetStr = prefs.getString(PREF_PROFILE, ProfilePreset.BALANCED_DEFAULT.name());
        try {
            activePreset = ProfilePreset.valueOf(presetStr);
        } catch (Exception e) {
            activePreset = ProfilePreset.BALANCED_DEFAULT;
        }
        initDefaultRules(activePreset);
        seedDefaultIconGestures();

        for (GestureKind k : GestureKind.values()) {
            String raw = prefs.getString(PREF_RULE_PREFIX + k.name(), null);
            if (raw != null) {
                String[] parts = raw.split("\\|");
                if (parts.length == 5) {
                    try {
                        rules.put(k, new GestureRule(
                                k,
                                Boolean.parseBoolean(parts[0]),
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Long.parseLong(parts[3]),
                                ActionType.valueOf(parts[4])));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void initDefaultRules(ProfilePreset preset) {
        rules.clear();
        rules.put(GestureKind.SINGLE_TAP, new GestureRule(GestureKind.SINGLE_TAP, true, 1f, 0f, 220L, ActionType.LAUNCH_TARGET));
        rules.put(GestureKind.DOUBLE_TAP, new GestureRule(GestureKind.DOUBLE_TAP, true, 1f, 0f, 260L, ActionType.LOCK_SCREEN));
        rules.put(GestureKind.LONG_PRESS, new GestureRule(GestureKind.LONG_PRESS, true, 1f, 0f, 420L, ActionType.OPEN_EDIT_MENU));
        rules.put(GestureKind.LONG_PRESS_DRAG, new GestureRule(GestureKind.LONG_PRESS_DRAG, true, 1f, 14f, 420L, ActionType.MOVE_ICON));
        rules.put(GestureKind.SWIPE_UP, new GestureRule(GestureKind.SWIPE_UP, true, 1f, 56f, 500L, ActionType.OPEN_APP_DRAWER));
        rules.put(GestureKind.SWIPE_DOWN, new GestureRule(GestureKind.SWIPE_DOWN, true, 1f, 56f, 500L, ActionType.EXPAND_NOTIFICATIONS));
        rules.put(GestureKind.SWIPE_LEFT, new GestureRule(GestureKind.SWIPE_LEFT, true, 1f, 64f, 450L, ActionType.NEXT_PAGE));
        rules.put(GestureKind.SWIPE_RIGHT, new GestureRule(GestureKind.SWIPE_RIGHT, true, 1f, 64f, 450L, ActionType.PREVIOUS_PAGE));
        rules.put(GestureKind.PINCH_IN, new GestureRule(GestureKind.PINCH_IN, true, 1f, 44f, 650L, ActionType.OPEN_OVERVIEW));
        rules.put(GestureKind.PINCH_OUT, new GestureRule(GestureKind.PINCH_OUT, true, 1f, 44f, 650L, ActionType.RETURN_HOME));
        rules.put(GestureKind.TWO_FINGER_SWIPE_UP, new GestureRule(GestureKind.TWO_FINGER_SWIPE_UP, true, 1f, 64f, 550L, ActionType.EXPAND_QUICK_SETTINGS));
        rules.put(GestureKind.TWO_FINGER_SWIPE_DOWN, new GestureRule(GestureKind.TWO_FINGER_SWIPE_DOWN, true, 1f, 64f, 550L, ActionType.OPEN_LAUNCHER_SETTINGS));

        if (preset == ProfilePreset.BATTERY_SAVER) {
            rules.get(GestureKind.PINCH_IN).enabled = false;
            rules.get(GestureKind.PINCH_OUT).enabled = false;
            rules.get(GestureKind.TWO_FINGER_SWIPE_UP).enabled = false;
            rules.get(GestureKind.TWO_FINGER_SWIPE_DOWN).enabled = false;
        } else if (preset == ProfilePreset.ONE_HANDED) {
            GestureRule sd = rules.get(GestureKind.SWIPE_DOWN);
            sd.action = ActionType.OPEN_SEARCH;
            sd.minDistanceDp = 44f;
            sd.sensitivity = 1.25f;
            GestureRule su = rules.get(GestureKind.SWIPE_UP);
            su.minDistanceDp = 44f;
            su.sensitivity = 1.25f;
            rules.get(GestureKind.TWO_FINGER_SWIPE_DOWN).action = ActionType.EXPAND_NOTIFICATIONS;
        } else if (preset == ProfilePreset.POWER_USER) {
            rules.get(GestureKind.SWIPE_DOWN).action = ActionType.OPEN_SEARCH;
            rules.get(GestureKind.DOUBLE_TAP).timeoutMs = 220L;
        }
    }

    private void seedDefaultIconGestures() {
        iconGestures.clear();
        EnumMap<GestureKind, IconShortcutAction> tg = new EnumMap<GestureKind, IconShortcutAction>(GestureKind.class);
        tg.put(GestureKind.SINGLE_TAP, new IconShortcutAction(ActionType.LAUNCH_TARGET, "org.telegram.messenger", "tg_open", "Open Telegram"));
        tg.put(GestureKind.DOUBLE_TAP, new IconShortcutAction(ActionType.LAUNCH_SHORTCUT, "org.telegram.messenger", "tg_saved_messages", "Saved Messages"));
        tg.put(GestureKind.SWIPE_UP, new IconShortcutAction(ActionType.LAUNCH_SHORTCUT, "org.telegram.messenger", "tg_new_message", "New Message"));
        tg.put(GestureKind.SWIPE_DOWN, new IconShortcutAction(ActionType.LAUNCH_SHORTCUT, "org.telegram.messenger", "tg_search", "Search"));
        tg.put(GestureKind.SWIPE_LEFT, new IconShortcutAction(ActionType.LAUNCH_SHORTCUT, "org.telegram.messenger", "tg_contacts", "Contacts"));
        tg.put(GestureKind.SWIPE_RIGHT, new IconShortcutAction(ActionType.LAUNCH_SHORTCUT, "org.telegram.messenger", "tg_settings", "Settings"));
        iconGestures.put("org.telegram.messenger", tg);
    }

    public boolean onTouchEvent(MotionEvent ev, SurfaceZone zone, String targetPackage) {
        int masked = ev.getActionMasked();
        long now = ev.getEventTime();

        switch (masked) {
            case MotionEvent.ACTION_DOWN: {
                mainHandler.removeCallbacks(longPressRunnable);
                activeZone = zone;
                activePackage = targetPackage;
                downTimeMs = now;
                primaryPointerId = ev.getPointerId(0);
                startXdp = ev.getX(0) / density;
                startYdp = ev.getY(0) / density;
                lastXdp = startXdp;
                lastYdp = startYdp;
                maxPointerCount = ev.getPointerCount();
                multiTracking = false;
                multiGestureEmitted = false;
                longPressTriggered = false;
                dragStarted = false;

                GestureRule lp = rules.get(GestureKind.LONG_PRESS);
                if (lp != null && lp.enabled) {
                    mainHandler.postDelayed(longPressRunnable, lp.timeoutMs);
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                mainHandler.removeCallbacks(longPressRunnable);
                maxPointerCount = Math.max(maxPointerCount, ev.getPointerCount());
                if (ev.getPointerCount() >= 2 && (!homeOnlyMultitouch || activeZone == SurfaceZone.HOME_SCREEN)) {
                    float x1 = ev.getX(0) / density;
                    float y1 = ev.getY(0) / density;
                    float x2 = ev.getX(1) / density;
                    float y2 = ev.getY(1) / density;
                    float dx = x2 - x1;
                    float dy = y2 - y1;
                    multiStartSpanDp = (float) Math.sqrt(dx * dx + dy * dy);
                    multiStartCxDp = (x1 + x2) * 0.5f;
                    multiStartCyDp = (y1 + y2) * 0.5f;
                    multiTracking = true;
                    clearPendingTap();
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (multiTracking && ev.getPointerCount() >= 2 && !multiGestureEmitted) {
                    float x1 = ev.getX(0) / density;
                    float y1 = ev.getY(0) / density;
                    float x2 = ev.getX(1) / density;
                    float y2 = ev.getY(1) / density;
                    long dur = Math.max(0L, now - downTimeMs);

                    float span = (float) Math.hypot(x2 - x1, y2 - y1);
                    float spanDelta = span - multiStartSpanDp;
                    GestureKind pinchKind = spanDelta < 0 ? GestureKind.PINCH_IN : GestureKind.PINCH_OUT;
                    GestureRule pinchRule = rules.get(pinchKind);
                    if (pinchRule != null && pinchRule.enabled && dur <= pinchRule.timeoutMs
                            && Math.abs(spanDelta) >= pinchRule.effectiveDistanceDp()) {
                        multiGestureEmitted = true;
                        emit(pinchKind, activeZone, null, 0f, 0f);
                        return true;
                    }

                    float cx = (x1 + x2) * 0.5f;
                    float cy = (y1 + y2) * 0.5f;
                    float cdx = cx - multiStartCxDp;
                    float cdy = cy - multiStartCyDp;
                    if (Math.abs(cdy) >= Math.abs(cdx)) {
                        GestureKind tfKind = cdy < 0 ? GestureKind.TWO_FINGER_SWIPE_UP : GestureKind.TWO_FINGER_SWIPE_DOWN;
                        GestureRule tfRule = rules.get(tfKind);
                        if (tfRule != null && tfRule.enabled && dur <= tfRule.timeoutMs
                                && Math.abs(cdy) >= tfRule.effectiveDistanceDp()) {
                            multiGestureEmitted = true;
                            emit(tfKind, activeZone, null, cdx, cdy);
                            return true;
                        }
                    }
                    return true;
                }

                int idx = ev.findPointerIndex(primaryPointerId);
                if (idx < 0) idx = 0;
                lastXdp = ev.getX(idx) / density;
                lastYdp = ev.getY(idx) / density;
                float dx = lastXdp - startXdp;
                float dy = lastYdp - startYdp;
                if ((dx * dx + dy * dy) > (TOUCH_SLOP_DP * TOUCH_SLOP_DP) && !longPressTriggered) {
                    mainHandler.removeCallbacks(longPressRunnable);
                }

                GestureRule dragRule = rules.get(GestureKind.LONG_PRESS_DRAG);
                if (longPressTriggered && dragRule != null && dragRule.enabled && maxPointerCount == 1) {
                    float minD = dragRule.effectiveDistanceDp();
                    if ((dx * dx + dy * dy) >= (minD * minD)) {
                        dragStarted = true;
                        emit(GestureKind.LONG_PRESS_DRAG, activeZone, activePackage, dx, dy);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP: {
                mainHandler.removeCallbacks(longPressRunnable);
                if (maxPointerCount >= 2 || multiGestureEmitted || dragStarted || longPressTriggered) {
                    return true;
                }

                int idx = ev.findPointerIndex(primaryPointerId);
                if (idx < 0) idx = 0;
                float upX = ev.getX(idx) / density;
                float upY = ev.getY(idx) / density;
                float dx = upX - startXdp;
                float dy = upY - startYdp;
                long dur = Math.max(0L, now - downTimeMs);

                GestureRule tapRule = rules.get(GestureKind.SINGLE_TAP);
                long tapTimeout = tapRule != null ? tapRule.timeoutMs : 220L;
                if (dur <= tapTimeout && (dx * dx + dy * dy) <= (TOUCH_SLOP_DP * TOUCH_SLOP_DP)) {
                    GestureRule dtRule = rules.get(GestureKind.DOUBLE_TAP);
                    if (dtRule != null && dtRule.enabled && hasPendingTap && pendingTapZone == activeZone
                            && equalsNullable(pendingTapPkg, activePackage)) {
                        long delta = now - pendingTapTimeMs;
                        float dtx = upX - pendingTapXdp;
                        float dty = upY - pendingTapYdp;
                        if (delta <= dtRule.timeoutMs && (dtx * dtx + dty * dty) <= (DOUBLE_TAP_SLOP_DP * DOUBLE_TAP_SLOP_DP)) {
                            clearPendingTap();
                            emit(GestureKind.DOUBLE_TAP, activeZone, activePackage, 0f, 0f);
                            return true;
                        }
                    }

                    if (dtRule != null && dtRule.enabled) {
                        hasPendingTap = true;
                        pendingTapTimeMs = now;
                        pendingTapXdp = upX;
                        pendingTapYdp = upY;
                        pendingTapZone = activeZone;
                        pendingTapPkg = activePackage;
                        mainHandler.removeCallbacks(singleTapCommitRunnable);
                        mainHandler.postDelayed(singleTapCommitRunnable, dtRule.timeoutMs);
                        return true;
                    }

                    if (tapRule != null && tapRule.enabled) {
                        emit(GestureKind.SINGLE_TAP, activeZone, activePackage, 0f, 0f);
                    }
                    return true;
                }

                clearPendingTap();
                if (Math.abs(dy) >= Math.abs(dx)) {
                    GestureKind sKind = dy < 0 ? GestureKind.SWIPE_UP : GestureKind.SWIPE_DOWN;
                    GestureRule r = rules.get(sKind);
                    if (r != null && r.enabled && dur <= r.timeoutMs && Math.abs(dy) >= r.effectiveDistanceDp()) {
                        emit(sKind, activeZone, activePackage, dx, dy);
                    }
                } else {
                    GestureKind sKind = dx < 0 ? GestureKind.SWIPE_LEFT : GestureKind.SWIPE_RIGHT;
                    GestureRule r = rules.get(sKind);
                    if (r != null && r.enabled && dur <= r.timeoutMs && Math.abs(dx) >= r.effectiveDistanceDp()) {
                        emit(sKind, activeZone, activePackage, dx, dy);
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                mainHandler.removeCallbacks(longPressRunnable);
                clearPendingTap();
                return true;
        }
        return false;
    }

    private void emit(GestureKind kind, SurfaceZone zone, String pkg, float dxDp, float dyDp) {
        // Optional native telemetry hook (guarded inside Core wrapper)
        Core.processGestureTouch(kind.name() + ":" + zone.name());

        ActionType resolvedAction = ActionType.NONE;
        String shortcutId = null;
        String shortcutLabel = null;

        if (pkg != null) {
            EnumMap<GestureKind, IconShortcutAction> perIcon = iconGestures.get(pkg);
            if (perIcon != null && perIcon.containsKey(kind)) {
                IconShortcutAction binding = perIcon.get(kind);
                resolvedAction = binding.actionType;
                shortcutId = binding.shortcutId;
                shortcutLabel = binding.label;
            } else if (kind == GestureKind.SINGLE_TAP) {
                resolvedAction = ActionType.LAUNCH_TARGET;
            } else if (kind == GestureKind.LONG_PRESS) {
                resolvedAction = ActionType.OPEN_EDIT_MENU;
            } else if (kind == GestureKind.LONG_PRESS_DRAG) {
                resolvedAction = ActionType.MOVE_ICON;
            }
        }

        if (resolvedAction == ActionType.NONE) {
            GestureRule r = rules.get(kind);
            if (r != null) {
                resolvedAction = r.action;
            }
        }

        if (listener != null && resolvedAction != ActionType.NONE) {
            listener.onGestureCommand(new GestureCommand(
                    true, kind, resolvedAction, zone, pkg, shortcutId, shortcutLabel, dxDp, dyDp));
        }
    }

    private void clearPendingTap() {
        hasPendingTap = false;
        pendingTapPkg = null;
        mainHandler.removeCallbacks(singleTapCommitRunnable);
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}

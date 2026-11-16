package com.kayumars.launcher.gesture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import com.kayumars.launcher.bridge.Core
import com.kayumars.launcher.bridge.DispatchedCommand
import com.kayumars.launcher.bridge.GestureKind
import com.kayumars.launcher.bridge.GestureProfilePreset
import com.kayumars.launcher.bridge.SurfaceZone
import com.kayumars.launcher.history.LaunchLogger
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class GestureRule(
    val kind: GestureKind,
    var enabled: Boolean = true,
    var sensitivity: Float = 1.0f,
    var minDistanceDp: Float = 56f,
    var timeoutMs: Long = 450L,
    var actionType: String = "NONE"
) {
    fun effectiveDistanceDp(): Float = max(4f, minDistanceDp / min(2.5f, max(0.4f, sensitivity)))
}

/**
 * Event-driven Hybrid Gesture Recognizer in Kotlin (mirrors Rust `gesture_engine`).
 * Zero background polling threads; multi-touch strictly gated to HOME_SCREEN.
 */
class HybridGestureRecognizer(
    context: Context,
    private val onCommand: (DispatchedCommand) -> Unit
) {
    private val density = max(1f, context.resources.displayMetrics.density)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rules = mutableMapOf<GestureKind, GestureRule>()
    private val iconGestures = mutableMapOf<String, MutableMap<GestureKind, DispatchedCommand>>()

    var activePreset: GestureProfilePreset = GestureProfilePreset.BALANCED_DEFAULT
        private set

    private var activeZone = SurfaceZone.HOME_SCREEN
    private var activePkg: String? = null
    private var downTimeMs = 0L
    private var primaryId = -1
    private var startXdp = 0f
    private var startYdp = 0f
    private var lastXdp = 0f
    private var lastYdp = 0f
    private var maxPointers = 0
    private var multiSpanStart = 0f
    private var multiCxStart = 0f
    private var multiCyStart = 0f
    private var multiTracking = false
    private var multiEmitted = false
    private var longPressTriggered = false
    private var dragStarted = false
    private var pendingTapTime = 0L
    private var hasPendingTap = false
    private var pendingTapX = 0f
    private var pendingTapY = 0f
    private var pendingTapZone = SurfaceZone.HOME_SCREEN
    private var pendingTapPkg: String? = null

    private val longPressTask = Runnable {
        val rule = rules[GestureKind.LONG_PRESS] ?: return@Runnable
        if (!rule.enabled || maxPointers != 1 || dragStarted) return@Runnable
        val dx = lastXdp - startXdp
        val dy = lastYdp - startYdp
        if (dx * dx + dy * dy <= 144f) {
            longPressTriggered = true
            clearPendingTap()
            emit(GestureKind.LONG_PRESS, activeZone, activePkg)
        }
    }

    private val singleTapCommitTask = Runnable {
        if (!hasPendingTap) return@Runnable
        val z = pendingTapZone
        val pkg = pendingTapPkg
        clearPendingTap()
        if (rules[GestureKind.SINGLE_TAP]?.enabled == true) {
            emit(GestureKind.SINGLE_TAP, z, pkg)
        }
    }

    init {
        applyPreset(GestureProfilePreset.BALANCED_DEFAULT)
        seedDefaultIconGestures()
    }

    fun applyPreset(preset: GestureProfilePreset) {
        activePreset = preset
        rules[GestureKind.SINGLE_TAP] = GestureRule(GestureKind.SINGLE_TAP, true, 1f, 0f, 220L, "LAUNCH_TARGET")
        rules[GestureKind.DOUBLE_TAP] = GestureRule(GestureKind.DOUBLE_TAP, true, 1f, 0f, 260L, "LOCK_SCREEN")
        rules[GestureKind.LONG_PRESS] = GestureRule(GestureKind.LONG_PRESS, true, 1f, 0f, 420L, "OPEN_EDIT_MENU")
        rules[GestureKind.LONG_PRESS_DRAG] = GestureRule(GestureKind.LONG_PRESS_DRAG, true, 1f, 14f, 420L, "MOVE_ICON")
        rules[GestureKind.SWIPE_UP] = GestureRule(GestureKind.SWIPE_UP, true, 1f, 56f, 500L, "OPEN_APP_DRAWER")
        rules[GestureKind.SWIPE_DOWN] = GestureRule(GestureKind.SWIPE_DOWN, true, 1f, 56f, 500L, "EXPAND_NOTIFICATIONS")
        rules[GestureKind.SWIPE_LEFT] = GestureRule(GestureKind.SWIPE_LEFT, true, 1f, 64f, 450L, "NEXT_PAGE")
        rules[GestureKind.SWIPE_RIGHT] = GestureRule(GestureKind.SWIPE_RIGHT, true, 1f, 64f, 450L, "PREVIOUS_PAGE")
        rules[GestureKind.PINCH_IN] = GestureRule(GestureKind.PINCH_IN, true, 1f, 44f, 650L, "OPEN_OVERVIEW")
        rules[GestureKind.PINCH_OUT] = GestureRule(GestureKind.PINCH_OUT, true, 1f, 44f, 650L, "RETURN_HOME")
        rules[GestureKind.TWO_FINGER_SWIPE_UP] = GestureRule(GestureKind.TWO_FINGER_SWIPE_UP, true, 1f, 64f, 550L, "EXPAND_QUICK_SETTINGS")
        rules[GestureKind.TWO_FINGER_SWIPE_DOWN] = GestureRule(GestureKind.TWO_FINGER_SWIPE_DOWN, true, 1f, 64f, 550L, "OPEN_LAUNCHER_SETTINGS")

        if (preset == GestureProfilePreset.BATTERY_SAVER) {
            listOf(
                GestureKind.PINCH_IN, GestureKind.PINCH_OUT,
                GestureKind.TWO_FINGER_SWIPE_UP, GestureKind.TWO_FINGER_SWIPE_DOWN
            ).forEach { rules[it]?.enabled = false }
        }
        Core.setGestureProfile(preset)
    }

    private fun seedDefaultIconGestures() {
        val tg = mutableMapOf<GestureKind, DispatchedCommand>()
        tg[GestureKind.DOUBLE_TAP] = DispatchedCommand(true, GestureKind.DOUBLE_TAP, "LAUNCH_SHORTCUT", "org.telegram.messenger", "tg_saved_messages", "Saved Messages")
        tg[GestureKind.SWIPE_UP] = DispatchedCommand(true, GestureKind.SWIPE_UP, "LAUNCH_SHORTCUT", "org.telegram.messenger", "tg_new_message", "New Message")
        tg[GestureKind.SWIPE_DOWN] = DispatchedCommand(true, GestureKind.SWIPE_DOWN, "LAUNCH_SHORTCUT", "org.telegram.messenger", "tg_search", "Search")
        tg[GestureKind.SWIPE_LEFT] = DispatchedCommand(true, GestureKind.SWIPE_LEFT, "LAUNCH_SHORTCUT", "org.telegram.messenger", "tg_contacts", "Contacts")
        tg[GestureKind.SWIPE_RIGHT] = DispatchedCommand(true, GestureKind.SWIPE_RIGHT, "LAUNCH_SHORTCUT", "org.telegram.messenger", "tg_settings", "Settings")
        iconGestures["org.telegram.messenger"] = tg
    }

    fun onTouchEvent(ev: MotionEvent, zone: SurfaceZone, pkg: String?): Boolean {
        val now = ev.eventTime
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mainHandler.removeCallbacks(longPressTask)
                activeZone = zone
                activePkg = pkg
                downTimeMs = now
                primaryId = ev.getPointerId(0)
                startXdp = ev.getX(0) / density
                startYdp = ev.getY(0) / density
                lastXdp = startXdp
                lastYdp = startYdp
                maxPointers = ev.pointerCount
                multiTracking = false
                multiEmitted = false
                longPressTriggered = false
                dragStarted = false
                rules[GestureKind.LONG_PRESS]?.takeIf { it.enabled }?.let {
                    mainHandler.postDelayed(longPressTask, it.timeoutMs)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                mainHandler.removeCallbacks(longPressTask)
                maxPointers = max(maxPointers, ev.pointerCount)
                if (ev.pointerCount >= 2 && activeZone == SurfaceZone.HOME_SCREEN) {
                    val x1 = ev.getX(0) / density
                    val y1 = ev.getY(0) / density
                    val x2 = ev.getX(1) / density
                    val y2 = ev.getY(1) / density
                    multiSpanStart = hypot(x2 - x1, y2 - y1)
                    multiCxStart = (x1 + x2) * 0.5f
                    multiCyStart = (y1 + y2) * 0.5f
                    multiTracking = true
                    clearPendingTap()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiTracking && ev.pointerCount >= 2 && !multiEmitted) {
                    val x1 = ev.getX(0) / density
                    val y1 = ev.getY(0) / density
                    val x2 = ev.getX(1) / density
                    val y2 = ev.getY(1) / density
                    val dur = max(0L, now - downTimeMs)
                    val spanDelta = hypot(x2 - x1, y2 - y1) - multiSpanStart
                    val pKind = if (spanDelta < 0f) GestureKind.PINCH_IN else GestureKind.PINCH_OUT
                    val pRule = rules[pKind]
                    if (pRule != null && pRule.enabled && dur <= pRule.timeoutMs && abs(spanDelta) >= pRule.effectiveDistanceDp()) {
                        multiEmitted = true
                        emit(pKind, activeZone, null)
                        return true
                    }
                    val cdx = ((x1 + x2) * 0.5f) - multiCxStart
                    val cdy = ((y1 + y2) * 0.5f) - multiCyStart
                    if (abs(cdy) >= abs(cdx)) {
                        val tfKind = if (cdy < 0f) GestureKind.TWO_FINGER_SWIPE_UP else GestureKind.TWO_FINGER_SWIPE_DOWN
                        val tfRule = rules[tfKind]
                        if (tfRule != null && tfRule.enabled && dur <= tfRule.timeoutMs && abs(cdy) >= tfRule.effectiveDistanceDp()) {
                            multiEmitted = true
                            emit(tfKind, activeZone, null)
                            return true
                        }
                    }
                    return true
                }
                val idx = ev.findPointerIndex(primaryId).takeIf { it >= 0 } ?: 0
                lastXdp = ev.getX(idx) / density
                lastYdp = ev.getY(idx) / density
                val dx = lastXdp - startXdp
                val dy = lastYdp - startYdp
                if (dx * dx + dy * dy > 144f && !longPressTriggered) {
                    mainHandler.removeCallbacks(longPressTask)
                }
                val dragRule = rules[GestureKind.LONG_PRESS_DRAG]
                if (longPressTriggered && dragRule?.enabled == true && maxPointers == 1) {
                    val md = dragRule.effectiveDistanceDp()
                    if (dx * dx + dy * dy >= md * md) {
                        dragStarted = true
                        emit(GestureKind.LONG_PRESS_DRAG, activeZone, activePkg)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressTask)
                if (maxPointers >= 2 || multiEmitted || dragStarted || longPressTriggered) return true
                val idx = ev.findPointerIndex(primaryId).takeIf { it >= 0 } ?: 0
                val upX = ev.getX(idx) / density
                val upY = ev.getY(idx) / density
                val dx = upX - startXdp
                val dy = upY - startYdp
                val dur = max(0L, now - downTimeMs)
                val tapRule = rules[GestureKind.SINGLE_TAP]
                if (dur <= (tapRule?.timeoutMs ?: 220L) && dx * dx + dy * dy <= 144f) {
                    val dtRule = rules[GestureKind.DOUBLE_TAP]
                    if (dtRule?.enabled == true && hasPendingTap && pendingTapZone == activeZone && pendingTapPkg == activePkg) {
                        val dtx = upX - pendingTapX
                        val dty = upY - pendingTapY
                        if ((now - pendingTapTime) <= dtRule.timeoutMs && dtx * dtx + dty * dty <= 1296f) {
                            clearPendingTap()
                            emit(GestureKind.DOUBLE_TAP, activeZone, activePkg)
                            return true
                        }
                    }
                    if (dtRule?.enabled == true) {
                        hasPendingTap = true
                        pendingTapTime = now
                        pendingTapX = upX
                        pendingTapY = upY
                        pendingTapZone = activeZone
                        pendingTapPkg = activePkg
                        mainHandler.removeCallbacks(singleTapCommitTask)
                        mainHandler.postDelayed(singleTapCommitTask, dtRule.timeoutMs)
                        return true
                    }
                    if (tapRule?.enabled == true) emit(GestureKind.SINGLE_TAP, activeZone, activePkg)
                    return true
                }
                clearPendingTap()
                val sKind = if (abs(dy) >= abs(dx)) {
                    if (dy < 0f) GestureKind.SWIPE_UP else GestureKind.SWIPE_DOWN
                } else {
                    if (dx < 0f) GestureKind.SWIPE_LEFT else GestureKind.SWIPE_RIGHT
                }
                val r = rules[sKind]
                val dist = if (abs(dy) >= abs(dx)) abs(dy) else abs(dx)
                if (r != null && r.enabled && dur <= r.timeoutMs && dist >= r.effectiveDistanceDp()) {
                    emit(sKind, activeZone, activePkg)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressTask)
                clearPendingTap()
                return true
            }
        }
        return false
    }

    private fun emit(kind: GestureKind, zone: SurfaceZone, pkg: String?) {
        Core.processGestureTouch("${kind.name}:${zone.name}")
        if (pkg != null) {
            iconGestures[pkg]?.get(kind)?.let {
                onCommand(it)
                return
            }
        }
        val act = rules[kind]?.actionType ?: "NONE"
        if (act != "NONE") {
            onCommand(DispatchedCommand(true, kind, act, pkg))
        }
    }

    private fun clearPendingTap() {
        hasPendingTap = false
        pendingTapPkg = null
        mainHandler.removeCallbacks(singleTapCommitTask)
    }
}

class GestureActionExecutor(
    private val context: Context,
    private val onDrawer: () -> Unit,
    private val onSearch: () -> Unit,
    private val onReturnHome: () -> Unit,
    private val onSettings: () -> Unit
) {
    fun execute(cmd: DispatchedCommand) {
        if (!cmd.recognized) return
        when (cmd.actionType) {
            "LAUNCH_TARGET" -> cmd.targetPackage?.let { LaunchLogger.launchApp(context, it) }
            "LAUNCH_SHORTCUT" -> {
                val pkg = cmd.targetPackage ?: return
                val uri = when (cmd.shortcutId) {
                    "tg_saved_messages" -> "tg://resolve?domain=me"
                    "tg_new_message" -> "tg://msg"
                    "tg_search" -> "tg://search"
                    "tg_contacts" -> "tg://contacts"
                    "tg_settings" -> "tg://settings"
                    else -> null
                }
                if (uri != null) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                            setPackage(pkg)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        return
                    } catch (_: Exception) {}
                }
                LaunchLogger.launchApp(context, pkg)
            }
            "OPEN_APP_DRAWER", "OPEN_OVERVIEW" -> onDrawer()
            "OPEN_SEARCH" -> onSearch()
            "RETURN_HOME" -> onReturnHome()
            "OPEN_LAUNCHER_SETTINGS", "OPEN_EDIT_MENU" -> onSettings()
            "LOCK_SCREEN" -> Toast.makeText(context, "Lock Screen requested", Toast.LENGTH_SHORT).show()
            "EXPAND_NOTIFICATIONS" -> invokeStatusBar("expandNotificationsPanel")
            "EXPAND_QUICK_SETTINGS" -> invokeStatusBar("expandSettingsPanel")
        }
    }

    private fun invokeStatusBar(methodName: String) {
        try {
            val sb = context.getSystemService("statusbar") ?: return
            Class.forName("android.app.StatusBarManager").getMethod(methodName).invoke(sb)
        } catch (_: Exception) {
            if (methodName == "expandSettingsPanel") {
                try {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
        }
    }
}

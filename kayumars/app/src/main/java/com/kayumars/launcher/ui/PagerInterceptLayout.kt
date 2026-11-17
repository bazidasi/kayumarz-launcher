package com.kayumars.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Pager root that steals horizontal swipes from clickable children (icon cells, cards,
 * buttons) once movement exceeds touch slop, while taps, stationary long-presses and
 * vertical scrolling stay with the children. Intercepted events fall through to the
 * pager's OnTouchListener; [onHorizontalDragStart] seeds that listener with the original
 * DOWN coordinates because a child consumed the real DOWN. Set [interceptEnabled] = false
 * during edit/jiggle mode so cell drag gestures keep their horizontal moves.
 */
class PagerInterceptLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var interceptEnabled = true
    var onHorizontalDragStart: ((rawX: Float, rawY: Float) -> Unit)? = null

    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!interceptEnabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                intercepting = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (intercepting) return true
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (abs(dx) > touchSlopPx && abs(dx) > abs(dy)) {
                    intercepting = true
                    onHorizontalDragStart?.invoke(downX, downY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> intercepting = false
        }
        return false
    }
}
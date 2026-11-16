package com.kayumars.launcher.ui.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

/**
 * AppGridItemAnimator — Custom RecyclerView.ItemAnimator for Kayumars grid/list items.
 *
 * - Add / Install: Pop-in scale 0.6 -> 1.0 using BOUNCE spring + alpha fade
 * - Remove / Uninstall: Shrink 1.0 -> 0.5 + fade out (140 ms EMPHASIZED_ACCELERATE)
 * - Move / Reflow: Neighbor translation using SNAPPY spring (no stretching)
 */
class AppGridItemAnimator : SimpleItemAnimator() {

    private val pendingAdds = ArrayList<RecyclerView.ViewHolder>()
    private val pendingRemovals = ArrayList<RecyclerView.ViewHolder>()
    private val pendingMoves = ArrayList<MoveInfo>()
    private val runningAddRemove = ArrayList<RecyclerView.ViewHolder>()

    private data class MoveInfo(
        val holder: RecyclerView.ViewHolder,
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int
    )

    init {
        supportsChangeAnimations = false
        addDuration = MotionSpec.DURATION_STANDARD_MS
        removeDuration = MotionSpec.DURATION_FAST_MS
        moveDuration = MotionSpec.DURATION_STANDARD_MS
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        resetAnimation(holder)
        pendingRemovals.add(holder)
        return true
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        resetAnimation(holder)
        val view = holder.itemView
        if (!MotionSpec.isReducedMotion(view.context)) {
            view.alpha = 0f
            view.scaleX = 0.6f
            view.scaleY = 0.6f
        }
        pendingAdds.add(holder)
        return true
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        val view = holder.itemView
        val dx = toX - fromX - view.translationX.toInt()
        val dy = toY - fromY - view.translationY.toInt()
        resetAnimation(holder)
        if (dx == 0 && dy == 0) {
            dispatchMoveFinished(holder)
            return false
        }
        view.translationX = -dx.toFloat()
        view.translationY = -dy.toFloat()
        pendingMoves.add(MoveInfo(holder, fromX, fromY, toX, toY))
        return true
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        fromLeft: Int,
        fromTop: Int,
        toLeft: Int,
        toTop: Int
    ): Boolean {
        if (oldHolder === newHolder) {
            return animateMove(oldHolder, fromLeft, fromTop, toLeft, toTop)
        }
        dispatchChangeFinished(oldHolder, true)
        dispatchChangeFinished(newHolder, false)
        return false
    }

    override fun runPendingAnimations() {
        val removals = ArrayList(pendingRemovals)
        val moves = ArrayList(pendingMoves)
        val adds = ArrayList(pendingAdds)
        pendingRemovals.clear()
        pendingMoves.clear()
        pendingAdds.clear()

        for (holder in removals) {
            val v = holder.itemView
            runningAddRemove.add(holder)
            if (MotionSpec.isReducedMotion(v.context)) {
                v.alpha = 1f
                v.scaleX = 1f
                v.scaleY = 1f
                runningAddRemove.remove(holder)
                dispatchRemoveFinished(holder)
                dispatchFinishedWhenDone()
            } else {
                v.animate()
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setDuration(MotionSpec.DURATION_FAST_MS)
                    .setInterpolator(MotionSpec.EMPHASIZED_ACCELERATE)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            dispatchRemoveStarting(holder)
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            v.animate().setListener(null)
                            v.alpha = 1f
                            v.scaleX = 1f
                            v.scaleY = 1f
                            runningAddRemove.remove(holder)
                            dispatchRemoveFinished(holder)
                            dispatchFinishedWhenDone()
                        }
                    })
                    .start()
            }
        }

        for (m in moves) {
            val v = m.holder.itemView
            dispatchMoveStarting(m.holder)
            SpringPreset.animate(v, DynamicAnimation.TRANSLATION_X, 0f, SpringPreset.SNAPPY)
            SpringPreset.animate(v, DynamicAnimation.TRANSLATION_Y, 0f, SpringPreset.SNAPPY) {
                dispatchMoveFinished(m.holder)
                dispatchFinishedWhenDone()
            }
        }

        for (holder in adds) {
            val v = holder.itemView
            dispatchAddStarting(holder)
            if (MotionSpec.isReducedMotion(v.context)) {
                v.alpha = 1f
                v.scaleX = 1f
                v.scaleY = 1f
                dispatchAddFinished(holder)
                dispatchFinishedWhenDone()
            } else {
                v.animate()
                    .alpha(1f)
                    .setDuration(MotionSpec.DURATION_FAST_MS)
                    .setInterpolator(MotionSpec.STANDARD)
                    .start()
                SpringPreset.animateScale(v, 1.0f, SpringPreset.BOUNCE) {
                    dispatchAddFinished(holder)
                    dispatchFinishedWhenDone()
                }
            }
        }
    }

    override fun endAnimation(item: RecyclerView.ViewHolder) {
        item.itemView.animate().cancel()
        SpringPreset.cancelForView(item.itemView)
        if (pendingRemovals.remove(item)) {
            item.itemView.alpha = 1f
            item.itemView.scaleX = 1f
            item.itemView.scaleY = 1f
            dispatchRemoveFinished(item)
        }
        if (pendingAdds.remove(item)) {
            item.itemView.alpha = 1f
            item.itemView.scaleX = 1f
            item.itemView.scaleY = 1f
            dispatchAddFinished(item)
        }
        val moveIt = pendingMoves.iterator()
        while (moveIt.hasNext()) {
            val m = moveIt.next()
            if (m.holder === item) {
                item.itemView.translationX = 0f
                item.itemView.translationY = 0f
                moveIt.remove()
                dispatchMoveFinished(item)
            }
        }
        dispatchFinishedWhenDone()
    }

    override fun endAnimations() {
        for (h in pendingRemovals) {
            h.itemView.alpha = 1f
            h.itemView.scaleX = 1f
            h.itemView.scaleY = 1f
            dispatchRemoveFinished(h)
        }
        pendingRemovals.clear()
        for (h in pendingAdds) {
            h.itemView.alpha = 1f
            h.itemView.scaleX = 1f
            h.itemView.scaleY = 1f
            dispatchAddFinished(h)
        }
        pendingAdds.clear()
        for (m in pendingMoves) {
            m.holder.itemView.translationX = 0f
            m.holder.itemView.translationY = 0f
            dispatchMoveFinished(m.holder)
        }
        pendingMoves.clear()
        dispatchAnimationsFinished()
    }

    override fun isRunning(): Boolean {
        return pendingAdds.isNotEmpty() ||
            pendingRemovals.isNotEmpty() ||
            pendingMoves.isNotEmpty() ||
            runningAddRemove.isNotEmpty()
    }

    private fun resetAnimation(holder: RecyclerView.ViewHolder) {
        holder.itemView.animate().cancel()
        SpringPreset.cancelForView(holder.itemView)
    }

    private fun dispatchFinishedWhenDone() {
        if (!isRunning) {
            dispatchAnimationsFinished()
        }
    }
}

package com.kayumars.launcher.ui.motion

import android.util.SparseArray
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.lang.ref.WeakReference

/**
 * SpringPreset — Pooled AndroidX SpringAnimation helper with interruptible retargeting.
 *
 * Presets:
 * - SNAP:   stiffness 1200f, damping 1.0f  (toggles, dismiss, cancel)
 * - BOUNCE: stiffness 380f,  damping 0.62f (long-press pop, icon release, install pop-in)
 * - GLIDE:  stiffness 200f,  damping 0.85f (drawer open, sheet expand)
 * - SNAPPY: stiffness 800f,  damping 0.9f  (drag settle, neighbor reflow)
 */
enum class SpringPreset(val stiffness: Float, val dampingRatio: Float) {
    SNAP(1200f, SpringForce.DAMPING_RATIO_NO_BOUNCE),
    BOUNCE(380f, 0.62f),
    GLIDE(200f, 0.85f),
    SNAPPY(800f, 0.90f),
    PAGE_SNAP(320f, 0.88f),
    FOLDER_SPRING(250f, 0.85f),
    MENU_SPRING(500f, 0.80f),
    SPOTLIGHT_GLIDE(200f, 0.85f);

    companion object {
        private val activeSprings = ArrayList<WeakReference<SpringAnimation>>(24)

        @JvmStatic
        fun animate(
            view: View,
            property: DynamicAnimation.ViewProperty,
            finalPosition: Float,
            preset: SpringPreset,
            startVelocity: Float = 0f,
            onEnd: (() -> Unit)? = null
        ): SpringAnimation? {
            if (MotionSpec.isReducedMotion(view.context)) {
                property.setValue(view, finalPosition)
                onEnd?.invoke()
                return null
            }

            val spring = obtainSpring(view, property)
            val force = spring.spring ?: SpringForce(finalPosition).also { spring.spring = it }
            force.stiffness = preset.stiffness
            force.dampingRatio = preset.dampingRatio
            force.finalPosition = finalPosition

            if (startVelocity != 0f) {
                spring.setStartVelocity(startVelocity)
            }

            if (onEnd != null) {
                val listener = object : DynamicAnimation.OnAnimationEndListener {
                    override fun onAnimationEnd(
                        animation: DynamicAnimation<*>?,
                        canceled: Boolean,
                        value: Float,
                        velocity: Float
                    ) {
                        spring.removeEndListener(this)
                        if (!canceled) {
                            onEnd.invoke()
                        }
                    }
                }
                spring.addEndListener(listener)
            }

            spring.animateToFinalPosition(finalPosition)
            return spring
        }

        @JvmStatic
        fun animateScale(
            view: View,
            targetScale: Float,
            preset: SpringPreset,
            startVelocity: Float = 0f,
            onEnd: (() -> Unit)? = null
        ) {
            animate(view, DynamicAnimation.SCALE_X, targetScale, preset, startVelocity, onEnd)
            animate(view, DynamicAnimation.SCALE_Y, targetScale, preset, startVelocity, null)
        }

        @JvmStatic
        fun cancelForView(view: View) {
            @Suppress("UNCHECKED_CAST")
            val pool = view.getTag(TAG_SPRING_POOL) as? SparseArray<SpringAnimation> ?: return
            for (i in 0 until pool.size()) {
                pool.valueAt(i)?.cancel()
            }
        }

        @JvmStatic
        fun cancelAll() {
            for (ref in activeSprings) {
                ref.get()?.cancel()
            }
            activeSprings.clear()
        }

        private fun obtainSpring(
            view: View,
            property: DynamicAnimation.ViewProperty
        ): SpringAnimation {
            @Suppress("UNCHECKED_CAST")
            var pool = view.getTag(TAG_SPRING_POOL) as? SparseArray<SpringAnimation>
            if (pool == null) {
                pool = SparseArray(4)
                view.setTag(TAG_SPRING_POOL, pool)
            }
            val key = System.identityHashCode(property)
            var anim = pool.get(key)
            if (anim == null) {
                anim = SpringAnimation(view, property)
                if (property === DynamicAnimation.SCALE_X || property === DynamicAnimation.SCALE_Y) {
                    anim.minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
                } else if (property === DynamicAnimation.ALPHA) {
                    anim.minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA
                } else {
                    anim.minimumVisibleChange = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
                }
                pool.put(key, anim)
                pruneDead()
                activeSprings.add(WeakReference(anim))
            }
            return anim
        }

        private fun pruneDead() {
            val it = activeSprings.iterator()
            while (it.hasNext()) {
                val s = it.next().get()
                if (s == null || !s.isRunning) {
                    it.remove()
                }
            }
        }

        private const val TAG_SPRING_POOL = 0x7F0A1201
    }
}

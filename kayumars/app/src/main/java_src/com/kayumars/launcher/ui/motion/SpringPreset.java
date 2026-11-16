package com.kayumars.launcher.ui.motion;

import android.util.Property;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.View;
import com.kayumars.launcher.R;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * SpringPreset — Pure-SDK 120 Hz Choreographer damped harmonic spring engine.
 * Preserves live velocity across rapid interruptible retargeting with zero AndroidX JARs.
 *
 * Presets:
 * - SNAP:   stiffness 1200f, damping 1.0f  (toggles, dismiss, cancel)
 * - BOUNCE: stiffness 380f,  damping 0.62f (long-press pop, icon release, install pop-in)
 * - GLIDE:  stiffness 200f,  damping 0.85f (drawer open, sheet expand)
 * - SNAPPY: stiffness 800f,  damping 0.9f  (drag settle, neighbor reflow)
 */
public enum SpringPreset {
    SNAP(1200f, 1.00f),
    BOUNCE(380f, 0.62f),
    GLIDE(200f, 0.85f),
    SNAPPY(800f, 0.90f),
    PAGE_SNAP(320f, 0.88f),
    FOLDER_SPRING(250f, 0.85f),
    MENU_SPRING(500f, 0.80f),
    SPOTLIGHT_GLIDE(200f, 0.85f);

    public final float stiffness;
    public final float dampingRatio;

    private static final int TAG_SPRING_POOL = R.id.tag_spring_pool;
    private static final List<WeakReference<SpringRunner>> ACTIVE_SPRINGS =
            new ArrayList<WeakReference<SpringRunner>>(24);

    SpringPreset(float stiffness, float dampingRatio) {
        this.stiffness = stiffness;
        this.dampingRatio = dampingRatio;
    }

    public static void animate(
            View view,
            Property<View, Float> property,
            float finalPosition,
            SpringPreset preset,
            float startVelocity,
            Runnable onEnd) {
        if (view == null || property == null) return;
        if (MotionSpec.isReducedMotion(view.getContext())) {
            property.set(view, finalPosition);
            if (onEnd != null) onEnd.run();
            return;
        }
        SpringRunner runner = obtainRunner(view, property);
        runner.retarget(finalPosition, preset, startVelocity, onEnd);
    }

    public static void animateScale(
            View view,
            float targetScale,
            SpringPreset preset,
            float startVelocity,
            Runnable onEnd) {
        animate(view, View.SCALE_X, targetScale, preset, startVelocity, onEnd);
        animate(view, View.SCALE_Y, targetScale, preset, startVelocity, null);
    }

    public static void cancelForView(View view) {
        if (view == null) return;
        Object tag = view.getTag(TAG_SPRING_POOL);
        if (tag instanceof SparseArray) {
            @SuppressWarnings("unchecked")
            SparseArray<SpringRunner> pool = (SparseArray<SpringRunner>) tag;
            for (int i = 0; i < pool.size(); i++) {
                SpringRunner r = pool.valueAt(i);
                if (r != null) r.cancel();
            }
        }
    }

    public static void cancelAll() {
        for (WeakReference<SpringRunner> ref : ACTIVE_SPRINGS) {
            SpringRunner r = ref.get();
            if (r != null) r.cancel();
        }
        ACTIVE_SPRINGS.clear();
    }

    private static SpringRunner obtainRunner(View view, Property<View, Float> property) {
        @SuppressWarnings("unchecked")
        SparseArray<SpringRunner> pool = (SparseArray<SpringRunner>) view.getTag(TAG_SPRING_POOL);
        if (pool == null) {
            pool = new SparseArray<SpringRunner>(4);
            view.setTag(TAG_SPRING_POOL, pool);
        }
        int key = System.identityHashCode(property);
        SpringRunner runner = pool.get(key);
        if (runner == null) {
            boolean isScaleOrAlpha = (property == View.SCALE_X || property == View.SCALE_Y || property == View.ALPHA);
            runner = new SpringRunner(view, property, isScaleOrAlpha ? 0.002f : 0.25f);
            pool.put(key, runner);
            pruneDead();
            ACTIVE_SPRINGS.add(new WeakReference<SpringRunner>(runner));
        }
        return runner;
    }

    private static void pruneDead() {
        Iterator<WeakReference<SpringRunner>> it = ACTIVE_SPRINGS.iterator();
        while (it.hasNext()) {
            SpringRunner r = it.next().get();
            if (r == null || !r.isRunning) {
                it.remove();
            }
        }
    }

    private static final class SpringRunner implements Choreographer.FrameCallback {
        private final WeakReference<View> viewRef;
        private final Property<View, Float> property;
        private final float valueThreshold;
        private final float velocityThreshold;

        private float value;
        private float velocity;
        private float target;
        private float stiffness;
        private float dampingRatio;
        private long lastFrameNs;
        private boolean isRunning = false;
        private Runnable endCallback;

        SpringRunner(View view, Property<View, Float> property, float valueThreshold) {
            this.viewRef = new WeakReference<View>(view);
            this.property = property;
            this.valueThreshold = valueThreshold;
            this.velocityThreshold = valueThreshold * 62.5f;
        }

        void retarget(float finalPos, SpringPreset preset, float startVel, Runnable onEnd) {
            View v = viewRef.get();
            if (v == null) return;
            if (!isRunning) {
                Float cur = property.get(v);
                this.value = cur != null ? cur : finalPos;
                this.velocity = startVel;
            } else if (startVel != 0f) {
                this.velocity = startVel;
            }
            this.target = finalPos;
            this.stiffness = preset.stiffness;
            this.dampingRatio = preset.dampingRatio;
            this.endCallback = onEnd;

            if (!isRunning) {
                isRunning = true;
                lastFrameNs = 0L;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        void cancel() {
            if (isRunning) {
                isRunning = false;
                Choreographer.getInstance().removeFrameCallback(this);
            }
            endCallback = null;
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isRunning) return;
            View v = viewRef.get();
            if (v == null) {
                isRunning = false;
                return;
            }

            if (lastFrameNs == 0L) {
                lastFrameNs = frameTimeNanos;
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }

            float dt = (frameTimeNanos - lastFrameNs) / 1_000_000_000f;
            lastFrameNs = frameTimeNanos;
            if (dt > 0.033f) dt = 0.033f;
            if (dt <= 0f) dt = 0.0083f;

            // Symplectic Euler sub-stepped twice for unconditional stability at high stiffness
            int subSteps = 2;
            float subDt = dt / subSteps;
            float c = 2.0f * dampingRatio * (float) Math.sqrt(stiffness);
            for (int i = 0; i < subSteps; i++) {
                float displacement = value - target;
                float accel = -stiffness * displacement - c * velocity;
                velocity += accel * subDt;
                value += velocity * subDt;
            }

            if (Math.abs(value - target) < valueThreshold && Math.abs(velocity) < velocityThreshold) {
                value = target;
                velocity = 0f;
                property.set(v, value);
                isRunning = false;
                Runnable cb = endCallback;
                endCallback = null;
                if (cb != null) cb.run();
                return;
            }

            property.set(v, value);
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
}

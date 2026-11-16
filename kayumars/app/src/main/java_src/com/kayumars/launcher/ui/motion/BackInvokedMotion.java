package com.kayumars.launcher.ui.motion;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * BackInvokedMotion — Predictive Back handler for Android 13/14+ (API 33/34+).
 * Uses static inner classes so older API 26-32 VMs never verify API 33/34 symbols.
 */
public final class BackInvokedMotion {

    public interface Host {
        View getActiveFrontSurface();
        boolean hasActiveOverlay();
        void onCommitPredictiveBack();
    }

    private final Activity activity;
    private final Host host;
    private Object registeredCallback;

    public BackInvokedMotion(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    public void register() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        unregister();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registeredCallback = Api34Helper.register(activity, host);
        } else {
            registeredCallback = Api33Helper.register(activity, host);
        }
    }

    public void unregister() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || registeredCallback == null) return;
        Api33Helper.unregister(activity, registeredCallback);
        registeredCallback = null;
    }

    private static final class Api33Helper {
        static Object register(Activity activity, final Host host) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null;
            OnBackInvokedCallback cb = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    if (host.hasActiveOverlay()) {
                        host.onCommitPredictiveBack();
                    }
                }
            };
            activity.getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            return cb;
        }

        static void unregister(Activity activity, Object cbObj) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
            if (cbObj instanceof OnBackInvokedCallback) {
                try {
                    activity.getOnBackInvokedDispatcher()
                            .unregisterOnBackInvokedCallback((OnBackInvokedCallback) cbObj);
                } catch (Exception ignored) {}
            }
        }
    }

    private static final class Api34Helper {
        static Object register(Activity activity, final Host host) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null;
            OnBackAnimationCallback cb = new OnBackAnimationCallback() {
                @Override
                public void onBackStarted(BackEvent backEvent) {
                    if (!host.hasActiveOverlay()) return;
                    View v = host.getActiveFrontSurface();
                    if (v != null) {
                        v.setPivotX(v.getWidth() * 0.5f);
                        v.setPivotY(v.getHeight() * 0.5f);
                    }
                }

                @Override
                public void onBackProgressed(BackEvent backEvent) {
                    if (!host.hasActiveOverlay()) return;
                    View v = host.getActiveFrontSurface();
                    if (v != null) {
                        float progress = Math.max(0f, Math.min(1f, backEvent.getProgress()));
                        float scale = 1f - 0.10f * progress;
                        v.setScaleX(scale);
                        v.setScaleY(scale);
                        v.setAlpha(1f - 0.28f * progress);
                    }
                }

                @Override
                public void onBackInvoked() {
                    if (host.hasActiveOverlay()) {
                        host.onCommitPredictiveBack();
                    }
                }

                @Override
                public void onBackCancelled() {
                    View v = host.getActiveFrontSurface();
                    if (v != null) {
                        SpringPreset.animateScale(v, 1f, SpringPreset.SNAP, 0f, null);
                        v.animate()
                                .alpha(1f)
                                .setDuration(MotionSpec.DURATION_FAST_MS)
                                .setInterpolator(MotionSpec.STANDARD)
                                .start();
                    }
                }
            };
            activity.getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            return cb;
        }
    }
}

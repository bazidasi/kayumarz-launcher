package com.kayumars.launcher.ui.motion;

import android.view.Choreographer;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;

/**
 * MotionDebug — Lightweight Choreographer.FrameCallback frame-time HUD.
 */
public final class MotionDebug implements Choreographer.FrameCallback {

    private final TextView hudView;
    private boolean enabled = false;
    private long lastFrameNs = 0L;
    private int droppedFrames = 0;
    private int frameCount = 0;

    public MotionDebug(TextView hudView) {
        this.hudView = hudView;
    }

    public void setEnabled(boolean enable) {
        if (this.enabled == enable) return;
        this.enabled = enable;
        if (hudView == null) return;
        if (enable) {
            lastFrameNs = 0L;
            droppedFrames = 0;
            frameCount = 0;
            hudView.setVisibility(View.VISIBLE);
            hudView.setText("120Hz HUD · 0 drop");
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
            hudView.setVisibility(View.GONE);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!enabled) return;
        if (lastFrameNs != 0L) {
            float deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000f;
            frameCount++;
            if (deltaMs > 16.9f) {
                droppedFrames++;
            }
            if (frameCount % 6 == 0 && hudView != null) {
                hudView.setText(String.format(Locale.US, "%.1fms | drop:%d", deltaMs, droppedFrames));
            }
        }
        lastFrameNs = frameTimeNanos;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void stop() {
        enabled = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }
}

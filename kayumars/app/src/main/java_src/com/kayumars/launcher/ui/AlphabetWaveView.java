package com.kayumars.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * AlphabetWaveView - Niagara-style curved alphabet index bar (`★ # A..Z ○`).
 * Zero per-frame allocations in onDraw(): pre-allocated Paints and static char array.
 * Supports full RTL mirroring: curves rightward when on left edge, leftward when on right edge.
 */
public class AlphabetWaveView extends View {

    public interface OnLetterSelectListener {
        void onLetterSelected(String section, int index, boolean isActionUp);
    }

    private static final String[] SECTIONS = {
        "★", "#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
        "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",
        "W", "X", "Y", "Z", "○"
    };

    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeLetterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnLetterSelectListener listener;
    private int activeIndex = -1;
    private float touchY = -1f;
    private boolean isTouching = false;

    private float density;
    private float stripWidthPx;
    private float maxWaveOffsetPx;
    private float bubbleRadiusPx;

    public AlphabetWaveView(Context context) {
        super(context);
        init(context);
    }

    public AlphabetWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AlphabetWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        stripWidthPx = 44f * density;
        maxWaveOffsetPx = 38f * density;
        bubbleRadiusPx = 26f * density;

        letterPaint.setColor(0xE6FFFFFF);
        letterPaint.setTextSize(11.5f * density);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        activeLetterPaint.setColor(0xFFFFFFFF);
        activeLetterPaint.setTextSize(14f * density);
        activeLetterPaint.setTextAlign(Paint.Align.CENTER);
        activeLetterPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        bubbleBgPaint.setColor(0xCC6E5FA8);
        bubbleBgPaint.setStyle(Paint.Style.FILL);

        bubbleTextPaint.setColor(Color.WHITE);
        bubbleTextPaint.setTextSize(22f * density);
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    }

    public void setOnLetterSelectListener(OnLetterSelectListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float topPad = getPaddingTop() + 8f * density;
        float bottomPad = getPaddingBottom() + 8f * density;
        float usableHeight = Math.max(1f, height - topPad - bottomPad);
        float stepY = usableHeight / SECTIONS.length;

        // Baseline X anchored near end edge
        float anchorX = isRtl ? (20f * density) : (width - 20f * density);
        float waveDir = isRtl ? 1f : -1f;
        float waveRadius = stepY * 5.2f;

        for (int i = 0; i < SECTIONS.length; i++) {
            float cy = topPad + stepY * (i + 0.5f);
            float offset = 0f;

            if (isTouching && touchY >= 0f) {
                float dist = Math.abs(cy - touchY);
                if (dist < waveRadius) {
                    float norm = dist / waveRadius;
                    // Smooth raised-cosine Niagara arc
                    offset = (float) (0.5 * (1.0 + Math.cos(Math.PI * norm))) * maxWaveOffsetPx;
                }
            }

            float x = anchorX + (offset * waveDir);
            Paint p = (i == activeIndex) ? activeLetterPaint : letterPaint;
            float textBaseline = cy - ((p.descent() + p.ascent()) / 2f);
            canvas.drawText(SECTIONS[i], x, textBaseline, p);
        }

        // Draw floating frosted wave bubble when scrubbing
        if (isTouching && activeIndex >= 0 && activeIndex < SECTIONS.length) {
            float bubbleY = Math.max(bubbleRadiusPx + topPad, Math.min(height - bubbleRadiusPx - bottomPad, touchY));
            float bubbleX = isRtl
                    ? (anchorX + maxWaveOffsetPx + bubbleRadiusPx + 22f * density)
                    : (anchorX - maxWaveOffsetPx - bubbleRadiusPx - 22f * density);

            canvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx, bubbleBgPaint);
            float textY = bubbleY - ((bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2f);
            canvas.drawText(SECTIONS[activeIndex], bubbleX, textY, bubbleTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int width = getWidth();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        // Only capture initial DOWN within the end alphabet strip so list scrolling remains smooth
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            boolean inStrip = isRtl ? (x <= stripWidthPx + 16f * density) : (x >= width - stripWidthPx - 16f * density);
            if (!inStrip) {
                return false;
            }
            isTouching = true;
            updateTouch(event.getY(), false);
            return true;
        }

        if (!isTouching) return false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                updateTouch(event.getY(), false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateTouch(event.getY(), true);
                isTouching = false;
                activeIndex = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateTouch(float y, boolean isActionUp) {
        touchY = y;
        float topPad = getPaddingTop() + 8f * density;
        float bottomPad = getPaddingBottom() + 8f * density;
        float usableHeight = Math.max(1f, getHeight() - topPad - bottomPad);
        float rel = (y - topPad) / usableHeight;
        int idx = Math.max(0, Math.min(SECTIONS.length - 1, (int) (rel * SECTIONS.length)));

        if (idx != activeIndex || isActionUp) {
            activeIndex = idx;
            if (listener != null) {
                listener.onLetterSelected(SECTIONS[idx], idx, isActionUp);
            }
        }
        invalidate();
    }
}

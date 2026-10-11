package com.psthetech.swara.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.ui.glass.LiquidGlassRenderer;
import com.psthetech.swara.ui.theme.DesignTokens;

/**
 * Apple Music style scrubber: a thin flat capsule track with no visible thumb.
 * The track grows taller while it is being dragged (direct manipulation feedback),
 * and the user can grab it anywhere along its length.
 */
public class AppleScrubberView extends View {

    public interface OnScrubListener {
        void onScrubStart(AppleScrubberView view);
        void onScrubMove(AppleScrubberView view, long positionMs);
        void onScrubEnd(AppleScrubberView view, long positionMs);
    }

    private static final float IDLE_HEIGHT_DP = 6f;
    private static final float ACTIVE_HEIGHT_DP = 12f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private long max = 0;
    private long progress = 0;
    private boolean scrubbing = false;
    private float heightFraction = 0f; // 0 = idle, 1 = active
    @Nullable private ValueAnimator heightAnimator;
    @Nullable private OnScrubListener listener;

    public AppleScrubberView(Context context) { this(context, null); }

    public AppleScrubberView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        trackPaint.setColor(Color.argb(56, 255, 255, 255));
        fillPaint.setColor(Color.argb(220, 255, 255, 255));
        setFocusable(true);
        setClickable(true);
    }

    public void setOnScrubListener(@Nullable OnScrubListener l) { listener = l; }

    public void setMax(long max) {
        this.max = Math.max(0, max);
        invalidate();
    }

    public long getMax() { return max; }

    public void setProgress(long p) {
        if (scrubbing) return; // never fight the finger
        progress = clamp(p);
        invalidate();
    }

    public long getProgress() { return progress; }

    public boolean isScrubbing() { return scrubbing; }

    public void setDesignTokens(@NonNull DesignTokens tokens) {
        int base = tokens.getTextPrimaryColor();
        int r = Color.red(base), g = Color.green(base), b = Color.blue(base);
        trackPaint.setColor(Color.argb(tokens.isNightMode() ? 56 : 40, r, g, b));
        fillPaint.setColor(Color.argb(tokens.isNightMode() ? 220 : 200, r, g, b));
        invalidate();
    }

    /** Lets an artwork-tinted background drive the scrubber colours. */
    public void setColors(int trackColor, int fillColor) {
        trackPaint.setColor(trackColor);
        fillPaint.setColor(fillColor);
        invalidate();
    }

    private long clamp(long v) {
        if (max <= 0) return Math.max(0, v);
        return Math.max(0, Math.min(v, max));
    }

    private long positionFor(float x) {
        float left = getPaddingLeft();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (width <= 0 || max <= 0) return 0;
        float ratio = Math.max(0f, Math.min(1f, (x - left) / width));
        return (long) (ratio * max);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = Math.round(32 * density); // generous touch target around the thin track
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float h = (IDLE_HEIGHT_DP + (ACTIVE_HEIGHT_DP - IDLE_HEIGHT_DP) * heightFraction) * density;
        float cy = getHeight() / 2f;
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float radius = h / 2f;
        rect.set(left, cy - h / 2f, right, cy + h / 2f);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);
        if (max > 0 && progress > 0) {
            float x = left + (right - left) * ((float) progress / (float) max);
            canvas.save();
            canvas.clipRect(left, rect.top, x, rect.bottom);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || max <= 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                scrubbing = true;
                animateHeight(1f);
                progress = positionFor(e.getX());
                if (listener != null) {
                    listener.onScrubStart(this);
                    listener.onScrubMove(this, progress);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                progress = positionFor(e.getX());
                if (listener != null) listener.onScrubMove(this, progress);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                // fall through
            case MotionEvent.ACTION_CANCEL:
                scrubbing = false;
                animateHeight(0f);
                if (listener != null) listener.onScrubEnd(this, progress);
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Interruptible: always animates from the current fraction. */
    private void animateHeight(float target) {
        if (heightAnimator != null) heightAnimator.cancel();
        if (LiquidGlassRenderer.isReducedMotion(getContext())) {
            heightFraction = target;
            invalidate();
            return;
        }
        heightAnimator = ValueAnimator.ofFloat(heightFraction, target);
        heightAnimator.setDuration(target > heightFraction ? 140 : 280);
        heightAnimator.setInterpolator(LiquidGlassRenderer.SPRING_CRITICAL);
        heightAnimator.addUpdateListener(a -> {
            heightFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        heightAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (heightAnimator != null) heightAnimator.cancel();
        super.onDetachedFromWindow();
    }

    // Seek-bar semantics for TalkBack, with volume-key style +/- 5 s adjustments.
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, (float) max, (float) progress));
        if (max > 0) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable android.os.Bundle args) {
        if (max > 0 && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            long delta = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 5000 : -5000;
            progress = clamp(progress + delta);
            invalidate();
            if (listener != null) listener.onScrubEnd(this, progress);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}

package com.psthetech.swara.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

import com.psthetech.swara.audio.EqPresets;

/** Vertical slider for one equalizer band: top = +12 dB, middle = 0, bottom = -12 dB. */
public class EqBandSlider extends View {

    public interface OnChange { void onLevel(int mb, boolean fromUser); }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int levelMb;
    private boolean enabledLook = true;
    @Nullable private OnChange listener;
    private final float dp;

    public EqBandSlider(Context c) { this(c, null); }

    public EqBandSlider(Context c, @Nullable AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        track.setColor(0x33FFFFFF);
        fill.setColor(0xFF1DB954);
        thumb.setColor(0xFFFFFFFF);
        setFocusable(true);
    }

    public void setColors(int trackColor, int accent, int thumbColor) {
        track.setColor(trackColor);
        fill.setColor(accent);
        thumb.setColor(thumbColor);
        invalidate();
    }

    /** Dim the control while the equalizer is switched off (still adjustable). */
    public void setActiveLook(boolean on) {
        enabledLook = on;
        setAlpha(on ? 1f : 0.5f);
    }

    public void setOnChange(@Nullable OnChange l) { listener = l; }

    public int getLevelMb() { return levelMb; }

    public void setLevelMb(int mb) {
        int snapped = EqPresets.snap(mb);
        if (snapped == levelMb) return;
        levelMb = snapped;
        invalidate();
    }

    private float padV() { return 18 * dp; }

    private float yFor(int mb) {
        float top = padV(), bottom = getHeight() - padV();
        float t = (EqPresets.RANGE_MB - mb) / (2f * EqPresets.RANGE_MB);
        return top + t * (bottom - top);
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float tw = 4 * dp;
        rect.set(cx - tw / 2, padV(), cx + tw / 2, getHeight() - padV());
        canvas.drawRoundRect(rect, tw, tw, track);
        float y0 = yFor(0), y = yFor(levelMb);
        rect.set(cx - tw / 2, Math.min(y0, y), cx + tw / 2, Math.max(y0, y));
        canvas.drawRoundRect(rect, tw, tw, fill);
        canvas.drawCircle(cx, y, 11 * dp, thumb);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through
            case MotionEvent.ACTION_MOVE:
                float top = padV(), bottom = getHeight() - padV();
                float t = (Math.max(top, Math.min(bottom, e.getY())) - top) / (bottom - top);
                int mb = EqPresets.snap(Math.round(EqPresets.RANGE_MB - t * 2 * EqPresets.RANGE_MB));
                if (mb != levelMb) {
                    levelMb = mb;
                    invalidate();
                    if (listener != null) listener.onLevel(mb, true);
                }
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    // TalkBack: swipe up/down adjusts by 1 dB.
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.setContentDescription(getContentDescription() + " " + (levelMb / 100) + " dB");
    }

    @Override public boolean performAccessibilityAction(int action, @Nullable android.os.Bundle args) {
        int delta = 0;
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) delta = EqPresets.STEP_MB;
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) delta = -EqPresets.STEP_MB;
        if (delta != 0) {
            int mb = EqPresets.snap(levelMb + delta);
            if (mb != levelMb) {
                levelMb = mb;
                invalidate();
                if (listener != null) listener.onLevel(mb, true);
            }
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}

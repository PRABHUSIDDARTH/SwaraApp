package com.psthetech.swara.ui.deck;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.ui.glass.LiquidGlassRenderer;

/** Draws a physical key with press travel; shared by the image and layout keys. */
final class DeckKeyPainter {

    private static final float TRAVEL_DP = 4f;
    private static final float SIDE_INSET_DP = 2f;
    private static final float CORNER_DP = 11f;

    private final View host;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    @Nullable private DeckPalette palette;
    @Nullable private ValueAnimator animator;
    private float pressed; // 0 = up, 1 = fully down
    private float target;
    private boolean primary;
    private boolean lit;

    DeckKeyPainter(@NonNull View host) {
        this.host = host;
        density = host.getResources().getDisplayMetrics().density;
    }

    void setPalette(@NonNull DeckPalette palette) {
        this.palette = palette;
        host.invalidate();
    }

    /** The primary key (play/pause) has an accent cap. */
    void setPrimary(boolean primary) {
        this.primary = primary;
        host.invalidate();
    }

    /** Shows the indicator lamp of a toggle that is on. */
    void setLit(boolean lit) {
        if (this.lit == lit) return;
        this.lit = lit;
        host.invalidate();
    }

    void onPressedChanged(boolean down) {
        float next = down ? 1f : 0f;
        if (next == target) return;
        target = next;
        if (animator != null) animator.cancel();
        if (!host.isAttachedToWindow() || LiquidGlassRenderer.isReducedMotion(host.getContext())) {
            pressed = next;
            host.invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(pressed, next);
        animator.setDuration(down ? 50 : 120);
        animator.addUpdateListener(a -> {
            pressed = (float) a.getAnimatedValue();
            host.invalidate();
        });
        animator.start();
    }

    void cancel() {
        if (animator != null) animator.cancel();
        pressed = target;
    }

    /** How far the cap's centre is from the view's centre, for placing the icon on it. */
    float contentOffset() {
        return (pressed - 0.5f) * TRAVEL_DP * density;
    }

    void draw(@NonNull Canvas canvas) {
        DeckPalette p = palette;
        if (p == null) return;
        float inset = SIDE_INSET_DP * density;
        float travel = TRAVEL_DP * density;
        float corner = CORNER_DP * density;
        float left = inset;
        float right = host.getWidth() - inset;
        float bottom = host.getHeight();

        // Well and side wall, then the cap sliding down over them.
        rect.set(left, travel + density, right, bottom - density);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(primary ? p.accentSide : p.capSide);
        canvas.drawRoundRect(rect, corner, corner, paint);

        float edge = density; // keeps the outline from being clipped at the top and bottom
        rect.set(left, edge + pressed * travel, right,
                bottom - travel - edge + pressed * travel);
        paint.setColor(primary ? p.accent : p.cap);
        canvas.drawRoundRect(rect, corner, corner, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((host.isFocused() ? 2.5f : 1f) * density);
        paint.setColor(host.isFocused() ? p.accentText : primary ? p.accent : p.capEdge);
        canvas.drawRoundRect(rect, corner, corner, paint);

        if (lit) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(primary ? p.onAccent : p.accentText);
            canvas.drawCircle(host.getWidth() / 2f, rect.top + 6f * density, 2.5f * density, paint);
        }
    }
}

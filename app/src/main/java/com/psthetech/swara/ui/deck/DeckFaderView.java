package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.ui.widget.AppleScrubberView;

/**
 * A chunky seek fader. Extends {@link AppleScrubberView} so touch handling, the scrub listener
 * and TalkBack seek-bar actions stay the same; only the drawing is different.
 */
public class DeckFaderView extends AppleScrubberView {

    private static final float CAP_WIDTH_DP = 28f;
    private static final float CAP_HEIGHT_DP = 36f;
    private static final float SLOT_HEIGHT_DP = 8f;
    private static final int TICKS = 21;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    @Nullable private DeckPalette palette;

    public DeckFaderView(Context context) { this(context, null); }

    public DeckFaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        // The padding is half a cap, so the inherited touch mapping matches the cap's travel.
        int side = Math.round(CAP_WIDTH_DP / 2f * density);
        setPadding(side, 0, side, 0);
    }

    public void setPalette(@NonNull DeckPalette palette) {
        this.palette = palette;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        DeckPalette p = palette;
        if (p == null) return;
        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float cy = getHeight() / 2f;
        float half = SLOT_HEIGHT_DP / 2f * density;

        // Scale ticks above and below the slot.
        paint.setColor(p.textTertiary);
        paint.setStrokeWidth(density);
        for (int i = 0; i < TICKS; i++) {
            float x = left + (right - left) * i / (TICKS - 1);
            float length = (i % 5 == 0 ? 6f : 3f) * density;
            canvas.drawLine(x, cy - half - 5f * density - length, x, cy - half - 5f * density, paint);
        }

        float x = DeckFaderMath.capCentre(
                DeckFaderMath.fraction(getProgress(), getMax()), left, right);
        rect.set(left - half, cy - half, right + half, cy + half);
        paint.setColor(p.well);
        canvas.drawRoundRect(rect, half, half, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(p.capEdge);
        canvas.drawRoundRect(rect, half, half, paint);
        paint.setStyle(Paint.Style.FILL);
        rect.set(left - half, cy - half / 2f, x, cy + half / 2f);
        paint.setColor(p.accent);
        canvas.drawRoundRect(rect, half / 2f, half / 2f, paint);

        float w = CAP_WIDTH_DP / 2f * density;
        float h = Math.min(CAP_HEIGHT_DP * density, getHeight() - 6f * density) / 2f;
        float corner = 5f * density;
        // A held cap sits closer to the body, so its shadow shortens.
        float lift = (isScrubbing() ? 1.5f : 3.5f) * density;
        rect.set(x - w, cy - h + lift, x + w, cy + h + lift);
        paint.setColor(p.shadow);
        canvas.drawRoundRect(rect, corner, corner, paint);
        rect.offset(0f, -lift);
        paint.setColor(p.cap);
        canvas.drawRoundRect(rect, corner, corner, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(p.capEdge);
        canvas.drawRoundRect(rect, corner, corner, paint);
        paint.setStyle(Paint.Style.FILL);

        // Finger grips, with the middle one marking the exact position.
        paint.setStrokeWidth(2f * density);
        for (int i = -1; i <= 1; i++) {
            paint.setColor(i == 0 ? p.accentText : p.textTertiary);
            float gx = x + i * 6f * density;
            canvas.drawLine(gx, cy - h * 0.55f, gx, cy + h * 0.55f, paint);
        }
    }
}

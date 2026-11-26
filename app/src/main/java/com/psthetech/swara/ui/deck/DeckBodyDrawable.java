package com.psthetech.swara.ui.deck;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The matte body: a rounded panel with a screw in each corner. */
public final class DeckBodyDrawable extends Drawable {

    private static final float MARGIN_DP = 6f;
    private static final float CORNER_DP = 22f;
    private static final float SCREW_INSET_DP = 13f;
    private static final float SCREW_RADIUS_DP = 5f;
    /** Slot angle of each screw, so they do not all look machine-aligned. */
    private static final float[] SLOT_DEGREES = {25f, -50f, 70f, -10f};

    private final DeckPalette palette;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panel = new RectF();

    public DeckBodyDrawable(@NonNull DeckPalette palette, float density) {
        this.palette = palette;
        this.density = density;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        panel.set(bounds);
        panel.inset(MARGIN_DP * density, MARGIN_DP * density);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.background);
        canvas.drawRect(getBounds(), paint);

        float corner = CORNER_DP * density;
        paint.setColor(palette.body);
        canvas.drawRoundRect(panel, corner, corner, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(palette.bodyEdge);
        canvas.drawRoundRect(panel, corner, corner, paint);

        float inset = SCREW_INSET_DP * density;
        screw(canvas, panel.left + inset, panel.top + inset, SLOT_DEGREES[0]);
        screw(canvas, panel.right - inset, panel.top + inset, SLOT_DEGREES[1]);
        screw(canvas, panel.left + inset, panel.bottom - inset, SLOT_DEGREES[2]);
        screw(canvas, panel.right - inset, panel.bottom - inset, SLOT_DEGREES[3]);
    }

    private void screw(Canvas canvas, float x, float y, float slotDegrees) {
        float r = SCREW_RADIUS_DP * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.shadow);
        canvas.drawCircle(x, y + density, r + density, paint);
        paint.setColor(palette.metal);
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(palette.textTertiary);
        double a = Math.toRadians(slotDegrees);
        float dx = (float) Math.cos(a) * r * 0.65f;
        float dy = (float) Math.sin(a) * r * 0.65f;
        canvas.drawLine(x - dx, y - dy, x + dx, y + dy, paint);
        canvas.drawLine(x + dy, y - dx, x - dy, y + dx, paint);
    }

    @Override public void setAlpha(int alpha) { /* always opaque */ }

    @Override public void setColorFilter(@Nullable ColorFilter filter) { /* not tinted */ }

    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}

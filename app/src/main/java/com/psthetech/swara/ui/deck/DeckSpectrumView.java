package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Segmented spectrum bars. Levels are 0..1 per band. */
public class DeckSpectrumView extends View {

    public static final int BANDS = 16;
    private static final int SEGMENTS = 10;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[BANDS];
    @Nullable private DeckPalette palette;

    public DeckSpectrumView(Context context) { this(context, null); }

    public DeckSpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
    }

    public void setPalette(@NonNull DeckPalette palette) {
        this.palette = palette;
        invalidate();
    }

    /** Copies the levels; the caller may reuse its array. */
    public void setLevels(@NonNull float[] values) {
        System.arraycopy(values, 0, levels, 0, Math.min(values.length, BANDS));
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (palette == null) return;
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float column = width / BANDS;
        float segment = height / SEGMENTS;
        float barWidth = column * 0.68f;
        float barHeight = Math.max(density, segment - 1.5f * density);
        for (int band = 0; band < BANDS; band++) {
            int lit = Math.round(Math.max(0f, Math.min(1f, levels[band])) * SEGMENTS);
            float left = getPaddingLeft() + band * column + (column - barWidth) / 2f;
            for (int s = 0; s < SEGMENTS; s++) {
                float bottom = getPaddingTop() + height - s * segment;
                paint.setColor(s < lit ? palette.accentText : palette.dotOff);
                canvas.drawRect(left, bottom - barHeight, left + barWidth, bottom, paint);
            }
        }
    }
}

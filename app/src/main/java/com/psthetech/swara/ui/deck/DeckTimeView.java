package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.R;
import com.psthetech.swara.util.TimeFormatter;

/** Dot-matrix time: large elapsed time over a smaller total. */
public class DeckTimeView extends View {

    private static final float BIG_PITCH_DP = 3.6f;
    private static final float SMALL_PITCH_DP = 2.2f;
    private static final float LINE_GAP_DP = 6f;
    /** Columns reserved for "0:00:00", so the layout does not move as digits are added. */
    private static final int RESERVED_CHARS = 7;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final char[] elapsed = new char[DeckTimeText.MAX_LENGTH];
    private final char[] total = new char[DeckTimeText.MAX_LENGTH + 1];
    private int elapsedLength;
    private int totalLength;
    @Nullable private DeckPalette palette;

    public DeckTimeView(Context context) { this(context, null); }

    public DeckTimeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        setTimes(0, 0);
    }

    public void setPalette(@NonNull DeckPalette palette) {
        this.palette = palette;
        invalidate();
    }

    public void setTimes(long elapsedMs, long totalMs) {
        elapsedLength = DeckTimeText.write(elapsedMs, elapsed, 0);
        total[0] = '/';
        totalLength = 1 + DeckTimeText.write(totalMs, total, 1);
        // Spoken form of the dots; built here, never in onDraw.
        setContentDescription(getResources().getString(R.string.cd_deck_time,
                TimeFormatter.formatAccessible(elapsedMs),
                TimeFormatter.formatAccessible(totalMs)));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float width = RESERVED_CHARS * (DeckDotFont.COLUMNS + 1) * BIG_PITCH_DP * density;
        float height = (DeckDotFont.ROWS * (BIG_PITCH_DP + SMALL_PITCH_DP) + LINE_GAP_DP) * density;
        setMeasuredDimension(
                resolveSize(Math.round(width) + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(Math.round(height) + getPaddingTop() + getPaddingBottom(), heightSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (palette == null) return;
        // Shrink both lines together if the window is shorter than they need.
        float needed = (DeckDotFont.ROWS * (BIG_PITCH_DP + SMALL_PITCH_DP) + LINE_GAP_DP) * density;
        float fit = Math.min(1f, (getHeight() - getPaddingTop() - getPaddingBottom()) / needed);
        float big = BIG_PITCH_DP * density * fit;
        float small = SMALL_PITCH_DP * density * fit;
        float top = getPaddingTop();
        draw(canvas, elapsed, elapsedLength, top, big, palette.accentText, true);
        draw(canvas, total, totalLength, top + DeckDotFont.ROWS * big + LINE_GAP_DP * density * fit,
                small, palette.textSecondary, false);
    }

    /** Unlit dots are drawn only on the large line; behind small digits they hurt reading. */
    private void draw(Canvas canvas, char[] text, int length, float top, float pitch, int color,
                      boolean showUnlit) {
        float x = getPaddingLeft();
        float dot = pitch * 0.42f;
        for (int i = 0; i < length; i++) {
            for (int row = 0; row < DeckDotFont.ROWS; row++) {
                for (int column = 0; column < DeckDotFont.COLUMNS; column++) {
                    boolean on = DeckDotFont.isLit(text[i], column, row);
                    if (!on && !showUnlit) continue;
                    paint.setColor(on ? color : palette.dotOff);
                    canvas.drawCircle(x + (column + 0.5f) * pitch,
                            top + (row + 0.5f) * pitch, dot, paint);
                }
            }
            x += (DeckDotFont.COLUMNS + 1) * pitch;
        }
    }
}

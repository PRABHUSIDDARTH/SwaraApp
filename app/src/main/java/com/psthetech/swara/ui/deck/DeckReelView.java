package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.ui.widget.DiscSpinPhysics;

/**
 * One large tape reel: a flange with three rounded windows over the tape pack, the cover art as
 * the hub label and a marker dot that makes rotation visible. Drawing only for now; spin and
 * scrubbing are connected after the static screen is approved.
 */
public class DeckReelView extends View {

    private static final int DEFAULT_SIZE_DP = 220;
    private static final float WINDOW_SWEEP_DEG = 78f;
    private static final float SPINDLE_RING_DP = 1.75f;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF windowArc = new RectF();
    private final Matrix artMatrix = new Matrix();

    @Nullable private DeckPalette palette;
    @Nullable private Bitmap art;
    private float rotationDeg;
    private float cx, cy, radius, labelRadius, windowRadius, windowWidth;

    public DeckReelView(Context context) { this(context, null); }

    public DeckReelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
    }

    public void setPalette(@NonNull DeckPalette palette) {
        this.palette = palette;
        invalidate();
    }

    public void setArtworkBitmap(@Nullable Bitmap bitmap) {
        art = bitmap;
        artPaint.setShader(bitmap == null ? null
                : new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        fitArt();
        invalidate();
    }

    public void setRotationDegrees(float degrees) {
        rotationDeg = DiscSpinPhysics.wrap(degrees);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int fallback = Math.round(DEFAULT_SIZE_DP * density);
        setMeasuredDimension(
                resolveSize(fallback, widthSpec),
                resolveSize(Math.max(fallback, getSuggestedMinimumHeight()), heightSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.max(0f, Math.min(w, h) / 2f - 6f * density);
        labelRadius = radius * 0.63f;
        windowRadius = radius * 0.795f;
        windowWidth = radius * 0.13f;
        windowArc.set(cx - windowRadius, cy - windowRadius, cx + windowRadius, cy + windowRadius);
        fitArt();
    }

    private void fitArt() {
        Shader shader = artPaint.getShader();
        if (shader == null || art == null || labelRadius <= 0f) return;
        float scale = 2f * labelRadius / Math.min(art.getWidth(), art.getHeight());
        artMatrix.setScale(scale, scale);
        artMatrix.postTranslate(cx - art.getWidth() * scale / 2f, cy - art.getHeight() * scale / 2f);
        shader.setLocalMatrix(artMatrix);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        DeckPalette p = palette;
        if (p == null || radius <= 0f) return;

        // The shadow stays put while the reel turns.
        fill(p.shadow);
        canvas.drawCircle(cx, cy + 5f * density, radius, paint);

        canvas.save();
        canvas.rotate(rotationDeg, cx, cy);

        fill(p.cap);
        canvas.drawCircle(cx, cy, radius, paint);
        stroke(p.capEdge, 2f * density);
        canvas.drawCircle(cx, cy, radius - density, paint);
        stroke(p.bodyEdge, density);
        canvas.drawCircle(cx, cy, radius * 0.915f, paint);

        // Windows: a rim, then the dark tape pack seen through them.
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            // Window centres at 90, 210 and 330 degrees leave a solid spoke at 12 o'clock.
            float start = i * 120f + 90f - WINDOW_SWEEP_DEG / 2f;
            stroke(p.capEdge, windowWidth + 3f * density);
            canvas.drawArc(windowArc, start, WINDOW_SWEEP_DEG, false, paint);
            stroke(p.tape, windowWidth);
            canvas.drawArc(windowArc, start, WINDOW_SWEEP_DEG, false, paint);
            stroke(p.tapeLine, density);
            for (int ring = -2; ring <= 2; ring++) {
                float r = windowRadius + ring * windowWidth * 0.16f;
                windowArc.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(windowArc, start, WINDOW_SWEEP_DEG, false, paint);
            }
            windowArc.set(cx - windowRadius, cy - windowRadius,
                    cx + windowRadius, cy + windowRadius);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);

        // Hub label with the cover art.
        fill(p.labelRim);
        canvas.drawCircle(cx, cy, labelRadius + 5f * density, paint);
        if (artPaint.getShader() != null) {
            canvas.drawCircle(cx, cy, labelRadius, artPaint);
        } else {
            fill(p.well);
            canvas.drawCircle(cx, cy, labelRadius, paint);
        }

        // Spindle with three small pin holes. It sits on the cover art, so it gets two rings of
        // opposite tones (text and cap colour): whatever the art, one of them stands out.
        float spindle = radius * 0.085f;
        fill(p.text);
        canvas.drawCircle(cx, cy, spindle + 2f * SPINDLE_RING_DP * density, paint);
        fill(p.cap);
        canvas.drawCircle(cx, cy, spindle + SPINDLE_RING_DP * density, paint);
        fill(p.metal);
        canvas.drawCircle(cx, cy, spindle, paint);
        fill(p.textTertiary);
        canvas.drawCircle(cx, cy, radius * 0.026f, paint);
        for (int i = 0; i < 3; i++) {
            canvas.drawCircle(cx, cy - radius * 0.055f, radius * 0.013f, paint);
            canvas.rotate(120f, cx, cy);
        }

        // Marker dot, on the solid spoke at 12 o'clock.
        fill(p.accent);
        canvas.drawCircle(cx, cy - windowRadius, radius * 0.045f, paint);

        canvas.restore();
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void stroke(int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
    }
}

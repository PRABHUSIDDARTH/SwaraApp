package com.psthetech.swara.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * Utility to generate multi-frame wavy slider bitmaps for the Swara AppWidget RemoteViews.
 *
 * It renders the Material You-style squiggly wavy slider:
 *  - Left of thumb: An undulating sinusoidal wave in the theme's accent color (animates across frames).
 *  - Thumb knob: A smooth circular knob positioned at progressRatio.
 *  - Right of thumb: A straight muted horizontal track.
 *  - When paused: Flattens to a smooth straight bar up to the thumb knob.
 */
public final class WidgetWaveHelper {

    private WidgetWaveHelper() { /* static only */ }

    public static final int FRAME_COUNT = 4;

    /**
     * Generate an array of 4 wave slider Bitmaps representing the undulating sound vibration.
     */
    public static Bitmap[] generateWavySliderFrames(Context context, int widthPx, int heightPx,
                                                    int accentColor, float progressRatio) {
        if (widthPx <= 0) widthPx = Math.round(220 * context.getResources().getDisplayMetrics().density);
        if (heightPx <= 0) heightPx = Math.round(22 * context.getResources().getDisplayMetrics().density);

        Bitmap[] frames = new Bitmap[FRAME_COUNT];
        float phaseStep = (float) ((2.0 * Math.PI) / FRAME_COUNT);

        for (int i = 0; i < FRAME_COUNT; i++) {
            float phase = i * phaseStep;
            frames[i] = renderWavySliderBitmap(context, widthPx, heightPx, accentColor, phase, progressRatio, false);
        }

        return frames;
    }

    /**
     * Generate an idle/resting wavy slider bitmap for when playback is paused.
     */
    public static Bitmap generateIdleWavySlider(Context context, int widthPx, int heightPx,
                                                int accentColor, float progressRatio) {
        if (widthPx <= 0) widthPx = Math.round(220 * context.getResources().getDisplayMetrics().density);
        if (heightPx <= 0) heightPx = Math.round(22 * context.getResources().getDisplayMetrics().density);

        return renderWavySliderBitmap(context, widthPx, heightPx, accentColor, 0f, progressRatio, true);
    }

    public static Bitmap[] generateWaveFrames(Context context, int widthPx, int heightPx, int accentColor) {
        return generateWavySliderFrames(context, widthPx, heightPx, accentColor, 0.35f);
    }

    public static Bitmap generateIdleWave(Context context, int widthPx, int heightPx, int accentColor) {
        return generateIdleWavySlider(context, widthPx, heightPx, accentColor, 0.35f);
    }

    private static Bitmap renderWavySliderBitmap(Context context, int width, int height,
                                                 int accentColor, float phase, float progressRatio,
                                                 boolean isResting) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float density = context.getResources().getDisplayMetrics().density;
        float centerY = height / 2.0f;
        float trackPadding = 8f * density;
        float trackLeft = trackPadding;
        float trackRight = width - trackPadding;
        float trackWidth = Math.max(1f, trackRight - trackLeft);

        float clampedRatio = Math.max(0f, Math.min(1f, progressRatio));
        float thumbX = trackLeft + clampedRatio * trackWidth;
        float thumbRadius = 5.5f * density;
        float strokeWidth = 3.5f * density;
        float maxAmplitude = 3.5f * density;

        int r = Color.red(accentColor);
        int g = Color.green(accentColor);
        int b = Color.blue(accentColor);

        // 1. Unplayed straight track (right of thumb)
        Paint unplayedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unplayedPaint.setStyle(Paint.Style.STROKE);
        unplayedPaint.setStrokeWidth(strokeWidth);
        unplayedPaint.setStrokeCap(Paint.Cap.ROUND);
        unplayedPaint.setColor(Color.argb(55, 255, 255, 255));

        if (thumbX < trackRight) {
            canvas.drawLine(thumbX, centerY, trackRight, centerY, unplayedPaint);
        }

        // 2. Played track (left of thumb)
        Paint playedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playedPaint.setStyle(Paint.Style.STROKE);
        playedPaint.setStrokeWidth(strokeWidth);
        playedPaint.setStrokeCap(Paint.Cap.ROUND);
        playedPaint.setStrokeJoin(Paint.Join.ROUND);
        playedPaint.setShader(new LinearGradient(
                trackLeft, 0, thumbX, 0,
                new int[]{
                        Color.argb(190, r, g, b),
                        Color.argb(255, r, g, b)
                },
                null,
                Shader.TileMode.CLAMP
        ));

        if (thumbX > trackLeft) {
            float playedLength = thumbX - trackLeft;
            if (isResting || playedLength < 12f) {
                // Flat line when paused
                canvas.drawLine(trackLeft, centerY, thumbX, centerY, playedPaint);
            } else {
                // Wavy sinusoidal line when playing
                Path path = new Path();
                buildWavySegment(path, trackLeft, thumbX, centerY, maxAmplitude, phase, density);
                canvas.drawPath(path, playedPaint);
            }
        }

        // 3. Thumb knob at (thumbX, centerY)
        Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(Color.WHITE);
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint);

        Paint thumbBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbBorder.setStyle(Paint.Style.STROKE);
        thumbBorder.setStrokeWidth(1.2f * density);
        thumbBorder.setColor(Color.argb(60, 0, 0, 0));
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbBorder);

        return bitmap;
    }

    private static void buildWavySegment(Path path, float startX, float endX, float centerY,
                                         float amplitude, float phase, float density) {
        path.reset();
        float length = endX - startX;
        if (length <= 0) return;

        float cycleLength = 22f * density;
        float angularFreq = (float) (2.0 * Math.PI / cycleLength);

        path.moveTo(startX, centerY);
        float step = 2.5f;

        for (float x = startX; x <= endX; x += step) {
            float relX = x - startX;
            float normRemaining = (endX - x) / length;

            // Smooth taper envelope to meet thumb exactly at centerY
            float taper = (float) Math.sin(Math.PI * 0.5 * Math.min(1.0f, normRemaining * 3.5f));
            float y = centerY + (float) (Math.sin(relX * angularFreq - phase) * amplitude * taper);

            path.lineTo(x, y);
        }

        path.lineTo(endX, centerY);
    }
}

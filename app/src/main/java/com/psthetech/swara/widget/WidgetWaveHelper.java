package com.psthetech.swara.widget;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Compatibility delegate for widget waveform rendering.
 * All generation routes to WidgetWaveformRenderer.
 */
public final class WidgetWaveHelper {

    private WidgetWaveHelper() { /* static only */ }

    public static final int FRAME_COUNT = 4;

    public static Bitmap[] generateWavySliderFrames(Context context, int widthPx, int heightPx,
                                                    int accentColor, float progressRatio) {
        return generateWavySliderFrames(context, widthPx, heightPx, accentColor, progressRatio, true, false);
    }

    public static Bitmap[] generateWavySliderFrames(Context context, int widthPx, int heightPx,
                                                    int accentColor, float progressRatio,
                                                    boolean isNightMode, boolean isOffWhite) {
        Bitmap[] frames = new Bitmap[FRAME_COUNT];
        float phaseStep = (float) ((2.0 * Math.PI) / FRAME_COUNT);
        float density = context.getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 1.0f;
        int w = widthPx <= 0 ? Math.round(240 * density) : widthPx;
        int h = heightPx <= 0 ? Math.round(22 * density) : heightPx;

        for (int i = 0; i < FRAME_COUNT; i++) {
            frames[i] = WidgetWaveformRenderer.renderWaveformAtPhase(
                    density, w, h, accentColor, progressRatio, i * phaseStep, isNightMode, isOffWhite);
        }
        return frames;
    }

    public static Bitmap generateIdleWavySlider(Context context, int widthPx, int heightPx,
                                                int accentColor, float progressRatio) {
        return generateIdleWavySlider(context, widthPx, heightPx, accentColor, progressRatio, true, false);
    }

    public static Bitmap generateIdleWavySlider(Context context, int widthPx, int heightPx,
                                                int accentColor, float progressRatio,
                                                boolean isNightMode, boolean isOffWhite) {
        return WidgetWaveformRenderer.renderWaveform(
                context, widthPx, heightPx, accentColor, progressRatio, false, isNightMode, isOffWhite);
    }
}

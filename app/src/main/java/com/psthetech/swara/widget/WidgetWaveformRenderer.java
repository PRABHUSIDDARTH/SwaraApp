package com.psthetech.swara.widget;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.annotation.NonNull;

/**
 * High-performance, thread-safe, deterministic waveform renderer for RemoteViews.
 * Core math and phase synchronization foundation.
 */
public final class WidgetWaveformRenderer {

    private static final Object PHASE_LOCK = new Object();
    private static float currentPhase = 0.0f;

    private static final int MAX_WIDTH_PX = 1080;
    private static final int MAX_HEIGHT_PX = 160;

    private WidgetWaveformRenderer() { /* static utility */ }

    public static void advancePhase(float delta) {
        synchronized (PHASE_LOCK) {
            currentPhase += delta;
            float maxWrap = (float) (Math.PI * 200.0);
            if (currentPhase > maxWrap) {
                currentPhase = currentPhase % ((float) (Math.PI * 2.0));
            }
        }
    }

    public static float getCurrentPhase() {
        synchronized (PHASE_LOCK) {
            return currentPhase;
        }
    }

    public static void setPhase(float phase) {
        synchronized (PHASE_LOCK) {
            currentPhase = phase;
        }
    }

    public static float computeWave(float relX, float w1, float w2, float w3, float a1, float a2, float a3, float phase) {
        float s1 = (float) Math.sin(relX * w1 - phase) * a1;
        float s2 = (float) Math.sin(relX * w2 + phase * 1.2f + 1.1f) * a2;
        float s3 = (float) Math.sin(relX * w3 - phase * 0.5f + 2.5f) * a3;
        return s1 + s2 + s3;
    }

    public static float clampProgress(float ratio) {
        if (Float.isNaN(ratio) || Float.isInfinite(ratio)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, ratio));
    }
}

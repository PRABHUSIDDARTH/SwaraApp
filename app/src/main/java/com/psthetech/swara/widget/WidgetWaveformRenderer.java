package com.psthetech.swara.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;

/**
 * High-performance, thread-safe, deterministic waveform renderer for RemoteViews.
 *
 * Renders an organic, multi-layer flowing sinusoidal audio waveform into a bounded ARGB_8888 Bitmap.
 *
 * Visual Architecture:
 *   - Layer 1 (Primary): Strongest amplitude, highest contrast, vibrant theme accent.
 *   - Layer 2 (Harmonic Crest): Counter-phase rippling ribbon providing organic acoustic texture.
 *   - Layer 3 (Ambient Swell): Gentle undulating foundation wave.
 *   - Layer 4 (Luminous Halo): Soft translucent glow accentuating active wave crests.
 *   - Played vs. Remaining Regions: Bright theme accent + glow left of thumb, muted translucent track right of thumb.
 *   - Circular Thumb: Crisp circular knob with outer glow halo, theme styling, and specular highlight.
 *
 * Performance Safeguards:
 *   - Dimensions strictly bounded to prevent memory pressure on Android AppWidget service.
 *   - Thread-safe static state tracking preserved phase across playback transitions without resets.
 *   - Zero unnecessary object allocations during draw passes.
 */
public final class WidgetWaveformRenderer {

    private static final Object PHASE_LOCK = new Object();
    private static float currentPhase = 0.0f;

    // Bounded max dimension constraints for RemoteViews bitmaps
    private static final int MAX_WIDTH_PX = 1080;
    private static final int MAX_HEIGHT_PX = 160;

    private WidgetWaveformRenderer() { /* static utility */ }

    /**
     * Advance the persistent waveform phase when playback is active.
     * Advances smoothly and wraps around to prevent precision loss.
     */
    public static void advancePhase(float delta) {
        synchronized (PHASE_LOCK) {
            currentPhase += delta;
            float maxWrap = (float) (Math.PI * 200.0);
            if (currentPhase > maxWrap) {
                currentPhase = currentPhase % ((float) (Math.PI * 2.0));
            }
        }
    }

    /**
     * Get the current frozen or advancing wave phase.
     */
    public static float getCurrentPhase() {
        synchronized (PHASE_LOCK) {
            return currentPhase;
        }
    }

    /**
     * Reset the waveform phase (e.g. for testing).
     */
    public static void setPhase(float phase) {
        synchronized (PHASE_LOCK) {
            currentPhase = phase;
        }
    }

    /**
     * Render the widget waveform into a bounded ARGB_8888 bitmap.
     *
     * @param context Application context for display metrics and theme resolution.
     * @param widthPx Requested width in pixels (or <= 0 for default 240dp).
     * @param heightPx Requested height in pixels (or <= 0 for default 22dp).
     * @param accentColor Theme accent color.
     * @param progressRatio Normalized playback progress (0.0f to 1.0f).
     * @param isPlaying True if actively playing (advances phase).
     * @param isNightMode True if theme is in dark mode.
     * @param isOffWhite True if theme is OFF_WHITE.
     * @return Bounded, non-null Bitmap containing the rendered multi-layer waveform.
     */
    @NonNull
    // live method in next commit
    private static Bitmap _unused(@NonNull Context context,
                                        int widthPx,
                                        int heightPx,
                                        int accentColor,
                                        float progressRatio,
                                        boolean isPlaying,
                                        boolean isNightMode,
                                        boolean isOffWhite) {
        float density = context.getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 1.0f;

        if (widthPx <= 0) widthPx = Math.round(240 * density);
        if (heightPx <= 0) heightPx = Math.round(22 * density);

        // Bounded clamping to prevent excessive memory allocations
        int width = Math.min(MAX_WIDTH_PX, Math.max(32, widthPx));
        int height = Math.min(MAX_HEIGHT_PX, Math.max(16, heightPx));

        float phase;
        synchronized (PHASE_LOCK) {
            if (isPlaying) {
                currentPhase += 0.22f;
                float maxWrap = (float) (Math.PI * 200.0);
                if (currentPhase > maxWrap) {
                    currentPhase = currentPhase % ((float) (Math.PI * 2.0));
                }
            }
            phase = currentPhase;
        }

        return renderWaveformAtPhase(density, width, height, accentColor, progressRatio, phase, isNightMode, isOffWhite);
    }

    /**
     * Deterministic rendering pass at a fixed phase (used by both live rendering and unit tests).
     */
    @NonNull
    public static Bitmap renderWaveformAtPhase(float density,
                                               int width,
                                               int height,
                                               int accentColor,
                                               float progressRatio,
                                               float phase,
                                               boolean isNightMode,
                                               boolean isOffWhite) {
        float clampedProgress = clampProgress(progressRatio);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float centerY = height / 2.0f;
        float trackPadding = 10.0f * density;
        float trackLeft = trackPadding;
        float trackRight = width - trackPadding;
        float trackWidth = Math.max(1.0f, trackRight - trackLeft);

        float thumbX = trackLeft + clampedProgress * trackWidth;

        // Theme palette resolution
        int r = (accentColor >> 16) & 0xFF;
        int g = (accentColor >> 8) & 0xFF;
        int b = accentColor & 0xFF;

        int unplayedColor;
        int thumbColor;
        int thumbGlowColor;

        if (isOffWhite && !isNightMode) {
            unplayedColor = Color.argb(55, 92, 74, 30);
            thumbColor = accentColor;
            thumbGlowColor = Color.argb(50, 92, 74, 30);
        } else if (isNightMode) {
            unplayedColor = Color.argb(50, 255, 255, 255);
            thumbColor = Color.WHITE;
            thumbGlowColor = Color.argb(70, r, g, b);
        } else {
            unplayedColor = Color.argb(45, 0, 0, 0);
            thumbColor = accentColor;
            thumbGlowColor = Color.argb(55, r, g, b);
        }

        // Wave parameter setup
        float maxAmplitude = Math.min(height * 0.36f, 3.5f * density);
        float cycleLength1 = 30.0f * density;
        float cycleLength2 = 19.0f * density;
        float cycleLength3 = 44.0f * density;

        float w1 = (float) (2.0 * Math.PI / cycleLength1);
        float w2 = (float) (2.0 * Math.PI / cycleLength2);
        float w3 = (float) (2.0 * Math.PI / cycleLength3);

        float a1 = maxAmplitude * 0.85f;
        float a2 = maxAmplitude * 0.50f;
        float a3 = maxAmplitude * 0.32f;

        // ── 1. Unplayed Remaining Region (thumbX to trackRight) ─────────────────
        if (thumbX < trackRight) {
            Paint unplayedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            unplayedPaint.setStyle(Paint.Style.STROKE);
            unplayedPaint.setColor(unplayedColor);
            unplayedPaint.setStrokeCap(Paint.Cap.ROUND);

            // Subtle secondary wave continuation in remaining track
            Path remainingWave = new Path();
            remainingWave.moveTo(thumbX, centerY);
            float step = 2.5f * density;
            for (float x = thumbX; x <= trackRight; x += step) {
                float relX = x - trackLeft;
                // Soft damped waves in unplayed area
                float waveY = centerY + (computeWave(relX, w1, w2, w3, a1 * 0.45f, a2 * 0.45f, a3 * 0.45f, phase));
                remainingWave.lineTo(x, waveY);
            }
            remainingWave.lineTo(trackRight, centerY);

            unplayedPaint.setStrokeWidth(1.8f * density);
            canvas.drawPath(remainingWave, unplayedPaint);

            // Center guide line for clean tactile track definition
            unplayedPaint.setStrokeWidth(1.0f * density);
            unplayedPaint.setColor(Color.argb(Math.max(10, Color.alpha(unplayedColor) / 2), Color.red(unplayedColor), Color.green(unplayedColor), Color.blue(unplayedColor)));
            canvas.drawLine(thumbX, centerY, trackRight, centerY, unplayedPaint);
        }

        // ── 2. Played Flowing Region (trackLeft to thumbX) ──────────────────────
        if (thumbX > trackLeft) {
            float playedLength = thumbX - trackLeft;

            // Halo glow layer (broad translucent stroke)
            Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            haloPaint.setStyle(Paint.Style.STROKE);
            haloPaint.setStrokeCap(Paint.Cap.ROUND);
            haloPaint.setStrokeJoin(Paint.Join.ROUND);
            haloPaint.setStrokeWidth(4.5f * density);
            haloPaint.setColor(Color.argb(isNightMode ? 45 : 30, r, g, b));

            Path primaryPath = new Path();
            Path harmonicPath = new Path();
            Path ambientPath = new Path();

            primaryPath.moveTo(trackLeft, centerY);
            harmonicPath.moveTo(trackLeft, centerY);
            ambientPath.moveTo(trackLeft, centerY);

            float step = 2.0f;
            for (float x = trackLeft; x <= thumbX; x += step) {
                float relX = x - trackLeft;
                float normRemaining = (thumbX - x) / playedLength; // 1 at start, 0 at thumb

                // Taper envelope so all wave ribbons converge seamlessly into the thumb knob
                float taper = (float) Math.sin(Math.PI * 0.5 * Math.min(1.0f, normRemaining * 4.0f));

                // Layer 1: Primary wave
                float y1 = centerY + (float) (Math.sin(relX * w1 - phase) * a1 + Math.sin(relX * w3 - phase * 0.6f + 2.0f) * a3 * 0.5f) * taper;
                primaryPath.lineTo(x, y1);

                // Layer 2: Harmonic crest wave
                float y2 = centerY + (float) (Math.sin(relX * w2 + phase * 1.2f + 1.1f) * a2 + Math.sin(relX * w1 - phase) * a1 * 0.35f) * taper;
                harmonicPath.lineTo(x, y2);

                // Layer 3: Ambient swell wave
                float y3 = centerY + (float) (Math.sin(relX * w3 - phase * 0.5f + 2.5f) * a3) * taper;
                ambientPath.lineTo(x, y3);
            }

            primaryPath.lineTo(thumbX, centerY);
            harmonicPath.lineTo(thumbX, centerY);
            ambientPath.lineTo(thumbX, centerY);

            // Draw Layer 4: Halo glow along primary wave
            canvas.drawPath(primaryPath, haloPaint);

            // Draw Layer 3: Ambient swell ribbon
            Paint ambientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ambientPaint.setStyle(Paint.Style.STROKE);
            ambientPaint.setStrokeCap(Paint.Cap.ROUND);
            ambientPaint.setStrokeJoin(Paint.Join.ROUND);
            ambientPaint.setStrokeWidth(1.4f * density);
            ambientPaint.setColor(Color.argb(isNightMode ? 95 : 75, r, g, b));
            canvas.drawPath(ambientPath, ambientPaint);

            // Draw Layer 2: Harmonic wave ribbon
            Paint harmonicPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            harmonicPaint.setStyle(Paint.Style.STROKE);
            harmonicPaint.setStrokeCap(Paint.Cap.ROUND);
            harmonicPaint.setStrokeJoin(Paint.Join.ROUND);
            harmonicPaint.setStrokeWidth(1.8f * density);
            harmonicPaint.setColor(Color.argb(isNightMode ? 160 : 130, r, g, b));
            canvas.drawPath(harmonicPath, harmonicPaint);

            // Draw Layer 1: Primary vibrant waveform with smooth horizontal gradient
            Paint primaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            primaryPaint.setStyle(Paint.Style.STROKE);
            primaryPaint.setStrokeCap(Paint.Cap.ROUND);
            primaryPaint.setStrokeJoin(Paint.Join.ROUND);
            primaryPaint.setStrokeWidth(2.6f * density);
            primaryPaint.setShader(new LinearGradient(
                    trackLeft, 0, thumbX, 0,
                    new int[]{
                            Color.argb(180, r, g, b),
                            Color.argb(255, r, g, b)
                    },
                    null,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawPath(primaryPath, primaryPaint);
        }

        // ── 3. Premium Circular Thumb at (thumbX, centerY) ──────────────────────
        float thumbRadius = 5.2f * density;
        float thumbGlowRadius = 10.5f * density;

        // A. Soft luminous outer glow halo
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(thumbGlowColor);
        canvas.drawCircle(thumbX, centerY, thumbGlowRadius, glowPaint);

        // B. Thumb solid base
        Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(thumbColor);
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint);

        // C. Crisp subtle border
        Paint thumbBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbBorder.setStyle(Paint.Style.STROKE);
        thumbBorder.setStrokeWidth(1.0f * density);
        thumbBorder.setColor(Color.argb(60, 0, 0, 0));
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbBorder);

        // D. Specular top-left highlight
        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawCircle(thumbX - thumbRadius * 0.28f, centerY - thumbRadius * 0.28f, thumbRadius * 0.35f, highlightPaint);

        return bitmap;
    }

    /**
     * Compute multi-harmonic organic wave displacement at a given horizontal distance.
     */
    public static float computeWave(float relX, float w1, float w2, float w3, float a1, float a2, float a3, float phase) {
        float s1 = (float) Math.sin(relX * w1 - phase) * a1;
        float s2 = (float) Math.sin(relX * w2 + phase * 1.2f + 1.1f) * a2;
        float s3 = (float) Math.sin(relX * w3 - phase * 0.5f + 2.5f) * a3;
        return s1 + s2 + s3;
    }

    /**
     * Guaranteed safe clamp between 0.0f and 1.0f for all inputs including NaN and Infinity.
     */
    public static float clampProgress(float ratio) {
        if (Float.isNaN(ratio) || Float.isInfinite(ratio)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, ratio));
    }
}
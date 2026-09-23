package com.psthetech.swara.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * AudioWaveView is a hardware-accelerated custom view that renders a living,
 * multi-layered vibrating sound wave pattern.
 *
 * It features:
 *  - 3 harmonically blended sinusoidal wave paths with individual phase shifts
 *  - Organic amplitude modulation simulating musical vibration and rhythm
 *  - Smooth spring-like damping when paused, easing into a calm resting baseline
 *  - Dynamic gradient styling reacting to the active theme's accent color
 */
public class AudioWaveView extends View {

    private final Paint wavePaintPrimary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaintSecondary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaintTertiary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path pathPrimary = new Path();
    private final Path pathSecondary = new Path();
    private final Path pathTertiary = new Path();

    private boolean isPlaying = false;

    // Animation & physics state
    private float currentPhase = 0f;
    private float currentAmplitude = 0f;
    private float targetAmplitude = 0f;
    private float beatPulse = 1f;

    private int accentColor = 0xFFC9A84C; // default Swara gold
    private int secondaryColor = 0xFF9B7EC8;

    private ValueAnimator animator;

    public AudioWaveView(Context context) {
        this(context, null);
    }

    public AudioWaveView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioWaveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        wavePaintPrimary.setStyle(Paint.Style.STROKE);
        wavePaintPrimary.setStrokeWidth(2.2f * density);
        wavePaintPrimary.setStrokeCap(Paint.Cap.ROUND);
        wavePaintPrimary.setStrokeJoin(Paint.Join.ROUND);

        wavePaintSecondary.setStyle(Paint.Style.STROKE);
        wavePaintSecondary.setStrokeWidth(1.6f * density);
        wavePaintSecondary.setStrokeCap(Paint.Cap.ROUND);
        wavePaintSecondary.setStrokeJoin(Paint.Join.ROUND);

        wavePaintTertiary.setStyle(Paint.Style.STROKE);
        wavePaintTertiary.setStrokeWidth(1.2f * density);
        wavePaintTertiary.setStrokeCap(Paint.Cap.ROUND);
        wavePaintTertiary.setStrokeJoin(Paint.Join.ROUND);

        baselinePaint.setStyle(Paint.Style.STROKE);
        baselinePaint.setStrokeWidth(1f * density);
        baselinePaint.setColor(Color.argb(35, 255, 255, 255));

        updateColors();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        updateColors();
        invalidate();
    }

    private void updateColors() {
        int r = Color.red(accentColor);
        int g = Color.green(accentColor);
        int b = Color.blue(accentColor);

        wavePaintPrimary.setColor(Color.argb(235, r, g, b));
        wavePaintSecondary.setColor(Color.argb(160, r, g, b));
        wavePaintTertiary.setColor(Color.argb(95, r, g, b));

        baselinePaint.setColor(Color.argb(30, r, g, b));
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;
        this.targetAmplitude = playing ? 1.0f : 0.0f;

        if (playing) {
            startAnimation();
        }
    }

    private void startAnimation() {
        if (animator != null && animator.isRunning()) return;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            // Advance phase based on vibration speed
            currentPhase += 0.09f;
            if (currentPhase > (float) (Math.PI * 200)) {
                currentPhase = 0f;
            }

            // Smooth amplitude interpolation (lerp towards target)
            currentAmplitude += (targetAmplitude - currentAmplitude) * 0.08f;

            // Rhythmic beat pulse simulation (breathing harmonic vibration)
            float beat = (float) Math.sin(currentPhase * 1.5f);
            beatPulse = 0.85f + 0.30f * Math.abs(beat);

            // Stop animating once amplitude has settled to near-zero when paused
            if (!isPlaying && currentAmplitude < 0.005f) {
                currentAmplitude = 0f;
                animator.cancel();
            }

            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isPlaying) {
            startAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            // Apply subtle horizontal gradient shimmer to the wave strokes
            int r = Color.red(accentColor);
            int g = Color.green(accentColor);
            int b = Color.blue(accentColor);

            Shader shaderPrimary = new LinearGradient(
                    0, 0, w, 0,
                    new int[]{
                            Color.argb(80, r, g, b),
                            Color.argb(255, r, g, b),
                            Color.argb(255, r, g, b),
                            Color.argb(80, r, g, b)
                    },
                    new float[]{0f, 0.25f, 0.75f, 1f},
                    Shader.TileMode.CLAMP
            );
            wavePaintPrimary.setShader(shaderPrimary);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float centerY = height / 2.0f;
        float maxWaveHeight = (height * 0.44f) * currentAmplitude * beatPulse;

        // Draw calm baseline
        canvas.drawLine(0, centerY, width, centerY, baselinePaint);

        if (currentAmplitude <= 0.001f) {
            return; // At rest, only baseline is shown
        }

        buildWavePath(pathPrimary, width, centerY, maxWaveHeight, 1.0f, currentPhase, 1.2f);
        buildWavePath(pathSecondary, width, centerY, maxWaveHeight * 0.72f, 1.4f, currentPhase * 1.35f + 1.2f, 1.0f);
        buildWavePath(pathTertiary, width, centerY, maxWaveHeight * 0.45f, 2.0f, currentPhase * 0.8f + 2.5f, 0.8f);

        canvas.drawPath(pathTertiary, wavePaintTertiary);
        canvas.drawPath(pathSecondary, wavePaintSecondary);
        canvas.drawPath(pathPrimary, wavePaintPrimary);
    }

    /**
     * Builds a smooth fluid sinusoidal wave with edge windowing (tapers cleanly at ends).
     */
    private void buildWavePath(Path path, int width, float centerY, float amplitude,
                              float frequencyMultiplier, float phase, float harmonicWeight) {
        path.reset();
        int step = 3;

        for (int x = 0; x <= width; x += step) {
            // Normalized x [0, 1]
            float nx = (float) x / (float) width;

            // Hann windowing function: creates a natural, tapered envelope (0 at borders, 1 in center)
            float envelope = (float) (0.5f * (1.0f - Math.cos(2.0 * Math.PI * nx)));

            // Multi-harmonic oscillation for dynamic "vibration" feel
            double wave1 = Math.sin((nx * Math.PI * 4.0 * frequencyMultiplier) + phase);
            double wave2 = Math.sin((nx * Math.PI * 8.0 * frequencyMultiplier * 0.7) - phase * 0.6) * 0.35;
            double wave3 = Math.cos((nx * Math.PI * 2.0) + phase * 1.4) * 0.2;

            float y = centerY + (float) ((wave1 + wave2 * harmonicWeight + wave3) * amplitude * envelope);

            if (x == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
    }
}

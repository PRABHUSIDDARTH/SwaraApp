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
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.widget.WidgetWaveformRenderer;

/**
 * WavySliderView is an interactive organic flowing multi-layer waveform seekbar.
 *
 * Visual Character:
 *  - Multiple overlapping sinusoidal ribbon layers (Primary, Harmonic Crest, Ambient Swell, Soft Halo).
 *  - Played region (left of thumb): Vibrant theme accent with glow and energy.
 *  - Remaining region (right of thumb): Subtle muted continuous wave presence on track surface.
 *  - Circular Thumb: Crisp circular knob with outer glow halo, theme styling, and specular highlight.
 *  - Smooth continuous animation: Waves flow horizontally while playing.
 *  - Pause preservation: When paused, immediately freezes current phase without resetting to zero.
 *  - Full touch scrubbing: Users can touch and drag anywhere along the track to seek.
 *  - Dynamic theme coloring: Colors react directly to DesignTokens and all 8 themes.
 */
public class WavySliderView extends View {

    public interface OnWavySliderChangeListener {
        void onProgressChanged(WavySliderView slider, long progress, boolean fromUser);
        void onStartTrackingTouch(WavySliderView slider);
        void onStopTrackingTouch(WavySliderView slider);
    }

    // Wave Paints
    private final Paint wavePaintPrimary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaintHarmonic = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaintAmbient = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaintHalo = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Track Paints
    private final Paint trackPaintUnplayed = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Thumb Paints
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Paths
    private final Path primaryPath = new Path();
    private final Path harmonicPath = new Path();
    private final Path ambientPath = new Path();
    private final Path remainingWavePath = new Path();

    private long progress = 0;
    private long max = 100;
    private boolean isPlaying = false;
    private boolean isTracking = false;

    // Animation state — currentPhase is preserved across pause/resume
    private float currentPhase = 0f;
    private ValueAnimator waveAnimator;

    // Dimensions
    private float thumbRadiusPx;
    private float thumbGlowRadiusPx;
    private float maxWaveAmplitudePx;
    private float trackPaddingPx;

    // Colors
    private int accentColor = 0xFFC9A84C;
    private int unplayedColor = 0x4DFFFFFF;
    private int thumbColor = 0xFFFFFFFF;
    private int thumbGlowColor = 0x33C9A84C;
    private boolean isNightMode = true;

    private OnWavySliderChangeListener listener;

    public WavySliderView(Context context) {
        this(context, null);
    }

    public WavySliderView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WavySliderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        thumbRadiusPx = 7.0f * density;
        thumbGlowRadiusPx = 13.5f * density;
        maxWaveAmplitudePx = 4.0f * density;
        trackPaddingPx = thumbGlowRadiusPx + (2f * density);

        wavePaintPrimary.setStyle(Paint.Style.STROKE);
        wavePaintPrimary.setStrokeWidth(2.6f * density);
        wavePaintPrimary.setStrokeCap(Paint.Cap.ROUND);
        wavePaintPrimary.setStrokeJoin(Paint.Join.ROUND);

        wavePaintHarmonic.setStyle(Paint.Style.STROKE);
        wavePaintHarmonic.setStrokeWidth(1.8f * density);
        wavePaintHarmonic.setStrokeCap(Paint.Cap.ROUND);
        wavePaintHarmonic.setStrokeJoin(Paint.Join.ROUND);

        wavePaintAmbient.setStyle(Paint.Style.STROKE);
        wavePaintAmbient.setStrokeWidth(1.4f * density);
        wavePaintAmbient.setStrokeCap(Paint.Cap.ROUND);
        wavePaintAmbient.setStrokeJoin(Paint.Join.ROUND);

        wavePaintHalo.setStyle(Paint.Style.STROKE);
        wavePaintHalo.setStrokeWidth(5.0f * density);
        wavePaintHalo.setStrokeCap(Paint.Cap.ROUND);
        wavePaintHalo.setStrokeJoin(Paint.Join.ROUND);

        trackPaintUnplayed.setStyle(Paint.Style.STROKE);
        trackPaintUnplayed.setStrokeCap(Paint.Cap.ROUND);

        thumbGlowPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setStyle(Paint.Style.FILL);

        thumbStrokePaint.setStyle(Paint.Style.STROKE);
        thumbStrokePaint.setStrokeWidth(1.2f * density);
        thumbStrokePaint.setColor(Color.argb(60, 0, 0, 0));

        thumbHighlightPaint.setStyle(Paint.Style.FILL);
        thumbHighlightPaint.setColor(Color.argb(150, 255, 255, 255));

        updateColors();
    }

    public void setOnWavySliderChangeListener(OnWavySliderChangeListener listener) {
        this.listener = listener;
    }

    public void setProgress(long progress) {
        if (!isTracking) {
            this.progress = Math.max(0, Math.min(progress, max));
            invalidate();
        }
    }

    public long getProgress() {
        return progress;
    }

    public void setMax(long max) {
        this.max = Math.max(1, max);
        if (progress > this.max) {
            progress = this.max;
        }
        invalidate();
    }

    public long getMax() {
        return max;
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;

        if (playing) {
            startWaveAnimation();
        } else {
            // When paused: stop animation immediately and preserve currentPhase
            if (waveAnimator != null) {
                waveAnimator.cancel();
                waveAnimator = null;
            }
            invalidate();
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setDesignTokens(DesignTokens tokens) {
        if (tokens == null) return;
        this.isNightMode = tokens.isNightMode();
        this.accentColor = tokens.getReadableAccentColor();
        int r = (accentColor >> 16) & 0xFF;
        int g = (accentColor >> 8) & 0xFF;
        int b = accentColor & 0xFF;

        this.thumbGlowColor = Color.argb(isNightMode ? 75 : 55, r, g, b);

        if (tokens.getColorTheme() == ColorTheme.OFF_WHITE && !isNightMode) {
            this.unplayedColor = Color.argb(60, 92, 74, 30);
            this.thumbColor = accentColor;
        } else if (tokens.getColorTheme() == ColorTheme.ROSE && !isNightMode) {
            this.unplayedColor = Color.argb(55, 120, 60, 80);
            this.thumbColor = accentColor;
        } else if (isNightMode) {
            this.unplayedColor = Color.argb(50, 255, 255, 255);
            this.thumbColor = Color.WHITE;
        } else {
            this.unplayedColor = Color.argb(45, 0, 0, 0);
            this.thumbColor = accentColor;
        }

        updateColors();
        invalidate();
    }

    private void updateColors() {
        int r = (accentColor >> 16) & 0xFF;
        int g = (accentColor >> 8) & 0xFF;
        int b = accentColor & 0xFF;

        wavePaintPrimary.setColor(accentColor);
        wavePaintHarmonic.setColor(Color.argb(isNightMode ? 160 : 130, r, g, b));
        wavePaintAmbient.setColor(Color.argb(isNightMode ? 95 : 75, r, g, b));
        wavePaintHalo.setColor(Color.argb(isNightMode ? 45 : 30, r, g, b));

        trackPaintUnplayed.setColor(unplayedColor);
        thumbPaint.setColor(thumbColor);
        thumbGlowPaint.setColor(thumbGlowColor);
    }

    private void startWaveAnimation() {
        if (waveAnimator != null && waveAnimator.isRunning()) return;

        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(1200);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(anim -> {
            // Smooth horizontal wave flow
            currentPhase += 0.08f;
            float maxWrap = (float) (Math.PI * 200.0);
            if (currentPhase > maxWrap) {
                currentPhase = currentPhase % ((float) (Math.PI * 2.0));
            }
            invalidate();
        });
        waveAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isPlaying) {
            startWaveAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (waveAnimator != null) {
            waveAnimator.cancel();
            waveAnimator = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = Math.round(36 * getResources().getDisplayMetrics().density);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float centerY = height / 2.0f;
        float trackLeft = trackPaddingPx;
        float trackRight = width - trackPaddingPx;
        float trackWidth = Math.max(1f, trackRight - trackLeft);

        float progressRatio = (float) progress / (float) max;
        progressRatio = Math.max(0f, Math.min(1f, progressRatio));

        float thumbX = trackLeft + progressRatio * trackWidth;

        int r = (accentColor >> 16) & 0xFF;
        int g = (accentColor >> 8) & 0xFF;
        int b = accentColor & 0xFF;

        // Wave parameters
        float cycleLength1 = 30.0f * density;
        float cycleLength2 = 19.0f * density;
        float cycleLength3 = 44.0f * density;

        float w1 = (float) (2.0 * Math.PI / cycleLength1);
        float w2 = (float) (2.0 * Math.PI / cycleLength2);
        float w3 = (float) (2.0 * Math.PI / cycleLength3);

        float a1 = maxWaveAmplitudePx * 0.85f;
        float a2 = maxWaveAmplitudePx * 0.50f;
        float a3 = maxWaveAmplitudePx * 0.32f;

        // ── 1. Unplayed Remaining Region (thumbX to trackRight) ─────────────────
        if (thumbX < trackRight) {
            remainingWavePath.reset();
            remainingWavePath.moveTo(thumbX, centerY);
            float step = 3.0f * density;
            for (float x = thumbX; x <= trackRight; x += step) {
                float relX = x - trackLeft;
                float waveY = centerY + WidgetWaveformRenderer.computeWave(
                        relX, w1, w2, w3, a1 * 0.45f, a2 * 0.45f, a3 * 0.45f, currentPhase);
                remainingWavePath.lineTo(x, waveY);
            }
            remainingWavePath.lineTo(trackRight, centerY);

            trackPaintUnplayed.setStrokeWidth(1.8f * density);
            trackPaintUnplayed.setColor(unplayedColor);
            canvas.drawPath(remainingWavePath, trackPaintUnplayed);

            // Subtle center guide
            trackPaintUnplayed.setStrokeWidth(1.0f * density);
            int halfAlpha = Color.argb(Math.max(10, Color.alpha(unplayedColor) / 2),
                    Color.red(unplayedColor), Color.green(unplayedColor), Color.blue(unplayedColor));
            trackPaintUnplayed.setColor(halfAlpha);
            canvas.drawLine(thumbX, centerY, trackRight, centerY, trackPaintUnplayed);
        }

        // ── 2. Played Flowing Region (trackLeft to thumbX) ──────────────────────
        if (thumbX > trackLeft) {
            float playedLength = thumbX - trackLeft;

            primaryPath.reset();
            harmonicPath.reset();
            ambientPath.reset();

            primaryPath.moveTo(trackLeft, centerY);
            harmonicPath.moveTo(trackLeft, centerY);
            ambientPath.moveTo(trackLeft, centerY);

            float step = 2.0f;
            for (float x = trackLeft; x <= thumbX; x += step) {
                float relX = x - trackLeft;
                float normRemaining = (thumbX - x) / playedLength; // 1 at start, 0 at thumb

                float taper = (float) Math.sin(Math.PI * 0.5 * Math.min(1.0f, normRemaining * 4.0f));

                float y1 = centerY + (float) (Math.sin(relX * w1 - currentPhase) * a1 + Math.sin(relX * w3 - currentPhase * 0.6f + 2.0f) * a3 * 0.5f) * taper;
                primaryPath.lineTo(x, y1);

                float y2 = centerY + (float) (Math.sin(relX * w2 + currentPhase * 1.2f + 1.1f) * a2 + Math.sin(relX * w1 - currentPhase) * a1 * 0.35f) * taper;
                harmonicPath.lineTo(x, y2);

                float y3 = centerY + (float) (Math.sin(relX * w3 - currentPhase * 0.5f + 2.5f) * a3) * taper;
                ambientPath.lineTo(x, y3);
            }

            primaryPath.lineTo(thumbX, centerY);
            harmonicPath.lineTo(thumbX, centerY);
            ambientPath.lineTo(thumbX, centerY);

            // Halo glow
            canvas.drawPath(primaryPath, wavePaintHalo);

            // Layer 3: Ambient swell
            canvas.drawPath(ambientPath, wavePaintAmbient);

            // Layer 2: Harmonic wave
            canvas.drawPath(harmonicPath, wavePaintHarmonic);

            // Layer 1: Primary vibrant waveform
            wavePaintPrimary.setShader(new LinearGradient(
                    trackLeft, 0, thumbX, 0,
                    new int[]{
                            Color.argb(180, r, g, b),
                            Color.argb(255, r, g, b)
                    },
                    null,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawPath(primaryPath, wavePaintPrimary);
        }

        // ── 3. Premium Circular Thumb at (thumbX, centerY) ──────────────────────
        canvas.drawCircle(thumbX, centerY, thumbGlowRadiusPx, thumbGlowPaint);
        canvas.drawCircle(thumbX, centerY, thumbRadiusPx, thumbPaint);
        canvas.drawCircle(thumbX, centerY, thumbRadiusPx, thumbStrokePaint);
        canvas.drawCircle(thumbX - thumbRadiusPx * 0.28f, centerY - thumbRadiusPx * 0.28f, thumbRadiusPx * 0.35f, thumbHighlightPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        float x = event.getX();
        float trackLeft = trackPaddingPx;
        float trackRight = getWidth() - trackPaddingPx;
        float trackWidth = Math.max(1f, trackRight - trackLeft);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isTracking = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateProgressFromTouch(x, trackLeft, trackWidth);
                if (listener != null) {
                    listener.onStartTrackingTouch(this);
                    listener.onProgressChanged(this, progress, true);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isTracking) {
                    updateProgressFromTouch(x, trackLeft, trackWidth);
                    if (listener != null) {
                        listener.onProgressChanged(this, progress, true);
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isTracking) {
                    isTracking = false;
                    updateProgressFromTouch(x, trackLeft, trackWidth);
                    if (listener != null) {
                        listener.onProgressChanged(this, progress, true);
                        listener.onStopTrackingTouch(this);
                    }
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    invalidate();
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    private void updateProgressFromTouch(float touchX, float trackLeft, float trackWidth) {
        float ratio = (touchX - trackLeft) / trackWidth;
        ratio = Math.max(0f, Math.min(1f, ratio));
        this.progress = Math.round(ratio * max);
    }
}

package com.psthetech.swara.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.psthetech.swara.ui.theme.DesignTokens;

/**
 * WavySliderView is an interactive Android 13/14 Material You-style squiggly/wavy seekbar.
 *
 * Features:
 *  - Played track (left of thumb): An undulating sinusoidal wave with rounded caps that vibrates with music playback.
 *  - Thumb knob: A smooth circular pearl knob located at the current progress position.
 *  - Unplayed track (right of thumb): A straight, muted horizontal line.
 *  - Smooth flattening when paused: The wave flattens gracefully into a clean straight line.
 *  - Full touch scrubbing: Users can touch and drag anywhere along the track to seek.
 *  - Dynamic theme coloring: Colors react directly to DesignTokens.
 */
public class WavySliderView extends View {

    public interface OnWavySliderChangeListener {
        void onProgressChanged(WavySliderView slider, long progress, boolean fromUser);
        void onStartTrackingTouch(WavySliderView slider);
        void onStopTrackingTouch(WavySliderView slider);
    }

    private final Paint wavePaintPlayed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaintUnplayed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path playedWavePath = new Path();

    private long progress = 0;
    private long max = 100;
    private boolean isPlaying = false;
    private boolean isTracking = false;

    // Animation state
    private float currentPhase = 0f;
    private float currentWaveAmplitudeRatio = 0f;
    private float targetWaveAmplitudeRatio = 0f;
    private ValueAnimator waveAnimator;

    // Dimensions
    private float strokeWidthPx;
    private float thumbRadiusPx;
    private float maxWaveAmplitudePx;
    private float trackPaddingPx;

    // Colors
    private int accentColor = 0xFFC9A84C;
    private int unplayedColor = 0x4DFFFFFF;
    private int thumbColor = 0xFFFFFFFF;

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

        strokeWidthPx = 4.5f * density;
        thumbRadiusPx = 8.5f * density;
        maxWaveAmplitudePx = 5.0f * density;
        trackPaddingPx = thumbRadiusPx + (2f * density);

        wavePaintPlayed.setStyle(Paint.Style.STROKE);
        wavePaintPlayed.setStrokeWidth(strokeWidthPx);
        wavePaintPlayed.setStrokeCap(Paint.Cap.ROUND);
        wavePaintPlayed.setStrokeJoin(Paint.Join.ROUND);

        trackPaintUnplayed.setStyle(Paint.Style.STROKE);
        trackPaintUnplayed.setStrokeWidth(strokeWidthPx);
        trackPaintUnplayed.setStrokeCap(Paint.Cap.ROUND);

        thumbPaint.setStyle(Paint.Style.FILL);

        thumbStrokePaint.setStyle(Paint.Style.STROKE);
        thumbStrokePaint.setStrokeWidth(1.5f * density);
        thumbStrokePaint.setColor(Color.argb(70, 0, 0, 0));

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
        this.targetWaveAmplitudeRatio = playing ? 1.0f : 0.0f;

        if (playing) {
            startWaveAnimation();
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setDesignTokens(DesignTokens tokens) {
        if (tokens == null) return;
        this.accentColor = tokens.getAccentColor();
        int r = Color.red(accentColor);
        int g = Color.green(accentColor);
        int b = Color.blue(accentColor);

        this.unplayedColor = tokens.isNightMode()
                ? Color.argb(65, 255, 255, 255)
                : Color.argb(45, 0, 0, 0);

        this.thumbColor = tokens.isNightMode()
                ? Color.argb(255, 255, 255, 255)
                : Color.rgb(r, g, b);

        updateColors();
        invalidate();
    }

    private void updateColors() {
        wavePaintPlayed.setColor(accentColor);
        trackPaintUnplayed.setColor(unplayedColor);
        thumbPaint.setColor(thumbColor);
    }

    private void startWaveAnimation() {
        if (waveAnimator != null && waveAnimator.isRunning()) return;

        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(1200);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(anim -> {
            // Continuous phase advance
            currentPhase += 0.12f;
            if (currentPhase > (float) (Math.PI * 200)) {
                currentPhase = 0f;
            }

            // Smooth amplitude interpolation
            currentWaveAmplitudeRatio += (targetWaveAmplitudeRatio - currentWaveAmplitudeRatio) * 0.08f;

            // If stopped and amplitude has faded to zero, stop animator
            if (!isPlaying && currentWaveAmplitudeRatio < 0.005f) {
                currentWaveAmplitudeRatio = 0f;
                waveAnimator.cancel();
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

        float centerY = height / 2.0f;
        float trackLeft = trackPaddingPx;
        float trackRight = width - trackPaddingPx;
        float trackWidth = Math.max(1f, trackRight - trackLeft);

        float progressRatio = (float) progress / (float) max;
        progressRatio = Math.max(0f, Math.min(1f, progressRatio));

        float thumbX = trackLeft + progressRatio * trackWidth;

        // 1. Draw unplayed straight track (from thumbX to trackRight)
        if (thumbX < trackRight) {
            canvas.drawLine(thumbX, centerY, trackRight, centerY, trackPaintUnplayed);
        }

        // 2. Draw played wavy track (from trackLeft to thumbX)
        if (thumbX > trackLeft) {
            float playedLength = thumbX - trackLeft;
            float currentAmplitude = maxWaveAmplitudePx * currentWaveAmplitudeRatio;

            if (currentAmplitude <= 0.2f || playedLength < 10f) {
                // Flat line when paused or too short for waves
                canvas.drawLine(trackLeft, centerY, thumbX, centerY, wavePaintPlayed);
            } else {
                buildWavyPath(playedWavePath, trackLeft, thumbX, centerY, currentAmplitude, currentPhase);
                canvas.drawPath(playedWavePath, wavePaintPlayed);
            }
        }

        // 3. Draw circular thumb knob at (thumbX, centerY)
        canvas.drawCircle(thumbX, centerY, thumbRadiusPx, thumbPaint);
        canvas.drawCircle(thumbX, centerY, thumbRadiusPx, thumbStrokePaint);
    }

    /**
     * Constructs a smooth sinusoidal wave from startX to endX,
     * tapering to amplitude 0 at endX (where it meets the thumb knob).
     */
    private void buildWavyPath(Path path, float startX, float endX, float centerY, float amplitude, float phase) {
        path.reset();
        float length = endX - startX;
        if (length <= 0) return;

        // Spatial frequency: wave cycle ~28dp
        float cycleLength = 28f * getResources().getDisplayMetrics().density;
        float angularFreq = (float) (2.0 * Math.PI / cycleLength);

        path.moveTo(startX, centerY);
        float step = 3f;

        for (float x = startX; x <= endX; x += step) {
            float relX = x - startX;
            float normRemaining = (endX - x) / length; // 1 at start, 0 at thumb

            // Taper envelope so wave joins thumb smoothly at centerY
            float taper = (float) Math.sin(Math.PI * 0.5 * Math.min(1.0f, normRemaining * 4f));
            float y = centerY + (float) (Math.sin(relX * angularFreq - phase) * amplitude * taper);

            path.lineTo(x, y);
        }

        // Ensure path firmly terminates right at the center of the thumb
        path.lineTo(endX, centerY);
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
                    return true;
                }
                break;

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
                    return true;
                }
                break;
        }

        return super.onTouchEvent(event);
    }

    private void updateProgressFromTouch(float touchX, float trackLeft, float trackWidth) {
        float ratio = (touchX - trackLeft) / trackWidth;
        ratio = Math.max(0f, Math.min(1f, ratio));
        this.progress = Math.round(ratio * max);
    }
}

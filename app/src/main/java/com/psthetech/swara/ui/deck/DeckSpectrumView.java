package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.audio.spectrum.PcmRing;
import com.psthetech.swara.audio.spectrum.SpectrumAnalyzer;
import com.psthetech.swara.audio.spectrum.SpectrumSmoother;
import com.psthetech.swara.audio.spectrum.SpectrumTap;
import com.psthetech.swara.ui.glass.LiquidGlassRenderer;

import java.util.Arrays;

/**
 * Segmented spectrum bars, fed from the {@link SpectrumTap}.
 *
 * <p>The tap and the frame loop run only while audio is playing, the owning screen is started,
 * the view is visible and animations are allowed. Otherwise the bars are flat and nothing is
 * copied or drawn. A frame allocates nothing.
 */
public class DeckSpectrumView extends View {

    public static final int BANDS = SpectrumAnalyzer.BANDS;
    private static final int SEGMENTS = 10;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[BANDS];
    private final float[] samples = new float[SpectrumAnalyzer.SIZE];
    private final float[] target = new float[BANDS];
    private final Runnable frame = this::onFrame;
    @Nullable private DeckPalette palette;
    @Nullable private SpectrumTap tap;
    @Nullable private SpectrumAnalyzer analyzer;
    private boolean playing;
    private boolean hostStarted;
    private boolean visible;
    private boolean running;
    private long lastFrameMs;

    public DeckSpectrumView(Context context) { this(context, null); }

    public DeckSpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
    }

    public void setPalette(@NonNull DeckPalette palette) {
        this.palette = palette;
        invalidate();
    }

    /** Connects the bars to the audio tap. */
    public void setTap(@NonNull SpectrumTap tap) {
        this.tap = tap;
        if (analyzer == null) analyzer = new SpectrumAnalyzer();
        updateRunning();
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        updateRunning();
    }

    /** False while the screen that owns the bars is stopped (including screen off). */
    public void setHostStarted(boolean started) {
        hostStarted = started;
        updateRunning();
    }

    /** A seek or skip: hide queued audio too. */
    public void onUserSeek() {
        if (tap != null) tap.ring().discardWritten();
        flatten();
    }

    /** A track change: hide what has been shown, keeping the new track's queued audio. */
    public void onTrackChanged() {
        if (tap != null) tap.ring().discardShown();
        flatten();
    }

    private void flatten() {
        Arrays.fill(levels, 0f);
        invalidate();
    }

    private void updateRunning() {
        boolean wanted = tap != null && playing && hostStarted && visible && isAttachedToWindow()
                && !LiquidGlassRenderer.isReducedMotion(getContext());
        if (wanted == running) return;
        running = wanted;
        removeCallbacks(frame);
        if (tap != null) tap.setEnabled(wanted);
        if (wanted) {
            lastFrameMs = SystemClock.uptimeMillis();
            postOnAnimation(frame);
        } else {
            flatten();
        }
    }

    private void onFrame() {
        if (!running || tap == null || analyzer == null) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(now - lastFrameMs, 48L);
        lastFrameMs = now;
        // Another Deck screen closing late must not leave these bars without audio.
        tap.setEnabled(true);
        PcmRing ring = tap.ring();
        // The lag is in milliseconds; the rate is that of the last window read.
        int rate = ring.lastSampleRate() > 0 ? ring.lastSampleRate() : 44_100;
        if (ring.read(samples, tap.lagFrames(rate))) {
            analyzer.analyze(samples, ring.lastSampleRate(), target);
        } else {
            Arrays.fill(target, 0f);
        }
        SpectrumSmoother.step(levels, target, dt);
        invalidate();
        postOnAnimation(frame);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        visible = false;
        updateRunning();
        super.onDetachedFromWindow();
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

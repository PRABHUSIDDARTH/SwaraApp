package com.psthetech.swara.wrapped.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.wrapped.SlideType;
import com.psthetech.swara.wrapped.WrappedStats;
import com.psthetech.swara.wrapped.render.SlideRenderer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Full-screen story: draws the current slide with {@link SlideRenderer}, progress bars on top.
 * Tap the right two-thirds for next, the left third for previous; press and hold to pause.
 */
public class StoryView extends View implements Choreographer.FrameCallback {

    public interface Listener {
        void onSlideChanged(int index, SlideType type);
        void onFinished();
    }

    private SlideRenderer renderer;
    private WrappedStats stats;
    private List<SlideType> slides = Collections.emptyList();
    private Map<Long, Bitmap> art = Collections.emptyMap();
    private StoryState state = new StoryState(0);
    private Listener listener;
    private long lastFrameNs;
    private boolean running;
    private int shownIndex = -1;
    private boolean held;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable holdToPause = () -> {
        held = true;
        state.pause();
    };

    public StoryView(Context c) { super(c); }
    public StoryView(Context c, @Nullable AttributeSet a) { super(c, a); }

    public void bind(@NonNull WrappedStats s, @NonNull List<SlideType> plan, @NonNull Map<Long, Bitmap> artwork,
                     @NonNull Listener l) {
        renderer = new SlideRenderer(getContext());
        stats = s;
        slides = plan;
        art = artwork;
        listener = l;
        state = new StoryState(plan.size());
        shownIndex = -1;
        start();
    }

    public void setArtwork(@NonNull Map<Long, Bitmap> artwork) {
        art = artwork;
        invalidate();
    }

    @Nullable
    public SlideType current() {
        return slides.isEmpty() ? null : slides.get(state.index());
    }

    public void pauseStory() { state.pause(); }
    public void resumeStory() { if (!held) state.resume(); }

    public void start() {
        if (running) return;
        running = true;
        lastFrameNs = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (lastFrameNs != 0) state.tick((frameTimeNanos - lastFrameNs) / 1_000_000L);
        lastFrameNs = frameTimeNanos;
        if (state.index() != shownIndex && !slides.isEmpty()) {
            shownIndex = state.index();
            if (listener != null) listener.onSlideChanged(shownIndex, slides.get(shownIndex));
        }
        if (state.isFinished()) {
            running = false;
            if (listener != null) listener.onFinished();
            return;
        }
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        if (stats == null || slides.isEmpty()) return;
        SlideType t = slides.get(state.index());
        // Keep the 9:16 slide whole: letterbox on unusual screens.
        float w = getWidth(), h = getHeight();
        float sw = w, sh = w * 16f / 9f;
        if (sh > h) { sh = h; sw = h * 9f / 16f; }
        float left = (w - sw) / 2f, top = (h - sh) / 2f;
        c.drawColor(0xFF000000);
        c.save();
        c.translate(left, top);
        renderer.draw(c, (int) sw, (int) sh, t, stats, state.entranceProgress(),
                art.get(SlideRenderer.artSongFor(t, stats)));
        c.restore();
        drawBars(c, left, top, sw);
    }

    private void drawBars(Canvas c, float left, float top, float width) {
        float d = getResources().getDisplayMetrics().density;
        float gap = 4 * d, y = top + 12 * d, hgt = 3 * d, pad = 10 * d;
        int n = slides.size();
        float each = (width - 2 * pad - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            float x = left + pad + i * (each + gap);
            bar.setColor(0x59FFFFFF);
            c.drawRoundRect(new RectF(x, y, x + each, y + hgt), hgt, hgt, bar);
            float f = i < state.index() ? 1f : (i == state.index() ? state.slideProgress() : 0f);
            if (f > 0) {
                bar.setColor(0xFFFFFFFF);
                c.drawRoundRect(new RectF(x, y, x + each * f, y + hgt), hgt, hgt, bar);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                held = false;
                postDelayed(holdToPause, 250);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(holdToPause);
                if (held) {
                    held = false;
                    state.resume();
                } else if (e.getX() < getWidth() / 3f) {
                    state.prev();
                } else {
                    state.next();
                }
                if (!running && !state.isFinished()) start();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(holdToPause);
                if (held) state.resume();
                held = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}

package com.psthetech.swara.ui.deck;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.R;
import com.psthetech.swara.ui.glass.LiquidGlassRenderer;
import com.psthetech.swara.ui.widget.CircularSeekHelper;
import com.psthetech.swara.ui.widget.DiscSpinPhysics;

/**
 * One large tape reel: a flange with three rounded windows over the tape pack, the cover art as
 * the hub label and a marker dot that makes rotation visible.
 *
 * <p>It spins up, spins down and follows a scratching finger with {@link DiscSpinPhysics}, like
 * the standard player's disc. Dragging seeks; a still tap on the cover is a click. The animator
 * runs only while the reel is turning, its screen is started and it is visible.
 */
public class DeckReelView extends View {

    private static final int DEFAULT_SIZE_DP = 220;
    private static final float WINDOW_SWEEP_DEG = 78f;
    private static final float SPINDLE_RING_DP = 1.75f;

    // The same feel as the standard player's disc.
    private static final float PLAY_DEG_PER_MS = 360f / 10_000f;
    private static final float FLING_DECAY_TAU_MS = 420f;
    private static final float SPIN_UP_TAU_MS = 260f;
    private static final float STOP_EPSILON_DEG_PER_MS = 0.002f;
    private static final float MAX_FLING_DEG_PER_MS = 1.1f;
    private static final long SEEK_THROTTLE_MS = 80L;
    private static final float ACCESSIBILITY_SEEK_SECONDS = 5f;

    /** Seeking requested by dragging the reel, as a change in seconds. */
    public interface SeekListener {
        void onSeekDelta(float deltaSeconds);
    }

    @Nullable private SeekListener seekListener;
    private boolean rotatingParts = true;
    @Nullable private TimeAnimator spin;
    private float spinVelocity; // degrees per ms, positive = clockwise
    private boolean playing;
    private boolean hostStarted;
    private boolean visible;

    private boolean touching;
    private float lastAngle;
    private float draggedDeg;
    private float pendingSeekDeg;
    private long lastMoveTime;
    private long lastSeekTime;

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

    public void setSeekListener(@Nullable SeekListener listener) {
        seekListener = listener;
    }

    /**
     * Off for the widget's still base while it spins: the launcher then draws the windows and
     * marker as separate rotating layers on top.
     */
    public void setRotatingParts(boolean shown) {
        rotatingParts = shown;
        invalidate();
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        updateSpin();
    }

    /** False while the screen that owns the reel is stopped (including screen off). */
    public void setHostStarted(boolean started) {
        hostStarted = started;
        updateSpin();
    }

    /** Back to the resting angle for a new track. */
    public void resetRotation() {
        spinVelocity = 0f;
        rotationDeg = 0f;
        updateSpin();
        invalidate();
    }

    /** True while the frame animator is running. */
    boolean isSpinning() {
        return spin != null && spin.isStarted();
    }

    private boolean canAnimate() {
        return hostStarted && visible && isAttachedToWindow()
                && !LiquidGlassRenderer.isReducedMotion(getContext());
    }

    private void updateSpin() {
        boolean wanted = canAnimate() && !touching && (playing || spinVelocity != 0f);
        if (wanted) {
            if (spin == null) {
                spin = new TimeAnimator();
                spin.setTimeListener((animation, total, delta) -> onSpinFrame(delta));
            }
            if (!spin.isStarted()) spin.start();
        } else {
            if (spin != null && spin.isStarted()) spin.end();
            // Off screen there is no spin-down to resume later.
            if (!canAnimate()) spinVelocity = 0f;
        }
    }

    private void onSpinFrame(long deltaMs) {
        if (deltaMs <= 0 || spin == null) return;
        float dt = Math.min(deltaMs, 48L);
        float target = playing ? PLAY_DEG_PER_MS : 0f;
        spinVelocity = DiscSpinPhysics.approach(spinVelocity, target, dt,
                Math.abs(spinVelocity) > Math.abs(target) ? FLING_DECAY_TAU_MS : SPIN_UP_TAU_MS);
        rotationDeg = DiscSpinPhysics.wrap(rotationDeg + spinVelocity * dt);
        if (!playing && Math.abs(spinVelocity) < STOP_EPSILON_DEG_PER_MS) {
            spinVelocity = 0f;
            spin.end();
        }
        invalidate();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        updateSpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (spin != null) {
            spin.end();
            spin.setTimeListener(null);
            spin = null;
        }
        touching = false;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        float dx = event.getX() - cx;
        float dy = event.getY() - cy;
        float distance = (float) Math.hypot(dx, dy);
        float angle = CircularSeekHelper.calculateAngleDeg(event.getX(), event.getY(), cx, cy);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (distance > radius) return false; // let the sheet and scroll view have it
                touching = true;
                lastAngle = angle;
                lastMoveTime = event.getEventTime();
                draggedDeg = 0f;
                pendingSeekDeg = 0f;
                spinVelocity = 0f; // the finger catches the reel
                updateSpin();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!touching) return false;
                // Near the centre the angle jumps about; ignore those samples.
                if (distance < radius * 0.12f) return true;
                float delta = CircularSeekHelper.angularDelta(lastAngle, angle);
                lastAngle = angle;
                draggedDeg += Math.abs(delta);
                rotationDeg = DiscSpinPhysics.wrap(rotationDeg + delta);
                long now = event.getEventTime();
                spinVelocity = DiscSpinPhysics.trackVelocity(spinVelocity, delta, now - lastMoveTime);
                lastMoveTime = now;
                invalidate();
                if (draggedDeg > CircularSeekHelper.DEFAULT_DEAD_ZONE_DEG) {
                    pendingSeekDeg += delta;
                    if (now - lastSeekTime > SEEK_THROTTLE_MS) flushSeek(now);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!touching) return false;
                touching = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                boolean dragged = draggedDeg > CircularSeekHelper.DEFAULT_DEAD_ZONE_DEG;
                if (dragged) flushSeek(event.getEventTime());
                // A finger that stopped before lifting leaves no momentum.
                if (event.getEventTime() - lastMoveTime > 80L) spinVelocity = 0f;
                spinVelocity = Math.max(-MAX_FLING_DEG_PER_MS,
                        Math.min(MAX_FLING_DEG_PER_MS, spinVelocity));
                updateSpin();
                if (!dragged && distance <= labelRadius
                        && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void flushSeek(long now) {
        if (pendingSeekDeg != 0f && seekListener != null) {
            seekListener.onSeekDelta(pendingSeekDeg / CircularSeekHelper.DEFAULT_DEG_PER_SECOND);
        }
        pendingSeekDeg = 0f;
        lastSeekTime = now;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLICK,
                getResources().getString(R.string.cd_deck_change_artwork)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                getResources().getString(R.string.cd_deck_seek_forward)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                getResources().getString(R.string.cd_deck_seek_back)));
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            if (seekListener != null) {
                seekListener.onSeekDelta(action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        ? ACCESSIBILITY_SEEK_SECONDS : -ACCESSIBILITY_SEEK_SECONDS);
            }
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
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
        for (int i = 0; rotatingParts && i < 3; i++) {
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
        if (rotatingParts) {
            fill(p.accent);
            canvas.drawCircle(cx, cy - windowRadius, radius * 0.045f, paint);
        }

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

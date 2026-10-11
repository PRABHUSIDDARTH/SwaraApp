package com.psthetech.swara.ui.widget;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CircularArtworkView — Premium circular rotating artwork for Now Playing.
 *
 * Visual architecture:
 *  - High-res song artwork filled in a circular disc via BitmapShader (no fake CD textures)
 *  - Disc-only rotation driven by a frame-based spin engine (follows scratch gestures, keeps fling momentum)
 *  - Pause/resume preserves exact rotational angle without snapping back to 0°
 *  - Song change resets rotation cleanly to 0°
 *  - Stationary circular progress arc beginning at 12 o'clock (-90°)
 *  - Stationary seek knob indicating playback position
 *  - Circular seeking via angular delta touch gesture, handling 0°/360° boundary wrap-around
 *  - Subtle ambient glow and specular highlight
 *  - Strict 1:1 mathematical circularity via custom onMeasure()
 */
public class CircularArtworkView extends View {

    // One full rotation every 10 seconds (standard physical LP speed feel)
    private static final long ROTATION_DURATION_MS = 10_000L;
    private static final float BASE_DEG_PER_MS = 360f / ROTATION_DURATION_MS;
    // How quickly a flung disc sheds its extra speed, and how quickly it spins up from rest
    private static final float FLING_DECAY_TAU_MS = 420f;
    private static final float SPIN_UP_TAU_MS = 260f;
    private static final float STOP_EPSILON_DEG_PER_MS = 0.002f;
    // Fastest release spin we honour (about 3 turns per second)
    private static final float MAX_FLING_DEG_PER_MS = 1.1f;
    // Dead-zone degrees to ignore accidental micro-movements
    private static final float SEEK_DEAD_ZONE_DEG = CircularSeekHelper.DEFAULT_DEAD_ZONE_DEG;
    // Degrees of drag mapped per second of seek
    private static final float DEG_PER_SEEK_SECOND = CircularSeekHelper.DEFAULT_DEG_PER_SECOND;
    // Throttle seek callbacks to keep media transport smooth
    private static final long SEEK_THROTTLE_MS = 80L;

    // Disc Rotation
    @Nullable private TimeAnimator spinAnimator;
    private float discRotation = 0f;
    private float spinVelocity = 0f;   // deg per ms, positive = clockwise
    private boolean playing = false;

    // Progress
    private float progress = 0f;
    private float progressStrokeWidthPx;
    private float progressTrackWidthPx;

    // Paints
    private final Paint artworkPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Theme colors
    @ColorInt private int glowColor = Color.argb(60, 120, 80, 200);
    @ColorInt private int progressColor = Color.parseColor("#C9A84C");
    @ColorInt private int progressTrackColor = Color.argb(50, 200, 200, 200);
    @ColorInt private int borderColor = Color.argb(80, 255, 255, 255);
    @ColorInt private int knobColor = Color.parseColor("#C9A84C");
    private boolean isNightMode = true;

    // Artwork
    @Nullable private Bitmap artworkBitmap;
    @Nullable private BitmapShader artworkShader;
    private final Matrix shaderMatrix = new Matrix();

    // Geometry
    private float cx, cy, artworkRadius, ringRadius;
    private final RectF progressRectF = new RectF();

    // Touch / seek
    private boolean isTouchSeeking = false;
    private float touchStartAngle = 0f;
    private float lastTouchAngle = 0f;
    private long lastSeekTime = 0L;
    private long lastMoveTime = 0L;
    private float pendingSeekDeg = 0f;
    private float totalDragDeg = 0f;

    @Nullable private SeekListener seekListener;

    public interface SeekListener {
        void onSeekDelta(float deltaSeconds);
        void onSeekEnd();
    }

    public CircularArtworkView(Context context) {
        super(context);
        init(context);
    }

    public CircularArtworkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CircularArtworkView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        progressStrokeWidthPx = 5f * density;
        progressTrackWidthPx = 4f * density;

        artworkPaint.setStyle(Paint.Style.FILL);
        artworkPaint.setDither(true);

        fallbackPaint.setStyle(Paint.Style.FILL);
        fallbackPaint.setColor(Color.argb(220, 32, 24, 48));

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(45, 0, 0, 0));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * density);
        borderPaint.setColor(borderColor);

        progressTrackPaint.setStyle(Paint.Style.STROKE);
        progressTrackPaint.setStrokeWidth(progressTrackWidthPx);
        progressTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        progressTrackPaint.setColor(progressTrackColor);

        progressArcPaint.setStyle(Paint.Style.STROKE);
        progressArcPaint.setStrokeWidth(progressStrokeWidthPx);
        progressArcPaint.setStrokeCap(Paint.Cap.ROUND);
        progressArcPaint.setColor(progressColor);

        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(knobColor);

        knobGlowPaint.setStyle(Paint.Style.FILL);
        knobHighlightPaint.setStyle(Paint.Style.FILL);

        specularPaint.setStyle(Paint.Style.FILL);
        specularPaint.setColor(Color.argb(isNightMode ? 22 : 35, 255, 255, 255));

        setLayerType(LAYER_TYPE_HARDWARE, null);
        setupRotationAnimator();
    }

    // ── Measurement / Sizing ─────────────────────────────────────────────────────

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        int size;
        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            size = Math.min(width, height);
        } else if (widthMode == MeasureSpec.EXACTLY) {
            size = width;
        } else if (heightMode == MeasureSpec.EXACTLY) {
            size = height;
        } else {
            int defaultSize = (int) (280 * getResources().getDisplayMetrics().density);
            size = defaultSize;
            if (widthMode == MeasureSpec.AT_MOST) size = Math.min(size, width);
            if (heightMode == MeasureSpec.AT_MOST) size = Math.min(size, height);
        }
        // Strict 1:1 aspect ratio guarantees geometric circularity across all devices
        setMeasuredDimension(size, size);
    }

    // ── Rotation engine ──────────────────────────────────────────────────────────
    //
    // A single frame-driven engine replaces a fixed ValueAnimator so the disc behaves like
    // a physical platter: it follows the finger 1:1 while scratching, keeps the release
    // momentum, then eases back to playback speed (or spins down when paused).

    private void setupRotationAnimator() {
        if (spinAnimator != null) return;
        spinAnimator = new TimeAnimator();
        spinAnimator.setTimeListener((animation, totalTime, deltaTime) -> onSpinFrame(deltaTime));
    }

    private void onSpinFrame(long deltaMs) {
        if (deltaMs <= 0) return;
        float dt = Math.min(deltaMs, 48L); // clamp long frames (e.g. after a jank) to avoid jumps
        if (!isTouchSeeking) {
            float target = playing && !isReducedMotion() ? BASE_DEG_PER_MS : 0f;
            spinVelocity = DiscSpinPhysics.approach(spinVelocity, target, dt,
                    Math.abs(spinVelocity) > Math.abs(target) ? FLING_DECAY_TAU_MS : SPIN_UP_TAU_MS);
            discRotation = DiscSpinPhysics.wrap(discRotation + spinVelocity * dt);
            if (!playing && Math.abs(spinVelocity) < STOP_EPSILON_DEG_PER_MS) {
                spinVelocity = 0f;
                spinAnimator.end();
            }
        }
        invalidate();
    }

    private void ensureSpinning() {
        if (spinAnimator == null) setupRotationAnimator();
        if (!spinAnimator.isStarted() && isAttachedToWindow()) spinAnimator.start();
    }

    private boolean isReducedMotion() {
        return com.psthetech.swara.ui.glass.LiquidGlassRenderer.isReducedMotion(getContext());
    }

    /** Start/resume continuous rotation from the current angle without resetting. */
    public void startRotation() {
        playing = true;
        // Respect "Remove animations": the disc stays still, playback is unaffected.
        if (isReducedMotion()) return;
        ensureSpinning();
    }

    /** Pause: the disc spins down from its current angle (turntable feel), never snapping. */
    public void pauseRotation() {
        playing = false;
        if (isReducedMotion() && spinAnimator != null) {
            spinVelocity = 0f;
            spinAnimator.end();
        }
        invalidate();
    }

    /** Resume rotation seamlessly from the frozen angle. */
    public void resumeRotation() {
        startRotation();
    }

    /** Reset rotation for a newly loaded song. Stops the disc and resets to 0°. */
    public void resetRotation() {
        if (spinAnimator != null) spinAnimator.end();
        spinVelocity = 0f;
        discRotation = 0f;
        invalidate();
    }

    /** Sync rotation with playback state. */
    public void setPlaying(boolean playing) {
        if (playing) {
            resumeRotation();
        } else {
            pauseRotation();
        }
    }

    public float getDiscRotation() {
        return discRotation;
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    public void setArtworkBitmap(@Nullable Bitmap bitmap) {
        this.artworkBitmap = bitmap;
        rebuildShader();
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    public void setSeekListener(@Nullable SeekListener listener) {
        this.seekListener = listener;
    }

    public void setThemeColors(@ColorInt int glowColor, @ColorInt int progressColor,
                               @ColorInt int progressTrackColor, @ColorInt int borderColor,
                               @ColorInt int knobColor, boolean isNightMode) {
        this.glowColor = glowColor;
        this.progressColor = progressColor;
        this.progressTrackColor = progressTrackColor;
        this.borderColor = borderColor;
        this.knobColor = knobColor;
        this.isNightMode = isNightMode;

        progressTrackPaint.setColor(progressTrackColor);
        progressArcPaint.setColor(progressColor);
        knobPaint.setColor(knobColor);
        borderPaint.setColor(borderColor);
        specularPaint.setColor(Color.argb(isNightMode ? 22 : 35, 255, 255, 255));
        fallbackPaint.setColor(isNightMode ? Color.argb(220, 32, 24, 48) : Color.argb(220, 235, 230, 225));

        rebuildGlow();
        invalidate();
    }

    // ── Layout / Geometry ────────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        float minDim = Math.min(w, h);

        ringRadius = (minDim / 2f) * 0.93f;
        artworkRadius = ringRadius - progressStrokeWidthPx * 2.8f;

        float inset = progressStrokeWidthPx / 2f;
        progressRectF.set(cx - ringRadius + inset, cy - ringRadius + inset,
                cx + ringRadius - inset, cy + ringRadius - inset);

        rebuildShader();
        rebuildGlow();
    }

    private void rebuildShader() {
        if (artworkBitmap == null || artworkRadius <= 0) {
            artworkShader = null;
            artworkPaint.setShader(null);
            return;
        }
        float bw = artworkBitmap.getWidth();
        float bh = artworkBitmap.getHeight();
        float diameter = artworkRadius * 2f;
        float scale = Math.max(diameter / bw, diameter / bh);
        float dx = (diameter - bw * scale) / 2f;
        float dy = (diameter - bh * scale) / 2f;

        shaderMatrix.reset();
        shaderMatrix.setScale(scale, scale);
        shaderMatrix.postTranslate(cx - artworkRadius + dx, cy - artworkRadius + dy);

        artworkShader = new BitmapShader(artworkBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        artworkShader.setLocalMatrix(shaderMatrix);
        artworkPaint.setShader(artworkShader);
    }

    private void rebuildGlow() {
        if (artworkRadius <= 0) return;
        int r = Color.red(glowColor), g = Color.green(glowColor), b = Color.blue(glowColor);
        int a1 = isNightMode ? 80 : 55;
        int a2 = isNightMode ? 30 : 18;
        try {
            RadialGradient gradient = new RadialGradient(
                    cx, cy, artworkRadius * 1.45f,
                    new int[]{Color.argb(a1, r, g, b), Color.argb(a2, r, g, b), Color.argb(0, r, g, b)},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP);
            glowPaint.setShader(gradient);
        } catch (Exception ignored) { /* size not ready yet */ }
    }

    // ── Drawing ──────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (cx <= 0 || artworkRadius <= 0) return;

        // 1. Ambient glow (stationary, atmospheric background)
        canvas.drawCircle(cx, cy, artworkRadius * 1.45f, glowPaint);

        // 2. Subtle drop shadow for disc depth (stationary)
        canvas.drawCircle(cx + 2f, cy + 4f, artworkRadius, shadowPaint);

        // 3. Rotating Artwork Disc (rotation applied strictly to disc, not entire view)
        canvas.save();
        canvas.rotate(discRotation, cx, cy);
        if (artworkShader != null) {
            canvas.drawCircle(cx, cy, artworkRadius, artworkPaint);
        } else {
            canvas.drawCircle(cx, cy, artworkRadius, fallbackPaint);
        }
        canvas.restore();

        // 4. Specular sheen at top (stationary highlight over physical vinyl surface)
        drawSpecularHighlight(canvas);

        // 5. Outer disc border (crisp stationary glass edge)
        canvas.drawCircle(cx, cy, artworkRadius, borderPaint);

        // 6. Progress ring track (concentric circle outside artwork disc)
        canvas.drawOval(progressRectF, progressTrackPaint);

        // 7. Progress arc (stationary coordinate system, begins at 12 o'clock = -90°)
        float sweepAngle = progress * 360f;
        if (sweepAngle > 0.5f) {
            canvas.drawArc(progressRectF, -90f, sweepAngle, false, progressArcPaint);
        }

        // 8. Seek knob positioned at the head of the progress arc
        if (progress > 0.002f) {
            drawSeekKnob(canvas, sweepAngle);
        }
    }

    private void drawSeekKnob(Canvas canvas, float sweepAngle) {
        double knobRad = Math.toRadians(-90.0 + sweepAngle);
        float kx = cx + (float) (ringRadius * Math.cos(knobRad));
        float ky = cy + (float) (ringRadius * Math.sin(knobRad));
        float knobR = progressStrokeWidthPx * 1.5f;

        // Subtle glow around knob
        int kr = Color.red(knobColor), kg = Color.green(knobColor), kb = Color.blue(knobColor);
        knobGlowPaint.setColor(Color.argb(isNightMode ? 60 : 40, kr, kg, kb));
        canvas.drawCircle(kx, ky, knobR * 2.2f, knobGlowPaint);

        // Knob body
        knobPaint.setColor(knobColor);
        canvas.drawCircle(kx, ky, knobR, knobPaint);

        // Knob inner highlight
        knobHighlightPaint.setColor(Color.argb(140, 255, 255, 255));
        canvas.drawCircle(kx - knobR * 0.3f, ky - knobR * 0.3f, knobR * 0.38f, knobHighlightPaint);
    }

    private void drawSpecularHighlight(Canvas canvas) {
        canvas.save();
        canvas.clipRect(cx - artworkRadius, cy - artworkRadius, cx + artworkRadius, cy - artworkRadius * 0.1f);
        canvas.drawCircle(cx, cy - artworkRadius * 0.05f, artworkRadius * 0.7f, specularPaint);
        canvas.restore();
    }

    // ── Touch / Seek Gesture ─────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();
        float dist = (float) Math.sqrt((tx - cx) * (tx - cx) + (ty - cy) * (ty - cy));
        float slop = 28f;
        // Anywhere on the disc or its ring can be "scratched"; a still tap on the disc edits artwork.
        boolean onDisc = dist <= ringRadius + slop;
        boolean inArtwork = dist < artworkRadius - slop;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!onDisc) return false;
                touchStartAngle = CircularSeekHelper.calculateAngleDeg(tx, ty, cx, cy);
                lastTouchAngle = touchStartAngle;
                lastMoveTime = event.getEventTime();
                pendingSeekDeg = 0f;
                totalDragDeg = 0f;
                isTouchSeeking = true;
                // Catch the platter: it stops under the finger.
                spinVelocity = 0f;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isTouchSeeking) break;
                // Near the centre the angle is unstable; ignore those samples.
                if (dist < artworkRadius * 0.12f) return true;
                float cur = CircularSeekHelper.calculateAngleDeg(tx, ty, cx, cy);
                float delta = CircularSeekHelper.angularDelta(lastTouchAngle, cur);
                lastTouchAngle = cur;
                totalDragDeg += Math.abs(delta);

                // Disc follows the finger in whichever direction it moves.
                discRotation = DiscSpinPhysics.wrap(discRotation + delta);
                long now = event.getEventTime();
                spinVelocity = DiscSpinPhysics.trackVelocity(spinVelocity, delta, now - lastMoveTime);
                lastMoveTime = now;
                invalidate();

                if (totalDragDeg > SEEK_DEAD_ZONE_DEG) {
                    pendingSeekDeg += delta;
                    if (now - lastSeekTime > SEEK_THROTTLE_MS) flushSeek(now);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!isTouchSeeking) break;
                isTouchSeeking = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                boolean wasDrag = totalDragDeg > SEEK_DEAD_ZONE_DEG;
                if (wasDrag) flushSeek(event.getEventTime());
                if (seekListener != null) seekListener.onSeekEnd();

                // A finger that stopped before lifting carries no momentum.
                if (event.getEventTime() - lastMoveTime > 80L || isReducedMotion()) spinVelocity = 0f;
                spinVelocity = Math.max(-MAX_FLING_DEG_PER_MS, Math.min(MAX_FLING_DEG_PER_MS, spinVelocity));
                if (spinVelocity != 0f || playing) ensureSpinning();

                if (!wasDrag && inArtwork && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    /** Sends accumulated drag degrees as a seek so no movement is lost to throttling. */
    private void flushSeek(long now) {
        if (pendingSeekDeg != 0f && seekListener != null) {
            seekListener.onSeekDelta(pendingSeekDeg / DEG_PER_SEEK_SECOND);
        }
        pendingSeekDeg = 0f;
        lastSeekTime = now;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (spinAnimator != null) {
            spinAnimator.end();
            spinAnimator.setTimeListener(null);
            spinAnimator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setupRotationAnimator();
        if (playing && !isReducedMotion()) ensureSpinning();
    }
}

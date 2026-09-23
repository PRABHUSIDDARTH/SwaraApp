package com.psthetech.swara.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CircularArtworkView — Premium circular rotating artwork for Now Playing.
 *
 * Visual architecture:
 *  - High-res song artwork filled in a circular disc via BitmapShader (no fake CD textures)
 *  - Disc-only rotation: smoothly animated via ValueAnimator without rotating the view or progress ring
 *  - Pause/resume preserves exact rotational angle without snapping back to 0°
 *  - Song change resets rotation cleanly to 0°
 *  - Stationary circular progress arc beginning at 12 o'clock (-90°)
 *  - Stationary seek knob indicating playback position
 *  - Strict 1:1 mathematical circularity via custom onMeasure()
 */
public class CircularArtworkView extends View {

    // One full rotation every 10 seconds (standard physical LP speed feel)
    private static final long ROTATION_DURATION_MS = 10_000L;

    // Disc Rotation
    private ValueAnimator rotationAnimator;
    private float discRotation = 0f;

    // Progress
    private float progress = 0f;
    private float progressStrokeWidthPx;
    private float progressTrackWidthPx;

    // Paints
    private final Paint artworkPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Theme colors
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

    // ── Rotation ─────────────────────────────────────────────────────────────────

    private void setupRotationAnimator() {
        if (rotationAnimator != null) return;
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        rotationAnimator.setDuration(ROTATION_DURATION_MS);
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.setRepeatMode(ValueAnimator.RESTART);
        rotationAnimator.addUpdateListener(animation -> {
            discRotation = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    /** Start/resume continuous rotation from current angle without resetting. */
    public void startRotation() {
        if (rotationAnimator == null) {
            setupRotationAnimator();
        }
        if (rotationAnimator.isRunning()) {
            return;
        }
        long playTime = (long) ((discRotation / 360f) * ROTATION_DURATION_MS);
        if (playTime < 0) playTime = 0;
        if (playTime >= ROTATION_DURATION_MS) playTime = 0;
        rotationAnimator.setCurrentPlayTime(playTime);
        rotationAnimator.start();
    }

    /** Pause rotation immediately and freeze at the exact current angle. */
    public void pauseRotation() {
        if (rotationAnimator != null && rotationAnimator.isRunning()) {
            discRotation = (float) rotationAnimator.getAnimatedValue();
            rotationAnimator.cancel();
            invalidate();
        }
    }

    /** Resume rotation seamlessly from the frozen angle. */
    public void resumeRotation() {
        startRotation();
    }

    /** Reset rotation for a newly loaded song. Stops animation and resets to 0°. */
    public void resetRotation() {
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
        }
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

    // ── Drawing ──────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (cx <= 0 || artworkRadius <= 0) return;

        // 1. Subtle drop shadow for disc depth (stationary)
        canvas.drawCircle(cx + 2f, cy + 4f, artworkRadius, shadowPaint);

        // 2. Rotating Artwork Disc (rotation applied strictly to disc, not entire view)
        canvas.save();
        canvas.rotate(discRotation, cx, cy);
        if (artworkShader != null) {
            canvas.drawCircle(cx, cy, artworkRadius, artworkPaint);
        } else {
            canvas.drawCircle(cx, cy, artworkRadius, fallbackPaint);
        }
        canvas.restore();

        // 3. Specular sheen at top (stationary highlight over physical vinyl surface)
        drawSpecularHighlight(canvas);

        // 4. Outer disc border (crisp stationary glass edge)
        canvas.drawCircle(cx, cy, artworkRadius, borderPaint);

        // 5. Progress ring track (concentric circle outside artwork disc)
        canvas.drawOval(progressRectF, progressTrackPaint);

        // 6. Progress arc (stationary coordinate system, begins at 12 o'clock = -90°)
        float sweepAngle = progress * 360f;
        if (sweepAngle > 0.5f) {
            canvas.drawArc(progressRectF, -90f, sweepAngle, false, progressArcPaint);
        }

        // 7. Seek knob positioned at the head of the progress arc
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

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (rotationAnimator != null) {
            if (rotationAnimator.isRunning()) {
                discRotation = (float) rotationAnimator.getAnimatedValue();
            }
            rotationAnimator.cancel();
            rotationAnimator.removeAllUpdateListeners();
            rotationAnimator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotationAnimator == null) {
            setupRotationAnimator();
        }
    }
}

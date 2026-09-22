package com.psthetech.swara.ui.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.psthetech.swara.ui.theme.DesignTokens;

/**
 * LiquidGlassRenderer — centralized glass surface application engine.
 *
 * Design principles (from BitChord glass study):
 *  - Glass = backdrop blur + translucent fill + highlight gradient + stroke
 *  - All colors derived from DesignTokens — zero hardcoded values
 *  - Blur is progressive: full blur on API 31+, gradient-only on older APIs
 *  - Respects reduced-motion accessibility setting (no scale animations)
 *  - Surgical application: only nav, mini-player, search bar, action pills
 *
 * Architecture:
 *  - Static utility — no instance state
 *  - No Android ViewGroup or layout manipulation — only background drawables
 *  - Theme-aware: takes DesignTokens as parameter, never reads globals
 *
 * BitChord-inspired constants:
 *  BLUR_RADIUS_DP = 8f (matching BitChord's BLUR_RADIUS_DP)
 *  SURFACE_OPACITY ≈ 0.72 for nav, 0.62 for cards (scaled from BitChord's 0.4f base)
 *  HIGHLIGHT_OPACITY = 14% dark / 50% light (matches VIBRANCY=1f interpretation)
 */
public final class LiquidGlassRenderer {

    // BitChord-derived constants
    public static final float BLUR_RADIUS_DP = 8f;
    private static final float SCALE_ON_PRESS = 0.97f;
    private static final long PRESS_ANIM_DURATION_MS = 80;
    private static final long RELEASE_ANIM_DURATION_MS = 160;

    private LiquidGlassRenderer() { /* static utility */ }

    // ===== API Support Checks =====

    /**
     * Returns true if the device supports GPU-accelerated backdrop blur
     * via RenderEffect (API 31+, Android 12+).
     */
    public static boolean isBlurSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /**
     * Returns true if the user has disabled animations (accessibility).
     * In this case, skip scale animations but still apply glass drawables.
     */
    public static boolean isReducedMotion(@NonNull Context context) {
        try {
            float scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
            );
            return scale == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== Glass Background Application =====

    /**
     * Applies a glass background drawable to a View.
     * Uses tokens.createGlassMiniPlayerDrawable() for mini-player surfaces
     * and tokens.createGlassNavDrawable() for navigation surfaces.
     *
     * Also sets clip-to-outline so the glass corners are respected by children.
     *
     * @param view         Target view to receive the glass background.
     * @param tokens       Current design tokens (theme-aware).
     * @param cornerRadiusDp Corner radius in dp (pass 999 for pill, 20 for mini-player).
     */
    public static void applyGlassBackground(
            @NonNull View view,
            @NonNull DesignTokens tokens,
            float cornerRadiusDp
    ) {
        Context ctx = view.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        GradientDrawable glass;
        if (cornerRadiusDp >= 100f) {
            // Pill-shaped glass (nav bar)
            glass = tokens.createGlassNavDrawable(ctx);
        } else {
            // Rounded rectangle glass (mini-player, cards)
            glass = tokens.createGlassMiniPlayerDrawable(ctx);
        }

        view.setBackground(glass);
        view.setClipToOutline(true);

        // Elevation for floating effect
        ViewCompat.setElevation(view, 6 * density);
    }

    /**
     * Applies pill-shaped glass to a view (convenience overload for chips/search).
     */
    public static void applyGlassPill(@NonNull View view, @NonNull DesignTokens tokens) {
        view.setBackground(tokens.createGlassPillDrawable(view.getContext()));
        view.setClipToOutline(true);
        ViewCompat.setElevation(view, 2 * view.getContext().getResources().getDisplayMetrics().density);
    }

    /**
     * Applies glass card surface to a view.
     */
    public static void applyGlassCard(@NonNull View view, @NonNull DesignTokens tokens) {
        view.setBackground(tokens.createGlassCardDrawable(view.getContext()));
        view.setClipToOutline(true);
        ViewCompat.setElevation(view, 3 * view.getContext().getResources().getDisplayMetrics().density);
    }

    // ===== Press Interaction =====

    /**
     * Attaches a subtle scale-down press animation to a view.
     * Respects reduced-motion setting — no animation if user disabled animations.
     *
     * Scale: 0.97x on press, returns to 1.0x on release.
     * This matches BitChord's Spring animation behavior adapted for View system.
     */
    public static void attachPressAnimation(@NonNull View view) {
        boolean reducedMotion = isReducedMotion(view.getContext());
        if (reducedMotion) return;

        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(SCALE_ON_PRESS)
                            .scaleY(SCALE_ON_PRESS)
                            .setDuration(PRESS_ANIM_DURATION_MS)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(RELEASE_ANIM_DURATION_MS)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                            .start();
                    break;
            }
            // Return false so the click listener still fires
            return false;
        });
    }

    // ===== Nav Indicator =====

    /**
     * Creates a glass-style active indicator drawable for BottomNavigationView items.
     * Uses accent at reduced alpha — the selection indicator is a subtle accent glow,
     * not a heavy solid block.
     */
    public static GradientDrawable createNavActiveIndicator(
            @NonNull Context context,
            @NonNull DesignTokens tokens
    ) {
        float density = context.getResources().getDisplayMetrics().density;
        @ColorInt int indicatorColor = tokens.getGlassAccentGlowColor();
        GradientDrawable indicator = new GradientDrawable();
        indicator.setShape(GradientDrawable.RECTANGLE);
        indicator.setColor(indicatorColor);
        indicator.setCornerRadius(999 * density); // Pill
        return indicator;
    }

    // ===== Tinted Overlay =====

    /**
     * Returns a semi-transparent accent tint color suitable for overlaying on glass
     * to indicate the currently playing/selected state, without destroying contrast.
     */
    @ColorInt
    public static int getGlassPlayingSurfaceColor(@NonNull DesignTokens tokens) {
        return tokens.getPlaybackSurfaceColor();
    }
}

package com.psthetech.swara.util;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

/**
 * FavoriteAnimationHelper — Reusable micro-animation for the favorite icon.
 *
 * Usage in onBindViewHolder:
 *   - Do NOT call animateFavoriteAdd/Remove during bind; only call from user action callbacks.
 *   - Call cancelFavoriteAnimation(holder.ivFavorite) inside onViewRecycled.
 *
 * RecyclerView safety:
 *   - All animations use ViewPropertyAnimator which auto-cancels on new calls.
 *   - cancelFavoriteAnimation() resets scale and alpha to neutral, removing any mid-flight state.
 */
public final class FavoriteAnimationHelper {

    private FavoriteAnimationHelper() {}

    /**
     * Play a satisfying "add to favorites" scale-pulse animation.
     * Scale up with overshoot, then settle back to 1.0.
     * Duration: ~280ms total.
     *
     * @param iv          The favorite ImageView.
     * @param accentColor Theme accent color (reserved for future glow ring extension).
     */
    public static void animateFavoriteAdd(ImageView iv, int accentColor) {
        if (iv == null) return;
        // Reset to baseline first (protects against half-finished recycled animations)
        iv.animate().cancel();
        iv.setScaleX(1f);
        iv.setScaleY(1f);
        iv.setAlpha(1f);

        // Phase 1: scale up quickly with overshoot feel
        iv.animate()
                .scaleX(1.35f)
                .scaleY(1.35f)
                .setDuration(150)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .withEndAction(() -> {
                    // Phase 2: settle back to normal
                    iv.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(130)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    /**
     * Play a subtle "remove from favorites" alpha-fade transition.
     * Much quieter than the add animation — no celebration.
     * Duration: ~180ms.
     *
     * @param iv The favorite ImageView.
     */
    public static void animateFavoriteRemove(ImageView iv) {
        if (iv == null) return;
        iv.animate().cancel();
        iv.setScaleX(1f);
        iv.setScaleY(1f);
        iv.setAlpha(1f);

        // Brief dip in alpha then back — subtle acknowledgement
        iv.animate()
                .alpha(0.4f)
                .setDuration(90)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    iv.animate()
                            .alpha(1f)
                            .setDuration(90)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    /**
     * Cancel any in-flight animation and reset the view to a fully neutral state.
     * Call from RecyclerView.Adapter.onViewRecycled() to prevent animation leakage.
     *
     * @param iv The favorite ImageView (may be null — safe to call).
     */
    public static void cancelFavoriteAnimation(ImageView iv) {
        if (iv == null) return;
        iv.animate().cancel();
        iv.setScaleX(1f);
        iv.setScaleY(1f);
        iv.setAlpha(1f);
    }
}

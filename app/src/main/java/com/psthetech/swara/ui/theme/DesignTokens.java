package com.psthetech.swara.ui.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;

import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.MorphismStyle;

/**
 * Complete Semantic Design Tokens for Swara V2.1.
 * Dynamically computes colors, surfaces, text contrast, icons, and drawables based on (ColorTheme + ThemeMode + MorphismStyle).
 */
public class DesignTokens {

    private final ColorTheme colorTheme;
    private final MorphismStyle style;
    private final boolean isNightMode;

    @ColorInt private final int backgroundColor;
    @ColorInt private final int backgroundSecondaryColor;
    @ColorInt private final int surfaceColor;
    @ColorInt private final int surfaceElevatedColor;
    @ColorInt private final int surfaceVariantColor;
    @ColorInt private final int primaryColor;
    @ColorInt private final int primaryContainerColor;
    @ColorInt private final int secondaryColor;
    @ColorInt private final int accentColor;

    @ColorInt private final int textPrimaryColor;
    @ColorInt private final int textSecondaryColor;
    @ColorInt private final int textTertiaryColor;

    @ColorInt private final int iconPrimaryColor;
    @ColorInt private final int iconSecondaryColor;

    @ColorInt private final int strokeColor;
    @ColorInt private final int dividerColor;
    @ColorInt private final int selectedColor;
    @ColorInt private final int overlayColor;

    private final int cornerRadiusDp;
    private final int elevationDp;
    private final int strokeWidthDp;

    public DesignTokens(Context context, MorphismStyle style, boolean isNightMode) {
        this(context, ColorTheme.SWARA, style, isNightMode);
    }

    public DesignTokens(Context context, ColorTheme colorTheme, MorphismStyle style, boolean isNightMode) {
        this.colorTheme = colorTheme != null ? colorTheme : ColorTheme.SWARA;
        this.style = style != null ? style : MorphismStyle.LIQUID_GLASS;
        this.isNightMode = isNightMode;

        // Morphism geometry overrides
        this.cornerRadiusDp = this.style.getDefaultCornerRadiusDp();
        this.elevationDp = this.style.getDefaultElevationDp();
        this.strokeWidthDp = this.style.getDefaultStrokeWidthDp();

        // Apple-style neutral material system: every theme shares the same system greys,
        // and the ColorTheme only contributes a single accent hue.
        final int accent = resolveAccent(this.colorTheme, isNightMode);
        this.accentColor = accent;
        this.secondaryColor = accent;
        if (isNightMode) {
            this.backgroundColor = Color.parseColor("#000000");
            this.backgroundSecondaryColor = Color.parseColor("#0B0B0D");
            this.surfaceColor = Color.parseColor("#1C1C1E");
            this.surfaceElevatedColor = Color.parseColor("#2C2C2E");
            this.surfaceVariantColor = Color.parseColor("#2C2C2E");
            this.primaryColor = accent;
            this.primaryContainerColor = Color.parseColor("#2C2C2E");
            this.textPrimaryColor = Color.parseColor("#FFFFFF");
            this.textSecondaryColor = Color.parseColor("#A1A1A6");
            this.textTertiaryColor = Color.parseColor("#8E8E93");
            this.iconPrimaryColor = Color.parseColor("#FFFFFF");
            this.iconSecondaryColor = Color.parseColor("#A1A1A6");
            this.strokeColor = Color.parseColor("#38383A");
            this.dividerColor = Color.parseColor("#2C2C2E");
            this.selectedColor = withAlpha(accent, 0x26);
            this.overlayColor = Color.parseColor("#CC000000");
        } else {
            this.backgroundColor = Color.parseColor("#F2F2F7");
            this.backgroundSecondaryColor = Color.parseColor("#EBEBF0");
            this.surfaceColor = Color.parseColor("#FFFFFF");
            this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
            this.surfaceVariantColor = Color.parseColor("#E9E9EE");
            this.primaryColor = Color.parseColor("#1C1C1E");
            this.primaryContainerColor = Color.parseColor("#E9E9EE");
            this.textPrimaryColor = Color.parseColor("#1C1C1E");
            this.textSecondaryColor = Color.parseColor("#55555A");
            this.textTertiaryColor = Color.parseColor("#6E6E73");
            this.iconPrimaryColor = Color.parseColor("#1C1C1E");
            this.iconSecondaryColor = Color.parseColor("#55555A");
            this.strokeColor = Color.parseColor("#D1D1D6");
            this.dividerColor = Color.parseColor("#E5E5EA");
            this.selectedColor = withAlpha(accent, 0x1F);
            this.overlayColor = Color.parseColor("#CCF2F2F7");
        }
    }

    /** One accent per theme; light values are tuned for >= 4.5:1 on white. */
    @ColorInt
    static int resolveAccent(ColorTheme theme, boolean night) {
        switch (theme) {
            case MIDNIGHT:  return Color.parseColor(night ? "#0A84FF" : "#0066D6");
            case LAVENDER:  return Color.parseColor(night ? "#BF5AF2" : "#8E3DBD");
            case CHAMPAGNE: return Color.parseColor(night ? "#FF9F0A" : "#B25000");
            case ROSE:      return Color.parseColor(night ? "#FF375F" : "#D30F45");
            case OCEAN:     return Color.parseColor(night ? "#40C8E0" : "#0A7C8C");
            case FOREST:    return Color.parseColor(night ? "#30D158" : "#1E7F3A");
            case OFF_WHITE: return Color.parseColor(night ? "#E8C97A" : "#5C4A1E");
            case SWARA:
            default:        return Color.parseColor(night ? "#D4AF5A" : "#8A6610");
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public ColorTheme getColorTheme() {
        return colorTheme;
    }

    public MorphismStyle getStyle() {
        return style;
    }

    public boolean isNightMode() {
        return isNightMode;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getBackgroundSecondaryColor() {
        return backgroundSecondaryColor;
    }

    public int getSurfaceColor() {
        return surfaceColor;
    }

    public int getSurfaceElevatedColor() {
        return surfaceElevatedColor;
    }

    public int getSurfaceVariantColor() {
        return surfaceVariantColor;
    }

    public int getPrimaryColor() {
        return primaryColor;
    }

    public int getPrimaryContainerColor() {
        return primaryContainerColor;
    }

    public int getSecondaryColor() {
        return secondaryColor;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public int getTextPrimaryColor() {
        return textPrimaryColor;
    }

    public int getPrimaryTextColor() {
        return textPrimaryColor;
    }

    public int getTextSecondaryColor() {
        return textSecondaryColor;
    }

    public int getSecondaryTextColor() {
        return textSecondaryColor;
    }

    public int getTextTertiaryColor() {
        return textTertiaryColor;
    }

    public int getDimTextColor() {
        return textTertiaryColor;
    }

    public int getIconPrimaryColor() {
        return iconPrimaryColor;
    }

    public int getIconSecondaryColor() {
        return iconSecondaryColor;
    }

    public int getStrokeColor() {
        return strokeColor;
    }

    public int getDividerColor() {
        return dividerColor;
    }

    public int getSelectedColor() {
        return selectedColor;
    }

    public int getOverlayColor() {
        return overlayColor;
    }

    // ===== Additional semantic tokens =====

    /** Accent tint for a filled favorite icon. Uses readable accent in light mode for crisp contrast. */
    public int getFavoriteActiveColor() {
        return isNightMode ? accentColor : getReadableAccentColor();
    }

    /**
     * Tint for an empty/unfilled favorite icon.
     * In dark mode uses iconSecondaryColor; in light mode uses a slightly muted icon color
     * to ensure visibility on light surfaces.
     */
    public int getFavoriteInactiveColor() {
        // In light mode iconSecondaryColor is already the darker theme secondary — good contrast.
        return iconSecondaryColor;
    }

    /**
     * Semantic playback surface color for the currently playing item row background.
     * In dark mode: mixes surfaceElevatedColor with accent at 14% for an atmospheric tinted glass feel.
     * In light mode: mixes surfaceVariantColor with accent at 22% so it is immediately and clearly
     * identifiable against light background surfaces while preserving WCAG AAA text contrast.
     */
    public int getPlaybackSurfaceColor() {
        // Quiet selection fill: the accent-coloured title carries the state, not a loud card.
        return isNightMode ? mix(surfaceColor, accentColor, 0.06f) : mix(surfaceColor, accentColor, 0.07f);
    }

    /**
     * Backward-compatible alias for getPlaybackSurfaceColor().
     */
    public int getPlaybackHighlightColor() {
        return getPlaybackSurfaceColor();
    }

    /**
     * Semantic playback stroke border color for currently playing rows.
     * In dark mode: uses a restrained ~35% alpha accent border.
     * In light mode: mixes accent with deep primary color at 20% to produce a crisp, readable,
     * high-definition stroke that never washes out on light surfaces.
     */
    public int getPlaybackStrokeColor() {
        return Color.TRANSPARENT; // no border on the playing row
    }

    /**
     * Soft accent glow color (with alpha) for ambient highlight cues.
     * In dark mode: uses ~31% alpha accent for atmospheric glow.
     * In light mode: uses ~47% alpha accent for controlled definition.
     */
    public int getPlaybackGlowColor() {
        int r = android.graphics.Color.red(accentColor);
        int g = android.graphics.Color.green(accentColor);
        int b = android.graphics.Color.blue(accentColor);
        if (isNightMode) {
            return android.graphics.Color.argb(80, r, g, b); // ~31%
        } else {
            return android.graphics.Color.argb(120, r, g, b); // ~47%
        }
    }

    /**
     * Semantic icon/accent tint for playback indicators.
     */
    public int getPlaybackIconColor() {
        return getReadableAccentColor();
    }

    /**
     * A readable foreground variant of the accent color.
     * In light mode, some accent colors (e.g. pale champagne gold) have insufficient contrast
     * on light surfaces, so we dynamically darken the hue while preserving its color theme identity.
     * In dark mode the accent is vibrant and readable as-is.
     */
    public int getReadableAccentColor() {
        if (isNightMode) {
            return accentColor;
        }
        // For light mode: ensure >= 4.5:1 WCAG AA contrast against light surfaces
        double r = android.graphics.Color.red(accentColor) / 255.0;
        double g = android.graphics.Color.green(accentColor) / 255.0;
        double b = android.graphics.Color.blue(accentColor) / 255.0;
        double lr = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        double lg = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        double lb = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        double l1 = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
        double contrast = 1.05 / (l1 + 0.05);

        if (contrast < 5.2) {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(accentColor, hsv);
            hsv[2] = Math.min(hsv[2], 0.34f); // Darken value to guarantee >= 4.5:1 across all tinted light backgrounds
            hsv[1] = Math.max(hsv[1], 0.75f); // Rich theme saturation
            return android.graphics.Color.HSVToColor(hsv);
        }
        return accentColor;
    }

    /**
     * Text color to use on accent-colored button backgrounds.
     * Dark text for light-accent buttons; white for dark-accent buttons.
     */
    public int getButtonTextColor() {
        double lum = (0.299 * android.graphics.Color.red(accentColor)
                + 0.587 * android.graphics.Color.green(accentColor)
                + 0.114 * android.graphics.Color.blue(accentColor)) / 255.0;
        return lum > 0.55 ? android.graphics.Color.parseColor("#1A1A1A")
                : android.graphics.Color.WHITE;
    }

    public int getCornerRadiusDp() {
        return cornerRadiusDp;
    }

    public int getElevationDp() {
        return elevationDp;
    }

    public int getStrokeWidthDp() {
        return strokeWidthDp;
    }

    /**
     * Creates an ambient glow drawable for the Now Playing album art.
     * Uses a multi-stop radial gradient centered behind the art, with soft fading alpha.
     */
    public Drawable createArtworkGlowDrawable(int glowColor) {
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setShape(GradientDrawable.RECTANGLE);
        int r = android.graphics.Color.red(glowColor);
        int g = android.graphics.Color.green(glowColor);
        int b = android.graphics.Color.blue(glowColor);
        int centerAlpha = isNightMode ? 90 : 50;
        int midAlpha    = isNightMode ? 40 : 20;
        int centerColor = android.graphics.Color.argb(centerAlpha, r, g, b);
        int midColor    = android.graphics.Color.argb(midAlpha, r, g, b);
        int outerColor  = android.graphics.Color.argb(0, r, g, b);
        glow.setColors(new int[] { centerColor, midColor, outerColor });
        glow.setGradientRadius(550f);
        glow.setCornerRadius(60f);
        return glow;
    }

    /**
     * Creates a circular glass play/pause button background with subtle accent glow and border.
     */
    public Drawable createPlayButtonDrawable(Context context) {
        // Apple Music style: the play glyph stands alone; only a soft circular press target.
        GradientDrawable disk = new GradientDrawable();
        disk.setShape(GradientDrawable.OVAL);
        disk.setColor(isNightMode ? Color.argb(28, 255, 255, 255) : Color.argb(14, 0, 0, 0));
        return disk;
    }

    /**
     * Create a card background drawable adhering to design tokens.
     */
    public Drawable createCardDrawable(Context context) {
        if (style == MorphismStyle.LIQUID_GLASS) {
            return createGlassDrawable(context, false);
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(surfaceColor);

        float density = context.getResources().getDisplayMetrics().density;
        float radiusPx = cornerRadiusDp * density;
        drawable.setCornerRadius(radiusPx);

        if (strokeWidthDp > 0) {
            int strokePx = Math.max(1, Math.round(strokeWidthDp * density));
            drawable.setStroke(strokePx, strokeColor);
        }
        return drawable;
    }

    /**
     * Create a surface variant container drawable (e.g. search box, dialog, elevated card).
     */
    public Drawable createSurfaceVariantDrawable(Context context) {
        if (style == MorphismStyle.LIQUID_GLASS) {
            return createGlassDrawable(context, true);
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(surfaceVariantColor);

        float density = context.getResources().getDisplayMetrics().density;
        float radiusPx = cornerRadiusDp * density;
        drawable.setCornerRadius(radiusPx);

        if (strokeWidthDp > 0) {
            int strokePx = Math.max(1, Math.round(strokeWidthDp * density));
            drawable.setStroke(strokePx, strokeColor);
        }
        return drawable;
    }

    /**
     * Create a primary action button background drawable.
     */
    public Drawable createButtonDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(accentColor);

        float density = context.getResources().getDisplayMetrics().density;
        float radiusPx = style == MorphismStyle.BRUTALISM ? 2 * density : 999 * density;
        drawable.setCornerRadius(radiusPx);

        if (style == MorphismStyle.BRUTALISM) {
            drawable.setStroke(Math.round(2 * density), Color.BLACK);
        }
        return drawable;
    }

    // ===== Semantic Component Helpers =====

    public int getSearchBackgroundColor() {
        return isNightMode ? surfaceVariantColor : surfaceColor;
    }

    public int getSearchTextColor() {
        return textPrimaryColor;
    }

    public int getSearchHintColor() {
        return textTertiaryColor;
    }

    public int getNavBackgroundColor() {
        return surfaceColor;
    }

    public int getNavSelectedColor() {
        return accentColor;
    }

    public int getNavUnselectedColor() {
        return textTertiaryColor;
    }

    public int getDialogBackgroundColor() {
        return surfaceColor;
    }

    public int getDialogTextColor() {
        return textPrimaryColor;
    }

    public int getDialogSecondaryTextColor() {
        return textSecondaryColor;
    }

    public int getOnAccentColor() {
        return getButtonTextColor();
    }

    /** Palette-aware glass approximation; no offscreen blur or continuous GPU work. */
    private GradientDrawable createGlassDrawable(Context context, boolean elevated) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable glass = new GradientDrawable();
        glass.setShape(GradientDrawable.RECTANGLE);
        glass.setColor(elevated ? surfaceElevatedColor : surfaceColor);
        glass.setCornerRadius(cornerRadiusDp * density);
        return glass;
    }

    /** An opaque backdrop keeps the glass and text contrast stable in every palette. */
    public Drawable createAmbientDrawable() {
        return new android.graphics.drawable.ColorDrawable(backgroundColor);
    }

    // ===== Liquid Glass Semantic Tokens =====
    // All derived from existing palette fields — zero hardcoded colors.
    // Adapt automatically across all 7 themes and both light/dark modes.

    /**
     * Glass background fill color — semi-transparent surface for blur overlays.
     * Dark: surface at ~62% alpha for deep translucency.
     * Light: white at ~85% alpha for a clean frosted look.
     */
    @ColorInt
    public int getGlassBackgroundColor() {
        return isNightMode ? Color.argb(214, 30, 30, 32) : Color.argb(214, 249, 249, 251);
    }

    /**
     * Elevated glass fill — high-opacity frosted base for floating nav/mini-player surfaces.
     * Prevents content underneath from causing double-exposure collisions while maintaining
     * rich translucent depth and specular highlight.
     */
    @ColorInt
    public int getGlassNavBackgroundColor() {
        // Thick material: translucent enough to hint at content beneath, opaque enough for legibility.
        return isNightMode ? Color.argb(224, 28, 28, 30) : Color.argb(230, 250, 250, 252);
    }

    /**
     * Subtle theme-colored tint wash over glass surfaces.
     * Dark: accent at 9% alpha for atmospheric coloring.
     * Light: accent at 5% alpha (very subtle, avoids washing out text).
     */
    @ColorInt
    public int getGlassTintColor() {
        return Color.TRANSPARENT;
    }

    /**
     * Glass border/stroke color — alpha-adjusted stroke for crisp glass edges.
     * Dark: stroke at ~55% alpha.
     * Light: stroke at ~65% alpha (needs to be more visible on white).
     */
    @ColorInt
    public int getGlassBorderColor() {
        return isNightMode ? Color.argb(46, 255, 255, 255) : Color.argb(26, 0, 0, 0);
    }

    /**
     * Glass highlight color — top-left to bottom-right sheen.
     */
    @ColorInt
    public int getGlassHighlightColor() {
        return isNightMode ? Color.argb(20, 255, 255, 255) : Color.argb(90, 255, 255, 255);
    }

    /**
     * Accent-colored glow for glass-selected/active states.
     * Uses readableAccentColor at controlled alpha so it works in both modes.
     */
    @ColorInt
    public int getGlassAccentGlowColor() {
        int r = Color.red(accentColor);
        int g = Color.green(accentColor);
        int b = Color.blue(accentColor);
        return Color.argb(isNightMode ? 65 : 45, r, g, b);
    }

    /**
     * Creates a premium pill-shaped glass surface drawable for the Bottom Navigation.
     * Structure: high-opacity frosted base fill + TL→BR specular highlight + crisp refraction border.
     * Corner radius: full pill (999dp).
     */
    public GradientDrawable createGlassNavDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable glass = new GradientDrawable();
        glass.setShape(GradientDrawable.RECTANGLE);
        glass.setColor(getGlassNavBackgroundColor());
        glass.setCornerRadius(999 * density);
        glass.setStroke(Math.max(1, Math.round(0.5f * density)), getGlassBorderColor());
        return glass;
    }

    /**
     * Creates a glass drawable for the Mini Player — fully rounded on all corners (22dp pill).
     * High-opacity frosted base fill blocks text bleed-through from background lists.
     */
    public GradientDrawable createGlassMiniPlayerDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable glass = new GradientDrawable();
        glass.setShape(GradientDrawable.RECTANGLE);
        glass.setColor(getGlassNavBackgroundColor());
        glass.setCornerRadius(16f * density);
        glass.setStroke(Math.max(1, Math.round(0.5f * density)), getGlassBorderColor());
        return glass;
    }

    /**
     * Creates a glass pill drawable for action chips, search bars, and segmented control backgrounds.
     */
    public GradientDrawable createGlassPillDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable glass = new GradientDrawable();
        glass.setShape(GradientDrawable.RECTANGLE);
        glass.setColor(isNightMode ? surfaceVariantColor : Color.parseColor("#E3E3E8"));
        glass.setCornerRadius(999 * density);
        glass.setStroke(Math.max(1, Math.round(0.5f * density)), getGlassBorderColor());
        return glass;
    }

    /**
     * Creates a glass card drawable with theme corner radius.
     */
    public GradientDrawable createGlassCardDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable glass = new GradientDrawable();
        glass.setShape(GradientDrawable.RECTANGLE);
        glass.setColor(surfaceColor);
        glass.setCornerRadius(cornerRadiusDp * density);
        return glass;
    }

    /**
     * Creates a shuffle action button background.
     * Glass pill with accent glow — visually distinctive from ordinary buttons.
     */
    public GradientDrawable createShuffleButtonDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setColor(isNightMode ? surfaceElevatedColor : Color.parseColor("#E3E3E8"));
        pill.setCornerRadius(12 * density);
        return pill;
    }

    // ===== Internal Helpers =====

    /** Package-visible color mix for use in LiquidGlassRenderer. */
    static int mixColors(int from, int to, float amount) {
        return mix(from, to, amount);
    }

    private static int mix(int from, int to, float amount) {
        return Color.rgb(Math.round(Color.red(from) * (1 - amount) + Color.red(to) * amount),
                Math.round(Color.green(from) * (1 - amount) + Color.green(to) * amount),
                Math.round(Color.blue(from) * (1 - amount) + Color.blue(to) * amount));
    }
}

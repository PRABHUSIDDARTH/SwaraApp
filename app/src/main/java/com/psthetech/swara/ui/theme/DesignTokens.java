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

        if (isNightMode) {
            // ===== DARK PALETTES =====
            switch (this.colorTheme) {
                case MIDNIGHT:
                    this.backgroundColor = Color.parseColor("#080A0F");
                    this.backgroundSecondaryColor = Color.parseColor("#0F131C");
                    this.surfaceColor = Color.parseColor("#161C29");
                    this.surfaceElevatedColor = Color.parseColor("#1E2638");
                    this.surfaceVariantColor = Color.parseColor("#27324A");
                    this.accentColor = Color.parseColor("#38BDF8");
                    this.primaryColor = Color.parseColor("#38BDF8");
                    this.primaryContainerColor = Color.parseColor("#1E2638");
                    this.secondaryColor = Color.parseColor("#7DD3FC");
                    this.textPrimaryColor = Color.parseColor("#F0F6FC");
                    this.textSecondaryColor = Color.parseColor("#94A3B8");
                    this.textTertiaryColor = Color.parseColor("#64748B");
                    this.iconPrimaryColor = Color.parseColor("#F0F6FC");
                    this.iconSecondaryColor = Color.parseColor("#94A3B8");
                    this.strokeColor = Color.parseColor("#2A3852");
                    this.dividerColor = Color.parseColor("#1E2638");
                    this.selectedColor = Color.parseColor("#2638BDF8");
                    this.overlayColor = Color.parseColor("#CC080A0F");
                    break;

                case LAVENDER:
                    this.backgroundColor = Color.parseColor("#11091F");
                    this.backgroundSecondaryColor = Color.parseColor("#190D2E");
                    this.surfaceColor = Color.parseColor("#22123B");
                    this.surfaceElevatedColor = Color.parseColor("#2E194F");
                    this.surfaceVariantColor = Color.parseColor("#3C2166");
                    this.accentColor = Color.parseColor("#C084FC");
                    this.primaryColor = Color.parseColor("#C084FC");
                    this.primaryContainerColor = Color.parseColor("#2E194F");
                    this.secondaryColor = Color.parseColor("#E9D5FF");
                    this.textPrimaryColor = Color.parseColor("#FAF5FF");
                    this.textSecondaryColor = Color.parseColor("#D8B4FE");
                    this.textTertiaryColor = Color.parseColor("#A855F7");
                    this.iconPrimaryColor = Color.parseColor("#FAF5FF");
                    this.iconSecondaryColor = Color.parseColor("#D8B4FE");
                    this.strokeColor = Color.parseColor("#4C1D95");
                    this.dividerColor = Color.parseColor("#2E194F");
                    this.selectedColor = Color.parseColor("#26C084FC");
                    this.overlayColor = Color.parseColor("#CC11091F");
                    break;

                case CHAMPAGNE:
                    this.backgroundColor = Color.parseColor("#140F0A");
                    this.backgroundSecondaryColor = Color.parseColor("#1F1810");
                    this.surfaceColor = Color.parseColor("#2A2016");
                    this.surfaceElevatedColor = Color.parseColor("#382A1C");
                    this.surfaceVariantColor = Color.parseColor("#473624");
                    this.accentColor = Color.parseColor("#F59E0B");
                    this.primaryColor = Color.parseColor("#FBBF24");
                    this.primaryContainerColor = Color.parseColor("#382A1C");
                    this.secondaryColor = Color.parseColor("#FDE68A");
                    this.textPrimaryColor = Color.parseColor("#FEF3C7");
                    this.textSecondaryColor = Color.parseColor("#FDE68A");
                    this.textTertiaryColor = Color.parseColor("#D97706");
                    this.iconPrimaryColor = Color.parseColor("#FEF3C7");
                    this.iconSecondaryColor = Color.parseColor("#FDE68A");
                    this.strokeColor = Color.parseColor("#5E4426");
                    this.dividerColor = Color.parseColor("#382A1C");
                    this.selectedColor = Color.parseColor("#26F59E0B");
                    this.overlayColor = Color.parseColor("#CC140F0A");
                    break;

                case ROSE:
                    this.backgroundColor = Color.parseColor("#170910");
                    this.backgroundSecondaryColor = Color.parseColor("#240D19");
                    this.surfaceColor = Color.parseColor("#301222");
                    this.surfaceElevatedColor = Color.parseColor("#40182E");
                    this.surfaceVariantColor = Color.parseColor("#521F3B");
                    this.accentColor = Color.parseColor("#FB7185");
                    this.primaryColor = Color.parseColor("#FDA4AF");
                    this.primaryContainerColor = Color.parseColor("#40182E");
                    this.secondaryColor = Color.parseColor("#FECDD3");
                    this.textPrimaryColor = Color.parseColor("#FFF1F2");
                    this.textSecondaryColor = Color.parseColor("#FECDD3");
                    this.textTertiaryColor = Color.parseColor("#F43F5E");
                    this.iconPrimaryColor = Color.parseColor("#FFF1F2");
                    this.iconSecondaryColor = Color.parseColor("#FECDD3");
                    this.strokeColor = Color.parseColor("#6B1D45");
                    this.dividerColor = Color.parseColor("#40182E");
                    this.selectedColor = Color.parseColor("#26FB7185");
                    this.overlayColor = Color.parseColor("#CC170910");
                    break;

                case OCEAN:
                    this.backgroundColor = Color.parseColor("#07121A");
                    this.backgroundSecondaryColor = Color.parseColor("#0C1D29");
                    this.surfaceColor = Color.parseColor("#122738");
                    this.surfaceElevatedColor = Color.parseColor("#19334A");
                    this.surfaceVariantColor = Color.parseColor("#21415E");
                    this.accentColor = Color.parseColor("#14B8A6");
                    this.primaryColor = Color.parseColor("#2DD4BF");
                    this.primaryContainerColor = Color.parseColor("#19334A");
                    this.secondaryColor = Color.parseColor("#99F6E4");
                    this.textPrimaryColor = Color.parseColor("#F0FDFA");
                    this.textSecondaryColor = Color.parseColor("#99F6E4");
                    this.textTertiaryColor = Color.parseColor("#0D9488");
                    this.iconPrimaryColor = Color.parseColor("#F0FDFA");
                    this.iconSecondaryColor = Color.parseColor("#99F6E4");
                    this.strokeColor = Color.parseColor("#275270");
                    this.dividerColor = Color.parseColor("#19334A");
                    this.selectedColor = Color.parseColor("#2614B8A6");
                    this.overlayColor = Color.parseColor("#CC07121A");
                    break;

                case FOREST:
                    this.backgroundColor = Color.parseColor("#08140C");
                    this.backgroundSecondaryColor = Color.parseColor("#0D2114");
                    this.surfaceColor = Color.parseColor("#132E1C");
                    this.surfaceElevatedColor = Color.parseColor("#1C3D26");
                    this.surfaceVariantColor = Color.parseColor("#254E31");
                    this.accentColor = Color.parseColor("#34D399");
                    this.primaryColor = Color.parseColor("#6EE7B7");
                    this.primaryContainerColor = Color.parseColor("#1C3D26");
                    this.secondaryColor = Color.parseColor("#A7F3D0");
                    this.textPrimaryColor = Color.parseColor("#ECFDF5");
                    this.textSecondaryColor = Color.parseColor("#A7F3D0");
                    this.textTertiaryColor = Color.parseColor("#059669");
                    this.iconPrimaryColor = Color.parseColor("#ECFDF5");
                    this.iconSecondaryColor = Color.parseColor("#A7F3D0");
                    this.strokeColor = Color.parseColor("#2B633C");
                    this.dividerColor = Color.parseColor("#1C3D26");
                    this.selectedColor = Color.parseColor("#2634D399");
                    this.overlayColor = Color.parseColor("#CC08140C");
                    break;

                case SWARA:
                default:
                    this.backgroundColor = Color.parseColor("#0D0819");
                    this.backgroundSecondaryColor = Color.parseColor("#140D24");
                    this.surfaceColor = Color.parseColor("#1A0E2E");
                    this.surfaceElevatedColor = Color.parseColor("#231645");
                    this.surfaceVariantColor = Color.parseColor("#2E1A5A");
                    this.accentColor = Color.parseColor("#C9A84C"); // Swara Gold
                    this.primaryColor = Color.parseColor("#3D1F7A");
                    this.primaryContainerColor = Color.parseColor("#231645");
                    this.secondaryColor = Color.parseColor("#9B7EC8");
                    this.textPrimaryColor = Color.parseColor("#F0E6C8");
                    this.textSecondaryColor = Color.parseColor("#9B7EC8");
                    this.textTertiaryColor = Color.parseColor("#7A6A9A");
                    this.iconPrimaryColor = Color.parseColor("#F0E6C8");
                    this.iconSecondaryColor = Color.parseColor("#9B7EC8");
                    this.strokeColor = Color.parseColor("#4D3080");
                    this.dividerColor = Color.parseColor("#231645");
                    this.selectedColor = Color.parseColor("#26C9A84C");
                    this.overlayColor = Color.parseColor("#CC0D0819");
                    break;
            }
        } else {
            // ===== LIGHT PALETTES =====
            switch (this.colorTheme) {
                case MIDNIGHT:
                    this.backgroundColor = Color.parseColor("#F4F7FA");
                    this.backgroundSecondaryColor = Color.parseColor("#E8EEF5");
                    this.surfaceColor = Color.parseColor("#FFFFFF");
                    this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
                    this.surfaceVariantColor = Color.parseColor("#E8EEF5");
                    this.accentColor = Color.parseColor("#0284C7");
                    this.primaryColor = Color.parseColor("#0F172A");
                    this.primaryContainerColor = Color.parseColor("#E8EEF5");
                    this.secondaryColor = Color.parseColor("#334155");
                    this.textPrimaryColor = Color.parseColor("#0F172A"); // Strong dark
                    this.textSecondaryColor = Color.parseColor("#334155");
                    this.textTertiaryColor = Color.parseColor("#64748B");
                    this.iconPrimaryColor = Color.parseColor("#0F172A");
                    this.iconSecondaryColor = Color.parseColor("#334155");
                    this.strokeColor = Color.parseColor("#CBD5E1");
                    this.dividerColor = Color.parseColor("#E2E8F0");
                    this.selectedColor = Color.parseColor("#260284C7");
                    this.overlayColor = Color.parseColor("#CCF4F7FA");
                    break;

                case LAVENDER:
                    this.backgroundColor = Color.parseColor("#FAF6FF");
                    this.backgroundSecondaryColor = Color.parseColor("#F3E8FF");
                    this.surfaceColor = Color.parseColor("#FFFFFF");
                    this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
                    this.surfaceVariantColor = Color.parseColor("#F3E8FF");
                    this.accentColor = Color.parseColor("#7E22CE");
                    this.primaryColor = Color.parseColor("#3B0764");
                    this.primaryContainerColor = Color.parseColor("#F3E8FF");
                    this.secondaryColor = Color.parseColor("#6B21A8");
                    this.textPrimaryColor = Color.parseColor("#2E0A4E");
                    this.textSecondaryColor = Color.parseColor("#6B21A8");
                    this.textTertiaryColor = Color.parseColor("#8B5CF6");
                    this.iconPrimaryColor = Color.parseColor("#2E0A4E");
                    this.iconSecondaryColor = Color.parseColor("#6B21A8");
                    this.strokeColor = Color.parseColor("#E9D5FF");
                    this.dividerColor = Color.parseColor("#F3E8FF");
                    this.selectedColor = Color.parseColor("#267E22CE");
                    this.overlayColor = Color.parseColor("#CCFAF6FF");
                    break;

                case CHAMPAGNE:
                    this.backgroundColor = Color.parseColor("#FAF7F2");
                    this.backgroundSecondaryColor = Color.parseColor("#F4EFE6");
                    this.surfaceColor = Color.parseColor("#FFFFFF");
                    this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
                    this.surfaceVariantColor = Color.parseColor("#F4EFE6");
                    this.accentColor = Color.parseColor("#B45309"); // Rich dark amber (contrast > 4.5:1)
                    this.primaryColor = Color.parseColor("#451A03");
                    this.primaryContainerColor = Color.parseColor("#F4EFE6");
                    this.secondaryColor = Color.parseColor("#78350F");
                    this.textPrimaryColor = Color.parseColor("#361A05");
                    this.textSecondaryColor = Color.parseColor("#78350F");
                    this.textTertiaryColor = Color.parseColor("#92400E");
                    this.iconPrimaryColor = Color.parseColor("#361A05");
                    this.iconSecondaryColor = Color.parseColor("#78350F");
                    this.strokeColor = Color.parseColor("#E5D9C8");
                    this.dividerColor = Color.parseColor("#EFE7DC");
                    this.selectedColor = Color.parseColor("#26B45309");
                    this.overlayColor = Color.parseColor("#CCFAF7F2");
                    break;

                case ROSE:
                    this.backgroundColor = Color.parseColor("#FFF8FA");
                    this.backgroundSecondaryColor = Color.parseColor("#FCECEF");
                    this.surfaceColor = Color.parseColor("#FFFFFF");
                    this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
                    this.surfaceVariantColor = Color.parseColor("#FCE4EC"); // Soft gentle rose tint
                    this.accentColor = Color.parseColor("#C2185B"); // Deep rose red (contrast 4.7:1 on white)
                    this.primaryColor = Color.parseColor("#4C0519");
                    this.primaryContainerColor = Color.parseColor("#FCE4EC");
                    this.secondaryColor = Color.parseColor("#6B2135");
                    this.textPrimaryColor = Color.parseColor("#2E1018"); // Deep dark rose/black (contrast > 15:1)
                    this.textSecondaryColor = Color.parseColor("#6B2135"); // Rich dark rose (contrast > 6:1)
                    this.textTertiaryColor = Color.parseColor("#9B3D55"); // (contrast > 4.5:1)
                    this.iconPrimaryColor = Color.parseColor("#2E1018");
                    this.iconSecondaryColor = Color.parseColor("#6B2135");
                    this.strokeColor = Color.parseColor("#F8BBD0");
                    this.dividerColor = Color.parseColor("#FCE4EC");
                    this.selectedColor = Color.parseColor("#26C2185B");
                    this.overlayColor = Color.parseColor("#CCFFF8FA");
                    break;

                case OCEAN:
                    this.backgroundColor = Color.parseColor("#F2F9FA");
                    this.backgroundSecondaryColor = Color.parseColor("#E4F3F5");
                    this.surfaceColor = Color.parseColor("#FFFFFF");
                    this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
                    this.surfaceVariantColor = Color.parseColor("#E0F2F1");
                    this.accentColor = Color.parseColor("#0D9488"); // Teal 600 (contrast > 4.5:1)
                    this.primaryColor = Color.parseColor("#042F2E");
                    this.primaryContainerColor = Color.parseColor("#E0F2F1");
                    this.secondaryColor = Color.parseColor("#115E59");
                    this.textPrimaryColor = Color.parseColor("#042F2E");
                    this.textSecondaryColor = Color.parseColor("#115E59");
                    this.textTertiaryColor = Color.parseColor("#0F766E");
                    this.iconPrimaryColor = Color.parseColor("#042F2E");
                    this.iconSecondaryColor = Color.parseColor("#115E59");
                    this.strokeColor = Color.parseColor("#B2DFDB");
                    this.dividerColor = Color.parseColor("#E0F2F1");
                    this.selectedColor = Color.parseColor("#260D9488");
                    this.overlayColor = Color.parseColor("#CCF2F9FA");
                    break;

                case FOREST:
                    this.backgroundColor = Color.parseColor("#F3F9F5");
                    this.backgroundSecondaryColor = Color.parseColor("#E3F3E8");
                    this.surfaceColor = Color.parseColor("#FFFFFF");
                    this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
                    this.surfaceVariantColor = Color.parseColor("#DCF0E3");
                    this.accentColor = Color.parseColor("#059669"); // Emerald 600 (contrast > 4.5:1)
                    this.primaryColor = Color.parseColor("#022C22");
                    this.primaryContainerColor = Color.parseColor("#DCF0E3");
                    this.secondaryColor = Color.parseColor("#065F46");
                    this.textPrimaryColor = Color.parseColor("#022C22");
                    this.textSecondaryColor = Color.parseColor("#065F46");
                    this.textTertiaryColor = Color.parseColor("#047857");
                    this.iconPrimaryColor = Color.parseColor("#022C22");
                    this.iconSecondaryColor = Color.parseColor("#065F46");
                    this.strokeColor = Color.parseColor("#A7D7BC");
                    this.dividerColor = Color.parseColor("#DCF0E3");
                    this.selectedColor = Color.parseColor("#26059669");
                    this.overlayColor = Color.parseColor("#CCF3F9F5");
                    break;

                case SWARA:
                default:
                    this.backgroundColor = Color.parseColor("#F6F2FC");
                    this.backgroundSecondaryColor = Color.parseColor("#EDE6F7");
                    this.surfaceColor = Color.parseColor("#FFFFFF");
                    this.surfaceElevatedColor = Color.parseColor("#FFFFFF");
                    this.surfaceVariantColor = Color.parseColor("#EDE6F7");
                    this.accentColor = Color.parseColor("#946E14"); // Darker Gold/Bronze for Light Mode contrast (>4.5:1)
                    this.primaryColor = Color.parseColor("#3D1F7A");
                    this.primaryContainerColor = Color.parseColor("#EDE6F7");
                    this.secondaryColor = Color.parseColor("#5B4380");
                    this.textPrimaryColor = Color.parseColor("#1F0F3D"); // Deep Purple (Crisp dark text!)
                    this.textSecondaryColor = Color.parseColor("#5B4380");
                    this.textTertiaryColor = Color.parseColor("#7E69A3");
                    this.iconPrimaryColor = Color.parseColor("#1F0F3D");
                    this.iconSecondaryColor = Color.parseColor("#5B4380");
                    this.strokeColor = Color.parseColor("#DCCDF2");
                    this.dividerColor = Color.parseColor("#EBE0F7");
                    this.selectedColor = Color.parseColor("#26946E14");
                    this.overlayColor = Color.parseColor("#CCF6F2FC");
                    break;
            }
        }
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

    /** Accent tint for a filled favorite icon. Alias of accentColor. */
    public int getFavoriteActiveColor() {
        return accentColor;
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
        if (isNightMode) {
            return mix(surfaceElevatedColor, accentColor, 0.14f);
        } else {
            return mix(surfaceVariantColor, accentColor, 0.22f);
        }
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
        if (isNightMode) {
            int r = android.graphics.Color.red(accentColor);
            int g = android.graphics.Color.green(accentColor);
            int b = android.graphics.Color.blue(accentColor);
            return android.graphics.Color.argb(90, r, g, b); // ~35% alpha
        } else {
            return mix(accentColor, primaryColor, 0.20f);
        }
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
     * on light surfaces, so we use textPrimaryColor as the safe fallback.
     * In dark mode the accent is typically readable as-is.
     */
    public int getReadableAccentColor() {
        if (isNightMode) {
            return accentColor;
        }
        // For light mode: check luminance — if accent is very light use primary text instead
        double lum = (0.299 * android.graphics.Color.red(accentColor)
                + 0.587 * android.graphics.Color.green(accentColor)
                + 0.114 * android.graphics.Color.blue(accentColor)) / 255.0;
        // If luminance > 0.7 (very bright), fall back to primary text
        return lum > 0.70 ? textPrimaryColor : accentColor;
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
        int base = elevated ? surfaceVariantColor : surfaceColor;
        GradientDrawable glass;

        if (isNightMode) {
            glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{mix(base, Color.WHITE, 0.18f), mix(base, Color.WHITE, 0.05f),
                            mix(base, secondaryColor, 0.08f)});
            glass.setAlpha(238);
            glass.setStroke(Math.max(1, Math.round(density)), strokeColor);
        } else {
            // Light mode: clean white highlight top-left, soft surface tone, crisp subtle stroke
            glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.WHITE, base, mix(base, surfaceVariantColor, 0.40f)});
            glass.setAlpha(250);
            glass.setStroke(Math.max(1, Math.round(density)), strokeColor);
        }

        glass.setCornerRadius(cornerRadiusDp * density);
        return glass;
    }

    /** An opaque backdrop keeps the glass and text contrast stable in every palette. */
    public Drawable createAmbientDrawable() {
        if (style != MorphismStyle.LIQUID_GLASS) {
            return new android.graphics.drawable.ColorDrawable(backgroundColor);
        }
        if (isNightMode) {
            return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{mix(backgroundColor, primaryColor, 0.13f),
                            backgroundSecondaryColor, backgroundColor});
        } else {
            // Light mode: soft soothing ambient gradient between background tones without darkening
            return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{backgroundColor, backgroundSecondaryColor, backgroundColor});
        }
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
        if (isNightMode) {
            int r = Color.red(surfaceColor);
            int g = Color.green(surfaceColor);
            int b = Color.blue(surfaceColor);
            return Color.argb(230, r, g, b); // ~90% alpha — frosted base
        } else {
            return Color.argb(242, 255, 255, 255); // ~95% white
        }
    }

    /**
     * Elevated glass fill — high-opacity frosted base for floating nav/mini-player surfaces.
     * Prevents content underneath from causing double-exposure collisions while maintaining
     * rich translucent depth and specular highlight.
     */
    @ColorInt
    public int getGlassNavBackgroundColor() {
        if (isNightMode) {
            int r = Color.red(surfaceElevatedColor);
            int g = Color.green(surfaceElevatedColor);
            int b = Color.blue(surfaceElevatedColor);
            return Color.argb(240, r, g, b); // ~94% alpha
        } else {
            return Color.argb(248, 255, 255, 255); // ~97% white
        }
    }

    /**
     * Subtle theme-colored tint wash over glass surfaces.
     * Dark: accent at 9% alpha for atmospheric coloring.
     * Light: accent at 5% alpha (very subtle, avoids washing out text).
     */
    @ColorInt
    public int getGlassTintColor() {
        int r = Color.red(accentColor);
        int g = Color.green(accentColor);
        int b = Color.blue(accentColor);
        return Color.argb(isNightMode ? 23 : 13, r, g, b);
    }

    /**
     * Glass border/stroke color — alpha-adjusted stroke for crisp glass edges.
     * Dark: stroke at ~55% alpha.
     * Light: stroke at ~65% alpha (needs to be more visible on white).
     */
    @ColorInt
    public int getGlassBorderColor() {
        int r = Color.red(strokeColor);
        int g = Color.green(strokeColor);
        int b = Color.blue(strokeColor);
        return Color.argb(isNightMode ? 140 : 166, r, g, b);
    }

    /**
     * Glass highlight color — top-left to bottom-right sheen.
     */
    @ColorInt
    public int getGlassHighlightColor() {
        return Color.argb(isNightMode ? 40 : 128, 255, 255, 255);
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
        int baseColor = isNightMode ? surfaceElevatedColor : surfaceColor;
        int startColor = isNightMode
                ? Color.argb(246,
                        Math.min(255, Color.red(baseColor) + 26),
                        Math.min(255, Color.green(baseColor) + 26),
                        Math.min(255, Color.blue(baseColor) + 36))
                : Color.argb(248, 255, 255, 255);
        int endColor = isNightMode
                ? Color.argb(238, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                : Color.argb(240, 245, 245, 248);

        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        glass.setCornerRadius(999 * density); // Full pill
        int strokeColor = isNightMode
                ? Color.argb(70, 255, 255, 255)
                : Color.argb(45, 0, 0, 0);
        glass.setStroke(Math.max(1, Math.round(density)), strokeColor);
        return glass;
    }

    /**
     * Creates a glass drawable for the Mini Player — fully rounded on all corners (22dp pill).
     * High-opacity frosted base fill blocks text bleed-through from background lists.
     */
    public GradientDrawable createGlassMiniPlayerDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        float cornerPx = 22f * density;
        int baseColor = isNightMode ? surfaceElevatedColor : surfaceColor;
        int startColor = isNightMode
                ? Color.argb(246,
                        Math.min(255, Color.red(baseColor) + 26),
                        Math.min(255, Color.green(baseColor) + 26),
                        Math.min(255, Color.blue(baseColor) + 34))
                : Color.argb(250, 255, 255, 255);
        int endColor = isNightMode
                ? Color.argb(240, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                : Color.argb(242, 245, 245, 248);

        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        glass.setCornerRadius(cornerPx);
        int strokeColor = isNightMode
                ? Color.argb(65, 255, 255, 255)
                : Color.argb(40, 0, 0, 0);
        glass.setStroke(Math.max(1, Math.round(density)), strokeColor);
        return glass;
    }

    /**
     * Creates a glass pill drawable for action chips, search bars, and segmented control backgrounds.
     */
    public GradientDrawable createGlassPillDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int baseColor = isNightMode ? surfaceVariantColor : surfaceColor;
        int startColor = isNightMode
                ? Color.argb(235,
                        Math.min(255, Color.red(baseColor) + 20),
                        Math.min(255, Color.green(baseColor) + 20),
                        Math.min(255, Color.blue(baseColor) + 28))
                : Color.argb(240, 255, 255, 255);
        int endColor = isNightMode
                ? Color.argb(225, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                : Color.argb(230, 240, 240, 244);

        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        glass.setCornerRadius(999 * density); // Full pill
        int strokeColor = isNightMode
                ? Color.argb(55, 255, 255, 255)
                : Color.argb(35, 0, 0, 0);
        glass.setStroke(Math.max(1, Math.round(density)), strokeColor);
        return glass;
    }

    /**
     * Creates a glass card drawable with theme corner radius.
     */
    public GradientDrawable createGlassCardDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int baseColor = isNightMode ? surfaceColor : surfaceColor;
        int startColor = isNightMode
                ? Color.argb(230,
                        Math.min(255, Color.red(baseColor) + 18),
                        Math.min(255, Color.green(baseColor) + 18),
                        Math.min(255, Color.blue(baseColor) + 24))
                : Color.argb(240, 255, 255, 255);
        int endColor = isNightMode
                ? Color.argb(220, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                : Color.argb(230, 245, 245, 248);

        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        glass.setCornerRadius(cornerRadiusDp * density);
        int strokeColor = isNightMode
                ? Color.argb(50, 255, 255, 255)
                : Color.argb(30, 0, 0, 0);
        glass.setStroke(Math.max(1, Math.round(density)), strokeColor);
        return glass;
    }

    /**
     * Creates a shuffle action button background.
     * Glass pill with accent glow — visually distinctive from ordinary buttons.
     */
    public GradientDrawable createShuffleButtonDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int startColor = getGlassHighlightColor();
        int endColor = getGlassAccentGlowColor();
        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        glass.setCornerRadius(999 * density); // Full pill
        int strokeColor = getReadableAccentColor();
        int sr = Color.red(strokeColor), sg = Color.green(strokeColor), sb = Color.blue(strokeColor);
        glass.setStroke(Math.max(1, Math.round(density)), Color.argb(120, sr, sg, sb));
        return glass;
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

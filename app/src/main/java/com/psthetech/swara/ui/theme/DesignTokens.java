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
                    this.surfaceColor = Color.parseColor("#DCE4F0");
                    this.surfaceElevatedColor = Color.parseColor("#CFD9E8");
                    this.surfaceVariantColor = Color.parseColor("#C1CFE0");
                    this.accentColor = Color.parseColor("#0284C7");
                    this.primaryColor = Color.parseColor("#0F172A");
                    this.primaryContainerColor = Color.parseColor("#CFD9E8");
                    this.secondaryColor = Color.parseColor("#334155");
                    this.textPrimaryColor = Color.parseColor("#0F172A"); // Strong dark
                    this.textSecondaryColor = Color.parseColor("#334155");
                    this.textTertiaryColor = Color.parseColor("#64748B");
                    this.iconPrimaryColor = Color.parseColor("#0F172A");
                    this.iconSecondaryColor = Color.parseColor("#334155");
                    this.strokeColor = Color.parseColor("#B0C2DE");
                    this.dividerColor = Color.parseColor("#DCE4F0");
                    this.selectedColor = Color.parseColor("#260284C7");
                    this.overlayColor = Color.parseColor("#CCF4F7FA");
                    break;

                case LAVENDER:
                    this.backgroundColor = Color.parseColor("#FBF7FE");
                    this.backgroundSecondaryColor = Color.parseColor("#F3E8FF");
                    this.surfaceColor = Color.parseColor("#E9D5FF");
                    this.surfaceElevatedColor = Color.parseColor("#DDD6FE");
                    this.surfaceVariantColor = Color.parseColor("#C4B5FD");
                    this.accentColor = Color.parseColor("#7E22CE");
                    this.primaryColor = Color.parseColor("#3B0764");
                    this.primaryContainerColor = Color.parseColor("#DDD6FE");
                    this.secondaryColor = Color.parseColor("#6B21A8");
                    this.textPrimaryColor = Color.parseColor("#2E0A4E");
                    this.textSecondaryColor = Color.parseColor("#6B21A8");
                    this.textTertiaryColor = Color.parseColor("#9333EA");
                    this.iconPrimaryColor = Color.parseColor("#2E0A4E");
                    this.iconSecondaryColor = Color.parseColor("#6B21A8");
                    this.strokeColor = Color.parseColor("#C4B5FD");
                    this.dividerColor = Color.parseColor("#E9D5FF");
                    this.selectedColor = Color.parseColor("#267E22CE");
                    this.overlayColor = Color.parseColor("#CCFBF7FE");
                    break;

                case CHAMPAGNE:
                    this.backgroundColor = Color.parseColor("#FAF6F0");
                    this.backgroundSecondaryColor = Color.parseColor("#F3EBE0");
                    this.surfaceColor = Color.parseColor("#EAE0CF");
                    this.surfaceElevatedColor = Color.parseColor("#DECFC0");
                    this.surfaceVariantColor = Color.parseColor("#D2BFB0");
                    this.accentColor = Color.parseColor("#B45309");
                    this.primaryColor = Color.parseColor("#451A03");
                    this.primaryContainerColor = Color.parseColor("#DECFC0");
                    this.secondaryColor = Color.parseColor("#78350F");
                    this.textPrimaryColor = Color.parseColor("#361504");
                    this.textSecondaryColor = Color.parseColor("#78350F");
                    this.textTertiaryColor = Color.parseColor("#92400E");
                    this.iconPrimaryColor = Color.parseColor("#361504");
                    this.iconSecondaryColor = Color.parseColor("#78350F");
                    this.strokeColor = Color.parseColor("#CBB6A4");
                    this.dividerColor = Color.parseColor("#EAE0CF");
                    this.selectedColor = Color.parseColor("#26B45309");
                    this.overlayColor = Color.parseColor("#CCFAF6F0");
                    break;

                case ROSE:
                    this.backgroundColor = Color.parseColor("#FDF4F6");
                    this.backgroundSecondaryColor = Color.parseColor("#FCE7F0");
                    this.surfaceColor = Color.parseColor("#FBCFE8");
                    this.surfaceElevatedColor = Color.parseColor("#F472B6");
                    this.surfaceVariantColor = Color.parseColor("#E11D48");
                    this.accentColor = Color.parseColor("#E11D48");
                    this.primaryColor = Color.parseColor("#4C0519");
                    this.primaryContainerColor = Color.parseColor("#FBCFE8");
                    this.secondaryColor = Color.parseColor("#881337");
                    this.textPrimaryColor = Color.parseColor("#3B0313");
                    this.textSecondaryColor = Color.parseColor("#881337");
                    this.textTertiaryColor = Color.parseColor("#BE123C");
                    this.iconPrimaryColor = Color.parseColor("#3B0313");
                    this.iconSecondaryColor = Color.parseColor("#881337");
                    this.strokeColor = Color.parseColor("#F472B6");
                    this.dividerColor = Color.parseColor("#FCE7F0");
                    this.selectedColor = Color.parseColor("#26E11D48");
                    this.overlayColor = Color.parseColor("#CCFDF4F6");
                    break;

                case OCEAN:
                    this.backgroundColor = Color.parseColor("#F2F9FA");
                    this.backgroundSecondaryColor = Color.parseColor("#E3F2F5");
                    this.surfaceColor = Color.parseColor("#D2E7ED");
                    this.surfaceElevatedColor = Color.parseColor("#C0DCE3");
                    this.surfaceVariantColor = Color.parseColor("#ADCFD9");
                    this.accentColor = Color.parseColor("#0D9488");
                    this.primaryColor = Color.parseColor("#042F2E");
                    this.primaryContainerColor = Color.parseColor("#C0DCE3");
                    this.secondaryColor = Color.parseColor("#115E59");
                    this.textPrimaryColor = Color.parseColor("#042F2E");
                    this.textSecondaryColor = Color.parseColor("#115E59");
                    this.textTertiaryColor = Color.parseColor("#0F766E");
                    this.iconPrimaryColor = Color.parseColor("#042F2E");
                    this.iconSecondaryColor = Color.parseColor("#115E59");
                    this.strokeColor = Color.parseColor("#8CC0CE");
                    this.dividerColor = Color.parseColor("#D2E7ED");
                    this.selectedColor = Color.parseColor("#260D9488");
                    this.overlayColor = Color.parseColor("#CCF2F9FA");
                    break;

                case FOREST:
                    this.backgroundColor = Color.parseColor("#F3F9F5");
                    this.backgroundSecondaryColor = Color.parseColor("#E4F2E9");
                    this.surfaceColor = Color.parseColor("#D2E7DC");
                    this.surfaceElevatedColor = Color.parseColor("#BEDCCD");
                    this.surfaceVariantColor = Color.parseColor("#A8CEBD");
                    this.accentColor = Color.parseColor("#059669");
                    this.primaryColor = Color.parseColor("#022C22");
                    this.primaryContainerColor = Color.parseColor("#BEDCCD");
                    this.secondaryColor = Color.parseColor("#065F46");
                    this.textPrimaryColor = Color.parseColor("#022C22");
                    this.textSecondaryColor = Color.parseColor("#065F46");
                    this.textTertiaryColor = Color.parseColor("#047857");
                    this.iconPrimaryColor = Color.parseColor("#022C22");
                    this.iconSecondaryColor = Color.parseColor("#065F46");
                    this.strokeColor = Color.parseColor("#8CBFA9");
                    this.dividerColor = Color.parseColor("#D2E7DC");
                    this.selectedColor = Color.parseColor("#26059669");
                    this.overlayColor = Color.parseColor("#CCF3F9F5");
                    break;

                case SWARA:
                default:
                    this.backgroundColor = Color.parseColor("#F6F2FC");
                    this.backgroundSecondaryColor = Color.parseColor("#EDE6F7");
                    this.surfaceColor = Color.parseColor("#E5D9F4");
                    this.surfaceElevatedColor = Color.parseColor("#D9C9EE");
                    this.surfaceVariantColor = Color.parseColor("#CDBAE8");
                    this.accentColor = Color.parseColor("#A68020"); // Darker Gold for Light Mode contrast
                    this.primaryColor = Color.parseColor("#3D1F7A");
                    this.primaryContainerColor = Color.parseColor("#D9C9EE");
                    this.secondaryColor = Color.parseColor("#5B4380");
                    this.textPrimaryColor = Color.parseColor("#1F0F3D"); // Deep Purple (Crisp dark text!)
                    this.textSecondaryColor = Color.parseColor("#5B4380");
                    this.textTertiaryColor = Color.parseColor("#7E69A3");
                    this.iconPrimaryColor = Color.parseColor("#1F0F3D");
                    this.iconSecondaryColor = Color.parseColor("#5B4380");
                    this.strokeColor = Color.parseColor("#C6B0EA");
                    this.dividerColor = Color.parseColor("#E5D9F4");
                    this.selectedColor = Color.parseColor("#26A68020");
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
}

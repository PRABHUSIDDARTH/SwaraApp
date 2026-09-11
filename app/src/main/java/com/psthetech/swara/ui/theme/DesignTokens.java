package com.psthetech.swara.ui.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.MorphismStyle;

/**
 * Dynamic Design Tokens for Swara V2.1 Morphism System.
 * Encapsulates color, corner radii, elevation, stroke width, and shape drawables for all 6 styles.
 */
public class DesignTokens {

    private final MorphismStyle style;
    private final boolean isNightMode;

    @ColorInt private final int backgroundColor;
    @ColorInt private final int surfaceColor;
    @ColorInt private final int surfaceVariantColor;
    @ColorInt private final int strokeColor;
    @ColorInt private final int accentColor;
    @ColorInt private final int primaryTextColor;
    @ColorInt private final int secondaryTextColor;
    @ColorInt private final int dimTextColor;

    private final int cornerRadiusDp;
    private final int elevationDp;
    private final int strokeWidthDp;

    public DesignTokens(Context context, MorphismStyle style, boolean isNightMode) {
        this.style = style;
        this.isNightMode = isNightMode;

        // Base Swara colors
        int gold = ContextCompat.getColor(context, R.color.swara_gold);
        int cream = ContextCompat.getColor(context, R.color.swara_cream);
        int lavender = ContextCompat.getColor(context, R.color.swara_lavender);
        int dim = ContextCompat.getColor(context, R.color.swara_text_dim);
        int glassStroke = ContextCompat.getColor(context, R.color.swara_glass_stroke);

        this.accentColor = gold;

        if (isNightMode) {
            this.primaryTextColor = cream;
            this.secondaryTextColor = lavender;
            this.dimTextColor = dim;

            switch (style) {
                case CLAY:
                    this.backgroundColor = Color.parseColor("#0F0A1C");
                    this.surfaceColor = Color.parseColor("#251740");
                    this.surfaceVariantColor = Color.parseColor("#2E1C50");
                    this.strokeColor = Color.parseColor("#3B2466");
                    this.cornerRadiusDp = 24;
                    this.elevationDp = 6;
                    this.strokeWidthDp = 1;
                    break;

                case NEO:
                    this.backgroundColor = Color.parseColor("#0C0718");
                    this.surfaceColor = Color.parseColor("#1C0F35");
                    this.surfaceVariantColor = Color.parseColor("#271549");
                    this.strokeColor = Color.parseColor("#442678");
                    this.cornerRadiusDp = 16;
                    this.elevationDp = 4;
                    this.strokeWidthDp = 1;
                    break;

                case MONOLISM:
                    this.backgroundColor = Color.parseColor("#0A0514");
                    this.surfaceColor = Color.parseColor("#150B28");
                    this.surfaceVariantColor = Color.parseColor("#1E1038");
                    this.strokeColor = Color.parseColor("#2F1854");
                    this.cornerRadiusDp = 12;
                    this.elevationDp = 1;
                    this.strokeWidthDp = 1;
                    break;

                case BRUTALISM:
                    this.backgroundColor = Color.parseColor("#080410");
                    this.surfaceColor = Color.parseColor("#140A26");
                    this.surfaceVariantColor = Color.parseColor("#1F103A");
                    this.strokeColor = gold; // High contrast gold border
                    this.cornerRadiusDp = 2; // Hard box edges
                    this.elevationDp = 0;
                    this.strokeWidthDp = 3;  // Bold stroke
                    break;

                case MINIMALISM:
                    this.backgroundColor = Color.parseColor("#0B0616");
                    this.surfaceColor = Color.parseColor("#160C2A");
                    this.surfaceVariantColor = Color.parseColor("#20123C");
                    this.strokeColor = Color.parseColor("#2A174E");
                    this.cornerRadiusDp = 8;
                    this.elevationDp = 0;
                    this.strokeWidthDp = 1;
                    break;

                case LIQUID_GLASS:
                default:
                    this.backgroundColor = Color.parseColor("#0D0819");
                    this.surfaceColor = Color.parseColor("#1A0E2E");
                    this.surfaceVariantColor = Color.parseColor("#231645");
                    this.strokeColor = glassStroke;
                    this.cornerRadiusDp = 20;
                    this.elevationDp = 2;
                    this.strokeWidthDp = 1;
                    break;
            }
        } else {
            // Light Theme Tokens
            this.primaryTextColor = Color.parseColor("#1A0E2E");
            this.secondaryTextColor = Color.parseColor("#5A3E8A");
            this.dimTextColor = Color.parseColor("#7E69A3");

            switch (style) {
                case CLAY:
                    this.backgroundColor = Color.parseColor("#F4EFFC");
                    this.surfaceColor = Color.parseColor("#E5D7F8");
                    this.surfaceVariantColor = Color.parseColor("#D7C2F3");
                    this.strokeColor = Color.parseColor("#C3A9EA");
                    this.cornerRadiusDp = 24;
                    this.elevationDp = 6;
                    this.strokeWidthDp = 1;
                    break;

                case NEO:
                    this.backgroundColor = Color.parseColor("#F7F3FD");
                    this.surfaceColor = Color.parseColor("#EBE2F9");
                    this.surfaceVariantColor = Color.parseColor("#DECFF5");
                    this.strokeColor = Color.parseColor("#CAAFEF");
                    this.cornerRadiusDp = 16;
                    this.elevationDp = 4;
                    this.strokeWidthDp = 1;
                    break;

                case MONOLISM:
                    this.backgroundColor = Color.parseColor("#FAF8FF");
                    this.surfaceColor = Color.parseColor("#F0EAFA");
                    this.surfaceVariantColor = Color.parseColor("#E4DAF5");
                    this.strokeColor = Color.parseColor("#D0C0EC");
                    this.cornerRadiusDp = 12;
                    this.elevationDp = 1;
                    this.strokeWidthDp = 1;
                    break;

                case BRUTALISM:
                    this.backgroundColor = Color.parseColor("#FFFFFF");
                    this.surfaceColor = Color.parseColor("#F3ECFC");
                    this.surfaceVariantColor = Color.parseColor("#E6D8F8");
                    this.strokeColor = Color.parseColor("#1A0E2E"); // Thick dark border
                    this.cornerRadiusDp = 2;
                    this.elevationDp = 0;
                    this.strokeWidthDp = 3;
                    break;

                case MINIMALISM:
                    this.backgroundColor = Color.parseColor("#FCFAFF");
                    this.surfaceColor = Color.parseColor("#F2ECFA");
                    this.surfaceVariantColor = Color.parseColor("#E7DCF6");
                    this.strokeColor = Color.parseColor("#D5C4F0");
                    this.cornerRadiusDp = 8;
                    this.elevationDp = 0;
                    this.strokeWidthDp = 1;
                    break;

                case LIQUID_GLASS:
                default:
                    this.backgroundColor = Color.parseColor("#F7F4FC");
                    this.surfaceColor = Color.parseColor("#EDE5F8");
                    this.surfaceVariantColor = Color.parseColor("#E0D3F5");
                    this.strokeColor = Color.parseColor("#C6B0EA");
                    this.cornerRadiusDp = 20;
                    this.elevationDp = 2;
                    this.strokeWidthDp = 1;
                    break;
            }
        }
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

    public int getSurfaceColor() {
        return surfaceColor;
    }

    public int getSurfaceVariantColor() {
        return surfaceVariantColor;
    }

    public int getStrokeColor() {
        return strokeColor;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public int getPrimaryTextColor() {
        return primaryTextColor;
    }

    public int getSecondaryTextColor() {
        return secondaryTextColor;
    }

    public int getDimTextColor() {
        return dimTextColor;
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
     * Create a custom card background drawable adhering to the current design tokens.
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
     * Create a surface variant container drawable (e.g. search box, mini player, dialog background).
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
     * Create a primary button background drawable.
     */
    public Drawable createButtonDrawable(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(accentColor);

        float density = context.getResources().getDisplayMetrics().density;
        // Clay/LiquidGlass get pill shape, Brutalism gets sharp edge
        float radiusPx = style == MorphismStyle.BRUTALISM ? 2 * density : 999 * density;
        drawable.setCornerRadius(radiusPx);

        if (style == MorphismStyle.BRUTALISM) {
            drawable.setStroke(Math.round(2 * density), Color.BLACK);
        }
        return drawable;
    }
}

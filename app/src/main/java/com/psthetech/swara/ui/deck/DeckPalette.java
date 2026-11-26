package com.psthetech.swara.ui.deck;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.psthetech.swara.ui.theme.DesignTokens;

/**
 * Every Deck colour, derived from {@link DesignTokens} so the skin works in all colour themes,
 * light and dark. Shades are mixes or alphas of token colours; there are no fixed colours.
 */
public final class DeckPalette {

    public final int background;
    public final int body;
    public final int bodyEdge;
    public final int cap;
    public final int capSide;
    public final int capEdge;
    public final int metal;
    public final int well;
    public final int tape;
    public final int tapeLine;
    public final int shadow;
    public final int text;
    public final int textSecondary;
    public final int textTertiary;
    public final int icon;
    public final int accent;
    public final int accentText;
    public final int onAccent;
    public final int accentSide;
    public final int dotOff;

    public DeckPalette(@NonNull DesignTokens t) {
        boolean night = t.isNightMode();
        background = t.getBackgroundColor();
        body = night ? t.getSurfaceColor() : t.getSurfaceVariantColor();
        bodyEdge = t.getStrokeColor();
        cap = night ? t.getSurfaceElevatedColor() : t.getSurfaceColor();
        // The side of a key and every shadow is a darker mix: toward the page background at
        // night, toward the text colour (near black) by day.
        int dark = night ? t.getBackgroundColor() : t.getTextPrimaryColor();
        capSide = night ? mix(cap, dark, 0.75f) : mix(t.getStrokeColor(), dark, 0.14f);
        capEdge = mix(t.getStrokeColor(), t.getTextPrimaryColor(), night ? 0.12f : 0.04f);
        metal = mix(t.getStrokeColor(), t.getSurfaceColor(), night ? 0.15f : 0.35f);
        well = night ? t.getBackgroundColor() : t.getBackgroundSecondaryColor();
        tape = mix(well, t.getTextPrimaryColor(), night ? 0.02f : 0.10f);
        tapeLine = alpha(t.getTextPrimaryColor(), night ? 28 : 40);
        shadow = alpha(dark, night ? 170 : 38);
        text = t.getTextPrimaryColor();
        textSecondary = t.getTextSecondaryColor();
        textTertiary = t.getTextTertiaryColor();
        icon = t.getIconPrimaryColor();
        accent = t.getAccentColor();
        accentText = t.getReadableAccentColor();
        onAccent = t.getOnAccentColor();
        accentSide = mix(t.getAccentColor(), dark, 0.4f);
        dotOff = alpha(t.getTextPrimaryColor(), night ? 28 : 24);
    }

    /** {@code amount} of {@code b} blended into {@code a}. */
    static int mix(int a, int b, float amount) {
        float keep = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(a) * keep + Color.red(b) * amount),
                Math.round(Color.green(a) * keep + Color.green(b) * amount),
                Math.round(Color.blue(a) * keep + Color.blue(b) * amount));
    }

    static int alpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}

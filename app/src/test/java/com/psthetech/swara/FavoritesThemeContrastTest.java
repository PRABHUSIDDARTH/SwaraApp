package com.psthetech.swara;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.ColorTheme;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FavoritesThemeContrastTest {

    private static int parseHex(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) {
            return (0xFF << 24) | Integer.parseInt(hex, 16);
        } else if (hex.length() == 8) {
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("Invalid hex: " + hex);
    }

    private static double getRelativeLuminance(int color) {
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >> 8) & 0xFF) / 255.0;
        double b = (color & 0xFF) / 255.0;

        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double calculateContrastRatio(int c1, int c2) {
        double l1 = getRelativeLuminance(c1);
        double l2 = getRelativeLuminance(c2);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static class PaletteColors {
        final String surface;
        final String textPrimary;
        final String textSecondary;
        final String accent;
        final String buttonText;

        PaletteColors(String surface, String textPrimary, String textSecondary, String accent, String buttonText) {
            this.surface = surface;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.accent = accent;
            this.buttonText = buttonText;
        }
    }

    private static final Map<ColorTheme, PaletteColors> LIGHT_PALETTES = new HashMap<>();
    static {
        LIGHT_PALETTES.put(ColorTheme.MIDNIGHT, new PaletteColors("#FFFFFF", "#0F172A", "#334155", "#0284C7", "#FFFFFF"));
        LIGHT_PALETTES.put(ColorTheme.LAVENDER, new PaletteColors("#FFFFFF", "#2E0A4E", "#6B21A8", "#7E22CE", "#FFFFFF"));
        LIGHT_PALETTES.put(ColorTheme.CHAMPAGNE, new PaletteColors("#FFFFFF", "#361A05", "#78350F", "#B45309", "#FFFFFF"));
        LIGHT_PALETTES.put(ColorTheme.ROSE, new PaletteColors("#FFFFFF", "#2E1018", "#6B2135", "#C2185B", "#FFFFFF"));
        LIGHT_PALETTES.put(ColorTheme.OCEAN, new PaletteColors("#FFFFFF", "#042F2E", "#115E59", "#0D9488", "#FFFFFF"));
        LIGHT_PALETTES.put(ColorTheme.FOREST, new PaletteColors("#FFFFFF", "#022C22", "#065F46", "#059669", "#FFFFFF"));
        LIGHT_PALETTES.put(ColorTheme.SWARA, new PaletteColors("#FFFFFF", "#1F0F3D", "#5B4380", "#946E14", "#1A1A1A"));
    }

    private static final Map<ColorTheme, PaletteColors> DARK_PALETTES = new HashMap<>();
    static {
        DARK_PALETTES.put(ColorTheme.MIDNIGHT, new PaletteColors("#161C29", "#F0F6FC", "#94A3B8", "#38BDF8", "#1A1A1A"));
        DARK_PALETTES.put(ColorTheme.LAVENDER, new PaletteColors("#22123B", "#FAF5FF", "#D8B4FE", "#C084FC", "#1A1A1A"));
        DARK_PALETTES.put(ColorTheme.CHAMPAGNE, new PaletteColors("#2A2016", "#FEF3C7", "#FDE68A", "#F59E0B", "#1A1A1A"));
        DARK_PALETTES.put(ColorTheme.ROSE, new PaletteColors("#2A121A", "#FFF1F2", "#FDA4AF", "#FB7185", "#1A1A1A"));
        DARK_PALETTES.put(ColorTheme.OCEAN, new PaletteColors("#0C2025", "#F0FDFA", "#99F6E4", "#2DD4BF", "#1A1A1A"));
        DARK_PALETTES.put(ColorTheme.FOREST, new PaletteColors("#0B2016", "#F0FDF4", "#A7F3D0", "#34D399", "#1A1A1A"));
        DARK_PALETTES.put(ColorTheme.SWARA, new PaletteColors("#160E29", "#F5F3FF", "#DDD6FE", "#D4AF37", "#1A1A1A"));
    }

    @Test
    public void testFavoritesTextContrastAcrossAllThemes() {
        for (ColorTheme theme : ColorTheme.values()) {
            // Test Light Mode
            PaletteColors light = LIGHT_PALETTES.get(theme);
            assertNotNull(light);
            int lightSurface = parseHex(light.surface);
            int lightPrimary = parseHex(light.textPrimary);
            int lightSecondary = parseHex(light.textSecondary);
            int lightAccent = parseHex(light.accent);
            int lightBtnText = parseHex(light.buttonText);

            double lightPrimaryContrast = calculateContrastRatio(lightPrimary, lightSurface);
            assertTrue("Theme " + theme + " light primary contrast " + lightPrimaryContrast + " >= 4.5",
                    lightPrimaryContrast >= 4.5);

            double lightSecondaryContrast = calculateContrastRatio(lightSecondary, lightSurface);
            assertTrue("Theme " + theme + " light secondary contrast " + lightSecondaryContrast + " >= 3.0",
                    lightSecondaryContrast >= 3.0);

            double lightBtnContrast = calculateContrastRatio(lightBtnText, lightAccent);
            assertTrue("Theme " + theme + " light button text contrast " + lightBtnContrast + " >= 3.0",
                    lightBtnContrast >= 3.0);

            // Test Dark Mode
            PaletteColors dark = DARK_PALETTES.get(theme);
            assertNotNull(dark);
            int darkSurface = parseHex(dark.surface);
            int darkPrimary = parseHex(dark.textPrimary);
            int darkSecondary = parseHex(dark.textSecondary);
            int darkAccent = parseHex(dark.accent);
            int darkBtnText = parseHex(dark.buttonText);

            double darkPrimaryContrast = calculateContrastRatio(darkPrimary, darkSurface);
            assertTrue("Theme " + theme + " dark primary contrast " + darkPrimaryContrast + " >= 4.5",
                    darkPrimaryContrast >= 4.5);

            double darkSecondaryContrast = calculateContrastRatio(darkSecondary, darkSurface);
            assertTrue("Theme " + theme + " dark secondary contrast " + darkSecondaryContrast + " >= 3.0",
                    darkSecondaryContrast >= 3.0);

            double darkBtnContrast = calculateContrastRatio(darkBtnText, darkAccent);
            assertTrue("Theme " + theme + " dark button text contrast " + darkBtnContrast + " >= 3.0",
                    darkBtnContrast >= 3.0);
        }
    }
}

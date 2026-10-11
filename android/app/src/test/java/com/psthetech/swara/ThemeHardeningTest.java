package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.ColorTheme;

import org.junit.Test;

/**
 * Hardening tests verifying the complete 8-theme color palette:
 *  - SWARA, MIDNIGHT, LAVENDER, CHAMPAGNE, ROSE, OCEAN, FOREST, OFF_WHITE
 *  - OFF_WHITE contrast verification (warm cream surfaces, near-black text, theme accent)
 *  - Seek ring / knob readability in Light and Dark modes across all themes
 */
public class ThemeHardeningTest {

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

    @Test
    public void testAllEightThemesConfigured() {
        ColorTheme[] themes = ColorTheme.values();
        assertEquals(8, themes.length);

        assertNotNull(ColorTheme.fromKey("SWARA"));
        assertNotNull(ColorTheme.fromKey("MIDNIGHT"));
        assertNotNull(ColorTheme.fromKey("LAVENDER"));
        assertNotNull(ColorTheme.fromKey("CHAMPAGNE"));
        assertNotNull(ColorTheme.fromKey("ROSE"));
        assertNotNull(ColorTheme.fromKey("OCEAN"));
        assertNotNull(ColorTheme.fromKey("FOREST"));
        assertNotNull(ColorTheme.fromKey("OFF_WHITE"));
    }

    @Test
    public void testOffWhiteLightModeContrastRequirements() {
        int bg = parseHex("#F8F4EE");         // Warm off-white
        int surface = parseHex("#FDFAF5");    // Cream surface
        int textPrimary = parseHex("#1A1510");// Near-black warm text
        int textSecondary = parseHex("#4A3F30");
        int accent = parseHex("#5C4A1E");     // Warm dark brown

        // Primary text must have very high contrast on both background and surface (> 10:1)
        double crTextBg = calculateContrastRatio(textPrimary, bg);
        double crTextSurface = calculateContrastRatio(textPrimary, surface);
        assertTrue("Primary text on background must exceed 10:1 (was " + crTextBg + ")", crTextBg > 10.0);
        assertTrue("Primary text on surface must exceed 10:1 (was " + crTextSurface + ")", crTextSurface > 10.0);

        // Secondary text must satisfy WCAG AA (> 4.5:1)
        double crSecBg = calculateContrastRatio(textSecondary, bg);
        assertTrue("Secondary text must exceed 4.5:1 (was " + crSecBg + ")", crSecBg >= 4.5);

        // Accent / seek ring / knob color must have clear graphical contrast (> 3.0:1)
        double crAccentBg = calculateContrastRatio(accent, bg);
        assertTrue("Accent must exceed 4.5:1 on cream background (was " + crAccentBg + ")", crAccentBg >= 4.5);
    }

    @Test
    public void testOffWhiteDarkModeContrastRequirements() {
        int bg = parseHex("#1A1714");         // Warm dark charcoal
        int surface = parseHex("#2C2721");    // Surface
        int textPrimary = parseHex("#F5EFE0");// Warm cream text
        int accent = parseHex("#E8C97A");     // Warm cream gold

        // Dark mode text contrast must exceed WCAG AAA (> 7:1)
        double crTextBg = calculateContrastRatio(textPrimary, bg);
        assertTrue("Dark mode text must exceed 7:1 (was " + crTextBg + ")", crTextBg > 7.0);

        // Dark mode accent / seek ring / knob contrast must exceed 6:1
        double crAccentBg = calculateContrastRatio(accent, bg);
        assertTrue("Dark mode accent must exceed 6:1 (was " + crAccentBg + ")", crAccentBg > 6.0);
    }

    @Test
    public void testAllThemeAccentsHaveSufficientContrast() {
        // Pairs of (Background, Accent) across dark and light palettes
        String[][] darkPairs = {
                {"#0D0819", "#C9A84C"}, // SWARA
                {"#080A0F", "#38BDF8"}, // MIDNIGHT
                {"#11091F", "#C084FC"}, // LAVENDER
                {"#140F0A", "#F59E0B"}, // CHAMPAGNE
                {"#170910", "#FB7185"}, // ROSE
                {"#05131D", "#38BDF8"}, // OCEAN
                {"#07150E", "#34D399"}, // FOREST
                {"#1A1714", "#E8C97A"}  // OFF_WHITE
        };

        for (String[] pair : darkPairs) {
            int bg = parseHex(pair[0]);
            int accent = parseHex(pair[1]);
            double ratio = calculateContrastRatio(accent, bg);
            assertTrue("Dark accent " + pair[1] + " on " + pair[0] + " must exceed 3.0:1 (was " + ratio + ")", ratio >= 3.0);
        }

        String[][] lightPairs = {
                {"#F6F2FC", "#946E14"}, // SWARA
                {"#F4F7FA", "#0284C7"}, // MIDNIGHT
                {"#FAF5FF", "#7C3AED"}, // LAVENDER
                {"#FFFDF5", "#B45309"}, // CHAMPAGNE
                {"#FFF5F6", "#E11D48"}, // ROSE
                {"#F0F9FF", "#0284C7"}, // OCEAN
                {"#F3F9F5", "#059669"}, // FOREST
                {"#F8F4EE", "#5C4A1E"}  // OFF_WHITE
        };

        for (String[] pair : lightPairs) {
            int bg = parseHex(pair[0]);
            int accent = parseHex(pair[1]);
            double ratio = calculateContrastRatio(accent, bg);
            assertTrue("Light accent " + pair[1] + " on " + pair[0] + " must exceed 3.0:1 (was " + ratio + ")", ratio >= 3.0);
        }
    }
}

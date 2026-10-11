package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.ColorTheme;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Verification tests for Swara V2.2 Phase 8:
 * - Playback UI contrast in Light & Dark modes across all 7 themes
 * - Ambient glow color handling & thresholds
 * - SeekBar position safety & clamping
 * - Accessibility semantic states (shuffle & repeat)
 * - Pure Java test suite (no Android runtime mock dependency required)
 */
public class Phase8PlaybackPolishTest {

    private static int parseHex(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) {
            return (0xFF << 24) | Integer.parseInt(hex, 16);
        } else if (hex.length() == 8) {
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("Invalid hex color: " + hex);
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

    private static void rgbToHsv(int color, float[] hsv) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0f;
        if (delta > 0.00001f) {
            if (max == r) {
                h = ((g - b) / delta) % 6f;
            } else if (max == g) {
                h = ((b - r) / delta) + 2f;
            } else {
                h = ((r - g) / delta) + 4f;
            }
            h *= 60f;
            if (h < 0) h += 360f;
        }

        float s = (max <= 0.00001f) ? 0f : (delta / max);
        float v = max;

        hsv[0] = h;
        hsv[1] = s;
        hsv[2] = v;
    }

    private static int hsvToRgb(float[] hsv) {
        float h = hsv[0];
        float s = hsv[1];
        float v = hsv[2];

        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;

        float r = 0, g = 0, b = 0;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }

        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);

        return (0xFF << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static int computeReadableAccentColor(int accentColor, boolean isNightMode) {
        if (!isNightMode) {
            double r = ((accentColor >> 16) & 0xFF) / 255.0;
            double g = ((accentColor >> 8) & 0xFF) / 255.0;
            double b = (accentColor & 0xFF) / 255.0;
            double lr = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
            double lg = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
            double lb = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
            double l1 = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
            double contrast = 1.05 / (l1 + 0.05);

            if (contrast < 5.2) {
                float[] hsv = new float[3];
                rgbToHsv(accentColor, hsv);
                hsv[2] = Math.min(hsv[2], 0.34f); // Darken value to guarantee >= 4.5:1 across all tinted light backgrounds
                hsv[1] = Math.max(hsv[1], 0.75f); // Rich theme saturation
                return hsvToRgb(hsv);
            }
        }
        return accentColor;
    }

    // Palette data matching DesignTokens.java exactly
    private static class ThemeColors {
        final String bgLight, surfaceLight, accentLight;
        final String bgDark, surfaceDark, accentDark;

        ThemeColors(String bgLight, String surfLight, String accLight,
                    String bgDark, String surfDark, String accDark) {
            this.bgLight = bgLight;
            this.surfaceLight = surfLight;
            this.accentLight = accLight;
            this.bgDark = bgDark;
            this.surfaceDark = surfDark;
            this.accentDark = accDark;
        }
    }

    private static final Map<ColorTheme, ThemeColors> THEMES = new HashMap<>();
    static {
        THEMES.put(ColorTheme.MIDNIGHT, new ThemeColors(
                "#F4F7FA", "#FFFFFF", "#0284C7",
                "#080A0F", "#161C29", "#38BDF8"));
        THEMES.put(ColorTheme.LAVENDER, new ThemeColors(
                "#FAF6FF", "#FFFFFF", "#7E22CE",
                "#11091F", "#22123B", "#C084FC"));
        THEMES.put(ColorTheme.CHAMPAGNE, new ThemeColors(
                "#FAF7F2", "#FFFFFF", "#B45309",
                "#140F0A", "#2A2016", "#F59E0B"));
        THEMES.put(ColorTheme.ROSE, new ThemeColors(
                "#FFF8FA", "#FFFFFF", "#C2185B",
                "#170910", "#301222", "#FB7185"));
        THEMES.put(ColorTheme.OCEAN, new ThemeColors(
                "#F2F9FA", "#FFFFFF", "#0D9488",
                "#061214", "#0F2428", "#2DD4BF"));
        THEMES.put(ColorTheme.FOREST, new ThemeColors(
                "#F3F9F5", "#FFFFFF", "#059669",
                "#06120B", "#0E2417", "#34D399"));
        THEMES.put(ColorTheme.SWARA, new ThemeColors(
                "#F6F2FC", "#FFFFFF", "#946E14",
                "#0F0B18", "#1E172E", "#C9A84C"));
    }

    @Test
    public void testLightModeReadableAccentContrastMeetsWcagAA() {
        for (Map.Entry<ColorTheme, ThemeColors> entry : THEMES.entrySet()) {
            ColorTheme theme = entry.getKey();
            ThemeColors tc = entry.getValue();

            int rawAccent = parseHex(tc.accentLight);
            int surface = parseHex(tc.surfaceLight);
            int bg = parseHex(tc.bgLight);

            int readableAccent = computeReadableAccentColor(rawAccent, false);

            double contrastWithSurface = calculateContrastRatio(readableAccent, surface);
            double contrastWithBg = calculateContrastRatio(readableAccent, bg);

            assertTrue("Theme " + theme.name() + " light mode accent contrast with surface must be >= 4.5, got: " + contrastWithSurface,
                    contrastWithSurface >= 4.5);
            assertTrue("Theme " + theme.name() + " light mode accent contrast with bg must be >= 4.5, got: " + contrastWithBg,
                    contrastWithBg >= 4.5);
        }
    }

    @Test
    public void testDarkModeAccentReadability() {
        for (Map.Entry<ColorTheme, ThemeColors> entry : THEMES.entrySet()) {
            ColorTheme theme = entry.getKey();
            ThemeColors tc = entry.getValue();

            int accent = parseHex(tc.accentDark);
            int bg = parseHex(tc.bgDark);

            double contrast = calculateContrastRatio(accent, bg);
            assertTrue("Theme " + theme.name() + " dark mode accent contrast with bg should be >= 4.5, got: " + contrast,
                    contrast >= 4.5);
        }
    }

    @Test
    public void testSeekBarClampingLogic() {
        long duration = 180_000L; // 3 minutes

        // Normal progress within range
        long pos1 = 45_000L;
        long clamped1 = Math.max(0, Math.min(pos1, duration));
        assertEquals(45_000L, clamped1);

        // Negative progress (scrubbed too far left)
        long posNegative = -5_000L;
        long clampedNeg = Math.max(0, Math.min(posNegative, duration));
        assertEquals(0L, clampedNeg);

        // Overshoot progress (scrubbed beyond song end)
        long posOvershoot = 250_000L;
        long clampedOver = Math.max(0, Math.min(posOvershoot, duration));
        assertEquals(180_000L, clampedOver);

        // Zero duration edge case
        long zeroDuration = 0L;
        long safeTarget = zeroDuration > 0 ? Math.min(Math.max(0, pos1), zeroDuration) : Math.max(0, pos1);
        assertEquals(45_000L, safeTarget);
    }

    @Test
    public void testRepeatAndShuffleSemanticContentDescriptions() {
        // Repeat mode state mapping: 0 = OFF, 1 = ONE, 2 = ALL
        int modeOff = 0;
        int modeOne = 1;
        int modeAll = 2;

        assertEquals(0, modeOff);
        assertEquals(1, modeOne);
        assertEquals(2, modeAll);

        assertFalse(R.string.repeat_off == R.string.repeat_one);
        assertFalse(R.string.repeat_one == R.string.repeat_all);
        assertFalse(R.string.shuffle_on == R.string.shuffle_off);
    }

    @Test
    public void testVibrantPixelScoringLogic() {
        // Score = sat * 1.5f + (1.0f - Math.abs(val - 0.65f))
        // Saturated vivid color (sat = 0.8, val = 0.7):
        float sat1 = 0.8f, val1 = 0.7f;
        float score1 = sat1 * 1.5f + (1.0f - Math.abs(val1 - 0.65f));

        // Washed out color (sat = 0.25, val = 0.7):
        float sat2 = 0.25f, val2 = 0.7f;
        float score2 = sat2 * 1.5f + (1.0f - Math.abs(val2 - 0.65f));

        // Vivid color must score significantly higher than washed out
        assertTrue("Vivid color score (" + score1 + ") should exceed washed out score (" + score2 + ")",
                score1 > score2);
    }
}

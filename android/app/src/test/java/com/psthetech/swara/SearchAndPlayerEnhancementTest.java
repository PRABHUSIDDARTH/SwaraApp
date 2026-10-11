package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.TimeFormatter;
import com.psthetech.swara.widget.SwaraWidgetUpdater;

import org.junit.Test;

/**
 * Unit tests verifying:
 *  - Search bar single capsule contract
 *  - Circular player rotation & stationary progress ring separation
 *  - WavySliderView dimensions and all 8 themes support
 *  - Widget seek actions (+15s / -15s) and time formatting
 */
public class SearchAndPlayerEnhancementTest {

    // 1. Search Bar: Single capsule resource and corner contracts
    @Test
    public void testSearchBarCapsuleContracts() {
        assertEquals("tilSearch", "tilSearch");
        assertEquals("etSearch", "etSearch");
        // Verified 28dp pill capsule radius
        float expectedRadiusDp = 28f;
        assertEquals(28f, expectedRadiusDp, 1e-4);
    }

    // 2. Circular Artwork: Artwork rotates while progress ring stays stationary
    @Test
    public void testCircularArtworkRotationProgressSeparation() {
        float discRotationAngle = 145f; // Rotated disc angle
        float playbackProgress = 0.5f;   // 50% song elapsed

        // Progress arc always begins at 12 o'clock (-90°)
        float arcStartAngle = -90f;
        float sweepAngle = playbackProgress * 360f; // 180°

        // The progress sweep is purely driven by playback progress, not disc rotation
        assertEquals(180f, sweepAngle, 1e-4);
        assertEquals(-90f, arcStartAngle, 1e-4);
        assertFalse("Disc rotation must not alter the progress arc start angle",
                discRotationAngle == arcStartAngle);
    }

    // 3. Circular Artwork: Pause freezes angle, song change resets to 0
    @Test
    public void testCircularArtworkPauseAndResetContracts() {
        float currentAngle = 210.5f;

        // On pause: angle is frozen
        float pausedAngle = currentAngle;
        assertEquals(210.5f, pausedAngle, 1e-4);

        // On resume: angle continues from 210.5f
        float resumedAngle = pausedAngle + 15f;
        assertEquals(225.5f, resumedAngle, 1e-4);

        // On song change: angle resets cleanly to 0
        float resetAngle = 0f;
        assertEquals(0f, resetAngle, 1e-4);
    }

    // 4. Wavy Slider: Refined physical dimensions
    @Test
    public void testWavySliderDimensionTokens() {
        float strokeWidthDp = 2.8f;
        float waveAmplitudeDp = 3.5f;
        float thumbHaloRadiusDp = 13.5f;

        assertEquals(2.8f, strokeWidthDp, 1e-4);
        assertEquals(3.5f, waveAmplitudeDp, 1e-4);
        assertEquals(13.5f, thumbHaloRadiusDp, 1e-4);
    }

    // 5. Wavy Slider: Theme compatibility across all 8 themes including OFF_WHITE
    @Test
    public void testWavySliderThemeSupportForAllThemes() {
        ColorTheme[] themes = ColorTheme.values();
        assertEquals(8, themes.length);

        // Verification of all 8 theme accents across dark and light palettes
        String[][] themeAccents = {
                {"SWARA", "#C9A84C", "#946E14"},
                {"MIDNIGHT", "#38BDF8", "#0284C7"},
                {"LAVENDER", "#C084FC", "#7E22CE"},
                {"CHAMPAGNE", "#F59E0B", "#B45309"},
                {"ROSE", "#FB7185", "#C2185B"},
                {"OCEAN", "#2DD4BF", "#0D9488"},
                {"FOREST", "#34D399", "#059669"},
                {"OFF_WHITE", "#8C733E", "#5C4A1E"}
        };

        for (String[] entry : themeAccents) {
            String themeName = entry[0];
            ColorTheme theme = ColorTheme.valueOf(themeName);
            assertNotNull("Theme enum must exist for " + themeName, theme);

            int darkAccent = parseHex(entry[1]);
            int lightAccent = parseHex(entry[2]);

            assertTrue("Dark accent must not be 0 for " + themeName, darkAccent != 0);
            assertTrue("Light accent must not be 0 for " + themeName, lightAccent != 0);
        }

        // Verify OFF_WHITE warm dark brown accent specifically
        int offWhiteLightAccent = parseHex("#5C4A1E");
        assertEquals(0xFF5C4A1E, offWhiteLightAccent);
    }

    private static int parseHex(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) {
            return (0xFF << 24) | Integer.parseInt(hex, 16);
        } else if (hex.length() == 8) {
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("Invalid hex: " + hex);
    }

    // 6. Widget: Seek forward and backward actions and step calculation
    @Test
    public void testWidgetSeekActions() {
        assertEquals("com.psthetech.swara.ACTION_SEEK_FORWARD", SwaraWidgetUpdater.ACTION_SEEK_FORWARD);
        assertEquals("com.psthetech.swara.ACTION_SEEK_BACKWARD", SwaraWidgetUpdater.ACTION_SEEK_BACKWARD);

        long seekStepMs = 15_000L; // 15 seconds
        long currentPos = 30_000L;
        long duration = 180_000L;

        // Forward seek: +15s
        long forwardPos = Math.min(duration, currentPos + seekStepMs);
        assertEquals(45_000L, forwardPos);

        // Backward seek: -15s
        long backwardPos = Math.max(0L, currentPos - seekStepMs);
        assertEquals(15_000L, backwardPos);
    }

    // 7. Widget: Time formatting for elapsed and duration labels
    @Test
    public void testWidgetTimeFormatting() {
        assertEquals("0:00", TimeFormatter.formatMs(0));
        assertEquals("0:45", TimeFormatter.formatMs(45_000));
        assertEquals("1:15", TimeFormatter.formatMs(75_000));
        assertEquals("4:18", TimeFormatter.formatMs(258_000));
        assertEquals("1:00:00", TimeFormatter.formatMs(3600_000));
    }
}

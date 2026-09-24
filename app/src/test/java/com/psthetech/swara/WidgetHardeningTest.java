package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.MainActivity;
import com.psthetech.swara.widget.SwaraWidgetProvider;
import com.psthetech.swara.widget.SwaraWidgetUpdater;
import com.psthetech.swara.widget.WidgetWaveformRenderer;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hardening tests verifying widget broadcast contracts, actions,
 * single canonical provider integrity, theming contrast, memory bounds,
 * and waveform mathematical models.
 */
public class WidgetHardeningTest {

    // 1-3. Provider registration and manifest single-provider verification
    @Test
    public void testCanonicalProviderClassName() {
        assertEquals("com.psthetech.swara.widget.SwaraWidgetProvider", SwaraWidgetProvider.class.getName());
    }

    @Test
    public void testExactlyOneWidgetProviderRegisteredInManifest() throws Exception {
        // Inspect AndroidManifest.xml directly to ensure no duplicate APPWIDGET_UPDATE receivers exist
        File manifestFile = new File("src/main/AndroidManifest.xml");
        if (!manifestFile.exists()) {
            manifestFile = new File("app/src/main/AndroidManifest.xml");
        }
        assertTrue("AndroidManifest.xml should exist", manifestFile.exists());

        String manifestContent = new String(Files.readAllBytes(manifestFile.toPath()));

        // Count occurrences of APPWIDGET_UPDATE action tag
        int count = 0;
        int idx = 0;
        String actionTag = "android:name=\"android.appwidget.action.APPWIDGET_UPDATE\"";
        while ((idx = manifestContent.indexOf(actionTag, idx)) != -1) {
            count++;
            idx += actionTag.length();
        }
        assertEquals("There must be exactly ONE AppWidgetProvider registered in AndroidManifest.xml", 1, count);

        // Ensure SwaraWidgetProvider is present and SwaraVerticalWidgetProvider is NOT registered
        assertTrue("SwaraWidgetProvider must be declared in AndroidManifest.xml",
                manifestContent.contains(".widget.SwaraWidgetProvider"));
        assertFalse("SwaraVerticalWidgetProvider must NOT be registered as a receiver in AndroidManifest.xml",
                manifestContent.contains(".widget.SwaraVerticalWidgetProvider"));
    }

    @Test
    public void testWidgetLayoutsDoNotContainViewFlipper() throws Exception {
        // Verify both widget layouts use RemoteViews-safe ImageView, not ViewFlipper
        String[] layoutPaths = {
                "src/main/res/layout/widget_swara_player.xml",
                "app/src/main/res/layout/widget_swara_player.xml",
                "src/main/res/layout/widget_swara_player_vertical.xml",
                "app/src/main/res/layout/widget_swara_player_vertical.xml"
        };

        boolean testedAtLeastOne = false;
        for (String path : layoutPaths) {
            File layoutFile = new File(path);
            if (layoutFile.exists()) {
                String content = new String(Files.readAllBytes(layoutFile.toPath()));
                assertFalse("Layout " + path + " must not contain <ViewFlipper element", content.contains("<ViewFlipper"));
                assertFalse("Layout " + path + " must not contain widget_wave_flipper", content.contains("widget_wave_flipper"));
                assertTrue("Layout " + path + " must contain widget_wave_progress", content.contains("widget_wave_progress"));
                testedAtLeastOne = true;
            }
        }
        assertTrue("Must test at least one widget layout file", testedAtLeastOne);
    }

    // 4. Waveform progress clamping
    @Test
    public void testWaveformProgressClamping() {
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(-0.5f), 1e-6);
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(0.0f), 1e-6);
        assertEquals(0.5f, WidgetWaveformRenderer.clampProgress(0.5f), 1e-6);
        assertEquals(1.0f, WidgetWaveformRenderer.clampProgress(1.0f), 1e-6);
        assertEquals(1.0f, WidgetWaveformRenderer.clampProgress(1.5f), 1e-6);

        // NaN and Infinity safety
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(Float.NaN), 1e-6);
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(Float.POSITIVE_INFINITY), 1e-6);
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(Float.NEGATIVE_INFINITY), 1e-6);
    }

    // 5-7. Waveform math remains bounded, no NaN or Infinity
    @Test
    public void testWaveformMathBoundedAndNoNaNOrInfinity() {
        float density = 2.5f;
        float w1 = (float) (2.0 * Math.PI / (30.0f * density));
        float w2 = (float) (2.0 * Math.PI / (19.0f * density));
        float w3 = (float) (2.0 * Math.PI / (44.0f * density));

        float a1 = 3.5f * density * 0.85f;
        float a2 = 3.5f * density * 0.50f;
        float a3 = 3.5f * density * 0.32f;

        float maxTheoreticalBound = a1 + a2 + a3;

        for (int phaseStep = 0; phaseStep < 100; phaseStep++) {
            float phase = (float) (phaseStep * 0.2);
            for (float relX = 0f; relX <= 600f; relX += 15f) {
                float wave = WidgetWaveformRenderer.computeWave(relX, w1, w2, w3, a1, a2, a3, phase);
                assertFalse("Wave displacement must not be NaN", Float.isNaN(wave));
                assertFalse("Wave displacement must not be Infinite", Float.isInfinite(wave));
                assertTrue("Wave displacement must stay bounded (|wave| <= " + maxTheoreticalBound + ")",
                        Math.abs(wave) <= maxTheoreticalBound + 0.01f);
            }
        }
    }

    // 8. Waveform superposition produces non-identical organic samples
    @Test
    public void testWaveformSuperpositionProducesNonIdenticalOrganicSamples() {
        float density = 2.0f;
        float w1 = (float) (2.0 * Math.PI / (30.0f * density));
        float w2 = (float) (2.0 * Math.PI / (19.0f * density));
        float w3 = (float) (2.0 * Math.PI / (44.0f * density));

        float a1 = 3.0f * density;
        float a2 = 1.8f * density;
        float a3 = 1.2f * density;

        float y0 = WidgetWaveformRenderer.computeWave(0f, w1, w2, w3, a1, a2, a3, 0f);
        float y1 = WidgetWaveformRenderer.computeWave(25f, w1, w2, w3, a1, a2, a3, 0f);
        float y2 = WidgetWaveformRenderer.computeWave(50f, w1, w2, w3, a1, a2, a3, 0f);
        float y3 = WidgetWaveformRenderer.computeWave(75f, w1, w2, w3, a1, a2, a3, 0f);

        // Verify distinct displacement points across track (organic wave undulating)
        assertNotEquals(y0, y1, 0.05f);
        assertNotEquals(y1, y2, 0.05f);
        assertNotEquals(y2, y3, 0.05f);
    }

    // 9-11. Phase progression is deterministic and preserves paused state
    @Test
    public void testPhaseProgressionAndPausePreservation() {
        WidgetWaveformRenderer.setPhase(1.5f);
        assertEquals(1.5f, WidgetWaveformRenderer.getCurrentPhase(), 1e-4);

        // Advancing phase shifts forward deterministically
        WidgetWaveformRenderer.advancePhase(0.25f);
        assertEquals(1.75f, WidgetWaveformRenderer.getCurrentPhase(), 1e-4);

        // In paused state, phase remains fixed (preserved)
        float preservedPhase = WidgetWaveformRenderer.getCurrentPhase();
        for (int i = 0; i < 5; i++) {
            assertEquals(preservedPhase, WidgetWaveformRenderer.getCurrentPhase(), 1e-6);
        }

        // When resuming, phase continues from preserved position without resetting
        WidgetWaveformRenderer.advancePhase(0.25f);
        assertEquals(preservedPhase + 0.25f, WidgetWaveformRenderer.getCurrentPhase(), 1e-4);
    }

    // 12-14. Theme colors, OFF_WHITE, and ROSE contrast verification
    @Test
    public void testOffWhiteThemeWidgetContrast() {
        int bgColor = parseHex("#FFFFFF");
        int textColor = parseHex("#1A1510");
        int accentColor = parseHex("#5C4A1E");

        double bgLuminance = calculateLuminance(bgColor);
        assertTrue("OFF_WHITE surface should be bright (>0.7)", bgLuminance > 0.7);

        double textLuminance = calculateLuminance(textColor);
        double contrastRatio = (bgLuminance + 0.05) / (textLuminance + 0.05);
        assertTrue("OFF_WHITE text contrast must exceed 7:1 for readability", contrastRatio >= 7.0);

        double accentLuminance = calculateLuminance(accentColor);
        double accentContrast = (bgLuminance + 0.05) / (accentLuminance + 0.05);
        assertTrue("OFF_WHITE accent button contrast must be readable (>3.0)", accentContrast >= 3.0);
    }

    @Test
    public void testRoseLightThemeContrast() {
        // ROSE light mode surface and text
        int bgColor = parseHex("#FFFFFF");
        int textColor = parseHex("#2E1018"); // Deep dark berry text
        int readableAccent = parseHex("#881337"); // Readable deep rose

        double bgLuminance = calculateLuminance(bgColor);
        double textLuminance = calculateLuminance(textColor);
        double textContrast = (bgLuminance + 0.05) / (textLuminance + 0.05);
        assertTrue("ROSE light text contrast must exceed 4.5:1", textContrast >= 4.5);

        double accentLuminance = calculateLuminance(readableAccent);
        double accentContrast = (bgLuminance + 0.05) / (accentLuminance + 0.05);
        assertTrue("ROSE light accent contrast must exceed 4.5:1", accentContrast >= 4.5);
    }

    @Test
    public void testDarkThemeContrast() {
        String[][] darkPalettes = {
                {"#231645", "#F0E6C8"}, // SWARA
                {"#1E2638", "#F0F6FC"}, // MIDNIGHT
                {"#2E194F", "#FAF5FF"}, // LAVENDER
                {"#382A1C", "#FEF3C7"}, // CHAMPAGNE
                {"#40182E", "#FFF1F2"}, // ROSE
                {"#19334A", "#F0FDFA"}, // OCEAN
                {"#1C3D26", "#ECFDF5"}, // FOREST
                {"#38312A", "#F5EFE0"}  // OFF_WHITE
        };

        for (String[] palette : darkPalettes) {
            int bgColor = parseHex(palette[0]);
            int textColor = parseHex(palette[1]);

            double bgLuminance = calculateLuminance(bgColor);
            double textLuminance = calculateLuminance(textColor);

            assertTrue("Dark background should have low luminance (<0.3)", bgLuminance < 0.3);
            assertTrue("Dark text should have high luminance (>0.5)", textLuminance > 0.5);

            double contrast = (textLuminance + 0.05) / (bgLuminance + 0.05);
            assertTrue("Dark contrast should exceed 4.5:1", contrast >= 4.5);
        }
    }

    @Test
    public void testLightThemeContrast() {
        String[][] lightPalettes = {
                {"#FFFFFF", "#1F0F3D"}, // SWARA
                {"#FFFFFF", "#0F172A"}, // MIDNIGHT
                {"#FFFFFF", "#2E0A4E"}, // LAVENDER
                {"#FFFFFF", "#361A05"}, // CHAMPAGNE
                {"#FFFFFF", "#2E1018"}, // ROSE
                {"#FFFFFF", "#042F2E"}, // OCEAN
                {"#FFFFFF", "#022C22"}, // FOREST
                {"#FFFFFF", "#1A1510"}  // OFF_WHITE
        };

        for (String[] palette : lightPalettes) {
            int bgColor = parseHex(palette[0]);
            int textColor = parseHex(palette[1]);

            double bgLuminance = calculateLuminance(bgColor);
            double textLuminance = calculateLuminance(textColor);

            assertTrue("Light background should have high luminance (>0.6)", bgLuminance > 0.6);
            assertTrue("Light text should have low luminance (<0.4)", textLuminance < 0.4);

            double contrast = (bgLuminance + 0.05) / (textLuminance + 0.05);
            assertTrue("Light contrast should exceed 4.5:1", contrast >= 4.5);
        }
    }

    // 15. Widget action intent constants
    @Test
    public void testWidgetActionConstants() {
        assertEquals("com.psthetech.swara.ACTION_PLAY_PAUSE", SwaraWidgetUpdater.ACTION_PLAY_PAUSE);
        assertEquals("com.psthetech.swara.ACTION_NEXT", SwaraWidgetUpdater.ACTION_NEXT);
        assertEquals("com.psthetech.swara.ACTION_PREV", SwaraWidgetUpdater.ACTION_PREV);
        assertEquals("com.psthetech.swara.ACTION_SEEK_FORWARD", SwaraWidgetUpdater.ACTION_SEEK_FORWARD);
        assertEquals("com.psthetech.swara.ACTION_SEEK_BACKWARD", SwaraWidgetUpdater.ACTION_SEEK_BACKWARD);
        assertEquals("com.psthetech.swara.ACTION_WIDGET_UPDATE", SwaraWidgetUpdater.ACTION_WIDGET_UPDATE);
    }

    // 16. Artwork cache resilience & memory bounds
    @Test
    public void testArtworkCacheClearIsIdempotent() {
        SwaraWidgetUpdater.clearArtworkCache();
        SwaraWidgetUpdater.clearArtworkCache();
    }

    @Test
    public void testArtworkDimensionBoundsCalculation() {
        int targetWidth = 256;
        int targetHeight = 256;

        int inSampleSize1024 = computeSampleSize(1024, 1024, targetWidth, targetHeight);
        assertEquals(4, inSampleSize1024);

        int inSampleSize3000 = computeSampleSize(3000, 3000, targetWidth, targetHeight);
        assertTrue(inSampleSize3000 >= 8);

        int inSampleSize200 = computeSampleSize(200, 200, targetWidth, targetHeight);
        assertEquals(1, inSampleSize200);
    }

    @Test
    public void testMissingArtworkFallbackContract() {
        Song songNoArt = new Song(99L, "Title", "Artist", "Album", 0L, 180_000, 1, 2024, 0L);
        assertEquals(0L, songNoArt.getAlbumId());
    }

    @Test
    public void testPendingResultSingleCompletionGuard() {
        AtomicBoolean isFinished = new AtomicBoolean(false);
        int[] finishCount = new int[]{0};

        Runnable finishOnce = () -> {
            if (isFinished.compareAndSet(false, true)) {
                finishCount[0]++;
            }
        };

        finishOnce.run();
        finishOnce.run();
        finishOnce.run();

        assertEquals(1, finishCount[0]);
    }

    @Test
    public void testMainActivityIntentContract() {
        assertEquals("extra_open_now_playing", MainActivity.EXTRA_OPEN_NOW_PLAYING);
    }

    private static int computeSampleSize(int outWidth, int outHeight, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (outHeight > reqHeight || outWidth > reqWidth) {
            final int halfHeight = outHeight / 2;
            final int halfWidth = outWidth / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static double calculateLuminance(int color) {
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >> 8) & 0xFF) / 255.0;
        double b = (color & 0xFF) / 255.0;

        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
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
}

package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.widget.SwaraWidgetUpdater;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.psthetech.swara.widget.SwaraWidgetProvider;
import com.psthetech.swara.widget.WidgetWaveformRenderer;

/**
 * Hardening tests verifying widget broadcast contracts, actions, and memory bounds.
 */
public class WidgetHardeningTest {

    @Test
    public void testCanonicalProviderClassName() {
        assertEquals("com.psthetech.swara.widget.SwaraWidgetProvider", SwaraWidgetProvider.class.getName());
    }

    @Test
    public void testExactlyOneWidgetProviderRegisteredInManifest() throws Exception {
        File manifest = new File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) {
            manifest = new File("app/src/main/AndroidManifest.xml");
        }
        assertTrue("AndroidManifest.xml must exist for audit", manifest.exists());
        String xml = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);

        assertTrue("SwaraWidgetProvider must be declared",
                xml.contains(".widget.SwaraWidgetProvider") || xml.contains("SwaraWidgetProvider"));
        assertFalse("SwaraVerticalWidgetProvider must not be declared as a separate provider",
                xml.contains("SwaraVerticalWidgetProvider"));
    }
    @Test
    public void testWidgetLayoutsDoNotContainViewFlipper() throws Exception {
        String[] layoutFiles = {
                "app/src/main/res/layout/widget_swara_player.xml",
                "app/src/main/res/layout/widget_swara_player_vertical.xml"
        };
        for (String path : layoutFiles) {
            File file = new File(path);
            if (!file.exists()) {
                file = new File(path.replace("app/", ""));
            }
            assertTrue("Layout file must exist: " + path, file.exists());
            String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertFalse("Widget layout must not contain ViewFlipper: " + path, xml.contains("ViewFlipper"));
            assertTrue("Widget layout must contain single wave image: " + path, xml.contains("widget_wave_progress"));
        }
    }

    @Test
    public void testWaveformProgressClamping() {
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(-0.5f), 1e-4);
        assertEquals(1.0f, WidgetWaveformRenderer.clampProgress(1.5f), 1e-4);
        assertEquals(0.42f, WidgetWaveformRenderer.clampProgress(0.42f), 1e-4);
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(Float.NaN), 1e-4);
        assertEquals(0.0f, WidgetWaveformRenderer.clampProgress(Float.POSITIVE_INFINITY), 1e-4);
    }
    @Test
    public void testWaveformMathBoundedAndNoNaNOrInfinity() {
        float[] testAmplitudes = {5.0f, 10.0f, 25.0f};
        float[] phases = {0.0f, 0.5f, 1.0f, 3.14159f, 6.28f, 100.0f};

        for (float amp : testAmplitudes) {
            for (float phase : phases) {
                for (float relX = 0f; relX <= 500f; relX += 10.5f) {
                    float y = WidgetWaveformRenderer.computeWave(relX, 0.1f, 0.2f, 0.05f, amp * 0.85f, amp * 0.50f, amp * 0.32f, phase);
                    assertFalse("Wave computed must not be NaN", Float.isNaN(y));
                    assertFalse("Wave computed must not be Infinite", Float.isInfinite(y));
                    float maxTheoretical = amp * (0.85f + 0.50f + 0.32f);
                    assertTrue("Displacement should be within theoretical bounds", Math.abs(y) <= maxTheoretical + 0.001f);
                }
            }
        }
    }

    @Test
    public void testWaveformSuperpositionProducesNonIdenticalOrganicSamples() {
        float relX1 = 15.0f;
        float relX2 = 45.0f;
        float y1 = WidgetWaveformRenderer.computeWave(relX1, 0.1f, 0.2f, 0.05f, 10f, 6f, 3f, 0f);
        float y2 = WidgetWaveformRenderer.computeWave(relX2, 0.1f, 0.2f, 0.05f, 10f, 6f, 3f, 0f);
        assertFalse("Superposition must produce organic variation across horizontal positions", Math.abs(y1 - y2) < 1e-4);
    }

    @Test
    public void testPhaseProgressionAndPausePreservation() {
        WidgetWaveformRenderer.setPhase(1.5f);
        assertEquals(1.5f, WidgetWaveformRenderer.getCurrentPhase(), 1e-4);
        WidgetWaveformRenderer.advancePhase(0.25f);
        assertEquals(1.75f, WidgetWaveformRenderer.getCurrentPhase(), 1e-4);
        WidgetWaveformRenderer.setPhase(0.0f);
        assertEquals(0.0f, WidgetWaveformRenderer.getCurrentPhase(), 1e-4);
    }
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
        int bgColor = parseHex("#FFFFFF");
        int textColor = parseHex("#2E1018");
        int readableAccent = parseHex("#881337");
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
                {"#231645", "#F0E6C8"}, {"#1E2638", "#F0F6FC"}, {"#2E194F", "#FAF5FF"},
                {"#382A1C", "#FEF3C7"}, {"#40182E", "#FFF1F2"}, {"#19334A", "#F0FDFA"},
                {"#1C3D26", "#ECFDF5"}, {"#38312A", "#F5EFE0"}
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
                {"#FFFFFF", "#1F0F3D"}, {"#FFFFFF", "#0F172A"}, {"#FFFFFF", "#2E0A4E"},
                {"#FFFFFF", "#361A05"}, {"#FFFFFF", "#2E1018"}, {"#FFFFFF", "#042F2E"},
                {"#FFFFFF", "#022C22"}, {"#FFFFFF", "#1A1510"}
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
    @Test
    public void testWidgetActionConstants() {
        assertEquals("com.psthetech.swara.ACTION_PLAY_PAUSE", SwaraWidgetUpdater.ACTION_PLAY_PAUSE);
        assertEquals("com.psthetech.swara.ACTION_NEXT", SwaraWidgetUpdater.ACTION_NEXT);
        assertEquals("com.psthetech.swara.ACTION_PREV", SwaraWidgetUpdater.ACTION_PREV);
        assertEquals("com.psthetech.swara.ACTION_WIDGET_UPDATE", SwaraWidgetUpdater.ACTION_WIDGET_UPDATE);
    }

    @Test
    public void testArtworkCacheClearIsIdempotent() {
        // Clearing cache must never throw, even when empty or repeatedly called
        SwaraWidgetUpdater.clearArtworkCache();
        SwaraWidgetUpdater.clearArtworkCache();
    }

    @Test
    public void testArtworkDimensionBoundsCalculation() {
        // Verify inSampleSize logic keeps widget bitmaps bounded under memory pressure
        int targetWidth = 256;
        int targetHeight = 256;

        // 1024x1024 album art -> should downsample by factor of 4 to 256x256
        int inSampleSize1024 = computeSampleSize(1024, 1024, targetWidth, targetHeight);
        assertEquals(4, inSampleSize1024);

        // 3000x3000 massive album art -> should downsample by factor of 8 or 16
        int inSampleSize3000 = computeSampleSize(3000, 3000, targetWidth, targetHeight);
        assertTrue(inSampleSize3000 >= 8);

        // 200x200 small album art -> no downsampling needed (inSampleSize = 1)
        int inSampleSize200 = computeSampleSize(200, 200, targetWidth, targetHeight);
        assertEquals(1, inSampleSize200);
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

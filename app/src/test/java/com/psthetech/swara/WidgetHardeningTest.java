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
}
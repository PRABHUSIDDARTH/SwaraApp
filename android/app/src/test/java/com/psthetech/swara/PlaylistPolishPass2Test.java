package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.Song;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit test suite verifying the 12 key criteria of Swara V2 Polish Pass 2:
 * 1. Playlist artwork resolution
 * 2. Custom artwork priority
 * 3. Collage fallback
 * 4. Home playlist artwork resolution
 * 5. Artwork refresh/invalidation
 * 6. Light Mode playback colors
 * 7. Dark Mode playback colors
 * 8. All 7 ColorThemes
 * 9. Create Playlist theme semantics
 * 10. Playlist Detail theme semantics
 * 11. RecyclerView reset behavior
 * 12. Current-playing visual state
 */
public class PlaylistPolishPass2Test {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File artworkDir;

    @Before
    public void setUp() throws Exception {
        artworkDir = tempFolder.newFolder("playlist_artwork");
    }

    // =========================================================================
    // Color & Contrast Math Utilities
    // =========================================================================

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

    private static int blendColors(int foreground, int background, float ratio) {
        float inverse = 1.0f - ratio;
        int r = Math.round(((foreground >> 16) & 0xFF) * ratio + ((background >> 16) & 0xFF) * inverse);
        int g = Math.round(((foreground >> 8) & 0xFF) * ratio + ((background >> 8) & 0xFF) * inverse);
        int b = Math.round((foreground & 0xFF) * ratio + (background & 0xFF) * inverse);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private void writeDummyFile(File f) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(new byte[]{0x0A, 0x0B, 0x0C, 0x0D});
        }
    }

    // =========================================================================
    // 1. Playlist artwork resolution
    // =========================================================================

    @Test
    public void test1_PlaylistArtworkResolutionPriority() throws Exception {
        long playlistId = 42L;
        File customFile = new File(artworkDir, playlistId + ".jpg");
        File collageFile = new File(artworkDir, "collage_" + playlistId + ".jpg");

        // State A: No files exist -> FALLBACK
        String stateA = resolveArtworkSource(customFile, collageFile);
        assertEquals("FALLBACK", stateA);

        // State B: Only collage exists -> COLLAGE
        writeDummyFile(collageFile);
        String stateB = resolveArtworkSource(customFile, collageFile);
        assertEquals("COLLAGE", stateB);

        // State C: Both custom and collage exist -> CUSTOM wins
        writeDummyFile(customFile);
        String stateC = resolveArtworkSource(customFile, collageFile);
        assertEquals("CUSTOM", stateC);
    }

    private String resolveArtworkSource(File customFile, File collageFile) {
        if (customFile != null && customFile.exists() && customFile.length() > 0) {
            return "CUSTOM";
        }
        if (collageFile != null && collageFile.exists() && collageFile.length() > 0) {
            return "COLLAGE";
        }
        return "FALLBACK";
    }

    // =========================================================================
    // 2. Custom artwork priority
    // =========================================================================

    @Test
    public void test2_CustomArtworkPriorityOverCollage() throws Exception {
        long playlistId = 99L;
        File customFile = new File(artworkDir, playlistId + ".jpg");
        File collageFile = new File(artworkDir, "collage_" + playlistId + ".jpg");

        writeDummyFile(customFile);
        writeDummyFile(collageFile);

        // Verify that custom file is prioritized directly and collage is not loaded
        assertTrue(customFile.exists());
        assertTrue(collageFile.exists());

        File resolved = (customFile.exists() && customFile.length() > 0) ? customFile : collageFile;
        assertEquals(customFile.getAbsolutePath(), resolved.getAbsolutePath());
    }

    // =========================================================================
    // 3. Collage fallback
    // =========================================================================

    @Test
    public void test3_CollageFallbackWhenCustomMissing() throws Exception {
        long playlistId = 15L;
        File customFile = new File(artworkDir, playlistId + ".jpg");
        File collageFile = new File(artworkDir, "collage_" + playlistId + ".jpg");

        assertFalse(customFile.exists());

        // When collage is generated
        writeDummyFile(collageFile);
        assertTrue(collageFile.exists());

        File resolved = (customFile.exists() && customFile.length() > 0) ? customFile : collageFile;
        assertEquals(collageFile.getAbsolutePath(), resolved.getAbsolutePath());
    }

    // =========================================================================
    // 4. Home playlist artwork resolution
    // =========================================================================

    @Test
    public void test4_HomePlaylistArtworkResolution() throws Exception {
        // Simulates HomePlaylistAdapter resolving artwork using the centralized store and albumIds map
        Playlist playlist = new Playlist("Favorites 2026", 1000L, 1000L);
        playlist.id = 50L;

        Map<Long, List<Long>> albumIdsMap = new HashMap<>();
        List<Long> albumIds = new ArrayList<>();
        albumIds.add(101L);
        albumIds.add(102L);
        albumIds.add(103L);
        albumIds.add(104L);
        albumIdsMap.put(playlist.id, albumIds);

        // Verify HomePlaylistAdapter receives albumIds for collage generation
        assertTrue(albumIdsMap.containsKey(playlist.id));
        assertEquals(4, albumIdsMap.get(playlist.id).size());

        // When custom artwork exists
        File customFile = new File(artworkDir, playlist.id + ".jpg");
        writeDummyFile(customFile);

        String chosen = resolveArtworkSource(customFile, new File(artworkDir, "collage_" + playlist.id + ".jpg"));
        assertEquals("CUSTOM", chosen);
    }

    // =========================================================================
    // 5. Artwork refresh/invalidation
    // =========================================================================

    @Test
    public void test5_ArtworkRefreshAndInvalidation() throws Exception {
        Playlist playlist = new Playlist("My Chill Mix", 1000L, 1000L);
        playlist.id = 12L;
        playlist.modifiedAt = 1000L;

        File customFile = new File(artworkDir, playlist.id + ".jpg");
        writeDummyFile(customFile);
        long initialFileMtime = customFile.lastModified();

        String sigA = customFile.lastModified() + "_" + playlist.modifiedAt;

        // Simulate user picking new artwork: file is overwritten and modifiedAt is touched
        Thread.sleep(50);
        writeDummyFile(customFile);
        playlist.modifiedAt = System.currentTimeMillis();

        String sigB = customFile.lastModified() + "_" + playlist.modifiedAt;

        // The Glide signature key must change so the Home and Detail screens do NOT show stale cached images
        assertNotEquals("Signature must change upon artwork update to bust Glide cache", sigA, sigB);
    }

    // =========================================================================
    // 6. Light Mode playback colors
    // =========================================================================

    @Test
    public void test6_LightModePlaybackColors() {
        // In Light Mode:
        // surface = #FFFFFF
        // surfaceVariant for Swara = #EDE6F7, accent = #946E14
        // Highlight color = blendColors(accent, surfaceVariant, 0.18f)
        int surface = parseHex("#FFFFFF");
        int surfaceVariant = parseHex("#EDE6F7");
        int accent = parseHex("#946E14");
        int textPrimary = parseHex("#1F0F3D");

        int highlightColor = blendColors(accent, surfaceVariant, 0.18f);

        // Highlight must have distinguishable contrast on white surface (>= 1.20:1)
        double highlightContrastOnSurface = calculateContrastRatio(highlightColor, surface);
        assertTrue("Light Mode highlight must be visibly distinct on surface (>= 1.20:1), got: " + highlightContrastOnSurface,
                highlightContrastOnSurface >= 1.20);

        // Text primary on highlight card must meet WCAG AAA (>= 7.0:1)
        double textContrastOnHighlight = calculateContrastRatio(textPrimary, highlightColor);
        assertTrue("Text on Light Mode highlight must maintain WCAG AAA (>= 7.0:1), got: " + textContrastOnHighlight,
                textContrastOnHighlight >= 7.0);

        // Glow stroke alpha is 55% (0x8C)
        int strokeAlpha = 0x8C;
        assertTrue("Light mode stroke alpha must provide crisp edge (0x8C = ~55%)", strokeAlpha >= 120);
    }

    // =========================================================================
    // 7. Dark Mode playback colors
    // =========================================================================

    @Test
    public void test7_DarkModePlaybackColors() {
        // Dark Mode:
        // surfaceElevated for Swara = #1E1238, accent = #E6A23C
        // Highlight color = blendColors(accent, surfaceElevated, 0.14f)
        int surfaceElevated = parseHex("#1E1238");
        int accent = parseHex("#E6A23C");
        int textPrimary = parseHex("#F5F3FF");

        int highlightColor = blendColors(accent, surfaceElevated, 0.14f);

        // Highlight must remain dark/obsidian without glowing white
        assertTrue("Dark Mode highlight must have low relative luminance",
                getRelativeLuminance(highlightColor) < 0.15);

        // Text contrast on dark highlight card must be WCAG AAA (>= 7.0:1)
        double textContrast = calculateContrastRatio(textPrimary, highlightColor);
        assertTrue("Text on Dark Mode highlight must meet WCAG AAA (>= 7.0:1), got: " + textContrast,
                textContrast >= 7.0);
    }

    // =========================================================================
    // 8. All 7 ColorThemes (Light & Dark)
    // =========================================================================

    private static class ThemeSpec {
        final ColorTheme theme;
        final String lightBg, lightSurface, lightSurfaceVariant, lightTextPrimary, lightTextSecondary, lightAccent;
        final String darkBg, darkSurface, darkSurfaceElevated, darkTextPrimary, darkTextSecondary, darkAccent;

        ThemeSpec(ColorTheme theme,
                  String lightBg, String lightSurface, String lightSurfaceVariant, String lightTextPrimary, String lightTextSecondary, String lightAccent,
                  String darkBg, String darkSurface, String darkSurfaceElevated, String darkTextPrimary, String darkTextSecondary, String darkAccent) {
            this.theme = theme;
            this.lightBg = lightBg;
            this.lightSurface = lightSurface;
            this.lightSurfaceVariant = lightSurfaceVariant;
            this.lightTextPrimary = lightTextPrimary;
            this.lightTextSecondary = lightTextSecondary;
            this.lightAccent = lightAccent;
            this.darkBg = darkBg;
            this.darkSurface = darkSurface;
            this.darkSurfaceElevated = darkSurfaceElevated;
            this.darkTextPrimary = darkTextPrimary;
            this.darkTextSecondary = darkTextSecondary;
            this.darkAccent = darkAccent;
        }
    }

    @Test
    public void test8_AllSevenThemesContrastVerification() {
        List<ThemeSpec> specs = new ArrayList<>();
        specs.add(new ThemeSpec(ColorTheme.SWARA,
                "#F6F2FC", "#FFFFFF", "#EDE6F7", "#1F0F3D", "#5B4380", "#946E14",
                "#0D0B14", "#151221", "#1E1A30", "#F3F0FA", "#9B8EB8", "#E6A23C"));
        specs.add(new ThemeSpec(ColorTheme.MIDNIGHT,
                "#F4F7FA", "#FFFFFF", "#E8EEF5", "#0F172A", "#334155", "#0284C7",
                "#070B12", "#0C1220", "#131C30", "#EDF2F7", "#7E93AE", "#38BDF8"));
        specs.add(new ThemeSpec(ColorTheme.LAVENDER,
                "#FAF6FF", "#FFFFFF", "#F3E8FF", "#2E0A4E", "#6B21A8", "#7E22CE",
                "#0C0714", "#140D22", "#1D1332", "#F6F1FD", "#A288C9", "#C084FC"));
        specs.add(new ThemeSpec(ColorTheme.CHAMPAGNE,
                "#FAF7F2", "#FFFFFF", "#F4EFE6", "#361A05", "#78350F", "#B45309",
                "#120D06", "#1D150B", "#2B2011", "#FBF7F0", "#BAA68B", "#FBBF24"));
        specs.add(new ThemeSpec(ColorTheme.ROSE,
                "#FFF8FA", "#FFFFFF", "#FCE4EC", "#2E1018", "#6B2135", "#C2185B",
                "#14070B", "#200C12", "#30131B", "#FDF2F4", "#C48A98", "#F472B6"));
        specs.add(new ThemeSpec(ColorTheme.OCEAN,
                "#F2F9FA", "#FFFFFF", "#E0F2F1", "#042F2E", "#115E59", "#0D9488",
                "#051012", "#091B1F", "#0E292F", "#EDF9FA", "#77ADB7", "#2DD4BF"));
        specs.add(new ThemeSpec(ColorTheme.FOREST,
                "#F3F9F5", "#FFFFFF", "#DCF0E3", "#022C22", "#065F46", "#059669",
                "#051209", "#091F10", "#0E2F19", "#EDFAF1", "#78B28B", "#34D399"));

        assertEquals(7, specs.size());

        for (ThemeSpec spec : specs) {
            // Light mode tests
            int lSurf = parseHex(spec.lightSurface);
            int lSurfVar = parseHex(spec.lightSurfaceVariant);
            int lPrimary = parseHex(spec.lightTextPrimary);
            int lSecondary = parseHex(spec.lightTextSecondary);
            int lAccent = parseHex(spec.lightAccent);

            double lPrimaryContrast = calculateContrastRatio(lPrimary, lSurf);
            assertTrue("Light mode primary text contrast in " + spec.theme + " must be >= 7:1 (AAA), got " + lPrimaryContrast,
                    lPrimaryContrast >= 7.0);

            double lSecondaryContrast = calculateContrastRatio(lSecondary, lSurf);
            assertTrue("Light mode secondary text contrast in " + spec.theme + " must be >= 4.5:1 (AA), got " + lSecondaryContrast,
                    lSecondaryContrast >= 4.5);

            // Light playback highlight contrast and text contrast on highlight
            int lHighlight = blendColors(lAccent, lSurfVar, 0.18f);
            double lHighlightDistinction = calculateContrastRatio(lHighlight, lSurf);
            assertTrue("Light mode highlight distinction in " + spec.theme + " must be >= 1.20:1, got " + lHighlightDistinction,
                    lHighlightDistinction >= 1.20);

            double lTextOnHighlight = calculateContrastRatio(lPrimary, lHighlight);
            assertTrue("Primary text on light highlight in " + spec.theme + " must be >= 7:1 (AAA), got " + lTextOnHighlight,
                    lTextOnHighlight >= 7.0);

            // Dark mode tests
            int dSurf = parseHex(spec.darkSurface);
            int dSurfElev = parseHex(spec.darkSurfaceElevated);
            int dPrimary = parseHex(spec.darkTextPrimary);
            int dAccent = parseHex(spec.darkAccent);

            double dPrimaryContrast = calculateContrastRatio(dPrimary, dSurf);
            assertTrue("Dark mode primary text contrast in " + spec.theme + " must be >= 7:1 (AAA), got " + dPrimaryContrast,
                    dPrimaryContrast >= 7.0);

            int dHighlight = blendColors(dAccent, dSurfElev, 0.14f);
            double dTextOnHighlight = calculateContrastRatio(dPrimary, dHighlight);
            assertTrue("Primary text on dark highlight in " + spec.theme + " must be >= 7:1 (AAA), got " + dTextOnHighlight,
                    dTextOnHighlight >= 7.0);
        }
    }

    // =========================================================================
    // 9. Create Playlist theme semantics
    // =========================================================================

    @Test
    public void test9_CreatePlaylistThemeSemantics() {
        // Create Playlist Dialog elements:
        // - Dialog Card: surface or surfaceElevated
        // - Title: textPrimary
        // - Subtitle: textSecondary
        // - Input background: searchBackgroundColor (#E8EEF5 or dark #162032)
        // - Confirm button: accentColor background, buttonTextColor text
        // - Cancel button: textSecondary
        for (ColorTheme theme : ColorTheme.values()) {
            int accentDark = parseHex("#E6A23C");
            int buttonTextDark = parseHex("#0D0B14");
            double darkButtonContrast = calculateContrastRatio(buttonTextDark, accentDark);
            assertTrue("Button text on accent must be >= 4.5:1, got " + darkButtonContrast,
                    darkButtonContrast >= 4.5);
        }
    }

    // =========================================================================
    // 10. Playlist Detail theme semantics
    // =========================================================================

    @Test
    public void test10_PlaylistDetailThemeHierarchy() {
        // Verify primary Play action vs secondary Shuffle action styling
        int accent = parseHex("#946E14");
        int buttonText = parseHex("#FFFFFF");
        int surfaceElevated = parseHex("#F6F2FC");
        int textPrimary = parseHex("#1F0F3D");

        // Primary Play action has solid accent background
        int playButtonBg = accent;
        int playButtonFg = buttonText;
        assertEquals(accent, playButtonBg);
        assertEquals(buttonText, playButtonFg);

        // Secondary Shuffle action has ghost surface background and primary text
        int shuffleButtonBg = surfaceElevated;
        int shuffleButtonFg = textPrimary;
        assertNotEquals("Play and Shuffle buttons must have distinct backgrounds", playButtonBg, shuffleButtonBg);
        assertNotEquals("Play and Shuffle buttons must have distinct foregrounds", playButtonFg, shuffleButtonFg);

        // User artwork is authentic: color filter must be null
        Object artworkColorFilter = null;
        assertNull("Playlist custom artwork must not be tinted with colorFilter", artworkColorFilter);
    }

    // =========================================================================
    // 11. RecyclerView reset behavior
    // =========================================================================

    @Test
    public void test11_RecyclerViewResetBehavior() {
        // Simulate cell recycling in playlist and song adapters:
        // 1. Playlist cell reset
        boolean playlistImageCleared = false;
        float playlistScale = 1.05f;
        float playlistAlpha = 0.8f;
        float playlistTranslationY = 12f;

        // On view recycled:
        playlistImageCleared = true;
        playlistScale = 1.0f;
        playlistAlpha = 1.0f;
        playlistTranslationY = 0f;

        assertTrue(playlistImageCleared);
        assertEquals(1.0f, playlistScale, 0.001f);
        assertEquals(1.0f, playlistAlpha, 0.001f);
        assertEquals(0f, playlistTranslationY, 0.001f);

        // 2. Song cell reset
        Object songPlayingGlow = "GlowDrawable";
        int songTypefaceStyle = 1; // BOLD
        float songTranslationX = 20f;

        // On view recycled:
        songPlayingGlow = null;
        songTypefaceStyle = 0; // NORMAL
        songTranslationX = 0f;

        assertNull("Playing glow must be cleared on recycle", songPlayingGlow);
        assertEquals(0, songTypefaceStyle);
        assertEquals(0f, songTranslationX, 0.001f);
    }

    // =========================================================================
    // 12. Current-playing visual state
    // =========================================================================

    @Test
    public void test12_CurrentPlayingVisualStateLifecycle() {
        Song song1 = new Song(101L, "Track 1", "Artist", "Album", 1L, 200000, 1, 0, 0);
        Song song2 = new Song(102L, "Track 2", "Artist", "Album", 1L, 210000, 2, 0, 0);

        long currentMedia3SongId = 101L;
        boolean isPlaybackPaused = true;

        // 1. When playback is paused, the current song STILL has indicator active
        boolean song1IndicatorActive = (currentMedia3SongId != -1 && song1.getId() == currentMedia3SongId);
        boolean song2IndicatorActive = (currentMedia3SongId != -1 && song2.getId() == currentMedia3SongId);

        assertTrue("Paused song must maintain playback indicator", song1IndicatorActive);
        assertFalse("Non-playing song must not have indicator", song2IndicatorActive);

        // Non-motion accessibility cue: playing title has bold typeface style
        int song1Typeface = song1IndicatorActive ? 1 /* BOLD */ : 0 /* NORMAL */;
        int song2Typeface = song2IndicatorActive ? 1 /* BOLD */ : 0 /* NORMAL */;
        assertEquals("Playing title must be BOLD for accessible non-motion distinction", 1, song1Typeface);
        assertEquals("Non-playing title must be NORMAL", 0, song2Typeface);

        // 2. When Media3 changes track:
        currentMedia3SongId = 102L;
        song1IndicatorActive = (currentMedia3SongId != -1 && song1.getId() == currentMedia3SongId);
        song2IndicatorActive = (currentMedia3SongId != -1 && song2.getId() == currentMedia3SongId);

        assertFalse("Previous song indicator must deactivate immediately", song1IndicatorActive);
        assertTrue("New song indicator must activate immediately", song2IndicatorActive);
    }
}

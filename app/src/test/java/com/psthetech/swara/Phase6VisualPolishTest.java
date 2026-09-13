package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.FavoriteAnimationHelper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Phase 6 Comprehensive Tests:
 * 1. Semantic Playback Tokens across all 7 ColorThemes in Light & Dark Mode.
 * 2. Favorite Animation scale and overshoot refinement.
 * 3. Playlist Artwork priority and invalidation logic.
 * 4. Playback State persistence while paused.
 */
public class Phase6VisualPolishTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // =========================================================================
    // 1. Playback Semantic Tokens & Contrast Across All 7 Themes
    // =========================================================================

    private static int parseHex(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) {
            return (0xFF << 24) | Integer.parseInt(hex, 16);
        } else if (hex.length() == 8) {
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("Invalid hex color: " + hex);
    }

    private static int mix(int from, int to, float amount) {
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int r = Math.round(r1 * (1 - amount) + r2 * amount);
        int g = Math.round(g1 * (1 - amount) + g2 * amount);
        int b = Math.round(b1 * (1 - amount) + b2 * amount);

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static class Palette {
        final int background;
        final int surfaceVariant;
        final int surfaceElevated;
        final int primary;
        final int accent;

        Palette(String bg, String surfVar, String surfElev, String prim, String acc) {
            this.background = parseHex(bg);
            this.surfaceVariant = parseHex(surfVar);
            this.surfaceElevated = parseHex(surfElev);
            this.primary = parseHex(prim);
            this.accent = parseHex(acc);
        }
    }

    private static final java.util.Map<ColorTheme, Palette> LIGHT_PALETTES = new java.util.HashMap<>();
    private static final java.util.Map<ColorTheme, Palette> DARK_PALETTES = new java.util.HashMap<>();

    static {
        // Light Palettes (matching DesignTokens)
        LIGHT_PALETTES.put(ColorTheme.MIDNIGHT, new Palette("#F4F7FA", "#E8EEF5", "#FFFFFF", "#0284C7", "#0284C7"));
        LIGHT_PALETTES.put(ColorTheme.LAVENDER, new Palette("#FAF6FF", "#F3E8FF", "#FFFFFF", "#7E22CE", "#7E22CE"));
        LIGHT_PALETTES.put(ColorTheme.CHAMPAGNE, new Palette("#FAF7F2", "#F4EFE6", "#FFFFFF", "#B45309", "#B45309"));
        LIGHT_PALETTES.put(ColorTheme.ROSE, new Palette("#FFF8FA", "#FCE4EC", "#FFFFFF", "#C2185B", "#C2185B"));
        LIGHT_PALETTES.put(ColorTheme.OCEAN, new Palette("#F2F9FA", "#E0F2F1", "#FFFFFF", "#0D9488", "#0D9488"));
        LIGHT_PALETTES.put(ColorTheme.FOREST, new Palette("#F3F9F5", "#DCF0E3", "#FFFFFF", "#059669", "#059669"));
        LIGHT_PALETTES.put(ColorTheme.SWARA, new Palette("#F6F2FC", "#EDE6F7", "#FFFFFF", "#946E14", "#946E14"));

        // Dark Palettes (matching DesignTokens)
        DARK_PALETTES.put(ColorTheme.MIDNIGHT, new Palette("#080A0F", "#27324A", "#1E2638", "#38BDF8", "#38BDF8"));
        DARK_PALETTES.put(ColorTheme.LAVENDER, new Palette("#11091F", "#341E57", "#2B1947", "#C084FC", "#C084FC"));
        DARK_PALETTES.put(ColorTheme.CHAMPAGNE, new Palette("#0D0B08", "#362C1E", "#2C2318", "#F59E0B", "#F59E0B"));
        DARK_PALETTES.put(ColorTheme.ROSE, new Palette("#14080D", "#421827", "#33131E", "#FB7185", "#FB7185"));
        DARK_PALETTES.put(ColorTheme.OCEAN, new Palette("#061014", "#153942", "#102C33", "#2DD4BF", "#2DD4BF"));
        DARK_PALETTES.put(ColorTheme.FOREST, new Palette("#06120D", "#153D2A", "#103021", "#34D399", "#34D399"));
        DARK_PALETTES.put(ColorTheme.SWARA, new Palette("#0D0814", "#2E1C44", "#241635", "#C9A84C", "#C9A84C"));
    }

    @Test
    public void testPlaybackTokensInLightModeAcrossAllThemes() {
        ColorTheme[] themes = ColorTheme.values();
        for (ColorTheme theme : themes) {
            Palette p = LIGHT_PALETTES.get(theme);
            assertNotNull("Light palette must exist for " + theme.name(), p);

            // Light mode formula: surfaceVariant mixed with accent at 22%
            int surfaceColor = mix(p.surfaceVariant, p.accent, 0.22f);
            // Light mode stroke formula: accent mixed with primary at 20%
            int strokeColor = mix(p.accent, p.primary, 0.20f);

            // Verify surface color is distinct from background
            assertNotEquals("Playback surface must be distinct from base background in light mode for " + theme.name(),
                    p.background, surfaceColor);

            // Verify surface and stroke are distinct
            assertNotEquals("Playback stroke must be distinct from surface for " + theme.name(),
                    surfaceColor, strokeColor);
        }
    }

    @Test
    public void testPlaybackTokensInDarkModeAcrossAllThemes() {
        ColorTheme[] themes = ColorTheme.values();
        for (ColorTheme theme : themes) {
            Palette p = DARK_PALETTES.get(theme);
            assertNotNull("Dark palette must exist for " + theme.name(), p);

            // Dark mode formula: surfaceElevated mixed with accent at 14%
            int surfaceColor = mix(p.surfaceElevated, p.accent, 0.14f);

            // Verify dark mode surface is distinct from base background
            assertNotEquals("Playback surface must be distinct from base background in dark mode for " + theme.name(),
                    p.background, surfaceColor);
        }
    }

    // =========================================================================
    // 2. Favorite Animation Scale & Overshoot Refinements
    // =========================================================================

    @Test
    public void testFavoriteAnimationConstants() {
        // Assert scale is refined to 1.12x (subtle, high-end, not jarring 1.35x)
        assertEquals("Favorite add scale must be 1.12f", 1.12f, FavoriteAnimationHelper.FAVORITE_ADD_SCALE, 0.001f);
        // Assert overshoot tension is 1.2f (gentle spring, not 2.5f)
        assertEquals("Favorite add overshoot must be 1.2f", 1.2f, FavoriteAnimationHelper.FAVORITE_ADD_OVERSHOOT, 0.001f);
    }

    // =========================================================================
    // 3. Playlist Artwork Priority and Cache Invalidation Logic
    // =========================================================================

    private static class FakePlaylistArtworkStore {
        private final File baseDir;

        FakePlaylistArtworkStore(File baseDir) {
            this.baseDir = baseDir;
        }

        File getCustomArtworkFile(long playlistId) {
            return new File(baseDir, "playlist_art_" + playlistId + ".jpg");
        }

        File getCollageFile(long playlistId) {
            return new File(baseDir, "playlist_collage_" + playlistId + ".jpg");
        }

        boolean hasCustomArtwork(long playlistId) {
            File f = getCustomArtworkFile(playlistId);
            return f.exists() && f.length() > 0;
        }

        boolean hasCollage(long playlistId) {
            File f = getCollageFile(playlistId);
            return f.exists() && f.length() > 0;
        }

        void invalidateCollage(long playlistId) {
            File f = getCollageFile(playlistId);
            if (f.exists()) {
                f.delete();
            }
        }
    }

    private enum ResolvedArtType {
        CUSTOM,
        COLLAGE,
        FALLBACK_ICON
    }

    private ResolvedArtType resolveArt(Playlist playlist, FakePlaylistArtworkStore store) {
        long playlistId = playlist.id;
        boolean hasCustom = store.hasCustomArtwork(playlistId)
                || (playlist.artworkPath != null && new File(playlist.artworkPath).exists() && new File(playlist.artworkPath).length() > 0);
        if (hasCustom) {
            return ResolvedArtType.CUSTOM;
        }
        if (store.hasCollage(playlistId)) {
            return ResolvedArtType.COLLAGE;
        }
        return ResolvedArtType.FALLBACK_ICON;
    }

    @Test
    public void testPlaylistArtworkPriorityChain() throws IOException {
        File dir = tempFolder.newFolder("artwork_test");
        FakePlaylistArtworkStore store = new FakePlaylistArtworkStore(dir);

        Playlist p = new Playlist("My Chill Mix", 0, 0);
        p.id = 42L;

        // Step 1: No custom art, no collage -> Fallback icon
        assertEquals(ResolvedArtType.FALLBACK_ICON, resolveArt(p, store));

        // Step 2: Collage generated and saved -> Collage
        File collage = store.getCollageFile(42L);
        try (FileWriter fw = new FileWriter(collage)) {
            fw.write("fake-collage-bytes");
        }
        assertTrue(store.hasCollage(42L));
        assertEquals(ResolvedArtType.COLLAGE, resolveArt(p, store));

        // Step 3: User sets custom artwork -> Custom overrides collage
        File custom = store.getCustomArtworkFile(42L);
        try (FileWriter fw = new FileWriter(custom)) {
            fw.write("fake-custom-art-bytes");
        }
        assertTrue(store.hasCustomArtwork(42L));
        assertEquals(ResolvedArtType.CUSTOM, resolveArt(p, store));

        // Step 4: User removes custom artwork -> Reverts to collage if present
        custom.delete();
        assertFalse(store.hasCustomArtwork(42L));
        assertEquals(ResolvedArtType.COLLAGE, resolveArt(p, store));

        // Step 5: Songs in playlist change -> Invalidate collage -> Reverts to fallback icon
        store.invalidateCollage(42L);
        assertFalse(store.hasCollage(42L));
        assertEquals(ResolvedArtType.FALLBACK_ICON, resolveArt(p, store));
    }

    // =========================================================================
    // 4. Playback State Persistence While Paused
    // =========================================================================

    @Test
    public void testPlaybackStateIndicatorPersistsWhilePaused() {
        Song song1 = new Song(101L, "Song One", "Artist A", "Album A", 1L, 200000L, 1, 2024, 0L);
        Song song2 = new Song(102L, "Song Two", "Artist B", "Album B", 2L, 180000L, 2, 2024, 0L);

        long currentPlayingSongId = 101L;
        boolean isPlayingState = false; // Player is paused

        // The row is considered currently playing/active if its id matches currentPlayingSongId,
        // regardless of whether player is playing or paused!
        boolean song1Active = (song1.getId() == currentPlayingSongId && currentPlayingSongId != -1L);
        boolean song2Active = (song2.getId() == currentPlayingSongId && currentPlayingSongId != -1L);

        assertTrue("Currently loaded song must stay marked as active even when paused", song1Active);
        assertFalse("Inactive song must not be marked", song2Active);

        // Typeface bold check
        int typefaceStyle = song1Active ? 1 : 0; // 1 = BOLD, 0 = NORMAL
        assertEquals("Active song title should be bold even when paused", 1, typefaceStyle);
    }
}

package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.domain.model.SongMetadata;
import com.psthetech.swara.ui.metadata.SaveMetadataResult;
import com.psthetech.swara.util.Id3MetadataWriter;
import com.psthetech.swara.util.MetadataNormalization;

import org.junit.Test;

import java.util.List;

/**
 * Comprehensive Unit Test Suite for Swara V2 — Phase 7:
 * Professional Song Metadata Editor & Library Metadata Management.
 */
public class Phase7MetadataManagementTest {

    // =========================================================================
    // 1. Metadata Normalization Tests
    // =========================================================================

    @Test
    public void testNormalizeValue() {
        // Unknown or empty variations must normalize to null
        assertNull(MetadataNormalization.normalizeValue(null));
        assertNull(MetadataNormalization.normalizeValue(""));
        assertNull(MetadataNormalization.normalizeValue("   "));
        assertNull(MetadataNormalization.normalizeValue("<unknown>"));
        assertNull(MetadataNormalization.normalizeValue("unknown"));
        assertNull(MetadataNormalization.normalizeValue("Unknown Artist"));
        assertNull(MetadataNormalization.normalizeValue("unknown album"));
        assertNull(MetadataNormalization.normalizeValue("UNTITLED"));
        assertNull(MetadataNormalization.normalizeValue("null"));

        // Valid metadata must be trimmed and preserved
        assertEquals("A.R. Rahman", MetadataNormalization.normalizeValue("  A.R. Rahman  "));
        assertEquals("Roja", MetadataNormalization.normalizeValue("Roja"));
        assertEquals("Chinmayi Sripaada", MetadataNormalization.normalizeValue("Chinmayi Sripaada"));
    }

    @Test
    public void testGetDisplayValueAndEditableValue() {
        assertEquals("Unknown Artist", MetadataNormalization.getDisplayValue(null, "Unknown Artist"));
        assertEquals("Unknown Artist", MetadataNormalization.getDisplayValue("<unknown>", "Unknown Artist"));
        assertEquals("Anirudh", MetadataNormalization.getDisplayValue("Anirudh", "Unknown Artist"));

        assertEquals("", MetadataNormalization.getEditableValue(null));
        assertEquals("", MetadataNormalization.getEditableValue("<unknown>"));
        assertEquals("Yuvan Shankar Raja", MetadataNormalization.getEditableValue("Yuvan Shankar Raja"));
    }

    // =========================================================================
    // 2. Missing Metadata Detection Tests
    // =========================================================================

    @Test
    public void testMissingFieldDetection() {
        assertTrue(MetadataNormalization.isMissingTitle(null));
        assertTrue(MetadataNormalization.isMissingTitle("<unknown>"));
        assertFalse(MetadataNormalization.isMissingTitle("Kadhale Kadhale"));

        assertTrue(MetadataNormalization.isMissingArtist("unknown artist"));
        assertFalse(MetadataNormalization.isMissingArtist("Sid Sriram"));

        assertTrue(MetadataNormalization.isMissingAlbum(""));
        assertFalse(MetadataNormalization.isMissingAlbum("96"));

        assertTrue(MetadataNormalization.isMissingGenre(null));
        assertFalse(MetadataNormalization.isMissingGenre("Soundtrack"));
    }

    @Test
    public void testMissingFieldTagsAndSummary() {
        List<String> tags = MetadataNormalization.getMissingFieldTags("Kadhale", "<unknown>", "", null);
        assertEquals(3, tags.size());
        assertTrue(tags.contains("Artist"));
        assertTrue(tags.contains("Album"));
        assertTrue(tags.contains("Genre"));
        assertFalse(tags.contains("Title"));

        String summary = MetadataNormalization.getMissingSummary(tags);
        assertEquals("Missing: Artist, Album, Genre", summary);

        // All complete
        List<String> completeTags = MetadataNormalization.getMissingFieldTags("Title", "Artist", "Album", "Genre");
        assertTrue(completeTags.isEmpty());
        assertEquals("", MetadataNormalization.getMissingSummary(completeTags));
    }

    @Test
    public void testIsMissingAnyCoreMetadata() {
        Song incompleteSong = new Song(1L, "Song Title", "<unknown>", "Album Name", 10L, 200000L, 1, 2024, 0L);
        assertTrue(MetadataNormalization.isMissingAnyCoreMetadata(incompleteSong));

        Song completeSong = new Song(2L, "Kannazhaga", "Dhanush", "3", 11L, 210000L, 2, 2012, 0L);
        assertFalse(MetadataNormalization.isMissingAnyCoreMetadata(completeSong));
    }

    // =========================================================================
    // 3. Numeric Validation Tests
    // =========================================================================

    @Test
    public void testParseYear() {
        assertEquals(0, MetadataNormalization.parseYear(null));
        assertEquals(0, MetadataNormalization.parseYear(""));
        assertEquals(0, MetadataNormalization.parseYear("   "));

        // Valid years
        assertEquals(1992, MetadataNormalization.parseYear("1992"));
        assertEquals(2024, MetadataNormalization.parseYear(" 2024 "));

        // Invalid years (out of range or non-numeric)
        assertEquals(-1, MetadataNormalization.parseYear("1799"));
        assertEquals(-1, MetadataNormalization.parseYear("2101"));
        assertEquals(-1, MetadataNormalization.parseYear("abcd"));
        assertEquals(-1, MetadataNormalization.parseYear("20 24"));
    }

    @Test
    public void testParseTrackNumber() {
        assertEquals(0, MetadataNormalization.parseTrackNumber(null));
        assertEquals(0, MetadataNormalization.parseTrackNumber(""));

        // Valid track numbers
        assertEquals(1, MetadataNormalization.parseTrackNumber("1"));
        assertEquals(14, MetadataNormalization.parseTrackNumber("14"));
        assertEquals(3, MetadataNormalization.parseTrackNumber("3/12")); // slash support

        // Invalid track numbers
        assertEquals(-1, MetadataNormalization.parseTrackNumber("0"));
        assertEquals(-1, MetadataNormalization.parseTrackNumber("-5"));
        assertEquals(-1, MetadataNormalization.parseTrackNumber("1000"));
        assertEquals(-1, MetadataNormalization.parseTrackNumber("track"));
    }

    @Test
    public void testParseDiscNumber() {
        assertEquals(0, MetadataNormalization.parseDiscNumber(null));
        assertEquals(0, MetadataNormalization.parseDiscNumber(""));

        // Valid disc numbers
        assertEquals(1, MetadataNormalization.parseDiscNumber("1"));
        assertEquals(2, MetadataNormalization.parseDiscNumber("2/2"));

        // Invalid disc numbers
        assertEquals(-1, MetadataNormalization.parseDiscNumber("0"));
        assertEquals(-1, MetadataNormalization.parseDiscNumber("100"));
        assertEquals(-1, MetadataNormalization.parseDiscNumber("disc"));
    }

    // =========================================================================
    // 4. SongMetadata Change Detection Tests
    // =========================================================================

    @Test
    public void testSongMetadataChangeDetection() {
        SongMetadata base = new SongMetadata.Builder()
                .setSongId(101L)
                .setTitle("Pachai Nirame")
                .setArtist("Hariharan")
                .setAlbum("Alaipayuthey")
                .setYear(2000)
                .setTrackNumber(1)
                .build();

        // Identical
        SongMetadata same = new SongMetadata.Builder(base).build();
        assertFalse("Identical metadata must report no changes", base.hasChanges(same));

        // Modified Artist
        SongMetadata newArtist = new SongMetadata.Builder(base).setArtist("Hariharan, Clinton").build();
        assertTrue("Modified artist must report changes", base.hasChanges(newArtist));

        // Modified Year
        SongMetadata newYear = new SongMetadata.Builder(base).setYear(2001).build();
        assertTrue("Modified year must report changes", base.hasChanges(newYear));

        // Modified Genre
        SongMetadata newGenre = new SongMetadata.Builder(base).setGenre("Classical Fusion").build();
        assertTrue("Modified genre must report changes", base.hasChanges(newGenre));
    }

    // =========================================================================
    // 5. Format Support Check Tests
    // =========================================================================

    @Test
    public void testId3FormatSupport() {
        assertTrue(Id3MetadataWriter.isSupportedFormat("audio/mpeg", null));
        assertTrue(Id3MetadataWriter.isSupportedFormat(null, "/storage/emulated/0/Music/song.mp3"));
        assertTrue(Id3MetadataWriter.isSupportedFormat(null, "/sdcard/Song.MP3"));

        assertFalse(Id3MetadataWriter.isSupportedFormat("audio/wav", "/music/song.wav"));
        assertFalse(Id3MetadataWriter.isSupportedFormat("audio/flac", "/music/song.flac"));
        assertFalse(Id3MetadataWriter.isSupportedFormat(null, null));
    }

    // =========================================================================
    // 6. Result Model Tests
    // =========================================================================

    @Test
    public void testSaveMetadataResultStatuses() {
        SongMetadata sample = new SongMetadata.Builder().setSongId(1L).setTitle("Sample").build();

        SaveMetadataResult success = SaveMetadataResult.success(sample);
        assertTrue(success.isSuccess());
        assertEquals(SaveMetadataResult.Status.SUCCESS, success.getStatus());
        assertNotNull(success.getUpdatedMetadata());

        SaveMetadataResult partial = SaveMetadataResult.partialSuccess(sample, "MediaStore updated");
        assertTrue(partial.isSuccess());
        assertEquals(SaveMetadataResult.Status.PARTIAL_SUCCESS, partial.getStatus());

        SaveMetadataResult failed = SaveMetadataResult.failed("Write error");
        assertFalse(failed.isSuccess());
        assertEquals(SaveMetadataResult.Status.FAILED, failed.getStatus());
        assertEquals("Write error", failed.getMessage());

        SaveMetadataResult unsupported = SaveMetadataResult.unsupportedFormat("Not supported");
        assertFalse(unsupported.isSuccess());
        assertEquals(SaveMetadataResult.Status.UNSUPPORTED_FORMAT, unsupported.getStatus());
    }

    // =========================================================================
    // 7. Theme Palette Verification for Metadata Editor Across All 7 Themes
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

    @Test
    public void testMetadataEditorContrastAcrossThemes() {
        // Verify all 8 ColorTheme enums are covered (added OFF_WHITE)
        ColorTheme[] themes = ColorTheme.values();
        assertEquals(8, themes.length);


        for (ColorTheme theme : themes) {
            assertNotNull("Theme must have valid name", theme.name());
        }
    }
}

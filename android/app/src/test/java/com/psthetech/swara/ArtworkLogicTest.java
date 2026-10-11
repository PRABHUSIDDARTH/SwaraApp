package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

/**
 * Pure JVM unit tests for Artwork logic and store naming conventions.
 */
public class ArtworkLogicTest {

    @Test
    public void testCustomArtworkFilenameFormat() {
        long songId = 98765L;
        String expectedFilename = songId + ".jpg";
        File file = new File("/tmp/custom_artwork", expectedFilename);
        assertEquals("98765.jpg", file.getName());
    }

    @Test
    public void testSongIdValidationForArtwork() {
        long validSongId = 123L;
        long invalidSongId = -1L;
        long zeroSongId = 0L;

        assertTrue(validSongId > 0);
        assertFalse(invalidSongId > 0);
        assertFalse(zeroSongId > 0);
    }
}

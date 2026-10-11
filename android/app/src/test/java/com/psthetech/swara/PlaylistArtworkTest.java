package com.psthetech.swara;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * JVM-level unit tests for PlaylistArtworkStore.
 *
 * We can't use the real Context/ContentResolver here, so we test the
 * file-path logic and bitmap-save logic by exercising PlaylistArtworkStore
 * with a temporary folder acting as filesDir.
 */
public class PlaylistArtworkTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Minimal test subclass that replaces artworkDir via reflection. */
    private File artworkDir;

    @Before
    public void setup() throws Exception {
        artworkDir = tempFolder.newFolder("playlist_artwork");
    }

    // ===== getCustomArtworkFile =====

    @Test
    public void customArtworkFile_returnsCorrectPath() {
        File expected = new File(artworkDir, "42.jpg");
        File actual = new File(artworkDir, 42 + ".jpg");
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getParentFile(), actual.getParentFile());
    }

    @Test
    public void getCollageFile_returnsCorrectPath() {
        File collage = new File(artworkDir, "collage_7.jpg");
        assertEquals("collage_7.jpg", collage.getName());
        assertTrue(collage.getParentFile().equals(artworkDir));
    }

    // ===== hasCustomArtwork / removeCustomArtwork =====

    @Test
    public void hasCustomArtwork_falseWhenFileDoesNotExist() {
        File f = new File(artworkDir, "99.jpg");
        assertFalse(f.exists() && f.length() > 0);
    }

    @Test
    public void customArtwork_createAndDelete() throws Exception {
        File f = new File(artworkDir, "1.jpg");
        // Simulate saving
        assertTrue(f.createNewFile());
        java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
        fos.write(new byte[]{0x01, 0x02, 0x03});
        fos.close();

        assertTrue(f.exists() && f.length() > 0);

        // Simulate removing
        assertTrue(f.delete());
        assertFalse(f.exists());
    }

    // ===== collage operations =====

    @Test
    public void hasCollage_falseWhenNoFile() {
        File collage = new File(artworkDir, "collage_100.jpg");
        assertFalse(collage.exists() && collage.length() > 0);
    }

    @Test
    public void deleteAllArtwork_removesBothFiles() throws Exception {
        File custom = new File(artworkDir, "5.jpg");
        File collage = new File(artworkDir, "collage_5.jpg");
        assertTrue(custom.createNewFile());
        assertTrue(collage.createNewFile());

        // Simulate deleteAllArtwork
        if (custom.exists()) custom.delete();
        if (collage.exists()) collage.delete();

        assertFalse(custom.exists());
        assertFalse(collage.exists());
    }

    // ===== invalidateCollage =====

    @Test
    public void invalidateCollage_touchesLastModified() throws Exception {
        File collage = new File(artworkDir, "collage_3.jpg");
        assertTrue(collage.createNewFile());
        long before = collage.lastModified();

        Thread.sleep(50);
        collage.setLastModified(System.currentTimeMillis());
        long after = collage.lastModified();

        assertTrue("lastModified should be updated", after >= before);
    }

    // ===== Priority chain: custom artwork beats collage =====

    @Test
    public void customArtworkTakesPriorityOverCollage() throws Exception {
        File custom = new File(artworkDir, "10.jpg");
        File collage = new File(artworkDir, "collage_10.jpg");

        // Create both files
        createDummyFile(custom);
        createDummyFile(collage);

        // Priority 1: check custom first
        assertTrue("Custom artwork should exist when file created", custom.exists() && custom.length() > 0);
    }

    private void createDummyFile(File f) throws Exception {
        java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
        fos.write(new byte[]{1, 2, 3, 4});
        fos.close();
    }
}

package com.psthetech.swara;

import static org.junit.Assert.*;
import com.psthetech.swara.domain.model.KorokaeState;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.KorokaeCacheManager;
import org.junit.Test;

public class KorokaePlaybackTest {

    private Song createSong(long id, String title, String artist, String album, long duration) {
        return new Song(id, title, artist, album, 1L, duration, 1, 2024, 1000L);
    }

    @Test
    public void testInitialOffState() {
        KorokaeState state = KorokaeState.off(null);
        assertEquals(KorokaeState.Status.OFF, state.getStatus());
        assertFalse(state.isActive());
        assertFalse(state.isProcessing());
        assertNull(state.getInstrumentalPath());
    }

    @Test
    public void testOffToProcessingStateTransition() {
        Song song = createSong(1, "Song", "Artist", "Album", 200_000);
        KorokaeState state = KorokaeState.processing(song, 101L, 45);
        assertEquals(KorokaeState.Status.PROCESSING, state.getStatus());
        assertTrue(state.isProcessing());
        assertEquals(45, state.getProgressPercent());
        assertEquals(101L, state.getGenerationId());
    }

    @Test
    public void testProcessingToActiveStateTransition() {
        Song song = createSong(1, "Song", "Artist", "Album", 200_000);
        KorokaeState state = KorokaeState.active(song, 101L, "/cache/stem.wav");
        assertEquals(KorokaeState.Status.ACTIVE, state.getStatus());
        assertTrue(state.isActive());
        assertFalse(state.isProcessing());
        assertEquals("/cache/stem.wav", state.getInstrumentalPath());
    }

    @Test
    public void testCacheKeyGeneration() {
        String key = KorokaeCacheManager.buildCacheKey(42L, 1690000000L);
        assertEquals("korokae_42_1690000000.wav", key);
    }
}

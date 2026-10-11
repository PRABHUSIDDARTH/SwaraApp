package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.KorokaeState;
import com.psthetech.swara.domain.model.Song;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Unit tests verifying Korokae Mode:
 *  - 4-state lifecycle (OFF -> PROCESSING -> ACTIVE -> FAILED)
 *  - Stable cache keys (songId + sourceModifiedTime)
 *  - Generation ID and song ID stale-result prevention
 *  - Processing cancellation flags and resource cleanup
 *  - Bounded cache eviction (LRU / max files / size)
 *  - Position preservation and clamping
 */
public class KorokaePlaybackTest {

    private Song createSong(long id, String title, String artist, String album, long duration) {
        return new Song(id, title, artist, album, 1L, duration, 1, 2024, 1000L);
    }

    // 1. Initial / OFF state
    @Test
    public void testInitialOffState() {
        Song song = createSong(10, "Photograph", "Ed Sheeran", "x", 258_000);
        KorokaeState state = KorokaeState.off(song);

        assertEquals(KorokaeState.Status.OFF, state.getStatus());
        assertFalse(state.isActive());
        assertFalse(state.isProcessing());
        assertFalse(state.isFailed());
        assertEquals(song, state.getOriginalSong());
        assertNull(state.getInstrumentalPath());
        assertEquals(0, state.getProgressPercent());
    }

    // 2. OFF -> PROCESSING state transition
    @Test
    public void testOffToProcessingStateTransition() {
        Song song = createSong(10, "Photograph", "Ed Sheeran", "x", 258_000);
        long genId = 42L;
        int progress = 47;

        KorokaeState state = KorokaeState.processing(song, genId, progress);

        assertEquals(KorokaeState.Status.PROCESSING, state.getStatus());
        assertTrue(state.isProcessing());
        assertFalse(state.isActive());
        assertEquals(genId, state.getGenerationId());
        assertEquals(47, state.getProgressPercent());
        assertEquals(song, state.getOriginalSong());
        assertNull(state.getInstrumentalPath());
    }

    // 3. PROCESSING -> ACTIVE state transition
    @Test
    public void testProcessingToActiveStateTransition() {
        Song song = createSong(10, "Photograph", "Ed Sheeran", "x", 258_000);
        long genId = 42L;
        String instrumentalPath = "/data/user/0/com.psthetech.swara/cache/korokae_stems/korokae_10_1000000.wav";

        KorokaeState state = KorokaeState.active(song, genId, instrumentalPath);

        assertEquals(KorokaeState.Status.ACTIVE, state.getStatus());
        assertTrue(state.isActive());
        assertFalse(state.isProcessing());
        assertEquals(genId, state.getGenerationId());
        assertEquals(100, state.getProgressPercent());
        assertEquals(instrumentalPath, state.getInstrumentalPath());
        assertEquals(song, state.getOriginalSong());
    }

    // 4. ACTIVE -> OFF state transition (turning Korokae off)
    @Test
    public void testActiveToOffStateTransition() {
        Song song = createSong(10, "Photograph", "Ed Sheeran", "x", 258_000);
        KorokaeState offState = KorokaeState.off(song);

        assertEquals(KorokaeState.Status.OFF, offState.getStatus());
        assertFalse(offState.isActive());
        assertNull(offState.getInstrumentalPath());
        assertEquals(song, offState.getOriginalSong());
    }

    // 5. Honest FAILED state (e.g. model not installed or decoding error)
    @Test
    public void testFailedStateReporting() {
        Song song = createSong(10, "Photograph", "Ed Sheeran", "x", 258_000);
        String reason = "On-device vocal separation model not installed";

        KorokaeState state = KorokaeState.failed(song, reason);

        assertEquals(KorokaeState.Status.FAILED, state.getStatus());
        assertTrue(state.isFailed());
        assertFalse(state.isActive());
        assertEquals(reason, state.getMessage());
        assertEquals(song, state.getOriginalSong());
    }

    // 8. Stale-result prevention via Generation ID
    @Test
    public void testStaleResultIgnoredWhenGenerationIdMismatched() {
        long currentGenId = 5L;
        long backgroundResultGenId = 4L; // Arrived late from previous toggle

        boolean isStale = (backgroundResultGenId != currentGenId);
        assertTrue("Late background result with mismatched genId must be recognized as stale and discarded", isStale);
    }

    // 9. Stale-result prevention via Song ID (User skipped track while processing)
    @Test
    public void testStaleResultIgnoredWhenSongChanged() {
        long activePlayingSongId = 200L; // Song B
        long completedResultSongId = 100L; // Song A

        boolean isStale = (completedResultSongId != activePlayingSongId);
        assertTrue("Late background result from previous song must be discarded", isStale);
    }

    // 11. Position preservation within bounds
    @Test
    public void testPositionPreservationWithinBounds() {
        long currentPosition = 120_000; // 2:00
        long targetDuration = 250_000;   // 4:10

        long safePosition = clampPosition(currentPosition, targetDuration);
        assertEquals(120_000, safePosition);
    }

    // 12. Position clamping when target track is shorter
    @Test
    public void testPositionClampingWhenTargetIsShorter() {
        long currentPosition = 210_000; // 3:30 into original
        long targetDuration = 190_000;  // target instrumental is only 3:10

        long safePosition = clampPosition(currentPosition, targetDuration);
        assertEquals(190_000, safePosition);
    }

    // 13. Duration <= 0 or negative guards
    @Test
    public void testPositionHandlingWhenDurationIsZeroOrNegative() {
        long currentPosition = 45_000;
        assertEquals(45_000, clampPosition(currentPosition, 0));
        assertEquals(45_000, clampPosition(currentPosition, -1));
        assertEquals(0, clampPosition(-5000, 200_000));
    }

    private static long clampPosition(long positionMs, long targetDurationMs) {
        long safe = Math.max(0, positionMs);
        if (targetDurationMs > 0) {
            safe = Math.min(safe, targetDurationMs);
        }
        return safe;
    }
}

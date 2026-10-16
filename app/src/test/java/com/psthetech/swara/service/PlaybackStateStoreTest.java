package com.psthetech.swara.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PlaybackStateStoreTest {

    @Test
    public void idsRoundTrip() {
        long[] ids = {12L, 7L, 900000000001L};
        assertArrayEquals(ids, PlaybackStateStore.decodeIds(PlaybackStateStore.encodeIds(ids)));
    }

    @Test
    public void emptyAndNullDecodeToEmpty() {
        assertEquals(0, PlaybackStateStore.decodeIds(null).length);
        assertEquals(0, PlaybackStateStore.decodeIds("").length);
    }

    @Test
    public void malformedAndInvalidEntriesAreSkipped() {
        assertArrayEquals(new long[]{3L, 5L}, PlaybackStateStore.decodeIds("3,abc,-1,0, 5"));
    }

    @Test
    public void invalidIdsAreNotEncoded() {
        assertEquals("4", PlaybackStateStore.encodeIds(new long[]{-1L, 0L, 4L}));
    }

    @Test
    public void stateStore_roundTripsVibeState() {
        assertNull(PlaybackStateStore.decodeVibe(PlaybackStateStore.encodeVibe(null)));
        String vibe = "v1;0.2;0.3;4;0.5;0";
        assertEquals(vibe, PlaybackStateStore.decodeVibe(PlaybackStateStore.encodeVibe(vibe)));
        assertNull(PlaybackStateStore.decodeVibe(null));
        assertNull(PlaybackStateStore.decodeVibe(""));
    }
}

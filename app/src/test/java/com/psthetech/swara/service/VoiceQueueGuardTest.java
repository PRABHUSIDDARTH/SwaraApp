package com.psthetech.swara.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class VoiceQueueGuardTest {
    private VoiceQueueGuard guard;

    @Before public void setUp() {
        guard = new VoiceQueueGuard();
        guard.activate(new long[]{1, 2, 3});
    }

    @Test public void activeAfterActivate() { assertTrue(guard.isActive()); }

    @Test public void reorderOrRemoveKeepsActive() {
        guard.onPlaylistChanged(Arrays.asList(3L, 1L));
        assertTrue(guard.isActive());
    }

    @Test public void manualSongClears() {
        guard.onPlaylistChanged(Arrays.asList(1L, 2L, 3L, 99L));
        assertFalse(guard.isActive());
    }

    @Test public void newQueueClears() {
        guard.onPlaylistChanged(Arrays.asList(7L, 8L));
        assertFalse(guard.isActive());
    }

    @Test public void emptyPlaylistClears() {
        guard.onPlaylistChanged(Collections.<Long>emptyList());
        assertFalse(guard.isActive());
    }
}

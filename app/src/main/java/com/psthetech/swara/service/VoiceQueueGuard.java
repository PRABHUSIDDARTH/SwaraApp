package com.psthetech.swara.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks a queue started by a voice command (artist/album). While it is active, Vibe Mode and
 * Autoplay stay off; the guard clears as soon as the playlist contains anything outside the
 * activated set (the user played something else) or becomes empty.
 */
final class VoiceQueueGuard {
    private final Set<Long> ids = new HashSet<>();
    private boolean active;

    void activate(long[] songIds) {
        ids.clear();
        if (songIds != null) for (long id : songIds) ids.add(id);
        active = !ids.isEmpty();
    }

    boolean isActive() { return active; }

    void onPlaylistChanged(List<Long> currentIds) {
        if (!active) return;
        if (currentIds == null || currentIds.isEmpty()) { clear(); return; }
        for (Long id : currentIds) {
            if (id == null || !ids.contains(id)) { clear(); return; }
        }
    }

    void clear() {
        ids.clear();
        active = false;
    }
}

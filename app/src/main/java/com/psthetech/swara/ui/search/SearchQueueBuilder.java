package com.psthetech.swara.ui.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the playback queue used when a song is tapped from search results.
 *
 * The queue is the full library in the user's chosen Songs order (sort or custom
 * drag order), starting at the tapped song, so playback continues with the next
 * songs instead of stopping after one track.
 */
public final class SearchQueueBuilder {

    /** Immutable queue + start index pair. */
    public static final class Queue {
        @NonNull public final List<Song> songs;
        public final int startIndex;

        Queue(@NonNull List<Song> songs, int startIndex) {
            this.songs = songs;
            this.startIndex = startIndex;
        }
    }

    private SearchQueueBuilder() {}

    @NonNull
    public static Queue build(@Nullable List<Song> library, @NonNull Song tapped) {
        if (library == null || library.isEmpty()) {
            return new Queue(Collections.singletonList(tapped), 0);
        }
        int index = indexOfId(library, tapped.getId());
        if (index >= 0) {
            return new Queue(new ArrayList<>(library), index);
        }
        // Song not present in the library list (e.g. stale cache): play it first,
        // then continue with the library in order.
        List<Song> queue = new ArrayList<>(library.size() + 1);
        queue.add(tapped);
        queue.addAll(library);
        return new Queue(queue, 0);
    }

    static int indexOfId(@NonNull List<Song> songs, long id) {
        for (int i = 0; i < songs.size(); i++) {
            Song s = songs.get(i);
            if (s != null && s.getId() == id) return i;
        }
        return -1;
    }
}

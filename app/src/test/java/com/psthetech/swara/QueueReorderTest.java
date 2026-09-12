package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.domain.model.Song;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueueReorderTest {

    private List<Song> queue;

    @Before
    public void setUp() {
        queue = new ArrayList<>();
        queue.add(new Song(1L, "Song 1", "Artist", "Album", 180000L, 1L, 2024, 0, 1000L));
        queue.add(new Song(2L, "Song 2", "Artist", "Album", 200000L, 1L, 2024, 0, 1000L));
        queue.add(new Song(3L, "Song 3", "Artist", "Album", 220000L, 1L, 2024, 0, 1000L));
    }

    @Test
    public void testMoveItemForward() {
        // Move Song 1 (index 0) to index 2
        moveQueueItem(0, 2);
        assertEquals("Song 2", queue.get(0).getTitle());
        assertEquals("Song 3", queue.get(1).getTitle());
        assertEquals("Song 1", queue.get(2).getTitle());
    }

    @Test
    public void testMoveItemBackward() {
        // Move Song 3 (index 2) to index 0
        moveQueueItem(2, 0);
        assertEquals("Song 3", queue.get(0).getTitle());
        assertEquals("Song 1", queue.get(1).getTitle());
        assertEquals("Song 2", queue.get(2).getTitle());
    }

    private void moveQueueItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= queue.size()
                || toPosition < 0 || toPosition >= queue.size()) {
            return;
        }
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(queue, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(queue, i, i - 1);
            }
        }
    }
}

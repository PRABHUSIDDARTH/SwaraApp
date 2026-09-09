package com.psthetech.swara;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.psthetech.swara.data.repository.QueueManager;
import com.psthetech.swara.domain.model.Song;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QueueManagerTest {

    private FakeQueueController controller;
    private QueueManager queueManager;
    private QueueManager.MediaItemMapper mapper;

    @Before
    public void setup() {
        controller = new FakeQueueController();
        mapper = song -> new MediaItem.Builder().setMediaId(String.valueOf(song.getId())).build();
        queueManager = new QueueManager(controller, mapper);
    }

    private Song createSong(long id) {
        return new Song(id, "Title" + id, "Artist", "Album", id, 1000, 1, 2024, 0);
    }

    @Test
    public void testPlay_ReplacesQueue() {
        List<Song> songs = Arrays.asList(createSong(1), createSong(2));
        queueManager.play(songs, 1);

        assertEquals(2, controller.items.size());
        assertEquals(1, controller.startIndex);
        assertTrue(controller.isPrepared);
        assertTrue(controller.isPlaying);
    }

    @Test
    public void testPlayNext_EmptyQueue() {
        queueManager.playNext(createSong(1));
        assertEquals(1, controller.items.size());
        assertEquals("1", controller.items.get(0).mediaId);
    }

    @Test
    public void testPlayNext_ExistingQueue() {
        controller.items.add(new MediaItem.Builder().setMediaId("1").build());
        controller.items.add(new MediaItem.Builder().setMediaId("2").build());
        controller.currentMediaItemIndex = 0;

        queueManager.playNext(createSong(3));

        assertEquals(3, controller.items.size());
        // Inserted at index 1
        assertEquals("3", controller.items.get(1).mediaId);
    }

    @Test
    public void testAddToQueue() {
        queueManager.addToQueue(createSong(1));
        queueManager.addToQueue(createSong(2));

        assertEquals(2, controller.items.size());
        assertEquals("1", controller.items.get(0).mediaId);
        assertEquals("2", controller.items.get(1).mediaId);
    }

    @Test
    public void testRemoveFromQueue() {
        controller.items.add(new MediaItem.Builder().setMediaId("1").build());
        controller.items.add(new MediaItem.Builder().setMediaId("2").build());
        queueManager.removeFromQueue(0);

        assertEquals(1, controller.items.size());
        assertEquals("2", controller.items.get(0).mediaId);
    }

    @Test
    public void testClearQueue() {
        controller.items.add(new MediaItem.Builder().setMediaId("1").build());
        controller.isPlaying = true;

        queueManager.clearQueue();

        assertTrue(controller.items.isEmpty());
        assertTrue(controller.isStopped);
    }

    @Test
    public void testToggleShuffle() {
        assertFalse(controller.shuffleModeEnabled);
        queueManager.toggleShuffle();
        assertTrue(controller.shuffleModeEnabled);
        queueManager.toggleShuffle();
        assertFalse(controller.shuffleModeEnabled);
    }

    @Test
    public void testCycleRepeatMode() {
        assertEquals(Player.REPEAT_MODE_OFF, controller.repeatMode);
        queueManager.cycleRepeatMode();
        assertEquals(Player.REPEAT_MODE_ONE, controller.repeatMode);
        queueManager.cycleRepeatMode();
        assertEquals(Player.REPEAT_MODE_ALL, controller.repeatMode);
        queueManager.cycleRepeatMode();
        assertEquals(Player.REPEAT_MODE_OFF, controller.repeatMode);
    }

    private static class FakeQueueController implements QueueManager.QueueController {
        public List<MediaItem> items = new ArrayList<>();
        public int startIndex = -1;
        public long startPositionMs = -1;
        public boolean isPrepared = false;
        public boolean isPlaying = false;
        public boolean isStopped = false;
        public boolean shuffleModeEnabled = false;
        public int repeatMode = Player.REPEAT_MODE_OFF;
        public int currentMediaItemIndex = -1;
        public long currentPosition = 0;

        @Override
        public void setMediaItems(List<MediaItem> items, int startIndex, long startPositionMs) {
            this.items = new ArrayList<>(items);
            this.startIndex = startIndex;
            this.startPositionMs = startPositionMs;
        }

        @Override public void prepare() { isPrepared = true; }
        @Override public void play() { isPlaying = true; isStopped = false; }
        @Override public void pause() { isPlaying = false; }
        @Override public void addMediaItem(MediaItem item) { items.add(item); }
        @Override public void addMediaItem(int index, MediaItem item) { items.add(index, item); }
        @Override public void removeMediaItem(int index) { items.remove(index); }
        @Override public void moveMediaItem(int currentIndex, int newIndex) {
            MediaItem item = items.remove(currentIndex);
            items.add(newIndex, item);
        }
        @Override public void clearMediaItems() { items.clear(); }
        @Override public void stop() { isStopped = true; isPlaying = false; }
        @Override public void seekTo(long positionMs) { currentPosition = positionMs; }
        @Override public void seekTo(int mediaItemIndex, long positionMs) {
            currentMediaItemIndex = mediaItemIndex;
            currentPosition = positionMs;
        }
        @Override public void seekToNextMediaItem() { currentMediaItemIndex++; }
        @Override public void seekToPreviousMediaItem() { currentMediaItemIndex--; }
        @Override public int getCurrentMediaItemIndex() { return currentMediaItemIndex; }
        @Override public long getCurrentPosition() { return currentPosition; }
        @Override public boolean getShuffleModeEnabled() { return shuffleModeEnabled; }
        @Override public void setShuffleModeEnabled(boolean shuffleModeEnabled) { this.shuffleModeEnabled = shuffleModeEnabled; }
        @Override public int getRepeatMode() { return repeatMode; }
        @Override public void setRepeatMode(int repeatMode) { this.repeatMode = repeatMode; }
        @Override public boolean hasNextMediaItem() { return currentMediaItemIndex < items.size() - 1; }
    }
}

package com.psthetech.swara.data.repository;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages playback queue operations.
 *
 * Does NOT maintain an independent playback state. It delegates all operations to
 * the provided QueueController (which wraps MediaController in production),
 * ensuring ExoPlayer/MediaSession remains the single source of truth.
 */
public class QueueManager {

    public interface QueueController {
        void setMediaItems(List<MediaItem> items, int startIndex, long startPositionMs);
        void prepare();
        void play();
        void pause();
        void addMediaItem(MediaItem item);
        void addMediaItem(int index, MediaItem item);
        void removeMediaItem(int index);
        void moveMediaItem(int currentIndex, int newIndex);
        void clearMediaItems();
        void stop();
        void seekTo(long positionMs);
        void seekTo(int mediaItemIndex, long positionMs);
        void seekToNextMediaItem();
        void seekToPreviousMediaItem();

        int getCurrentMediaItemIndex();
        long getCurrentPosition();
        boolean getShuffleModeEnabled();
        void setShuffleModeEnabled(boolean shuffleModeEnabled);
        int getRepeatMode();
        void setRepeatMode(int repeatMode);
        boolean hasNextMediaItem();
    }

    public interface MediaItemMapper {
        MediaItem toMediaItem(Song song);
    }

    private final QueueController controller;
    private final MediaItemMapper mapper;

    public QueueManager(QueueController controller, MediaItemMapper mapper) {
        this.controller = controller;
        this.mapper = mapper;
    }

    public void play(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) return;
        List<MediaItem> items = new ArrayList<>();
        for (Song song : songs) {
            items.add(mapper.toMediaItem(song));
        }
        controller.setMediaItems(items, startIndex, 0);
        controller.prepare();
        controller.play();
    }

    public void playNext(Song song) {
        if (song == null) return;
        int currentIndex = controller.getCurrentMediaItemIndex();
        int insertIndex = currentIndex < 0 ? 0 : currentIndex + 1;
        controller.addMediaItem(insertIndex, mapper.toMediaItem(song));
    }

    public void addToQueue(Song song) {
        if (song == null) return;
        controller.addMediaItem(mapper.toMediaItem(song));
    }

    public void addAllToQueue(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        for (Song song : songs) {
            controller.addMediaItem(mapper.toMediaItem(song));
        }
    }

    public void pause() {
        controller.pause();
    }

    public void resume() {
        controller.play();
    }

    public void seekTo(long positionMs) {
        controller.seekTo(positionMs);
    }

    public void skipToNext() {
        controller.seekToNextMediaItem();
    }

    public void skipToPrevious() {
        if (controller.getCurrentPosition() > 3000) {
            controller.seekTo(0);
        } else {
            controller.seekToPreviousMediaItem();
        }
    }

    public void toggleShuffle() {
        controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
    }

    public void cycleRepeatMode() {
        int current = controller.getRepeatMode();
        int next;
        switch (current) {
            case Player.REPEAT_MODE_OFF: next = Player.REPEAT_MODE_ONE; break;
            case Player.REPEAT_MODE_ONE: next = Player.REPEAT_MODE_ALL; break;
            default: next = Player.REPEAT_MODE_OFF; break;
        }
        controller.setRepeatMode(next);
    }

    public void removeFromQueue(int index) {
        if (index >= 0) {
            controller.removeMediaItem(index);
        }
    }

    public void moveQueueItem(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && toIndex >= 0) {
            controller.moveMediaItem(fromIndex, toIndex);
        }
    }

    public void clearQueue() {
        controller.clearMediaItems();
        controller.stop();
    }

    public void skipToQueueItem(int index) {
        if (index >= 0) {
            controller.seekTo(index, 0);
            controller.play();
        }
    }
}

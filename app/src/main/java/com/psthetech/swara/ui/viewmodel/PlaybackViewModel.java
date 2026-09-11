package com.psthetech.swara.ui.viewmodel;

import android.app.Application;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.data.repository.FavoritesRepository;
import com.psthetech.swara.data.repository.PlayHistoryRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.service.SwaraPlaybackService;
import com.psthetech.swara.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * PlaybackViewModel — the UI-facing playback state authority.
 *
 * Architecture:
 *  - ExoPlayer (in SwaraPlaybackService) is the actual state source
 *  - MediaController connects to MediaSession and implements Player
 *  - Player.Listener callbacks update MutableLiveData fields
 *  - All fragments observe this ViewModel (Activity-scoped)
 *  - This ViewModel survives configuration changes (rotation)
 *  - Controller is released in onCleared() when Activity is permanently destroyed
 *
 * This ViewModel does NOT compete with ExoPlayer as a state authority —
 * it is simply a bridge that converts Player.Listener events into LiveData.
 */
public class PlaybackViewModel extends AndroidViewModel {

    private static final String TAG = "PlaybackViewModel";

    // ===== Live Data (read-only public API) =====
    private final MutableLiveData<Song> currentSong = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Long> currentPositionMs = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> durationMs = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> shuffleEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> repeatMode = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<List<Song>> currentQueue = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> currentQueueIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    // ===== Internal =====
    private ListenableFuture<MediaController> controllerFuture;
    @Nullable private MediaController controller;
    @Nullable private com.psthetech.swara.data.repository.QueueManager queueManager;
    private final Handler positionHandler = new Handler(Looper.getMainLooper());
    private final List<Song> queueSnapshot = new ArrayList<>(); // mirrors ExoPlayer queue

    // Repositories for recording history and favorites
    private final PlayHistoryRepository historyRepository;
    private final FavoritesRepository favoritesRepository;

    // Position tracker runnable
    private final Runnable positionUpdater = new Runnable() {
        @Override
        public void run() {
            if (controller != null && controller.isPlaying()) {
                currentPositionMs.setValue(controller.getCurrentPosition());
            }
            positionHandler.postDelayed(this, 500);
        }
    };

    public PlaybackViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        historyRepository = new PlayHistoryRepository(db);
        favoritesRepository = new FavoritesRepository(db);
        connectToService();
        positionHandler.post(positionUpdater);
    }

    // ===== Service Connection =====

    private void connectToService() {
        SessionToken sessionToken = new SessionToken(
                getApplication(),
                new ComponentName(getApplication(), SwaraPlaybackService.class)
        );
        controllerFuture = new MediaController.Builder(getApplication(), sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);

                queueManager = new com.psthetech.swara.data.repository.QueueManager(
                        new MediaControllerQueueAdapter(controller),
                        this::songToMediaItem
                );

                // Sync initial state in case service was already playing
                syncStateFromController();
                Log.d(TAG, "MediaController connected to SwaraPlaybackService");
            } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to connect MediaController", e);
                errorMessage.postValue("Failed to connect to playback service");
            }
        }, MoreExecutors.directExecutor());
    }

    private void syncStateFromController() {
        if (controller == null) return;
        isPlaying.postValue(controller.isPlaying());
        shuffleEnabled.postValue(controller.getShuffleModeEnabled());
        repeatMode.postValue(controller.getRepeatMode());
        currentPositionMs.postValue(controller.getCurrentPosition());
        durationMs.postValue(controller.getDuration() == androidx.media3.common.C.TIME_UNSET
                ? 0L : controller.getDuration());
        updateCurrentSongFromController();
    }

    private void updateCurrentSongFromController() {
        if (controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null) {
            Song song = mediaItemToSong(item);
            currentSong.postValue(song);
        } else {
            currentSong.postValue(null);
        }
        currentQueueIndex.postValue(controller.getCurrentMediaItemIndex());
    }

    // ===== Player.Listener =====

    private final Player.Listener playerListener = new Player.Listener() {

        @Override
        public void onIsPlayingChanged(boolean playing) {
            isPlaying.postValue(playing);
            if (!playing) {
                // Record meaningful play when playback stops (user paused or song ended)
                Song song = currentSong.getValue();
                if (song != null && controller != null) {
                    historyRepository.maybeRecordPlay(song, controller.getCurrentPosition());
                }
            }
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            updateCurrentSongFromController();
            if (controller != null) {
                durationMs.postValue(controller.getDuration() == androidx.media3.common.C.TIME_UNSET
                        ? 0L : controller.getDuration());
                currentPositionMs.postValue(0L);
            }
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleEnabled.postValue(shuffleModeEnabled);
        }

        @Override
        public void onRepeatModeChanged(int mode) {
            repeatMode.postValue(mode);
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY && controller != null) {
                durationMs.postValue(controller.getDuration() == androidx.media3.common.C.TIME_UNSET
                        ? 0L : controller.getDuration());
            }
        }

        @Override
        public void onTimelineChanged(androidx.media3.common.Timeline timeline, int reason) {
            // Rebuild queue snapshot from ExoPlayer's timeline
            rebuildQueueSnapshot();
        }

        @Override
        public void onPlayerError(androidx.media3.common.PlaybackException error) {
            Log.e(TAG, "Playback error: " + error.getMessage());
            errorMessage.postValue("Playback error: " + error.getMessage());
            // Try to advance to next track
            if (controller != null && controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem();
                controller.play();
            }
        }
    };

    // ===== Public Playback Commands =====
    // Delegated to QueueManager

    public void play(List<Song> songs, int startIndex) {
        if (queueManager != null) queueManager.play(songs, startIndex);
    }

    public void playNext(Song song) {
        if (queueManager != null) queueManager.playNext(song);
    }

    public void addToQueue(Song song) {
        if (queueManager != null) queueManager.addToQueue(song);
    }

    public void addAllToQueue(List<Song> songs) {
        if (queueManager != null) queueManager.addAllToQueue(songs);
    }

    public void pause() {
        if (queueManager != null) queueManager.pause();
    }

    public void resume() {
        if (queueManager != null) queueManager.resume();
    }

    public void seekTo(long positionMs) {
        if (queueManager != null) {
            queueManager.seekTo(positionMs);
            currentPositionMs.setValue(positionMs);
        }
    }

    public void skipToNext() {
        if (queueManager != null) queueManager.skipToNext();
    }

    public void skipToPrevious() {
        if (queueManager != null) queueManager.skipToPrevious();
    }

    public void toggleShuffle() {
        if (queueManager != null) queueManager.toggleShuffle();
    }

    public void cycleRepeatMode() {
        if (queueManager != null) queueManager.cycleRepeatMode();
    }

    public void removeFromQueue(int index) {
        if (queueManager != null) queueManager.removeFromQueue(index);
    }

    public void moveQueueItem(int fromIndex, int toIndex) {
        if (queueManager != null) queueManager.moveQueueItem(fromIndex, toIndex);
    }

    public void clearQueue() {
        if (queueManager != null) queueManager.clearQueue();
    }

    public void skipToQueueItem(int index) {
        if (queueManager != null) queueManager.skipToQueueItem(index);
    }

    // ===== Convenience Aliases for Fragments =====

    public void playSongs(List<Song> songs, int position) {
        play(songs, position);
    }

    public void playSong(Song song) {
        if (song != null) {
            List<Song> list = new ArrayList<>();
            list.add(song);
            play(list, 0);
        }
    }

    public void togglePlayPause() {
        if (Boolean.TRUE.equals(isPlaying.getValue())) {
            pause();
        } else {
            resume();
        }
    }

    public void toggleRepeatMode() {
        cycleRepeatMode();
    }

    public LiveData<Long> getCurrentPosition() {
        return getCurrentPositionMs();
    }

    public LiveData<Boolean> getShuffleMode() {
        return getShuffleEnabled();
    }

    public LiveData<List<Song>> getQueue() {
        return getCurrentQueue();
    }

    // ===== LiveData getters =====

    public LiveData<Song> getCurrentSong() { return currentSong; }
    public LiveData<Boolean> getIsPlaying() { return isPlaying; }
    public LiveData<Long> getCurrentPositionMs() { return currentPositionMs; }
    public LiveData<Long> getDurationMs() { return durationMs; }
    public LiveData<Boolean> getShuffleEnabled() { return shuffleEnabled; }
    public LiveData<Integer> getRepeatMode() { return repeatMode; }
    public LiveData<List<Song>> getCurrentQueue() { return currentQueue; }
    public LiveData<Integer> getCurrentQueueIndex() { return currentQueueIndex; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void refreshCurrentSongArtwork() {
        Song song = currentSong.getValue();
        if (song != null) {
            currentSong.postValue(song);
        }
    }

    // ===== Helpers =====

    private MediaItem songToMediaItem(Song song) {
        return new MediaItem.Builder()
                .setMediaId(String.valueOf(song.getId()))
                .setUri(ArtworkRepository.getSongUri(song.getId()))
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(song.getTitle())
                        .setArtist(song.getArtist())
                        .setAlbumTitle(song.getAlbum())
                        .setArtworkUri(new ArtworkRepository(getApplication()).getArtworkUri(song))
                        .build())
                .build();
    }

    private List<MediaItem> songsToMediaItems(List<Song> songs) {
        List<MediaItem> items = new ArrayList<>();
        for (Song song : songs) items.add(songToMediaItem(song));
        return items;
    }

    private static class MediaControllerQueueAdapter implements com.psthetech.swara.data.repository.QueueManager.QueueController {
        private final MediaController controller;

        MediaControllerQueueAdapter(MediaController controller) {
            this.controller = controller;
        }

        @Override
        public void setMediaItems(List<MediaItem> items, int startIndex, long startPositionMs) {
            controller.setMediaItems(items, startIndex, startPositionMs);
        }

        @Override
        public void prepare() {
            controller.prepare();
        }

        @Override
        public void play() {
            controller.play();
        }

        @Override
        public void pause() {
            controller.pause();
        }

        @Override
        public void addMediaItem(MediaItem item) {
            controller.addMediaItem(item);
        }

        @Override
        public void addMediaItem(int index, MediaItem item) {
            controller.addMediaItem(index, item);
        }

        @Override
        public void removeMediaItem(int index) {
            controller.removeMediaItem(index);
        }

        @Override
        public void moveMediaItem(int currentIndex, int newIndex) {
            controller.moveMediaItem(currentIndex, newIndex);
        }

        @Override
        public void clearMediaItems() {
            controller.clearMediaItems();
        }

        @Override
        public void stop() {
            controller.stop();
        }

        @Override
        public void seekTo(long positionMs) {
            controller.seekTo(positionMs);
        }

        @Override
        public void seekTo(int mediaItemIndex, long positionMs) {
            controller.seekTo(mediaItemIndex, positionMs);
        }

        @Override
        public void seekToNextMediaItem() {
            controller.seekToNextMediaItem();
        }

        @Override
        public void seekToPreviousMediaItem() {
            controller.seekToPreviousMediaItem();
        }

        @Override
        public int getCurrentMediaItemIndex() {
            return controller.getCurrentMediaItemIndex();
        }

        @Override
        public long getCurrentPosition() {
            return controller.getCurrentPosition();
        }

        @Override
        public boolean getShuffleModeEnabled() {
            return controller.getShuffleModeEnabled();
        }

        @Override
        public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
            controller.setShuffleModeEnabled(shuffleModeEnabled);
        }

        @Override
        public int getRepeatMode() {
            return controller.getRepeatMode();
        }

        @Override
        public void setRepeatMode(int repeatMode) {
            controller.setRepeatMode(repeatMode);
        }

        @Override
        public boolean hasNextMediaItem() {
            return controller.hasNextMediaItem();
        }
    }

    private Song mediaItemToSong(MediaItem item) {
        MediaMetadata meta = item.mediaMetadata;
        long id = 0;
        try { id = Long.parseLong(item.mediaId); } catch (NumberFormatException ignored) {}
        return new Song(
                id,
                meta.title != null ? meta.title.toString() : "Unknown",
                meta.artist != null ? meta.artist.toString() : "Unknown",
                meta.albumTitle != null ? meta.albumTitle.toString() : "Unknown",
                0, 0, 0, 0, 0
        );
    }

    private void rebuildQueueSnapshot() {
        if (controller == null) return;
        queueSnapshot.clear();
        int count = controller.getMediaItemCount();
        for (int i = 0; i < count; i++) {
            queueSnapshot.add(mediaItemToSong(controller.getMediaItemAt(i)));
        }
        currentQueue.postValue(new ArrayList<>(queueSnapshot));
        currentQueueIndex.postValue(controller.getCurrentMediaItemIndex());
    }

    @Override
    protected void onCleared() {
        positionHandler.removeCallbacks(positionUpdater);
        if (controller != null) {
            controller.removeListener(playerListener);
        }
        MediaController.releaseFuture(controllerFuture);
        super.onCleared();
    }
}

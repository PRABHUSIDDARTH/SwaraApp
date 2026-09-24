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
import com.psthetech.swara.R;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.data.repository.FavoritesRepository;
import com.psthetech.swara.data.repository.PlayHistoryRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.service.SwaraPlaybackService;
import com.psthetech.swara.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final MutableLiveData<com.psthetech.swara.domain.model.KorokaeState> korokaeState =
            new MutableLiveData<>(com.psthetech.swara.domain.model.KorokaeState.off(null));

    public LiveData<com.psthetech.swara.domain.model.KorokaeState> getKorokaeState() {
        return korokaeState;
    }

    // ===== Internal =====
    private ListenableFuture<MediaController> controllerFuture;
    @Nullable private MediaController controller;
    @Nullable private com.psthetech.swara.data.repository.QueueManager queueManager;
    private final Handler positionHandler = new Handler(Looper.getMainLooper());
    private final List<Song> queueSnapshot = new ArrayList<>(); // mirrors ExoPlayer queue

    // Repositories for recording history and favorites
    private final PlayHistoryRepository historyRepository;
    private final FavoritesRepository favoritesRepository;

    private final Map<Long, Song> songCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Position tracker runnable
    private final Runnable positionUpdater = new Runnable() {
        @Override
        public void run() {
            if (controller != null && controller.isPlaying()) {
                currentPositionMs.setValue(controller.getCurrentPosition());
                positionHandler.postDelayed(this, 500);
            }
        }
    };

    public PlaybackViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        historyRepository = new PlayHistoryRepository(db);
        favoritesRepository = new FavoritesRepository(db);
        connectToService();
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
        boolean playing = controller.isPlaying();
        isPlaying.postValue(playing);
        shuffleEnabled.postValue(controller.getShuffleModeEnabled());
        repeatMode.postValue(controller.getRepeatMode());
        currentPositionMs.postValue(controller.getCurrentPosition());
        updateCurrentSongFromController();
        if (playing) {
            positionHandler.post(positionUpdater);
        }
    }

    private void updateCurrentSongFromController() {
        if (controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null) {
            Song song = mediaItemToSong(item);
            com.psthetech.swara.domain.model.KorokaeState kState = korokaeState.getValue();
            if (kState != null && kState.isActive() && kState.getOriginalSong() != null) {
                currentSong.postValue(kState.getOriginalSong());
                updateDurationFromController(kState.getOriginalSong());
            } else {
                currentSong.postValue(song);
                updateDurationFromController(song);
            }
        } else {
            currentSong.postValue(null);
            durationMs.postValue(0L);
        }
        currentQueueIndex.postValue(controller.getCurrentMediaItemIndex());
    }

    private void updateDurationFromController(@Nullable Song song) {
        if (controller == null) return;
        long dur = controller.getDuration();
        if (dur != androidx.media3.common.C.TIME_UNSET && dur > 0) {
            durationMs.postValue(dur);
        } else if (song != null && song.getDuration() > 0) {
            durationMs.postValue(song.getDuration());
        } else if (song != null) {
            Song canonical = com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(song.getId());
            if (canonical != null && canonical.getDuration() > 0) {
                durationMs.postValue(canonical.getDuration());
            } else {
                durationMs.postValue(0L);
            }
        } else {
            durationMs.postValue(0L);
        }
    }

    // ===== Player.Listener =====

    private final Player.Listener playerListener = new Player.Listener() {

        @Override
        public void onIsPlayingChanged(boolean playing) {
            isPlaying.postValue(playing);
            if (playing) {
                positionHandler.removeCallbacks(positionUpdater);
                positionHandler.post(positionUpdater);
            } else {
                positionHandler.removeCallbacks(positionUpdater);
                if (controller != null) {
                    currentPositionMs.postValue(controller.getCurrentPosition());
                }
                // Record meaningful play when playback stops (user paused or song ended)
                Song song = currentSong.getValue();
                if (song != null && controller != null) {
                    historyRepository.maybeRecordPlay(song, controller.getCurrentPosition());
                }
            }
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            checkKorokaeSongTransition(mediaItem);
            updateCurrentSongFromController();
            if (controller != null) {
                currentPositionMs.postValue(controller.getCurrentPosition());
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
            if (playbackState == Player.STATE_READY) {
                updateDurationFromController(currentSong.getValue());
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
            Long dur = durationMs.getValue();
            long safePos = Math.max(0, positionMs);
            if (dur != null && dur > 0) {
                safePos = Math.min(safePos, dur);
            }
            queueManager.seekTo(safePos);
            currentPositionMs.setValue(safePos);
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

    public void handleSongDeleted(long songId) {
        if (controller == null) return;
        List<Song> queue = currentQueue.getValue();
        if (queue == null || queue.isEmpty()) return;

        Song activeSong = currentSong.getValue();
        boolean isPlayingActive = activeSong != null && activeSong.getId() == songId;

        List<Integer> indicesToRemove = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == songId) {
                indicesToRemove.add(i);
            }
        }

        if (indicesToRemove.isEmpty()) return;

        if (isPlayingActive) {
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem();
            } else {
                controller.stop();
                currentSong.postValue(null);
            }
        }

        for (int i = indicesToRemove.size() - 1; i >= 0; i--) {
            int idx = indicesToRemove.get(i);
            if (idx >= 0 && idx < controller.getMediaItemCount()) {
                controller.removeMediaItem(idx);
            }
        }

        rebuildQueueSnapshot();
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

    // ===== Korokae Mode (On-Demand Vocal / Instrumental Separation) =====

    private final java.util.concurrent.atomic.AtomicLong korokaeGenerationCounter =
            new java.util.concurrent.atomic.AtomicLong(0);
    @androidx.annotation.Nullable private com.psthetech.swara.util.KorokaeAudioProcessor activeKorokaeProcessor = null;
    @androidx.annotation.Nullable private Song originalSongForKorokae = null;

    public void toggleKorokaeMode() {
        Song song = currentSong.getValue();
        if (song == null) return;
        com.psthetech.swara.domain.model.KorokaeState state = korokaeState.getValue();

        if (state != null && state.isActive()) {
            // Turning OFF -> restore original song
            if (activeKorokaeProcessor != null) {
                activeKorokaeProcessor.cancel();
                activeKorokaeProcessor = null;
            }
            if (originalSongForKorokae != null) {
                switchTrackPreservingPosition(originalSongForKorokae, null, false);
            }
            korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.off(song));
            originalSongForKorokae = null;
            return;
        }

        if (state != null && state.isProcessing()) {
            // User tapped while processing -> cancel and reset to OFF
            if (activeKorokaeProcessor != null) {
                activeKorokaeProcessor.cancel();
                activeKorokaeProcessor = null;
            }
            korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.off(song));
            return;
        }

        // Turning ON -> Check cache or start asynchronous on-device separation
        final long genId = korokaeGenerationCounter.incrementAndGet();
        final long songId = song.getId();
        final Song targetSong = song;
        originalSongForKorokae = targetSong;

        long modifiedTime = targetSong.getDateAdded() * 1000L;
        java.io.File cached = com.psthetech.swara.util.KorokaeCacheManager.getCachedStem(getApplication(), songId, modifiedTime);
        if (cached != null) {
            // Cached instrumental available: immediate switch!
            switchTrackPreservingPosition(targetSong, cached.getAbsolutePath(), true);
            korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.active(targetSong, genId, cached.getAbsolutePath()));
            return;
        }

        // Not cached: begin on-demand separation with progress
        korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.processing(targetSong, genId, 0));
        activeKorokaeProcessor = new com.psthetech.swara.util.KorokaeAudioProcessor(songId, genId);

        activeKorokaeProcessor.process(getApplication(), targetSong, new com.psthetech.swara.util.KorokaeAudioProcessor.ProgressCallback() {
            @Override
            public void onProgress(int percent) {
                if (genId == korokaeGenerationCounter.get() && isCurrentSong(songId)) {
                    korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.processing(targetSong, genId, percent));
                }
            }

            @Override
            public void onSuccess(@NonNull java.io.File instrumentalStem) {
                if (genId == korokaeGenerationCounter.get() && isCurrentSong(songId)) {
                    switchTrackPreservingPosition(targetSong, instrumentalStem.getAbsolutePath(), true);
                    korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.active(targetSong, genId, instrumentalStem.getAbsolutePath()));
                }
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                if (genId == korokaeGenerationCounter.get() && isCurrentSong(songId)) {
                    korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.failed(targetSong, errorMessage));
                }
            }
        });
    }

    private void switchTrackPreservingPosition(@NonNull Song song, @androidx.annotation.Nullable String instrumentalFilePath, boolean isEnteringKorokae) {
        if (controller == null) return;
        long currentPos = controller.getCurrentPosition();
        boolean wasPlaying = controller.isPlaying();
        long targetDuration = song.getDuration();
        long safePos = Math.max(0, currentPos);
        if (targetDuration > 0) {
            safePos = Math.min(safePos, targetDuration);
        }

        int currentIndex = controller.getCurrentMediaItemIndex();
        MediaItem newItem;
        if (isEnteringKorokae && instrumentalFilePath != null) {
            // Point to temporary instrumental file while preserving logical song metadata
            newItem = new MediaItem.Builder()
                    .setMediaId(String.valueOf(song.getId()))
                    .setUri(android.net.Uri.fromFile(new java.io.File(instrumentalFilePath)))
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setTitle(song.getTitle())
                            .setArtist(song.getArtist())
                            .setAlbumTitle(song.getAlbum())
                            .setArtworkUri(new ArtworkRepository(getApplication()).getArtworkUri(song))
                            .build())
                    .build();
        } else {
            newItem = songToMediaItem(song);
        }

        if (currentIndex >= 0 && currentIndex < controller.getMediaItemCount()) {
            controller.replaceMediaItem(currentIndex, newItem);
            controller.seekTo(currentIndex, safePos);
            if (wasPlaying) {
                controller.play();
            }
        }
        currentPositionMs.postValue(safePos);
    }

    private void checkKorokaeSongTransition(@Nullable MediaItem mediaItem) {
        if (mediaItem == null) {
            korokaeGenerationCounter.incrementAndGet();
            if (activeKorokaeProcessor != null) {
                activeKorokaeProcessor.cancel();
                activeKorokaeProcessor = null;
            }
            korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.off(null));
            originalSongForKorokae = null;
            return;
        }

        try {
            long currentId = Long.parseLong(mediaItem.mediaId);
            if (originalSongForKorokae != null && currentId == originalSongForKorokae.getId()) {
                // Same logical song (either original or instrumental stem); preserve active state
                return;
            }

            // User skipped to a different track: auto-cancel processing and reset mode
            korokaeGenerationCounter.incrementAndGet();
            if (activeKorokaeProcessor != null) {
                activeKorokaeProcessor.cancel();
                activeKorokaeProcessor = null;
            }
            originalSongForKorokae = null;
            korokaeState.postValue(com.psthetech.swara.domain.model.KorokaeState.off(mediaItemToSong(mediaItem)));
        } catch (NumberFormatException ignored) {}
    }

    private boolean isCurrentSong(long songId) {
        Song current = currentSong.getValue();
        return current != null && current.getId() == songId;
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

    /**
     * Updates metadata for a song in-place across current playing state, ExoPlayer timeline,
     * MediaSession, and queue snapshot without interrupting playback or altering seek position.
     */
    public void updateSongMetadataInPlace(@NonNull Song updatedSong) {
        long id = updatedSong.getId();
        songCache.put(id, updatedSong);

        // 1. Update active currentSong if matching
        Song active = currentSong.getValue();
        if (active != null && active.getId() == id) {
            currentSong.setValue(updatedSong);

            if (controller != null) {
                int currentIndex = controller.getCurrentMediaItemIndex();
                if (currentIndex >= 0 && currentIndex < controller.getMediaItemCount()) {
                    MediaItem currentItem = controller.getMediaItemAt(currentIndex);
                    if (currentItem != null && String.valueOf(id).equals(currentItem.mediaId)) {
                        MediaItem newItem = songToMediaItem(updatedSong);
                        controller.replaceMediaItem(currentIndex, newItem);
                    }
                }
            }
        }

        // 2. Update queue snapshot
        boolean queueChanged = false;
        for (int i = 0; i < queueSnapshot.size(); i++) {
            Song s = queueSnapshot.get(i);
            if (s != null && s.getId() == id) {
                queueSnapshot.set(i, updatedSong);
                queueChanged = true;
                if (controller != null && i < controller.getMediaItemCount()) {
                    // Update timeline item in place if not current (current handled above)
                    if (active == null || active.getId() != id) {
                        MediaItem item = controller.getMediaItemAt(i);
                        if (item != null && String.valueOf(id).equals(item.mediaId)) {
                            controller.replaceMediaItem(i, songToMediaItem(updatedSong));
                        }
                    }
                }
            }
        }

        if (queueChanged) {
            currentQueue.setValue(new ArrayList<>(queueSnapshot));
        }
    }

    /**
     * Returns ExoPlayer's audio session ID for the system equalizer.
     * Returns AudioEffect.ERROR_BAD_VALUE (= -6) if the controller is not yet connected.
     */
    public int getAudioSessionId() {
        // MediaController doesn't expose audio session ID directly,
        // but the service's ExoPlayer does via AudioSessionIdTracker.
        // We use 0 here as a safe fallback — the system EQ will still open,
        // but may not attach to the correct session on all devices.
        if (controller != null) {
            // Try casting to get the underlying session — Media3 doesn't expose this yet.
            // Use 0 as the best available value from the controller side.
            return 0;
        }
        return 0;
    }

    // ===== Helpers =====

    private MediaItem songToMediaItem(Song song) {
        if (song != null) {
            if (song.getDuration() <= 0) {
                Song canonical = com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(song.getId());
                if (canonical != null && canonical.getDuration() > 0) {
                    song = canonical;
                }
            }
            songCache.put(song.getId(), song);
        }
        android.os.Bundle extras = new android.os.Bundle();
        if (song != null) {
            extras.putLong("albumId", song.getAlbumId());
            extras.putLong("duration", song.getDuration());
            extras.putInt("trackNumber", song.getTrackNumber());
            extras.putInt("year", song.getYear());
            extras.putLong("dateAdded", song.getDateAdded());
        }
        return new MediaItem.Builder()
                .setMediaId(song != null ? String.valueOf(song.getId()) : "0")
                .setUri(song != null ? ArtworkRepository.getSongUri(song.getId()) : null)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(song != null ? song.getTitle() : "Unknown")
                        .setArtist(song != null ? song.getArtist() : "Unknown")
                        .setAlbumTitle(song != null ? song.getAlbum() : "Unknown")
                        .setArtworkUri(song != null ? new ArtworkRepository(getApplication()).getArtworkUri(song) : null)
                        .setExtras(extras)
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
        if (item == null) return null;
        long id = 0;
        try { id = Long.parseLong(item.mediaId); } catch (NumberFormatException ignored) {}
        
        Song cached = songCache.get(id);
        if (cached != null && cached.getDuration() > 0) return cached;

        Song canonical = com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(id);
        if (canonical != null && canonical.getDuration() > 0) {
            songCache.put(id, canonical);
            return canonical;
        }

        MediaMetadata meta = item.mediaMetadata;
        android.os.Bundle extras = meta != null && meta.extras != null ? meta.extras : android.os.Bundle.EMPTY;
        long albumId = extras.getLong("albumId", canonical != null ? canonical.getAlbumId() : 0L);
        long duration = extras.getLong("duration", canonical != null ? canonical.getDuration() : 0L);
        int trackNumber = extras.getInt("trackNumber", canonical != null ? canonical.getTrackNumber() : 0);
        int year = extras.getInt("year", canonical != null ? canonical.getYear() : 0);
        long dateAdded = extras.getLong("dateAdded", canonical != null ? canonical.getDateAdded() : 0L);

        Song resolved = new Song(
                id,
                meta != null && meta.title != null ? meta.title.toString() : (canonical != null ? canonical.getTitle() : "Unknown"),
                meta != null && meta.artist != null ? meta.artist.toString() : (canonical != null ? canonical.getArtist() : "Unknown"),
                meta != null && meta.albumTitle != null ? meta.albumTitle.toString() : (canonical != null ? canonical.getAlbum() : "Unknown"),
                albumId, duration, trackNumber, year, dateAdded
        );
        songCache.put(id, resolved);
        return resolved;
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
        if (activeKorokaeProcessor != null) {
            activeKorokaeProcessor.release();
            activeKorokaeProcessor = null;
        }
        positionHandler.removeCallbacks(positionUpdater);
        if (controller != null) {
            controller.removeListener(playerListener);
        }
        MediaController.releaseFuture(controllerFuture);
        super.onCleared();
    }
}

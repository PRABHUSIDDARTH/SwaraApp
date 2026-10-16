package com.psthetech.swara.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.psthetech.swara.korokae.KorokaeAudioProcessor;
import com.psthetech.swara.korokae.KorokaeModelProvider;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.ListenEventDao;
import com.psthetech.swara.data.db.entity.ListenEvent;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.vibe.EventClassifier;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.MainActivity;
import com.psthetech.swara.util.SleepTimerManager;
import com.psthetech.swara.widget.SwaraWidgetUpdater;

/**
 * Swara V2 Playback Service — the authoritative playback engine.
 *
 * Architecture:
 *  - Extends MediaSessionService (Media3) — handles foreground service, notification, MediaSession
 *  - ExoPlayer is the single source of playback state (queue, position, playing/paused)
 *  - MediaSession exposes ExoPlayer to the UI layer (MediaController) and the system
 *    (notification, lock screen, Bluetooth controls)
 *  - The UI layer interacts ONLY via MediaController — no direct service references
 *
 * Background Playback:
 *  - MediaSessionService keeps the foreground notification active while playing
 *  - onTaskRemoved is NOT overridden — background playback continues when task is swiped away
 *  - When the user dismisses the notification, Media3 handles teardown automatically
 *
 * Audio Focus:
 *  - Handled by ExoPlayer via setAudioAttributes(..., handleAudioFocus = true)
 *  - No manual AudioManager focus management needed
 *
 * Headphone Unplug:
 *  - Handled by ExoPlayer via setHandleAudioBecomingNoisy(true)
 */
@UnstableApi
public class SwaraPlaybackService extends MediaSessionService {

    private static final String TAG = "SwaraPlaybackService";

    /** Custom session command toggling real-time Korokae vocal removal. */
    public static final String COMMAND_SET_KOROKAE = "com.psthetech.swara.SET_KOROKAE";
    public static final String EXTRA_KOROKAE_ENABLED = "enabled";
    /** Session extras key: true while Korokae is engaged (model loaded + enabled). */
    public static final String SESSION_EXTRA_KOROKAE_ACTIVE = "korokae_active";
    /** Session extras key: non-null when the model failed to load. */
    public static final String SESSION_EXTRA_KOROKAE_ERROR = "korokae_error";
    /** Custom session command (used by the widget): toggle Korokae on/off. */
    public static final String COMMAND_TOGGLE_KOROKAE = "com.psthetech.swara.TOGGLE_KOROKAE";
    /** Custom session command (used by the widget): favorite / unfavorite the current song. */
    public static final String COMMAND_TOGGLE_FAVORITE = "com.psthetech.swara.TOGGLE_FAVORITE";
    /** Session extras key: ExoPlayer's audio session ID, for the system equalizer. */
    public static final String SESSION_EXTRA_AUDIO_SESSION_ID = "audio_session_id";
    /** Custom session command: turn Vibe Mode on/off, optionally starting from a song. */
    public static final String COMMAND_SET_VIBE = "com.psthetech.swara.SET_VIBE";
    public static final String EXTRA_VIBE_ENABLED = "enabled";
    /** Optional long: play only this song and start Vibe Mode from it (-1 / absent = current). */
    public static final String EXTRA_VIBE_START_SONG_ID = "start_song_id";
    /** Session extras key: true while Vibe Mode is on. */
    public static final String SESSION_EXTRA_VIBE_ACTIVE = "vibe_active";

    /** Stop auto-skipping after this many consecutive unplayable items. */
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
    private int consecutiveErrors = 0;

    private com.psthetech.swara.data.repository.PlayHistoryRepository historyRepository;
    private PlaybackStateStore playbackStateStore;
    private static final long STATE_SAVE_INTERVAL_MS = 10_000L;
    private final Runnable periodicStateSave = new Runnable() {
        @Override
        public void run() {
            if (player == null || !player.isPlaying()) return;
            saveState();
            mainHandler.postDelayed(this, STATE_SAVE_INTERVAL_MS);
        }
    };

    private ExoPlayer player;
    private MediaSession mediaSession;
    private KorokaeModelProvider korokaeModelProvider;
    private KorokaeAudioProcessor korokaeProcessor;
    private VibeController vibeController;
    /** The current item has not yet logged its TYPE_PLAY listen event. */
    private boolean playEventPending = false;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // Drives the widget seekbar while playing (only when a widget is placed)
    private static final long WIDGET_TICK_MS = 1000L;
    private final Runnable widgetTicker = new Runnable() {
        @Override
        public void run() {
            if (player == null || !player.isPlaying()) return;
            if (SwaraWidgetUpdater.hasPlacedWidgets(SwaraPlaybackService.this)) {
                pushWidgetUpdateNow();
                mainHandler.postDelayed(this, WIDGET_TICK_MS);
            }
        }
    };

    // Favorite ids mirrored from Room so the widget heart reflects in-app changes too
    private final java.util.Set<Long> favoriteIds = new java.util.HashSet<>();
    private com.psthetech.swara.data.repository.FavoritesRepository favoritesRepository;
    private androidx.lifecycle.LiveData<java.util.List<com.psthetech.swara.data.db.entity.FavoriteSong>> favoritesLive;
    private final androidx.lifecycle.Observer<java.util.List<com.psthetech.swara.data.db.entity.FavoriteSong>> favoritesObserver =
            list -> {
                favoriteIds.clear();
                if (list != null) {
                    for (com.psthetech.swara.data.db.entity.FavoriteSong f : list) favoriteIds.add(f.id);
                }
                pushWidgetUpdateNow();
            };

    @Override
    public void onCreate() {
        super.onCreate();

        // Build ExoPlayer with automatic audio focus and headphone-unplug handling
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build();

        korokaeModelProvider = new KorokaeModelProvider(this);
        korokaeProcessor = new KorokaeAudioProcessor(korokaeModelProvider);
        korokaeModelProvider.setListener((available, error) ->
                mainHandler.post(this::publishKorokaeState));

        // Real-time vocal removal runs inside the audio sink's processor chain.
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(@NonNull android.content.Context context,
                                               boolean enableFloatOutput,
                                               boolean enableAudioTrackPlaybackParams) {
                return new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(false)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(new androidx.media3.common.audio.AudioProcessor[]{korokaeProcessor})
                        .build();
            }
        };

        player = new ExoPlayer.Builder(this, renderersFactory)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus */ true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        vibeController = new VibeController(this, player, mainHandler, () -> {
            publishKorokaeState();
            saveState();
        });

        // Notify SleepTimerManager when the song changes, and push widget updates
        player.addListener(new Player.Listener() {

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                // Push widget update with new track info
                pushWidgetUpdate(mediaItem, player.isPlaying());
                saveState();
                // TYPE_PLAY is logged once per item, when it actually plays: a paused queue
                // restore logs nothing, resuming from pause logs once.
                playEventPending = mediaItem != null;
                if (playEventPending && player.isPlaying()) {
                    playEventPending = false;
                    recordListenEvent(mediaItem, ListenEvent.TYPE_PLAY, 0L);
                }
                vibeController.onMediaItemTransition(mediaItem, reason);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Push widget update when play/pause state changes
                MediaItem current = player.getCurrentMediaItem();
                pushWidgetUpdate(current, isPlaying);
                mainHandler.removeCallbacks(widgetTicker);
                mainHandler.removeCallbacks(periodicStateSave);
                if (isPlaying) {
                    if (playEventPending && current != null) {
                        playEventPending = false;
                        recordListenEvent(current, ListenEvent.TYPE_PLAY, 0L);
                    }
                    mainHandler.postDelayed(widgetTicker, WIDGET_TICK_MS);
                    mainHandler.postDelayed(periodicStateSave, STATE_SAVE_INTERVAL_MS);
                } else {
                    // Paused or stopped: count the listen so far and remember the position.
                    recordPlay(current, player.getCurrentPosition());
                    saveState();
                }
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    // Paused at the end of the item because "sleep after this song" is armed.
                    SleepTimerManager.getInstance().onCurrentItemEnded();
                }
            }

            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                                @NonNull Player.PositionInfo newPosition,
                                                int reason) {
                // Leaving an item (auto-advance, repeat-one loop, skip) counts as a listen of
                // the old item up to where it was left.
                boolean leftItem = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                        || oldPosition.mediaItemIndex != newPosition.mediaItemIndex;
                if (leftItem && oldPosition.mediaItem != null) {
                    long listened = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                            ? Long.MAX_VALUE // finished naturally
                            : oldPosition.positionMs;
                    recordPlay(oldPosition.mediaItem, listened);
                }
                // Listen events (taste signals): separate from play history above.
                boolean sameIndex = oldPosition.mediaItemIndex == newPosition.mediaItemIndex;
                boolean autoTransition = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
                boolean sameItemRestarted = sameIndex && (autoTransition
                        || (reason == Player.DISCONTINUITY_REASON_SEEK
                        && newPosition.positionMs <= 1000 && oldPosition.positionMs > 5000));
                if ((autoTransition || !sameIndex || sameItemRestarted)
                        && oldPosition.mediaItem != null) {
                    Song left = MediaItemFactory.toSong(oldPosition.mediaItem);
                    long duration = left != null ? left.getDuration() : 0L;
                    int type = EventClassifier.classifyLeave(oldPosition.positionMs, duration,
                            autoTransition, sameItemRestarted);
                    recordListenEvent(oldPosition.mediaItem, type, oldPosition.positionMs);
                    if (left != null) vibeController.onItemLeft(left.getId(), type);
                }
                // Seeks (in-app, notification or widget) re-base the widget seekbar + timers
                if (reason == Player.DISCONTINUITY_REASON_SEEK
                        || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    pushWidgetUpdateNow();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // Duration becomes known at READY; refresh so the remaining time is right
                if (playbackState == Player.STATE_READY) {
                    consecutiveErrors = 0;
                    pushWidgetUpdateNow();
                } else if (playbackState == Player.STATE_ENDED) {
                    // Queue finished: an armed end-of-song timer is satisfied too.
                    SleepTimerManager.getInstance().onCurrentItemEnded();
                }
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                Log.e(TAG, "Playback error, skipping item", error);
                // After an error the player is IDLE; it must be re-prepared to continue.
                consecutiveErrors++;
                if (consecutiveErrors <= MAX_CONSECUTIVE_ERRORS && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem();
                    player.prepare();
                    player.play();
                }
            }

            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                openEqualizerSession(audioSessionId);
                publishKorokaeState();
            }
        });

        historyRepository = new com.psthetech.swara.data.repository.PlayHistoryRepository(
                com.psthetech.swara.data.db.AppDatabase.getInstance(this));
        playbackStateStore = new PlaybackStateStore(this);

        SleepTimerManager.getInstance().setPlaybackHooks(new SleepTimerManager.PlaybackHooks() {
            @Override
            public void pauseNow() {
                if (player != null) player.pause();
            }

            @Override
            public void setPauseAtEndOfCurrentItem(boolean pause) {
                if (player != null) player.setPauseAtEndOfMediaItems(pause);
            }
        });

        favoritesRepository = new com.psthetech.swara.data.repository.FavoritesRepository(
                com.psthetech.swara.data.db.AppDatabase.getInstance(this));
        favoritesLive = favoritesRepository.getFavoritesLive();
        favoritesLive.observeForever(favoritesObserver);

        // PendingIntent to open the app from the notification
        PendingIntent activityIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(activityIntent)
                .setCallback(new CustomCallback())
                .build();

        openEqualizerSession(player.getAudioSessionId());
        restoreState();

        Log.d(TAG, "SwaraPlaybackService created");
    }

    // ===== Play history =====

    private void recordPlay(@Nullable MediaItem item, long positionMs) {
        Song song = MediaItemFactory.toSong(item);
        if (song != null && historyRepository != null) {
            historyRepository.maybeRecordPlay(song, positionMs);
        }
    }

    private void recordListenEvent(@Nullable MediaItem item, int type, long positionMs) {
        final Song song = MediaItemFactory.toSong(item);
        if (song == null) return;
        final long now = System.currentTimeMillis();
        final int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        final long songId = song.getId();
        final android.content.Context ctx = getApplicationContext();
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            try {
                ListenEventDao dao = AppDatabase.getInstance(ctx).listenEventDao();
                ListenEvent e = new ListenEvent();
                e.songId = songId;
                e.type = type;
                e.positionMs = positionMs;
                e.timestamp = now;
                e.hourOfDay = hour;
                dao.insert(e);
                dao.trimTo(5000);
            } catch (Exception ex) {
                Log.w(TAG, "Failed to record listen event", ex);
            }
        });
    }

    // ===== Equalizer =====

    private int openedEqSessionId = C.AUDIO_SESSION_ID_UNSET;

    /** Lets system / third-party equalizers attach effects to our audio session. */
    private void openEqualizerSession(int sessionId) {
        if (sessionId == openedEqSessionId) return;
        closeEqualizerSession();
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return;
        Intent open = new Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        open.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
        open.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        open.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE,
                android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC);
        sendBroadcast(open);
        openedEqSessionId = sessionId;
    }

    private void closeEqualizerSession() {
        if (openedEqSessionId == C.AUDIO_SESSION_ID_UNSET) return;
        Intent close = new Intent(android.media.audiofx.AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        close.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, openedEqSessionId);
        close.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(close);
        openedEqSessionId = C.AUDIO_SESSION_ID_UNSET;
    }

    // ===== Queue persistence (resume after the process is killed) =====

    private void saveState() {
        if (player == null || playbackStateStore == null) return;
        int count = player.getMediaItemCount();
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            try {
                ids[i] = Long.parseLong(player.getMediaItemAt(i).mediaId);
            } catch (NumberFormatException e) {
                ids[i] = -1;
            }
        }
        playbackStateStore.save(new PlaybackStateStore.State(ids,
                player.getCurrentMediaItemIndex(), player.getCurrentPosition(),
                player.getShuffleModeEnabled(), player.getRepeatMode(),
                vibeController.serializedSession(), vibeController.vibePickIds(),
                vibeController.skippedIds()));
    }

    private void restoreState() {
        PlaybackStateStore.State saved = playbackStateStore.load();
        if (saved == null || saved.songIds.length == 0) return;
        com.psthetech.swara.SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            MusicRepository repo = new MusicRepository(this);
            java.util.List<MediaItem> items = new java.util.ArrayList<>();
            int startIndex = 0;
            boolean currentMissing = false;
            for (int i = 0; i < saved.songIds.length; i++) {
                Song song;
                try {
                    song = repo.getSongById(saved.songIds[i]);
                } catch (SecurityException e) {
                    return; // no media permission yet
                }
                if (i == saved.index) {
                    startIndex = items.size();
                    currentMissing = song == null;
                }
                if (song == null) continue; // deleted since last run
                items.add(MediaItemFactory.toMediaItem(this, song));
            }
            if (items.isEmpty()) return;
            final int start = Math.min(startIndex, items.size() - 1);
            final long startPosition = currentMissing ? 0 : saved.positionMs;
            mainHandler.post(() -> {
                // Don't clobber a queue the user started while we were loading.
                if (player == null || player.getMediaItemCount() > 0) return;
                player.setShuffleModeEnabled(saved.shuffle);
                player.setRepeatMode(saved.repeatMode);
                vibeController.expectPlaylistChange(items.get(start).mediaId);
                player.setMediaItems(items, start, startPosition);
                player.prepare();
                vibeController.restore(saved.vibeState, saved.vibePickIds, saved.vibeSkippedIds);
            });
        });
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "SwaraPlaybackService destroying");
        if (favoritesLive != null) favoritesLive.removeObserver(favoritesObserver);
        mainHandler.removeCallbacks(widgetTicker);
        mainHandler.removeCallbacks(periodicStateSave);
        if (player != null) {
            recordPlay(player.getCurrentMediaItem(), player.getCurrentPosition());
            saveState();
        }
        if (vibeController != null) vibeController.release();
        SleepTimerManager.getInstance().setPlaybackHooks(null);
        closeEqualizerSession();
        if (korokaeModelProvider != null) {
            korokaeModelProvider.setListener(null);
            korokaeModelProvider.shutdown();
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    /**
     * Resolve the Song for the given MediaItem and push a widget update.
     * Called from the Player.Listener on track transitions and play/pause changes.
     *
     * Song resolution priority:
     *   1. MusicRepository.getCanonicalSong() — has full metadata including albumId
     *   2. MediaItem.mediaMetadata fields — fallback if not in repo cache yet
     */
    private void pushWidgetUpdate(@Nullable MediaItem mediaItem, boolean isPlaying) {
        Song song = null;
        if (mediaItem != null) {
            try {
                long id = Long.parseLong(mediaItem.mediaId);
                song = MusicRepository.getCanonicalSong(id);
                if (song == null) {
                    // Decode from the item's metadata so the widget still updates
                    song = MediaItemFactory.toSong(mediaItem);
                }
            } catch (NumberFormatException ignored) { /* mediaId not a song ID */ }
        }
        long pos = player != null ? player.getCurrentPosition() : 0;
        long dur = player != null ? player.getDuration() : C.TIME_UNSET;
        if (dur == C.TIME_UNSET || dur <= 0) dur = song != null ? song.getDuration() : 0;
        boolean favorite = song != null && favoriteIds.contains(song.getId());
        boolean korokae = korokaeProcessor != null && korokaeProcessor.isEnabled();
        SwaraWidgetUpdater.pushUpdate(this, song,
                new SwaraWidgetUpdater.State(isPlaying, pos, dur, favorite, korokae));
    }

    /** Push the widget with the player's current item / state. */
    private void pushWidgetUpdateNow() {
        if (player == null) return;
        pushWidgetUpdate(player.getCurrentMediaItem(), player.isPlaying());
    }

    @Nullable
    private Song currentSong() {
        if (player == null || player.getCurrentMediaItem() == null) return null;
        try {
            return MusicRepository.getCanonicalSong(Long.parseLong(player.getCurrentMediaItem().mediaId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Bundle buildKorokaeExtras() {
        Bundle extras = new Bundle();
        boolean ready = korokaeModelProvider.getIfLoaded() != null;
        extras.putBoolean(SESSION_EXTRA_KOROKAE_ACTIVE, ready && korokaeProcessor.isEnabled());
        extras.putBoolean("korokae_enabled", korokaeProcessor.isEnabled());
        extras.putString(SESSION_EXTRA_KOROKAE_ERROR, korokaeModelProvider.getError());
        if (player != null) extras.putInt(SESSION_EXTRA_AUDIO_SESSION_ID, player.getAudioSessionId());
        extras.putBoolean(SESSION_EXTRA_VIBE_ACTIVE, vibeController != null && vibeController.isActive());
        return extras;
    }

    private void publishKorokaeState() {
        if (mediaSession != null) {
            mediaSession.setSessionExtras(buildKorokaeExtras());
        }
    }

    /**
     * Custom MediaSession.Callback — handles commands from the notification and external controllers.
     * ExoPlayer already handles most commands automatically via MediaSession integration.
     */
    private class CustomCallback implements MediaSession.Callback {

        @Override
        @NonNull
        public MediaSession.ConnectionResult onConnect(
                @NonNull MediaSession session,
                @NonNull MediaSession.ControllerInfo controller) {
            SessionCommands commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(new SessionCommand(COMMAND_SET_KOROKAE, Bundle.EMPTY))
                    .add(new SessionCommand(COMMAND_TOGGLE_KOROKAE, Bundle.EMPTY))
                    .add(new SessionCommand(COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
                    .add(new SessionCommand(COMMAND_SET_VIBE, Bundle.EMPTY))
                    .build();
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .setSessionExtras(buildKorokaeExtras())
                    .build();
        }

        @Override
        @NonNull
        public ListenableFuture<SessionResult> onCustomCommand(
                @NonNull MediaSession session,
                @NonNull MediaSession.ControllerInfo controller,
                @NonNull SessionCommand customCommand,
                @NonNull Bundle args) {
            if (COMMAND_SET_KOROKAE.equals(customCommand.customAction)) {
                boolean enable = args.getBoolean(EXTRA_KOROKAE_ENABLED, false);
                korokaeProcessor.setEnabled(enable);
                if (enable) korokaeModelProvider.loadAsync();
                publishKorokaeState();
                pushWidgetUpdateNow();
                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
            }
            if (COMMAND_TOGGLE_KOROKAE.equals(customCommand.customAction)) {
                boolean enable = !korokaeProcessor.isEnabled();
                korokaeProcessor.setEnabled(enable);
                if (enable) korokaeModelProvider.loadAsync();
                publishKorokaeState();
                pushWidgetUpdateNow();
                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
            }
            if (COMMAND_SET_VIBE.equals(customCommand.customAction)) {
                boolean enable = args.getBoolean(EXTRA_VIBE_ENABLED, false);
                long startId = args.getLong(EXTRA_VIBE_START_SONG_ID, -1L);
                if (enable && startId > 0) {
                    vibeController.startFrom(startId);
                } else {
                    vibeController.setEnabled(enable);
                }
                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
            }
            if (COMMAND_TOGGLE_FAVORITE.equals(customCommand.customAction)) {
                Song song = currentSong();
                if (song == null) {
                    return Futures.immediateFuture(new SessionResult(androidx.media3.session.SessionError.ERROR_BAD_VALUE));
                }
                // Optimistic flip so the widget heart responds instantly; Room observer confirms.
                if (!favoriteIds.remove(song.getId())) favoriteIds.add(song.getId());
                favoritesRepository.toggleFavorite(song);
                pushWidgetUpdateNow();
                return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
            }
            return MediaSession.Callback.super.onCustomCommand(session, controller, customCommand, args);
        }

        @Override
        public void onPostConnect(@NonNull MediaSession session,
                                  @NonNull MediaSession.ControllerInfo controller) {
            // Connection established — no additional setup needed
        }
    }
}

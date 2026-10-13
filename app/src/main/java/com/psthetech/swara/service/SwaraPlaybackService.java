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
import androidx.media3.common.MediaMetadata;
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

import com.psthetech.swara.data.repository.MusicRepository;
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

    private ExoPlayer player;
    private MediaSession mediaSession;
    private KorokaeModelProvider korokaeModelProvider;
    private KorokaeAudioProcessor korokaeProcessor;
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

        // Notify SleepTimerManager when the song changes, and push widget updates
        player.addListener(new Player.Listener() {

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Natural track finish → trigger end-of-song sleep if armed
                    SleepTimerManager.getInstance().onSongTransition();
                }
                // Push widget update with new track info
                pushWidgetUpdate(mediaItem, player.isPlaying());
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                // Push widget update when play/pause state changes
                MediaItem current = player.getCurrentMediaItem();
                pushWidgetUpdate(current, isPlaying);
                mainHandler.removeCallbacks(widgetTicker);
                if (isPlaying) mainHandler.postDelayed(widgetTicker, WIDGET_TICK_MS);
            }

            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                                @NonNull Player.PositionInfo newPosition,
                                                int reason) {
                // Seeks (in-app, notification or widget) re-base the widget seekbar + timers
                if (reason == Player.DISCONTINUITY_REASON_SEEK
                        || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    pushWidgetUpdateNow();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // Duration becomes known at READY; refresh so the remaining time is right
                if (playbackState == Player.STATE_READY) pushWidgetUpdateNow();
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

        Log.d(TAG, "SwaraPlaybackService created");
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
                    // Build a minimal Song from metadata so the widget still updates
                    MediaMetadata meta = mediaItem.mediaMetadata;
                    song = new Song(
                            id,
                            meta.title  != null ? meta.title.toString()  : "Unknown",
                            meta.artist != null ? meta.artist.toString() : "Unknown",
                            meta.albumTitle != null ? meta.albumTitle.toString() : "Unknown",
                            /* albumId */ 0, /* duration */ 0,
                            /* trackNumber */ 0, /* year */ 0, /* dateAdded */ 0
                    );
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

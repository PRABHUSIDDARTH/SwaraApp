package com.psthetech.swara.service;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.entity.ListenEvent;
import com.psthetech.swara.data.preference.LibraryPreferences;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.vibe.VibeQueuePolicy;
import com.psthetech.swara.vibe.VibeSession;
import com.psthetech.swara.vibe.android.RecommendationService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Vibe Mode and Autoplay queue orchestration, owned by {@link SwaraPlaybackService}.
 *
 * <p>Threading: every public method runs on the main thread and is the only code that touches
 * the player. Picking (Room, MediaStore, mood analysis) runs on the I/O executor; results are
 * posted back to the main handler and dropped when stale (generation changed, or the queue no
 * longer ends where it did when the request was made). One refill is in flight at a time.
 */
final class VibeController {

    private static final String TAG = "VibeMode";
    /** Picks kept queued after the current item while Vibe Mode is on. */
    private static final int AHEAD = 2;
    /** Picks appended by Autoplay when the last item starts. */
    private static final int AUTOPLAY_PICKS = 2;

    private final Context context;
    private final Player player;
    private final Handler mainHandler;
    private final Executor io;
    /** Publishes session extras and saves playback state. Main thread. */
    private final Runnable onStateChanged;
    private final RecommendationService recommendations;

    /** True from enable until disable; the session may still be null while m0 is analyzed. */
    private boolean vibeOn = false;
    @Nullable private VibeSession session;
    /** Song ids appended by Vibe Mode this session. */
    private final Set<Long> vibePickIds = new HashSet<>();
    /** Song ids early-skipped this session; excluded from further picks. */
    private final Set<Long> skippedIds = new HashSet<>();

    /** Bumped on enable / disable / start-from-song / restore / release. */
    private int generation = 0;
    private boolean refillInFlight = false;
    /** A transition happened while a refill was in flight: re-check once it lands. */
    private boolean recheckPending = false;
    private boolean released = false;

    VibeController(@NonNull Context context, @NonNull Player player, @NonNull Handler mainHandler,
                   @NonNull Runnable onStateChanged) {
        this.context = context.getApplicationContext();
        this.player = player;
        this.mainHandler = mainHandler;
        this.io = SwaraApplication.getInstance().getIoExecutor();
        this.onStateChanged = onStateChanged;
        this.recommendations = new RecommendationService(this.context);
    }

    boolean isActive() {
        return vibeOn;
    }

    // ===== Commands =====

    /** Turns Vibe Mode on (from the current song) or off. */
    void setEnabled(boolean enabled) {
        if (!enabled) {
            disable();
            return;
        }
        MediaItem current = player.getCurrentMediaItem();
        if (current == null) {
            Log.w(TAG, "Vibe Mode needs a current song");
            disable();
            return;
        }
        int idx = player.getCurrentMediaItemIndex();
        int count = player.getMediaItemCount();
        if (idx + 1 < count) player.removeMediaItems(idx + 1, count);
        beginSession(current.mediaId);
    }

    /** Plays only {@code songId} and enables Vibe Mode with it as the starting mood. */
    void startFrom(long songId) {
        final int gen = ++generation;
        io.execute(() -> {
            Song song;
            MediaItem item;
            try {
                song = new MusicRepository(context).getSongById(songId);
                item = song == null ? null : MediaItemFactory.toMediaItem(context, song);
            } catch (RuntimeException e) {
                Log.w(TAG, "Start song lookup failed", e);
                item = null;
            }
            final MediaItem result = item;
            mainHandler.post(() -> {
                if (released || gen != generation) return;
                if (result == null) {
                    Log.w(TAG, "Start song " + songId + " not found");
                    return;
                }
                // Mark Vibe on before the queue changes so the transition does not autoplay.
                vibeOn = true;
                session = null;
                player.setMediaItems(java.util.Collections.singletonList(result));
                player.prepare();
                player.play();
                beginSession(result.mediaId);
            });
        });
    }

    private void disable() {
        generation++;
        vibeOn = false;
        session = null;
        vibePickIds.clear();
        skippedIds.clear();
        onStateChanged.run();
    }

    /** Resets session state, publishes vibe_active and analyzes m0 off the main thread. */
    private void beginSession(@NonNull String mediaId) {
        final int gen = ++generation;
        vibeOn = true;
        session = null;
        vibePickIds.clear();
        skippedIds.clear();
        onStateChanged.run();
        final long songId = parseId(mediaId);
        io.execute(() -> {
            final double[] m0 = songId > 0 ? recommendations.moodOf(songId) : new double[]{0.5, 0.5};
            mainHandler.post(() -> {
                if (released || gen != generation || !vibeOn) return;
                session = new VibeSession(m0[0], m0[1]);
                Log.d(TAG, "Vibe session started at v=" + m0[0] + " e=" + m0[1]);
                maybeRefill();
            });
        });
    }

    // ===== Player events (main thread) =====

    /** Called from onMediaItemTransition. */
    void onMediaItemTransition() {
        maybeRefill();
    }

    /** Called when the player leaves an item with the classified listen-event type. */
    void onItemLeft(long songId, int type) {
        if (!vibeOn || session == null || !vibePickIds.contains(songId)) return;
        if (type == ListenEvent.TYPE_SKIP) {
            session.onEarlySkip();
            skippedIds.add(songId);
        } else if (type == ListenEvent.TYPE_FINISH) {
            session.onPickAccepted();
        }
    }

    void release() {
        released = true;
        generation++;
    }

    // ===== Persistence =====

    @Nullable
    String serializedSession() {
        return vibeOn && session != null ? session.serialize() : null;
    }

    long[] vibePickIds() {
        return toArray(vibePickIds);
    }

    long[] skippedIds() {
        return toArray(skippedIds);
    }

    /** Restores a saved session after the saved queue has been set on the player. */
    void restore(@Nullable String serialized, @NonNull long[] pickIds, @NonNull long[] skipped) {
        VibeSession restored = VibeSession.deserialize(serialized);
        if (restored == null) return;
        generation++;
        vibeOn = true;
        session = restored;
        vibePickIds.clear();
        for (long id : pickIds) vibePickIds.add(id);
        skippedIds.clear();
        for (long id : skipped) skippedIds.add(id);
        onStateChanged.run();
        Log.d(TAG, "Vibe session restored at step " + restored.step());
        maybeRefill();
    }

    // ===== Refill =====

    private void maybeRefill() {
        if (released) return;
        if (refillInFlight) {
            recheckPending = true;
            return;
        }
        MediaItem current = player.getCurrentMediaItem();
        if (current == null) return;
        int count = player.getMediaItemCount();
        int idx = player.getCurrentMediaItemIndex();

        final int needed;
        final List<double[]> targets = new ArrayList<>();
        final boolean vibe;
        if (vibeOn) {
            if (session == null) return; // m0 still being analyzed
            needed = VibeQueuePolicy.picksNeeded(count, idx, AHEAD);
            if (needed == 0) return;
            // Plan targets on a copy; the real session advances only when picks land.
            VibeSession plan = VibeSession.deserialize(session.serialize());
            if (plan == null) return;
            for (int i = 0; i < needed; i++) targets.add(plan.nextTarget());
            vibe = true;
        } else {
            if (!LibraryPreferences.autoplayEnabled(context)) return;
            // Last item: covers shuffle and repeat-all (where a "next" item still exists).
            if (player.hasNextMediaItem()) return;
            needed = AUTOPLAY_PICKS;
            vibe = false; // targets resolved on I/O from the current song's mood
        }

        final Set<Long> excluded = new HashSet<>();
        for (int i = 0; i < count; i++) {
            long id = parseId(player.getMediaItemAt(i).mediaId);
            if (id > 0) excluded.add(id);
        }
        if (vibe) excluded.addAll(skippedIds);
        final long currentId = parseId(current.mediaId);
        final Song anchor = MediaItemFactory.toSong(player.getMediaItemAt(count - 1));
        final List<String> recentArtists = new ArrayList<>();
        for (int i = Math.max(0, count - 2); i < count; i++) {
            Song s = MediaItemFactory.toSong(player.getMediaItemAt(i));
            if (s != null) recentArtists.add(RecommendationService.artistKey(s));
        }
        final String anchorMediaId = player.getMediaItemAt(count - 1).mediaId;
        final int expectedCount = count;
        final int gen = generation;

        refillInFlight = true;
        recheckPending = false;
        io.execute(() -> {
            List<MediaItem> items = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            try {
                if (!vibe) {
                    double[] mood = currentId > 0
                            ? recommendations.moodOf(currentId) : new double[]{0.5, 0.5};
                    for (int i = 0; i < needed; i++) targets.add(mood);
                }
                RecommendationService.Pool pool = recommendations.loadPool(excluded);
                List<Song> picks = recommendations.pickSequence(pool, anchor, recentArtists,
                        targets, excluded);
                for (Song s : picks) {
                    items.add(MediaItemFactory.toMediaItem(context, s));
                    ids.add(s.getId());
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Refill failed", e);
            }
            mainHandler.post(() -> onRefillLanded(gen, vibe, anchorMediaId, expectedCount,
                    items, ids));
        });
    }

    private void onRefillLanded(int gen, boolean vibe, String anchorMediaId, int expectedCount,
                                List<MediaItem> items, List<Long> ids) {
        refillInFlight = false;
        if (released) return;
        boolean fresh = gen == generation && vibe == vibeOn
                && player.getMediaItemCount() == expectedCount
                && expectedCount > 0
                && anchorMediaId.equals(player.getMediaItemAt(expectedCount - 1).mediaId);
        if (!fresh) {
            maybeRefill(); // state moved on: plan again against the current queue
            return;
        }
        if (!items.isEmpty()) {
            player.addMediaItems(items);
            if (vibe && session != null) {
                for (int i = 0; i < items.size(); i++) session.nextTarget();
                vibePickIds.addAll(ids);
            }
            Log.d(TAG, (vibe ? "Vibe" : "Autoplay") + " appended " + ids
                    + " queue=" + player.getMediaItemCount()
                    + " index=" + player.getCurrentMediaItemIndex()
                    + (vibe && session != null ? " step=" + session.step() : ""));
            onStateChanged.run();
        }
        if (recheckPending) {
            recheckPending = false;
            maybeRefill();
        }
    }

    private static long parseId(@Nullable String mediaId) {
        if (mediaId == null) return -1;
        try {
            return Long.parseLong(mediaId);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long[] toArray(Set<Long> ids) {
        long[] out = new long[ids.size()];
        int i = 0;
        for (long id : ids) out[i++] = id;
        return out;
    }
}

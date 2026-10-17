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
import com.psthetech.swara.vibe.RefillGate;
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
 * <p>Threading: every method here runs on the main thread and is the only code that touches
 * the player. Picking (Room, MediaStore, mood analysis) runs on the I/O executor; results are
 * posted back to the main handler and dropped when stale ({@link VibeQueuePolicy#landingIsFresh}).
 * One refill per generation is in flight at a time ({@link RefillGate}).
 */
final class VibeController {

    private static final String TAG = "VibeMode";
    /** Picks kept queued after the current item while Vibe Mode is on. */
    private static final int AHEAD = 2;
    /** Picks appended by Autoplay when the last item starts. */
    private static final int AUTOPLAY_PICKS = 2;
    /** Persisted while Vibe is on but m0 was still being analyzed. */
    static final String PENDING_SESSION = "pending";

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
    /** The first Vibe round replaces the items after the current one when its picks land. */
    private boolean replaceUpcoming = false;
    /** Song ids appended by Vibe Mode this session. */
    private final Set<Long> vibePickIds = new HashSet<>();
    /** Song ids early-skipped this session; excluded from further picks. */
    private final Set<Long> skippedIds = new HashSet<>();
    /** Media id of a PLAYLIST_CHANGED transition this controller (or a restore) caused. */
    @Nullable private String expectedPlaylistChangeId;

    /** Bumped on enable / disable / start-from-song / restore / release. */
    private int generation = 0;
    private final RefillGate gate = new RefillGate();
    /** A transition happened while this generation's refill was in flight. */
    private boolean recheckPending = false;
    /** Media id of the current item as of the last transition / session start. */
    @Nullable private String lastCurrentId;
    /** Read from the I/O thread by background analysis, so volatile. */
    private volatile boolean released = false;

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
        player.setShuffleModeEnabled(false);
        // Upcoming items stay until the first picks land (see onRefillLanded).
        beginSession(current.mediaId, true);
    }

    /** Plays only {@code songId} and enables Vibe Mode with it as the starting mood. */
    void startFrom(long songId) {
        final int gen = ++generation;
        io.execute(() -> {
            MediaItem item;
            try {
                Song song = new MusicRepository(context).getSongById(songId);
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
                    onStateChanged.run(); // re-publish the real vibe_active
                    return;
                }
                // Mark Vibe on first so the resulting transition neither autoplays nor
                // counts as the user replacing the queue.
                vibeOn = true;
                session = null;
                expectedPlaylistChangeId = result.mediaId;
                player.setShuffleModeEnabled(false);
                player.setMediaItems(java.util.Collections.singletonList(result));
                player.prepare();
                player.play();
                beginSession(result.mediaId, false);
            });
        });
    }

    private void disable() {
        generation++;
        vibeOn = false;
        session = null;
        replaceUpcoming = false;
        vibePickIds.clear();
        skippedIds.clear();
        onStateChanged.run();
    }

    /** Resets session state, publishes vibe_active and analyzes m0 off the main thread. */
    private void beginSession(@NonNull String mediaId, boolean replace) {
        final int gen = ++generation;
        lastCurrentId = mediaId;
        vibeOn = true;
        session = null;
        replaceUpcoming = replace;
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
                onStateChanged.run(); // persist the real session
                maybeRefill();
            });
        });
    }

    // ===== Player events (main thread) =====

    /** Called from onMediaItemTransition. */
    void onMediaItemTransition(@Nullable MediaItem item, int reason) {
        String id = item == null ? null : item.mediaId;
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            boolean ends = VibeQueuePolicy.playlistChangeEndsVibe(expectedPlaylistChangeId,
                    lastCurrentId, id, vibePickIds);
            expectedPlaylistChangeId = null;
            if (ends && vibeOn) {
                Log.d(TAG, "Queue replaced; Vibe Mode off");
                disable();
            }
        }
        lastCurrentId = id;
        maybeRefill();
    }

    /** Called when playback reaches STATE_ENDED: retry a refill that found nothing earlier. */
    void onPlaybackEnded() {
        maybeRefill();
    }

    /** A restore is about to set the saved queue; its transition is not a user replacement. */
    void expectPlaylistChange(@Nullable String mediaId) {
        expectedPlaylistChangeId = mediaId;
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
        if (!vibeOn) return null;
        return session != null ? session.serialize() : PENDING_SESSION;
    }

    long[] vibePickIds() {
        return toArray(vibePickIds);
    }

    long[] skippedIds() {
        return toArray(skippedIds);
    }

    /** Restores a saved session after the saved queue has been set on the player. */
    void restore(@Nullable String serialized, @NonNull long[] pickIds, @NonNull long[] skipped) {
        if (serialized == null) return;
        VibeSession restored = VibeSession.deserialize(serialized);
        if (restored == null) {
            // Died while m0 was analyzed (or garbage): start again from the current song.
            MediaItem current = player.getCurrentMediaItem();
            if (current != null) {
                Log.d(TAG, "Vibe was on without a session; re-enabling");
                beginSession(current.mediaId, false);
            }
            return;
        }
        generation++;
        MediaItem cur = player.getCurrentMediaItem();
        if (cur != null) lastCurrentId = cur.mediaId;
        vibeOn = true;
        session = restored;
        replaceUpcoming = false;
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
        if (gate.busy(generation)) {
            recheckPending = true;
            return;
        }
        MediaItem current = player.getCurrentMediaItem();
        if (current == null) return;
        int count = player.getMediaItemCount();
        int idx = player.getCurrentMediaItemIndex();

        final int needed;
        final List<double[]> targets = new ArrayList<>();
        final boolean vibe = vibeOn;
        final boolean replace = vibeOn && replaceUpcoming;
        if (vibe) {
            if (session == null) return; // m0 still being analyzed
            needed = VibeQueuePolicy.picksToRequest(replace, count, idx, AHEAD);
            if (needed == 0) return;
            // Plan targets on a copy; the real session advances only when picks land.
            VibeSession plan = VibeSession.deserialize(session.serialize());
            if (plan == null) return;
            for (int i = 0; i < needed; i++) targets.add(plan.nextTarget());
        } else {
            if (!LibraryPreferences.autoplayEnabled(context)) return;
            // Last item: covers shuffle and repeat-all (where a "next" item still exists).
            if (player.hasNextMediaItem()) return;
            needed = AUTOPLAY_PICKS;
        }

        // The pick follows the queue tail (or the current item when replacing upcoming items).
        final int anchorIndex = replace ? idx : count - 1;
        final List<Long> queueIds = new ArrayList<>();
        for (int i = 0; i <= (replace ? idx : count - 1); i++) {
            long id = parseId(player.getMediaItemAt(i).mediaId);
            if (id > 0) queueIds.add(id);
        }
        final long currentId = parseId(current.mediaId);
        final Set<Long> skipped = new HashSet<>(skippedIds);
        final Song anchor = MediaItemFactory.toSong(player.getMediaItemAt(anchorIndex));
        final List<String> recentArtists = new ArrayList<>();
        for (int i = Math.max(0, anchorIndex - 1); i <= anchorIndex; i++) {
            Song s = MediaItemFactory.toSong(player.getMediaItemAt(i));
            if (s != null) recentArtists.add(RecommendationService.artistKey(s));
        }
        final String requestCurrentId = current.mediaId;
        final String requestLastId = player.getMediaItemAt(count - 1).mediaId;
        final int requestCount = count;
        final int gen = generation;

        gate.start(gen);
        recheckPending = false;
        io.execute(() -> {
            List<MediaItem> items = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            List<Long> background = new ArrayList<>();
            try {
                List<VibeQueuePolicy.Relaxation> levels =
                        VibeQueuePolicy.relaxations(currentId, queueIds, skipped, vibe);
                RecommendationService.Pool pool = recommendations.loadPool(levels.get(0).excluded);
                background.addAll(pool.backgroundAnalysisIds);
                double[] currentMood = RecommendationService.storedMood(pool, currentId);
                List<Song> picks = new ArrayList<>();
                for (VibeQueuePolicy.Relaxation level : levels) {
                    int remaining = needed - picks.size();
                    if (remaining <= 0) break;
                    List<double[]> slotTargets = new ArrayList<>();
                    for (int i = 0; i < remaining; i++) {
                        slotTargets.add(level.useCurrentMood || !vibe
                                ? currentMood : targets.get(picks.size() + i));
                    }
                    Set<Long> excl = new HashSet<>(level.excluded);
                    for (Song s : picks) excl.add(s.getId());
                    Song chainAnchor = picks.isEmpty() ? anchor : picks.get(picks.size() - 1);
                    List<String> chainRecent = new ArrayList<>(recentArtists);
                    for (Song s : picks) chainRecent.add(RecommendationService.artistKey(s));
                    picks.addAll(recommendations.pickSequence(pool, chainAnchor, chainRecent,
                            slotTargets, excl));
                }
                for (Song s : picks) {
                    items.add(MediaItemFactory.toMediaItem(context, s));
                    ids.add(s.getId());
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Refill failed", e);
            }
            mainHandler.post(() -> onRefillLanded(gen, vibe, replace, requestCurrentId,
                    requestCount, requestLastId, items, ids));
            // Fire-and-forget: improves the next refill without holding this one.
            recommendations.analyzeInBackground(background, () -> released);
        });
    }

    private void onRefillLanded(int gen, boolean vibe, boolean replace, String requestCurrentId,
                                int requestCount, String requestLastId,
                                List<MediaItem> items, List<Long> ids) {
        boolean owned = gate.finish(gen);
        if (released) return;
        if (gen != generation) {
            // Stale generation: whoever bumped it plans its own refill.
            if (owned) maybeRefill();
            return;
        }
        int count = player.getMediaItemCount();
        MediaItem current = player.getCurrentMediaItem();
        boolean fresh = vibe == vibeOn && replace == (vibeOn && replaceUpcoming)
                && VibeQueuePolicy.landingIsFresh(gen, generation, replace, requestCurrentId,
                current == null ? null : current.mediaId, requestCount, count, requestLastId,
                count > 0 ? player.getMediaItemAt(count - 1).mediaId : null);
        if (!fresh) {
            maybeRefill(); // the queue moved on: plan again against it
            return;
        }
        if (!items.isEmpty()) {
            int idx = player.getCurrentMediaItemIndex();
            int remove = VibeQueuePolicy.itemsToRemoveOnLanding(replace, count, idx, items.size());
            if (remove > 0) player.removeMediaItems(idx + 1, idx + 1 + remove);
            if (replace) replaceUpcoming = false;
            player.addMediaItems(items);
            if (vibe && session != null) {
                for (int i = 0; i < items.size(); i++) session.nextTarget();
                vibePickIds.addAll(ids);
            }
            resumeIfEnded();
            Log.d(TAG, (vibe ? "Vibe" : "Autoplay") + " appended " + ids
                    + (remove > 0 ? " replacing " + remove : "")
                    + " queue=" + player.getMediaItemCount()
                    + " index=" + player.getCurrentMediaItemIndex()
                    + (vibe && session != null ? " step=" + session.step() : ""));
            onStateChanged.run();
        } else {
            // Nothing found even after relaxing; the next transition retries.
            Log.d(TAG, (vibe ? "Vibe" : "Autoplay") + " found no picks; retrying on next transition");
        }
        if (recheckPending) {
            recheckPending = false;
            maybeRefill();
        }
    }

    /** The queue ran out while picking: continue with the first appended item. */
    private void resumeIfEnded() {
        int state = player.getPlaybackState();
        if (state == Player.STATE_ENDED && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
            player.play();
        } else if (state == Player.STATE_IDLE && player.getPlayWhenReady()) {
            player.prepare();
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

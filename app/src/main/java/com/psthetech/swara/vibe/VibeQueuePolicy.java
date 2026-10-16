package com.psthetech.swara.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure queue-fill decisions for Vibe Mode and Autoplay. */
public final class VibeQueuePolicy {

    /** Below this many analyzed eligible songs, a refill analyzes on demand before picking. */
    public static final int MIN_ANALYZED_ELIGIBLE = 15;
    /** At most this many analyses per refill (on demand or in the background). */
    public static final int MAX_ANALYSES_PER_REFILL = 5;
    private static final long TWO_HOURS_MS = 2L * 3_600_000L;
    /** Relaxed exclusion keeps the current item plus this many items at the queue tail. */
    private static final int RELAXED_TAIL = 3;

    private VibeQueuePolicy() {}

    /** Picks to append so {@code ahead} items follow the current one. Never negative. */
    public static int picksNeeded(int itemCount, int currentIndex, int ahead) {
        return Math.max(0, ahead - (itemCount - 1 - currentIndex));
    }

    /**
     * Picks to request. An enabling round replaces the upcoming items, so it asks for a full
     * {@code ahead}; otherwise it tops the queue up.
     */
    public static int picksToRequest(boolean replaceUpcoming, int itemCount, int currentIndex,
                                     int ahead) {
        return replaceUpcoming ? ahead : picksNeeded(itemCount, currentIndex, ahead);
    }

    /**
     * Items after the current one to remove when picks land. Only an enabling round with at
     * least one pick removes anything, so the queue is never emptier than before.
     */
    public static int itemsToRemoveOnLanding(boolean replaceUpcoming, int itemCount,
                                             int currentIndex, int picksLanded) {
        if (!replaceUpcoming || picksLanded <= 0) return 0;
        return Math.max(0, itemCount - 1 - currentIndex);
    }

    /** True when a refill must analyze songs synchronously before picking. */
    public static boolean analyzeOnDemand(int analyzedEligibleCount) {
        return analyzedEligibleCount < MIN_ANALYZED_ELIGIBLE;
    }

    /** Eligible for picking now: not excluded and not played within 2 h. */
    public static boolean eligible(long songId, long lastPlayedMs, @Nullable Set<Long> excluded,
                                   long nowMs) {
        if (excluded != null && excluded.contains(songId)) return false;
        return lastPlayedMs <= 0 || nowMs - lastPlayedMs >= TWO_HOURS_MS;
    }

    /** One step of exclusion relaxation. */
    public static final class Relaxation {
        @NonNull public final Set<Long> excluded;
        /** Fill with the current song's mood instead of the planned targets. */
        public final boolean useCurrentMood;

        Relaxation(@NonNull Set<Long> excluded, boolean useCurrentMood) {
            this.excluded = Collections.unmodifiableSet(excluded);
            this.useCurrentMood = useCurrentMood;
        }
    }

    /**
     * Exclusion sets tried in order when a round returns fewer picks than needed:
     * the whole queue plus session skips; the current item, the last 3 queue items and session
     * skips; the same without session skips; then an Autoplay-style fill at the current mood.
     */
    @NonNull
    public static List<Relaxation> relaxations(long currentId, @NonNull List<Long> queueIds,
                                               @NonNull Set<Long> sessionSkipped,
                                               boolean vibe) {
        Set<Long> skipped = vibe ? sessionSkipped : Collections.<Long>emptySet();
        Set<Long> strict = new HashSet<>(queueIds);
        strict.add(currentId);
        strict.addAll(skipped);

        Set<Long> tail = new HashSet<>();
        tail.add(currentId);
        for (int i = Math.max(0, queueIds.size() - RELAXED_TAIL); i < queueIds.size(); i++) {
            tail.add(queueIds.get(i));
        }
        Set<Long> tailSkipped = new HashSet<>(tail);
        tailSkipped.addAll(skipped);

        List<Relaxation> out = new ArrayList<>();
        out.add(new Relaxation(strict, false));
        out.add(new Relaxation(tailSkipped, false));
        out.add(new Relaxation(tail, false));
        out.add(new Relaxation(new HashSet<>(tail), true));
        return out;
    }

    /**
     * Whether picks computed for a request still apply. Same generation is required. An
     * enabling (replace) round needs the same current item; a top-up round needs the same
     * item count and last item.
     */
    public static boolean landingIsFresh(int requestGeneration, int generation,
                                         boolean replaceUpcoming,
                                         @Nullable String requestCurrentId,
                                         @Nullable String currentId,
                                         int requestCount, int count,
                                         @Nullable String requestLastId,
                                         @Nullable String lastId) {
        if (requestGeneration != generation) return false;
        if (replaceUpcoming) return requestCurrentId != null
                && Objects.equals(requestCurrentId, currentId);
        return requestCount == count && count > 0 && Objects.equals(requestLastId, lastId);
    }
}

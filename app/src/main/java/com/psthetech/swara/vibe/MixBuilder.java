package com.psthetech.swara.vibe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure logic: builds the "For you" mixes from a candidate pool. Every method returns at most
 * {@code size} song ids, best first, with ties broken by song id ascending.
 */
public final class MixBuilder {

    public static final int MIN_MIX_SIZE = 10;
    public static final int DEFAULT_SIZE = 25;

    private static final double FEEL_GOOD_VALENCE = 0.65;
    private static final double FEEL_GOOD_TASTE = 0.5;
    private static final double REDISCOVER_TASTE = 0.6;
    private static final long REDISCOVER_AGE_MS = 30L * 24 * 60 * 60 * 1000;

    private MixBuilder() {
    }

    /** 5-11 -> 0 (morning), 12-16 -> 1 (afternoon), 17-21 -> 2 (evening), otherwise 3. */
    public static int hourBucket(int hour) {
        if (hour >= 5 && hour <= 11) return 0;
        if (hour >= 12 && hour <= 16) return 1;
        if (hour >= 17 && hour <= 21) return 2;
        return 3;
    }

    /** Songs played around this hour before, by hour affinity then taste. */
    public static List<Long> timeOfDay(List<Candidate> pool, TasteProfile t, int hour, int size) {
        List<double[]> keyed = new ArrayList<>();
        List<Candidate> kept = new ArrayList<>();
        for (Candidate c : pool) {
            double h = t.hour(c.songId, hour);
            if (h > 0) {
                kept.add(c);
                keyed.add(new double[]{h, t.combined(c.songId)});
            }
        }
        return top(kept, keyed, size);
    }

    /** Upbeat-valence songs the user likes. */
    public static List<Long> feelGood(List<Candidate> pool, TasteProfile t, int size) {
        List<double[]> keyed = new ArrayList<>();
        List<Candidate> kept = new ArrayList<>();
        for (Candidate c : pool) {
            double comb = t.combined(c.songId);
            if (c.mood != null && c.mood[0] >= FEEL_GOOD_VALENCE && comb > FEEL_GOOD_TASTE) {
                kept.add(c);
                keyed.add(new double[]{comb, 0});
            }
        }
        return top(kept, keyed, size);
    }

    /** Loved songs not played for at least 30 days. */
    public static List<Long> rediscover(List<Candidate> pool, TasteProfile t, long nowMs,
                                        int size) {
        List<double[]> keyed = new ArrayList<>();
        List<Candidate> kept = new ArrayList<>();
        for (Candidate c : pool) {
            double s = t.song(c.songId);
            if (nowMs - c.lastPlayedMs >= REDISCOVER_AGE_MS && s > REDISCOVER_TASTE) {
                kept.add(c);
                keyed.add(new double[]{s, t.combined(c.songId)});
            }
        }
        return top(kept, keyed, size);
    }

    private static List<Long> top(List<Candidate> cands, List<double[]> keys, int size) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < cands.size(); i++) idx.add(i);
        Collections.sort(idx, (a, b) -> {
            double[] ka = keys.get(a);
            double[] kb = keys.get(b);
            int c = Double.compare(kb[0], ka[0]);
            if (c == 0) c = Double.compare(kb[1], ka[1]);
            if (c == 0) c = Long.compare(cands.get(a).songId, cands.get(b).songId);
            return c;
        });
        List<Long> out = new ArrayList<>();
        for (int i : idx) {
            if (out.size() >= size) break;
            out.add(cands.get(i).songId);
        }
        return out;
    }
}

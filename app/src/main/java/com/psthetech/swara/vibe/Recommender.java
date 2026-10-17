package com.psthetech.swara.vibe;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Pure logic: scores candidates and greedily picks songs with hard-filter relaxation. */
public final class Recommender {

    static final double W_MOOD = 0.50;
    static final double W_TASTE = 0.25;
    static final double W_TRANSITION = 0.15;
    static final double W_FRESH = 0.10;
    static final double JITTER = 0.03;

    private static final long TWO_HOURS_MS = 2L * 3_600_000L;
    private static final long THIRTY_MIN_MS = 30L * 60_000L;
    private static final long SEVEN_DAYS_MS = 7L * 24L * 3_600_000L;
    private static final double TEMPO_GAP_BPM = 25.0;
    private static final double TEMPO_TOLERANCE = 0.08;

    private final Random rng;

    public Recommender(Random rng) {
        this.rng = rng;
    }

    /** Returns up to count picks, best first. Never mutates its arguments. */
    public List<Candidate> pick(@Nullable List<Candidate> pool, @Nullable Candidate current,
                                double[] targetMood, @Nullable List<String> recentArtistKeys,
                                @Nullable Set<Long> excluded, long nowMs, int count) {
        Objects.requireNonNull(targetMood, "targetMood");
        List<Candidate> result = new ArrayList<>();
        if (pool == null || pool.isEmpty() || count <= 0) return result;

        List<String> recent = recentArtistKeys == null
                ? new ArrayList<String>() : new ArrayList<>(recentArtistKeys);
        Set<Long> taken = excluded == null ? new HashSet<Long>() : new HashSet<>(excluded);
        Candidate cur = current;
        if (cur != null) taken.add(cur.songId);

        while (result.size() < count) {
            List<Candidate> eligible = null;
            for (int level = 0; level <= 3; level++) {
                eligible = filter(pool, taken, recent, nowMs, level);
                if (!eligible.isEmpty()) break;
            }
            if (eligible == null || eligible.isEmpty()) break;

            Candidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Candidate cand : eligible) {
                double s = W_MOOD * moodFit(cand.mood, targetMood)
                        + W_TASTE * cand.taste
                        + W_TRANSITION * (cur == null ? 0.5 : transition(cur, cand))
                        + W_FRESH * freshness(cand.lastPlayedMs, nowMs)
                        + (rng.nextDouble() * 2 - 1) * JITTER;
                if (s > bestScore) {
                    bestScore = s;
                    best = cand;
                }
            }
            result.add(best);
            recent.add(best.artistKey);
            taken.add(best.songId);
            cur = best;
        }
        return result;
    }

    private static List<Candidate> filter(List<Candidate> pool, Set<Long> taken,
                                          List<String> recent, long nowMs, int level) {
        long window = level == 0 ? TWO_HOURS_MS : THIRTY_MIN_MS;
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : pool) {
            if (c == null || taken.contains(c.songId)) continue;
            if (level <= 2 && c.lastPlayedMs > 0 && nowMs - c.lastPlayedMs < window) continue;
            if (level <= 1 && violatesArtistRule(c, recent)) continue;
            out.add(c);
        }
        return out;
    }

    private static boolean violatesArtistRule(Candidate c, List<String> recent) {
        String k = c.artistKey;
        int n = recent.size();
        if (k == null || k.isEmpty() || n < 2) return false;
        return k.equals(recent.get(n - 1)) && k.equals(recent.get(n - 2));
    }

    public static double moodFit(@Nullable double[] mood, double[] target) {
        if (mood == null) return 0.5;
        double dv = mood[0] - target[0];
        double de = mood[1] - target[1];
        return clamp01(1.0 - Math.sqrt(dv * dv + de * de));
    }

    public static double transition(Candidate a, Candidate b) {
        double t;
        if (a.mood == null || b.mood == null) {
            t = 0.5;
        } else {
            t = 1.0 - Math.abs(a.mood[1] - b.mood[1]);
        }
        if (a.tempoBpm > 0 && b.tempoBpm > 0
                && Math.abs(a.tempoBpm - b.tempoBpm) > TEMPO_GAP_BPM
                && !halfOrDouble(a.tempoBpm, b.tempoBpm)) {
            t -= 0.5;
        }
        return clamp01(t);
    }

    private static boolean halfOrDouble(double a, double b) {
        double hi = Math.max(a, b);
        double lo = Math.min(a, b);
        return Math.abs(hi / lo - 2.0) <= 2.0 * TEMPO_TOLERANCE;
    }

    public static double freshness(long lastPlayedMs, long nowMs) {
        if (lastPlayedMs <= 0) return 1.0;
        long age = nowMs - lastPlayedMs;
        if (age < TWO_HOURS_MS) return 0.0;
        if (age >= SEVEN_DAYS_MS) return 1.0;
        return (double) (age - TWO_HOURS_MS) / (double) (SEVEN_DAYS_MS - TWO_HOURS_MS);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}

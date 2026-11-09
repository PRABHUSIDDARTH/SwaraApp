package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RecommenderTest {

    private static final long NOW = 1_000_000_000_000L;
    private static final long H = 3_600_000L;
    private static final double[] TARGET = {0.7, 0.6};

    private static Candidate c(long id, String artist, double[] mood, double tempo, long lastPlayed) {
        return new Candidate(id, artist, mood, tempo, lastPlayed, 0.5);
    }

    private static List<Candidate> list(Candidate... cs) {
        return new ArrayList<>(Arrays.asList(cs));
    }

    private static List<Candidate> pick(Recommender r, List<Candidate> pool, Candidate cur,
                                        List<String> recent, Set<Long> excl, int n) {
        return r.pick(pool, cur, TARGET, recent, excl, NOW, n);
    }

    @Test
    public void prefersCloserMood() {
        Candidate near = c(1, "a", new double[]{0.7, 0.6}, 0, 0);
        Candidate far = c(2, "b", new double[]{0.1, 0.1}, 0, 0);
        List<Candidate> out = pick(new Recommender(new Random(1)), list(far, near), null,
                Collections.<String>emptyList(), new HashSet<Long>(), 1);
        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).songId);
    }

    @Test
    public void excludesPlayedWithin2h() {
        Candidate recent = c(1, "a", new double[]{0.7, 0.6}, 0, NOW - H);
        Candidate old = c(2, "b", new double[]{0.1, 0.1}, 0, NOW - 10 * H);
        List<Candidate> out = pick(new Recommender(new Random(1)), list(recent, old), null,
                Collections.<String>emptyList(), new HashSet<Long>(), 1);
        assertEquals(2L, out.get(0).songId);
    }

    @Test
    public void noThirdConsecutiveSameArtist() {
        Candidate same = c(1, "a", new double[]{0.7, 0.6}, 0, 0);
        Candidate other = c(2, "b", new double[]{0.1, 0.1}, 0, 0);
        List<Candidate> out = pick(new Recommender(new Random(1)), list(same, other), null,
                Arrays.asList("x", "a", "a"), new HashSet<Long>(), 1);
        assertEquals(2L, out.get(0).songId);
        // only one prior "a": still allowed
        out = pick(new Recommender(new Random(1)), list(same, other), null,
                Arrays.asList("a", "x", "a"), new HashSet<Long>(), 1);
        assertEquals(1L, out.get(0).songId);
    }

    @Test
    public void excludesCurrentAndExcluded() {
        Candidate cur = c(1, "a", new double[]{0.7, 0.6}, 0, 0);
        Candidate ex = c(2, "b", new double[]{0.7, 0.6}, 0, 0);
        Candidate ok = c(3, "c", new double[]{0.0, 0.0}, 0, 0);
        Set<Long> excl = new HashSet<>(Collections.singletonList(2L));
        List<Candidate> out = pick(new Recommender(new Random(1)), list(cur, ex, ok), cur,
                Collections.<String>emptyList(), excl, 5);
        assertEquals(1, out.size());
        assertEquals(3L, out.get(0).songId);
        assertEquals(1, excl.size());
    }

    @Test
    public void halfTempo_isCompatible() {
        Candidate a = c(1, "a", new double[]{0.5, 0.5}, 70, 0);
        Candidate half = c(2, "b", new double[]{0.5, 0.5}, 140, 0);
        Candidate clash = c(3, "b", new double[]{0.5, 0.5}, 100, 0);
        assertEquals(1.0, Recommender.transition(a, half), 1e-9);
        assertEquals(0.5, Recommender.transition(a, clash), 1e-9);
        Candidate noTempo = c(4, "b", new double[]{0.5, 0.5}, 0, 0);
        assertEquals(1.0, Recommender.transition(a, noTempo), 1e-9);
        Candidate noMood = c(5, "b", null, 70, 0);
        assertEquals(0.5, Recommender.transition(a, noMood), 1e-9);
    }

    @Test
    public void tinyLibrary_relaxesAndStillReturns() {
        Candidate a = c(1, "a", new double[]{0.7, 0.6}, 0, NOW - H);
        Candidate b = c(2, "b", new double[]{0.7, 0.6}, 0, NOW - H);
        List<Candidate> out = pick(new Recommender(new Random(1)), list(a, b), null,
                Collections.<String>emptyList(), new HashSet<Long>(), 1);
        assertEquals(1, out.size());
    }

    @Test
    public void relaxationLevelsAndExhaustion() {
        Candidate a = c(1, "a", null, 0, NOW - 10 * 60_000L); // 10 min ago
        List<Candidate> out = pick(new Recommender(new Random(1)), list(a), null,
                Collections.<String>emptyList(), new HashSet<Long>(), 3);
        assertEquals(1, out.size());
        Candidate b = c(2, "a", null, 0, 0);
        out = pick(new Recommender(new Random(1)), list(b), null,
                Arrays.asList("a", "a"), new HashSet<Long>(), 3);
        assertEquals(1, out.size());
    }

    @Test
    public void emptyPool_returnsEmpty() {
        Recommender r = new Recommender(new Random(1));
        assertTrue(pick(r, new ArrayList<Candidate>(), null, Collections.<String>emptyList(),
                new HashSet<Long>(), 3).isEmpty());
        assertTrue(pick(r, null, null, Collections.<String>emptyList(),
                new HashSet<Long>(), 3).isEmpty());
    }

    @Test
    public void seededRng_isDeterministic() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            pool.add(c(i, "art" + (i % 5), new double[]{0.5 + i * 0.005, 0.5}, 0, 0));
        }
        List<Candidate> x = pick(new Recommender(new Random(42)), pool, null,
                Collections.<String>emptyList(), new HashSet<Long>(), 8);
        List<Candidate> y = pick(new Recommender(new Random(42)), pool, null,
                Collections.<String>emptyList(), new HashSet<Long>(), 8);
        assertEquals(8, x.size());
        for (int i = 0; i < 8; i++) assertEquals(x.get(i).songId, y.get(i).songId);
        assertEquals(20, pool.size());
    }

    @Test
    public void moodFitAndFreshness() {
        assertEquals(0.5, Recommender.moodFit(null, TARGET), 1e-9);
        assertEquals(1.0, Recommender.moodFit(new double[]{0.7, 0.6}, TARGET), 1e-9);
        assertEquals(0.0, Recommender.moodFit(new double[]{-5, -5}, TARGET), 1e-9);
        assertEquals(1.0, Recommender.freshness(0, NOW), 1e-9);
        assertEquals(0.0, Recommender.freshness(NOW - H, NOW), 1e-9);
        assertEquals(1.0, Recommender.freshness(NOW - 8 * 24 * H, NOW), 1e-9);
        double mid = Recommender.freshness(NOW - (2 * H + (7 * 24 * H - 2 * H) / 2), NOW);
        assertEquals(0.5, mid, 1e-6);
    }

    @Test
    public void transition_isSymmetric() {
        double[][] pairs = {{100, 185}, {70, 140}, {120, 128}};
        for (double[] p : pairs) {
            Candidate a = c(1, "a", new double[]{0.5, 0.5}, p[0], 0);
            Candidate b = c(2, "b", new double[]{0.5, 0.5}, p[1], 0);
            assertEquals(Recommender.transition(a, b), Recommender.transition(b, a), 1e-12);
        }
        Candidate a = c(1, "a", new double[]{0.5, 0.5}, 100, 0);
        Candidate b = c(2, "b", new double[]{0.5, 0.5}, 185, 0);
        assertEquals(1.0, Recommender.transition(b, a), 1e-9);
    }

    @Test
    public void played45MinAgo_ineligibleAtL0_butChosenAtL1() {
        Candidate mid = c(1, "a", new double[]{0.7, 0.6}, 0, NOW - 45 * 60_000L);
        Candidate old = c(2, "b", new double[]{0.0, 0.0}, 0, 0);
        List<Candidate> out = pick(new Recommender(new Random(1)), list(mid, old), null,
                Collections.<String>emptyList(), new HashSet<Long>(), 1);
        assertEquals(2L, out.get(0).songId);
        out = pick(new Recommender(new Random(1)), list(mid), null,
                Collections.<String>emptyList(), new HashSet<Long>(), 1);
        assertEquals(1, out.size());
        assertEquals(1L, out.get(0).songId);
    }

    @Test
    public void onlyCurrentSong_returnsEmpty() {
        Candidate cur = c(1, "a", new double[]{0.7, 0.6}, 0, 0);
        assertTrue(pick(new Recommender(new Random(1)), list(cur), cur,
                Collections.<String>emptyList(), new HashSet<Long>(), 3).isEmpty());
    }

    @Test
    public void countLargerThanPool_returnsEachOnce() {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 4; i++) pool.add(c(i, "a", new double[]{0.5, 0.5}, 0, 0));
        List<Candidate> out = pick(new Recommender(new Random(3)), pool, null,
                Collections.<String>emptyList(), new HashSet<Long>(), 10);
        assertEquals(4, out.size());
        Set<Long> ids = new HashSet<>();
        for (Candidate x : out) ids.add(x.songId);
        assertEquals(4, ids.size());
    }

    @Test
    public void recentArtistKeys_notMutated() {
        List<String> recent = new ArrayList<>(Arrays.asList("x", "y"));
        pick(new Recommender(new Random(1)),
                list(c(1, "a", null, 0, 0), c(2, "b", null, 0, 0)), null, recent,
                new HashSet<Long>(), 2);
        assertEquals(Arrays.asList("x", "y"), recent);
    }
}

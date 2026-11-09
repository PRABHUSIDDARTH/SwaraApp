package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PersonalityTest {
    static WrappedStats.Builder base() {
        WrappedStats.Builder b = new WrappedStats.Builder();
        b.period = Period.month(2026, 10);
        b.totalMs = 1_000_000;
        b.topSongMs = 10_000;   // 1% — not a Loyalist
        b.distinctSongs = 10;
        return b;
    }

    @Test
    public void nightOwl_atThreshold() {
        WrappedStats.Builder b = base();
        b.nightMs = 400_000;
        assertEquals(Personality.NIGHT_OWL, Personality.of(new WrappedStats(b)));
        b.nightMs = 399_999;
        assertEquals(Personality.BALANCED, Personality.of(new WrappedStats(b)));
    }

    @Test
    public void earlyRiser_atThreshold() {
        WrappedStats.Builder b = base();
        b.morningMs = 400_000;
        assertEquals(Personality.EARLY_RISER, Personality.of(new WrappedStats(b)));
        b.morningMs = 399_999;
        assertEquals(Personality.BALANCED, Personality.of(new WrappedStats(b)));
    }

    @Test
    public void moodLifter_fiveVibeStarts() {
        WrappedStats.Builder b = base();
        b.vibeStarts = 5;
        assertEquals(Personality.MOOD_LIFTER, Personality.of(new WrappedStats(b)));
        b.vibeStarts = 4;
        assertEquals(Personality.BALANCED, Personality.of(new WrappedStats(b)));
    }

    @Test
    public void loyalist_topSong15Percent() {
        WrappedStats.Builder b = base();
        b.topSongMs = 150_000;
        assertEquals(Personality.LOYALIST, Personality.of(new WrappedStats(b)));
        b.topSongMs = 149_999;
        assertEquals(Personality.BALANCED, Personality.of(new WrappedStats(b)));
    }

    @Test
    public void explorer_thirtyPercentNew() {
        WrappedStats.Builder b = base();
        b.newSongs = 3;
        assertEquals(Personality.EXPLORER, Personality.of(new WrappedStats(b)));
        b.newSongs = 2;
        assertEquals(Personality.BALANCED, Personality.of(new WrappedStats(b)));
    }

    @Test
    public void energySeeker_fortyPercentEnergetic() {
        WrappedStats.Builder b = base();
        b.moodKnownMs = 500_000;
        b.moodMs.put(MoodBucket.ENERGETIC, 200_000L);
        assertEquals(Personality.ENERGY_SEEKER, Personality.of(new WrappedStats(b)));
        b.moodMs.put(MoodBucket.ENERGETIC, 199_999L);
        assertEquals(Personality.BALANCED, Personality.of(new WrappedStats(b)));
    }

    @Test
    public void ruleOrder_nightOwlBeatsMoodLifter() {
        WrappedStats.Builder b = base();
        b.nightMs = 500_000;
        b.vibeStarts = 9;
        assertEquals(Personality.NIGHT_OWL, Personality.of(new WrappedStats(b)));
    }

    @Test
    public void zeroListening_isBalanced() {
        WrappedStats.Builder b = new WrappedStats.Builder();
        b.period = Period.month(2026, 10);
        assertEquals(Personality.BALANCED, Personality.of(new WrappedStats(b)));
    }
}

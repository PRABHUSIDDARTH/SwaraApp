package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SlidePlanTest {
    private static WrappedStats.Builder rich() {
        WrappedStats.Builder b = new WrappedStats.Builder();
        b.period = Period.month(2026, 10);
        b.totalMs = 10_000_000;
        b.topSongIds.addAll(Arrays.asList(1L, 2L, 3L));
        b.topArtistKeys.addAll(Arrays.asList("a", "b", "c"));
        b.topArtistNames.addAll(Arrays.asList("A", "B", "C"));
        b.topAlbumId = 7;
        b.moodKnownMs = 5_000_000;
        b.vibeStarts = 1;
        b.listeningDays = 3;
        b.mostRepeatedSongId = 1;
        b.newSongs = 1;
        return b;
    }

    @Test
    public void under30Minutes_empty() {
        WrappedStats.Builder b = rich();
        b.totalMs = 1_799_999;
        assertTrue(SlidePlan.plan(new WrappedStats(b)).isEmpty());
    }

    @Test
    public void orderMatchesSpec_whenEverythingQualifies() {
        assertEquals(Arrays.asList(SlideType.values()), SlidePlan.plan(new WrappedStats(rich())));
    }

    @Test
    public void alwaysSlidesPresent_withMinimalData() {
        WrappedStats.Builder b = new WrappedStats.Builder();
        b.period = Period.month(2026, 10);
        b.totalMs = 1_800_000;
        List<SlideType> p = SlidePlan.plan(new WrappedStats(b));
        assertEquals(Arrays.asList(SlideType.INTRO, SlideType.MINUTES, SlideType.PERSONALITY, SlideType.SUMMARY), p);
    }

    @Test
    public void moodSkippedBelow50Percent() {
        WrappedStats.Builder b = rich();
        b.moodKnownMs = 4_999_999;
        List<SlideType> p = SlidePlan.plan(new WrappedStats(b));
        assertFalse(p.contains(SlideType.MOOD_MIX));
        assertFalse(p.contains(SlideType.MOOD_OF_PERIOD));
    }

    @Test
    public void topSongsNeedThree() {
        WrappedStats.Builder b = rich();
        b.topSongIds.remove(2);
        assertFalse(SlidePlan.plan(new WrappedStats(b)).contains(SlideType.TOP_SONGS));
    }

    @Test
    public void vibeSkippedWhenZero() {
        WrappedStats.Builder b = rich();
        b.vibeStarts = 0;
        assertFalse(SlidePlan.plan(new WrappedStats(b)).contains(SlideType.VIBE));
    }

    @Test
    public void habitsNeedThreeDays() {
        WrappedStats.Builder b = rich();
        b.listeningDays = 2;
        assertFalse(SlidePlan.plan(new WrappedStats(b)).contains(SlideType.HABITS));
    }

    @Test
    public void repeatSkipAndDiscovery_skippedWhenNothingQualifies() {
        WrappedStats.Builder b = rich();
        b.mostRepeatedSongId = -1;
        b.newSongs = 0;
        List<SlideType> p = SlidePlan.plan(new WrappedStats(b));
        assertFalse(p.contains(SlideType.REPEAT_SKIP));
        assertFalse(p.contains(SlideType.DISCOVERY));
    }
}

package com.psthetech.swara.vibe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MoodResolverTest {

    private static final double EPS = 1e-12;

    @Test
    public void override_winsOverAnalyzedMood() {
        double[] analyzed = {0.9, 0.9};
        assertArrayEquals(new double[]{0.15, 0.25},
                MoodResolver.resolve(analyzed, MoodLabel.SAD), EPS);
        assertArrayEquals(new double[]{0.9, 0.9}, analyzed, EPS); // input untouched
    }

    @Test
    public void override_appliesWhenNothingWasAnalyzed() {
        // Review Focus 1: no OK moods in the library yet.
        assertArrayEquals(new double[]{0.85, 0.90},
                MoodResolver.resolve(null, MoodLabel.ENERGETIC), EPS);
    }

    @Test
    public void noOverride_returnsACopyOfAnalyzed() {
        double[] analyzed = {0.3, 0.7};
        double[] out = MoodResolver.resolve(analyzed, null);
        assertArrayEquals(analyzed, out, EPS);
        assertNotSame(analyzed, out);
    }

    @Test
    public void neither_isUnknown() {
        assertNull(MoodResolver.resolve(null, null));
    }
}

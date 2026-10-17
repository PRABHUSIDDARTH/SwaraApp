package com.psthetech.swara.vibe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class MoodLabelTest {

    private static final double EPS = 1e-12;

    @Test
    public void labels_haveTheSpecifiedValenceAndEnergy() {
        assertArrayEquals(new double[]{0.15, 0.25}, MoodLabel.SAD.mood(), EPS);
        assertArrayEquals(new double[]{0.50, 0.25}, MoodLabel.CALM.mood(), EPS);
        assertArrayEquals(new double[]{0.80, 0.60}, MoodLabel.HAPPY.mood(), EPS);
        assertArrayEquals(new double[]{0.85, 0.90}, MoodLabel.ENERGETIC.mood(), EPS);
        assertEquals(4, MoodLabel.values().length);
    }

    @Test
    public void mood_returnsAFreshArray() {
        double[] a = MoodLabel.HAPPY.mood();
        a[0] = 0;
        assertNotSame(a, MoodLabel.HAPPY.mood());
        assertEquals(0.80, MoodLabel.HAPPY.mood()[0], EPS);
    }

    @Test
    public void fromName_roundTripsAndRejectsUnknown() {
        for (MoodLabel l : MoodLabel.values()) assertSame(l, MoodLabel.fromName(l.name()));
        assertNull(MoodLabel.fromName(null));
        assertNull(MoodLabel.fromName(""));
        assertNull(MoodLabel.fromName("sad"));
        assertNull(MoodLabel.fromName("ROMANTIC"));
    }
}

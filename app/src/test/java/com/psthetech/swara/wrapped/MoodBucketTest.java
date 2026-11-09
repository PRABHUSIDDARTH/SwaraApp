package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MoodBucketTest {
    @Test
    public void boundaries_inSpecOrder() {
        assertEquals(MoodBucket.SAD, MoodBucket.of(new double[]{0.349, 0.9}));      // sad wins over energetic
        assertEquals(MoodBucket.ENERGETIC, MoodBucket.of(new double[]{0.35, 0.70}));
        assertEquals(MoodBucket.CALM, MoodBucket.of(new double[]{0.59, 0.699}));
        assertEquals(MoodBucket.HAPPY, MoodBucket.of(new double[]{0.60, 0.5}));
        assertEquals(MoodBucket.CALM, MoodBucket.of(new double[]{0.35, 0.2}));
    }

    @Test
    public void unknownMood_isNull() {
        assertNull(MoodBucket.of(null));
        assertNull(MoodBucket.of(new double[]{Double.NaN, 0.5}));
    }
}

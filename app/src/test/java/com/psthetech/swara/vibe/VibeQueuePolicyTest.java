package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VibeQueuePolicyTest {

    @Test
    public void picksNeeded_fillsToTwoAhead() {
        assertEquals(2, VibeQueuePolicy.picksNeeded(5, 4, 2));
        assertEquals(1, VibeQueuePolicy.picksNeeded(5, 3, 2));
        assertEquals(0, VibeQueuePolicy.picksNeeded(5, 1, 2));
    }

    @Test
    public void picksNeeded_singleItemQueueNeedsTwo() {
        assertEquals(2, VibeQueuePolicy.picksNeeded(1, 0, 2));
    }
}

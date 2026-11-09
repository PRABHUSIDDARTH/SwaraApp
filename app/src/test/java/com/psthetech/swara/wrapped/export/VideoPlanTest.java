package com.psthetech.swara.wrapped.export;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VideoPlanTest {
    private final VideoPlan plan = new VideoPlan(3, 30, 3);

    @Test
    public void totalFrames() {
        assertEquals(270, plan.totalFrames());
        assertEquals(9_000_000L, plan.presentationTimeUs(270));
    }

    @Test
    public void slideIndexPerFrame() {
        assertEquals(0, plan.slideAt(0));
        assertEquals(0, plan.slideAt(89));
        assertEquals(1, plan.slideAt(90));
        assertEquals(2, plan.slideAt(269));
    }

    @Test
    public void progressRisesOver900msThenHolds() {
        assertEquals(0f, plan.progressAt(0), 1e-6);
        assertEquals(0.5f, plan.progressAt(13), 0.05);   // ~433 ms into a 900 ms entrance
        assertEquals(1f, plan.progressAt(27), 1e-6);    // 900 ms
        assertEquals(1f, plan.progressAt(80), 1e-6);
        assertEquals(0f, plan.progressAt(90), 1e-6);    // next slide restarts
    }

    @Test
    public void presentationTimesAreFrameSpaced() {
        assertEquals(0L, plan.presentationTimeUs(0));
        assertEquals(33_333L, plan.presentationTimeUs(1));
    }
}

package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Test
    public void onDemandAnalysis_onlyBelowFifteenAnalyzedEligible() {
        assertTrue(VibeQueuePolicy.analyzeOnDemand(0));
        assertTrue(VibeQueuePolicy.analyzeOnDemand(14));
        assertFalse(VibeQueuePolicy.analyzeOnDemand(15));
        assertFalse(VibeQueuePolicy.analyzeOnDemand(400));
    }

    @Test
    public void eligibleForAnalysis_respectsExclusionAndTwoHourRule() {
        long now = 10L * 3_600_000L;
        Set<Long> excluded = new HashSet<>(Collections.singletonList(7L));
        assertFalse(VibeQueuePolicy.eligible(7L, 0L, excluded, now));
        assertFalse(VibeQueuePolicy.eligible(8L, now - 3_600_000L, excluded, now));
        assertTrue(VibeQueuePolicy.eligible(8L, now - 3L * 3_600_000L, excluded, now));
        assertTrue(VibeQueuePolicy.eligible(8L, 0L, excluded, now));
    }

    @Test
    public void enable_requestsFullFillButRemovesUpcomingOnlyWhenPicksLand() {
        // Queue of 6 with current index 1: four upcoming items.
        assertEquals(2, VibeQueuePolicy.picksToRequest(true, 6, 1, 2));
        assertEquals(0, VibeQueuePolicy.picksToRequest(false, 6, 1, 2));
        // Nothing landed: keep the user's upcoming items.
        assertEquals(0, VibeQueuePolicy.itemsToRemoveOnLanding(true, 6, 1, 0));
        // Picks landed for the enabling round: replace the four upcoming items.
        assertEquals(4, VibeQueuePolicy.itemsToRemoveOnLanding(true, 6, 1, 2));
        // Ordinary refill never removes.
        assertEquals(0, VibeQueuePolicy.itemsToRemoveOnLanding(false, 6, 1, 2));
    }

    @Test
    public void exclusionLevels_relaxInOrder() {
        List<Long> queue = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L);
        Set<Long> skipped = new HashSet<>(Arrays.asList(90L, 91L));
        List<VibeQueuePolicy.Relaxation> levels =
                VibeQueuePolicy.relaxations(2L, queue, skipped, true);
        assertEquals(4, levels.size());
        // 0: whole queue + skipped
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 90L, 91L)), levels.get(0).excluded);
        assertFalse(levels.get(0).useCurrentMood);
        // 1: current + last 3 + skipped
        assertEquals(new HashSet<>(Arrays.asList(2L, 4L, 5L, 6L, 90L, 91L)), levels.get(1).excluded);
        assertFalse(levels.get(1).useCurrentMood);
        // 2: current + last 3
        assertEquals(new HashSet<>(Arrays.asList(2L, 4L, 5L, 6L)), levels.get(2).excluded);
        assertFalse(levels.get(2).useCurrentMood);
        // 3: autoplay-style fallback at the current mood
        assertEquals(new HashSet<>(Arrays.asList(2L, 4L, 5L, 6L)), levels.get(3).excluded);
        assertTrue(levels.get(3).useCurrentMood);
    }

    @Test
    public void exclusionLevels_autoplayHasNoSessionSkips() {
        List<VibeQueuePolicy.Relaxation> levels = VibeQueuePolicy.relaxations(
                1L, Arrays.asList(1L, 2L), new HashSet<>(Collections.singletonList(9L)), false);
        assertFalse(levels.get(0).excluded.contains(9L));
        assertTrue(levels.get(levels.size() - 1).useCurrentMood);
    }

    @Test
    public void landingFreshness() {
        // Ordinary refill: same generation, same tail.
        assertTrue(VibeQueuePolicy.landingIsFresh(3, 3, false, "a", "b", 4, 4, "d", "d"));
        assertFalse(VibeQueuePolicy.landingIsFresh(3, 4, false, "a", "a", 4, 4, "d", "d"));
        assertFalse(VibeQueuePolicy.landingIsFresh(3, 3, false, "a", "a", 4, 5, "d", "e"));
        // Replace landing: current item must be unchanged; tail may differ.
        assertTrue(VibeQueuePolicy.landingIsFresh(3, 3, true, "a", "a", 6, 7, "f", "g"));
        assertFalse(VibeQueuePolicy.landingIsFresh(3, 3, true, "a", "b", 6, 6, "f", "f"));
    }

    @Test
    public void refillGate_generationOwnership() {
        RefillGate gate = new RefillGate();
        assertFalse(gate.busy(1));
        gate.start(1);
        assertTrue(gate.busy(1));
        // Generation changed: a new refill may start immediately.
        assertFalse(gate.busy(2));
        gate.start(2);
        // The stale landing does not own the gate and must not clear it.
        assertFalse(gate.finish(1));
        assertTrue(gate.busy(2));
        assertTrue(gate.finish(2));
        assertFalse(gate.busy(2));
    }

    @Test
    public void playlistChange_endsVibeOnlyOnRealReplacement() {
        java.util.Set<Long> picks = new java.util.HashSet<>(java.util.Arrays.asList(7L, 8L));
        // tag edit of the playing song: same media id
        org.junit.Assert.assertFalse(VibeQueuePolicy.playlistChangeEndsVibe(null, "5", "5", picks));
        // expected by the controller (start-from / restore)
        org.junit.Assert.assertFalse(VibeQueuePolicy.playlistChangeEndsVibe("9", "5", "9", picks));
        // playing item removed, next Vibe pick took over
        org.junit.Assert.assertFalse(VibeQueuePolicy.playlistChangeEndsVibe(null, "5", "7", picks));
        // user replaced the queue with something else
        org.junit.Assert.assertTrue(VibeQueuePolicy.playlistChangeEndsVibe(null, "5", "42", picks));
        org.junit.Assert.assertTrue(VibeQueuePolicy.playlistChangeEndsVibe("9", "5", "42", picks));
        // queue cleared
        org.junit.Assert.assertTrue(VibeQueuePolicy.playlistChangeEndsVibe(null, "5", null, picks));
        org.junit.Assert.assertTrue(VibeQueuePolicy.playlistChangeEndsVibe(null, "5", "x", picks));
    }
}

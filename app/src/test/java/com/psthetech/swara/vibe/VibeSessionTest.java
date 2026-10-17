package com.psthetech.swara.vibe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VibeSessionTest {

    private static final double EPS = 1e-9;

    @Test
    public void holdsStartMoodForTwoPicks() {
        VibeSession s = new VibeSession(0.3, 0.4);
        assertArrayEquals(new double[]{0.3, 0.4}, s.nextTarget(), 0);
        assertArrayEquals(new double[]{0.3, 0.4}, s.nextTarget(), 0);
        assertEquals(0, s.step());
        double[] third = s.nextTarget();
        assertEquals(1, s.step());
        assertTrue(third[0] > 0.3);
    }

    @Test
    public void valenceRisesMonotonicallyToGoal() {
        VibeSession s = new VibeSession(0.1, 0.2);
        double prev = 0.1;
        double last = 0;
        for (int i = 0; i < 20; i++) {
            double[] t = s.nextTarget();
            assertTrue(t[0] >= prev - EPS);
            assertTrue(t[0] - prev <= 0.08 + EPS);
            prev = t[0];
            last = t[0];
        }
        assertEquals(0.70, last, 1e-6);
    }

    @Test
    public void energyHeadsIntoBand() {
        VibeSession hi = new VibeSession(0.4, 0.9);
        VibeSession lo = new VibeSession(0.4, 0.2);
        double[] a = null, b = null;
        for (int i = 0; i < 25; i++) { a = hi.nextTarget(); b = lo.nextTarget(); }
        assertEquals(0.65, a[1], 1e-6);
        assertEquals(0.55, b[1], 1e-6);
    }

    @Test
    public void aboveGoal_neverFalls() {
        VibeSession s = new VibeSession(0.9, 0.5);
        for (int i = 0; i < 20; i++) {
            assertTrue(s.nextTarget()[0] >= 0.9 - EPS);
        }
    }

    @Test
    public void earlySkip_doesNotAdvanceAndHalvesStep() {
        VibeSession a = new VibeSession(0.1, 0.5);
        VibeSession b = new VibeSession(0.1, 0.5);
        for (int i = 0; i < 4; i++) { a.nextTarget(); b.nextTarget(); }
        double[] before = a.nextTarget();
        b.nextTarget();
        int k = a.step();
        a.onEarlySkip();
        double[] skipped = a.nextTarget();
        assertEquals(k, a.step());
        double[] normal = b.nextTarget();
        assertEquals(k + 1, b.step());
        assertTrue(skipped[0] >= before[0] - EPS);
        assertTrue(skipped[0] - before[0] <= 0.04 + EPS);
        assertTrue(skipped[0] < normal[0]);
    }

    @Test
    public void twoSkips_stepBack() {
        VibeSession s = new VibeSession(0.1, 0.5);
        for (int i = 0; i < 6; i++) s.nextTarget();
        assertEquals(4, s.step());
        s.onEarlySkip();
        s.onEarlySkip();
        assertEquals(3, s.step());
        s.nextTarget();
        assertEquals(3, s.step());
        // acceptance resets the counter: skip, accept, skip does not step back
        s.onPickAccepted();
        s.onEarlySkip();
        s.onPickAccepted();
        s.onEarlySkip();
        assertEquals(3, s.step());
    }

    @Test
    public void stepBackClampsAtZero() {
        VibeSession s = new VibeSession(0.1, 0.5);
        s.onEarlySkip();
        s.onEarlySkip();
        assertEquals(0, s.step());
    }

    @Test
    public void nanAndOutOfRangeInputsClamp() {
        VibeSession s = new VibeSession(Double.NaN, 5);
        assertEquals(0.5, s.v0(), 0);
        assertEquals(1.0, s.e0(), 0);
    }

    @Test
    public void serializeRoundTrip_preservesStep() {
        VibeSession s = new VibeSession(0.15, 0.8);
        for (int i = 0; i < 5; i++) s.nextTarget();
        s.onEarlySkip();
        VibeSession r = VibeSession.deserialize(s.serialize());
        assertNotNull(r);
        assertEquals(s.step(), r.step());
        assertEquals(s.v0(), r.v0(), 0);
        for (int i = 0; i < 15; i++) {
            assertArrayEquals(s.nextTarget(), r.nextTarget(), 0);
            if (i == 3) { s.onEarlySkip(); r.onEarlySkip(); }
        }
    }

    @Test
    public void deserializeGarbageReturnsNull() {
        assertNull(VibeSession.deserialize(null));
        assertNull(VibeSession.deserialize(""));
        assertNull(VibeSession.deserialize("junk"));
        assertNull(VibeSession.deserialize("v1;a;b;c;d;e;f;g;h"));
        assertNull(VibeSession.deserialize("v1;0.1;0.2;99;0;0.1;0.2;0;0"));
    }

    private static double curV(VibeSession s) {
        return Double.parseDouble(s.serialize().split(";")[5]);
    }

    private static double curE(VibeSession s) {
        return Double.parseDouble(s.serialize().split(";")[6]);
    }

    @Test
    public void twoSkipRollback_neverRaisesValenceWhenCapLags() {
        VibeSession s = new VibeSession(0.0, 0.9);
        double v = 0;
        for (int i = 0; i < 8; i++) v = s.nextTarget()[0]; // 2 holds + steps 1..6
        assertEquals(6, s.step());
        double vBefore = curV(s);
        double eBefore = curE(s);
        assertEquals(v, vBefore, EPS);
        s.onEarlySkip();
        s.onEarlySkip();
        assertEquals(5, s.step());
        assertTrue("valence must not rise on rollback", curV(s) <= vBefore + EPS);
        // energy moves back toward e0 (0.9), never further toward the goal
        assertTrue(curE(s) >= eBefore - EPS);
        assertTrue(curE(s) <= 0.9 + EPS);
        assertTrue(s.nextTarget()[0] <= vBefore + 0.04 + EPS);
    }
}

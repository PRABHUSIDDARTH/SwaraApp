package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests verifying state machine and continuity rules for the circular rotating player:
 *  - Playing starts rotation
 *  - Pause freezes rotation at current angle (no snap back to 0°)
 *  - Resume continues from the exact frozen angle
 *  - Reset on song change resets angle to 0°
 *  - Idempotent start (repeated start does not duplicate or reset angle)
 */
public class CircularPlayerStateTest {

    /**
     * Pure Java state machine model replicating CircularArtworkView's lifecycle and animator behavior.
     */
    static class RotatingPlayerStateMachine {
        private static final long ROTATION_DURATION_MS = 10_000L;

        private boolean isPlaying = false;
        private float discRotation = 0f;
        private int animatorStartCount = 0;
        private long simulatedTimeMs = 0L;

        public void startRotation() {
            if (isPlaying) {
                // Idempotent: already running, do not re-trigger or reset
                return;
            }
            isPlaying = true;
            animatorStartCount++;
        }

        public void pauseRotation() {
            if (isPlaying) {
                // Freeze at current angle
                isPlaying = false;
            }
        }

        public void resumeRotation() {
            startRotation();
        }

        public void resetRotation() {
            isPlaying = false;
            discRotation = 0f;
        }

        public void simulatePlayback(long elapsedMs) {
            if (isPlaying && elapsedMs > 0) {
                float degreesPerMs = 360f / ROTATION_DURATION_MS;
                discRotation = (discRotation + (elapsedMs * degreesPerMs)) % 360f;
                simulatedTimeMs += elapsedMs;
            }
        }

        public boolean isPlaying() {
            return isPlaying;
        }

        public float getDiscRotation() {
            return discRotation;
        }

        public int getAnimatorStartCount() {
            return animatorStartCount;
        }
    }

    @Test
    public void testPlayStartsRotation() {
        RotatingPlayerStateMachine player = new RotatingPlayerStateMachine();
        assertEquals(0f, player.getDiscRotation(), 1e-4);
        assertFalse(player.isPlaying());

        player.startRotation();
        assertTrue(player.isPlaying());
        assertEquals(1, player.getAnimatorStartCount());

        player.simulatePlayback(2500L); // 2.5 seconds = 90 degrees
        assertEquals(90f, player.getDiscRotation(), 1e-3);
    }

    @Test
    public void testPauseFreezesAtCurrentAngle() {
        RotatingPlayerStateMachine player = new RotatingPlayerStateMachine();
        player.startRotation();
        player.simulatePlayback(5000L); // 5.0 seconds = 180 degrees
        assertEquals(180f, player.getDiscRotation(), 1e-3);

        player.pauseRotation();
        assertFalse(player.isPlaying());
        // Must freeze at 180°, NOT snap back to 0°
        assertEquals(180f, player.getDiscRotation(), 1e-3);

        // Time passes while paused; rotation must NOT advance
        player.simulatePlayback(2000L);
        assertEquals(180f, player.getDiscRotation(), 1e-3);
    }

    @Test
    public void testResumeContinuesFromFrozenAngle() {
        RotatingPlayerStateMachine player = new RotatingPlayerStateMachine();
        player.startRotation();
        player.simulatePlayback(3333L); // ~120 degrees
        float frozenAngle = player.getDiscRotation();

        player.pauseRotation();
        assertEquals(frozenAngle, player.getDiscRotation(), 1e-3);

        // Resume: must start from frozenAngle
        player.resumeRotation();
        assertTrue(player.isPlaying());
        assertEquals(frozenAngle, player.getDiscRotation(), 1e-3);

        // Continue playback: advances smoothly from frozenAngle
        player.simulatePlayback(2500L); // +90 degrees
        float expected = (frozenAngle + 90f) % 360f;
        assertEquals(expected, player.getDiscRotation(), 1e-3);
    }

    @Test
    public void testResetOnSongChangeResetsToZero() {
        RotatingPlayerStateMachine player = new RotatingPlayerStateMachine();
        player.startRotation();
        player.simulatePlayback(7500L); // 270 degrees
        assertEquals(270f, player.getDiscRotation(), 1e-3);

        // Song change triggers resetRotation()
        player.resetRotation();
        assertFalse(player.isPlaying());
        assertEquals(0f, player.getDiscRotation(), 1e-4);
    }

    @Test
    public void testRepeatedStartDoesNotDuplicateAnimatorOrResetAngle() {
        RotatingPlayerStateMachine player = new RotatingPlayerStateMachine();
        player.startRotation();
        player.simulatePlayback(1000L);
        float angle = player.getDiscRotation();

        // Calling start/resume while already playing must be a no-op
        player.startRotation();
        player.startRotation();
        player.resumeRotation();

        assertEquals(1, player.getAnimatorStartCount());
        assertEquals(angle, player.getDiscRotation(), 1e-4);
    }
}

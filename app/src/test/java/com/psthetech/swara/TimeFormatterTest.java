package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.util.TimeFormatter;

import org.junit.Test;

/**
 * Unit tests for TimeFormatter and progress calculation safety.
 */
public class TimeFormatterTest {

    @Test
    public void testZeroMs() {
        assertEquals("0:00", TimeFormatter.formatMs(0L));
    }

    @Test
    public void testSevenSeconds() {
        assertEquals("0:07", TimeFormatter.formatMs(7000L));
    }

    @Test
    public void testFortyTwoSeconds() {
        assertEquals("0:42", TimeFormatter.formatMs(42000L));
    }

    @Test
    public void testOneMinuteThreeSeconds() {
        assertEquals("1:03", TimeFormatter.formatMs(63000L));
    }

    @Test
    public void testTwoMinutesThirtyEightSeconds() {
        assertEquals("2:38", TimeFormatter.formatMs(158000L));
    }

    @Test
    public void testOneHour() {
        assertEquals("1:00:00", TimeFormatter.formatMs(3600000L));
    }

    @Test
    public void testOneHourTwoMinutesThirtyFourSeconds() {
        assertEquals("1:02:34", TimeFormatter.formatMs(3754000L));
    }

    @Test
    public void testNegativeMsClamping() {
        assertEquals("0:00", TimeFormatter.formatMs(-5000L));
    }

    @Test
    public void testPositionClamping() {
        long position = 180000L;
        long duration = 150000L;

        // Position greater than duration clamped to duration
        long clamped = Math.min(Math.max(0, position), duration);
        assertEquals(150000L, clamped);
        assertEquals("2:30", TimeFormatter.formatMs(clamped));
    }

    @Test
    public void testZeroDurationFractionSafety() {
        long position = 5000L;
        long duration = 0L;

        float fraction = duration > 0 ? (float) position / duration : 0f;
        assertEquals(0f, fraction, 0.001f);
    }

    //
    // Tests for formatAccessible
    //

    @Test
    public void testFormatAccessibleZeroMs() {
        assertEquals("0 seconds", TimeFormatter.formatAccessible(0L));
    }

    @Test
    public void testFormatAccessibleNegativeMs() {
        assertEquals("0 seconds", TimeFormatter.formatAccessible(-1000L));
    }

    @Test
    public void testFormatAccessibleOnlySeconds() {
        assertEquals("42 seconds", TimeFormatter.formatAccessible(42000L));
    }

    @Test
    public void testFormatAccessibleOneMinuteExactly() {
        assertEquals("1 minute", TimeFormatter.formatAccessible(60000L));
    }

    @Test
    public void testFormatAccessibleOneMinuteWithSeconds() {
        assertEquals("1 minute 5 seconds", TimeFormatter.formatAccessible(65000L));
    }

    @Test
    public void testFormatAccessibleMultipleMinutesExactly() {
        assertEquals("2 minutes", TimeFormatter.formatAccessible(120000L));
    }

    @Test
    public void testFormatAccessibleMultipleMinutesWithSeconds() {
        assertEquals("3 minutes 5 seconds", TimeFormatter.formatAccessible(185000L));
    }
}

package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.TimeZone;

public class DayTest {
    private static final TimeZone IST = TimeZone.getTimeZone("Asia/Kolkata");

    @Test
    public void ofEpoch_usesLocalTimeZone() {
        // 2026-10-17T18:45:00Z = 2026-10-18 00:15 IST
        long ms = 1792262700000L;
        assertEquals(20261018, Day.of(ms, IST));
        assertEquals(20261017, Day.of(ms, TimeZone.getTimeZone("UTC")));
    }

    @Test
    public void weekdayIndex_mondayIsZero() {
        assertEquals(0, Day.weekdayIndex(20261019)); // Monday
        assertEquals(6, Day.weekdayIndex(20261018)); // Sunday
    }

    @Test
    public void addDays_crossesMonthAndYear() {
        assertEquals(20261101, Day.addDays(20261031, 1));
        assertEquals(20270101, Day.addDays(20261231, 1));
        assertEquals(20280229, Day.addDays(20280301, -1));
    }

    @Test
    public void monthBounds_includingLeapFebruary() {
        assertEquals(20280201, Day.monthStart(2028, 2));
        assertEquals(20280229, Day.monthEnd(2028, 2));
        assertEquals(20270228, Day.monthEnd(2027, 2));
        assertEquals(20261231, Day.monthEnd(2026, 12));
    }
}

package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecapSchedulePolicyTest {
    private static final long ENOUGH = 2 * 3_600_000L;

    private static RecapSchedulePolicy.Due find(List<RecapSchedulePolicy.Due> due, String key) {
        for (RecapSchedulePolicy.Due d : due) if (d.period.key().equals(key)) return d;
        return null;
    }

    @Test
    public void monthlyDueOnFirst() {
        List<RecapSchedulePolicy.Due> due = RecapSchedulePolicy.recapsDue(20261101, new HashMap<>(), p -> ENOUGH);
        RecapSchedulePolicy.Due d = find(due, "month-2026-10");
        assertEquals(20261001, d.period.start);
        assertEquals(20261031, d.period.end);
        assertTrue(d.notify);
    }

    @Test
    public void monthlySkippedUnder30Min() {
        List<RecapSchedulePolicy.Due> due = RecapSchedulePolicy.recapsDue(20261101, new HashMap<>(), p -> 1_799_999L);
        assertEquals(null, find(due, "month-2026-10"));
    }

    @Test
    public void notDueTwice() {
        Map<String, Integer> existing = new HashMap<>();
        existing.put("month-2026-10", 20261031);
        assertEquals(null, find(RecapSchedulePolicy.recapsDue(20261115, existing, p -> ENOUGH), "month-2026-10"));
    }

    @Test
    public void januaryCoversDecemberOfPreviousYear() {
        RecapSchedulePolicy.Due d = find(RecapSchedulePolicy.recapsDue(20270101, new HashMap<>(), p -> ENOUGH),
                "month-2026-12");
        assertEquals(20261201, d.period.start);
    }

    @Test
    public void yearlyDueOnDec1_coversJanToNov() {
        RecapSchedulePolicy.Due d = find(RecapSchedulePolicy.recapsDue(20261201, new HashMap<>(), p -> ENOUGH),
                "year-2026");
        assertEquals(20260101, d.period.start);
        assertEquals(20261130, d.period.end);
        assertTrue(d.notify);
        assertEquals(null, find(RecapSchedulePolicy.recapsDue(20261130, new HashMap<>(), p -> ENOUGH), "year-2026"));
    }

    @Test
    public void yearRebuiltOnJan1_noNotify() {
        Map<String, Integer> existing = new HashMap<>();
        existing.put("year-2026", 20261130);
        existing.put("month-2026-12", 20261231);
        RecapSchedulePolicy.Due d = find(RecapSchedulePolicy.recapsDue(20270101, existing, p -> ENOUGH), "year-2026");
        assertEquals(20261231, d.period.end);
        assertFalse(d.notify);
    }

    @Test
    public void fullYearAlreadyStored_notRebuilt() {
        Map<String, Integer> existing = new HashMap<>();
        existing.put("year-2026", 20261231);
        assertEquals(null, find(RecapSchedulePolicy.recapsDue(20270301, existing, p -> ENOUGH), "year-2026"));
    }

    @Test
    public void missingPreviousYear_builtWithNotification() {
        RecapSchedulePolicy.Due d = find(RecapSchedulePolicy.recapsDue(20270115, new HashMap<>(), p -> ENOUGH), "year-2026");
        assertEquals(20261231, d.period.end);
        assertTrue(d.notify);
    }
}

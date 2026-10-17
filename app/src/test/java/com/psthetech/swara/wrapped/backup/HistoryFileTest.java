package com.psthetech.swara.wrapped.backup;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.wrapped.DailyRow;
import com.psthetech.swara.wrapped.HourlyRow;
import com.psthetech.swara.wrapped.SongInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HistoryFileTest {
    @Test
    public void roundTrip() {
        HistoryData d = new HistoryData(
                Arrays.asList(new DailyRow(20261017, 7, 60_000, 2, 1, 0, 1, 1)),
                Arrays.asList(new HourlyRow(20261017, 23, 60_000)),
                Arrays.asList(new SongInfo(7, "Kannukulla \"live\"", "Sai", "sai", null, 3, 20261001)),
                Arrays.asList(new HistoryData.RecapRow("month-2026-09", 20260901, 20260930, "{\"a\":1}", 5L, true, false)),
                Collections.singletonList(new HistoryData.OverrideRow(7, "HAPPY", 9L)));
        HistoryData back = HistoryFile.decode(HistoryFile.encode(d));
        assertEquals(HistoryFile.encode(d), HistoryFile.encode(back));
        assertEquals("Kannukulla \"live\"", back.songs.get(0).title);
        assertEquals(1, back.daily.get(0).vibeStarts);
        assertEquals("HAPPY", back.overrides.get(0).label);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOtherFiles() {
        HistoryFile.decode("{\"format\":\"something-else\"}");
    }
}

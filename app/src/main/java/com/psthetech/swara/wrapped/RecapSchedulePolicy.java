package com.psthetech.swara.wrapped;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Which recaps are due on a given day (spec §5). Pure. */
public final class RecapSchedulePolicy {

    /** A recap to build; {@code notify} is false for the silent January rebuild of the year. */
    public static final class Due {
        public final Period period;
        public final boolean notify;

        Due(Period period, boolean notify) {
            this.period = period;
            this.notify = notify;
        }
    }

    private RecapSchedulePolicy() {}

    /**
     * @param existingEnds stored recap key → its periodEnd
     * @param totalMs      listening time in a period
     */
    @NonNull
    public static List<Due> recapsDue(int today, @NonNull Map<String, Integer> existingEnds,
                                      @NonNull ToLongFunction<Period> totalMs) {
        List<Due> out = new ArrayList<>();
        int y = Day.year(today), m = Day.month(today);

        // Previous month.
        Period prev = m == 1 ? Period.month(y - 1, 12) : Period.month(y, m - 1);
        if (!existingEnds.containsKey(prev.key()) && enough(totalMs, prev)) out.add(new Due(prev, true));

        // This year's recap from 1 December (January–November).
        if (m == 12) {
            Period janToNov = Period.yearToDate(y, Day.monthEnd(y, 11));
            if (!existingEnds.containsKey(janToNov.key()) && enough(totalMs, janToNov)) {
                out.add(new Due(janToNov, true));
            }
        }

        // Last year's recap, completed with December (silently if it already existed).
        Period lastYear = Period.year(y - 1);
        Integer storedEnd = existingEnds.get(lastYear.key());
        if ((storedEnd == null || storedEnd < lastYear.end) && enough(totalMs, lastYear)) {
            out.add(new Due(lastYear, storedEnd == null));
        }
        return out;
    }

    private static boolean enough(ToLongFunction<Period> totalMs, Period p) {
        return totalMs.applyAsLong(p) >= SlidePlan.MIN_TOTAL_MS;
    }
}

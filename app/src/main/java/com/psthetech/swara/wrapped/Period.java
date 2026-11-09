package com.psthetech.swara.wrapped;

import java.util.Locale;

/** An inclusive range of local days that a recap or stats view covers. */
public final class Period {

    public enum Kind { MONTH, YEAR, ALL }

    public final Kind kind;
    public final int start;
    public final int end;

    public Period(Kind kind, int start, int end) {
        this.kind = kind;
        this.start = start;
        this.end = end;
    }

    public static Period month(int y, int m) {
        return new Period(Kind.MONTH, Day.monthStart(y, m), Day.monthEnd(y, m));
    }

    public static Period year(int y) {
        return new Period(Kind.YEAR, y * 10000 + 101, y * 10000 + 1231);
    }

    /** January 1 of {@code y} through {@code endDay} (inclusive). */
    public static Period yearToDate(int y, int endDay) {
        return new Period(Kind.YEAR, y * 10000 + 101, endDay);
    }

    public static Period allTime(int firstDay, int today) {
        return new Period(Kind.ALL, firstDay, today);
    }

    public boolean contains(int day) {
        return day >= start && day <= end;
    }

    /** Stable storage key, e.g. "month-2026-10", "year-2026", "all". */
    public String key() {
        switch (kind) {
            case MONTH: return String.format(Locale.ROOT, "month-%04d-%02d", Day.year(start), Day.month(start));
            case YEAR: return String.format(Locale.ROOT, "year-%04d", Day.year(start));
            default: return "all";
        }
    }
}

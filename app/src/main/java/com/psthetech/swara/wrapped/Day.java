package com.psthetech.swara.wrapped;

import java.util.Calendar;
import java.util.TimeZone;

/** Helpers for yyyymmdd day ints in a local time zone. Pure Java. */
public final class Day {

    private Day() {}

    public static int of(long epochMs, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(epochMs);
        return of(c);
    }

    static int of(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    public static int year(int day) { return day / 10000; }
    public static int month(int day) { return (day / 100) % 100; }
    public static int dom(int day) { return day % 100; }

    private static Calendar cal(int day) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year(day), month(day) - 1, dom(day));
        return c;
    }

    /** Monday = 0 … Sunday = 6. */
    public static int weekdayIndex(int day) {
        int dow = cal(day).get(Calendar.DAY_OF_WEEK); // Sunday = 1
        return (dow + 5) % 7;
    }

    public static int addDays(int day, int n) {
        Calendar c = cal(day);
        c.add(Calendar.DAY_OF_MONTH, n);
        return of(c);
    }

    public static int monthStart(int y, int m) {
        return y * 10000 + m * 100 + 1;
    }

    public static int monthEnd(int y, int m) {
        Calendar c = cal(monthStart(y, m));
        return y * 10000 + m * 100 + c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }
}

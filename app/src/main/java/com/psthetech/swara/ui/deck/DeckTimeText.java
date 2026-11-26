package com.psthetech.swara.ui.deck;

/**
 * Allocation-free version of {@code TimeFormatter.formatMs}: writes M:SS or H:MM:SS into a
 * caller-owned char array.
 */
public final class DeckTimeText {

    /** Longest text ever written ("9999:59:59"). */
    public static final int MAX_LENGTH = 10;

    private static final long MAX_SECONDS = 9999L * 3600 + 59 * 60 + 59;

    private DeckTimeText() { }

    /** Writes the time at {@code offset} and returns the number of chars written. */
    public static int write(long ms, char[] out, int offset) {
        long total = Math.min(Math.max(0, ms) / 1000, MAX_SECONDS);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        int at = offset;
        if (hours > 0) {
            at = number(hours, out, at);
            out[at++] = ':';
            out[at++] = digit(minutes / 10);
            out[at++] = digit(minutes % 10);
        } else {
            at = number(minutes, out, at);
        }
        out[at++] = ':';
        out[at++] = digit(seconds / 10);
        out[at++] = digit(seconds % 10);
        return at - offset;
    }

    private static int number(long value, char[] out, int at) {
        long divisor = 1;
        while (value / divisor >= 10) divisor *= 10;
        for (; divisor > 0; divisor /= 10) out[at++] = digit((value / divisor) % 10);
        return at;
    }

    private static char digit(long d) {
        return (char) ('0' + d);
    }
}

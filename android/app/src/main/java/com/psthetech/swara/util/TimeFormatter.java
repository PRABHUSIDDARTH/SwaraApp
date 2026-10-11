package com.psthetech.swara.util;

import java.util.Locale;

/**
 * Utility for formatting playback timestamps consistently throughout the app.
 */
public class TimeFormatter {

    private TimeFormatter() {}

    /**
     * Formats milliseconds to M:SS or H:MM:SS (e.g., 3:42, 0:05, 1:05:05)
     */
    public static String formatMs(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
    }

    /**
     * Formats seconds to M:SS (MediaStore DATE_ADDED is in seconds)
     */
    public static String formatSeconds(long seconds) {
        return formatMs(seconds * 1000);
    }

    /**
     * Short description of duration for accessibility (e.g., "3 minutes 42 seconds")
     */
    public static String formatAccessible(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes == 0) {
            return seconds + " seconds";
        }
        return minutes + " minute" + (minutes == 1 ? "" : "s") +
               (seconds > 0 ? " " + seconds + " seconds" : "");
    }
}

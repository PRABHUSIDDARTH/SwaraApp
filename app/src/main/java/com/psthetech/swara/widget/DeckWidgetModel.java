package com.psthetech.swara.widget;

/** What the Deck widget shows, as pure decisions. */
final class DeckWidgetModel {

    static final int PROGRESS_MAX = 1000;

    private DeckWidgetModel() { }

    /** The spinning overlays need a song, playback and Android 12 (to be tinted to the theme). */
    static boolean spins(boolean hasSong, boolean playing, int sdk) {
        return hasSong && playing && sdk >= 31;
    }

    static int progress(long positionMs, long durationMs) {
        if (durationMs <= 0) return 0;
        double fraction = Math.max(0d, Math.min(1d, (double) positionMs / (double) durationMs));
        return (int) Math.round(fraction * PROGRESS_MAX);
    }

    /**
     * Everything that changes the widget's look apart from the position. While it stays the same,
     * the one-second tick only needs to move the progress bar: re-sending the whole widget would
     * ship the reel bitmap every second and could restart the spin.
     */
    static String signature(long songId, boolean playing, boolean favorite, boolean vibe,
                            long durationMs, int artKey, int paletteKey) {
        return songId + "|" + playing + "|" + favorite + "|" + vibe + "|" + durationMs
                + "|" + artKey + "|" + paletteKey;
    }
}

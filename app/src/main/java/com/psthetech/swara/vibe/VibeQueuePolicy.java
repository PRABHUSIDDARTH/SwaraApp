package com.psthetech.swara.vibe;

/** Pure queue-fill arithmetic for Vibe Mode and Autoplay. */
public final class VibeQueuePolicy {

    private VibeQueuePolicy() {}

    /** Picks to append so {@code ahead} items follow the current one. Never negative. */
    public static int picksNeeded(int itemCount, int currentIndex, int ahead) {
        return Math.max(0, ahead - (itemCount - 1 - currentIndex));
    }
}

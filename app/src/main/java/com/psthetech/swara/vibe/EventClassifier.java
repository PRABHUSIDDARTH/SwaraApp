package com.psthetech.swara.vibe;

import com.psthetech.swara.data.db.entity.ListenEvent;

/** Pure logic: turns "the user left a song here" into a listen-event type. */
public final class EventClassifier {

    public static final long EARLY_SKIP_MS = 30_000L;
    public static final double EARLY_SKIP_FRACTION = 0.4;

    private EventClassifier() {
    }

    public static int classifyLeave(long positionMs, long durationMs,
                                    boolean autoTransition, boolean sameItemRestarted) {
        if (sameItemRestarted) return ListenEvent.TYPE_REPEAT;
        if (autoTransition) return ListenEvent.TYPE_FINISH;
        if (positionMs < EARLY_SKIP_MS || positionMs < EARLY_SKIP_FRACTION * durationMs) {
            return ListenEvent.TYPE_SKIP;
        }
        return ListenEvent.TYPE_FINISH;
    }
}

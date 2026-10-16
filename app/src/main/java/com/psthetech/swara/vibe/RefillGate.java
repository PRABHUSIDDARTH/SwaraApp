package com.psthetech.swara.vibe;

/**
 * One in-flight refill per generation. A generation change lets a new refill start at once;
 * a stale refill's landing clears the gate only if it still owns it. Not thread-safe (main).
 */
public final class RefillGate {

    private static final int NONE = Integer.MIN_VALUE;
    private int owner = NONE;

    /** True while a refill for {@code generation} is in flight. */
    public boolean busy(int generation) {
        return owner != NONE && owner == generation;
    }

    public void start(int generation) {
        owner = generation;
    }

    /** Releases the gate if {@code generation} owns it; returns whether it did. */
    public boolean finish(int generation) {
        if (owner != NONE && owner == generation) {
            owner = NONE;
            return true;
        }
        return false;
    }
}

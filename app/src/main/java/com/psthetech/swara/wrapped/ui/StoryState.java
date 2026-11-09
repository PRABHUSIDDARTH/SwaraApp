package com.psthetech.swara.wrapped.ui;

/** Pure navigation state of the Wrapped story: index, timing, pause. Main thread only. */
public final class StoryState {

    public static final long SLIDE_MS = 6_000L;
    static final long ENTRANCE_MS = 900L;

    private final int count;
    private int index;
    private long elapsed;
    private boolean paused;
    private boolean finished;

    public StoryState(int slideCount) {
        this.count = slideCount;
        this.finished = slideCount <= 0;
    }

    public int index() { return index; }
    public int count() { return count; }
    public boolean isFinished() { return finished; }
    public boolean isPaused() { return paused; }

    public void pause() { paused = true; }
    public void resume() { paused = false; }

    public void next() {
        if (finished) return;
        if (index >= count - 1) {
            finished = true;
            elapsed = SLIDE_MS;
            return;
        }
        index++;
        elapsed = 0;
    }

    public void prev() {
        finished = false;
        if (index > 0) index--;
        elapsed = 0;
    }

    public void tick(long dtMs) {
        if (paused || finished || dtMs <= 0) return;
        elapsed += dtMs;
        if (elapsed >= SLIDE_MS) next();
    }

    /** 0..1 through the current slide's display time (top progress bar). */
    public float slideProgress() {
        return Math.min(1f, elapsed / (float) SLIDE_MS);
    }

    /** 0..1 through the current slide's entrance animation. */
    public float entranceProgress() {
        return Math.min(1f, elapsed / (float) ENTRANCE_MS);
    }
}

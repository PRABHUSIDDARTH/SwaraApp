package com.psthetech.swara.wrapped.export;

/** Frame schedule for the recap video: fixed seconds per slide, 900 ms entrance then hold. */
public final class VideoPlan {

    static final int ENTRANCE_MS = 900;

    private final int slides;
    private final int fps;
    private final int framesPerSlide;

    public VideoPlan(int slides, int fps, int secondsPerSlide) {
        this.slides = slides;
        this.fps = fps;
        this.framesPerSlide = fps * secondsPerSlide;
    }

    public int totalFrames() {
        return slides * framesPerSlide;
    }

    public int slideAt(int frame) {
        return Math.min(slides - 1, frame / framesPerSlide);
    }

    /** Entrance progress (0..1) of the slide shown at {@code frame}. */
    public float progressAt(int frame) {
        int local = frame % framesPerSlide;
        float ms = local * 1000f / fps;
        return Math.min(1f, ms / ENTRANCE_MS);
    }

    public long presentationTimeUs(int frame) {
        return frame * 1_000_000L / fps;
    }
}

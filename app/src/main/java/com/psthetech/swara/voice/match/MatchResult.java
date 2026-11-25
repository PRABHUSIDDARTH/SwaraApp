package com.psthetech.swara.voice.match;

import java.util.Collections;
import java.util.List;

public final class MatchResult {
    public final Confidence confidence;
    /** Best first, at most 4. */
    public final List<Candidate> ranked;

    public MatchResult(Confidence confidence, List<Candidate> ranked) {
        this.confidence = confidence;
        this.ranked = Collections.unmodifiableList(ranked);
    }

    public static MatchResult none() {
        return new MatchResult(Confidence.NONE, Collections.emptyList());
    }
}

package com.psthetech.swara.voice.exec;

import com.psthetech.swara.voice.match.Candidate;

import java.util.Collections;
import java.util.List;

/** What Swara shows (and optionally speaks) after a voice command. */
public final class VoiceOutcome {
    public final String text;
    public final boolean speak;
    /** Non-empty for MEDIUM / AMBIGUOUS results. */
    public final List<Candidate> choices;

    public VoiceOutcome(String text, boolean speak, List<Candidate> choices) {
        this.text = text;
        this.speak = speak;
        this.choices = Collections.unmodifiableList(choices);
    }

    public static VoiceOutcome spoken(String text) {
        return new VoiceOutcome(text, true, Collections.emptyList());
    }

    public static VoiceOutcome silent(String text) {
        return new VoiceOutcome(text, false, Collections.emptyList());
    }
}

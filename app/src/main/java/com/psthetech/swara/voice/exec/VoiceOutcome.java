package com.psthetech.swara.voice.exec;

import com.psthetech.swara.voice.match.Candidate;

import java.util.Collections;
import java.util.List;

/** What Swara shows (and optionally speaks) after a voice command. */
public final class VoiceOutcome {
    /** What kind of reply this is, so the UI never has to guess from the text. */
    public enum Kind {
        /** Something was done (playback started, paused, skipped). */
        RESULT,
        /** MEDIUM match: "Did you mean X?" — choices.get(0) is the Yes option, the rest alternatives. */
        CONFIRM,
        /** AMBIGUOUS match: "Which one?" — all choices are peers. */
        CHOOSE,
        /** Nothing happened: not found, didn't catch, library loading, mic busy. */
        INFO
    }

    public final Kind kind;
    public final String text;
    public final boolean speak;
    /**
     * Non-empty for CONFIRM / CHOOSE. For CONFIRM, get(0) is the top candidate (the "Yes" option
     * of "Did you mean X?") and the rest are alternatives.
     */
    public final List<Candidate> choices;

    public VoiceOutcome(Kind kind, String text, boolean speak, List<Candidate> choices) {
        this.kind = kind;
        this.text = text;
        this.speak = speak;
        this.choices = Collections.unmodifiableList(choices);
    }

    /** Kind is CHOOSE when there are choices, INFO otherwise. */
    public VoiceOutcome(String text, boolean speak, List<Candidate> choices) {
        this(choices.isEmpty() ? Kind.INFO : Kind.CHOOSE, text, speak, choices);
    }

    /** A spoken reply where nothing happened (INFO). */
    public static VoiceOutcome spoken(String text) {
        return new VoiceOutcome(Kind.INFO, text, true, Collections.emptyList());
    }

    /** A silent reply where nothing happened (INFO). */
    public static VoiceOutcome silent(String text) {
        return new VoiceOutcome(Kind.INFO, text, false, Collections.emptyList());
    }

    /** Something was done. */
    public static VoiceOutcome result(String text, boolean speak) {
        return new VoiceOutcome(Kind.RESULT, text, speak, Collections.emptyList());
    }
}

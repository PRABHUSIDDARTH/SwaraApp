package com.psthetech.swara.voice.match;

import com.psthetech.swara.domain.model.Song;

import java.util.Collections;
import java.util.List;

/** One scored match. {@code songs} is filled for ARTIST and ALBUM (and holds the single song for SONG). */
public final class Candidate {
    public final MatchKind kind;
    public final String key;
    public final String display;
    public final String detail;
    public final double score;
    public final List<Song> songs;

    public Candidate(MatchKind kind, String key, String display, String detail, double score, List<Song> songs) {
        this.kind = kind;
        this.key = key;
        this.display = display;
        this.detail = detail;
        this.score = score;
        this.songs = Collections.unmodifiableList(songs);
    }

    @Override public String toString() {
        return "Candidate{" + kind + ", " + display + " [" + detail + "], " + score + "}";
    }
}

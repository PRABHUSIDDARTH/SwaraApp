package com.psthetech.swara.voice.intent;

/** Immutable result of parsing a voice transcript. */
public final class VoiceIntent {
    public enum Type { PLAY_ANY, PLAY_ARTIST, PLAY_ALBUM, PAUSE, RESUME, NEXT, PREVIOUS, UNKNOWN }

    private final Type type;
    private final String query;
    private final boolean artistThenAlbum;

    public VoiceIntent(Type type, String query, boolean artistThenAlbum) {
        this.type = type;
        this.query = query == null ? "" : query;
        this.artistThenAlbum = artistThenAlbum;
    }

    public VoiceIntent(Type type, String query) {
        this(type, query, false);
    }

    public Type getType() { return type; }
    public String getQuery() { return query; }
    /** True for "play X songs" without an album cue: X may be an artist or an album. */
    public boolean isArtistThenAlbum() { return artistThenAlbum; }

    @Override public String toString() {
        return "VoiceIntent{" + type + ", '" + query + "', artistThenAlbum=" + artistThenAlbum + "}";
    }
}

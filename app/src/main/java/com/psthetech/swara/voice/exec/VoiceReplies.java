package com.psthetech.swara.voice.exec;

import java.util.Locale;

/** Exact reply copy for voice commands. */
public final class VoiceReplies {
    public static final String PAUSED = "Paused";
    public static final String PLAYING = "Playing";
    public static final String NEXT = "Next";
    public static final String PREVIOUS = "Previous";

    private VoiceReplies() {}

    public static String playingSong(String title) { return String.format(Locale.ROOT, "Playing %s.", title); }
    public static String playingArtist(int n, String name) {
        return String.format(Locale.ROOT, "Playing %d %s songs, shuffled.", n, name);
    }
    public static String playingAlbum(String name, int n) {
        return String.format(Locale.ROOT, "Playing the album %s, %d songs.", name, n);
    }
    public static String didYouMean(String display) { return String.format(Locale.ROOT, "Did you mean %s?", display); }
    public static String whichOne() { return "Which one?"; }
    public static String notFound(String query) {
        return "I couldn't find " + titleCase(query)
                + " in your library. Add the songs to your phone and I'll find them.";
    }
    public static String didntCatch() { return "Sorry, I didn't catch that."; }
    public static String libraryLoading() { return "Your library is still loading, try again in a moment."; }
    public static String micBusy() { return "The mic is busy right now."; }

    static String titleCase(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean start = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) {
                start = true;
                sb.append(c);
            } else if (start) {
                sb.append(Character.toUpperCase(c));
                start = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

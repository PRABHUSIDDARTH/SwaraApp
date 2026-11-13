package com.psthetech.swara.voice.intent;

import com.psthetech.swara.voice.intent.VoiceIntent.Type;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rule-based parser turning a speech transcript into a {@link VoiceIntent}. */
public final class IntentParser {
    private static final Pattern WAKE = Pattern.compile("^(?:hey|hi|ok|okay) (?:swara|sara|swaraa)(?: |$)");
    private static final Pattern FILLERS = Pattern.compile("\\b(?:can you|could you|please|the|some|me)\\b");
    private static final Pattern PAUSE = Pattern.compile("^(?:pause|stop)$");
    private static final Pattern RESUME = Pattern.compile("^(?:resume|play|continue)$");
    private static final Pattern NEXT = Pattern.compile("^(?:next|skip|next song)$");
    private static final Pattern PREVIOUS = Pattern.compile("^(?:previous|go back)$");
    private static final Pattern BY = Pattern.compile("^songs by (.+)$");
    private static final Pattern ALBUM = Pattern.compile("^(.+) (?:movie|film|padam|album)(?: songs)?$");
    private static final Pattern PLAY_SONGS = Pattern.compile("^play (.+) songs$");
    private static final Pattern PLAY = Pattern.compile("^play (.+)$");

    private IntentParser() {}

    public static VoiceIntent parse(String transcript) {
        String s = normalize(transcript);
        s = WAKE.matcher(s).replaceFirst("");
        s = normalize(FILLERS.matcher(s).replaceAll(" "));
        if (s.isEmpty()) return new VoiceIntent(Type.UNKNOWN, "");

        if (PAUSE.matcher(s).matches()) return new VoiceIntent(Type.PAUSE, "");
        if (RESUME.matcher(s).matches()) return new VoiceIntent(Type.RESUME, "");
        if (NEXT.matcher(s).matches()) return new VoiceIntent(Type.NEXT, "");
        if (PREVIOUS.matcher(s).matches()) return new VoiceIntent(Type.PREVIOUS, "");

        Matcher m = BY.matcher(s);
        if (m.matches()) return new VoiceIntent(Type.PLAY_ARTIST, m.group(1), false);
        m = ALBUM.matcher(s);
        if (m.matches()) {
            String q = m.group(1).replaceFirst("^play ", "");
            if (!q.isEmpty() && !q.equals("play")) return new VoiceIntent(Type.PLAY_ALBUM, q, false);
        }
        m = PLAY_SONGS.matcher(s);
        if (m.matches()) return new VoiceIntent(Type.PLAY_ARTIST, m.group(1), true);
        m = PLAY.matcher(s);
        if (m.matches()) return new VoiceIntent(Type.PLAY_ANY, m.group(1), false);
        return new VoiceIntent(Type.UNKNOWN, "");
    }

    private static String normalize(String in) {
        if (in == null) return "";
        return in.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}

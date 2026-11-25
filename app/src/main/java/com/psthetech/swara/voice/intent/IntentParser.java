package com.psthetech.swara.voice.intent;

import com.psthetech.swara.voice.intent.VoiceIntent.Type;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rule-based parser turning a speech transcript into a {@link VoiceIntent}. */
public final class IntentParser {
    private static final Pattern WAKE = Pattern.compile("^(?:hey|hi|ok|okay) (?:swara|sara|swaraa)(?: |$)");
    /** Fillers before the command verb only. */
    private static final Pattern LEAD_FILLERS = Pattern.compile("^(?:(?:can you|could you|please|the|some|me) )+");
    /** Fillers directly after "play" only; never inside the query. */
    private static final Pattern PLAY_FILLERS = Pattern.compile("^(?:(?:please|some|me) )+");
    private static final Pattern LEAD_PLAY = Pattern.compile("^play ");
    /** Moonshine often hears "play" as one of these; only honoured when a query follows. */
    private static final Pattern MISHEARD_PLAY = Pattern.compile("^(?:player|played|plays|pray|flay) (?=.)");
    private static final Pattern PAUSE = Pattern.compile("^(?:pause|stop)(?: the)?(?: music| song)?(?: please)?$");
    private static final Pattern RESUME = Pattern.compile("^(?:resume|play|continue)$");
    private static final Pattern NEXT = Pattern.compile("^(?:play )?(?:the )?(?:next|skip)(?: song| one| this| this song)?$");
    private static final Pattern PREVIOUS = Pattern.compile("^(?:play )?(?:the )?(?:previous|go back)(?: song)?$");
    private static final Pattern BY = Pattern.compile("^(?:play )?songs by (.+)$");
    private static final Pattern ALBUM = Pattern.compile("^(.+) (?:movie|film|padam|album)(?: songs)?$");
    private static final Pattern PLAY_SONGS = Pattern.compile("^play (.+) songs$");
    private static final Pattern PLAY = Pattern.compile("^play (.+)$");

    private IntentParser() {}

    public static VoiceIntent parse(String transcript) {
        String s = normalize(transcript);
        s = WAKE.matcher(s).replaceFirst("");
        s = LEAD_FILLERS.matcher(s).replaceFirst("");
        if (s.isEmpty()) return new VoiceIntent(Type.UNKNOWN, "");
        s = MISHEARD_PLAY.matcher(s).replaceFirst("play ");

        if (PAUSE.matcher(s).matches()) return new VoiceIntent(Type.PAUSE, "");
        if (RESUME.matcher(s).matches()) return new VoiceIntent(Type.RESUME, "");
        if (NEXT.matcher(s).matches()) return new VoiceIntent(Type.NEXT, "");
        if (PREVIOUS.matcher(s).matches()) return new VoiceIntent(Type.PREVIOUS, "");

        Matcher m = BY.matcher(s);
        if (m.matches()) return new VoiceIntent(Type.PLAY_ARTIST, m.group(1), false);
        m = ALBUM.matcher(s);
        if (m.matches()) {
            String q = LEAD_PLAY.matcher(m.group(1)).replaceFirst("");
            if (!q.isEmpty() && !q.equals("play")) return new VoiceIntent(Type.PLAY_ALBUM, q, false);
        }
        m = PLAY_SONGS.matcher(s);
        if (m.matches()) return new VoiceIntent(Type.PLAY_ARTIST, stripPlayFillers(m.group(1)), true);
        m = PLAY.matcher(s);
        if (m.matches()) return new VoiceIntent(Type.PLAY_ANY, stripPlayFillers(m.group(1)), false);
        return new VoiceIntent(Type.UNKNOWN, "");
    }

    /** Normalized transcript with the wake phrase and leading fillers removed. */
    public static String stripLeading(String transcript) {
        String s = normalize(transcript);
        s = WAKE.matcher(s).replaceFirst("");
        return LEAD_FILLERS.matcher(s).replaceFirst("");
    }

    private static String stripPlayFillers(String q) {
        String r = PLAY_FILLERS.matcher(q).replaceFirst("");
        return r.isEmpty() ? q : r;
    }

    private static String normalize(String in) {
        if (in == null) return "";
        return in.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}

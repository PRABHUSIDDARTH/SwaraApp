package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.voice.intent.IntentParser;
import com.psthetech.swara.voice.intent.VoiceIntent;
import com.psthetech.swara.voice.intent.VoiceIntent.Type;

import org.junit.Test;

public class IntentParserTest {
    private static void check(String in, Type type, String query, boolean atA) {
        VoiceIntent v = IntentParser.parse(in);
        assertEquals(in, type, v.getType());
        assertEquals(in, query, v.getQuery());
        assertEquals(in, atA, v.isArtistThenAlbum());
    }

    @Test public void commandTable() {
        check("Hey Swara, play Kaavaalaa.", Type.PLAY_ANY, "kaavaalaa", false);
        check("play rahman songs", Type.PLAY_ARTIST, "rahman", true);
        check("songs by A R Rahman", Type.PLAY_ARTIST, "a r rahman", false);
        check("can you please play roja movie songs", Type.PLAY_ALBUM, "roja", false);
        check("play roja album", Type.PLAY_ALBUM, "roja", false);
        check("roja padam songs", Type.PLAY_ALBUM, "roja", false);
        check("play roja film songs", Type.PLAY_ALBUM, "roja", false);
        check("pause", Type.PAUSE, "", false);
        check("stop", Type.PAUSE, "", false);
        check("resume", Type.RESUME, "", false);
        check("play", Type.RESUME, "", false);
        check("continue", Type.RESUME, "", false);
        check("next", Type.NEXT, "", false);
        check("skip", Type.NEXT, "", false);
        check("next song", Type.NEXT, "", false);
        check("previous", Type.PREVIOUS, "", false);
        check("go back", Type.PREVIOUS, "", false);
        check("", Type.UNKNOWN, "", false);
        check("um", Type.UNKNOWN, "", false);
        check("play rahman", Type.PLAY_ANY, "rahman", false);
        check("play call me maybe", Type.PLAY_ANY, "call me maybe", false);
        check("hey swara can you please play some kaavaalaa", Type.PLAY_ANY, "kaavaalaa", false);
        check("play songs by the weeknd", Type.PLAY_ARTIST, "the weeknd", false);
        check("play songs by rahman", Type.PLAY_ARTIST, "rahman", false);
        check("play the weeknd", Type.PLAY_ANY, "the weeknd", false);
        check("pause the music", Type.PAUSE, "", false);
        check("stop the song", Type.PAUSE, "", false);
        check("play the next song", Type.NEXT, "", false);
        check("play the previous song", Type.PREVIOUS, "", false);
        check("Player kaavaalaa", Type.PLAY_ANY, "kaavaalaa", false);
        check("plays rahman songs", Type.PLAY_ARTIST, "rahman", true);
        check(null, Type.UNKNOWN, "", false);
    }
}

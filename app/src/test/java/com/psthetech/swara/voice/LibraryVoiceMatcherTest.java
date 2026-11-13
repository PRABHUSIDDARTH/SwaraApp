package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.voice.intent.VoiceIntent;
import com.psthetech.swara.voice.match.Candidate;
import com.psthetech.swara.voice.match.Confidence;
import com.psthetech.swara.voice.match.LibraryIndex;
import com.psthetech.swara.voice.match.LibraryVoiceMatcher;
import com.psthetech.swara.voice.match.MatchKind;
import com.psthetech.swara.voice.match.MatchResult;

import org.junit.Test;

public class LibraryVoiceMatcherTest {

    private static final LibraryIndex INDEX = LibraryIndex.build(VoiceFixtures.library());

    private static MatchResult match(VoiceIntent.Type t, String q) {
        return LibraryVoiceMatcher.match(new VoiceIntent(t, q), INDEX);
    }

    @Test public void kaavaalaaFoundFromMisspelling() {
        MatchResult r = match(VoiceIntent.Type.PLAY_ANY, "kavalaa");
        assertEquals(Confidence.HIGH, r.confidence);
        assertEquals(MatchKind.SONG, r.ranked.get(0).kind);
        assertEquals("Kaavaalaa", r.ranked.get(0).display);
    }

    @Test public void rahmanSongsGathersAllCreditVariants() {
        MatchResult r = match(VoiceIntent.Type.PLAY_ARTIST, "rahman");
        Candidate top = r.ranked.get(0);
        assertEquals(MatchKind.ARTIST, top.kind);
        assertEquals(VoiceFixtures.RAHMAN_SONG_COUNT, top.songs.size());
        for (Song s : top.songs) assertTrue(!s.getArtist().contains("Ameen"));
        assertEquals("12 songs", top.detail);
    }

    @Test public void playRahmanPicksArtistNotSongContainingRahman() {
        MatchResult r = match(VoiceIntent.Type.PLAY_ANY, "rahman");
        Candidate top = r.ranked.get(0);
        assertEquals(MatchKind.ARTIST, top.kind);
        assertTrue(top.display.startsWith("A.R."));
        assertTrue(r.confidence == Confidence.HIGH || r.confidence == Confidence.AMBIGUOUS);
        assertTrue(!top.display.equals("Rahman Theme"));
    }

    @Test public void sceneMovieSongsIsAlbum() {
        MatchResult r = match(VoiceIntent.Type.PLAY_ALBUM, "scene");
        assertEquals(Confidence.HIGH, r.confidence);
        Candidate top = r.ranked.get(0);
        assertEquals(MatchKind.ALBUM, top.kind);
        assertEquals("Scene", top.display);
        assertEquals(VoiceFixtures.SCENE_SONG_COUNT, top.songs.size());
    }

    @Test public void twoRojaAlbumsAreAmbiguous() {
        MatchResult r = match(VoiceIntent.Type.PLAY_ALBUM, "roja");
        assertEquals(Confidence.AMBIGUOUS, r.confidence);
        assertTrue(r.ranked.size() >= 2);
        String d0 = r.ranked.get(0).detail, d1 = r.ranked.get(1).detail;
        assertTrue(d0 + " / " + d1, (d0.contains("1992") && d1.contains("1993"))
                || (d0.contains("1993") && d1.contains("1992")));
        assertTrue(r.ranked.get(0).display.startsWith("Roja"));
        assertTrue(r.ranked.get(1).display.startsWith("Roja"));
    }

    @Test public void closeButNotSureIsMedium() {
        MatchResult r = match(VoiceIntent.Type.PLAY_ANY, "kalava");
        assertEquals(Confidence.MEDIUM, r.confidence);
        assertEquals("Kaavaalaa", r.ranked.get(0).display);
    }

    @Test public void unknownIsNone() {
        MatchResult r = match(VoiceIntent.Type.PLAY_ANY, "despacito");
        assertEquals(Confidence.NONE, r.confidence);
        assertTrue(r.ranked.isEmpty());
    }

    @Test public void artistThenAlbumFallsBackToAlbum() {
        MatchResult r = LibraryVoiceMatcher.match(
                new VoiceIntent(VoiceIntent.Type.PLAY_ARTIST, "scene", true), INDEX);
        assertEquals(MatchKind.ALBUM, r.ranked.get(0).kind);
        assertEquals("Scene", r.ranked.get(0).display);
    }
}

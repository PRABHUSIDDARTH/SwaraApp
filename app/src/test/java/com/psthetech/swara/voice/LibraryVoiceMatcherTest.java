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
        assertEquals(Confidence.HIGH, r.confidence);
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

    private static Song song(long id, String title, String artist, String album, long albumId) {
        return new Song(id, title, artist, album, albumId, 1000, 1, 2000, 1L);
    }

    private static MatchResult artist(String q, LibraryIndex idx) {
        return LibraryVoiceMatcher.match(new VoiceIntent(VoiceIntent.Type.PLAY_ARTIST, q), idx);
    }

    @Test public void artistWinsOverSameNamedAlbum() {
        MatchResult r = LibraryVoiceMatcher.match(
                new VoiceIntent(VoiceIntent.Type.PLAY_ARTIST, "hariharan", true), INDEX);
        assertEquals(MatchKind.ARTIST, r.ranked.get(0).kind);
        assertEquals("Hariharan", r.ranked.get(0).display);
    }

    @Test public void queryInitialsAreIgnoredOrJoined() {
        for (String q : new String[]{"a r rahman", "ar rahman", "a.r. rahman"}) {
            MatchResult r = artist(q, INDEX);
            assertEquals(q, Confidence.HIGH, r.confidence);
            assertEquals(q, MatchKind.ARTIST, r.ranked.get(0).kind);
            assertTrue(q, r.ranked.get(0).display.contains("Rahman")
                    && !r.ranked.get(0).display.contains("Ameen"));
        }
    }

    @Test public void arOnlyLibraryTreatsLeadingCapsPairAsInitials() {
        LibraryIndex idx = LibraryIndex.build(java.util.Arrays.asList(
                song(1, "One", "AR Rahman", "X", 1), song(2, "Two", "AR Rahman", "X", 1)));
        MatchResult r = artist("rahman", idx);
        assertEquals(Confidence.HIGH, r.confidence);
        assertEquals(2, r.ranked.get(0).songs.size());
    }

    @Test public void untaggedAlbumsAreNotAlbums() {
        LibraryIndex idx = LibraryIndex.build(java.util.Arrays.asList(
                song(1, "One", "Foo Bar", "Unknown Album", 5), song(2, "Two", "Baz Qux", "Unknown Album", 5),
                song(3, "Three", "Baz Qux", null, 6), song(4, "Four", "Baz Qux", "Real", 0)));
        MatchResult r = LibraryVoiceMatcher.match(
                new VoiceIntent(VoiceIntent.Type.PLAY_ALBUM, "unknown album"), idx);
        assertEquals(Confidence.NONE, r.confidence);
        assertEquals(Confidence.NONE, LibraryVoiceMatcher.match(
                new VoiceIntent(VoiceIntent.Type.PLAY_ALBUM, "real"), idx).confidence);
    }

    @Test public void similarMultiLetterNamesStayDistinct() {
        LibraryIndex idx = LibraryIndex.build(java.util.Arrays.asList(
                song(1, "One", "Ann Eliza", "X", 1), song(2, "Two", "Anne Liza", "Y", 2)));
        int artists = 0;
        for (Candidate c : LibraryVoiceMatcher.match(
                new VoiceIntent(VoiceIntent.Type.PLAY_ANY, "ann eliza"), idx).ranked) {
            if (c.kind == MatchKind.ARTIST) artists++;
        }
        assertEquals(2, artists);
    }

    @Test public void anirudhMatchesMergedArtist() {
        java.util.List<Song> songs = new java.util.ArrayList<>();
        songs.add(new Song(1, "Solo", "Anirudh", "Album10", 10L, 1000L, 1, 2020, 0L));
        for (int i = 0; i < 29; i++) {
            songs.add(new Song(100 + i, "Tune" + i, "Anirudh Ravichander", "Album10", 10L, 1000L, 1, 2020, 0L));
        }
        com.psthetech.swara.util.ArtistAliasResolver r =
                com.psthetech.swara.util.ArtistAliasResolver.build(songs, null);
        LibraryIndex idx = LibraryIndex.build(songs, r);
        MatchResult res = LibraryVoiceMatcher.match(new VoiceIntent(VoiceIntent.Type.PLAY_ARTIST, "anirudh"), idx);
        assertEquals(Confidence.HIGH, res.confidence);
        Candidate top = res.ranked.get(0);
        assertEquals(MatchKind.ARTIST, top.kind);
        assertEquals("Anirudh Ravichander", top.display);
        assertEquals(30, top.songs.size());
    }

    @Test public void raviDoesNotMatchMergedAnirudhRavichander() {
        java.util.List<Song> songs = new java.util.ArrayList<>();
        songs.add(new Song(1, "Solo", "Anirudh", "Album10", 10L, 1000L, 1, 2020, 0L));
        for (int i = 0; i < 29; i++) {
            songs.add(new Song(100 + i, "Tune" + i, "Anirudh Ravichander", "Album10", 10L, 1000L, 1, 2020, 0L));
        }
        LibraryIndex idx = LibraryIndex.build(songs,
                com.psthetech.swara.util.ArtistAliasResolver.build(songs, null));
        MatchResult res = LibraryVoiceMatcher.match(new VoiceIntent(VoiceIntent.Type.PLAY_ARTIST, "ravi"), idx);
        assertTrue(res.ranked.isEmpty() || !"Anirudh Ravichander".equals(res.ranked.get(0).display));
    }
}

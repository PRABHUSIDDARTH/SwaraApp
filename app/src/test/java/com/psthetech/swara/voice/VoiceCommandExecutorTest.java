package com.psthetech.swara.voice;

import static org.junit.Assert.*;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.voice.exec.VoiceCommandExecutor;
import com.psthetech.swara.voice.exec.VoiceOutcome;
import com.psthetech.swara.voice.exec.VoicePlayback;
import com.psthetech.swara.voice.exec.VoiceReplies;
import com.psthetech.swara.voice.match.LibraryIndex;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class VoiceCommandExecutorTest {

    static class Fake implements VoicePlayback {
        final List<String> calls = new ArrayList<>();
        Song song;
        List<Song> queue;
        @Override public void playSong(Song s) { song = s; calls.add("song"); }
        @Override public void playQueue(List<Song> songs) { queue = songs; calls.add("queue"); }
        @Override public void pause() { calls.add("pause"); }
        @Override public void resume() { calls.add("resume"); }
        @Override public void next() { calls.add("next"); }
        @Override public void previous() { calls.add("previous"); }
    }

    private Fake fake;
    private VoiceCommandExecutor exec;
    private LibraryIndex index;
    private List<Song> library;

    @Before public void setUp() {
        fake = new Fake();
        exec = new VoiceCommandExecutor(fake, new Random(42));
        library = VoiceFixtures.library();
        index = LibraryIndex.build(library);
    }

    @Test public void songPlaysSong() {
        VoiceOutcome o = exec.handle("play kavalaa", index);
        assertEquals(VoiceFixtures.KAAVAALAA_ID, fake.song.getId());
        assertEquals("Playing Kaavaalaa.", o.text);
        assertTrue(o.speak);
    }

    @Test public void artistQueueShuffledAndComplete() {
        VoiceOutcome o = exec.handle("play rahman songs", index);
        assertEquals(VoiceFixtures.RAHMAN_SONG_COUNT, fake.queue.size());
        List<Long> ids = new ArrayList<>();
        for (Song s : fake.queue) ids.add(s.getId());
        List<Long> credit = new ArrayList<>();
        for (int i = 0; i < 12; i++) credit.add(4000L + i);
        assertEquals(new HashSet<>(credit), new HashSet<>(ids));
        assertNotEquals(credit, ids);
        assertEquals("Playing 12 A.R. Rahman songs, shuffled.", o.text);
    }

    @Test public void albumQueueInTrackOrder() {
        VoiceOutcome o = exec.handle("play scene movie songs", index);
        assertEquals(VoiceFixtures.SCENE_SONG_COUNT, fake.queue.size());
        for (int i = 0; i < fake.queue.size(); i++) assertEquals(i + 1, fake.queue.get(i).getTrackNumber());
        assertEquals("Playing the album Scene, 4 songs.", o.text);
    }

    @Test public void ambiguousReturnsChoicesNoPlayback() {
        VoiceOutcome o = exec.handle("play roja movie songs", index);
        assertTrue(fake.calls.isEmpty());
        assertEquals("Which one?", o.text);
        assertEquals(2, o.choices.size());
        assertEquals(VoiceOutcome.Kind.CHOOSE, o.kind);
    }

    @Test public void choosingChipPlaysAlbum() {
        VoiceOutcome o = exec.handle("play roja movie songs", index);
        VoiceOutcome c = exec.choose(o.choices.get(0));
        assertEquals(6, fake.queue.size());
        for (int i = 0; i < 6; i++) assertEquals(i + 1, fake.queue.get(i).getTrackNumber());
        assertEquals(VoiceFixtures.ALBUM_ROJA_TAMIL, fake.queue.get(0).getAlbumId());
        assertTrue(c.text.startsWith("Playing the album"));
        assertEquals(VoiceOutcome.Kind.RESULT, c.kind);
    }

    @Test public void notFoundReply() {
        VoiceOutcome o = exec.handle("play despacito", index);
        assertEquals("I couldn't find Despacito in your library. Add the songs to your phone and I'll find them.", o.text);
        assertTrue(fake.calls.isEmpty());
    }

    @Test public void transportNotSpoken() {
        VoiceOutcome o = exec.handle("pause", index);
        assertEquals(java.util.Collections.singletonList("pause"), fake.calls);
        assertFalse(o.speak);
        assertEquals("Paused", o.text);
    }

    @Test public void emptyLibraryRepliesLoading() {
        assertEquals(VoiceReplies.libraryLoading(), exec.handle("play kavalaa", null).text);
        assertEquals(VoiceReplies.libraryLoading(),
                exec.handle("play kavalaa", LibraryIndex.build(new ArrayList<>())).text);
        assertTrue(fake.calls.isEmpty());
    }

    @Test public void unknownRepliesDidntCatch() {
        assertEquals("Sorry, I didn't catch that.", exec.handle("", index).text);
    }

    @Test public void blankQueryRepliesDidntCatch() {
        VoiceOutcome o = exec.handle(new com.psthetech.swara.voice.intent.VoiceIntent(
                com.psthetech.swara.voice.intent.VoiceIntent.Type.PLAY_ANY, "  "), index);
        assertEquals(VoiceReplies.didntCatch(), o.text);
        assertTrue(fake.calls.isEmpty());
    }

    @Test public void transportWorksWithoutLibrary() {
        VoiceOutcome o = exec.handle("pause", null);
        assertEquals(java.util.Collections.singletonList("pause"), fake.calls);
        assertEquals("Paused", o.text);
    }

    @Test public void mediumOffersDidYouMean() {
        VoiceOutcome o = exec.handle("play kalava", index);
        assertEquals("Did you mean Kaavaalaa?", o.text);
        assertTrue(o.speak);
        assertEquals("Kaavaalaa", o.choices.get(0).display);
        assertTrue(o.choices.size() <= 4);
        assertTrue(fake.calls.isEmpty());
        assertEquals(VoiceOutcome.Kind.CONFIRM, o.kind);
    }

    @Test public void outcomeKinds() {
        assertEquals(VoiceOutcome.Kind.RESULT, exec.handle("pause", index).kind);
        assertEquals(VoiceOutcome.Kind.INFO, exec.handle("play despacito", index).kind);
        assertEquals(VoiceOutcome.Kind.INFO, exec.handle("", index).kind);
        assertEquals(VoiceOutcome.Kind.INFO, exec.handle("play kavalaa", null).kind);
    }
}

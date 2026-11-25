package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.psthetech.swara.voice.audio.CommandRecorder;
import com.psthetech.swara.voice.exec.VoiceCommandExecutor;
import com.psthetech.swara.voice.exec.VoiceOutcome;
import com.psthetech.swara.voice.exec.VoiceReplies;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.voice.match.Candidate;
import com.psthetech.swara.voice.match.MatchKind;
import com.psthetech.swara.voice.session.VoiceSessionController;
import com.psthetech.swara.voice.session.VoiceState;
import com.psthetech.swara.voice.tts.SwaraVoice;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VoiceSessionControllerTest {
    int ducks, unducks;
    String sttResult = "play kaavaalaa";
    boolean sttThrows;
    boolean speakReplies = true;
    boolean voiceImmediate = true;
    final List<String> spoken = new ArrayList<>();
    final List<Runnable> pendingDone = new ArrayList<>();
    final List<VoiceState> states = new ArrayList<>();
    final List<VoiceOutcome> outcomes = new ArrayList<>();
    final List<Runnable> scheduled = new ArrayList<>();
    final List<Long> scheduledDelays = new ArrayList<>();
    final List<Boolean> cancelled = new ArrayList<>();
    VoiceCommandExecutor executor;
    VoiceSessionController c;

    @Before public void setUp() {
        executor = mock(VoiceCommandExecutor.class);
        when(executor.handle(anyString(), any())).thenReturn(VoiceOutcome.spoken("Playing Kaavaalaa."));
        SwaraVoice voice = new SwaraVoice() {
            @Override public boolean isAvailable() { return true; }
            @Override public void speak(String text, Runnable onDone) {
                spoken.add(text);
                if (voiceImmediate) onDone.run(); else pendingDone.add(onDone);
            }
            @Override public void shutdown() {}
        };
        VoiceSessionController.Deps d = new VoiceSessionController.Deps(
                new com.psthetech.swara.voice.stt.SpeechToText() {
                    @Override public String transcribe(float[] a) throws Exception {
                        if (sttThrows) throw new Exception("boom");
                        return sttResult;
                    }
                    @Override public void close() {}
                },
                executor, voice, () -> null,
                () -> ducks++, () -> unducks++,
                Runnable::run, Runnable::run, () -> speakReplies,
                (r, ms) -> {
                    scheduled.add(r);
                    scheduledDelays.add(ms);
                    final int i = cancelled.size();
                    cancelled.add(false);
                    return () -> cancelled.set(i, true);
                });
        c = new VoiceSessionController(d);
        c.setListener((s, o, l) -> { states.add(s); outcomes.add(o); });
    }

    static float[] speech() {
        float[] f = new float[1280];
        for (int i = 0; i < f.length; i++) f[i] = (float) (0.2 * Math.sin(2 * Math.PI * 440 * i / 16000.0));
        return f;
    }
    static float[] silence() { return new float[1280]; }

    void say() {
        for (int i = 0; i < 10; i++) c.onFrame(speech());
        for (int i = 0; i < 10; i++) c.onFrame(silence());
    }

    @Test public void happyPath() {
        assertTrue(c.startCommand());
        assertEquals(1, ducks);
        assertEquals(VoiceState.LISTENING, c.state());
        say();
        assertEquals(VoiceState.IDLE, c.state());
        assertTrue(states.contains(VoiceState.THINKING));
        assertTrue(states.contains(VoiceState.REPLY));
        assertEquals(Collections.singletonList("Playing Kaavaalaa."), spoken);
        assertEquals(1, unducks);
        assertEquals(1, ducks);
    }

    @Test public void wakeIgnoredWhileBusy() {
        assertTrue(c.startCommand());
        assertFalse(c.startCommand());
        assertEquals(1, ducks);
    }

    @Test public void timeoutRepliesDidntCatch() {
        c.startCommand();
        for (int i = 0; i < 75; i++) c.onFrame(silence());
        assertTrue(spoken.contains(VoiceReplies.didntCatch()));
        assertEquals(1, unducks);
        assertEquals(VoiceState.IDLE, c.state());
    }

    @Test public void unduckOnSttException() {
        sttThrows = true;
        c.startCommand();
        say();
        assertTrue(outcomes.stream().anyMatch(o -> o != null && o.text.equals(VoiceReplies.didntCatch())));
        assertEquals(1, unducks);
        assertEquals(VoiceState.IDLE, c.state());
    }

    @Test public void unduckOnExecutorException() {
        when(executor.handle(anyString(), any())).thenThrow(new RuntimeException("x"));
        c.startCommand();
        say();
        assertEquals(VoiceReplies.didntCatch(), lastNonNull());
        assertEquals(1, unducks);
        assertEquals(VoiceState.IDLE, c.state());
    }

    @Test public void unduckOnPauseMidSession() {
        c.startCommand();
        for (int i = 0; i < 5; i++) c.onFrame(speech());
        c.cancel();
        assertEquals(1, unducks);
        assertEquals(VoiceState.IDLE, c.state());
        assertTrue(spoken.isEmpty());
        // late frames after cancel are ignored
        for (int i = 0; i < 20; i++) c.onFrame(silence());
        assertEquals(VoiceState.IDLE, c.state());
        assertEquals(1, unducks);
    }

    @Test public void cancelIgnoresLateTtsCallback() {
        voiceImmediate = false;
        c.startCommand();
        say();
        assertEquals(VoiceState.REPLY, c.state());
        c.cancel();
        assertEquals(1, unducks);
        int n = states.size();
        pendingDone.get(0).run();
        assertEquals(n, states.size());
        assertEquals(1, unducks);
        assertTrue(c.startCommand());
        assertEquals(VoiceState.LISTENING, c.state());
    }

    @Test public void choicesWaitForTap() {
        Song s = VoiceFixtures.KAAVAALAA;
        Candidate cand = new Candidate(MatchKind.SONG, "k", "Kaavaalaa", "Anirudh", 0.7,
                Collections.singletonList(s));
        VoiceOutcome amb = new VoiceOutcome("Which one?", true, Arrays.asList(cand));
        when(executor.handle(anyString(), any())).thenReturn(amb);
        when(executor.choose(cand)).thenReturn(VoiceOutcome.spoken("Playing Kaavaalaa."));
        c.startCommand();
        say();
        assertEquals(VoiceState.REPLY, c.state());
        assertEquals(1, unducks);
        c.onChoice(cand);
        verify(executor).choose(cand);
        assertEquals("Playing Kaavaalaa.", lastNonNull());
        assertEquals(VoiceState.IDLE, c.state());
        assertEquals(1, unducks);
        assertEquals(1, ducks);
    }

    String lastNonNull() {
        String t = null;
        for (VoiceOutcome o : outcomes) if (o != null) t = o.text;
        return t;
    }

    @Test public void speakRepliesOffShowsTextOnly() {
        speakReplies = false;
        c.startCommand();
        say();
        assertTrue(spoken.isEmpty());
        assertEquals("Playing Kaavaalaa.", lastNonNull());
        assertEquals(1, unducks);
        assertEquals(VoiceState.IDLE, c.state());
    }

    @Test public void silentOutcomeNeverSpoken() {
        when(executor.handle(anyString(), any())).thenReturn(VoiceOutcome.silent("Paused"));
        c.startCommand();
        say();
        assertTrue(spoken.isEmpty());
        assertEquals(1, unducks);
        assertEquals(VoiceState.IDLE, c.state());
    }

    @Test public void ttsWatchdogFinishesStuckReply() {
        voiceImmediate = false;
        c.startCommand();
        say();
        assertEquals(VoiceState.REPLY, c.state());
        assertEquals(1, scheduled.size());
        assertEquals(2000L + 80L * "Playing Kaavaalaa.".length(), (long) scheduledDelays.get(0));
        scheduled.get(0).run();
        assertEquals(VoiceState.IDLE, c.state());
        assertEquals(1, unducks);
        // a late onDone afterwards changes nothing
        int n = states.size();
        pendingDone.get(0).run();
        assertEquals(n, states.size());
        assertEquals(1, unducks);
    }

    @Test public void ttsDoneCancelsWatchdog() {
        voiceImmediate = false;
        c.startCommand();
        say();
        pendingDone.get(0).run();
        assertEquals(VoiceState.IDLE, c.state());
        assertTrue(cancelled.get(0));
        assertEquals(1, unducks);
    }

    @Test public void throwingListenerStillUnducksAndReturnsIdle() {
        c.setListener((st, o, l) -> { throw new RuntimeException("ui"); });
        assertTrue(c.startCommand());
        say();
        assertEquals(VoiceState.IDLE, c.state());
        assertEquals(1, unducks);
        assertEquals(1, ducks);
    }

    private Candidate cand() {
        return new Candidate(MatchKind.SONG, "k", "Kaavaalaa", "Anirudh", 0.7,
                Collections.singletonList(VoiceFixtures.KAAVAALAA));
    }

    @Test public void newCommandSupersedesPendingChoices() {
        Candidate cand = cand();
        when(executor.handle(anyString(), any()))
                .thenReturn(new VoiceOutcome("Which one?", true, Arrays.asList(cand)))
                .thenReturn(VoiceOutcome.spoken("Playing Kaavaalaa."));
        c.startCommand();
        say();
        assertEquals(VoiceState.REPLY, c.state());
        assertTrue(c.startCommand());
        assertEquals(VoiceState.LISTENING, c.state());
        c.onChoice(cand);
        verify(executor, never()).choose(any());
        assertEquals(2, ducks);
        say();
        assertEquals(VoiceState.IDLE, c.state());
        assertEquals(2, unducks);
    }

    @Test public void startCommandRefusedInReplyWithoutChoices() {
        voiceImmediate = false;
        c.startCommand();
        say();
        assertEquals(VoiceState.REPLY, c.state());
        assertFalse(c.startCommand());
        assertEquals(1, ducks);
    }

    @Test public void spokenChoicesPromptUnducksWhenSpeechEnds() {
        voiceImmediate = false;
        Candidate cand = cand();
        when(executor.handle(anyString(), any()))
                .thenReturn(new VoiceOutcome("Which one?", true, Arrays.asList(cand)));
        c.startCommand();
        say();
        assertEquals(0, unducks);
        assertEquals(1, scheduled.size());
        pendingDone.get(0).run();
        assertEquals(1, unducks);
        assertEquals(VoiceState.REPLY, c.state());
    }

    @Test public void unspokenChoicesUnduckImmediately() {
        speakReplies = false;
        Candidate cand = cand();
        when(executor.handle(anyString(), any()))
                .thenReturn(new VoiceOutcome("Which one?", true, Arrays.asList(cand)));
        c.startCommand();
        say();
        assertEquals(1, unducks);
        assertEquals(VoiceState.REPLY, c.state());
    }

    @Test public void spokenChoicesWatchdogUnducks() {
        voiceImmediate = false;
        Candidate cand = cand();
        when(executor.handle(anyString(), any()))
                .thenReturn(new VoiceOutcome("Which one?", true, Arrays.asList(cand)));
        c.startCommand();
        say();
        scheduled.get(0).run();
        assertEquals(1, unducks);
        assertEquals(VoiceState.REPLY, c.state());
    }

    @Test public void cancelDuringThinkingIgnoresLateResult() {
        final List<Runnable> jobs = new ArrayList<>();
        VoiceSessionController.Deps d0 = deferredDeps(jobs);
        VoiceSessionController k = new VoiceSessionController(d0);
        k.startCommand();
        for (int i = 0; i < 10; i++) k.onFrame(speech());
        for (int i = 0; i < 10; i++) k.onFrame(silence());
        assertEquals(VoiceState.THINKING, k.state());
        k.cancel();
        jobs.get(0).run();
        assertEquals(VoiceState.IDLE, k.state());
        assertTrue(spoken.isEmpty());
        verify(executor, never()).handle(anyString(), any());
        assertEquals(1, unducks);
    }

    private VoiceSessionController.Deps deferredDeps(List<Runnable> jobs) {
        return new VoiceSessionController.Deps(
                new com.psthetech.swara.voice.stt.SpeechToText() {
                    @Override public String transcribe(float[] a) { return "play x"; }
                    @Override public void close() {}
                },
                executor, new SwaraVoice() {
                    @Override public boolean isAvailable() { return true; }
                    @Override public void speak(String t, Runnable r) { spoken.add(t); r.run(); }
                    @Override public void shutdown() {}
                }, () -> null, () -> ducks++, () -> unducks++,
                jobs::add, Runnable::run, () -> true,
                (r, ms) -> () -> { });
    }

    @Test public void onChoiceIgnoredWhenNotPending() {
        c.onChoice(cand());
        c.startCommand();
        c.onChoice(cand());
        verify(executor, never()).choose(any());
        assertEquals(VoiceState.LISTENING, c.state());
    }

    @Test public void watchdogAfterCancelIsNoop() {
        voiceImmediate = false;
        c.startCommand();
        say();
        c.cancel();
        int n = states.size();
        scheduled.get(0).run();
        assertEquals(n, states.size());
        assertEquals(1, unducks);
    }

    @Test public void speakThrowingStillFinishes() {
        SwaraVoice bad = new SwaraVoice() {
            @Override public boolean isAvailable() { return true; }
            @Override public void speak(String t, Runnable r) { throw new RuntimeException("tts"); }
            @Override public void shutdown() {}
        };
        VoiceSessionController k = new VoiceSessionController(new VoiceSessionController.Deps(
                new com.psthetech.swara.voice.stt.SpeechToText() {
                    @Override public String transcribe(float[] a) { return "play x"; }
                    @Override public void close() {}
                }, executor, bad, () -> null, () -> ducks++, () -> unducks++,
                Runnable::run, Runnable::run, () -> true, (r, ms) -> () -> { }));
        k.startCommand();
        for (int i = 0; i < 10; i++) k.onFrame(speech());
        for (int i = 0; i < 10; i++) k.onFrame(silence());
        assertEquals(VoiceState.IDLE, k.state());
        assertEquals(1, unducks);
    }
}

package com.psthetech.swara.voice.session;

import androidx.annotation.Nullable;

import com.psthetech.swara.voice.audio.CommandRecorder;
import com.psthetech.swara.voice.audio.EnergyVad;
import com.psthetech.swara.voice.exec.VoiceCommandExecutor;
import com.psthetech.swara.voice.exec.VoiceOutcome;
import com.psthetech.swara.voice.exec.VoiceReplies;
import com.psthetech.swara.voice.match.Candidate;
import com.psthetech.swara.voice.match.LibraryIndex;
import com.psthetech.swara.voice.stt.SpeechToText;
import com.psthetech.swara.voice.tts.SwaraVoice;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One voice command end to end: duck, listen, transcribe, execute, reply, unduck.
 * <p>
 * Threading: {@link #onFrame} runs on the mic thread and only feeds the recorder. Everything
 * else (state, listener, duck/unduck, executor, TTS) runs via {@code deps.main}. A generation
 * counter invalidates late STT/TTS callbacks from a cancelled session.
 */
public final class VoiceSessionController {
    public static final int SAMPLE_RATE = 16000;
    public static final int HOP = 1280;
    private static final long TTS_BASE_MS = 2000;
    private static final long TTS_PER_CHAR_MS = 80;

    public interface Cancellable { void cancel(); }

    public interface Scheduler { Cancellable schedule(Runnable r, long delayMs); }

    public interface Listener {
        void onState(VoiceState s, @Nullable VoiceOutcome outcome, float level);
    }

    public static final class Deps {
        public final SpeechToText stt;
        public final VoiceCommandExecutor executor;
        public final SwaraVoice voice;
        public final Supplier<LibraryIndex> library;
        public final Runnable duck;
        public final Runnable unduck;
        public final Executor sttExecutor;
        public final Executor main;
        public final BooleanSupplier speakReplies;
        public final Scheduler scheduler;

        public Deps(SpeechToText stt, VoiceCommandExecutor executor, SwaraVoice voice,
                    Supplier<LibraryIndex> library, Runnable duck, Runnable unduck,
                    Executor sttExecutor, Executor main, BooleanSupplier speakReplies,
                    Scheduler scheduler) {
            this.stt = stt;
            this.executor = executor;
            this.voice = voice;
            this.library = library;
            this.duck = duck;
            this.unduck = unduck;
            this.sttExecutor = sttExecutor;
            this.main = main;
            this.speakReplies = speakReplies;
            this.scheduler = scheduler;
        }
    }

    /** Mic-thread view of one listening session. */
    private static final class Capture {
        final int gen;
        final CommandRecorder recorder = new CommandRecorder(new EnergyVad(HOP), SAMPLE_RATE);
        boolean finished; // mic thread only
        Capture(int gen) { this.gen = gen; }
    }

    private final Deps deps;
    private @Nullable Listener listener;

    // Main-thread state.
    private VoiceState state = VoiceState.IDLE;
    private int gen;
    private boolean unducked = true;
    private boolean choicesPending;
    private int speakToken;
    private @Nullable Cancellable watchdog;

    private volatile @Nullable Capture capture;
    private volatile VoiceState stateView = VoiceState.IDLE;

    public VoiceSessionController(Deps deps) {
        this.deps = deps;
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }

    public VoiceState state() { return stateView; }

    /** Main thread. False (ignored) unless IDLE. */
    public boolean startCommand() {
        if (state != VoiceState.IDLE) return false;
        gen++;
        unducked = false;
        choicesPending = false;
        try {
            deps.duck.run();
        } catch (RuntimeException ignored) { }
        capture = new Capture(gen);
        setState(VoiceState.LISTENING, null, 0f);
        return true;
    }

    /** Mic thread. Cheap: pushes into the recorder, hands off to STT when done. */
    public void onFrame(float[] frame) {
        final Capture cap = capture;
        if (cap == null || cap.finished) return;
        final int g = cap.gen;
        double sum = 0;
        for (float v : frame) sum += v * v;
        final float level = frame.length == 0 ? 0f : (float) Math.sqrt(sum / frame.length);
        CommandRecorder.State rs;
        try {
            rs = cap.recorder.push(frame);
        } catch (RuntimeException e) {
            cap.finished = true;
            deps.main.execute(() -> reply(g, VoiceOutcome.spoken(VoiceReplies.didntCatch())));
            return;
        }
        if (rs == CommandRecorder.State.DONE) {
            cap.finished = true;
            final float[] audio = cap.recorder.audio();
            deps.main.execute(() -> startThinking(g, audio));
        } else if (rs == CommandRecorder.State.TIMEOUT) {
            cap.finished = true;
            deps.main.execute(() -> reply(g, VoiceOutcome.spoken(VoiceReplies.didntCatch())));
        } else {
            deps.main.execute(() -> {
                if (g == gen && state == VoiceState.LISTENING) {
                    setState(VoiceState.LISTENING, null, level);
                }
            });
        }
    }

    /** Main thread. The user tapped one of the choices of a REPLY. */
    public void onChoice(Candidate c) {
        if (state != VoiceState.REPLY || !choicesPending) return;
        choicesPending = false;
        speakToken++; // drop any in-flight speech callback for the question
        cancelWatchdog();
        VoiceOutcome out;
        try {
            out = deps.executor.choose(c);
        } catch (RuntimeException e) {
            out = VoiceOutcome.spoken(VoiceReplies.didntCatch());
        }
        reply(gen, out);
    }

    /** Main thread. Activity paused: drop everything, no reply. */
    public void cancel() {
        if (state == VoiceState.IDLE) return;
        gen++;
        speakToken++;
        capture = null;
        choicesPending = false;
        cancelWatchdog();
        unduckOnce();
        setState(VoiceState.IDLE, null, 0f);
    }

    // --- internals (main thread) ---

    private void startThinking(int g, float[] audio) {
        if (g != gen || state != VoiceState.LISTENING) return;
        capture = null;
        setState(VoiceState.THINKING, null, 0f);
        try {
            deps.sttExecutor.execute(() -> {
                String text;
                try {
                    text = deps.stt.transcribe(audio);
                } catch (Throwable t) {
                    deps.main.execute(() -> reply(g, VoiceOutcome.spoken(VoiceReplies.didntCatch())));
                    return;
                }
                final String transcript = text == null ? "" : text;
                deps.main.execute(() -> execute(g, transcript));
            });
        } catch (RuntimeException e) {
            reply(g, VoiceOutcome.spoken(VoiceReplies.didntCatch()));
        }
    }

    private void execute(int g, String transcript) {
        if (g != gen || state != VoiceState.THINKING) return;
        VoiceOutcome out;
        try {
            out = deps.executor.handle(transcript, deps.library.get());
        } catch (Throwable t) {
            out = VoiceOutcome.spoken(VoiceReplies.didntCatch());
        }
        reply(g, out);
    }

    private void reply(int g, VoiceOutcome out) {
        if (g != gen || state == VoiceState.IDLE) return;
        capture = null;
        final boolean hasChoices = !out.choices.isEmpty();
        choicesPending = hasChoices;
        setState(VoiceState.REPLY, out, 0f);
        if (hasChoices) unduckOnce(); // music resumes while the user chooses

        boolean speak = false;
        try {
            speak = out.speak && deps.speakReplies.getAsBoolean() && deps.voice.isAvailable();
        } catch (RuntimeException ignored) { }
        if (!speak) {
            if (!hasChoices) finish(g);
            return;
        }
        final int token = ++speakToken;
        final Runnable done = () -> {
            if (g != gen || token != speakToken) return;
            speakToken++;
            cancelWatchdog();
            if (!hasChoices) finish(g);
        };
        if (!hasChoices) {
            try {
                watchdog = deps.scheduler.schedule(done, TTS_BASE_MS + TTS_PER_CHAR_MS * out.text.length());
            } catch (RuntimeException ignored) { }
        }
        try {
            deps.voice.speak(out.text, done);
        } catch (RuntimeException e) {
            done.run();
        }
    }

    private void finish(int g) {
        if (g != gen || state == VoiceState.IDLE) return;
        cancelWatchdog();
        unduckOnce();
        setState(VoiceState.IDLE, null, 0f);
    }

    private void unduckOnce() {
        if (unducked) return;
        unducked = true;
        try {
            deps.unduck.run();
        } catch (RuntimeException ignored) { }
    }

    private void cancelWatchdog() {
        Cancellable w = watchdog;
        watchdog = null;
        if (w != null) w.cancel();
    }

    private void setState(VoiceState s, @Nullable VoiceOutcome outcome, float level) {
        state = s;
        stateView = s;
        Listener l = listener;
        if (l != null) l.onState(s, outcome, level);
    }
}

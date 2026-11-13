package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.voice.audio.CommandRecorder;
import com.psthetech.swara.voice.audio.CommandRecorder.State;
import com.psthetech.swara.voice.audio.EnergyVad;
import org.junit.Test;

public class CommandRecorderTest {
    static final int HOP = 1280;

    private CommandRecorder recorder() {
        return new CommandRecorder(new EnergyVad(HOP), 16000);
    }

    @Test public void endsAfterEightHundredMsSilence() {
        CommandRecorder r = recorder();
        for (int i = 0; i < 3; i++) r.push(EnergyVadTest.noise(0.001f, i));
        State s = State.WAITING;
        for (int i = 0; i < 10; i++) s = r.push(EnergyVadTest.sine(0.2f));
        assertEquals(State.RECORDING, s);
        for (int i = 0; i < 10; i++) s = r.push(EnergyVadTest.zeros());
        assertEquals(State.DONE, s);
        assertEquals(20 * HOP, r.audio().length);
    }

    @Test public void timesOutWithoutSpeech() {
        CommandRecorder r = recorder();
        State s = State.WAITING;
        for (int i = 0; i < 74; i++) s = r.push(EnergyVadTest.zeros());
        assertEquals(State.WAITING, s);
        s = r.push(EnergyVadTest.zeros());
        assertEquals(State.TIMEOUT, s);
    }

    @Test public void capsAtSixSeconds() {
        CommandRecorder r = recorder();
        State s = State.WAITING;
        int doneAt = -1;
        for (int i = 1; i <= 80; i++) {
            s = r.push(EnergyVadTest.sine(0.2f));
            if (s == State.DONE && doneAt < 0) doneAt = i;
        }
        assertEquals(75, doneAt);
        assertEquals(State.DONE, s);
        assertEquals(75 * HOP, r.audio().length);
    }
}

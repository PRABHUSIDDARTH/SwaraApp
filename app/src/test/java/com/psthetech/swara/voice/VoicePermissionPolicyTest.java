package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.voice.VoicePermissionPolicy.Action;

import org.junit.Test;

public class VoicePermissionPolicyTest {

    @Test public void grantedStartsRegardlessOfHistory() {
        assertEquals(Action.START, VoicePermissionPolicy.decide(true, false, false));
        assertEquals(Action.START, VoicePermissionPolicy.decide(true, false, true));
        assertEquals(Action.START, VoicePermissionPolicy.decide(true, true, true));
    }

    @Test public void notAskedBeforeRequests() {
        assertEquals(Action.REQUEST, VoicePermissionPolicy.decide(false, false, false));
    }

    @Test public void rationaleRequestsAgain() {
        assertEquals(Action.REQUEST, VoicePermissionPolicy.decide(false, true, true));
        assertEquals(Action.REQUEST, VoicePermissionPolicy.decide(false, true, false));
    }

    @Test public void deniedPermanentlyOpensSettings() {
        // Asked before, the system no longer shows a rationale: "Don't ask again" / denied twice.
        assertEquals(Action.EXPLAIN_SETTINGS, VoicePermissionPolicy.decide(false, false, true));
    }
}

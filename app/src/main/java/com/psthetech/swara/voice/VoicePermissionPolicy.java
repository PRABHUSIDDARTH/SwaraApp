package com.psthetech.swara.voice;

/** Pure decision for the microphone permission when the user taps the mic. */
public final class VoicePermissionPolicy {
    public enum Action { START, REQUEST, EXPLAIN_SETTINGS }

    private VoicePermissionPolicy() {}

    /**
     * @param granted             RECORD_AUDIO is granted
     * @param shouldShowRationale the system would still show the dialog after an earlier denial
     * @param askedBefore         we have launched the system request at least once
     */
    public static Action decide(boolean granted, boolean shouldShowRationale, boolean askedBefore) {
        if (granted) return Action.START;
        if (!askedBefore || shouldShowRationale) return Action.REQUEST;
        return Action.EXPLAIN_SETTINGS;
    }
}

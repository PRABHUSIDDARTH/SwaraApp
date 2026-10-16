package com.psthetech.swara.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;

/**
 * Persists the playback queue, current index and position so the service can restore
 * the session after the process is killed.
 */
final class PlaybackStateStore {

    private static final String PREFS = "swara_playback_state";
    private static final String KEY_IDS = "song_ids";
    private static final String KEY_INDEX = "index";
    private static final String KEY_POSITION = "position_ms";
    private static final String KEY_SHUFFLE = "shuffle";
    private static final String KEY_REPEAT = "repeat_mode";

    static final class State {
        final long[] songIds;
        final int index;
        final long positionMs;
        final boolean shuffle;
        final int repeatMode;

        State(@NonNull long[] songIds, int index, long positionMs, boolean shuffle, int repeatMode) {
            this.songIds = songIds;
            this.index = index;
            this.positionMs = positionMs;
            this.shuffle = shuffle;
            this.repeatMode = repeatMode;
        }
    }

    private final SharedPreferences prefs;

    PlaybackStateStore(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(@NonNull State state) {
        prefs.edit()
                .putString(KEY_IDS, encodeIds(state.songIds))
                .putInt(KEY_INDEX, state.index)
                .putLong(KEY_POSITION, Math.max(0, state.positionMs))
                .putBoolean(KEY_SHUFFLE, state.shuffle)
                .putInt(KEY_REPEAT, state.repeatMode)
                .apply();
    }

    @Nullable
    State load() {
        long[] ids = decodeIds(prefs.getString(KEY_IDS, null));
        if (ids.length == 0) return null;
        return new State(ids,
                prefs.getInt(KEY_INDEX, 0),
                prefs.getLong(KEY_POSITION, 0),
                prefs.getBoolean(KEY_SHUFFLE, false),
                prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF));
    }

    /** Package-private for tests. */
    static String encodeIds(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (id <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    /** Package-private for tests. Malformed entries are skipped. */
    static long[] decodeIds(@Nullable String encoded) {
        if (encoded == null || encoded.isEmpty()) return new long[0];
        String[] parts = encoded.split(",");
        long[] out = new long[parts.length];
        int n = 0;
        for (String p : parts) {
            try {
                long id = Long.parseLong(p.trim());
                if (id > 0) out[n++] = id;
            } catch (NumberFormatException ignored) { }
        }
        return java.util.Arrays.copyOf(out, n);
    }
}

package com.psthetech.swara.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Analysed mood features for one song (one row per song). */
@Entity(tableName = "song_mood")
public class SongMood {

    public static final int STATUS_OK = 0;
    public static final int STATUS_FAILED = 1;

    @PrimaryKey
    public long songId;

    public double valence;
    public double energy;
    public double tempoBpm;

    /** Raw, un-normalized feature vector; null when analysis failed. */
    public byte[] rawFeatures;

    public int scorerVersion;
    public int status;
    public long analyzedAt;

    public SongMood() {
    }

    /** Placeholder row recording that analysis of this song failed. */
    public static SongMood failed(long songId, int scorerVersion, long nowMs) {
        SongMood m = new SongMood();
        m.songId = songId;
        m.valence = 0.5;
        m.energy = 0.5;
        m.tempoBpm = 0;
        m.rawFeatures = null;
        m.scorerVersion = scorerVersion;
        m.status = STATUS_FAILED;
        m.analyzedAt = nowMs;
        return m;
    }
}

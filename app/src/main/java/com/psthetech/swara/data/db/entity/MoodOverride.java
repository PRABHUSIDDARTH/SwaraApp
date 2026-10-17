package com.psthetech.swara.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * The user's correction of a song's mood. Separate from {@link SongMood}: analysis keeps writing
 * its own values, and this row takes precedence wherever moods are read.
 */
@Entity(tableName = "mood_override")
public class MoodOverride {

    @PrimaryKey
    public long songId;

    /** {@link com.psthetech.swara.vibe.MoodLabel#name()}. */
    @NonNull
    public String label = "";

    public long setAt;

    public MoodOverride() {
    }

    @Ignore
    public MoodOverride(long songId, @NonNull String label, long setAt) {
        this.songId = songId;
        this.label = label;
        this.setAt = setAt;
    }
}

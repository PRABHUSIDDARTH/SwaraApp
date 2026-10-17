package com.psthetech.swara.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A generated mix; songIds is a comma-separated list of song ids. */
@Entity(tableName = "mixes")
public class Mix {

    @PrimaryKey
    @NonNull
    public String mixKey = "";

    public String title;

    /** Comma-separated song ids. */
    public String songIds;

    public long builtAt;

    public Mix() {
    }
}

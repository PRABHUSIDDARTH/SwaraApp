package com.psthetech.swara.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A stored (frozen) Wrapped recap, e.g. "month-2026-10" or "year-2026". */
@Entity(tableName = "recap")
public class Recap {
    @PrimaryKey
    @NonNull
    public String recapKey = "";
    public int periodStart;
    public int periodEnd;
    @NonNull
    public String statsJson = "{}";
    public long createdAt;
    public boolean seen;
    public boolean bannerDismissed;
}

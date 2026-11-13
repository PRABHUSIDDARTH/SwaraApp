package com.psthetech.swara.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * A manual artist-credit override. {@code toKey == null} means "never merge this key";
 * otherwise the key is merged into {@code toKey}. Keys are canonical artist keys.
 */
@Entity(tableName = "artist_alias")
public class ArtistAlias {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "from_key")
    public String fromKey = "";

    @Nullable
    @ColumnInfo(name = "to_key")
    public String toKey;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public ArtistAlias() {
    }

    @Ignore
    public ArtistAlias(@NonNull String fromKey, @Nullable String toKey, long createdAt) {
        this.fromKey = fromKey;
        this.toKey = toKey;
        this.createdAt = createdAt;
    }
}

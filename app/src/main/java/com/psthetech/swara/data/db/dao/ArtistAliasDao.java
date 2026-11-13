package com.psthetech.swara.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.psthetech.swara.data.db.entity.ArtistAlias;

import java.util.List;

@Dao
public interface ArtistAliasDao {

    @Query("SELECT * FROM artist_alias")
    List<ArtistAlias> getAll();

    @Query("SELECT * FROM artist_alias")
    LiveData<List<ArtistAlias>> observeAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ArtistAlias alias);

    @Query("DELETE FROM artist_alias WHERE from_key = :fromKey")
    void delete(String fromKey);
}

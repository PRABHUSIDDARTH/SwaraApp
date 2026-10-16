package com.psthetech.swara.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.psthetech.swara.data.db.entity.Mix;

import java.util.List;

@Dao
public interface MixDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(Mix mix);

    @Query("SELECT * FROM mixes ORDER BY mixKey ASC")
    LiveData<List<Mix>> allLive();

    @Query("DELETE FROM mixes")
    void deleteAll();
}

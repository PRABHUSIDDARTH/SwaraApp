package com.psthetech.swara.data.db.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.psthetech.swara.data.db.entity.ListenDaily;
import com.psthetech.swara.data.db.entity.ListenHourly;
import com.psthetech.swara.data.db.entity.Recap;
import com.psthetech.swara.data.db.entity.SongSnapshot;

import java.util.List;

/** Swara Wrapped aggregates. These tables are never trimmed or pruned. */
@Dao
public interface WrappedDao {

    // ===== listen_daily =====

    @Query("SELECT * FROM listen_daily WHERE day = :day AND songId = :songId")
    @Nullable ListenDaily dailyRow(int day, long songId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putDaily(ListenDaily row);

    /** Adds the given amounts to the (day, songId) row, creating it if needed. */
    @Transaction
    default void addDaily(int day, long songId, long playedMs, int plays, int finishes,
                          int skips, int repeats, int vibeStarts) {
        ListenDaily r = dailyRow(day, songId);
        if (r == null) {
            r = new ListenDaily();
            r.day = day;
            r.songId = songId;
        }
        r.playedMs += playedMs;
        r.plays += plays;
        r.finishes += finishes;
        r.skips += skips;
        r.repeats += repeats;
        r.vibeStarts += vibeStarts;
        putDaily(r);
    }

    @Query("SELECT * FROM listen_daily WHERE day BETWEEN :fromDay AND :toDay")
    List<ListenDaily> daily(int fromDay, int toDay);

    @Query("SELECT * FROM listen_daily WHERE day < :day")
    List<ListenDaily> dailyBefore(int day);

    @Query("SELECT COUNT(*) FROM listen_daily WHERE songId = :songId AND day = :day")
    int countDailyFor(long songId, int day);

    @Query("SELECT COALESCE(SUM(playedMs), 0) FROM listen_daily WHERE day BETWEEN :fromDay AND :toDay")
    long totalPlayedMs(int fromDay, int toDay);

    @Query("SELECT * FROM listen_daily")
    List<ListenDaily> allDaily();

    @Query("SELECT MIN(day) FROM listen_daily")
    @Nullable Integer firstDay();

    // ===== listen_hourly =====

    @Query("SELECT * FROM listen_hourly WHERE day = :day AND hour = :hour")
    @Nullable ListenHourly hourlyRow(int day, int hour);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putHourly(ListenHourly row);

    @Transaction
    default void addHourly(int day, int hour, long playedMs) {
        ListenHourly r = hourlyRow(day, hour);
        if (r == null) {
            r = new ListenHourly();
            r.day = day;
            r.hour = hour;
        }
        r.playedMs += playedMs;
        putHourly(r);
    }

    @Query("SELECT * FROM listen_hourly WHERE day BETWEEN :fromDay AND :toDay")
    List<ListenHourly> hourly(int fromDay, int toDay);

    @Query("SELECT * FROM listen_hourly")
    List<ListenHourly> allHourly();

    // ===== song_snapshot =====

    @Query("SELECT * FROM song_snapshot WHERE songId = :songId")
    @Nullable SongSnapshot snapshot(long songId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void putSnapshot(SongSnapshot s);

    /** Inserts or refreshes tags, keeping the earliest firstHeardDay. */
    @Transaction
    default void upsertSnapshot(SongSnapshot s) {
        SongSnapshot old = snapshot(s.songId);
        if (old != null && old.firstHeardDay > 0
                && (s.firstHeardDay <= 0 || old.firstHeardDay < s.firstHeardDay)) {
            s.firstHeardDay = old.firstHeardDay;
        }
        putSnapshot(s);
    }

    @Query("SELECT * FROM song_snapshot")
    List<SongSnapshot> snapshots();

    // ===== recap =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRecap(Recap r);

    @Query("SELECT * FROM recap WHERE recapKey = :key")
    @Nullable Recap recap(String key);

    @Query("SELECT * FROM recap")
    List<Recap> recaps();

    @Query("SELECT recapKey FROM recap")
    List<String> recapKeys();

    @Query("SELECT * FROM recap WHERE bannerDismissed = 0 AND createdAt >= :sinceMs "
            + "ORDER BY createdAt DESC LIMIT 1")
    LiveData<Recap> latestUndismissedLive(long sinceMs);

    @Query("UPDATE recap SET seen = 1 WHERE recapKey = :key")
    void markSeen(String key);

    @Query("UPDATE recap SET bannerDismissed = 1 WHERE recapKey = :key")
    void dismissBanner(String key);
}

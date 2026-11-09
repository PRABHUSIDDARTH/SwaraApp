package com.psthetech.swara.wrapped.backup;

import androidx.annotation.NonNull;

import com.psthetech.swara.wrapped.DailyRow;
import com.psthetech.swara.wrapped.HourlyRow;
import com.psthetech.swara.wrapped.SongInfo;

import java.util.List;

/** Everything a listening-history file carries. No audio, no file paths. */
public final class HistoryData {

    public static final class RecapRow {
        @NonNull public final String key;
        public final int periodStart;
        public final int periodEnd;
        @NonNull public final String statsJson;
        public final long createdAt;
        public final boolean seen;
        public final boolean bannerDismissed;

        public RecapRow(@NonNull String key, int periodStart, int periodEnd, @NonNull String statsJson,
                        long createdAt, boolean seen, boolean bannerDismissed) {
            this.key = key;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
            this.statsJson = statsJson;
            this.createdAt = createdAt;
            this.seen = seen;
            this.bannerDismissed = bannerDismissed;
        }
    }

    public static final class OverrideRow {
        public final long songId;
        @NonNull public final String label;
        public final long setAt;

        public OverrideRow(long songId, @NonNull String label, long setAt) {
            this.songId = songId;
            this.label = label;
            this.setAt = setAt;
        }
    }

    public final List<DailyRow> daily;
    public final List<HourlyRow> hourly;
    public final List<SongInfo> songs;
    public final List<RecapRow> recaps;
    public final List<OverrideRow> overrides;

    public HistoryData(List<DailyRow> daily, List<HourlyRow> hourly, List<SongInfo> songs,
                       List<RecapRow> recaps, List<OverrideRow> overrides) {
        this.daily = daily;
        this.hourly = hourly;
        this.songs = songs;
        this.recaps = recaps;
        this.overrides = overrides;
    }
}

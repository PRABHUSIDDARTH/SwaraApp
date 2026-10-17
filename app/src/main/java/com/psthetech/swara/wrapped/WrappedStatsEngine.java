package com.psthetech.swara.wrapped;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Pure computation of {@link WrappedStats} from the Wrapped aggregate rows. */
public final class WrappedStatsEngine {

    static final int TOP_N = 5;
    static final int MIN_ALBUM_PLAYS = 3;
    static final int MIN_REPEATS_OR_SKIPS = 3;
    static final int REDISCOVER_GAP_DAYS = 60;

    private WrappedStatsEngine() {}

    /**
     * @param dailyInPeriod  listen_daily rows (rows outside the period are ignored)
     * @param dailyBefore    rows before the period start (for "rediscovered")
     * @param hourlyInPeriod listen_hourly rows (rows outside the period are ignored)
     * @param snapshots      stored tags by song id
     * @param resolvedMoods  {valence, energy} per song (user corrections already applied)
     */
    @NonNull
    public static WrappedStats compute(@NonNull Period p, @NonNull List<DailyRow> dailyInPeriod,
                                       @NonNull List<DailyRow> dailyBefore,
                                       @NonNull List<HourlyRow> hourlyInPeriod,
                                       @NonNull Map<Long, SongInfo> snapshots,
                                       @NonNull Map<Long, double[]> resolvedMoods) {
        WrappedStats.Builder b = new WrappedStats.Builder();
        b.period = p;

        Map<Long, long[]> perSong = new HashMap<>(); // playedMs, plays, repeats, skips, vibeStarts
        Map<Integer, Long> perDay = new HashMap<>();
        for (DailyRow r : dailyInPeriod) {
            if (!p.contains(r.day)) continue;
            long[] s = perSong.computeIfAbsent(r.songId, k -> new long[5]);
            s[0] += r.playedMs;
            s[1] += r.plays;
            s[2] += r.repeats;
            s[3] += r.skips;
            s[4] += r.vibeStarts;
            b.totalMs += r.playedMs;
            b.vibeStarts += r.vibeStarts;
            if (r.playedMs > 0 || r.plays > 0) perDay.merge(r.day, r.playedMs, Long::sum);
        }

        // Top songs by playedMs (ties: lower songId).
        List<Long> byTime = sortedIds(perSong, s -> s[0]);
        for (Long id : byTime) {
            if (perSong.get(id)[0] <= 0 || b.topSongIds.size() >= TOP_N) break;
            b.topSongIds.add(id);
        }
        if (!b.topSongIds.isEmpty()) b.topSongMs = perSong.get(b.topSongIds.get(0))[0];

        // Top artists by canonical key.
        Map<String, Long> artistMs = new HashMap<>();
        Map<String, String> artistName = new HashMap<>();
        for (Map.Entry<Long, long[]> e : perSong.entrySet()) {
            SongInfo info = snapshots.get(e.getKey());
            if (info == null || e.getValue()[0] <= 0) continue;
            String key = artistKey(info);
            if (key == null) continue;
            artistMs.merge(key, e.getValue()[0], Long::sum);
            artistName.putIfAbsent(key, info.artist);
        }
        List<String> artists = new ArrayList<>(artistMs.keySet());
        artists.sort(Comparator.comparingLong((String k) -> -artistMs.get(k)).thenComparing(k -> k));
        for (String k : artists) {
            if (b.topArtistKeys.size() >= TOP_N) break;
            b.topArtistKeys.add(k);
            b.topArtistNames.add(artistName.get(k) != null ? artistName.get(k) : k);
        }

        // Top album (needs ≥ 3 plays).
        Map<Long, long[]> albums = new HashMap<>(); // playedMs, plays
        for (Map.Entry<Long, long[]> e : perSong.entrySet()) {
            SongInfo info = snapshots.get(e.getKey());
            if (info == null || info.albumId <= 0) continue;
            long[] a = albums.computeIfAbsent(info.albumId, k -> new long[2]);
            a[0] += e.getValue()[0];
            a[1] += e.getValue()[1];
        }
        for (Long albumId : sortedIds(albums, a -> a[0])) {
            if (albums.get(albumId)[1] >= MIN_ALBUM_PLAYS && albums.get(albumId)[0] > 0) {
                b.topAlbumId = albumId;
                break;
            }
        }

        // Mood mix.
        for (Map.Entry<Long, long[]> e : perSong.entrySet()) {
            MoodBucket bucket = MoodBucket.of(resolvedMoods.get(e.getKey()));
            if (bucket == null || e.getValue()[0] <= 0) continue;
            b.moodMs.merge(bucket, e.getValue()[0], Long::sum);
            b.moodKnownMs += e.getValue()[0];
        }

        // Vibe, repeats, skips.
        b.topVibeSongId = topWithMinimum(perSong, 4, 1);
        b.mostRepeatedSongId = topWithMinimum(perSong, 2, MIN_REPEATS_OR_SKIPS);
        if (b.mostRepeatedSongId != -1) b.mostRepeatedCount = (int) perSong.get(b.mostRepeatedSongId)[2];
        b.mostSkippedSongId = topWithMinimum(perSong, 3, MIN_REPEATS_OR_SKIPS);

        // Hours.
        long[] byHour = new long[24];
        for (HourlyRow h : hourlyInPeriod) {
            if (!p.contains(h.day) || h.hour < 0 || h.hour > 23) continue;
            byHour[h.hour] += h.playedMs;
        }
        long bestHour = 0;
        for (int h = 0; h < 24; h++) {
            if (byHour[h] > bestHour) {
                bestHour = byHour[h];
                b.peakHour = h;
            }
            if (h >= 22 || h <= 3) b.nightMs += byHour[h];
            if (h >= 5 && h <= 8) b.morningMs += byHour[h];
        }

        // Days: busiest weekday (ties: Monday first), streaks.
        long[] byWeekday = new long[7];
        for (Map.Entry<Integer, Long> e : perDay.entrySet()) byWeekday[Day.weekdayIndex(e.getKey())] += e.getValue();
        long bestWd = 0;
        for (int w = 0; w < 7; w++) {
            if (byWeekday[w] > bestWd) {
                bestWd = byWeekday[w];
                b.busiestWeekday = w;
            }
        }
        TreeSet<Integer> days = new TreeSet<>(perDay.keySet());
        b.listeningDays = days.size();
        int streak = 0;
        Integer prev = null;
        for (Integer d : days) {
            streak = (prev != null && Day.addDays(prev, 1) == d) ? streak + 1 : 1;
            b.longestStreakDays = Math.max(b.longestStreakDays, streak);
            prev = d;
        }

        // Discovery.
        Set<Long> played = new HashSet<>();
        for (Map.Entry<Long, long[]> e : perSong.entrySet()) {
            if (e.getValue()[0] > 0 || e.getValue()[1] > 0) played.add(e.getKey());
        }
        b.distinctSongs = played.size();
        for (Long id : played) {
            SongInfo info = snapshots.get(id);
            if (info != null && info.firstHeardDay > 0 && p.contains(info.firstHeardDay)) b.newSongs++;
        }
        b.rediscoveredSongId = rediscovered(p, perSong, dailyBefore);

        // Song info for every referenced id.
        List<Long> refs = new ArrayList<>(b.topSongIds);
        refs.add(b.topVibeSongId);
        refs.add(b.mostRepeatedSongId);
        refs.add(b.mostSkippedSongId);
        refs.add(b.rediscoveredSongId);
        for (Map.Entry<Long, long[]> e : perSong.entrySet()) {
            SongInfo info = snapshots.get(e.getKey());
            if (info != null && b.topAlbumId > 0 && info.albumId == b.topAlbumId) {
                refs.add(e.getKey());
                break;
            }
        }
        for (Long id : refs) {
            // -1 is the "none" sentinel; imported songs not on this phone use ids below -1.
            if (id == null || id == -1L || id == 0L) continue;
            SongInfo info = snapshots.get(id);
            if (info != null) b.songs.put(id, info);
        }
        return new WrappedStats(b);
    }

    @Nullable
    private static String artistKey(SongInfo info) {
        if (info.artistKey != null && !info.artistKey.isEmpty()) return info.artistKey;
        if (info.artist == null || info.artist.trim().isEmpty()) return null;
        return info.artist.trim().toLowerCase(Locale.ROOT);
    }

    private interface Metric {
        long of(long[] s);
    }

    /** Ids sorted by metric descending, ties by id ascending. */
    private static List<Long> sortedIds(Map<Long, long[]> m, Metric metric) {
        List<Long> ids = new ArrayList<>(m.keySet());
        ids.sort(Comparator.comparingLong((Long id) -> -metric.of(m.get(id))).thenComparingLong(id -> id));
        return ids;
    }

    private static long topWithMinimum(Map<Long, long[]> perSong, int column, int minimum) {
        List<Long> ids = sortedIds(perSong, s -> s[column]);
        if (ids.isEmpty()) return -1;
        long best = ids.get(0);
        return perSong.get(best)[column] >= minimum ? best : -1;
    }

    private static long rediscovered(Period p, Map<Long, long[]> perSong, Collection<DailyRow> before) {
        int gapStart = Day.addDays(p.start, -REDISCOVER_GAP_DAYS);
        Set<Long> playedBefore = new HashSet<>();
        Set<Long> playedInGap = new HashSet<>();
        for (DailyRow r : before) {
            if (r.day >= p.start || (r.playedMs <= 0 && r.plays <= 0)) continue;
            playedBefore.add(r.songId);
            if (r.day >= gapStart) playedInGap.add(r.songId);
        }
        Map<Long, long[]> candidates = new LinkedHashMap<>();
        for (Map.Entry<Long, long[]> e : perSong.entrySet()) {
            long id = e.getKey();
            if (e.getValue()[0] > 0 && playedBefore.contains(id) && !playedInGap.contains(id)) {
                candidates.put(id, e.getValue());
            }
        }
        List<Long> ids = sortedIds(candidates, s -> s[0]);
        return ids.isEmpty() ? -1 : ids.get(0);
    }
}

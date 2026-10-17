package com.psthetech.swara.vibe;

import com.psthetech.swara.data.db.entity.ListenEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure logic: per-song / artist / album affinity in [0, 1] from listen events. */
public final class TasteProfile {

    private static final double NEUTRAL = 0.5;

    private final Map<Long, Double> songScore = new HashMap<>();
    private final Map<Long, int[]> hourCounts = new HashMap<>(); // 24 buckets per song
    private final Map<Long, Integer> playTotals = new HashMap<>();
    private final Map<String, Double> artistScore = new HashMap<>();
    private final Map<Long, Double> albumScore = new HashMap<>();
    private final Map<Long, String> artistKeyBySong;
    private final Map<Long, Long> albumBySong;

    public TasteProfile(List<ListenEvent> events, Set<Long> favoriteIds,
                        Map<Long, String> artistKeyBySong, Map<Long, Long> albumBySong) {
        this.artistKeyBySong = artistKeyBySong;
        this.albumBySong = albumBySong;

        // counts per song: finishes, repeats, skips
        Map<Long, int[]> counts = new HashMap<>();
        for (ListenEvent e : events) {
            int[] c = counts.get(e.songId);
            if (c == null) {
                c = new int[3];
                counts.put(e.songId, c);
            }
            switch (e.type) {
                case ListenEvent.TYPE_FINISH: c[0]++; break;
                case ListenEvent.TYPE_REPEAT: c[1]++; break;
                case ListenEvent.TYPE_SKIP: c[2]++; break;
                case ListenEvent.TYPE_PLAY: {
                    int[] h = hourCounts.get(e.songId);
                    if (h == null) {
                        h = new int[24];
                        hourCounts.put(e.songId, h);
                    }
                    int hr = ((e.hourOfDay % 24) + 24) % 24;
                    h[hr]++;
                    Integer t = playTotals.get(e.songId);
                    playTotals.put(e.songId, t == null ? 1 : t + 1);
                    break;
                }
                default: break;
            }
        }
        for (Map.Entry<Long, int[]> en : counts.entrySet()) {
            int[] c = en.getValue();
            boolean fav = favoriteIds.contains(en.getKey());
            songScore.put(en.getKey(), score(c[0], c[1], c[2], fav));
        }
        for (Long id : favoriteIds) {
            if (!songScore.containsKey(id)) songScore.put(id, score(0, 0, 0, true));
        }

        Map<String, double[]> artistAgg = new HashMap<>();
        for (Map.Entry<Long, String> en : artistKeyBySong.entrySet()) {
            accumulate(artistAgg, en.getValue(), song(en.getKey()));
        }
        for (Map.Entry<String, double[]> en : artistAgg.entrySet()) {
            artistScore.put(en.getKey(), en.getValue()[0] / en.getValue()[1]);
        }
        Map<Long, double[]> albumAgg = new HashMap<>();
        for (Map.Entry<Long, Long> en : albumBySong.entrySet()) {
            accumulate(albumAgg, en.getValue(), song(en.getKey()));
        }
        for (Map.Entry<Long, double[]> en : albumAgg.entrySet()) {
            albumScore.put(en.getKey(), en.getValue()[0] / en.getValue()[1]);
        }
    }

    private static <K> void accumulate(Map<K, double[]> agg, K key, double v) {
        double[] a = agg.get(key);
        if (a == null) {
            a = new double[2];
            agg.put(key, a);
        }
        a[0] += v;
        a[1] += 1;
    }

    private static double score(int finishes, int repeats, int skips, boolean favorite) {
        double x = 0.4 * finishes + 0.6 * repeats - 0.5 * skips + (favorite ? 1.0 : 0.0);
        return (Math.tanh(x) + 1.0) / 2.0;
    }

    public double song(long id) {
        Double s = songScore.get(id);
        return s == null ? NEUTRAL : s;
    }

    public double artist(String key) {
        Double s = key == null ? null : artistScore.get(key);
        return s == null ? NEUTRAL : s;
    }

    public double album(long albumId) {
        Double s = albumScore.get(albumId);
        return s == null ? NEUTRAL : s;
    }

    /** Share of this song's PLAY events within +-1 h (circular) of hourOfDay; 0 if no plays. */
    public double hour(long id, int hourOfDay) {
        int[] h = hourCounts.get(id);
        Integer total = playTotals.get(id);
        if (h == null || total == null || total == 0) return 0.0;
        int sum = 0;
        for (int d = -1; d <= 1; d++) {
            sum += h[(((hourOfDay + d) % 24) + 24) % 24];
        }
        return (double) sum / total;
    }

    public double combined(long id) {
        String ak = artistKeyBySong.get(id);
        Long al = albumBySong.get(id);
        return 0.6 * song(id)
                + 0.25 * (ak == null ? NEUTRAL : artist(ak))
                + 0.15 * (al == null ? NEUTRAL : album(al));
    }
}

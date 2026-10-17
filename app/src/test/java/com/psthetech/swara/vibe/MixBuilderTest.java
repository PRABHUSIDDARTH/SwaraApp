package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.data.db.entity.ListenEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class MixBuilderTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private static Candidate cand(long id, double valence, long lastPlayed) {
        return new Candidate(id, "a" + id, new double[]{valence, 0.5}, 0, lastPlayed, 0.5);
    }

    private static TasteProfile profile(List<ListenEvent> events, long... ids) {
        Map<Long, String> artists = new HashMap<>();
        Map<Long, Long> albums = new HashMap<>();
        for (long id : ids) {
            artists.put(id, "a" + id);
            albums.put(id, id);
        }
        return new TasteProfile(events, new HashSet<>(), artists, albums);
    }

    private static ListenEvent ev(long song, int type, int hour) {
        ListenEvent e = new ListenEvent();
        e.songId = song;
        e.type = type;
        e.hourOfDay = hour;
        return e;
    }

    @Test
    public void hourBuckets() {
        assertEquals(3, MixBuilder.hourBucket(4));
        assertEquals(0, MixBuilder.hourBucket(5));
        assertEquals(0, MixBuilder.hourBucket(11));
        assertEquals(1, MixBuilder.hourBucket(12));
        assertEquals(1, MixBuilder.hourBucket(16));
        assertEquals(2, MixBuilder.hourBucket(17));
        assertEquals(2, MixBuilder.hourBucket(21));
        assertEquals(3, MixBuilder.hourBucket(22));
        assertEquals(3, MixBuilder.hourBucket(0));
    }

    @Test
    public void feelGood_filtersByValenceAndTaste() {
        List<ListenEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) events.add(ev(1, ListenEvent.TYPE_FINISH, 9));
        events.add(ev(2, ListenEvent.TYPE_SKIP, 9));
        events.add(ev(2, ListenEvent.TYPE_SKIP, 9));
        events.add(ev(4, ListenEvent.TYPE_FINISH, 9));
        TasteProfile t = profile(events, 1, 2, 3, 4);
        List<Candidate> pool = Arrays.asList(
                cand(1, 0.9, 0),   // happy, liked
                cand(2, 0.9, 0),   // happy, disliked
                cand(3, 0.2, 0),   // sad, neutral
                cand(4, 0.65, 0)); // boundary valence, mildly liked
        List<Long> out = MixBuilder.feelGood(pool, t, 25);
        assertEquals(Arrays.asList(1L, 4L), out);
    }

    @Test
    public void feelGood_noHistory_stillBuildsRankedByValence() {
        TasteProfile t = profile(Collections.emptyList(), 1, 2, 3);
        List<Candidate> pool = Arrays.asList(
                cand(1, 0.7, 0), cand(2, 0.95, 0), cand(3, 0.3, 0));
        assertEquals(Arrays.asList(2L, 1L), MixBuilder.feelGood(pool, t, 25));
    }

    @Test
    public void rediscover_requires30DaysAndHighAffinity() {
        long now = 100 * DAY;
        List<ListenEvent> events = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            events.add(ev(1, ListenEvent.TYPE_FINISH, 9));
            events.add(ev(2, ListenEvent.TYPE_FINISH, 9));
            events.add(ev(3, ListenEvent.TYPE_FINISH, 9));
        }
        TasteProfile t = profile(events, 1, 2, 3, 4);
        List<Candidate> pool = Arrays.asList(
                cand(1, 0.5, now - 31 * DAY),  // old + loved
                cand(2, 0.5, now - 5 * DAY),   // recent
                cand(3, 0.5, now - 30 * DAY),  // exactly 30 days
                cand(4, 0.5, now - 90 * DAY)); // old but neutral affinity
        List<Long> out = MixBuilder.rediscover(pool, t, now, 25);
        assertEquals(Arrays.asList(1L, 3L), out);
    }

    @Test
    public void timeOfDay_ordersByHourAffinity() {
        List<ListenEvent> events = new ArrayList<>();
        // song 1: 1 of 2 plays near 8h; song 2: all plays near 8h; song 3: none near 8h
        events.add(ev(1, ListenEvent.TYPE_PLAY, 8));
        events.add(ev(1, ListenEvent.TYPE_PLAY, 20));
        events.add(ev(2, ListenEvent.TYPE_PLAY, 8));
        events.add(ev(2, ListenEvent.TYPE_PLAY, 9));
        events.add(ev(3, ListenEvent.TYPE_PLAY, 20));
        TasteProfile t = profile(events, 1, 2, 3, 4);
        List<Candidate> pool = Arrays.asList(cand(1, 0.5, 0), cand(2, 0.5, 0),
                cand(3, 0.5, 0), cand(4, 0.5, 0));
        assertEquals(Arrays.asList(2L, 1L), MixBuilder.timeOfDay(pool, t, 8, 25));
    }

    @Test
    public void mixesCappedAtSize() {
        TasteProfile t = profile(Collections.emptyList());
        List<Candidate> pool = new ArrayList<>();
        for (long i = 1; i <= 40; i++) pool.add(cand(i, 0.9, 0));
        // give taste via events
        List<ListenEvent> events = new ArrayList<>();
        long[] ids = new long[40];
        for (int i = 0; i < 40; i++) {
            ids[i] = i + 1;
            events.add(ev(i + 1, ListenEvent.TYPE_FINISH, 9));
            events.add(ev(i + 1, ListenEvent.TYPE_PLAY, 9));
        }
        t = profile(events, ids);
        List<Long> fg = MixBuilder.feelGood(pool, t, 25);
        assertEquals(25, fg.size());
        assertEquals(Long.valueOf(1), fg.get(0)); // ties -> id ascending
        assertEquals(10, MixBuilder.timeOfDay(pool, t, 9, 10).size());
        assertEquals(5, MixBuilder.rediscover(pool, t, 100 * DAY, 5).size());
    }

    @Test
    public void visibleMixKeys_onlyCurrentBucket() {
        assertEquals(true, MixBuilder.isVisibleAtHour("feel_good", 3));
        assertEquals(true, MixBuilder.isVisibleAtHour("rediscover", 14));
        assertEquals(true, MixBuilder.isVisibleAtHour("time_of_day_0", 8));
        assertEquals(false, MixBuilder.isVisibleAtHour("time_of_day_3", 8));
        assertEquals(true, MixBuilder.isVisibleAtHour("time_of_day_1", 12));
        assertEquals(true, MixBuilder.isVisibleAtHour("time_of_day_2", 21));
        assertEquals(true, MixBuilder.isVisibleAtHour("time_of_day_3", 23));
        assertEquals(true, MixBuilder.isVisibleAtHour("time_of_day_3", 4));
        assertEquals(false, MixBuilder.isVisibleAtHour("time_of_day_0", 4));
        // legacy single time-of-day row is never shown
        assertEquals(false, MixBuilder.isVisibleAtHour("time_of_day", 8));
        assertEquals(false, MixBuilder.isVisibleAtHour(null, 8));
        assertEquals("time_of_day_2", MixBuilder.timeOfDayKey(2));
    }

    @Test
    public void bucketHours_coverDayOnce() {
        int total = 0;
        for (int b = 0; b < MixBuilder.BUCKET_COUNT; b++) total += MixBuilder.bucketHours(b).size();
        assertEquals(24, total);
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 22, 23), MixBuilder.bucketHours(3));
    }

    @Test
    public void timeOfDayBucket_usesWholeBucket() {
        List<ListenEvent> events = new ArrayList<>();
        events.add(ev(1, ListenEvent.TYPE_PLAY, 23)); // late night
        events.add(ev(2, ListenEvent.TYPE_PLAY, 9));  // morning
        events.add(ev(3, ListenEvent.TYPE_PLAY, 3));  // late night
        events.add(ev(3, ListenEvent.TYPE_PLAY, 3));
        TasteProfile t = profile(events, 1, 2, 3);
        List<Candidate> pool = Arrays.asList(cand(1, 0.5, 0), cand(2, 0.5, 0), cand(3, 0.5, 0));
        assertEquals(Arrays.asList(1L, 3L), MixBuilder.timeOfDayBucket(pool, t, 3, 25));
        assertEquals(Arrays.asList(2L), MixBuilder.timeOfDayBucket(pool, t, 0, 25));
        assertEquals(Collections.emptyList(), MixBuilder.timeOfDayBucket(pool, t, 1, 25));
    }
}

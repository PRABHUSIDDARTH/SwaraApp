package com.psthetech.swara.vibe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import com.psthetech.swara.data.db.dao.MoodDao;
import com.psthetech.swara.data.db.entity.SongMood;
import com.psthetech.swara.vibe.android.MoodRepository;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MoodRepositoryTest {

    static class FakeDao implements MoodDao {
        final Map<Long, SongMood> rows = new LinkedHashMap<>();
        int batchCalls, singleCalls;
        long[] lastIds;

        @Override public void upsert(SongMood m) { rows.put(m.songId, m); }
        @Override public List<SongMood> getAll() { return new ArrayList<>(rows.values()); }
        @Override public SongMood get(long id) { return rows.get(id); }
        @Override public void deleteForSong(long id) { rows.remove(id); }
        @Override public void deleteForSongs(List<Long> ids) { for (Long id : ids) rows.remove(id); }
        @Override public void updateNormalized(long id, double v, double e) { singleCalls++; }
        @Override public void updateNormalizedBatch(long[] ids, double[] v, double[] e) {
            batchCalls++;
            lastIds = ids;
            for (int i = 0; i < ids.length; i++) {
                rows.get(ids[i]).valence = v[i];
                rows.get(ids[i]).energy = e[i];
            }
        }
    }

    static class FakeScorer implements MoodScorer {
        final AtomicInteger calls = new AtomicInteger();
        @Override public int version() { return 2; }
        @Override public MoodResult score(Uri u) {
            calls.incrementAndGet();
            return new MoodResult(true, 0.3, 0.4, 100, RawMoodModel.pack(0.3, 0.4, 100), 2);
        }
    }

    static MoodRepository repo(FakeDao dao, MoodScorer s) {
        return new MoodRepository(dao, s) {
            @Override protected Uri uriFor(long id) { return null; }
        };
    }

    static SongMood ok(long id, double rv, double re, int ver) {
        SongMood m = new SongMood();
        m.songId = id; m.valence = rv; m.energy = re; m.scorerVersion = ver;
        m.status = SongMood.STATUS_OK;
        m.rawFeatures = RawMoodModel.pack(rv, re, 100);
        return m;
    }

    @Test
    public void renormalizeWritesOkRowsInOneBatchAndSkipsFailed() {
        FakeDao dao = new FakeDao();
        dao.upsert(ok(1, 0.1, 0.9, 2));
        dao.upsert(ok(2, 0.5, 0.5, 2));
        dao.upsert(SongMood.failed(3, 2, 0));
        dao.upsert(ok(4, 0.9, 0.1, 2));
        repo(dao, new FakeScorer()).renormalize();
        assertEquals(1, dao.batchCalls);
        assertEquals(0, dao.singleCalls);
        assertArrayEquals(new long[]{1, 2, 4}, dao.lastIds);
        assertEquals(0.0, dao.rows.get(1L).valence, 1e-9);
        assertEquals(1.0, dao.rows.get(4L).valence, 1e-9);
        assertEquals(0.5, dao.rows.get(3L).valence, 1e-9);
    }

    @Test
    public void analyzeBatchStopsOnceStopCheckIsTrue() {
        FakeDao dao = new FakeDao();
        FakeScorer scorer = new FakeScorer();
        AtomicInteger checks = new AtomicInteger();
        repo(dao, scorer).analyzeBatch(Arrays.asList(1L, 2L, 3L, 4L),
                () -> checks.incrementAndGet() > 2);
        assertEquals(2, scorer.calls.get());
        assertEquals(2, dao.rows.size());
        assertNull(dao.get(3));
    }

    @Test
    public void idsNeedingAnalysisUsesScorerVersion() {
        FakeDao dao = new FakeDao();
        dao.upsert(ok(1, 0.1, 0.1, 2));
        dao.upsert(SongMood.failed(2, 2, 0));
        dao.upsert(ok(3, 0.1, 0.1, 1));
        dao.upsert(SongMood.failed(4, 1, 0));
        List<Long> need = repo(dao, new FakeScorer())
                .idsNeedingAnalysis(Arrays.asList(1L, 2L, 3L, 4L, 5L));
        assertEquals(Arrays.asList(3L, 4L, 5L), need);
        assertTrue(!need.contains(1L) && !need.contains(2L));
    }
}

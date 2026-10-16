package com.psthetech.swara.vibe.android;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.MoodDao;
import com.psthetech.swara.data.db.entity.SongMood;
import com.psthetech.swara.vibe.MoodResult;
import com.psthetech.swara.vibe.MoodScorer;
import com.psthetech.swara.vibe.PercentileNormalizer;
import com.psthetech.swara.vibe.RawMoodModel;

import java.util.ArrayList;
import java.util.List;

/** Stores per-song mood analysis and keeps library-relative percentiles up to date. */
public class MoodRepository {
    private final MoodDao dao;
    private final MoodScorer scorer;

    public MoodRepository(Context context) {
        Context app = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(app).moodDao();
        this.scorer = new AudioFeatureScorer(app);
    }

    /** Test seam. */
    public MoodRepository(MoodDao dao, MoodScorer scorer) {
        this.dao = dao;
        this.scorer = scorer;
    }

    public int scorerVersion() {
        return scorer.version();
    }

    /** Ids from {@code libraryIds} with no stored row at the current scorer version. */
    public List<Long> idsNeedingAnalysis(List<Long> libraryIds) {
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (SongMood m : dao.getAll()) {
            if (m.scorerVersion >= scorer.version()) done.add(m.songId);
        }
        List<Long> out = new ArrayList<>();
        for (Long id : libraryIds) if (!done.contains(id)) out.add(id);
        return out;
    }

    /** Analyzes and stores each song; does not renormalize. Stops early if the thread is interrupted. */
    public void analyzeBatch(List<Long> songIds) {
        for (long id : songIds) {
            if (Thread.currentThread().isInterrupted()) return;
            analyzeAndStore(id);
        }
    }

    private SongMood analyzeAndStore(long songId) {
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId);
        MoodResult r = scorer.score(uri);
        long now = System.currentTimeMillis();
        SongMood m;
        if (r.ok && RawMoodModel.unpack(r.raw) != null) {
            m = new SongMood();
            m.songId = songId;
            // Placeholders until renormalize() writes percentiles.
            m.valence = r.valence;
            m.energy = r.energy;
            m.tempoBpm = r.tempoBpm;
            m.rawFeatures = r.raw;
            m.scorerVersion = scorer.version();
            m.status = SongMood.STATUS_OK;
            m.analyzedAt = now;
        } else {
            m = SongMood.failed(songId, scorer.version(), now);
        }
        dao.upsert(m);
        return m;
    }

    /** Returns the current-version row, analyzing on demand. Never call on the main thread. */
    @Nullable
    public SongMood getOrAnalyze(long songId) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("getOrAnalyze must not run on the main thread");
        }
        SongMood m = dao.get(songId);
        if (m != null && m.scorerVersion >= scorer.version()) return m;
        analyzeAndStore(songId);
        renormalize();
        return dao.get(songId);
    }

    /** Recomputes percentile valence/energy over all OK rows. FAILED rows are untouched. */
    public void renormalize() {
        List<SongMood> ok = new ArrayList<>();
        List<double[]> raws = new ArrayList<>();
        for (SongMood m : dao.getAll()) {
            if (m.status != SongMood.STATUS_OK) continue;
            double[] raw = RawMoodModel.unpack(m.rawFeatures);
            if (raw == null) continue;
            ok.add(m);
            raws.add(raw);
        }
        int n = ok.size();
        if (n == 0) return;
        double[] rv = new double[n];
        double[] re = new double[n];
        for (int i = 0; i < n; i++) {
            rv[i] = raws.get(i)[0];
            re[i] = raws.get(i)[1];
        }
        double[] pv = PercentileNormalizer.normalize(rv);
        double[] pe = PercentileNormalizer.normalize(re);
        for (int i = 0; i < n; i++) dao.updateNormalized(ok.get(i).songId, pv[i], pe[i]);
    }
}

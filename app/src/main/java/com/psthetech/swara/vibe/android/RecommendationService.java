package com.psthetech.swara.vibe.android;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.FavoriteSong;
import com.psthetech.swara.data.db.entity.ListenEvent;
import com.psthetech.swara.data.db.entity.SongMood;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistIdentityHelper;
import com.psthetech.swara.vibe.Candidate;
import com.psthetech.swara.vibe.Recommender;
import com.psthetech.swara.vibe.TasteProfile;
import com.psthetech.swara.vibe.VibeQueuePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds the candidate pool from the library, stored moods, listen events and favorites, and
 * asks {@link Recommender} for picks. Every method is blocking and must run on an I/O thread.
 */
public final class RecommendationService {

    private static final String TAG = "RecommendationService";
    private static final double[] NEUTRAL = {0.5, 0.5};

    private final Context context;
    private final MoodRepository moodRepository;
    private final Recommender recommender = new Recommender(new Random());
    private final java.util.concurrent.atomic.AtomicBoolean backgroundBusy =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** One refill's view of the library. */
    public static final class Pool {
        final Map<Long, Song> songs = new HashMap<>();
        final Map<Long, Candidate> byId = new HashMap<>();
        final List<Candidate> candidates = new ArrayList<>();
        /** Eligible unanalyzed songs to analyze after this refill lands (may be empty). */
        public final List<Long> backgroundAnalysisIds = new ArrayList<>();
        /** Taste profile the candidates were scored with. */
        public TasteProfile taste;
        /** Candidates, in library order. */
        public List<Candidate> candidates() {
            return candidates;
        }
    }

    public RecommendationService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.moodRepository = new MoodRepository(this.context);
    }

    @NonNull
    public static String artistKey(@NonNull Song song) {
        return ArtistIdentityHelper.getCanonicalKey(song.getArtist());
    }

    /** Mood {valence, energy} of a song, analyzing on demand; neutral (0.5, 0.5) on failure. */
    @NonNull
    public double[] moodOf(long songId) {
        try {
            SongMood m = moodRepository.getOrAnalyze(songId);
            if (m != null && m.status == SongMood.STATUS_OK) return new double[]{m.valence, m.energy};
        } catch (RuntimeException e) {
            Log.w(TAG, "Mood analysis failed for " + songId, e);
        }
        return NEUTRAL.clone();
    }

    /**
     * Builds the pool from stored moods (unanalyzed songs score a neutral mood fit). Songs come
     * from the library cache, or a MediaStore query when the cache is cold (service restarted
     * without the UI). Only when fewer than 15 analyzed eligible songs exist are up to 5
     * eligible unanalyzed songs (top taste, not excluded, not played within 2 h) analyzed
     * synchronously; otherwise those ids are returned in {@link Pool#backgroundAnalysisIds}
     * for {@link #analyzeInBackground}.
     */
    @NonNull
    public Pool loadPool(@Nullable Set<Long> excluded) {
        return loadPool(excluded, true);
    }

    /** As {@link #loadPool(Set)}; {@code allowOnDemand=false} never analyzes synchronously. */
    @NonNull
    public Pool loadPool(@Nullable Set<Long> excluded, boolean allowOnDemand) {
        long now = System.currentTimeMillis();
        List<Song> library = MusicRepository.getCachedLibrarySongs();
        if (library.isEmpty()) {
            try {
                library = new MusicRepository(context).loadAllSongsBlocking();
            } catch (SecurityException e) {
                Log.w(TAG, "No media permission; empty pool");
                library = Collections.emptyList();
            }
        }
        AppDatabase db = AppDatabase.getInstance(context);
        List<ListenEvent> events = db.listenEventDao().all();
        Set<Long> favorites = new HashSet<>();
        for (FavoriteSong f : db.favoriteDao().getAllFavorites()) favorites.add(f.id);

        Pool pool = new Pool();
        Map<Long, String> artistBySong = new HashMap<>();
        Map<Long, Long> albumBySong = new HashMap<>();
        for (Song s : library) {
            pool.songs.put(s.getId(), s);
            artistBySong.put(s.getId(), artistKey(s));
            albumBySong.put(s.getId(), s.getAlbumId());
        }
        Map<Long, Long> lastPlayed = new HashMap<>();
        for (ListenEvent e : events) {
            Long prev = lastPlayed.get(e.songId);
            if (prev == null || e.timestamp > prev) lastPlayed.put(e.songId, e.timestamp);
        }
        TasteProfile taste = new TasteProfile(events, favorites, artistBySong, albumBySong);
        pool.taste = taste;

        Map<Long, SongMood> moods = moodsById(db);
        int version = moodRepository.scorerVersion();
        int analyzedEligible = 0;
        List<Long> unanalyzedEligible = new ArrayList<>();
        for (Song s : library) {
            long id = s.getId();
            Long lp = lastPlayed.get(id);
            if (!VibeQueuePolicy.eligible(id, lp == null ? 0L : lp, excluded, now)) continue;
            SongMood m = moods.get(id);
            if (m == null || m.scorerVersion < version) unanalyzedEligible.add(id);
            else if (m.status == SongMood.STATUS_OK) analyzedEligible++;
        }
        Collections.sort(unanalyzedEligible,
                (a, b) -> Double.compare(taste.combined(b), taste.combined(a)));
        List<Long> top = new ArrayList<>(unanalyzedEligible.subList(0,
                Math.min(VibeQueuePolicy.MAX_ANALYSES_PER_REFILL, unanalyzedEligible.size())));
        if (allowOnDemand && !top.isEmpty() && VibeQueuePolicy.analyzeOnDemand(analyzedEligible)) {
            for (long id : top) {
                try {
                    moodRepository.getOrAnalyze(id);
                } catch (RuntimeException e) {
                    Log.w(TAG, "On-demand analysis failed", e);
                }
            }
            // Analysis renormalizes every OK row, so reload all moods once.
            moods = moodsById(db);
        } else {
            pool.backgroundAnalysisIds.addAll(top);
        }

        for (Song s : library) {
            long id = s.getId();
            SongMood m = moods.get(id);
            boolean ok = m != null && m.status == SongMood.STATUS_OK;
            Long lp = lastPlayed.get(id);
            Candidate c = new Candidate(id, artistBySong.get(id),
                    ok ? new double[]{m.valence, m.energy} : null,
                    ok ? m.tempoBpm : 0,
                    lp == null ? 0L : lp,
                    taste.combined(id));
            pool.candidates.add(c);
            pool.byId.put(id, c);
        }
        return pool;
    }

    /**
     * Analyzes {@code ids} and renormalizes once. Skipped if another background batch is
     * running. Blocking; I/O thread only.
     */
    public void analyzeInBackground(@NonNull List<Long> ids,
                                    @NonNull java.util.function.BooleanSupplier stop) {
        if (ids.isEmpty() || !backgroundBusy.compareAndSet(false, true)) return;
        try {
            moodRepository.analyzeBatch(ids, stop);
            moodRepository.renormalize();
        } catch (RuntimeException e) {
            Log.w(TAG, "Background analysis failed", e);
        } finally {
            backgroundBusy.set(false);
        }
    }

    /** Stored mood of a pool song, or neutral when unanalyzed. */
    @NonNull
    public static double[] storedMood(@NonNull Pool pool, long songId) {
        Candidate c = pool.byId.get(songId);
        return c != null && c.mood != null ? c.mood.clone() : NEUTRAL.clone();
    }

    private static Map<Long, SongMood> moodsById(AppDatabase db) {
        Map<Long, SongMood> out = new HashMap<>();
        for (SongMood m : db.moodDao().getAll()) out.put(m.songId, m);
        return out;
    }

    /**
     * One pick per target, chained: each pick follows the previous one (transition, artist rule)
     * and is excluded from later slots. {@code anchor} is the song the first pick follows;
     * {@code recentArtistKeys} lists the queue's last artists, most recent last.
     */
    @NonNull
    public List<Song> pickSequence(@NonNull Pool pool, @Nullable Song anchor,
                                   @NonNull List<String> recentArtistKeys,
                                   @NonNull List<double[]> targets, @NonNull Set<Long> excluded) {
        List<Song> out = new ArrayList<>();
        List<String> recent = new ArrayList<>(recentArtistKeys);
        Set<Long> taken = new HashSet<>(excluded);
        Candidate cur = anchor == null ? null : candidateFor(pool, anchor);
        long now = System.currentTimeMillis();
        for (double[] target : targets) {
            List<Candidate> picked = recommender.pick(pool.candidates, cur, target,
                    recent, taken, now, 1);
            if (picked.isEmpty()) break;
            Candidate c = picked.get(0);
            Song s = pool.songs.get(c.songId);
            if (s == null) break;
            out.add(s);
            taken.add(c.songId);
            recent.add(c.artistKey);
            while (recent.size() > 2) recent.remove(0);
            cur = c;
        }
        return out;
    }

    /** Convenience: builds a pool and picks {@code count} songs toward one target. */
    @NonNull
    public List<Song> nextPicks(@NonNull Song current, @NonNull double[] target,
                                @NonNull Set<Long> excluded, int count) {
        Pool pool = loadPool(excluded);
        List<double[]> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) targets.add(target);
        List<String> recent = new ArrayList<>();
        recent.add(artistKey(current));
        return pickSequence(pool, current, recent, targets, excluded);
    }

    private static Candidate candidateFor(Pool pool, Song song) {
        Candidate c = pool.byId.get(song.getId());
        if (c != null) return c;
        return new Candidate(song.getId(), artistKey(song), null, 0, 0L, 0.5);
    }
}

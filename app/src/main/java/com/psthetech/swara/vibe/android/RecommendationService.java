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
    /** At most this many on-demand analyses per pool build. */
    private static final int MAX_ON_DEMAND_ANALYSES = 5;
    private static final double[] NEUTRAL = {0.5, 0.5};

    private final Context context;
    private final MoodRepository moodRepository;
    private final Recommender recommender = new Recommender(new Random());

    /** One refill's view of the library. */
    public static final class Pool {
        final Map<Long, Song> songs = new HashMap<>();
        final Map<Long, Candidate> byId = new HashMap<>();
        final List<Candidate> candidates = new ArrayList<>();
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
     * Builds the pool. Songs come from the library cache (or a MediaStore query when the cache is
     * cold, e.g. the service restarted without the UI). Up to 5 songs with no current-version
     * mood, ranked by taste and not in {@code excluded}, are analyzed on demand.
     */
    @NonNull
    public Pool loadPool(@Nullable Set<Long> excluded) {
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

        Map<Long, SongMood> moods = moodsById(db);
        // On-demand analysis of the top-taste songs that lack a current-version mood.
        int version = moodRepository.scorerVersion();
        List<Long> missing = new ArrayList<>();
        for (Song s : library) {
            if (excluded != null && excluded.contains(s.getId())) continue;
            SongMood m = moods.get(s.getId());
            if (m == null || m.scorerVersion < version) missing.add(s.getId());
        }
        if (!missing.isEmpty()) {
            Collections.sort(missing, (a, b) -> Double.compare(taste.combined(b), taste.combined(a)));
            int n = Math.min(MAX_ON_DEMAND_ANALYSES, missing.size());
            for (int i = 0; i < n; i++) {
                try {
                    moodRepository.getOrAnalyze(missing.get(i));
                } catch (RuntimeException e) {
                    Log.w(TAG, "On-demand analysis failed", e);
                }
            }
            // Analysis renormalizes every OK row, so reload all moods once.
            moods = moodsById(db);
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

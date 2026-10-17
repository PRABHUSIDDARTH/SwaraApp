package com.psthetech.swara.vibe.android;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.vibe.LibraryPrune;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Analyzes the mood of every library song that has no current-version result. */
public class MoodAnalysisWorker extends Worker {
    public static final String UNIQUE_NAME = "mood_analysis";
    /** Separate name so the capped run can queue the constrained remainder under UNIQUE_NAME. */
    public static final String UNIQUE_NAME_NOW = "mood_analysis_now";
    /** Input key: analyze at most this many songs (0 or absent = no cap). */
    public static final String KEY_CAP = "cap";
    /** Songs analyzed right after the permission grant; the rest wait for charging + idle. */
    public static final int NOW_CAP = 75;
    private static final int BATCH = 25;

    public MoodAnalysisWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Runs when the device is charging and idle; keeps an already queued request. */
    public static void enqueue(Context ctx) {
        Constraints.Builder c = new Constraints.Builder().setRequiresCharging(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) c.setRequiresDeviceIdle(true);
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MoodAnalysisWorker.class)
                .setConstraints(c.build()).build();
        WorkManager.getInstance(ctx.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, req);
    }

    /**
     * Runs soon (battery not low), analyzing at most {@link #NOW_CAP} songs; the remainder is
     * queued through {@link #enqueue(Context)}.
     */
    public static void enqueueNow(Context ctx) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MoodAnalysisWorker.class)
                .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .setInputData(new Data.Builder().putInt(KEY_CAP, NOW_CAP).build())
                .build();
        WorkManager.getInstance(ctx.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_NAME_NOW, ExistingWorkPolicy.REPLACE, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            List<Song> songs = new MusicRepository(ctx).loadAllSongsBlocking();
            List<Long> ids = new ArrayList<>();
            for (Song s : songs) ids.add(s.getId());
            boolean pruned = pruneDeletedSongs(ids);
            MoodRepository repo = new MoodRepository(ctx);
            List<Long> todo = repo.idsNeedingAnalysis(ids);
            int cap = getInputData().getInt(KEY_CAP, 0);
            boolean capped = cap > 0 && todo.size() > cap;
            if (capped) {
                todo = new ArrayList<>(todo.subList(0, cap));
                // Queue the remainder (charging + idle) up front so a stop can't lose it.
                enqueue(ctx);
            }
            boolean analyzedAny = !todo.isEmpty();
            if (pruned && todo.isEmpty()) repo.renormalize();
            for (int i = 0; i < todo.size(); i += BATCH) {
                if (isStopped()) return Result.success();
                repo.analyzeBatch(todo.subList(i, Math.min(i + BATCH, todo.size())),
                        this::isStopped);
                if (isStopped()) return Result.success();
                repo.renormalize();
            }
            if (analyzedAny || pruned) MixRefreshWorker.refreshNow(ctx);
            return Result.success();
        } catch (SecurityException e) {
            return Result.success();
        }
    }

    /**
     * Deletes mood rows and listen events of songs no longer in the library (spec section 5), so
     * they stop skewing percentiles and taste. Never prunes on an empty library result, which is
     * what a failed or permission-less query returns.
     */
    private boolean pruneDeletedSongs(List<Long> libraryIds) {
        if (libraryIds.isEmpty()) return false;
        Set<Long> library = new HashSet<>(libraryIds);
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        List<Long> storedMood = new ArrayList<>();
        for (com.psthetech.swara.data.db.entity.SongMood m : db.moodDao().getAll()) {
            storedMood.add(m.songId);
        }
        List<Long> staleMood = LibraryPrune.staleIds(storedMood, library);
        List<Long> staleEvents = LibraryPrune.staleIds(db.listenEventDao().distinctSongIds(), library);
        if (staleMood.isEmpty() && staleEvents.isEmpty()) return false;
        db.runInTransaction(() -> {
            for (List<Long> c : LibraryPrune.chunks(staleMood, LibraryPrune.CHUNK)) {
                db.moodDao().deleteForSongs(c);
            }
            for (List<Long> c : LibraryPrune.chunks(staleEvents, LibraryPrune.CHUNK)) {
                db.listenEventDao().deleteForSongs(c);
            }
        });
        android.util.Log.i("MoodAnalysisWorker", "Pruned " + staleMood.size() + " mood rows, "
                + staleEvents.size() + " songs' listen events");
        return true;
    }
}

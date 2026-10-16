package com.psthetech.swara.vibe.android;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.List;

/** Analyzes the mood of every library song that has no current-version result. */
public class MoodAnalysisWorker extends Worker {
    public static final String UNIQUE_NAME = "mood_analysis";
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

    /** Runs immediately, replacing any queued request. */
    public static void enqueueNow(Context ctx) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MoodAnalysisWorker.class).build();
        WorkManager.getInstance(ctx.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            List<Song> songs = new MusicRepository(ctx).loadAllSongsBlocking();
            List<Long> ids = new ArrayList<>();
            for (Song s : songs) ids.add(s.getId());
            MoodRepository repo = new MoodRepository(ctx);
            List<Long> todo = repo.idsNeedingAnalysis(ids);
            for (int i = 0; i < todo.size(); i += BATCH) {
                if (isStopped()) return Result.success();
                repo.analyzeBatch(todo.subList(i, Math.min(i + BATCH, todo.size())));
                repo.renormalize();
            }
            return Result.success();
        } catch (SecurityException e) {
            return Result.success();
        }
    }
}

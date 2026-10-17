package com.psthetech.swara.vibe.android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.Mix;
import com.psthetech.swara.vibe.MixBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Rebuilds the "For you" mixes from listening history and stored moods. */
public class MixRefreshWorker extends Worker {
    public static final String UNIQUE_NAME = "mix_refresh";
    public static final String UNIQUE_NAME_NOW = "mix_refresh_now";

    public MixRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Daily refresh; keeps an already scheduled request. */
    public static void enqueuePeriodic(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                MixRefreshWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(ctx.getApplicationContext())
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    /** Runs one refresh immediately. */
    public static void refreshNow(Context ctx) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MixRefreshWorker.class).build();
        WorkManager.getInstance(ctx.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_NAME_NOW, ExistingWorkPolicy.REPLACE, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            RecommendationService.Pool pool =
                    new RecommendationService(ctx).loadPool(null, false);
            if (pool.taste == null || pool.candidates().isEmpty()) {
                AppDatabase.getInstance(ctx).mixDao().deleteAll();
                return Result.success();
            }
            long now = System.currentTimeMillis();
            int size = MixBuilder.DEFAULT_SIZE;

            int[] titles = {R.string.mix_morning, R.string.mix_afternoon,
                    R.string.mix_evening, R.string.mix_late_night};
            List<Mix> mixes = new ArrayList<>();
            // All four buckets are built; Home shows only the one for the current hour.
            for (int b = 0; b < MixBuilder.BUCKET_COUNT; b++) {
                addIfBigEnough(mixes, MixBuilder.timeOfDayKey(b), ctx.getString(titles[b]),
                        MixBuilder.timeOfDayBucket(pool.candidates(), pool.taste, b, size), now);
            }
            addIfBigEnough(mixes, "feel_good", ctx.getString(R.string.mix_feel_good),
                    MixBuilder.feelGood(pool.candidates(), pool.taste, size), now);
            addIfBigEnough(mixes, "rediscover", ctx.getString(R.string.mix_rediscover),
                    MixBuilder.rediscover(pool.candidates(), pool.taste, now, size), now);

            AppDatabase db = AppDatabase.getInstance(ctx);
            db.runInTransaction(() -> {
                db.mixDao().deleteAll();
                for (Mix m : mixes) db.mixDao().upsert(m);
            });
            return Result.success();
        } catch (RuntimeException e) {
            android.util.Log.w("MixRefreshWorker", "Mix refresh failed", e);
            return Result.success();
        }
    }

    private static void addIfBigEnough(List<Mix> out, String key, String title,
                                       List<Long> ids, long now) {
        if (ids.size() < MixBuilder.MIN_MIX_SIZE) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        Mix m = new Mix();
        m.mixKey = key;
        m.title = title;
        m.songIds = sb.toString();
        m.builtAt = now;
        out.add(m);
    }
}

package com.psthetech.swara.wrapped;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.WrappedDao;
import com.psthetech.swara.data.db.entity.Recap;

import java.text.DateFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Daily: builds monthly and yearly recaps that are due, stores them frozen, and notifies. */
public class RecapWorker extends Worker {

    private static final String TAG = "RecapWorker";
    static final String UNIQUE = "wrapped_recap";

    public RecapWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(@NonNull Context context) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(RecapWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            runFor(getApplicationContext(), RecapRepository.today(), this::isStopped);
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "Recap build failed", e);
            return Result.retry();
        }
    }

    /** Builds whatever is due on {@code today}. Worker thread only. */
    public static void runFor(@NonNull Context ctx, int today, @NonNull java.util.function.BooleanSupplier stopped) {
        {
            WrappedDao dao = AppDatabase.getInstance(ctx).wrappedDao();
            RecapRepository repo = new RecapRepository(ctx);
            Map<String, Integer> existing = new HashMap<>();
            for (String key : dao.recapKeys()) {
                Recap r = dao.recap(key);
                if (r != null) existing.put(key, r.periodEnd);
            }
            for (RecapSchedulePolicy.Due due : RecapSchedulePolicy.recapsDue(today, existing,
                    p -> dao.totalPlayedMs(p.start, p.end))) {
                if (stopped.getAsBoolean()) break;
                WrappedStats stats = repo.statsFor(due.period);
                repo.store(due.period.key(), stats);
                if (due.notify) {
                    WrappedNotifier.notifyReady(ctx, due.period.key(),
                            ctx.getString(R.string.wrapped_banner_title, name(due.period)));
                }
            }
        }
    }

    static String name(Period p) {
        if (p.kind == Period.Kind.MONTH) {
            return new DateFormatSymbols(Locale.getDefault()).getMonths()[Day.month(p.start) - 1];
        }
        return String.valueOf(Day.year(p.start));
    }
}

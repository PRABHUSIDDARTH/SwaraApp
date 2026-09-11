package com.psthetech.swara;

import android.app.Application;

import com.psthetech.swara.data.db.AppDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application class for Swara V2.
 *
 * Responsibilities:
 * - Provide a shared background ExecutorService for all database and I/O operations
 * - Initialize the Room database singleton
 * - Serve as the dependency root (no injection framework needed at this scale)
 */
public class SwaraApplication extends Application {

    /** Single-thread executor dedicated to Room DB operations — serializes writes */
    private ExecutorService dbExecutor;

    /** Cached thread pool for MediaStore queries and I/O (artwork, etc.) */
    private ExecutorService ioExecutor;

    private static SwaraApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        dbExecutor = Executors.newSingleThreadExecutor();
        ioExecutor = Executors.newCachedThreadPool();

        // Eagerly initialize the Room DB so the first DB access is not blocked
        AppDatabase.getInstance(this);

        // Eagerly initialize theme preferences and night mode state
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().init(this);
    }

    public static SwaraApplication getInstance() {
        return instance;
    }

    /** For database operations. Uses a single thread to serialize writes. */
    public ExecutorService getDbExecutor() {
        return dbExecutor;
    }

    /** For I/O work (MediaStore, artwork). Uses multiple threads. */
    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }
}

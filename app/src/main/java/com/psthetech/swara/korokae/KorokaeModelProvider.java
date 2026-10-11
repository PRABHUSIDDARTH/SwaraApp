package com.psthetech.swara.korokae;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lazily loads the bundled Korokae model from assets on a background thread, once per process.
 */
public final class KorokaeModelProvider {

    private static final String TAG = "KorokaeModelProvider";

    public interface Listener {
        void onModelState(boolean available, @Nullable String error);
    }

    private final Context appContext;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "korokae-model-loader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean loadStarted = new AtomicBoolean(false);
    @Nullable private volatile KorokaeModel model;
    @Nullable private volatile String error;
    @Nullable private volatile Listener listener;

    public KorokaeModelProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Nullable
    public KorokaeModel getIfLoaded() {
        return model;
    }

    @Nullable
    public String getError() {
        return error;
    }

    public void loadAsync() {
        if (!loadStarted.compareAndSet(false, true)) return;
        loader.execute(() -> {
            try (InputStream in = appContext.getAssets().open(KorokaeModel.ASSET_PATH)) {
                model = KorokaeModel.load(new java.io.BufferedInputStream(in, 1 << 16));
                Log.i(TAG, "Korokae model loaded");
            } catch (IOException | RuntimeException e) {
                error = "Karaoke model unavailable";
                Log.e(TAG, "Failed to load Korokae model", e);
            }
            Listener l = listener;
            if (l != null) l.onModelState(model != null, error);
        });
    }

    public void shutdown() {
        loader.shutdownNow();
    }
}

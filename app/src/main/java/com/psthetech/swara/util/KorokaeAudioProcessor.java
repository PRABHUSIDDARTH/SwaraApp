package com.psthetech.swara.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.psthetech.swara.domain.model.Song;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modular on-device audio pipeline for Korokae Mode.
 * Thread execution, progress interface, and cancellation architecture.
 */
public final class KorokaeAudioProcessor {

    public interface ProgressCallback {
        void onProgress(int percent);
        void onSuccess(@NonNull File instrumentalStem);
        void onError(@NonNull String errorMessage);
    }

    private final long songId;
    private final long generationId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public KorokaeAudioProcessor(long songId, long generationId) {
        this.songId = songId;
        this.generationId = generationId;
    }

    public long getGenerationId() { return generationId; }
    public long getSongId() { return songId; }

    public void cancel() {
        isCancelled.set(true);
        executor.shutdownNow();
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    public static boolean isSeparationModelInstalled(@NonNull Context context) {
        return false;
    }

    public void release() {
        cancel();
    }
}

package com.psthetech.swara.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;

/**
 * Cache manager for temporary Korokae instrumental stems.
 * Foundation directory and key generation.
 */
public final class KorokaeCacheManager {

    private static final String CACHE_SUBDIR = "korokae_stems";

    private KorokaeCacheManager() { /* static utility */ }

    @NonNull
    public static File getCacheDirectory(@NonNull Context context) {
        File dir = new File(context.getCacheDir(), CACHE_SUBDIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public static String buildCacheKey(long songId, long sourceModifiedTime) {
        return "korokae_" + songId + "_" + sourceModifiedTime + ".wav";
    }

    @Nullable
    public static File getCachedStem(@NonNull Context context, long songId, long sourceModifiedTime) {
        File stemFile = new File(getCacheDirectory(context), buildCacheKey(songId, sourceModifiedTime));
        if (stemFile.exists() && stemFile.length() > 44) {
            return stemFile;
        }
        return null;
    }
}

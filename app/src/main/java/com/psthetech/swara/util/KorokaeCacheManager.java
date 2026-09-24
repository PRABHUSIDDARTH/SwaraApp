package com.psthetech.swara.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Cache manager for temporary Korokae instrumental stems.
 *
 * Enforces:
 *  - Stable cache key based on song ID + source modification timestamp
 *  - Cache lives strictly in app internal cache directory (never MediaStore / Room)
 *  - Bounded storage: max 5 stems, max 100 MB total, max 24h retention (LRU cleanup)
 */
public final class KorokaeCacheManager {

    private static final String CACHE_SUBDIR = "korokae_stems";
    private static final int MAX_STEM_FILES = 5;
    private static final long MAX_STEM_AGE_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final long MAX_TOTAL_SIZE_BYTES = 100 * 1024 * 1024L; // 100 MB

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
        File dir = getCacheDirectory(context);
        String filename = buildCacheKey(songId, sourceModifiedTime);
        File stemFile = new File(dir, filename);
        if (stemFile.exists() && stemFile.length() > 0) {
            // Touch last modified for LRU
            stemFile.setLastModified(System.currentTimeMillis());
            return stemFile;
        }
        return null;
    }

    @NonNull
    public static File createTargetStemFile(@NonNull Context context, long songId, long sourceModifiedTime) {
        enforceBoundedCache(context);
        File dir = getCacheDirectory(context);
        String filename = buildCacheKey(songId, sourceModifiedTime);
        return new File(dir, filename);
    }

    /**
     * Bounded cache enforcement: purges stems older than MAX_STEM_AGE_MS,
     * or prunes oldest stems if count > MAX_STEM_FILES or total size > MAX_TOTAL_SIZE_BYTES.
     */
    public static synchronized void enforceBoundedCache(@NonNull Context context) {
        File dir = getCacheDirectory(context);
        File[] files = dir.listFiles((d, name) -> name.startsWith("korokae_") && name.endsWith(".wav"));
        if (files == null || files.length == 0) return;

        long now = System.currentTimeMillis();

        // 1. Delete expired files
        for (File f : files) {
            if (now - f.lastModified() > MAX_STEM_AGE_MS) {
                f.delete();
            }
        }

        // Re-list after age cleanup
        files = dir.listFiles((d, name) -> name.startsWith("korokae_") && name.endsWith(".wav"));
        if (files == null || files.length <= MAX_STEM_FILES) return;

        // 2. Sort by last modified ascending (oldest first for LRU eviction)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        long totalSize = 0;
        for (File f : files) {
            totalSize += f.length();
        }

        int index = 0;
        while ((files.length - index > MAX_STEM_FILES || totalSize > MAX_TOTAL_SIZE_BYTES) && index < files.length) {
            File toDelete = files[index];
            totalSize -= toDelete.length();
            toDelete.delete();
            index++;
        }
    }

    /** Clear all temporary stems (e.g. for testing or memory pressure). */
    public static synchronized void clearAll(@NonNull Context context) {
        File dir = getCacheDirectory(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }
}

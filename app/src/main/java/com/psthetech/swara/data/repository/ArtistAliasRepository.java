package com.psthetech.swara.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.ArtistAliasDao;
import com.psthetech.swara.data.db.entity.ArtistAlias;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * Persists manual artist-alias overrides and hands out an up-to-date {@link ArtistAliasResolver}.
 * {@link #resolver()} never blocks: it returns the last built resolver and schedules a rebuild
 * on the background executor when the library cache version or the overrides changed.
 */
public class ArtistAliasRepository {

    private static volatile ArtistAliasRepository instance;

    public static ArtistAliasRepository get(Context context) {
        if (instance == null) {
            synchronized (ArtistAliasRepository.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext();
                    MutableLiveData<Long> live = new MutableLiveData<>(0L);
                    Executor exec = SwaraApplication.getInstance().getDbExecutor();
                    instance = new ArtistAliasRepository(
                            AppDatabase.getInstance(app).artistAliasDao(),
                            MusicRepository::getCachedLibrarySongs,
                            MusicRepository::getLibraryCacheVersion,
                            exec, live::postValue, live);
                }
            }
        }
        return instance;
    }

    private final ArtistAliasDao dao;
    private final Supplier<List<Song>> songs;
    private final IntSupplier libraryVersion;
    private final Executor executor;
    private final LongConsumer publish;
    private final LiveData<Long> versionLive;

    private final Object lock = new Object();
    private volatile ArtistAliasResolver current = ArtistAliasResolver.identity();
    private int builtLibraryVersion = Integer.MIN_VALUE;
    private boolean overridesDirty = true;
    private boolean rebuildScheduled = false;
    private long version = 0;

    /** Package-private for tests. */
    ArtistAliasRepository(ArtistAliasDao dao, Supplier<List<Song>> songs, IntSupplier libraryVersion,
                          Executor executor, LongConsumer publish, LiveData<Long> versionLive) {
        this.dao = dao;
        this.songs = songs;
        this.libraryVersion = libraryVersion;
        this.executor = executor;
        this.publish = publish;
        this.versionLive = versionLive;
    }

    /** Last built resolver; identity() before the first build. Never blocks. */
    @NonNull
    public ArtistAliasResolver resolver() {
        ArtistAliasResolver r = current;
        boolean schedule = false;
        synchronized (lock) {
            if (!rebuildScheduled
                    && (overridesDirty || libraryVersion.getAsInt() != builtLibraryVersion)) {
                rebuildScheduled = true;
                schedule = true;
            }
        }
        if (schedule) {
            executor.execute(this::rebuild);
        }
        return r;
    }

    /** Bumps after every rebuild. */
    public LiveData<Long> version() {
        return versionLive;
    }

    public void merge(String fromKey, String toKey) {
        if (fromKey == null || toKey == null || fromKey.equals(toKey)) return;
        executor.execute(() -> {
            // Ignore a target that already resolves back to fromKey (would form a cycle).
            if (fromKey.equals(current.resolve(toKey))) return;
            dao.upsert(new ArtistAlias(fromKey, toKey, System.currentTimeMillis()));
            rebuild();
        });
    }

    public void unmerge(String fromKey) {
        if (fromKey == null) return;
        executor.execute(() -> {
            dao.upsert(new ArtistAlias(fromKey, null, System.currentTimeMillis()));
            rebuild();
        });
    }

    public void clearOverride(String fromKey) {
        if (fromKey == null) return;
        executor.execute(() -> {
            dao.delete(fromKey);
            rebuild();
        });
    }

    private void rebuild() {
        int libVer;
        synchronized (lock) {
            libVer = libraryVersion.getAsInt();
            overridesDirty = false;
        }
        Map<String, String> overrides = new HashMap<>();
        List<ArtistAlias> rows = dao.getAll();
        if (rows != null) {
            for (ArtistAlias a : rows) overrides.put(a.fromKey, a.toKey);
        }
        ArtistAliasResolver built = ArtistAliasResolver.build(songs.get(), overrides);
        long v;
        synchronized (lock) {
            current = built;
            builtLibraryVersion = libVer;
            rebuildScheduled = false;
            v = ++version;
        }
        publish.accept(v);
    }
}

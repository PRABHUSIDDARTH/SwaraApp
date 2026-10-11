package com.psthetech.swara.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Immutable state model representing Korokae Mode (on-demand vocal/instrumental separation).
 *
 * Supported states:
 *   - OFF: Normal original playback
 *   - PROCESSING: Separating vocal/instrumental tracks off main thread with progress
 *   - ACTIVE: Instrumental playback active with original metadata intact
 *   - FAILED: Processing failed or model unavailable on device
 */
public final class KorokaeState {

    public enum Status {
        OFF,
        PROCESSING,
        ACTIVE,
        FAILED
    }

    @NonNull private final Status status;
    @Nullable private final Song originalSong;
    private final long generationId;
    private final int progressPercent;
    @Nullable private final String instrumentalPath;
    @Nullable private final String message;

    private KorokaeState(@NonNull Status status,
                         @Nullable Song originalSong,
                         long generationId,
                         int progressPercent,
                         @Nullable String instrumentalPath,
                         @Nullable String message) {
        this.status = status;
        this.originalSong = originalSong;
        this.generationId = generationId;
        this.progressPercent = progressPercent;
        this.instrumentalPath = instrumentalPath;
        this.message = message;
    }

    public static KorokaeState off(@Nullable Song originalSong) {
        return new KorokaeState(Status.OFF, originalSong, 0L, 0, null, null);
    }

    public static KorokaeState processing(@Nullable Song originalSong, long generationId, int progressPercent) {
        return new KorokaeState(Status.PROCESSING, originalSong, generationId, Math.max(0, Math.min(100, progressPercent)), null, null);
    }

    public static KorokaeState active(@NonNull Song originalSong, long generationId, @NonNull String instrumentalPath) {
        return new KorokaeState(Status.ACTIVE, originalSong, generationId, 100, instrumentalPath, null);
    }

    public static KorokaeState failed(@Nullable Song originalSong, @Nullable String message) {
        return new KorokaeState(Status.FAILED, originalSong, 0L, 0, null, message);
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isProcessing() {
        return status == Status.PROCESSING;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    @Nullable
    public Song getOriginalSong() {
        return originalSong;
    }

    public long getGenerationId() {
        return generationId;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    @Nullable
    public String getInstrumentalPath() {
        return instrumentalPath;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KorokaeState)) return false;
        KorokaeState that = (KorokaeState) o;
        return generationId == that.generationId &&
                progressPercent == that.progressPercent &&
                status == that.status &&
                Objects.equals(originalSong, that.originalSong) &&
                Objects.equals(instrumentalPath, that.instrumentalPath) &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, originalSong, generationId, progressPercent, instrumentalPath, message);
    }

    @NonNull
    @Override
    public String toString() {
        return "KorokaeState{" +
                "status=" + status +
                ", song=" + (originalSong != null ? originalSong.getTitle() : "null") +
                ", gen=" + generationId +
                ", progress=" + progressPercent + "%" +
                ", msg='" + message + '\'' +
                '}';
    }
}

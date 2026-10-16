package com.psthetech.swara.service;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;

/**
 * Converts between {@link Song} and Media3 {@link MediaItem}. Shared by the UI controller
 * and the playback service so both sides encode/decode queue items identically.
 */
public final class MediaItemFactory {

    private static final String EXTRA_ALBUM_ID = "albumId";
    private static final String EXTRA_DURATION = "duration";
    private static final String EXTRA_TRACK = "trackNumber";
    private static final String EXTRA_YEAR = "year";
    private static final String EXTRA_DATE_ADDED = "dateAdded";

    private MediaItemFactory() {}

    @NonNull
    public static MediaItem toMediaItem(@NonNull Context context, @NonNull Song song) {
        if (song.getDuration() <= 0) {
            Song canonical = MusicRepository.getCanonicalSong(song.getId());
            if (canonical != null && canonical.getDuration() > 0) song = canonical;
        }
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_ALBUM_ID, song.getAlbumId());
        extras.putLong(EXTRA_DURATION, song.getDuration());
        extras.putInt(EXTRA_TRACK, song.getTrackNumber());
        extras.putInt(EXTRA_YEAR, song.getYear());
        extras.putLong(EXTRA_DATE_ADDED, song.getDateAdded());
        return new MediaItem.Builder()
                .setMediaId(String.valueOf(song.getId()))
                .setUri(ArtworkRepository.getSongUri(song.getId()))
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(song.getTitle())
                        .setArtist(song.getArtist())
                        .setAlbumTitle(song.getAlbum())
                        .setArtworkUri(sessionArtworkUri(context, song))
                        .setExtras(extras)
                        .build())
                .build();
    }

    /**
     * Artwork for the notification / lock screen. Media3 decodes this as an image, so it must
     * point at image data: the custom override or MediaStore album art, never the audio URI.
     */
    @Nullable
    static Uri sessionArtworkUri(@NonNull Context context, @NonNull Song song) {
        Uri custom = new ArtworkRepository(context).getCustomArtworkStore()
                .getCustomArtworkUri(song.getId());
        return custom != null ? custom : ArtworkRepository.getAlbumArtUri(song.getAlbumId());
    }

    /** Decodes a queue item, preferring the canonical MediaStore song when it is cached. */
    @Nullable
    public static Song toSong(@Nullable MediaItem item) {
        if (item == null) return null;
        long id;
        try {
            id = Long.parseLong(item.mediaId);
        } catch (NumberFormatException e) {
            return null;
        }
        Song canonical = MusicRepository.getCanonicalSong(id);
        if (canonical != null && canonical.getDuration() > 0) return canonical;

        MediaMetadata meta = item.mediaMetadata;
        Bundle extras = meta.extras != null ? meta.extras : Bundle.EMPTY;
        return new Song(
                id,
                meta.title != null ? meta.title.toString() : (canonical != null ? canonical.getTitle() : "Unknown"),
                meta.artist != null ? meta.artist.toString() : (canonical != null ? canonical.getArtist() : "Unknown"),
                meta.albumTitle != null ? meta.albumTitle.toString() : (canonical != null ? canonical.getAlbum() : "Unknown"),
                extras.getLong(EXTRA_ALBUM_ID, canonical != null ? canonical.getAlbumId() : 0L),
                extras.getLong(EXTRA_DURATION, canonical != null ? canonical.getDuration() : 0L),
                extras.getInt(EXTRA_TRACK, canonical != null ? canonical.getTrackNumber() : 0),
                extras.getInt(EXTRA_YEAR, canonical != null ? canonical.getYear() : 0),
                extras.getLong(EXTRA_DATE_ADDED, canonical != null ? canonical.getDateAdded() : 0L));
    }
}

package com.psthetech.swara.wrapped;

import androidx.annotation.Nullable;

/** Stored tags of a song (from song_snapshot), enough to show it after it was deleted. */
public final class SongInfo {
    public final long songId;
    @Nullable public final String title;
    @Nullable public final String artist;
    @Nullable public final String artistKey;
    @Nullable public final String album;
    public final long albumId;
    public final int firstHeardDay;

    public SongInfo(long songId, @Nullable String title, @Nullable String artist, @Nullable String artistKey,
                    @Nullable String album, long albumId, int firstHeardDay) {
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.artistKey = artistKey;
        this.album = album;
        this.albumId = albumId;
        this.firstHeardDay = firstHeardDay;
    }
}

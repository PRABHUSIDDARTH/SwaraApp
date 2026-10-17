package com.psthetech.swara.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;

/**
 * Builds the per-song overflow ("3 dots") menu so every song list in the app
 * (Songs tab, favorites, playlists, albums, artists, search) offers the same actions.
 */
public final class SongMenuHelper {

    private static final int ID_PLAY_NEXT = 1;
    private static final int ID_ADD_TO_QUEUE = 2;
    private static final int ID_ADD_TO_PLAYLIST = 3;
    private static final int ID_FAVORITE = 4;
    private static final int ID_REMOVE_FROM_PLAYLIST = 5;
    private static final int ID_EDIT_ARTWORK = 6;
    private static final int ID_DELETE = 7;
    private static final int ID_SHARE = 8;
    private static final int ID_EDIT_INFO = 9;
    private static final int ID_START_VIBE = 10;
    private static final int ID_SET_MOOD = 11;

    private SongMenuHelper() {}

    public static void show(@NonNull Context ctx, @NonNull View anchor, @NonNull Song song,
                            boolean isFavorite, boolean showRemoveFromPlaylist,
                            @NonNull SongAdapter.Listener listener) {
        PopupMenu popup = new PopupMenu(ctx, anchor);
        popup.getMenu().add(0, ID_PLAY_NEXT, 0, ctx.getString(R.string.play_next));
        popup.getMenu().add(0, ID_ADD_TO_QUEUE, 1, ctx.getString(R.string.add_to_queue));
        popup.getMenu().add(0, ID_START_VIBE, 2, ctx.getString(R.string.start_vibe_mode));
        popup.getMenu().add(0, ID_SET_MOOD, 2, ctx.getString(R.string.set_mood));
        popup.getMenu().add(0, ID_ADD_TO_PLAYLIST, 2, ctx.getString(R.string.add_to_playlist));
        popup.getMenu().add(0, ID_FAVORITE, 3, ctx.getString(isFavorite
                ? R.string.remove_from_favorites : R.string.add_to_favorites));
        if (showRemoveFromPlaylist) {
            popup.getMenu().add(0, ID_REMOVE_FROM_PLAYLIST, 4, ctx.getString(R.string.remove_from_playlist));
        }
        popup.getMenu().add(0, ID_EDIT_ARTWORK, 5, ctx.getString(R.string.edit_artwork));
        popup.getMenu().add(0, ID_EDIT_INFO, 6, ctx.getString(R.string.edit_song_info));
        popup.getMenu().add(0, ID_DELETE, 7, ctx.getString(R.string.delete_from_device));
        popup.getMenu().add(0, ID_SHARE, 8, ctx.getString(R.string.share));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case ID_PLAY_NEXT: listener.onPlayNext(song); return true;
                case ID_ADD_TO_QUEUE: listener.onAddToQueue(song); return true;
                case ID_START_VIBE: listener.onStartVibe(song); return true;
                case ID_SET_MOOD: listener.onSetMood(anchor.getContext(), song); return true;
                case ID_ADD_TO_PLAYLIST: listener.onAddToPlaylist(song); return true;
                case ID_FAVORITE: listener.onFavoriteToggle(song, isFavorite); return true;
                case ID_REMOVE_FROM_PLAYLIST: listener.onRemoveFromPlaylist(song); return true;
                case ID_EDIT_ARTWORK: listener.onEditArtwork(song); return true;
                case ID_EDIT_INFO: listener.onEditSongInfo(song); return true;
                case ID_DELETE: listener.onDeleteSong(song); return true;
                case ID_SHARE: shareAsText(ctx, song); return true;
            }
            return false;
        });
        popup.show();
    }

    private static void shareAsText(Context ctx, Song song) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, song.getTitle() + " — " + song.getArtist());
        ctx.startActivity(Intent.createChooser(shareIntent, ctx.getString(R.string.share)));
    }
}

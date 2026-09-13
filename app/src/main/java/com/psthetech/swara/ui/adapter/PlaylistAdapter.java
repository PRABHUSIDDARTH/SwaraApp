package com.psthetech.swara.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.repository.PlaylistArtworkStore;
import com.psthetech.swara.util.PlaylistArtworkHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the Playlists screen list.
 * Shows playlist artwork, name, song count, and a ⋮ menu button.
 *
 * Song counts are injected externally via setSongCounts() — they come from
 * PlaylistViewModel which queries Room in the background.
 *
 * Artwork is loaded via PlaylistArtworkHelper following the priority chain:
 *   custom → collage → fallback icon.
 */
public class PlaylistAdapter extends ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
        void onPlaylistMenuClick(View anchorView, Playlist playlist);
    }

    private final OnPlaylistClickListener listener;
    private PlaylistArtworkStore artworkStore;
    private Map<Long, Integer> songCounts = new HashMap<>();
    /** Map of playlistId → list of albumIds for songs in that playlist (for collage generation). */
    private Map<Long, List<Long>> playlistAlbumIds = new HashMap<>();

    public PlaylistAdapter(OnPlaylistClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /** Sets the PlaylistArtworkStore used for loading and generating artwork. */
    public void setArtworkStore(PlaylistArtworkStore store) {
        this.artworkStore = store;
    }

    /** Updates the song count map. */
    public void setSongCounts(Map<Long, Integer> counts) {
        this.songCounts = counts != null ? counts : new HashMap<>();
        notifyItemRangeChanged(0, getItemCount());
    }

    /** Updates the album IDs map for collage generation. */
    public void setPlaylistAlbumIds(Map<Long, List<Long>> albumIds) {
        this.playlistAlbumIds = albumIds != null ? albumIds : new HashMap<>();
        notifyItemRangeChanged(0, getItemCount());
    }

    private static final DiffUtil.ItemCallback<Playlist> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Playlist>() {
                @Override
                public boolean areItemsTheSame(@NonNull Playlist oldItem, @NonNull Playlist newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Playlist oldItem, @NonNull Playlist newItem) {
                    return oldItem.name.equals(newItem.name)
                            && oldItem.modifiedAt == newItem.modifiedAt
                            && safeEquals(oldItem.artworkPath, newItem.artworkPath);
                }

                private boolean safeEquals(String a, String b) {
                    if (a == null && b == null) return true;
                    if (a == null || b == null) return false;
                    return a.equals(b);
                }
            };

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = getItem(position);
        int count = songCounts.containsKey(playlist.id) ? songCounts.get(playlist.id) : 0;
        List<Long> albumIds = playlistAlbumIds.get(playlist.id);
        holder.bind(playlist, count, albumIds, artworkStore, listener);
    }

    @Override
    public void onViewRecycled(@NonNull PlaylistViewHolder holder) {
        super.onViewRecycled(holder);
        PlaylistArtworkHelper.clear(holder.itemView.getContext(), holder.ivPlaylistArtwork);
        holder.ivPlaylistArtwork.setImageDrawable(null);
        holder.itemView.setScaleX(1.0f);
        holder.itemView.setScaleY(1.0f);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivPlaylistArtwork;
        private final TextView playlistName;
        private final TextView playlistSongCount;
        private final ImageButton playlistMenuButton;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPlaylistArtwork = itemView.findViewById(R.id.ivPlaylistArtwork);
            playlistName      = itemView.findViewById(R.id.playlistName);
            playlistSongCount = itemView.findViewById(R.id.playlistSongCount);
            playlistMenuButton = itemView.findViewById(R.id.playlistMenuButton);
        }

        public void bind(Playlist playlist, int count, List<Long> albumIds,
                         PlaylistArtworkStore store, OnPlaylistClickListener listener) {
            playlistName.setText(playlist.name);

            if (playlistSongCount != null) {
                String countText = count == 1 ? "1 song" : count + " songs";
                playlistSongCount.setText(countText);
                playlistSongCount.setVisibility(View.VISIBLE);
            }

            // Load artwork via priority chain
            PlaylistArtworkHelper.loadPlaylistArt(
                    itemView.getContext(), playlist, store, albumIds, ivPlaylistArtwork);

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();

            if (tokens != null) {
                playlistName.setTextColor(tokens.getTextPrimaryColor());
                if (playlistSongCount != null) playlistSongCount.setTextColor(tokens.getTextSecondaryColor());
                if (playlistMenuButton != null) playlistMenuButton.setColorFilter(tokens.getIconSecondaryColor());
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                        .applyToView(itemView, false, tokens);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPlaylistClick(playlist);
            });

            if (playlistMenuButton != null) {
                playlistMenuButton.setOnClickListener(v -> {
                    if (listener != null) listener.onPlaylistMenuClick(v, playlist);
                });
            }
        }
    }
}

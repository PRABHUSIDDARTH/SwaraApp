package com.psthetech.swara.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter for the Playlists screen list.
 * Shows playlist name, song count, and a ⋮ menu button.
 *
 * Song counts are injected externally via setSongCounts() — they come from
 * PlaylistViewModel which queries Room in the background.
 */
public class PlaylistAdapter extends ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
        void onPlaylistMenuClick(View anchorView, Playlist playlist);
    }

    private final OnPlaylistClickListener listener;
    private Map<Long, Integer> songCounts = new HashMap<>();

    public PlaylistAdapter(OnPlaylistClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /** Updates the song count map. Call notifyDataSetChanged() is NOT needed —
     *  this triggers a DiffUtil-friendly re-bind via notifyItemRangeChanged(). */
    public void setSongCounts(Map<Long, Integer> counts) {
        this.songCounts = counts != null ? counts : new HashMap<>();
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
                            && oldItem.modifiedAt == newItem.modifiedAt;
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
        holder.bind(playlist, count, listener);
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final TextView playlistName;
        private final TextView playlistSongCount;
        private final ImageButton playlistMenuButton;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            playlistName      = itemView.findViewById(R.id.playlistName);
            playlistSongCount = itemView.findViewById(R.id.playlistSongCount);
            playlistMenuButton = itemView.findViewById(R.id.playlistMenuButton);
        }

        public void bind(Playlist playlist, int count, OnPlaylistClickListener listener) {
            playlistName.setText(playlist.name);

            if (playlistSongCount != null) {
                String countText = count == 1
                        ? "1 song"
                        : count + " songs";
                playlistSongCount.setText(countText);
                playlistSongCount.setVisibility(View.VISIBLE);
            }

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

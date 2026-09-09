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

public class PlaylistAdapter extends ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
        void onPlaylistMenuClick(View anchorView, Playlist playlist);
    }

    private final OnPlaylistClickListener listener;

    public PlaylistAdapter(OnPlaylistClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Playlist> DIFF_CALLBACK = new DiffUtil.ItemCallback<Playlist>() {
        @Override
        public boolean areItemsTheSame(@NonNull Playlist oldItem, @NonNull Playlist newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Playlist oldItem, @NonNull Playlist newItem) {
            return oldItem.name.equals(newItem.name);
        }
    };

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = getItem(position);
        holder.bind(playlist, listener);
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final TextView playlistName;
        private final TextView playlistSongCount;
        private final ImageButton playlistMenuButton;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            playlistName = itemView.findViewById(R.id.playlistName);
            playlistSongCount = itemView.findViewById(R.id.playlistSongCount);
            playlistMenuButton = itemView.findViewById(R.id.playlistMenuButton);
        }

        public void bind(Playlist playlist, OnPlaylistClickListener listener) {
            playlistName.setText(playlist.name);
            if (playlistSongCount != null) {
                playlistSongCount.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylistClick(playlist);
                }
            });

            if (playlistMenuButton != null) {
                playlistMenuButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onPlaylistMenuClick(v, playlist);
                    }
                });
            }
        }
    }
}

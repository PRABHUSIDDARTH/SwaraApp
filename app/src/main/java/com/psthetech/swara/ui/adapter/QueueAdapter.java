package com.psthetech.swara.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtworkHelper;

public class QueueAdapter extends ListAdapter<Song, QueueAdapter.QueueViewHolder> {

    public interface OnQueueItemClickListener {
        void onItemClick(int position, Song song);
    }

    private final OnQueueItemClickListener listener;
    private long currentPlayingSongId = -1;

    public QueueAdapter(OnQueueItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setCurrentPlayingSongId(long songId) {
        this.currentPlayingSongId = songId;
        notifyDataSetChanged();
    }

    private static final DiffUtil.ItemCallback<Song> DIFF_CALLBACK = new DiffUtil.ItemCallback<Song>() {
        @Override
        public boolean areItemsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            return oldItem.getTitle().equals(newItem.getTitle())
                    && oldItem.getArtist().equals(newItem.getArtist());
        }
    };

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue_song, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        Song song = getItem(position);
        boolean isPlaying = song.getId() == currentPlayingSongId;
        holder.bind(song, position, isPlaying, listener);
    }

    static class QueueViewHolder extends RecyclerView.ViewHolder {
        private final ImageView queuePlayingIndicator;
        private final ImageView queueSongArt;
        private final TextView queueSongTitle;
        private final TextView queueSongArtist;

        public QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            queuePlayingIndicator = itemView.findViewById(R.id.queuePlayingIndicator);
            queueSongArt = itemView.findViewById(R.id.queueSongArt);
            queueSongTitle = itemView.findViewById(R.id.queueSongTitle);
            queueSongArtist = itemView.findViewById(R.id.queueSongArtist);
        }

        public void bind(Song song, int position, boolean isPlaying, OnQueueItemClickListener listener) {
            queueSongTitle.setText(song.getTitle());
            queueSongArtist.setText(song.getArtist());

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();

            if (tokens != null) {
                queueSongTitle.setTextColor(isPlaying ? tokens.getAccentColor() : tokens.getTextPrimaryColor());
                queueSongArtist.setTextColor(tokens.getTextSecondaryColor());
                if (queuePlayingIndicator != null) queuePlayingIndicator.setColorFilter(tokens.getAccentColor());
            }

            queuePlayingIndicator.setVisibility(isPlaying ? View.VISIBLE : View.GONE);

            ArtworkHelper.loadSongArt(itemView.getContext(), song, queueSongArt);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(position, song);
                }
            });
        }
    }

    @Override
    public void onViewRecycled(@NonNull QueueViewHolder holder) {
        super.onViewRecycled(holder);
        ArtworkHelper.clear(holder.itemView.getContext(), holder.queueSongArt);
    }
}

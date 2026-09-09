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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Album;
import com.psthetech.swara.util.ArtworkHelper;

public class AlbumAdapter extends ListAdapter<Album, AlbumAdapter.AlbumViewHolder> {

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    private final OnAlbumClickListener listener;

    public AlbumAdapter(OnAlbumClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Album> DIFF_CALLBACK = new DiffUtil.ItemCallback<Album>() {
        @Override
        public boolean areItemsTheSame(@NonNull Album oldItem, @NonNull Album newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Album oldItem, @NonNull Album newItem) {
            return oldItem.getTitle().equals(newItem.getTitle())
                    && oldItem.getSongCount() == newItem.getSongCount();
        }
    };

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        Album album = getItem(position);
        holder.bind(album, listener);
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {
        private final ImageView albumArt;
        private final TextView albumTitle;
        private final TextView albumArtist;
        private final TextView albumTracks;

        public AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.albumArt);
            albumTitle = itemView.findViewById(R.id.albumTitle);
            albumArtist = itemView.findViewById(R.id.albumArtist);
            albumTracks = itemView.findViewById(R.id.albumTracks);
        }

        public void bind(Album album, OnAlbumClickListener listener) {
            albumTitle.setText(album.getTitle());
            albumArtist.setText(album.getArtist());
            String tracksText = album.getSongCount() + (album.getSongCount() == 1 ? " track" : " tracks");
            albumTracks.setText(tracksText);

            Glide.with(itemView.getContext())
                    .load(ArtworkHelper.getAlbumArtUri(album.getId()))
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(albumArt);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAlbumClick(album);
                }
            });
        }
    }
}

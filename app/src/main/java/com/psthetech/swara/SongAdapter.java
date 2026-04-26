package com.psthetech.swara;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.*;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private List<Song> lsongs;
    private List<Song> filteredSongs;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView artist;
        private final TextView btnFavorite;


        public ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
            artist = view.findViewById(R.id.artist);
            btnFavorite = view.findViewById(R.id.btnFavorite);

        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.item_song, viewGroup, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Song song = filteredSongs.get(position);
        holder.title.setText(song.getTitle());
        holder.artist.setText(song.getArtist());

        // check if favorite
        AppDatabase db = AppDatabase.getInstance(holder.itemView.getContext());
        boolean isFav = db.favoriteDao().isFavorite(song.getId());
        holder.btnFavorite.setText(isFav ? "♥" : "♡");
        holder.btnFavorite.setTextColor(isFav ?
                android.graphics.Color.parseColor("#C9A84C") :
                android.graphics.Color.parseColor("#9B7EC8"));

        // toggle favorite on click
        holder.btnFavorite.setOnClickListener(v -> {
            AppDatabase database = AppDatabase.getInstance(holder.itemView.getContext());
            if (database.favoriteDao().isFavorite(song.getId())) {
                FavoriteSong fav = new FavoriteSong(song.getId(), song.getTitle(),
                        song.getArtist(), song.getAlbum(), song.getDuration(), song.getPath());
                database.favoriteDao().removeFavorite(fav);
                holder.btnFavorite.setText("♡");
                holder.btnFavorite.setTextColor(android.graphics.Color.parseColor("#9B7EC8"));
            } else {
                FavoriteSong fav = new FavoriteSong(song.getId(), song.getTitle(),
                        song.getArtist(), song.getAlbum(), song.getDuration(), song.getPath());
                database.favoriteDao().addFavorite(fav);
                holder.btnFavorite.setText("♥");
                holder.btnFavorite.setTextColor(android.graphics.Color.parseColor("#C9A84C"));
            }
        });

        holder.itemView.setOnClickListener(v -> listener.onSongClick(song));
    }

    // Return the size of your dataset (invoked by the layout manager)

    @Override
    public int getItemCount() {
        return filteredSongs.size();
    }
    public interface OnSongClickListener {
        void onSongClick(Song song);
    }
    private OnSongClickListener listener;

    public SongAdapter(List<Song> songs, OnSongClickListener listener) {
        lsongs = songs;
        filteredSongs = new ArrayList<>(songs); // copy of full list
        this.listener = listener;
    }
    public void filter(String query) {
        filteredSongs.clear();
        if (query.isEmpty()) {
            filteredSongs.addAll(lsongs);
        } else {
            String lower = query.toLowerCase();
            for (Song song : lsongs) {
                if (song.getTitle().toLowerCase().contains(lower) ||
                        song.getArtist().toLowerCase().contains(lower)) {
                    filteredSongs.add(song);
                }
            }
        }
        notifyDataSetChanged();
    }
}
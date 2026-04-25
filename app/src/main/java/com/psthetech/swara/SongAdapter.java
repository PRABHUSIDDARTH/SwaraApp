package com.psthetech.swara;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private List<Song> lsongs;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView artist;

        public ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
            artist = view.findViewById(R.id.artist); // add this
        }
    }


    public SongAdapter(List<Song> songs) {
        lsongs = songs;
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
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        Song song = lsongs.get(position);
        viewHolder.title.setText(song.getTitle());
        viewHolder.artist.setText(song.getArtist());
        viewHolder.itemView.setOnClickListener(v -> {
            listener.onSongClick(lsongs.get(position));
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return lsongs.size();
    }
    public interface OnSongClickListener {
        void onSongClick(Song song);
    }
    private OnSongClickListener listener;

    public SongAdapter(List<Song> songs, OnSongClickListener listener) {
        lsongs = songs;
        this.listener = listener;
    }
}
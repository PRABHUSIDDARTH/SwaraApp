package com.psthetech.swara.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtworkHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * SongAdapter V2 — a ListAdapter using DiffUtil for efficient updates.
 *
 * Features:
 * - DiffUtil for O(n) diff instead of notifyDataSetChanged
 * - Album artwork loaded via Glide (async, cached)
 * - Favorite state driven from outside (set via setFavorites)
 * - Context menu: Play Next, Add to Queue, Add to Playlist, Favorite, Remove
 * - No main-thread DB access — favorites are injected via setFavorites()
 * - View recycling cleanup via onViewRecycled
 */
public class SongAdapter extends ListAdapter<Song, SongAdapter.ViewHolder> {

    public interface Listener {
        void onSongClick(Song song, int position);
        void onPlayNext(Song song);
        void onAddToQueue(Song song);
        void onAddToPlaylist(Song song);
        void onFavoriteToggle(Song song, boolean currentlyFavorite);
        void onRemoveFromPlaylist(Song song); // Optional — only shown in playlist context
        default void onEditArtwork(Song song) {}
    }

    private Listener listener;
    private Set<Long> favoriteSongIds = new HashSet<>();
    private boolean showRemoveFromPlaylist = false;

    public SongAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setFavorites(Set<Long> favoriteIds) {
        this.favoriteSongIds = favoriteIds != null ? favoriteIds : new HashSet<>();
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setShowRemoveFromPlaylist(boolean show) {
        this.showRemoveFromPlaylist = show;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Song song = getItem(position);
        Context ctx = holder.itemView.getContext();

        holder.tvTitle.setText(song.getTitle());
        holder.tvSubtitle.setText(song.getArtist() + " • " + song.getFormattedDuration());

        // Load artwork asynchronously via Glide
        ArtworkHelper.loadSongArt(ctx, song, holder.ivArtwork);

        // Favorite state (no DB query — driven by injected set)
        boolean isFav = favoriteSongIds.contains(song.getId());
        updateFavoriteIcon(holder.ivFavorite, isFav);
        holder.ivFavorite.setContentDescription(ctx.getString(
                isFav ? R.string.cd_favorite_filled : R.string.cd_favorite_empty));

        // Tap: play song
        holder.itemView.setOnClickListener(v -> listener.onSongClick(song, holder.getAdapterPosition()));

        // Favorite toggle
        holder.ivFavorite.setOnClickListener(v -> {
            boolean fav = favoriteSongIds.contains(song.getId());
            listener.onFavoriteToggle(song, fav);
            // Optimistic UI update
            if (fav) {
                favoriteSongIds.remove(song.getId());
            } else {
                favoriteSongIds.add(song.getId());
            }
            updateFavoriteIcon(holder.ivFavorite, !fav);
        });

        // Overflow / context menu
        holder.ivMore.setOnClickListener(v -> showContextMenu(v, song, ctx));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Clear Glide load to prevent image bleeding between items
        ArtworkHelper.clear(holder.itemView.getContext(), holder.ivArtwork);
    }

    private void updateFavoriteIcon(ImageView iv, boolean isFav) {
        iv.setImageResource(isFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        int tintColor = isFav
                ? iv.getContext().getColor(R.color.swara_gold)
                : iv.getContext().getColor(R.color.swara_lavender);
        iv.setColorFilter(tintColor);
    }

    private void showContextMenu(View anchor, Song song, Context ctx) {
        PopupMenu popup = new PopupMenu(ctx, anchor);
        popup.getMenu().add(0, 1, 0, ctx.getString(R.string.play_next));
        popup.getMenu().add(0, 2, 1, ctx.getString(R.string.add_to_queue));
        popup.getMenu().add(0, 3, 2, ctx.getString(R.string.add_to_playlist));
        boolean isFav = favoriteSongIds.contains(song.getId());
        popup.getMenu().add(0, 4, 3, ctx.getString(isFav
                ? R.string.remove_from_favorites : R.string.add_to_favorites));
        if (showRemoveFromPlaylist) {
            popup.getMenu().add(0, 5, 4, ctx.getString(R.string.remove_from_playlist));
        }
        popup.getMenu().add(0, 6, 5, ctx.getString(R.string.edit_artwork));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: listener.onPlayNext(song); return true;
                case 2: listener.onAddToQueue(song); return true;
                case 3: listener.onAddToPlaylist(song); return true;
                case 4: listener.onFavoriteToggle(song, isFav); return true;
                case 5: listener.onRemoveFromPlaylist(song); return true;
                case 6: listener.onEditArtwork(song); return true;
            }
            return false;
        });
        popup.show();
    }

    // ===== ViewHolder =====

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivArtwork;
        TextView tvTitle;
        TextView tvSubtitle;
        ImageView ivFavorite;
        ImageView ivMore;

        public ViewHolder(@NonNull View view) {
            super(view);
            ivArtwork = view.findViewById(R.id.ivArtwork);
            tvTitle = view.findViewById(R.id.tvTitle);
            tvSubtitle = view.findViewById(R.id.tvSubtitle);
            ivFavorite = view.findViewById(R.id.ivFavorite);
            ivMore = view.findViewById(R.id.ivMore);
        }
    }

    // ===== DiffUtil =====

    private static final DiffUtil.ItemCallback<Song> DIFF_CALLBACK = new DiffUtil.ItemCallback<Song>() {
        @Override
        public boolean areItemsTheSame(@NonNull Song a, @NonNull Song b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song a, @NonNull Song b) {
            return a.getId() == b.getId()
                    && a.getTitle().equals(b.getTitle())
                    && a.getArtist().equals(b.getArtist());
        }
    };
}

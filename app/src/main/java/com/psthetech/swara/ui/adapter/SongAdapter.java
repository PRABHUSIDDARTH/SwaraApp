package com.psthetech.swara.ui.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
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
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.ArtworkHelper;
import com.psthetech.swara.util.FavoriteAnimationHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * SongAdapter V2.2 — Liquid Glass + Color Themes + Playback Glow + Favorite Animation.
 *
 * Features:
 * - DiffUtil for efficient updates
 * - Live semantic DesignTokens consumed per bind — responds to theme changes
 * - Playback glow: subtle accent-tinted background + border for currently playing row
 * - Favorite micro-animation: scale-pulse on add, alpha-fade on remove
 * - RecyclerView safety: glow + animations fully reset in onViewRecycled
 * - Context menu: Play Next, Add to Queue, Add to Playlist, Favorite, Remove, Edit Artwork, Delete, Share
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
        default void onDeleteSong(Song song) {}
    }

    private Listener listener;
    private Set<Long> favoriteSongIds = new HashSet<>();
    private boolean showRemoveFromPlaylist = false;
    private long currentPlayingSongId = -1L;

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

    /**
     * Update the currently playing song. Only the affected rows are rebound.
     * Call from fragment's currentSong observer.
     */
    public void setCurrentPlayingSongId(long songId) {
        long previous = this.currentPlayingSongId;
        this.currentPlayingSongId = songId;

        // Rebind previous (clear glow) and new (apply glow)
        for (int i = 0; i < getItemCount(); i++) {
            Song item = getItem(i);
            if (item.getId() == previous || item.getId() == songId) {
                notifyItemChanged(i, "PLAYBACK_STATE_CHANGED"); // payload to avoid full re-bind flicker
            }
        }
    }

    // ===== RecyclerView.Adapter =====

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
        boolean isCurrentlyPlaying = song.getId() == currentPlayingSongId && currentPlayingSongId != -1L;

        DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();

        // ===== Text =====
        holder.tvTitle.setText(song.getTitle());
        holder.tvSubtitle.setText(song.getArtist() + " • " + song.getFormattedDuration());

        if (tokens != null) {
            // Currently playing rows: title uses readable accent color; others use primary text
            holder.tvTitle.setTextColor(
                    isCurrentlyPlaying ? tokens.getReadableAccentColor() : tokens.getTextPrimaryColor());
            holder.tvSubtitle.setTextColor(tokens.getTextSecondaryColor());
            holder.ivMore.setColorFilter(tokens.getIconSecondaryColor());
            holder.ivArtwork.setBackgroundColor(tokens.getSurfaceVariantColor());
        }
        holder.tvTitle.setTypeface(null, isCurrentlyPlaying ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        // ===== Artwork =====
        ArtworkHelper.loadSongArt(ctx, song, holder.ivArtwork);

        // ===== Playback Glow =====
        applyPlaybackGlow(holder, isCurrentlyPlaying, tokens);

        // ===== Favorite =====
        boolean isFav = favoriteSongIds.contains(song.getId());
        updateFavoriteIcon(holder.ivFavorite, isFav, tokens);
        holder.ivFavorite.setContentDescription(ctx.getString(
                isFav ? R.string.cd_favorite_filled : R.string.cd_favorite_empty));

        // ===== Click Listeners =====
        holder.itemView.setOnClickListener(v -> listener.onSongClick(song, holder.getAdapterPosition()));

        holder.ivFavorite.setOnClickListener(v -> {
            boolean fav = favoriteSongIds.contains(song.getId());
            listener.onFavoriteToggle(song, fav);
            // Optimistic UI update
            if (fav) {
                favoriteSongIds.remove(song.getId());
                FavoriteAnimationHelper.animateFavoriteRemove(holder.ivFavorite);
            } else {
                favoriteSongIds.add(song.getId());
                FavoriteAnimationHelper.animateFavoriteAdd(
                        holder.ivFavorite, tokens != null ? tokens.getFavoriteActiveColor() : 0xFFC9A84C);
            }
            updateFavoriteIcon(holder.ivFavorite, !fav, tokens);
        });

        holder.ivMore.setOnClickListener(v -> showContextMenu(v, song, ctx));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                  @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("PLAYBACK_STATE_CHANGED")) {
            // Lightweight rebind: only update glow + title color + bold typeface
            Song song = getItem(position);
            boolean isCurrentlyPlaying = song.getId() == currentPlayingSongId && currentPlayingSongId != -1L;
            DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                holder.tvTitle.setTextColor(
                        isCurrentlyPlaying ? tokens.getReadableAccentColor() : tokens.getTextPrimaryColor());
            }
            holder.tvTitle.setTypeface(null, isCurrentlyPlaying ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            applyPlaybackGlow(holder, isCurrentlyPlaying, tokens);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Clear Glide load to prevent image bleeding
        ArtworkHelper.clear(holder.itemView.getContext(), holder.ivArtwork);
        holder.ivArtwork.setImageDrawable(null);
        // Reset playback glow and typeface
        holder.itemView.setBackground(null);
        holder.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        // Reset scale and alpha
        holder.itemView.setScaleX(1.0f);
        holder.itemView.setScaleY(1.0f);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
        // Reset favorite animation state — prevent animation leakage to recycled rows
        FavoriteAnimationHelper.cancelFavoriteAnimation(holder.ivFavorite);
    }

    // ===== Glow helpers =====

    private void applyPlaybackGlow(ViewHolder holder, boolean isPlaying, DesignTokens tokens) {
        if (isPlaying && tokens != null) {
            // Subtle tinted glass surface for the playing row
            GradientDrawable glow = new GradientDrawable();
            glow.setShape(GradientDrawable.RECTANGLE);
            glow.setColor(tokens.getPlaybackHighlightColor());
            float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
            glow.setCornerRadius(tokens.getCornerRadiusDp() * density);
            glow.setStroke(Math.max(1, Math.round(1.5f * density)), tokens.getPlaybackGlowColor());
            holder.itemView.setBackground(glow);
        } else {
            // Always clear glow for non-playing rows (RecyclerView safety)
            holder.itemView.setBackground(null);
        }
    }

    // ===== Favorite icon =====

    private void updateFavoriteIcon(ImageView iv, boolean isFav, DesignTokens tokens) {
        iv.setImageResource(isFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        if (tokens != null) {
            iv.setColorFilter(isFav ? tokens.getFavoriteActiveColor() : tokens.getFavoriteInactiveColor());
        } else {
            iv.setColorFilter(isFav
                    ? iv.getContext().getColor(R.color.swara_gold)
                    : iv.getContext().getColor(R.color.swara_lavender));
        }
    }

    // ===== Context menu =====

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
        popup.getMenu().add(0, 7, 6, ctx.getString(R.string.delete_from_device));
        popup.getMenu().add(0, 8, 7, ctx.getString(R.string.share));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: listener.onPlayNext(song); return true;
                case 2: listener.onAddToQueue(song); return true;
                case 3: listener.onAddToPlaylist(song); return true;
                case 4: listener.onFavoriteToggle(song, isFav); return true;
                case 5: listener.onRemoveFromPlaylist(song); return true;
                case 6: listener.onEditArtwork(song); return true;
                case 7: listener.onDeleteSong(song); return true;
                case 8:
                    android.content.Intent shareIntent = new android.content.Intent(
                            android.content.Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                            song.getTitle() + " — " + song.getArtist());
                    ctx.startActivity(android.content.Intent.createChooser(
                            shareIntent, ctx.getString(R.string.share)));
                    return true;
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

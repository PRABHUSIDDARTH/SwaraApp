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

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.repository.PlaylistArtworkStore;
import com.psthetech.swara.util.PlaylistArtworkHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Horizontal compact playlist adapter for the Home screen.
 * Shows playlist artwork, name, and song count.
 */
public class HomePlaylistAdapter extends ListAdapter<Playlist, HomePlaylistAdapter.ViewHolder> {

    public interface Listener {
        void onPlaylistClick(Playlist playlist);
    }

    private final Listener listener;
    private PlaylistArtworkStore artworkStore;
    private Map<Long, Integer> songCounts = new HashMap<>();
    private Map<Long, List<Long>> playlistAlbumIds = new HashMap<>();

    public HomePlaylistAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setArtworkStore(PlaylistArtworkStore store) {
        this.artworkStore = store;
    }

    public void setSongCounts(Map<Long, Integer> counts) {
        this.songCounts = counts != null ? counts : new HashMap<>();
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setPlaylistAlbumIds(Map<Long, List<Long>> albumIds) {
        this.playlistAlbumIds = albumIds != null ? albumIds : new HashMap<>();
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_home, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = getItem(position);
        int count = songCounts.containsKey(playlist.id) ? songCounts.get(playlist.id) : 0;
        holder.bind(playlist, count, artworkStore, listener);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        PlaylistArtworkHelper.clear(holder.itemView.getContext(), holder.ivPlaylistArtwork);
        holder.ivPlaylistArtwork.setImageDrawable(null);
        holder.itemView.setScaleX(1.0f);
        holder.itemView.setScaleY(1.0f);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivPlaylistArtwork;
        private final TextView tvName;
        private final TextView tvCount;

        ViewHolder(@NonNull View v) {
            super(v);
            ivPlaylistArtwork = v.findViewById(R.id.ivPlaylistArtwork);
            tvName  = v.findViewById(R.id.tvPlaylistName);
            tvCount = v.findViewById(R.id.tvPlaylistCount);
        }

        void bind(Playlist playlist, int count,
                  PlaylistArtworkStore store, Listener listener) {
            // Explicitly reset any recycled state
            itemView.setScaleX(1.0f);
            itemView.setScaleY(1.0f);
            itemView.setAlpha(1.0f);
            itemView.setTranslationX(0f);
            itemView.setTranslationY(0f);

            tvName.setText(playlist.name);
            tvCount.setText(count + (count == 1 ? " song" : " songs"));

            // Load artwork via authoritative priority chain
            PlaylistArtworkHelper.loadPlaylistArt(
                    itemView.getContext(), playlist, store, ivPlaylistArtwork);

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();

            if (tokens != null) {
                tvName.setTextColor(tokens.getTextPrimaryColor());
                tvCount.setTextColor(tokens.getTextSecondaryColor());
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                        .applyToView(itemView, false, tokens);
            }

            itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        }
    }

    private static final DiffUtil.ItemCallback<Playlist> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Playlist>() {
                @Override
                public boolean areItemsTheSame(@NonNull Playlist a, @NonNull Playlist b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Playlist a, @NonNull Playlist b) {
                    return a.name.equals(b.name)
                            && a.modifiedAt == b.modifiedAt
                            && safeEquals(a.artworkPath, b.artworkPath);
                }

                private boolean safeEquals(String x, String y) {
                    if (x == null && y == null) return true;
                    if (x == null || y == null) return false;
                    return x.equals(y);
                }
            };
}

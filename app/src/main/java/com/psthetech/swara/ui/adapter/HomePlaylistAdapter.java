package com.psthetech.swara.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 * Horizontal compact playlist adapter for the Home screen.
 */
public class HomePlaylistAdapter extends ListAdapter<Playlist, HomePlaylistAdapter.ViewHolder> {

    public interface Listener {
        void onPlaylistClick(Playlist playlist);
    }

    private final Listener listener;
    private Map<Long, Integer> songCounts = new HashMap<>();

    public HomePlaylistAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setSongCounts(Map<Long, Integer> counts) {
        this.songCounts = counts != null ? counts : new HashMap<>();
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
        holder.bind(playlist, count, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvCount;

        ViewHolder(@NonNull View v) {
            super(v);
            tvName  = v.findViewById(R.id.tvPlaylistName);
            tvCount = v.findViewById(R.id.tvPlaylistCount);
        }

        void bind(Playlist playlist, int count, Listener listener) {
            tvName.setText(playlist.name);
            tvCount.setText(count + (count == 1 ? " song" : " songs"));

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
                    return a.name.equals(b.name) && a.modifiedAt == b.modifiedAt;
                }
            };
}

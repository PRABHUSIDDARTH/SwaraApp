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
import com.psthetech.swara.data.db.entity.Mix;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtworkHelper;

/** Horizontal "For you" mix cards: first song's artwork, title and song count. */
public class MixAdapter extends ListAdapter<Mix, MixAdapter.ViewHolder> {

    public interface Listener {
        void onMixClick(Mix mix);
    }

    private final Listener listener;

    public MixAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /** Parses a comma-separated id list, ignoring malformed entries. */
    @NonNull
    public static java.util.List<Long> parseIds(String csv) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String p : csv.split(",")) {
            try {
                out.add(Long.parseLong(p.trim()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mix, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Mix mix = getItem(position);
        java.util.List<Long> ids = parseIds(mix.songIds);
        holder.tvName.setText(mix.title);
        int n = ids.size();
        holder.tvCount.setText(holder.itemView.getResources()
                .getQuantityString(R.plurals.mix_song_count, n, n));
        Song first = null;
        for (long id : ids) {
            first = MusicRepository.getCanonicalSong(id);
            if (first != null) break;
        }
        ArtworkHelper.loadSongArt(holder.itemView.getContext(), first, holder.ivArtwork);

        com.psthetech.swara.ui.theme.DesignTokens tokens =
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
        if (tokens != null) {
            holder.tvName.setTextColor(tokens.getTextPrimaryColor());
            holder.tvCount.setTextColor(tokens.getTextSecondaryColor());
            com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                    .applyToView(holder.itemView, false, tokens);
        }
        holder.itemView.setOnClickListener(v -> listener.onMixClick(mix));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        ArtworkHelper.clear(holder.itemView.getContext(), holder.ivArtwork);
        holder.ivArtwork.setImageDrawable(null);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivArtwork;
        final TextView tvName;
        final TextView tvCount;

        ViewHolder(@NonNull View v) {
            super(v);
            ivArtwork = v.findViewById(R.id.ivMixArtwork);
            tvName = v.findViewById(R.id.tvMixName);
            tvCount = v.findViewById(R.id.tvMixCount);
        }
    }

    private static final DiffUtil.ItemCallback<Mix> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Mix>() {
                @Override
                public boolean areItemsTheSame(@NonNull Mix a, @NonNull Mix b) {
                    return a.mixKey.equals(b.mixKey);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Mix a, @NonNull Mix b) {
                    return a.builtAt == b.builtAt && java.util.Objects.equals(a.title, b.title)
                            && java.util.Objects.equals(a.songIds, b.songIds);
                }
            };
}

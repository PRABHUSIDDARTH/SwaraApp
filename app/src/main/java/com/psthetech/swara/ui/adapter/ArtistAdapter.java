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
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.util.ArtworkHelper;

public class ArtistAdapter extends ListAdapter<Artist, ArtistAdapter.ArtistViewHolder> {

    public interface OnArtistClickListener {
        void onArtistClick(Artist artist);
    }

    private final OnArtistClickListener listener;

    public ArtistAdapter(OnArtistClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Artist> DIFF_CALLBACK = new DiffUtil.ItemCallback<Artist>() {
        @Override
        public boolean areItemsTheSame(@NonNull Artist oldItem, @NonNull Artist newItem) {
            return oldItem.getCanonicalKey().equalsIgnoreCase(newItem.getCanonicalKey());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Artist oldItem, @NonNull Artist newItem) {
            return oldItem.getSongCount() == newItem.getSongCount()
                    && oldItem.getAlbumCount() == newItem.getAlbumCount()
                    && oldItem.getName().equals(newItem.getName());
        }
    };

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_artist, parent, false);
        return new ArtistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        Artist artist = getItem(position);
        holder.bind(artist, listener);
    }

    static class ArtistViewHolder extends RecyclerView.ViewHolder {
        private final ImageView artistImage;
        private final TextView artistName;
        private final TextView artistDetails;

        public ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            artistImage = itemView.findViewById(R.id.artistImage);
            artistName = itemView.findViewById(R.id.artistName);
            artistDetails = itemView.findViewById(R.id.artistDetails);
        }

        public void bind(Artist artist, OnArtistClickListener listener) {
            artistName.setText(artist.getName());
            String detailsText = artist.getAlbumCount() + " albums • " + artist.getSongCount() + " songs";
            artistDetails.setText(detailsText);

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();

            if (tokens != null) {
                artistName.setTextColor(tokens.getTextPrimaryColor());
                artistDetails.setTextColor(tokens.getTextSecondaryColor());
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                        .applyToView(itemView, false, tokens);
            }

            Glide.with(itemView.getContext())
                    .load(R.drawable.ic_artist_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(artistImage);

            itemView.setScaleX(1.0f);
            itemView.setScaleY(1.0f);
            itemView.setAlpha(1.0f);
            itemView.setTranslationX(0f);
            itemView.setTranslationY(0f);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onArtistClick(artist);
                }
            });
        }
    }

    @Override
    public void onViewRecycled(@NonNull ArtistViewHolder holder) {
        super.onViewRecycled(holder);
        ArtworkHelper.clear(holder.itemView.getContext(), holder.artistImage);
        holder.artistImage.setImageDrawable(null);
        holder.itemView.setScaleX(1.0f);
        holder.itemView.setScaleY(1.0f);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
    }
}

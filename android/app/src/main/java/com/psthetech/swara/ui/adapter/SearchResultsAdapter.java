package com.psthetech.swara.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.domain.model.Album;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.SearchResults;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.data.repository.PlaylistArtworkStore;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.ArtworkHelper;
import com.psthetech.swara.util.PlaylistArtworkHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-type adapter for search results.
 *
 * View types:
 *  HEADER  — section title (Songs, Albums, Artists, Playlists)
 *  SONG    — uses item_song layout
 *  ALBUM   — uses item_album layout
 *  ARTIST  — uses item_artist layout
 *  PLAYLIST — uses item_playlist layout
 *
 * Sections are only shown when they have results.
 */
public class SearchResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER  = 0;
    public static final int TYPE_SONG    = 1;
    public static final int TYPE_ALBUM   = 2;
    public static final int TYPE_ARTIST  = 3;
    public static final int TYPE_PLAYLIST = 4;

    public interface Listener {
        void onSongClick(Song song);
        void onAlbumClick(Album album);
        void onArtistClick(Artist artist);
        void onPlaylistClick(Playlist playlist);
        void onSongAddToPlaylist(Song song);
    }

    private final Listener listener;
    private final List<Object> items = new ArrayList<>(); // String (header) or domain obj
    private long currentPlayingSongId = -1;
    private PlaylistArtworkStore playlistArtworkStore;

    public SearchResultsAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void setPlaylistArtworkStore(PlaylistArtworkStore playlistArtworkStore) {
        this.playlistArtworkStore = playlistArtworkStore;
    }

    public void setCurrentPlayingSongId(long songId) {
        if (this.currentPlayingSongId != songId) {
            this.currentPlayingSongId = songId;
            notifyDataSetChanged();
        }
    }

    /** Replace the entire dataset with a new SearchResults object. */
    public void submitResults(SearchResults results) {
        items.clear();

        if (results == null || results.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        if (!results.songs.isEmpty()) {
            items.add(new Header(R.string.results_songs));
            items.addAll(results.songs);
        }
        if (!results.albums.isEmpty()) {
            items.add(new Header(R.string.results_albums));
            items.addAll(results.albums);
        }
        if (!results.artists.isEmpty()) {
            items.add(new Header(R.string.results_artists));
            items.addAll(results.artists);
        }
        if (!results.playlists.isEmpty()) {
            items.add(new Header(R.string.results_playlists));
            items.addAll(results.playlists);
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof Header)   return TYPE_HEADER;
        if (item instanceof Song)     return TYPE_SONG;
        if (item instanceof Album)    return TYPE_ALBUM;
        if (item instanceof Artist)   return TYPE_ARTIST;
        if (item instanceof Playlist) return TYPE_PLAYLIST;
        return TYPE_HEADER;
    }

    @Override
    public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderHolder(inf.inflate(R.layout.item_search_header, parent, false));
            case TYPE_ALBUM:
                return new AlbumHolder(inf.inflate(R.layout.item_album, parent, false));
            case TYPE_ARTIST:
                return new ArtistHolder(inf.inflate(R.layout.item_artist, parent, false));
            case TYPE_PLAYLIST:
                return new PlaylistHolder(inf.inflate(R.layout.item_playlist, parent, false));
            default: // TYPE_SONG
                return new SongHolder(inf.inflate(R.layout.item_song, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        Context ctx = holder.itemView.getContext();

        if (holder instanceof HeaderHolder && item instanceof Header) {
            ((HeaderHolder) holder).bind((Header) item, ctx);
        } else if (holder instanceof SongHolder && item instanceof Song) {
            Song song = (Song) item;
            boolean isPlaying = (currentPlayingSongId != -1 && song.getId() == currentPlayingSongId);
            ((SongHolder) holder).bind(song, isPlaying, listener, ctx);
        } else if (holder instanceof AlbumHolder && item instanceof Album) {
            ((AlbumHolder) holder).bind((Album) item, listener, ctx);
        } else if (holder instanceof ArtistHolder && item instanceof Artist) {
            ((ArtistHolder) holder).bind((Artist) item, listener, ctx);
        } else if (holder instanceof PlaylistHolder && item instanceof Playlist) {
            ((PlaylistHolder) holder).bind((Playlist) item, playlistArtworkStore, listener);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.itemView.setScaleX(1.0f);
        holder.itemView.setScaleY(1.0f);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
        if (holder instanceof SongHolder) {
            holder.itemView.setBackgroundResource(R.drawable.ripple_item);
            SongHolder sh = (SongHolder) holder;
            ArtworkHelper.clear(holder.itemView.getContext(), sh.ivArtwork);
            sh.ivArtwork.setImageDrawable(null);
            sh.tvTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else if (holder instanceof AlbumHolder) {
            AlbumHolder ah = (AlbumHolder) holder;
            ArtworkHelper.clear(holder.itemView.getContext(), ah.ivArtwork);
            ah.ivArtwork.setImageDrawable(null);
        } else if (holder instanceof ArtistHolder) {
            ArtistHolder arh = (ArtistHolder) holder;
            ArtworkHelper.clear(holder.itemView.getContext(), arh.ivArtwork);
            arh.ivArtwork.setImageDrawable(null);
        } else if (holder instanceof PlaylistHolder) {
            PlaylistHolder ph = (PlaylistHolder) holder;
            PlaylistArtworkHelper.clear(holder.itemView.getContext(), ph.ivArtwork);
            ph.ivArtwork.setImageDrawable(null);
        }
    }

    // ===== Header =====

    private static class Header {
        final int titleRes;
        Header(int titleRes) { this.titleRes = titleRes; }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView tvHeader;
        HeaderHolder(@NonNull View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tvSectionHeader);
        }
        void bind(Header h, Context ctx) {
            tvHeader.setText(ctx.getString(h.titleRes));
            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                tvHeader.setTextColor(tokens.getAccentColor());
            }
        }
    }

    // ===== Song =====

    static class SongHolder extends RecyclerView.ViewHolder {
        ImageView ivArtwork;
        TextView tvTitle, tvSubtitle;
        ImageView ivFavorite, ivMore;

        SongHolder(@NonNull View v) {
            super(v);
            ivArtwork  = v.findViewById(R.id.ivArtwork);
            tvTitle    = v.findViewById(R.id.tvTitle);
            tvSubtitle = v.findViewById(R.id.tvSubtitle);
            ivFavorite = v.findViewById(R.id.ivFavorite);
            ivMore     = v.findViewById(R.id.ivMore);
        }

        void bind(Song song, boolean isPlaying, Listener listener, Context ctx) {
            tvTitle.setText(song.getTitle());
            tvSubtitle.setText(song.getArtist() + " • " + song.getFormattedDuration());
            tvTitle.setTypeface(null, isPlaying ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            ArtworkHelper.loadSongArt(ctx, song, ivArtwork);

            DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                tvTitle.setTextColor(isPlaying ? tokens.getReadableAccentColor() : tokens.getTextPrimaryColor());
                tvSubtitle.setTextColor(tokens.getTextSecondaryColor());
                if (ivMore != null) ivMore.setColorFilter(tokens.getIconSecondaryColor());
                ivArtwork.setBackgroundColor(tokens.getSurfaceVariantColor());
            }

            applyPlaybackGlow(isPlaying, tokens);

            // Hide favorite in search results (not tracking favorites state here)
            if (ivFavorite != null) ivFavorite.setVisibility(View.GONE);
            itemView.setOnClickListener(v -> listener.onSongClick(song));
            if (ivMore != null) {
                ivMore.setOnClickListener(v -> listener.onSongAddToPlaylist(song));
            }
        }

        private void applyPlaybackGlow(boolean isPlaying, DesignTokens tokens) {
            if (isPlaying && tokens != null) {
                android.graphics.drawable.GradientDrawable glow = new android.graphics.drawable.GradientDrawable();
                glow.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                glow.setColor(tokens.getPlaybackSurfaceColor());
                float density = itemView.getContext().getResources().getDisplayMetrics().density;
                glow.setCornerRadius(tokens.getCornerRadiusDp() * density);
                glow.setStroke(Math.max(1, Math.round(1.5f * density)), tokens.getPlaybackStrokeColor());
                itemView.setBackground(glow);
            } else {
                itemView.setBackgroundResource(R.drawable.ripple_item);
            }
        }
    }

    // ===== Album =====

    static class AlbumHolder extends RecyclerView.ViewHolder {
        ImageView ivArtwork;
        TextView tvTitle, tvSubtitle;

        AlbumHolder(@NonNull View v) {
            super(v);
            ivArtwork  = v.findViewById(R.id.albumArt);
            tvTitle    = v.findViewById(R.id.albumTitle);
            tvSubtitle = v.findViewById(R.id.albumArtist);
        }

        void bind(Album album, Listener listener, Context ctx) {
            if (tvTitle != null) tvTitle.setText(album.getTitle());
            if (tvSubtitle != null) tvSubtitle.setText(album.getArtist());
            if (ivArtwork != null) ArtworkHelper.loadAlbumArt(ctx, album.getId(), ivArtwork);

            DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (tvTitle != null) tvTitle.setTextColor(tokens.getTextPrimaryColor());
                if (tvSubtitle != null) tvSubtitle.setTextColor(tokens.getTextSecondaryColor());
            }

            itemView.setOnClickListener(v -> listener.onAlbumClick(album));
        }
    }

    // ===== Artist =====

    static class ArtistHolder extends RecyclerView.ViewHolder {
        ImageView ivArtwork;
        TextView tvName, tvSubtitle;

        ArtistHolder(@NonNull View v) {
            super(v);
            ivArtwork  = v.findViewById(R.id.artistImage);
            tvName     = v.findViewById(R.id.artistName);
            tvSubtitle = v.findViewById(R.id.artistDetails);
        }

        void bind(Artist artist, Listener listener, Context ctx) {
            if (tvName != null) tvName.setText(artist.getName());
            if (tvSubtitle != null) {
                tvSubtitle.setText(artist.getSongCount() + " songs");
            }
            if (ivArtwork != null) ArtworkHelper.loadAlbumArt(ctx, artist.getRepresentativeAlbumId(), ivArtwork);

            DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (tvName != null) tvName.setTextColor(tokens.getTextPrimaryColor());
                if (tvSubtitle != null) tvSubtitle.setTextColor(tokens.getTextSecondaryColor());
            }

            itemView.setOnClickListener(v -> listener.onArtistClick(artist));
        }
    }

    // ===== Playlist =====

    static class PlaylistHolder extends RecyclerView.ViewHolder {
        final ImageView ivArtwork;
        final TextView tvName, tvCount;

        PlaylistHolder(@NonNull View v) {
            super(v);
            ivArtwork = v.findViewById(R.id.ivPlaylistArtwork);
            tvName  = v.findViewById(R.id.playlistName);
            tvCount = v.findViewById(R.id.playlistSongCount);
            // Hide the menu button in search context
            View menuBtn = v.findViewById(R.id.playlistMenuButton);
            if (menuBtn != null) menuBtn.setVisibility(View.GONE);
        }

        void bind(Playlist playlist, PlaylistArtworkStore artworkStore, Listener listener) {
            if (tvName != null) tvName.setText(playlist.name);
            if (tvCount != null) tvCount.setVisibility(View.GONE);

            if (ivArtwork != null) {
                PlaylistArtworkHelper.loadPlaylistArt(ivArtwork.getContext(), playlist, artworkStore, ivArtwork);
            }

            DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (tvName != null) tvName.setTextColor(tokens.getTextPrimaryColor());
            }

            itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        }
    }
}

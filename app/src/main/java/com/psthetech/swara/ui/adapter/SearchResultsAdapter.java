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
import com.psthetech.swara.util.ArtworkHelper;

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

    public SearchResultsAdapter(@NonNull Listener listener) {
        this.listener = listener;
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
            ((SongHolder) holder).bind((Song) item, listener, ctx);
        } else if (holder instanceof AlbumHolder && item instanceof Album) {
            ((AlbumHolder) holder).bind((Album) item, listener, ctx);
        } else if (holder instanceof ArtistHolder && item instanceof Artist) {
            ((ArtistHolder) holder).bind((Artist) item, listener, ctx);
        } else if (holder instanceof PlaylistHolder && item instanceof Playlist) {
            ((PlaylistHolder) holder).bind((Playlist) item, listener);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof SongHolder) {
            ArtworkHelper.clear(holder.itemView.getContext(), ((SongHolder) holder).ivArtwork);
        } else if (holder instanceof AlbumHolder) {
            ArtworkHelper.clear(holder.itemView.getContext(), ((AlbumHolder) holder).ivArtwork);
        } else if (holder instanceof ArtistHolder) {
            ArtworkHelper.clear(holder.itemView.getContext(), ((ArtistHolder) holder).ivArtwork);
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

        void bind(Song song, Listener listener, Context ctx) {
            tvTitle.setText(song.getTitle());
            tvSubtitle.setText(song.getArtist() + " • " + song.getFormattedDuration());
            ArtworkHelper.loadSongArt(ctx, song, ivArtwork);

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                tvTitle.setTextColor(tokens.getTextPrimaryColor());
                tvSubtitle.setTextColor(tokens.getTextSecondaryColor());
                if (ivMore != null) ivMore.setColorFilter(tokens.getIconSecondaryColor());
            }

            // Hide favorite in search results (not tracking favorites state here)
            if (ivFavorite != null) ivFavorite.setVisibility(View.GONE);
            itemView.setOnClickListener(v -> listener.onSongClick(song));
            if (ivMore != null) {
                ivMore.setOnClickListener(v -> listener.onSongAddToPlaylist(song));
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

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (tvTitle != null) tvTitle.setTextColor(tokens.getTextPrimaryColor());
                if (tvSubtitle != null) tvSubtitle.setTextColor(tokens.getTextSecondaryColor());
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                        .applyToView(itemView, false, tokens);
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

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (tvName != null) tvName.setTextColor(tokens.getTextPrimaryColor());
                if (tvSubtitle != null) tvSubtitle.setTextColor(tokens.getTextSecondaryColor());
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                        .applyToView(itemView, false, tokens);
            }

            itemView.setOnClickListener(v -> listener.onArtistClick(artist));
        }
    }

    // ===== Playlist =====

    static class PlaylistHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCount;

        PlaylistHolder(@NonNull View v) {
            super(v);
            tvName  = v.findViewById(R.id.playlistName);
            tvCount = v.findViewById(R.id.playlistSongCount);
            // Hide the menu button in search context
            View menuBtn = v.findViewById(R.id.playlistMenuButton);
            if (menuBtn != null) menuBtn.setVisibility(View.GONE);
        }

        void bind(Playlist playlist, Listener listener) {
            if (tvName != null) tvName.setText(playlist.name);
            if (tvCount != null) tvCount.setVisibility(View.GONE);

            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (tvName != null) tvName.setTextColor(tokens.getTextPrimaryColor());
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                        .applyToView(itemView, false, tokens);
            }

            itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        }
    }
}

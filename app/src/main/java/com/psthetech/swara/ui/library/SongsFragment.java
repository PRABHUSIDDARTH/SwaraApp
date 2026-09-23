package com.psthetech.swara.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.ShuffleEngine;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;
import com.psthetech.swara.ui.viewmodel.SortOrder;

import java.util.HashSet;
import java.util.List;

/**
 * Displays the full song list with sort options.
 *
 * Sort order is persisted in LibraryViewModel for the lifetime of the Activity.
 */
public class SongsFragment extends Fragment implements SongAdapter.Listener {

    private static final String PREF_SONGS_EXPANDED_MODE = "pref_songs_expanded_mode";

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;
    private PlaylistViewModel playlistViewModel;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View layoutEmpty;
    private ImageView btnSort;
    private ImageView btnShuffle;
    private ImageView btnViewMode;

    // Tracks last shuffled order for repeat-avoidance (same spec contract as playlist shuffle)
    private List<Song> lastShuffleOrder = null;

    private SongAdapter songAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_songs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        libraryViewModel   = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        playbackViewModel  = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);
        playlistViewModel  = new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class);

        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar  = view.findViewById(R.id.progressBar);
        layoutEmpty  = view.findViewById(R.id.layoutEmpty);
        btnSort      = view.findViewById(R.id.btnSort);
        btnShuffle   = view.findViewById(R.id.btnShuffle);
        btnViewMode  = view.findViewById(R.id.btnViewMode);

        songAdapter = new SongAdapter(this);

        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("swara_ui_prefs", android.content.Context.MODE_PRIVATE);
        boolean isExpanded = prefs.getBoolean(PREF_SONGS_EXPANDED_MODE, false);
        songAdapter.setExpandedMode(isExpanded);
        updateViewModeButton(isExpanded);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        if (btnViewMode != null) {
            btnViewMode.setOnClickListener(v -> {
                boolean newMode = !songAdapter.isExpandedMode();
                songAdapter.setExpandedMode(newMode);
                updateViewModeButton(newMode);
                prefs.edit().putBoolean(PREF_SONGS_EXPANDED_MODE, newMode).apply();
            });
        }

        if (btnSort != null) {
            btnSort.setOnClickListener(this::showSortMenu);
        }

        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(v -> shuffleLibrary());
        }

        observeData();
    }

    private void updateViewModeButton(boolean isExpanded) {
        if (btnViewMode != null) {
            btnViewMode.setImageResource(isExpanded ? R.drawable.ic_view_list : R.drawable.ic_view_expanded);
            btnViewMode.setContentDescription(getString(isExpanded ? R.string.view_mode_compact : R.string.view_mode_expanded));
        }
    }

    // ===== Sort menu =====

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 0, 0, getString(R.string.sort_title_az));
        popup.getMenu().add(0, 1, 1, getString(R.string.sort_title_za));
        popup.getMenu().add(0, 2, 2, getString(R.string.sort_artist_az));
        popup.getMenu().add(0, 3, 3, getString(R.string.sort_date_added));
        popup.getMenu().add(0, 4, 4, getString(R.string.sort_duration));

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: libraryViewModel.setSortOrder(SortOrder.TITLE_ASC);   return true;
                case 1: libraryViewModel.setSortOrder(SortOrder.TITLE_DESC);  return true;
                case 2: libraryViewModel.setSortOrder(SortOrder.ARTIST_ASC);  return true;
                case 3: libraryViewModel.setSortOrder(SortOrder.DATE_ADDED);  return true;
                case 4: libraryViewModel.setSortOrder(SortOrder.DURATION_ASC);return true;
            }
            return false;
        });
        popup.show();
    }

    // ===== Shuffle =====

    /**
     * Shuffles all visible library songs and starts playback.
     *
     * Contract:
     *  - Does NOT modify the library list order (display unchanged).
     *  - Uses ShuffleEngine.shuffleAvoidRepeat to avoid replaying the exact same order.
     *  - Deduplicates by Song ID before shuffling.
     *  - Delegates to PlaybackViewModel — ExoPlayer remains the sole playback authority.
     */
    private void shuffleLibrary() {
        List<Song> current = songAdapter.getCurrentList();
        if (current == null || current.isEmpty()) return;

        // ShuffleEngine: deduplicate + Fisher-Yates + repeat-avoidance
        List<Song> shuffled = ShuffleEngine.shuffleSongs(current, lastShuffleOrder);
        lastShuffleOrder = shuffled;

        // Play the shuffled queue — ExoPlayer receives the randomized order as MediaItems
        // position 0 = first song in the shuffled queue
        playbackViewModel.playSongs(shuffled, 0);
    }

    // ===== Data observation =====

    private void observeData() {
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), this::applyDesignTokens);

        libraryViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressBar != null) progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                songAdapter.setFavorites(new HashSet<>(ids));
            }
        });

        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (songAdapter != null) {
                songAdapter.setCurrentPlayingSongId(song != null ? song.getId() : -1L);
            }
        });

        libraryViewModel.getSongs().observe(getViewLifecycleOwner(), songs -> {
            if (songs == null || songs.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                songAdapter.submitList(songs);
            }
        });
    }

    private void applyDesignTokens(com.psthetech.swara.ui.theme.DesignTokens tokens) {
        View view = getView();
        if (tokens == null || view == null) return;

        view.setBackground(tokens.createAmbientDrawable());

        TextView tvHeaderTitle = view.findViewById(R.id.tvHeaderTitle);
        if (tvHeaderTitle != null) {
            tvHeaderTitle.setTextColor(tokens.getTextPrimaryColor());
        }

        View headerBar = view.findViewById(R.id.headerBar);
        if (headerBar instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) headerBar;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof android.widget.TextView) {
                    ((android.widget.TextView) child).setTextColor(tokens.getTextPrimaryColor());
                }
            }
        }

        if (btnSort != null) {
            btnSort.setBackground(tokens.createSurfaceVariantDrawable(requireContext()));
            btnSort.setColorFilter(tokens.getReadableAccentColor());
        }

        if (btnShuffle != null) {
            btnShuffle.setBackground(tokens.createSurfaceVariantDrawable(requireContext()));
            btnShuffle.setColorFilter(tokens.getReadableAccentColor());
        }

        if (btnViewMode != null) {
            btnViewMode.setBackground(tokens.createSurfaceVariantDrawable(requireContext()));
            btnViewMode.setColorFilter(tokens.getReadableAccentColor());
        }

        if (progressBar != null) {
            progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(tokens.getAccentColor()));
        }

        TextView tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle);
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setTextColor(tokens.getTextPrimaryColor());
        }

        TextView tvEmptySubtitle = view.findViewById(R.id.tvEmptySubtitle);
        if (tvEmptySubtitle != null) {
            tvEmptySubtitle.setTextColor(tokens.getTextSecondaryColor());
        }

        if (songAdapter != null) {
            songAdapter.notifyDataSetChanged();
        }
    }

    // ===== SongAdapter.Listener =====

    @Override
    public void onSongClick(Song song, int position) {
        List<Song> currentList = songAdapter.getCurrentList();
        playbackViewModel.playSongs(currentList, position);
    }

    @Override
    public void onFavoriteToggle(Song song, boolean currentlyFavorite) {
        favoritesViewModel.toggleFavorite(song);
    }

    @Override
    public void onPlayNext(Song song) {
        playbackViewModel.playNext(song);
    }

    @Override
    public void onAddToQueue(Song song) {
        playbackViewModel.addToQueue(song);
    }

    @Override
    public void onAddToPlaylist(Song song) {
        AddToPlaylistDialog.show(requireActivity(), requireView(), song,
                new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class));
    }

    @Override
    public void onRemoveFromPlaylist(Song song) {
        // not applicable
    }

    @Override
    public void onEditArtwork(Song song) {
        if (getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
            ((com.psthetech.swara.ui.MainActivity) getActivity()).promptEditArtwork(song);
        }
    }

    @Override
    public void onEditSongInfo(Song song) {
        if (getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
            ((com.psthetech.swara.ui.MainActivity) getActivity()).promptEditSongInfo(song);
        }
    }

    @Override
    public void onDeleteSong(Song song) {
        if (getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
            ((com.psthetech.swara.ui.MainActivity) getActivity()).promptDeleteSong(song);
        }
    }
}

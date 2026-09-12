package com.psthetech.swara.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
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

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;
    private PlaylistViewModel playlistViewModel;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View layoutEmpty;
    private ImageView btnSort;

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

        songAdapter = new SongAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        if (btnSort != null) {
            btnSort.setOnClickListener(this::showSortMenu);
        }

        observeData();
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

    // ===== Data observation =====

    private void observeData() {
        libraryViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressBar != null) progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                songAdapter.setFavorites(new HashSet<>(ids));
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
    public void onDeleteSong(Song song) {
        if (getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
            ((com.psthetech.swara.ui.MainActivity) getActivity()).promptDeleteSong(song);
        }
    }
}

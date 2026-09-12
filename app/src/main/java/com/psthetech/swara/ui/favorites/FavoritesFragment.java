package com.psthetech.swara.ui.favorites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;

import java.util.HashSet;
import java.util.List;

public class FavoritesFragment extends Fragment implements SongAdapter.Listener {

    private FavoritesViewModel favoritesViewModel;
    private PlaybackViewModel playbackViewModel;

    private RecyclerView recyclerView;
    private View layoutEmpty;
    private View btnPlayAllFavorites;
    private View btnShuffleFavorites;
    private SongAdapter songAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        recyclerView = view.findViewById(R.id.recyclerView);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        btnPlayAllFavorites  = view.findViewById(R.id.btnPlayAllFavorites);
        btnShuffleFavorites  = view.findViewById(R.id.btnShuffleFavorites);

        songAdapter = new SongAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        if (btnPlayAllFavorites != null) {
            btnPlayAllFavorites.setOnClickListener(v -> {
                List<Song> songs = songAdapter.getCurrentList();
                if (songs != null && !songs.isEmpty()) {
                    playbackViewModel.playSongs(songs, 0);
                }
            });
        }

        if (btnShuffleFavorites != null) {
            btnShuffleFavorites.setOnClickListener(v -> {
                List<Song> songs = songAdapter.getCurrentList();
                if (songs != null && !songs.isEmpty()) {
                    if (!Boolean.TRUE.equals(playbackViewModel.getShuffleEnabled().getValue())) {
                        playbackViewModel.toggleShuffle();
                    }
                    playbackViewModel.playSongs(songs, 0);
                }
            });
        }

        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    view.setBackgroundColor(tokens.getBackgroundColor());
                });

        observeData();
    }

    private void observeData() {
        favoritesViewModel.getFavoriteSongs().observe(getViewLifecycleOwner(), songs -> {
            if (songs == null || songs.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                if (btnPlayAllFavorites != null) btnPlayAllFavorites.setVisibility(View.GONE);
                if (btnShuffleFavorites != null) btnShuffleFavorites.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                if (btnPlayAllFavorites != null) btnPlayAllFavorites.setVisibility(View.VISIBLE);
                if (btnShuffleFavorites != null) btnShuffleFavorites.setVisibility(View.VISIBLE);
                songAdapter.submitList(songs);
            }
        });

        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                songAdapter.setFavorites(new HashSet<>(ids));
            }
        });
    }

    @Override
    public void onSongClick(Song song, int position) {
        List<Song> songs = songAdapter.getCurrentList();
        playbackViewModel.playSongs(songs, position);
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

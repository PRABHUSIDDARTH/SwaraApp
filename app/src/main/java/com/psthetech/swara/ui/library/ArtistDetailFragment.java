package com.psthetech.swara.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;

import java.util.HashSet;
import java.util.List;

public class ArtistDetailFragment extends Fragment implements SongAdapter.Listener {

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;

    private TextView artistTitle;
    private TextView artistMeta;
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;

    private String artistName = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_artist_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            artistName = getArguments().getString("artistName", "Artist");
        }

        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);

        artistTitle = view.findViewById(R.id.artistTitle);
        artistMeta = view.findViewById(R.id.artistMeta);
        recyclerView = view.findViewById(R.id.recyclerView);

        if (artistTitle != null) artistTitle.setText(artistName);

        view.findViewById(R.id.backButton).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        songAdapter = new SongAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        View btnPlayAll = view.findViewById(R.id.btnPlayAll);
        if (btnPlayAll != null) {
            btnPlayAll.setOnClickListener(v -> {
                List<Song> songs = songAdapter.getCurrentList();
                if (!songs.isEmpty()) playbackViewModel.playSongs(songs, 0);
            });
        }

        View btnShuffle = view.findViewById(R.id.btnShuffle);
        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(v -> {
                List<Song> songs = songAdapter.getCurrentList();
                if (!songs.isEmpty()) {
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
                    getView().setBackground(tokens.createAmbientDrawable());
                    if (artistTitle != null) artistTitle.setTextColor(tokens.getTextPrimaryColor());
                    if (artistMeta != null) artistMeta.setTextColor(tokens.getTextSecondaryColor());
                    android.widget.ImageView backButton = getView().findViewById(R.id.backButton);
                    if (backButton != null) backButton.setColorFilter(tokens.getTextPrimaryColor());
                    if (songAdapter != null) songAdapter.notifyDataSetChanged();
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

        libraryViewModel.getSongsForArtist(artistName).observe(getViewLifecycleOwner(), songs -> {
            if (songs != null) {
                songAdapter.submitList(songs);
                if (artistMeta != null) {
                    artistMeta.setText(songs.size() + (songs.size() == 1 ? " track" : " tracks"));
                }
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

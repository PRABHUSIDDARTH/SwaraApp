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

        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                songAdapter.setFavorites(new HashSet<>(ids));
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
    }

    @Override
    public void onRemoveFromPlaylist(Song song) {
    }
}

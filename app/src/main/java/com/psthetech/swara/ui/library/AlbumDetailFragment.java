package com.psthetech.swara.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;
import com.psthetech.swara.util.ArtworkHelper;

import java.util.HashSet;
import java.util.List;

public class AlbumDetailFragment extends Fragment implements SongAdapter.Listener {

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;

    private ImageView albumArt;
    private TextView albumTitle;
    private TextView albumMeta;
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;

    private long albumId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_album_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            albumId = getArguments().getLong("albumId", -1);
            String title = getArguments().getString("albumTitle", "Album");
            albumTitle = view.findViewById(R.id.albumTitle);
            if (albumTitle != null) albumTitle.setText(title);
        }

        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);

        albumArt = view.findViewById(R.id.albumArt);
        albumMeta = view.findViewById(R.id.albumMeta);
        recyclerView = view.findViewById(R.id.recyclerView);

        view.findViewById(R.id.backButton).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        songAdapter = new SongAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        View btnPlayAll = view.findViewById(R.id.btnPlayAll);
        if (btnPlayAll != null) {
            btnPlayAll.setOnClickListener(v -> {
                List<Song> songs = songAdapter.getCurrentList();
                if (!songs.isEmpty()) {
                    playbackViewModel.playSongs(songs, 0);
                }
            });
        }

        View btnShuffle = view.findViewById(R.id.btnShuffle);
        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(v -> {
                List<Song> songs = songAdapter.getCurrentList();
                if (!songs.isEmpty()) {
                    // Enable shuffle, then start from index 0
                    if (!Boolean.TRUE.equals(playbackViewModel.getShuffleEnabled().getValue())) {
                        playbackViewModel.toggleShuffle();
                    }
                    playbackViewModel.playSongs(songs, 0);
                }
            });
        }

        if (albumArt != null && albumId != -1) {
            Glide.with(this)
                    .load(ArtworkHelper.getAlbumArtUri(albumId))
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_placeholder)
                    .into(albumArt);
        }

        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                songAdapter.setFavorites(new HashSet<>(ids));
            }
        });

        libraryViewModel.getSongsForAlbum(albumId).observe(getViewLifecycleOwner(), songs -> {
            if (songs != null) {
                songAdapter.submitList(songs);
                if (albumMeta != null) {
                    albumMeta.setText(songs.size() + (songs.size() == 1 ? " track" : " tracks"));
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

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

public class ArtistDetailFragment extends Fragment implements SongAdapter.Listener, ArtistPickerBottomSheet.Listener {

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;

    private TextView artistTitle;
    private TextView artistMeta;
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;

    private String artistName = "";
    private String canonicalKey = "";

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
            canonicalKey = getArguments().getString("canonicalKey", "");
        }
        if (canonicalKey == null || canonicalKey.trim().isEmpty()) {
            canonicalKey = com.psthetech.swara.domain.model.Artist.getCanonicalKey(artistName);
        }

        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);

        artistTitle = view.findViewById(R.id.artistTitle);
        artistMeta = view.findViewById(R.id.artistMeta);
        recyclerView = view.findViewById(R.id.recyclerView);

        if (artistTitle != null) artistTitle.setText(artistName);

        view.findViewById(R.id.backButton).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        View btnMore = view.findViewById(R.id.btnArtistMore);
        if (btnMore != null) btnMore.setOnClickListener(this::showArtistMenu);

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
                    android.widget.ImageView moreButton = getView().findViewById(R.id.btnArtistMore);
                    if (moreButton != null) moreButton.setColorFilter(tokens.getTextPrimaryColor());
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

        libraryViewModel.getSongsForArtist(canonicalKey).observe(getViewLifecycleOwner(), songs -> {
            if (artistTitle != null) {
                artistTitle.setText(com.psthetech.swara.util.ArtistTitlePolicy.title(artistName,
                        com.psthetech.swara.data.repository.ArtistAliasRepository
                                .get(requireContext()).resolver()));
            }
            if (songs != null) {
                songAdapter.submitList(songs);
                if (artistMeta != null) {
                    artistMeta.setText(songs.size() + (songs.size() == 1 ? " track" : " tracks"));
                }
            }
        });
    }

    private static final int ID_MERGE_WITH = 1;
    private static final int ID_UNMERGE_BASE = 100;

    private void showArtistMenu(View anchor) {
        com.psthetech.swara.data.repository.ArtistAliasRepository repo =
                com.psthetech.swara.data.repository.ArtistAliasRepository.get(requireContext());
        com.psthetech.swara.util.ArtistAliasResolver resolver = repo.resolver();
        String mergedKey = resolver.resolve(canonicalKey);
        List<com.psthetech.swara.util.ArtistMergeMenuPolicy.Item> items =
                com.psthetech.swara.util.ArtistMergeMenuPolicy.items(mergedKey, resolver);
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), anchor);
        for (int i = 0; i < items.size(); i++) {
            com.psthetech.swara.util.ArtistMergeMenuPolicy.Item it = items.get(i);
            String label = it.kind == com.psthetech.swara.util.ArtistMergeMenuPolicy.Kind.UNMERGE
                    ? getString(R.string.artist_unmerge, it.creditDisplay)
                    : getString(R.string.artist_merge_with);
            int id = it.kind == com.psthetech.swara.util.ArtistMergeMenuPolicy.Kind.UNMERGE
                    ? ID_UNMERGE_BASE + i : ID_MERGE_WITH;
            android.view.MenuItem mi = popup.getMenu().add(0, id, i, label);
            if (android.os.Build.VERSION.SDK_INT >= 26) mi.setContentDescription(label);
        }
        popup.setOnMenuItemClickListener(mi -> {
            if (mi.getItemId() == ID_MERGE_WITH) {
                ArtistPickerBottomSheet.newInstance(mergedKey)
                        .show(getChildFragmentManager(), ArtistPickerBottomSheet.TAG);
                return true;
            }
            int idx = mi.getItemId() - ID_UNMERGE_BASE;
            if (idx >= 0 && idx < items.size()) {
                com.psthetech.swara.util.ArtistMergeMenuPolicy.Item it = items.get(idx);
                repo.unmerge(it.creditKey);
                com.google.android.material.snackbar.Snackbar.make(requireView(),
                        getString(R.string.artist_unmerged_snackbar, it.creditDisplay),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onArtistPicked(@NonNull String pickedKey, @NonNull String pickedName) {
        com.psthetech.swara.data.repository.ArtistAliasRepository repo =
                com.psthetech.swara.data.repository.ArtistAliasRepository.get(requireContext());
        String mergedKey = repo.resolver().resolve(canonicalKey);
        String fromName = artistTitle != null ? artistTitle.getText().toString() : artistName;
        repo.merge(mergedKey, pickedKey);
        View root = requireActivity().findViewById(android.R.id.content);
        com.google.android.material.snackbar.Snackbar.make(root != null ? root : requireView(),
                getString(R.string.artist_merged_snackbar, fromName, pickedName),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
        Bundle b = new Bundle();
        b.putString("artistName", pickedName);
        b.putString("canonicalKey", pickedKey);
        androidx.navigation.NavController nav = Navigation.findNavController(requireView());
        nav.navigate(R.id.artistDetailFragment, b,
                new androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.artistDetailFragment, true).build());
    }

    @Override
    public void onSongClick(Song song, int position) {
        List<Song> songs = songAdapter.getCurrentList();
        if (songs == null || songs.isEmpty()) return;
        int targetIndex = position;
        if (targetIndex < 0 || targetIndex >= songs.size() || songs.get(targetIndex).getId() != song.getId()) {
            targetIndex = -1;
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getId() == song.getId()) {
                    targetIndex = i;
                    break;
                }
            }
        }
        if (targetIndex >= 0) {
            playbackViewModel.playSongs(songs, targetIndex);
        } else {
            playbackViewModel.playSong(song);
        }
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
    public void onStartVibe(Song song) {
        playbackViewModel.startVibeFrom(song);
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

package com.psthetech.swara.ui.home;

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
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.HomePlaylistAdapter;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeFragment extends Fragment implements SongAdapter.Listener {

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;
    private PlaylistViewModel playlistViewModel;

    private TextView tvGreeting;
    private RecyclerView rvRecentlyPlayed;
    private RecyclerView rvRecentlyAdded;
    private RecyclerView rvPlaylists;
    private View sectionRecentlyPlayed;
    private View sectionRecentlyAdded;
    private View sectionPlaylists;
    private View layoutEmpty;
    private View tvSeeAllPlaylists;

    private SongAdapter recentlyPlayedAdapter;
    private SongAdapter recentlyAddedAdapter;
    private HomePlaylistAdapter playlistAdapter;
    private Set<Long> favoriteSongIds = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        libraryViewModel  = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);
        playlistViewModel  = new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class);

        tvGreeting          = view.findViewById(R.id.tvGreeting);
        rvRecentlyPlayed    = view.findViewById(R.id.rvRecentlyPlayed);
        rvRecentlyAdded     = view.findViewById(R.id.rvRecentlyAdded);
        rvPlaylists         = view.findViewById(R.id.rvPlaylists);
        sectionRecentlyPlayed = view.findViewById(R.id.sectionRecentlyPlayed);
        sectionRecentlyAdded  = view.findViewById(R.id.sectionRecentlyAdded);
        sectionPlaylists      = view.findViewById(R.id.sectionPlaylists);
        layoutEmpty           = view.findViewById(R.id.layoutEmpty);
        tvSeeAllPlaylists     = view.findViewById(R.id.tvSeeAllPlaylists);

        setGreeting();

        recentlyPlayedAdapter = new SongAdapter(this);
        rvRecentlyPlayed.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRecentlyPlayed.setAdapter(recentlyPlayedAdapter);

        recentlyAddedAdapter = new SongAdapter(this);
        rvRecentlyAdded.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRecentlyAdded.setAdapter(recentlyAddedAdapter);

        playlistAdapter = new HomePlaylistAdapter(this::onPlaylistClick);
        rvPlaylists.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPlaylists.setAdapter(playlistAdapter);

        // "See all" navigates to the Playlists tab
        if (tvSeeAllPlaylists != null) {
            tvSeeAllPlaylists.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.playlistsFragment));
        }

        View btnSettings = view.findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    Navigation.findNavController(v).navigate(R.id.action_home_to_settings));
        }

        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    View root = view.findViewById(R.id.layoutContent);
                    if (root != null) root.setBackground(tokens.createAmbientDrawable());
                    if (tvGreeting != null) tvGreeting.setTextColor(tokens.getTextSecondaryColor());
                    TextView tvAppName = view.findViewById(R.id.tvAppName);
                    if (tvAppName != null) tvAppName.setTextColor(tokens.getAccentColor());
                    if (btnSettings != null && btnSettings instanceof android.widget.ImageView) {
                        ((android.widget.ImageView) btnSettings).setColorFilter(tokens.getAccentColor());
                    }
                    if (tvSeeAllPlaylists != null && tvSeeAllPlaylists instanceof TextView) {
                        ((TextView) tvSeeAllPlaylists).setTextColor(tokens.getAccentColor());
                    }
                });

        observeData();

        // Clear recently played history with confirmation
        View btnClearHistory = view.findViewById(R.id.btnClearHistory);
        if (btnClearHistory != null) {
            btnClearHistory.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(requireContext())
                        .setMessage(R.string.clear_history_confirm)
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                libraryViewModel.clearPlayHistory())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }
    }

    private void onPlaylistClick(Playlist playlist) {
        Bundle args = new Bundle();
        args.putLong("playlistId", playlist.id);
        args.putString("playlistName", playlist.name);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_home_to_playlistDetail, args);
    }

    private void setGreeting() {
        if (tvGreeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 4 && hour < 12)       tvGreeting.setText(R.string.greeting_morning);
        else if (hour >= 12 && hour < 17) tvGreeting.setText(R.string.greeting_afternoon);
        else if (hour >= 17 && hour < 22) tvGreeting.setText(R.string.greeting_evening);
        else                               tvGreeting.setText(R.string.greeting_night);
    }

    private void observeData() {
        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                favoriteSongIds = new HashSet<>(ids);
                recentlyPlayedAdapter.setFavorites(favoriteSongIds);
                recentlyAddedAdapter.setFavorites(favoriteSongIds);
            }
        });

        // Recently Added
        libraryViewModel.getSongs().observe(getViewLifecycleOwner(), songs -> {
            if (songs == null || songs.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                if (sectionRecentlyAdded != null) sectionRecentlyAdded.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                if (sectionRecentlyAdded != null) sectionRecentlyAdded.setVisibility(View.VISIBLE);
                int limit = Math.min(songs.size(), 10);
                recentlyAddedAdapter.submitList(songs.subList(0, limit));
            }
        });

        // Recently Played
        libraryViewModel.getRecentlyPlayedSongs().observe(getViewLifecycleOwner(), songs -> {
            if (songs != null && !songs.isEmpty()) {
                if (sectionRecentlyPlayed != null) sectionRecentlyPlayed.setVisibility(View.VISIBLE);
                recentlyPlayedAdapter.submitList(songs);
            } else {
                if (sectionRecentlyPlayed != null) sectionRecentlyPlayed.setVisibility(View.GONE);
            }
        });

        // Playlists
        playlistViewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists != null && !playlists.isEmpty()) {
                if (sectionPlaylists != null) sectionPlaylists.setVisibility(View.VISIBLE);
                playlistAdapter.submitList(playlists);
                observePlaylistSongCounts(playlists);
            } else {
                if (sectionPlaylists != null) sectionPlaylists.setVisibility(View.GONE);
            }
        });
    }

    private void observePlaylistSongCounts(List<Playlist> playlists) {
        final Map<Long, Integer> counts = new HashMap<>();
        for (Playlist p : playlists) {
            playlistViewModel.getSongsForPlaylist(p.id)
                    .observe(getViewLifecycleOwner(), songs -> {
                        counts.put(p.id, songs != null ? songs.size() : 0);
                        playlistAdapter.setSongCounts(new HashMap<>(counts));
                    });
        }
    }

    // ===== SongAdapter.Listener =====

    @Override
    public void onSongClick(Song song, int position) {
        List<Song> currentList = recentlyAddedAdapter.getCurrentList();
        if (!currentList.isEmpty()) {
            playbackViewModel.playSongs(currentList, position);
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
    public void onAddToQueue(Song song) {
        playbackViewModel.addToQueue(song);
    }

    @Override
    public void onAddToPlaylist(Song song) {
        AddToPlaylistDialog.show(requireActivity(), requireView(), song, playlistViewModel);
    }

    @Override
    public void onRemoveFromPlaylist(Song song) {
        // Not applicable in home screen context
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

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeFragment extends Fragment implements SongAdapter.Listener {

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;
    private PlaylistViewModel playlistViewModel;

    private TextView tvGreeting;
    private RecyclerView rvRecentlyPlayed;
    private RecyclerView rvRecentlyAdded;
    private View sectionRecentlyPlayed;
    private View sectionRecentlyAdded;
    private View layoutEmpty;
    private View layoutContent;

    private SongAdapter recentlyPlayedAdapter;
    private SongAdapter recentlyAddedAdapter;
    private Set<Long> favoriteSongIds = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);
        playlistViewModel = new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class);

        tvGreeting = view.findViewById(R.id.tvGreeting);
        rvRecentlyPlayed = view.findViewById(R.id.rvRecentlyPlayed);
        rvRecentlyAdded = view.findViewById(R.id.rvRecentlyAdded);
        sectionRecentlyPlayed = view.findViewById(R.id.sectionRecentlyPlayed);
        sectionRecentlyAdded = view.findViewById(R.id.sectionRecentlyAdded);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        layoutContent = view.findViewById(R.id.layoutContent);

        setGreeting();

        recentlyPlayedAdapter = new SongAdapter(this);
        rvRecentlyPlayed.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRecentlyPlayed.setAdapter(recentlyPlayedAdapter);

        recentlyAddedAdapter = new SongAdapter(this);
        rvRecentlyAdded.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRecentlyAdded.setAdapter(recentlyAddedAdapter);

        observeData();
    }

    private void setGreeting() {
        if (tvGreeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 4 && hour < 12) {
            tvGreeting.setText(R.string.greeting_morning);
        } else if (hour >= 12 && hour < 17) {
            tvGreeting.setText(R.string.greeting_afternoon);
        } else if (hour >= 17 && hour < 22) {
            tvGreeting.setText(R.string.greeting_evening);
        } else {
            tvGreeting.setText(R.string.greeting_night);
        }
    }

    private void observeData() {
        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                favoriteSongIds = new HashSet<>(ids);
                recentlyPlayedAdapter.setFavorites(favoriteSongIds);
                recentlyAddedAdapter.setFavorites(favoriteSongIds);
            }
        });

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

        libraryViewModel.getRecentlyPlayedSongs().observe(getViewLifecycleOwner(), songs -> {
            if (songs != null && !songs.isEmpty()) {
                if (sectionRecentlyPlayed != null) sectionRecentlyPlayed.setVisibility(View.VISIBLE);
                recentlyPlayedAdapter.submitList(songs);
            } else {
                if (sectionRecentlyPlayed != null) sectionRecentlyPlayed.setVisibility(View.GONE);
            }
        });
    }

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
    }

    @Override
    public void onRemoveFromPlaylist(Song song) {
    }
}

package com.psthetech.swara.ui.miniplayer;

import android.content.Intent;
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
import androidx.navigation.fragment.NavHostFragment;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.util.ArtworkHelper;

/**
 * MiniPlayer — persistent playback bar shown above BottomNavigationView.
 *
 * Observes the Activity-scoped PlaybackViewModel.
 * Tapping opens NowPlayingFragment.
 * Play/Pause and Next buttons delegate to PlaybackViewModel.
 */
public class MiniPlayerFragment extends Fragment {

    private PlaybackViewModel playbackViewModel;
    private ImageView ivArtwork;
    private TextView tvTitle;
    private TextView tvArtist;
    private ImageView btnPlayPause;
    private ImageView btnNext;
    private View progressLine;
    private View rootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mini_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootView = view;
        ivArtwork = view.findViewById(R.id.ivArtwork);
        tvTitle = view.findViewById(R.id.tvTitle);
        tvArtist = view.findViewById(R.id.tvArtist);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnNext = view.findViewById(R.id.btnNext);
        progressLine = view.findViewById(R.id.progressLine);

        // Activity-scoped ViewModel
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        // Observe current song
        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), this::updateSong);

        // Observe playing state
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlayPause.setContentDescription(getString(playing ? R.string.pause : R.string.play));
        });

        // Observe progress for the top line
        playbackViewModel.getCurrentPositionMs().observe(getViewLifecycleOwner(), pos -> {
            Long duration = playbackViewModel.getDurationMs().getValue();
            if (duration != null && duration > 0) {
                float fraction = (float) pos / duration;
                ViewGroup parent = (ViewGroup) progressLine.getParent();
                if (parent != null) {
                    int totalWidth = rootView.getWidth();
                    if (totalWidth > 0) {
                        ViewGroup.LayoutParams lp = progressLine.getLayoutParams();
                        lp.width = (int)(totalWidth * fraction);
                        progressLine.setLayoutParams(lp);
                    }
                }
            }
        });

        // Tap the mini-player → open Now Playing.
        // MiniPlayerFragment lives outside the nav graph (in mini_player_container),
        // so we must reach the NavHostFragment via the host activity's FragmentManager.
        rootView.setOnClickListener(v -> {
            NavHostFragment navHostFragment = (NavHostFragment)
                    requireActivity().getSupportFragmentManager()
                            .findFragmentById(R.id.nav_host_fragment);
            if (navHostFragment != null) {
                navHostFragment.getNavController().navigate(R.id.nowPlayingFragment);
            }
        });

        btnPlayPause.setOnClickListener(v -> {
            Boolean isPlaying = playbackViewModel.getIsPlaying().getValue();
            if (Boolean.TRUE.equals(isPlaying)) {
                playbackViewModel.pause();
            } else {
                playbackViewModel.resume();
            }
        });

        btnNext.setOnClickListener(v -> playbackViewModel.skipToNext());
    }

    private void updateSong(@Nullable Song song) {
        if (song == null) return;
        tvTitle.setText(song.getTitle());
        tvArtist.setText(song.getArtist());
        ArtworkHelper.loadSongArt(requireContext(), song, ivArtwork);
    }
}

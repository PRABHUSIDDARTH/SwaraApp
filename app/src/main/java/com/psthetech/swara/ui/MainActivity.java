package com.psthetech.swara.ui;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.psthetech.swara.R;
import com.psthetech.swara.ui.miniplayer.MiniPlayerFragment;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.util.PermissionHelper;

/**
 * Swara V2 Single Activity.
 *
 * Responsibilities:
 *  - Hosts the NavController and all fragment destinations
 *  - Manages the persistent MiniPlayer fragment above the BottomNavigationView
 *  - Initializes the activity-scoped PlaybackViewModel (shared by all fragments)
 *  - Handles audio permission request results
 *  - Applies edge-to-edge insets
 *
 * Does NOT control playback directly — all playback goes through PlaybackViewModel → MediaController.
 */
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtworkHelper;

import androidx.activity.result.IntentSenderRequest;
import com.psthetech.swara.data.repository.SongDeletionManager;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private PlaybackViewModel playbackViewModel;
    private BottomNavigationView bottomNav;
    private View miniPlayerContainer;

    @Nullable private Song pendingArtworkSong;
    private ActivityResultLauncher<String> artworkPickerLauncher;

    @Nullable private Song pendingDeletionSong;
    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;
    private SongDeletionManager songDeletionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        songDeletionManager = new SongDeletionManager(this);

        artworkPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleImagePicked
        );

        deleteRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && pendingDeletionSong != null) {
                        songDeletionManager.onSystemDeleteConfirmed(pendingDeletionSong);
                        onSongDeletedSuccessfully(pendingDeletionSong);
                    } else if (pendingDeletionSong != null) {
                        Toast.makeText(this, R.string.song_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                    pendingDeletionSong = null;
                }
        );

        // Initialize Morphism & Theme engine
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().init(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Bottom nav handles its own bottom inset; apply top inset to the fragment container
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        bottomNav = findViewById(R.id.bottom_nav);
        miniPlayerContainer = findViewById(R.id.mini_player_container);

        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(this, tokens -> {
                    if (tokens == null) return;
                    View mainRoot = findViewById(R.id.main);
                    if (mainRoot != null) mainRoot.setBackgroundColor(tokens.getBackgroundColor());
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                            .applyToBottomNav(bottomNav, tokens);
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                            .applyToView(miniPlayerContainer, true, tokens);
                });

        // Set up Navigation Component.
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Initialize activity-scoped PlaybackViewModel (shared by all fragments)
        playbackViewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);

        // Show/hide mini-player based on active song
        playbackViewModel.getCurrentSong().observe(this, song -> {
            if (song != null) {
                showMiniPlayer();
            } else {
                hideMiniPlayer();
            }
        });

        // Request audio permission if needed
        if (!PermissionHelper.hasAudioPermission(this)) {
            PermissionHelper.requestAudioPermission(this);
        }
    }

    public void promptDeleteSong(Song song) {
        if (song == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_delete_song_title)
                .setMessage(getString(R.string.confirm_delete_song_message, song.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) -> performDeleteSong(song))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performDeleteSong(Song song) {
        songDeletionManager.deleteSong(song, new SongDeletionManager.DeletionCallback() {
            @Override
            public void onDeletionSuccess(Song song) {
                onSongDeletedSuccessfully(song);
            }

            @Override
            public void onDeletionFailed(Song song, String reason) {
                Toast.makeText(MainActivity.this, getString(R.string.song_delete_failed) + (reason != null ? ": " + reason : ""), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSystemPromptRequired(IntentSenderRequest request, Song song) {
                pendingDeletionSong = song;
                deleteRequestLauncher.launch(request);
            }
        });
    }

    private void onSongDeletedSuccessfully(Song song) {
        Toast.makeText(this, R.string.song_deleted, Toast.LENGTH_SHORT).show();
        if (playbackViewModel != null) {
            playbackViewModel.handleSongDeleted(song.getId());
        }
        LibraryViewModel libraryVm = new ViewModelProvider(this).get(LibraryViewModel.class);
        libraryVm.loadSongs();
    }

    public void promptEditArtwork(Song song) {
        if (song == null) return;
        ArtworkRepository repo = new ArtworkRepository(this);
        if (repo.hasCustomArtwork(song.getId())) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.edit_artwork)
                    .setItems(new CharSequence[]{
                            getString(R.string.change_artwork),
                            getString(R.string.reset_artwork)
                    }, (dialog, which) -> {
                        if (which == 0) {
                            launchImagePicker(song);
                        } else if (which == 1) {
                            resetArtwork(song);
                        }
                    })
                    .show();
        } else {
            launchImagePicker(song);
        }
    }

    private void launchImagePicker(Song song) {
        this.pendingArtworkSong = song;
        artworkPickerLauncher.launch("image/*");
    }

    private void resetArtwork(Song song) {
        ArtworkRepository repo = new ArtworkRepository(this);
        repo.removeCustomArtwork(song.getId());
        ArtworkHelper.notifyArtworkChanged(this);
        Toast.makeText(this, R.string.artwork_reset, Toast.LENGTH_SHORT).show();
        refreshPlaybackArtwork(song);
    }

    private void handleImagePicked(@Nullable Uri uri) {
        if (pendingArtworkSong == null || uri == null) return;
        ArtworkRepository repo = new ArtworkRepository(this);
        boolean saved = repo.saveCustomArtwork(pendingArtworkSong.getId(), uri);
        if (saved) {
            ArtworkHelper.notifyArtworkChanged(this);
            Toast.makeText(this, R.string.artwork_updated, Toast.LENGTH_SHORT).show();
            refreshPlaybackArtwork(pendingArtworkSong);
        } else {
            Toast.makeText(this, "Failed to update artwork", Toast.LENGTH_SHORT).show();
        }
        pendingArtworkSong = null;
    }

    private void refreshPlaybackArtwork(Song song) {
        if (playbackViewModel != null) {
            playbackViewModel.refreshCurrentSongArtwork();
        }
        // Force refresh active fragments
        recreateNavHostChild();
    }

    private void recreateNavHostChild() {
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null && navHostFragment.getChildFragmentManager().getFragments().size() > 0) {
            // Touch fragment view to trigger re-bind
            View view = navHostFragment.requireView();
            view.invalidate();
        }
    }

    private void showMiniPlayer() {
        if (miniPlayerContainer.getVisibility() == View.VISIBLE) return;
        // Add MiniPlayerFragment if not already added
        if (getSupportFragmentManager().findFragmentByTag("mini_player") == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.mini_player_container, new MiniPlayerFragment(), "mini_player");
            ft.commit();
        }
        miniPlayerContainer.setVisibility(View.VISIBLE);
        miniPlayerContainer.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(250)
                .start();
    }

    private void hideMiniPlayer() {
        if (miniPlayerContainer.getVisibility() == View.GONE) return;
        miniPlayerContainer.animate()
                .translationY(miniPlayerContainer.getHeight())
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> miniPlayerContainer.setVisibility(View.GONE))
                .start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQUEST_CODE_AUDIO) {
            // Permission result handled by the active fragment via its own observer
            // Fragments observe permission state via the activity recreate flow or direct check
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}

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

import com.google.android.material.navigation.NavigationBarView;
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

public class MainActivity extends AppCompatActivity
        implements com.psthetech.swara.ui.voice.VoiceUiCoordinator.Host {

    private NavController navController;
    private PlaybackViewModel playbackViewModel;
    private NavigationBarView bottomNav;
    private View miniPlayerContainer;

    @Nullable private Song pendingArtworkSong;
    private ActivityResultLauncher<String> artworkPickerLauncher;

    @Nullable private Song pendingDeletionSong;
    private ActivityResultLauncher<IntentSenderRequest> deleteRequestLauncher;
    private SongDeletionManager songDeletionManager;
    private boolean hadAudioPermission;

    @Nullable private com.psthetech.swara.ui.voice.VoiceUiCoordinator voiceCoordinator;
    private ActivityResultLauncher<String> micPermissionLauncher;

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

        micPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (voiceCoordinator != null) voiceCoordinator.onPermissionResult(granted);
                }
        );

        // Initialize Morphism & Theme engine
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().init(this);

        EdgeToEdge.enable(this);
        // Activity-scoped PlaybackViewModel (shared by all fragments) and tap-to-talk voice
        // commands (mic, listening sheet, permission flow). Created before
        // setContentView so fragments restored during inflation can already reach it.
        playbackViewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        voiceCoordinator = new com.psthetech.swara.ui.voice.VoiceUiCoordinator(
                this, playbackViewModel, micPermissionLauncher);

        setContentView(R.layout.activity_main);

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // Include the display cutout so a notch in landscape never clips the rail or content.
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // Keep the floating controls above both gesture and three-button navigation.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets.inset(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
        });

        bottomNav = findViewById(R.id.bottom_nav);
        miniPlayerContainer = findViewById(R.id.mini_player_container);

        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(this, tokens -> {
                    if (tokens == null) return;
                    View mainRoot = findViewById(R.id.main);
                    if (mainRoot != null) mainRoot.setBackground(tokens.createAmbientDrawable());
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                            .applyToBottomNav(bottomNav, tokens);
                });

        // Set up Navigation Component.
        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(bottomNav, navController);
        setupHeySwaraNavItem();

        // The activity-scoped PlaybackViewModel (shared by all fragments) was created above,
        // before setContentView, together with the voice coordinator.

        // Show/hide mini-player based on active song
        playbackViewModel.getCurrentSong().observe(this, song -> {
            hasSong = song != null;
            updateChrome();
        });

        // Landscape: the rail and mini player step aside on the expanded Now Playing screen.
        // The listener fires immediately with the current destination, so this also restores the
        // right state after rotation or Back.
        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            onNowPlaying = destination.getId() == R.id.nowPlayingFragment;
            updateChrome();
        });

        // Request audio permission if needed
        hadAudioPermission = PermissionHelper.hasAudioPermission(this);
        if (!hadAudioPermission) {
            PermissionHelper.requestAudioPermission(this);
        }

        checkIntentForNowPlaying(getIntent());
    }

    /**
     * Hey Swara (centre nav item) is an action, not a destination. The selection listener
     * returns false for it, so it can never become the checked tab (also after rotation or back
     * navigation); every other item keeps the stock NavigationUI behaviour. When the voice
     * models are unavailable the item stays visible but disabled.
     */
    private void setupHeySwaraNavItem() {
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.heySwaraItem) {
                if (voiceCoordinator != null) voiceCoordinator.onMicTapped();
                return false;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });
        if (voiceCoordinator != null) {
            voiceCoordinator.micVisible().observe(this, visible -> {
                boolean enabled = Boolean.TRUE.equals(visible);
                android.view.MenuItem hey = bottomNav.getMenu().findItem(R.id.heySwaraItem);
                if (hey != null) hey.setEnabled(enabled);
                View heyView = bottomNav.findViewById(R.id.heySwaraItem);
                if (heyView != null) heyView.setAlpha(enabled ? 1f : 0.4f);
            });
        } else {
            android.view.MenuItem hey = bottomNav.getMenu().findItem(R.id.heySwaraItem);
            if (hey != null) hey.setEnabled(false);
        }
    }

    public static final String EXTRA_OPEN_NOW_PLAYING = "extra_open_now_playing";

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkIntentForNowPlaying(intent);
    }

    private void checkIntentForNowPlaying(@Nullable android.content.Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false)) {
            openNowPlaying();
        }
    }

    public void openNowPlaying() {
        if (navController != null) {
            try {
                navController.navigate(R.id.nowPlayingFragment);
            } catch (Exception ignored) {}
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

    public void promptEditSongInfo(Song song) {
        if (song == null) return;
        com.psthetech.swara.ui.metadata.EditSongInfoBottomSheet sheet =
                com.psthetech.swara.ui.metadata.EditSongInfoBottomSheet.newInstance(song);
        sheet.show(getSupportFragmentManager(), com.psthetech.swara.ui.metadata.EditSongInfoBottomSheet.TAG);
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
            Song current = playbackViewModel.getCurrentSong().getValue();
            if (current != null && (song == null || current.getId() == song.getId())) {
                boolean isPlaying = Boolean.TRUE.equals(playbackViewModel.getIsPlaying().getValue());
                com.psthetech.swara.widget.SwaraWidgetUpdater.pushUpdate(this, current, isPlaying);
            }
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

    private boolean hasSong = false;
    private boolean onNowPlaying = false;

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /** Single place that decides whether the nav and the mini player are visible. */
    private void updateChrome() {
        boolean hideForNowPlaying = onNowPlaying && isLandscape();
        if (bottomNav != null && isLandscape()) {
            bottomNav.setVisibility(hideForNowPlaying ? View.GONE : View.VISIBLE);
        }
        if (hasSong && !hideForNowPlaying) {
            showMiniPlayer();
        } else if (hideForNowPlaying) {
            miniPlayerContainer.animate().cancel();
            miniPlayerContainer.setVisibility(View.GONE);
        } else {
            hideMiniPlayer();
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
        if (requestCode != PermissionHelper.REQUEST_CODE_AUDIO) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            hadAudioPermission = true;
            // The first library query ran before the grant and came back empty.
            new ViewModelProvider(this).get(LibraryViewModel.class).refreshAll();
            com.psthetech.swara.vibe.android.MoodAnalysisWorker.enqueueNow(this);
        } else {
            showPermissionDeniedDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Permission may have been granted (or revoked) from system Settings while away.
        boolean has = PermissionHelper.hasAudioPermission(this);
        if (has && !hadAudioPermission) {
            new ViewModelProvider(this).get(LibraryViewModel.class).refreshAll();
            com.psthetech.swara.vibe.android.MoodAnalysisWorker.enqueueNow(this);
        }
        hadAudioPermission = has;
        if (voiceCoordinator != null) voiceCoordinator.onResume();
    }

    @Override
    protected void onPause() {
        if (voiceCoordinator != null) voiceCoordinator.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (voiceCoordinator != null) {
            voiceCoordinator.onDestroy();
            voiceCoordinator = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public com.psthetech.swara.ui.voice.VoiceUiCoordinator getVoiceCoordinator() {
        return voiceCoordinator;
    }

    private void showPermissionDeniedDialog() {
        boolean canAskAgain = PermissionHelper.shouldShowRationale(this);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_title)
                .setMessage(canAskAgain ? R.string.permission_rationale : R.string.permission_denied_subtitle)
                .setNegativeButton(R.string.cancel, null);
        if (canAskAgain) {
            builder.setPositiveButton(R.string.grant_permission,
                    (d, w) -> PermissionHelper.requestAudioPermission(this));
        } else {
            builder.setPositiveButton(R.string.open_settings, (d, w) -> {
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            });
        }
        builder.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}

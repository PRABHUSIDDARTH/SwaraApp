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
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private PlaybackViewModel playbackViewModel;
    private BottomNavigationView bottomNav;
    private View miniPlayerContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
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

        // Set up Navigation Component.
        // NOTE: Navigation.findNavController(Activity, id) does NOT work with
        // FragmentContainerView hosts (Navigation ≥ 2.3). The NavController is
        // attached to the NavHostFragment's child view, not to the container,
        // so we must retrieve it via the FragmentManager.
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

package com.psthetech.swara.ui.playlists;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.snackbar.Snackbar;
import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.repository.PlaylistRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable "Add to Playlist" dialog utility.
 *
 * Shows a list of existing playlists + "New playlist…" option.
 * Handles:
 *  - Selecting an existing playlist → duplicate check → add → Snackbar feedback
 *  - "New playlist…" → name dialog → create → add song
 *
 * Must be called on the main thread with a live Activity.
 * No DB access on main thread — all work done via PlaylistViewModel's background executor.
 */
public class AddToPlaylistDialog {

    private AddToPlaylistDialog() {}

    /**
     * Shows the "Add to playlist" sheet.
     *
     * @param activity      Host activity (for dialog theming + Snackbar anchor)
     * @param anchorView    View used as Snackbar anchor (pass the root view)
     * @param song          The song to add
     * @param viewModel     The PlaylistViewModel (activity-scoped)
     */
    public static void show(FragmentActivity activity,
                            View anchorView,
                            Song song,
                            PlaylistViewModel viewModel) {
        // Load all playlists in background, then show dialog on main thread
        viewModel.loadAllPlaylistsBackground(playlists -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                showDialog(activity, anchorView, song, viewModel, playlists);
            }
        });
    }

    private static void showDialog(FragmentActivity activity,
                                   View anchorView,
                                   Song song,
                                   PlaylistViewModel viewModel,
                                   List<Playlist> playlists) {
        // Build display items: existing playlists + "New playlist…"
        List<String> labels = new ArrayList<>();
        for (Playlist p : playlists) {
            labels.add(p.name);
        }
        labels.add(activity.getString(R.string.new_playlist_ellipsis));

        CharSequence[] items = labels.toArray(new CharSequence[0]);

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.add_to_playlist))
                .setItems(items, (dialog, which) -> {
                    if (which < playlists.size()) {
                        // Existing playlist
                        Playlist selected = playlists.get(which);
                        addToExistingPlaylist(anchorView, song, selected, viewModel, activity);
                    } else {
                        // "New playlist…"
                        showCreateAndAddDialog(activity, anchorView, song, viewModel);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void addToExistingPlaylist(View anchorView,
                                              Song song,
                                              Playlist playlist,
                                              PlaylistViewModel viewModel,
                                              FragmentActivity activity) {
        viewModel.addSongToPlaylistChecked(
                playlist.id,
                playlist.name,
                song,
                new PlaylistRepository.AddSongCallback() {
                    @Override
                    public void onAdded(String playlistName) {
                        showSnackbar(anchorView,
                                activity.getString(R.string.song_added_to_playlist, playlistName));
                    }

                    @Override
                    public void onDuplicate(String playlistName) {
                        showSnackbar(anchorView,
                                activity.getString(R.string.song_already_in_playlist, playlistName));
                    }
                });
    }

    private static void showCreateAndAddDialog(FragmentActivity activity,
                                               View anchorView,
                                               Song song,
                                               PlaylistViewModel viewModel) {
        EditText input = new EditText(activity);
        input.setHint(R.string.playlist_name_hint);
        int margin = (int) (16 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(margin, margin, margin, margin);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.create_playlist)
                .setView(input)
                .setPositiveButton(R.string.create, (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(activity, R.string.playlist_name_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.createPlaylistAndAddSong(name, song, playlistId -> {
                        showSnackbar(anchorView,
                                activity.getString(R.string.song_added_to_playlist, name));
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void showSnackbar(View anchor, String message) {
        if (anchor != null) {
            Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();
        }
    }
}

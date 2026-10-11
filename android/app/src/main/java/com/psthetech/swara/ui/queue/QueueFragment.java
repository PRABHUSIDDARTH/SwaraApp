package com.psthetech.swara.ui.queue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.QueueAdapter;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;

/**
 * Queue bottom sheet — shows the current playback queue.
 *
 * Features:
 *  - Tap a song to skip to it
 *  - Drag the handle on the right to reorder (persisted to ExoPlayer)
 *  - "Clear queue" button
 */
public class QueueFragment extends BottomSheetDialogFragment
        implements QueueAdapter.OnQueueItemClickListener,
                   QueueAdapter.OnStartDragListener,
                   QueueAdapter.OnItemMoveListener {

    private PlaybackViewModel playbackViewModel;
    private RecyclerView recyclerView;
    private View btnClearQueue;
    private QueueAdapter queueAdapter;
    private ItemTouchHelper itemTouchHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        recyclerView  = view.findViewById(R.id.recyclerView);
        btnClearQueue = view.findViewById(R.id.btnClearQueue);

        // Build adapter with all three callbacks wired to this fragment
        queueAdapter = new QueueAdapter(this, this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(queueAdapter);

        // Attach ItemTouchHelper for drag-to-reorder via the drag handle
        QueueAdapter.DragCallback dragCallback = new QueueAdapter.DragCallback(queueAdapter);
        itemTouchHelper = new ItemTouchHelper(dragCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        if (btnClearQueue != null) {
            btnClearQueue.setOnClickListener(v -> playbackViewModel.clearQueue());
        }

        // Apply design tokens to queue sheet
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    getView().setBackground(tokens.createSurfaceVariantDrawable(requireContext()));
                    // Clear queue button
                    if (btnClearQueue instanceof android.widget.TextView) {
                        ((android.widget.TextView) btnClearQueue).setTextColor(tokens.getAccentColor());
                    } else if (btnClearQueue instanceof android.widget.ImageView) {
                        ((android.widget.ImageView) btnClearQueue).setColorFilter(tokens.getAccentColor());
                    }
                });

        playbackViewModel.getQueue().observe(getViewLifecycleOwner(), songs -> {
            if (songs != null) {
                queueAdapter.submitList(songs);
            }
        });

        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                queueAdapter.setCurrentPlayingSongId(song.getId());
            }
        });
    }

    // ===== OnQueueItemClickListener =====

    @Override
    public void onItemClick(int position, Song song) {
        playbackViewModel.skipToQueueItem(position);
    }

    // ===== OnStartDragListener — called from the drag handle touch =====

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

    // ===== OnItemMoveListener — called after each swap during drag =====

    @Override
    public void onItemMove(int fromPosition, int toPosition) {
        // Persist reorder to ExoPlayer via QueueManager
        playbackViewModel.moveQueueItem(fromPosition, toPosition);
    }
}

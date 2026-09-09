package com.psthetech.swara.ui.queue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.QueueAdapter;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;

public class QueueFragment extends BottomSheetDialogFragment implements QueueAdapter.OnQueueItemClickListener {

    private PlaybackViewModel playbackViewModel;
    private RecyclerView recyclerView;
    private View btnClearQueue;
    private QueueAdapter queueAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        recyclerView = view.findViewById(R.id.recyclerView);
        btnClearQueue = view.findViewById(R.id.btnClearQueue);

        queueAdapter = new QueueAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(queueAdapter);

        if (btnClearQueue != null) {
            btnClearQueue.setOnClickListener(v -> playbackViewModel.clearQueue());
        }

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

    @Override
    public void onItemClick(int position, Song song) {
        playbackViewModel.skipToQueueItem(position);
    }
}

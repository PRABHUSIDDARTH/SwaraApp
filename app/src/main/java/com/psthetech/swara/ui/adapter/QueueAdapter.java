package com.psthetech.swara.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.ArtworkHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for the playback queue sheet.
 *
 * Supports:
 *  - Tap-to-skip (OnQueueItemClickListener)
 *  - Drag-to-reorder via the drag handle (OnStartDragListener)
 *  - "Now playing" highlight on the current song
 *
 * NOTE: The adapter owns an internal mutable list for drag purposes so that
 * real-time swap feedback is smooth. The authoritative order lives in ExoPlayer.
 */
public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    // ===== Callback interfaces =====

    public interface OnQueueItemClickListener {
        void onItemClick(int position, Song song);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public interface OnItemMoveListener {
        void onItemMove(int fromPosition, int toPosition);
    }

    // ===== DiffUtil =====

    private static final DiffUtil.ItemCallback<Song> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Song>() {
                @Override
                public boolean areItemsTheSame(@NonNull Song o, @NonNull Song n) {
                    // In a queue the same song can appear multiple times; use position identity
                    // so DiffUtil treats each slot as distinct → no animation glitch on reorder.
                    return o.getId() == n.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Song o, @NonNull Song n) {
                    return o.getTitle().equals(n.getTitle())
                            && o.getArtist().equals(n.getArtist());
                }
            };

    // ===== State =====

    private final OnQueueItemClickListener clickListener;
    private final OnStartDragListener dragListener;
    private final OnItemMoveListener moveListener;
    private long currentPlayingSongId = -1;

    /** Mutable working copy used for in-place drag swaps. */
    private List<Song> items = new ArrayList<>();

    public QueueAdapter(OnQueueItemClickListener clickListener,
                        OnStartDragListener dragListener,
                        OnItemMoveListener moveListener) {
        this.clickListener = clickListener;
        this.dragListener  = dragListener;
        this.moveListener  = moveListener;
        setHasStableIds(false); // positions change on drag; stable IDs would confuse RecyclerView
    }

    // Legacy single-listener constructor for backward compat
    public QueueAdapter(OnQueueItemClickListener listener) {
        this(listener, null, null);
    }

    // ===== Data =====

    public void submitList(List<Song> newItems) {
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** Called by ItemTouchHelper during drag; swaps items in-place for live feedback. */
    public boolean onItemMove(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return false;
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
        if (moveListener != null) moveListener.onItemMove(from, to);
        return true;
    }

    public void setCurrentPlayingSongId(long songId) {
        this.currentPlayingSongId = songId;
        notifyDataSetChanged();
    }

    public List<Song> getCurrentList() {
        return Collections.unmodifiableList(items);
    }

    // ===== RecyclerView.Adapter =====

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue_song, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        Song song = items.get(position);
        boolean isPlaying = song.getId() == currentPlayingSongId;
        holder.bind(song, position, isPlaying, clickListener, dragListener);
    }

    @Override
    public void onViewRecycled(@NonNull QueueViewHolder holder) {
        super.onViewRecycled(holder);
        ArtworkHelper.clear(holder.itemView.getContext(), holder.queueSongArt);
        holder.queueSongArt.setImageDrawable(null);
        holder.itemView.setBackgroundResource(R.drawable.ripple_item);
        holder.queueSongTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        holder.itemView.setScaleX(1.0f);
        holder.itemView.setScaleY(1.0f);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setTranslationX(0f);
        holder.itemView.setTranslationY(0f);
    }

    private static void applyPlaybackGlow(QueueViewHolder holder, boolean isPlaying, DesignTokens tokens) {
        if (isPlaying && tokens != null) {
            GradientDrawable glow = new GradientDrawable();
            glow.setShape(GradientDrawable.RECTANGLE);
            glow.setColor(tokens.getPlaybackSurfaceColor());
            float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
            glow.setCornerRadius(tokens.getCornerRadiusDp() * density);
            glow.setStroke(Math.max(1, Math.round(1.5f * density)), tokens.getPlaybackStrokeColor());
            holder.itemView.setBackground(glow);
        } else {
            holder.itemView.setBackground(null);
        }
    }

    // ===== ViewHolder =====

    static class QueueViewHolder extends RecyclerView.ViewHolder {
        final ImageView queuePlayingIndicator;
        final ImageView queueSongArt;
        final TextView  queueSongTitle;
        final TextView  queueSongArtist;
        final ImageView ivDragHandle;

        QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            queuePlayingIndicator = itemView.findViewById(R.id.queuePlayingIndicator);
            queueSongArt          = itemView.findViewById(R.id.queueSongArt);
            queueSongTitle        = itemView.findViewById(R.id.queueSongTitle);
            queueSongArtist       = itemView.findViewById(R.id.queueSongArtist);
            ivDragHandle          = itemView.findViewById(R.id.ivDragHandle);
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(Song song, int position, boolean isPlaying,
                  OnQueueItemClickListener clickListener,
                  OnStartDragListener dragListener) {

            queueSongTitle.setText(song.getTitle());
            queueSongArtist.setText(song.getArtist());
            queueSongTitle.setTypeface(null, isPlaying ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                queueSongTitle.setTextColor(
                        isPlaying ? tokens.getReadableAccentColor() : tokens.getTextPrimaryColor());
                queueSongArtist.setTextColor(tokens.getTextSecondaryColor());
                if (queuePlayingIndicator != null) {
                    queuePlayingIndicator.setColorFilter(tokens.getPlaybackIconColor());
                }
                if (ivDragHandle != null) {
                    ivDragHandle.setColorFilter(tokens.getTextSecondaryColor());
                }
            }

            applyPlaybackGlow(this, isPlaying, tokens);

            if (queuePlayingIndicator != null) {
                queuePlayingIndicator.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
            }

            ArtworkHelper.loadSongArt(itemView.getContext(), song, queueSongArt);

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(getAdapterPosition(), song);
                }
            });

            if (ivDragHandle != null && dragListener != null) {
                ivDragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        dragListener.onStartDrag(this);
                    }
                    return false;
                });
            }
        }
    }

    // ===== ItemTouchHelper callback (used by QueueFragment) =====

    public static class DragCallback extends ItemTouchHelper.SimpleCallback {

        private final QueueAdapter adapter;

        public DragCallback(QueueAdapter adapter) {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            this.adapter = adapter;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv,
                              @NonNull RecyclerView.ViewHolder from,
                              @NonNull RecyclerView.ViewHolder to) {
            return adapter.onItemMove(from.getAdapterPosition(), to.getAdapterPosition());
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // No swipe-to-dismiss in the queue
        }

        @Override
        public boolean isLongPressDragEnabled() {
            // Drag only via the handle; long-press drag is disabled to keep tap-to-skip reliable.
            return false;
        }
    }
}

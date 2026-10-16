package com.psthetech.swara.util;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SleepTimerManager — Singleton sleep timer for Swara.
 *
 * Modes:
 *  - Fixed duration (15/30/45/60 minutes): after timeout, pauses playback
 *  - End-of-song: the player pauses at the end of the current item (before the next starts)
 *
 * Pausing is performed by {@link PlaybackHooks}, registered by the playback service, so the
 * timer still works when the UI that started it has been destroyed. The optional
 * {@link OnTimerFinishedListener} is only a notification.
 *
 * Observers are notified via LiveData:
 *  - remainingMs: countdown in milliseconds (0 when idle)
 *  - active: whether a timer is currently running
 */
public class SleepTimerManager {

    public interface OnTimerFinishedListener {
        void onSleepTimerFinished();
    }

    /** Implemented by the playback service; all calls happen on the main thread. */
    public interface PlaybackHooks {
        void pauseNow();
        void setPauseAtEndOfCurrentItem(boolean pause);
    }

    // ===== Singleton =====

    private static SleepTimerManager instance;

    public static synchronized SleepTimerManager getInstance() {
        if (instance == null) {
            instance = new SleepTimerManager();
        }
        return instance;
    }

    // ===== State =====

    private final MutableLiveData<Long>    remainingMs   = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> active        = new MutableLiveData<>(false);

    // Plain atomic flag for isActive() so JVM unit tests don't require a Looper
    private final AtomicBoolean activeState = new AtomicBoolean(false);

    @Nullable private CountDownTimer countDownTimer;
    @Nullable private OnTimerFinishedListener listener;
    @Nullable private PlaybackHooks playbackHooks;

    private boolean sleepAfterSong = false;
    private Handler mainHandler;

    private SleepTimerManager() {
        try {
            mainHandler = new Handler(Looper.getMainLooper());
        } catch (Throwable t) {
            mainHandler = null;
        }
    }

    // ===== Public API =====

    /** Registered by the playback service in onCreate; cleared (null) in onDestroy. */
    public void setPlaybackHooks(@Nullable PlaybackHooks hooks) {
        this.playbackHooks = hooks;
        if (hooks != null && sleepAfterSong) hooks.setPauseAtEndOfCurrentItem(true);
    }

    /** Start a fixed-duration countdown (minutes). */
    public void startTimer(int minutes, @Nullable OnTimerFinishedListener listener) {
        cancelTimer();
        this.listener = listener;
        this.sleepAfterSong = false;
        long durationMs = (long) minutes * 60 * 1000;

        countDownTimer = new CountDownTimer(durationMs, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (mainHandler != null) {
                    remainingMs.postValue(millisUntilFinished);
                }
            }

            @Override
            public void onFinish() {
                activeState.set(false);
                if (mainHandler != null) {
                    remainingMs.postValue(0L);
                    active.postValue(false);
                }
                countDownTimer = null;
                if (playbackHooks != null) playbackHooks.pauseNow();
                OnTimerFinishedListener l = SleepTimerManager.this.listener;
                SleepTimerManager.this.listener = null;
                if (l != null) l.onSleepTimerFinished();
            }
        }.start();

        activeState.set(true);
        if (mainHandler != null) {
            active.postValue(true);
        }
    }

    /** Request sleep at the end of the current song. */
    public void sleepAfterCurrentSong(@Nullable OnTimerFinishedListener listener) {
        cancelTimer();
        this.listener = listener;
        this.sleepAfterSong = true;
        activeState.set(true);
        if (playbackHooks != null) playbackHooks.setPauseAtEndOfCurrentItem(true);
        if (mainHandler != null) {
            // Show a symbolic "-1" as remaining to indicate end-of-song mode
            remainingMs.postValue(-1L);
            active.postValue(true);
        }
    }

    /**
     * Called by the playback service when the player paused at the end of the current item
     * (or the queue ended). If end-of-song mode is armed, clears the timer and notifies.
     */
    public void onCurrentItemEnded() {
        if (!sleepAfterSong) return;
        OnTimerFinishedListener l = listener;
        cancelTimer();
        if (l != null) l.onSleepTimerFinished();
    }

    /** Cancel any active timer. */
    public void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (sleepAfterSong && playbackHooks != null) playbackHooks.setPauseAtEndOfCurrentItem(false);
        sleepAfterSong = false;
        activeState.set(false);
        listener = null;
        if (mainHandler != null) {
            remainingMs.postValue(0L);
            active.postValue(false);
        }
    }

    public boolean isActive() {
        return activeState.get();
    }

    public boolean isSleepAfterSong() {
        return sleepAfterSong;
    }

    public LiveData<Long>    getRemainingMs() { return remainingMs; }
    public LiveData<Boolean> getActive()      { return active; }
}

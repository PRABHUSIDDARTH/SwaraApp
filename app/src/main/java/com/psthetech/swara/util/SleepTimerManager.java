package com.psthetech.swara.util;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * SleepTimerManager — Singleton sleep timer for Swara.
 *
 * Modes:
 *  - Fixed duration (15/30/45/60 minutes): after timeout, fires onTimerFinished
 *  - End-of-song: the playback controller polls isSleepAfterSong() and calls finishSleep()
 *
 * Observers are notified via LiveData:
 *  - remainingMs: countdown in milliseconds (0 when idle)
 *  - active: whether a timer is currently running
 */
public class SleepTimerManager {

    public interface OnTimerFinishedListener {
        void onSleepTimerFinished();
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

    @Nullable private CountDownTimer countDownTimer;
    @Nullable private OnTimerFinishedListener listener;

    private boolean sleepAfterSong = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SleepTimerManager() {}

    // ===== Public API =====

    /** Start a fixed-duration countdown (minutes). */
    public void startTimer(int minutes, @Nullable OnTimerFinishedListener listener) {
        cancelTimer();
        this.listener = listener;
        this.sleepAfterSong = false;
        long durationMs = (long) minutes * 60 * 1000;

        countDownTimer = new CountDownTimer(durationMs, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMs.postValue(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                remainingMs.postValue(0L);
                active.postValue(false);
                countDownTimer = null;
                if (SleepTimerManager.this.listener != null) {
                    SleepTimerManager.this.listener.onSleepTimerFinished();
                }
            }
        }.start();

        active.setValue(true);
    }

    /** Request sleep at the end of the current song. */
    public void sleepAfterCurrentSong(@Nullable OnTimerFinishedListener listener) {
        cancelTimer();
        this.listener = listener;
        this.sleepAfterSong = true;
        // Show a symbolic "99:00" as remaining to indicate end-of-song mode
        remainingMs.setValue(-1L);
        active.setValue(true);
    }

    /**
     * Called by the playback layer when a song transition occurs.
     * If sleepAfterSong mode is active, fires the listener and clears the timer.
     */
    public void onSongTransition() {
        if (!sleepAfterSong) return;
        cancelTimer();
        if (listener != null) {
            mainHandler.post(() -> listener.onSleepTimerFinished());
        }
    }

    /** Cancel any active timer. */
    public void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        sleepAfterSong = false;
        listener = null;
        remainingMs.postValue(0L);
        active.postValue(false);
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active.getValue());
    }

    public boolean isSleepAfterSong() {
        return sleepAfterSong;
    }

    public LiveData<Long>    getRemainingMs() { return remainingMs; }
    public LiveData<Boolean> getActive()      { return active; }
}

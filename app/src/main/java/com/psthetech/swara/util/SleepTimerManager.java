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

    // Plain atomic flag for isActive() so JVM unit tests don't require a Looper
    private final AtomicBoolean activeState = new AtomicBoolean(false);

    @Nullable private CountDownTimer countDownTimer;
    @Nullable private OnTimerFinishedListener listener;

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
                if (SleepTimerManager.this.listener != null) {
                    SleepTimerManager.this.listener.onSleepTimerFinished();
                }
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
        if (mainHandler != null) {
            // Show a symbolic "-1" as remaining to indicate end-of-song mode
            remainingMs.postValue(-1L);
            active.postValue(true);
        }
    }

    /**
     * Called by the playback layer when a song transition occurs.
     * If sleepAfterSong mode is active, fires the listener and clears the timer.
     */
    public void onSongTransition() {
        if (!sleepAfterSong) return;
        cancelTimer();
        if (listener != null) {
            if (mainHandler != null) {
                mainHandler.post(() -> listener.onSleepTimerFinished());
            } else {
                listener.onSleepTimerFinished();
            }
        }
    }

    /** Cancel any active timer. */
    public void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
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

package com.psthetech.swara;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PlayerActivity extends AppCompatActivity {

    private MusicService musicService;
    private boolean isBound = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvTitle, tvArtist, tvCurrentTime, tvTotalTime;
    private SeekBar seekBar;
    private Button btnPlayPause, btnNext, btnPrev;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        // 1. initialize all views with findViewById here
        tvTitle = findViewById(R.id.tvCurrentTitle);
        tvArtist = findViewById(R.id.tvCurrentArtist);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnBack = findViewById(R.id.btnBack);


        // 2. btnBack click → finish() to go back
        btnBack.setOnClickListener(v -> finish());

    }

    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;

            // 3. set title and artist
            Song current = musicService.getCurrentSong();
            tvTitle.setText(current.getTitle());
            tvArtist.setText(current.getArtist());

            // 4. set total time
            seekBar.setMax((int) current.getDuration());
            tvTotalTime.setText(formatTime(current.getDuration()));

            // 5. start seekbar updates
            handler.post(updateSeekBar);

            // 6. buttons
            btnPlayPause.setText(musicService.isPlaying() ? "⏸" : "▶");

            btnPlayPause.setOnClickListener(v -> {
                if (musicService.isPlaying()) {
                    musicService.pauseSong();
                    btnPlayPause.setText("▶");
                } else {
                    musicService.resumeSong();
                    btnPlayPause.setText("⏸");
                }
            });

            btnNext.setOnClickListener(v -> {
                musicService.playNext();
                updateSongInfo();
            });

            btnPrev.setOnClickListener(v -> {
                musicService.playPrevious();
                updateSongInfo();
            });

            // seekbar drag
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) musicService.seekTo(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (isBound && musicService != null) {
                seekBar.setProgress(musicService.getCurrentPosition());
                tvCurrentTime.setText(formatTime(musicService.getCurrentPosition()));
                handler.postDelayed(this, 1000);
            }
        }
    };

    // helper method to format milliseconds to mm:ss
    private String formatTime(long ms) {
        long minutes = (ms / 1000) / 60;
        long seconds = (ms / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
    private void updateSongInfo() {
        Song current = musicService.getCurrentSong();
        tvTitle.setText(current.getTitle());
        tvArtist.setText(current.getArtist());
        seekBar.setMax((int) current.getDuration());
        tvTotalTime.setText(formatTime(current.getDuration()));
        btnPlayPause.setText("⏸");
    }
}
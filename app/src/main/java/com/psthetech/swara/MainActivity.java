package com.psthetech.swara;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;



public class MainActivity extends AppCompatActivity {
    private SeekBar mainSeekBar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvCurrentTitle;
    private TextView tvCurrentArtist;
    private Button btnPlayPause;
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private MusicService musicService;
    private boolean isBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        if (ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED) {
            Log.d("Swara", "Permission already granted");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    PERMISSION_REQUEST_CODE);
        }
    }

    protected List<Song> loadSongs() {
        List<Song> songs = new ArrayList<>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
        };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
        int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        int artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
        int albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
        int durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
        int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        while (cursor.moveToNext()) {
            String title = cursor.getString(titleIndex);
            String artist = cursor.getString(artistIndex);
            long id = cursor.getLong(idIndex);
            long duration = cursor.getLong(durationIndex);
            String album = cursor.getString(albumIndex);
            String path = cursor.getString(dataIndex);
            songs.add(new Song(id, title, artist, album, duration, path));
        }
        cursor.close();
        Log.d("Swara", "Total songs: " + songs.size());
        return songs;
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder musicBinder = (MusicService.MusicBinder) service;
            musicService = musicBinder.getService();
            isBound = true;
            tvCurrentTitle = findViewById(R.id.tvCurrentTitle);
            tvCurrentArtist = findViewById(R.id.tvCurrentArtist);
            recyclerView = findViewById(R.id.recyclerView);
            btnPlayPause = findViewById(R.id.btnPlayPause);
            Button btnNext = findViewById(R.id.btnNext);
            Button btnPrevious = findViewById(R.id.btnPrevious);
            List<Song> songs = loadSongs();
            musicService.setQueue(songs, 0);
            recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            songAdapter = new SongAdapter(songs, song -> {
                int index = songs.indexOf(song);
                if (musicService.getCurrentSong() == null ||
                        !musicService.getCurrentSong().getPath().equals(song.getPath())) {
                    musicService.setQueue(songs, index);
                    musicService.playSong(song);
                }
                btnPlayPause.setText("Pause");
                tvCurrentTitle.setText(song.getTitle());
                tvCurrentArtist.setText(song.getArtist());
                startActivity(new Intent(MainActivity.this, PlayerActivity.class));
            });
            recyclerView.setAdapter(songAdapter);
            EditText etSearch = findViewById(R.id.etSearch);
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    songAdapter.filter(s.toString());
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
            if (!musicService.isPlaying() && !musicService.isPaused()) {
                musicService.playSong(songs.get(0));
            }
            btnNext.setOnClickListener(v -> {
                musicService.playNext();
                btnPlayPause.setText("Pause");
                tvCurrentTitle.setText(musicService.getCurrentSong().getTitle());
                tvCurrentArtist.setText(musicService.getCurrentSong().getArtist());
            });
            btnPrevious.setOnClickListener(v -> {
                musicService.playPrevious();
                btnPlayPause.setText("Pause");
                tvCurrentTitle.setText(musicService.getCurrentSong().getTitle());
                tvCurrentArtist.setText(musicService.getCurrentSong().getArtist());
            });
            btnPlayPause.setOnClickListener(v -> {
                if (musicService.isPlaying()) {
                    musicService.pauseSong();
                    btnPlayPause.setText("Play");
                } else {
                    musicService.resumeSong();
                    btnPlayPause.setText("Pause");
                }
            });
            tvCurrentTitle.setText(songs.get(0).getTitle());
            tvCurrentArtist.setText(songs.get(0).getArtist());
            mainSeekBar = findViewById(R.id.mainSeekBar);

// update seekbar every second
            Runnable updateSeekBar = new Runnable() {
                @Override
                public void run() {
                    if (isBound && musicService != null && musicService.isPlaying()) {
                        mainSeekBar.setMax(musicService.getDuration());
                        mainSeekBar.setProgress(musicService.getCurrentPosition());
                    }
                    handler.postDelayed(this, 1000);
                }
            };
            handler.post(updateSeekBar);

// seekbar drag
            mainSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isBound && musicService != null && tvCurrentTitle != null) {
            Song current = musicService.getCurrentSong();
            if (current != null) {
                tvCurrentTitle.setText(current.getTitle());
                tvCurrentArtist.setText(current.getArtist());
                btnPlayPause.setText(musicService.isPlaying() ? "Pause" : "Play");
            }
            tvCurrentTitle.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, PlayerActivity.class)));
            tvCurrentArtist.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, PlayerActivity.class)));
        }
    }
}
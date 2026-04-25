package com.psthetech.swara;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {
    private List<Song> queue = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPaused = false;
    private AudioManager audioManager;
    MediaPlayer mediaPlayer;
    private final IBinder binder = new MusicBinder();
    public class MusicBinder extends Binder {
        MusicService getService(){
            return MusicService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }
    public void playSong(Song s) {
        try {
            // release old player first!
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            isPaused = false; // reset flag
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(s.getPath());
            mediaPlayer.prepare();
            int result = audioManager.requestAudioFocus(focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(mp -> {
                    if (currentIndex + 1 < queue.size()) {
                        currentIndex++;
                        playSong(queue.get(currentIndex));
                    }
                });
            }
            Log.d("Swara", "Playing: " + s.getTitle());
        } catch (IOException e) {
            Log.e("Swara", "Error playing song: " + e.getMessage());
        }
    }
    public void setQueue(List<Song> songs, int startIndex) {
        this.queue = songs;
        this.currentIndex = startIndex;
    }
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
    public void pauseSong(){
        if(mediaPlayer!=null && mediaPlayer.isPlaying()){
            mediaPlayer.pause();
            isPaused = true;
        }
    }
    public void resumeSong(){
        if(mediaPlayer!=null && !mediaPlayer.isPlaying()){
            mediaPlayer.start();
        }
    }
    public boolean isPaused() {
        return isPaused;
    }
    private AudioManager.OnAudioFocusChangeListener focusListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            resumeSong();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            pauseSong();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pauseSong();
        }
    };
    public void playNext() {
        if(currentIndex+1<queue.size()){
            currentIndex+=1;
            playSong(queue.get(currentIndex));
        }
    }

    public void playPrevious() {
        if(currentIndex-1>=0){
            currentIndex-=1;
            playSong(queue.get(currentIndex));
        }
    }
    public Song getCurrentSong() {
        return queue.isEmpty() ? null : queue.get(currentIndex);
    }
    public void seekTo(int msec) {
        if (mediaPlayer != null) mediaPlayer.seekTo(msec);
    }

    public int getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
    }


}
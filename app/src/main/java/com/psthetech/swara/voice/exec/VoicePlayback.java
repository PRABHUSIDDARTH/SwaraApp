package com.psthetech.swara.voice.exec;

import com.psthetech.swara.domain.model.Song;

import java.util.List;

/** Playback surface the voice executor drives. */
public interface VoicePlayback {
    void playSong(Song s);
    void playQueue(List<Song> songs);
    void pause();
    void resume();
    void next();
    void previous();
}

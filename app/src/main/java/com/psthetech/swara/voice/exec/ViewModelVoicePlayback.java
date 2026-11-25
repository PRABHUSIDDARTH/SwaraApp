package com.psthetech.swara.voice.exec;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;

import java.util.List;

/** Adapts PlaybackViewModel to VoicePlayback. */
public final class ViewModelVoicePlayback implements VoicePlayback {
    private final PlaybackViewModel vm;

    public ViewModelVoicePlayback(PlaybackViewModel vm) { this.vm = vm; }

    @Override public void playSong(Song s) { vm.playSong(s); }
    @Override public void playQueue(List<Song> songs) { vm.playVoiceQueue(songs); }
    @Override public void pause() { vm.pause(); }
    @Override public void resume() { vm.resume(); }
    @Override public void next() { vm.skipToNext(); }
    @Override public void previous() { vm.skipToPrevious(); }
}

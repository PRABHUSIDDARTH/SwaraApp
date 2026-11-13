package com.psthetech.swara.voice.exec;

import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.voice.intent.IntentParser;
import com.psthetech.swara.voice.intent.VoiceIntent;
import com.psthetech.swara.voice.match.Candidate;
import com.psthetech.swara.voice.match.LibraryIndex;
import com.psthetech.swara.voice.match.LibraryVoiceMatcher;
import com.psthetech.swara.voice.match.MatchKind;
import com.psthetech.swara.voice.match.MatchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Parses a transcript, matches it against the library and drives playback. Pure Java. */
public final class VoiceCommandExecutor {
    private static final double AMBIGUOUS_BAND = 0.10;
    private static final int MAX_CHOICES = 4;

    private final VoicePlayback playback;
    private final Random random;

    public VoiceCommandExecutor(VoicePlayback playback, Random random) {
        this.playback = playback;
        this.random = random;
    }

    public VoiceOutcome handle(String transcript, @Nullable LibraryIndex index) {
        return handle(IntentParser.parse(transcript), index);
    }

    /** Entry point taking an already-parsed intent. Transport commands work without a library. */
    public VoiceOutcome handle(VoiceIntent intent, @Nullable LibraryIndex index) {
        switch (intent.getType()) {
            case PAUSE:
                playback.pause();
                return VoiceOutcome.result(VoiceReplies.PAUSED, false);
            case RESUME:
                playback.resume();
                return VoiceOutcome.result(VoiceReplies.PLAYING, false);
            case NEXT:
                playback.next();
                return VoiceOutcome.result(VoiceReplies.NEXT, false);
            case PREVIOUS:
                playback.previous();
                return VoiceOutcome.result(VoiceReplies.PREVIOUS, false);
            case UNKNOWN:
                return VoiceOutcome.spoken(VoiceReplies.didntCatch());
            default:
                break;
        }
        if (index == null || index.isEmpty()) return VoiceOutcome.spoken(VoiceReplies.libraryLoading());
        if (intent.getQuery().trim().isEmpty()) return VoiceOutcome.spoken(VoiceReplies.didntCatch());
        MatchResult r = LibraryVoiceMatcher.match(intent, index);
        switch (r.confidence) {
            case HIGH:
                return choose(r.ranked.get(0));
            case MEDIUM: {
                List<Candidate> choices = new ArrayList<>(r.ranked.subList(0, Math.min(MAX_CHOICES, r.ranked.size())));
                return new VoiceOutcome(VoiceOutcome.Kind.CONFIRM, VoiceReplies.didYouMean(r.ranked.get(0).display), true, choices);
            }
            case AMBIGUOUS: {
                double top = r.ranked.get(0).score;
                List<Candidate> choices = new ArrayList<>();
                for (Candidate c : r.ranked) {
                    if (top - c.score <= AMBIGUOUS_BAND + 1e-9) choices.add(c);
                }
                return new VoiceOutcome(VoiceOutcome.Kind.CHOOSE, VoiceReplies.whichOne(), true, choices);
            }
            default:
                return VoiceOutcome.spoken(VoiceReplies.notFound(intent.getQuery()));
        }
    }

    public VoiceOutcome choose(Candidate c) {
        if (c.songs.isEmpty()) return VoiceOutcome.spoken(VoiceReplies.didntCatch());
        if (c.kind == MatchKind.SONG) {
            playback.playSong(c.songs.get(0));
            return VoiceOutcome.result(VoiceReplies.playingSong(c.display), true);
        }
        List<Song> songs = new ArrayList<>(c.songs);
        if (c.kind == MatchKind.ARTIST) {
            Collections.shuffle(songs, random);
            playback.playQueue(songs);
            return VoiceOutcome.result(VoiceReplies.playingArtist(songs.size(), c.display), true);
        }
        Collections.sort(songs, Comparator.comparingInt(Song::getTrackNumber)
                .thenComparing(Song::getTitle, Comparator.nullsFirst(Comparator.naturalOrder())));
        playback.playQueue(songs);
        return VoiceOutcome.result(VoiceReplies.playingAlbum(c.display, songs.size()), true);
    }
}

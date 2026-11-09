package com.psthetech.swara.vibe;

import android.net.Uri;

/** Scores a song's mood. */
public interface MoodScorer {
    int version();

    MoodResult score(Uri song);
}

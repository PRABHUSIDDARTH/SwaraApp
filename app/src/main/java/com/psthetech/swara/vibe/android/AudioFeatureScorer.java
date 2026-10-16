package com.psthetech.swara.vibe.android;

import android.content.Context;
import android.net.Uri;

import com.psthetech.swara.vibe.MoodResult;
import com.psthetech.swara.vibe.MoodScorer;
import com.psthetech.swara.vibe.RawMoodModel;
import com.psthetech.swara.vibe.dsp.AudioFeatures;
import com.psthetech.swara.vibe.dsp.FeatureExtractor;

/** Scores a song by decoding its middle 30 s and extracting audio features. */
public class AudioFeatureScorer implements MoodScorer {
    public static final int VERSION = 2;
    private static final int SECONDS = 30;
    private static final int RATE = 22050;

    private final Context context;

    public AudioFeatureScorer(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public MoodResult score(Uri song) {
        float[] pcm = PcmDecoder.decodeMiddle(context, song, SECONDS, RATE);
        if (pcm == null) return MoodResult.failed(VERSION);
        AudioFeatures f = FeatureExtractor.extract(pcm, RATE);
        if (f == null) return MoodResult.failed(VERSION);
        double v = RawMoodModel.rawValence(f);
        double e = RawMoodModel.rawEnergy(f);
        return new MoodResult(true, v, e, f.tempoBpm, RawMoodModel.pack(v, e, f.tempoBpm), VERSION);
    }
}

package com.psthetech.swara.korokae.v2;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class KorokaeV2ParityTest {

    private static final String MODEL = "korokae_v2_8_256.onnx";
    private static final String FIXTURE = "korokae_v2_8_256_parity.bin";

    @Test
    public void parityWithPyTorch() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File model = TestAssets.copyToCache(app, MODEL);
        float[][] fx = TestAssets.readParity(FIXTURE);
        float[] input = fx[0];
        float[] expected = fx[1];
        float[] got;
        try (OnnxMaskModel m = OnnxMaskModel.open(model, 4)) {
            got = new KorokaeV2Engine(m).separate(input, input.length / 2, p -> {}, () -> false);
        }
        float max = 0f;
        for (int i = 0; i < got.length; i++) max = Math.max(max, Math.abs(got[i] - expected[i]));
        Log.i("KorokaeV2", "parity max abs error = " + max);
        assertTrue("max abs error " + max, max <= 1e-3f);
    }
}

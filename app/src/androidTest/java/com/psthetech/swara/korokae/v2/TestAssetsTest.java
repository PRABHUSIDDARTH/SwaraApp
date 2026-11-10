package com.psthetech.swara.korokae.v2;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class TestAssetsTest {

    private static final String MODEL = "korokae_v2_4_96.onnx";

    @Test
    public void staleCachedCopyIsReplaced() throws Exception {
        // adb install -r keeps the cache dir, so an old export may already sit there under the same name
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File cached = new File(app.getCacheDir(), MODEL);
        try (FileOutputStream os = new FileOutputStream(cached)) {
            os.write(new byte[]{1, 2, 3});
        }
        File out = TestAssets.copyToCache(app, MODEL);
        long assetLength;
        try (AssetFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().openFd(MODEL)) {
            assetLength = fd.getLength();
        }
        assertEquals(assetLength, out.length());
    }
}

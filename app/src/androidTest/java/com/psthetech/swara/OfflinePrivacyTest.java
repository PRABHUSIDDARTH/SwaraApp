package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.FileDescriptor;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Swara is offline. Libraries can add network access and startup components through manifest
 * merging (ONNX Runtime did both, for telemetry), so check what the installed app really has.
 */
@RunWith(AndroidJUnit4.class)
public class OfflinePrivacyTest {

    private final Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void noOnnxRuntimeProviderIsRegistered() throws Exception {
        PackageInfo info = app.getPackageManager()
                .getPackageInfo(app.getPackageName(), PackageManager.GET_PROVIDERS);
        for (ProviderInfo provider : info.providers) {
            assertFalse(provider.name, provider.name.startsWith("ai.onnxruntime."));
        }
    }

    @Test
    public void internetPermissionIsNotRequested() throws Exception {
        PackageInfo info = app.getPackageManager()
                .getPackageInfo(app.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertFalse(Arrays.toString(info.requestedPermissions),
                Arrays.asList(info.requestedPermissions).contains("android.permission.INTERNET"));
    }

    @Test
    public void systemRefusesInternetSockets() throws Exception {
        try {
            FileDescriptor fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, 0);
            Os.close(fd);
            fail("the app process could open an internet socket");
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EPERM && e.errno != OsConstants.EACCES) throw e;
        }
    }

    @Test
    public void onnxRuntimeTelemetryIsSwitchedOff() {
        assertEquals("1", Os.getenv("ORT_DISABLE_TELEMETRY"));
    }
}

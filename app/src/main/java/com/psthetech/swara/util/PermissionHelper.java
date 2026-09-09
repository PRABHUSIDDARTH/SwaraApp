package com.psthetech.swara.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Utility for handling media read permissions correctly across API levels.
 *
 * Android 13+ (API 33): READ_MEDIA_AUDIO
 * Android 12L and below: READ_EXTERNAL_STORAGE
 */
public class PermissionHelper {

    public static final int REQUEST_CODE_AUDIO = 100;

    private PermissionHelper() {}

    public static String getRequiredPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

    public static boolean hasAudioPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, getRequiredPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestAudioPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{getRequiredPermission()},
                REQUEST_CODE_AUDIO
        );
    }

    public static boolean shouldShowRationale(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(
                activity, getRequiredPermission());
    }
}

package com.psthetech.swara.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

public class PermissionHelperTest {

    private MockedStatic<ContextCompat> mockContextCompat;
    private MockedStatic<ActivityCompat> mockActivityCompat;
    private MockedStatic<PermissionHelper> mockPermissionHelper;

    @Before
    public void setUp() throws Exception {
        mockContextCompat = Mockito.mockStatic(ContextCompat.class);
        mockActivityCompat = Mockito.mockStatic(ActivityCompat.class);
        mockPermissionHelper = Mockito.mockStatic(PermissionHelper.class, Mockito.CALLS_REAL_METHODS);
    }

    @After
    public void tearDown() throws Exception {
        mockContextCompat.close();
        mockActivityCompat.close();
        mockPermissionHelper.close();
    }

    @Test
    public void getRequiredPermission_android13AndAbove() throws Exception {
        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.TIRAMISU);
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, PermissionHelper.getRequiredPermission());

        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, PermissionHelper.getRequiredPermission());
    }

    @Test
    public void getRequiredPermission_android12AndBelow() throws Exception {
        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.S_V2);
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, PermissionHelper.getRequiredPermission());

        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.R);
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, PermissionHelper.getRequiredPermission());
    }

    @Test
    public void hasAudioPermission_granted() {
        Context mockContext = mock(Context.class);
        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.TIRAMISU);
        mockContextCompat.when(() -> ContextCompat.checkSelfPermission(any(Context.class), any(String.class)))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        assertTrue(PermissionHelper.hasAudioPermission(mockContext));
    }

    @Test
    public void hasAudioPermission_denied() {
        Context mockContext = mock(Context.class);
        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.TIRAMISU);
        mockContextCompat.when(() -> ContextCompat.checkSelfPermission(any(Context.class), any(String.class)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(PermissionHelper.hasAudioPermission(mockContext));
    }

    @Test
    public void requestAudioPermission() throws Exception {
        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.TIRAMISU);
        Activity mockActivity = mock(Activity.class);

        PermissionHelper.requestAudioPermission(mockActivity);

        mockActivityCompat.verify(() -> ActivityCompat.requestPermissions(
                eq(mockActivity),
                eq(new String[]{Manifest.permission.READ_MEDIA_AUDIO}),
                eq(PermissionHelper.REQUEST_CODE_AUDIO)
        ));
    }

    @Test
    public void shouldShowRationale_true() throws Exception {
        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.TIRAMISU);
        Activity mockActivity = mock(Activity.class);
        mockActivityCompat.when(() -> ActivityCompat.shouldShowRequestPermissionRationale(any(Activity.class), any(String.class)))
                .thenReturn(true);

        assertTrue(PermissionHelper.shouldShowRationale(mockActivity));

        mockActivityCompat.verify(() -> ActivityCompat.shouldShowRequestPermissionRationale(
                eq(mockActivity), eq(Manifest.permission.READ_MEDIA_AUDIO)
        ));
    }

    @Test
    public void shouldShowRationale_false() throws Exception {
        mockPermissionHelper.when(PermissionHelper::getSdkInt).thenReturn(Build.VERSION_CODES.TIRAMISU);
        Activity mockActivity = mock(Activity.class);
        mockActivityCompat.when(() -> ActivityCompat.shouldShowRequestPermissionRationale(any(Activity.class), any(String.class)))
                .thenReturn(false);

        assertFalse(PermissionHelper.shouldShowRationale(mockActivity));

        mockActivityCompat.verify(() -> ActivityCompat.shouldShowRequestPermissionRationale(
                eq(mockActivity), eq(Manifest.permission.READ_MEDIA_AUDIO)
        ));
    }
}

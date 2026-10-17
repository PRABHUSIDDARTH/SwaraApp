package com.psthetech.swara.wrapped;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.psthetech.swara.R;
import com.psthetech.swara.wrapped.ui.WrappedStoryActivity;

/** The one quiet "Your Wrapped is ready" notification. */
public final class WrappedNotifier {

    static final String CHANNEL = "wrapped";

    private WrappedNotifier() {}

    public static void notifyReady(@NonNull Context context, @NonNull String key, @NonNull String title) {
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // the Home banner still announces it
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    app.getString(R.string.wrapped_channel_name), NotificationManager.IMPORTANCE_LOW);
            app.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(app, key.hashCode(),
                WrappedStoryActivity.forRecap(app, key),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_widget_note)
                .setContentTitle(title)
                .setContentText(app.getString(R.string.wrapped_banner_sub))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        try {
            NotificationManagerCompat.from(app).notify(key.hashCode(), b.build());
        } catch (SecurityException ignored) {
            // permission revoked between the check and the post
        }
    }
}

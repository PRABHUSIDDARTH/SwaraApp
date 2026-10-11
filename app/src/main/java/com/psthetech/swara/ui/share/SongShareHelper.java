package com.psthetech.swara.ui.share;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.domain.model.Song;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** Builds and launches share intents for songs and share cards. */
public final class SongShareHelper {

    public static final String PKG_WHATSAPP = "com.whatsapp";
    public static final String PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    public static final String PKG_INSTAGRAM = "com.instagram.android";

    // Nearby / Quick Share providers (Google Play services and Samsung Quick Share)
    private static final String PKG_GMS = "com.google.android.gms";
    private static final String PKG_SAMSUNG_QUICK_SHARE = "com.samsung.android.app.sharelive";

    private SongShareHelper() { }

    // ── Song file (Nearby / Quick Share) ─────────────────────────────────────────

    @Nullable
    private static Intent buildSongFileIntent(@NonNull Context ctx, @NonNull Song song) {
        Uri uri = ArtworkRepository.getSongUri(song.getId());
        if (uri == null) return null;
        String mime = null;
        try {
            mime = ctx.getContentResolver().getType(uri);
        } catch (Exception ignored) { /* fall back below */ }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime != null ? mime : "audio/*");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TITLE, song.getTitle());
        send.setClipData(ClipData.newUri(ctx.getContentResolver(), song.getTitle(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return send;
    }

    /**
     * Sends the song's audio file straight to Quick Share / Nearby Share when the device has it,
     * otherwise opens the system share sheet (where Quick Share is also offered).
     */
    public static void shareSongNearby(@NonNull Activity activity, @Nullable Song song) {
        if (song == null) return;
        Intent send = buildSongFileIntent(activity, song);
        if (send == null) {
            Toast.makeText(activity, R.string.share_file_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        ComponentName nearby = findNearbyShareTarget(activity, send);
        if (nearby != null) {
            Intent direct = new Intent(send).setComponent(nearby);
            try {
                activity.startActivity(direct);
                return;
            } catch (Exception ignored) {
                // Fall through to the chooser.
            }
        }
        shareSongWithChooser(activity, song);
    }

    /** Full system share sheet for the song's audio file. */
    public static void shareSongWithChooser(@NonNull Activity activity, @Nullable Song song) {
        if (song == null) return;
        Intent send = buildSongFileIntent(activity, song);
        if (send == null) {
            Toast.makeText(activity, R.string.share_file_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent chooser = Intent.createChooser(send, activity.getString(R.string.share_nearby));
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(chooser);
    }

    @Nullable
    private static ComponentName findNearbyShareTarget(@NonNull Context ctx, @NonNull Intent send) {
        List<ResolveInfo> targets = ctx.getPackageManager().queryIntentActivities(send, 0);
        for (ResolveInfo ri : targets) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            boolean isGmsNearby = PKG_GMS.equals(pkg) && cls.toLowerCase(java.util.Locale.ROOT).contains("nearby");
            if (isGmsNearby || PKG_SAMSUNG_QUICK_SHARE.equals(pkg)) {
                return new ComponentName(pkg, cls);
            }
        }
        return null;
    }

    // ── Share cards ──────────────────────────────────────────────────────────────

    /** Writes the card to app cache and returns a shareable content:// URI. */
    @NonNull
    public static Uri writeCardToCache(@NonNull Context ctx, @NonNull Bitmap card) throws IOException {
        File dir = new File(ctx.getCacheDir(), "share_cards");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File file = new File(dir, "swara_now_playing.png");
        try (OutputStream os = new FileOutputStream(file)) {
            card.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
    }

    public static boolean isInstalled(@NonNull Context ctx, @NonNull String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @NonNull
    public static Intent buildImageIntent(@NonNull Context ctx, @NonNull Uri image, @Nullable String caption) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, image);
        if (caption != null) send.putExtra(Intent.EXTRA_TEXT, caption);
        send.setClipData(ClipData.newUri(ctx.getContentResolver(), "Swara card", image));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return send;
    }

    /** Opens a specific app with the card (WhatsApp chat picker, Instagram share sheet). */
    public static boolean shareCardToApp(@NonNull Activity activity, @NonNull Uri image,
                                         @NonNull String pkg, @Nullable String caption) {
        Intent send = buildImageIntent(activity, image, caption).setPackage(pkg);
        try {
            activity.startActivity(send);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opens Instagram's story composer with the card as the background. Falls back to Instagram's
     * own share sheet (which offers Story, Feed and Messages) if the story intent is unavailable.
     */
    public static boolean shareCardToInstagramStory(@NonNull Activity activity, @NonNull Uri image) {
        Intent story = new Intent("com.instagram.share.ADD_TO_STORY");
        story.setDataAndType(image, "image/png");
        story.setPackage(PKG_INSTAGRAM);
        story.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String appId = activity.getString(R.string.facebook_app_id);
        if (!appId.isEmpty()) {
            story.putExtra("com.facebook.platform.extra.APPLICATION_ID", appId);
        }
        activity.grantUriPermission(PKG_INSTAGRAM, image, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Without a Facebook App ID newer Instagram builds reject ADD_TO_STORY, so use the share sheet.
        if (!appId.isEmpty() && story.resolveActivity(activity.getPackageManager()) != null) {
            try {
                activity.startActivity(story);
                return true;
            } catch (Exception ignored) {
                // Fall back below.
            }
        }
        return shareCardToApp(activity, image, PKG_INSTAGRAM, null);
    }

    public static void shareCardWithChooser(@NonNull Activity activity, @NonNull Uri image, @Nullable String caption) {
        Intent chooser = Intent.createChooser(buildImageIntent(activity, image, caption),
                activity.getString(R.string.share_card));
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(chooser);
    }

    /** Saves the card to Pictures/Swara (Android 10+, no storage permission needed). */
    public static boolean saveCardToGallery(@NonNull Context ctx, @NonNull Bitmap card, @NonNull String name) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Swara");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return false;
        try (OutputStream os = cr.openOutputStream(uri)) {
            if (os == null) throw new IOException("No stream");
            card.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (IOException e) {
            cr.delete(uri, null, null);
            return false;
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        return true;
    }

    /** Caption used alongside the card in chat apps. */
    @NonNull
    public static String caption(@NonNull Song song) {
        return "\uD83C\uDFB5 " + song.getTitle() + " \u2014 " + song.getArtist();
    }
}

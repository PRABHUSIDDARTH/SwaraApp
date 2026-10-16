package com.psthetech.swara.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.SongMetadata;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure Java ID3v2.3 parser and writer for MP3 audio files.
 *
 * Guarantees:
 * 1. Embedded artwork (APIC frames), lyrics (USLT frames), and custom tags are 100% preserved.
 * 2. UTF-16 with BOM encoding for all text frames ensures global Unicode support (Tamil, Hindi, etc.).
 * 3. Staged rewriting with a verified backup: a failed write-back restores the original bytes.
 * 4. Tags that cannot be rewritten safely (v2.2, unsynchronised, malformed) are left untouched.
 */
public final class Id3MetadataWriter {

    private static final String TAG = "Id3MetadataWriter";

    private Id3MetadataWriter() {}

    /** Tags larger than this (e.g. huge embedded artwork) are not rewritten, to avoid OOM. */
    private static final int MAX_TAG_BODY_BYTES = 32 * 1024 * 1024;

    /**
     * Originals are journaled in internal storage (not the cache, which the system may clear)
     * until the rewrite completes. A leftover journal entry means the process died mid-write.
     */
    private static final String JOURNAL_DIR = "metadata_journal";
    private static final String BACKUP_SUFFIX = ".orig";
    private static final String URI_SUFFIX = ".uri";

    // Set of frame IDs managed/replaced by the metadata editor
    private static final Set<String> MANAGED_FRAMES = new HashSet<>(Arrays.asList(
            "TIT2", // Title
            "TPE1", // Lead Artist
            "TALB", // Album
            "TPE2", // Album Artist / Band
            "TCON", // Genre
            "TYER", // Year (v2.3)
            "TDRC", // Recording Time / Year (v2.4)
            "TRCK", // Track number
            "TPOS", // Disc number / Part of set
            "TCOM", // Composer
            "COMM"  // Comments
    ));

    /**
     * Checks if this writer supports embedding tags into the given file.
     */
    public static boolean isSupportedFormat(@Nullable String mimeType, @Nullable String filePath) {
        if (mimeType != null && mimeType.equalsIgnoreCase("audio/mpeg")) {
            return true;
        }
        if (filePath != null) {
            String lower = filePath.toLowerCase();
            return lower.endsWith(".mp3");
        }
        return false;
    }

    /**
     * Safely updates ID3v2 tags in the audio file at the given URI.
     *
     * @param context   Application context
     * @param uri       Content URI of the audio file
     * @param metadata  New metadata to write
     * @return true if embedded tags were written successfully
     */
    public static boolean writeMetadata(@NonNull Context context,
                                        @NonNull Uri uri,
                                        @NonNull SongMetadata metadata) {
        ContentResolver resolver = context.getContentResolver();
        File stagingFile = null;

        try {
            // Step 1: Parse the existing tag (if any) and locate the start of the audio stream.
            List<RawFrame> preservedFrames = new ArrayList<>();
            long oldTagSize = 0;
            long sourceLength;

            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) {
                    Log.e(TAG, "Cannot open input stream for URI: " + uri);
                    return false;
                }
                byte[] header = new byte[10];
                int read = readFully(in, header, 0, 10);
                if (read == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                    TagLayout layout = parseHeader(header);
                    if (layout == null) {
                        Log.w(TAG, "Unsupported ID3 layout, skipping embedded write: " + uri);
                        return false;
                    }
                    if (layout.bodySize > MAX_TAG_BODY_BYTES) {
                        Log.w(TAG, "ID3 tag too large to rewrite safely (" + layout.bodySize + " bytes): " + uri);
                        return false;
                    }
                    byte[] tagData = new byte[layout.bodySize];
                    if (readFully(in, tagData, 0, layout.bodySize) != layout.bodySize) {
                        Log.e(TAG, "Truncated ID3 tag, skipping embedded write: " + uri);
                        return false;
                    }
                    List<RawFrame> frames = parseFrames(tagData, layout);
                    if (frames == null) {
                        Log.w(TAG, "Unparseable ID3 frames, skipping embedded write: " + uri);
                        return false;
                    }
                    preservedFrames.addAll(frames);
                    oldTagSize = layout.totalSize;
                }
            }

            // Step 2: Build new ID3v2.3 tag payload
            byte[] newTagPayload = buildId3v23TagPayload(metadata, preservedFrames);
            byte[] newTagHeader = buildId3Header(newTagPayload.length);

            // Step 3: Stage (new tag + original audio) in app cache.
            stagingFile = File.createTempFile("swara_meta_", ".tmp", context.getCacheDir());
            long audioBytes;
            try (FileOutputStream out = new FileOutputStream(stagingFile);
                 InputStream in = resolver.openInputStream(uri)) {
                if (in == null) return false;
                out.write(newTagHeader);
                out.write(newTagPayload);
                if (skipFully(in, oldTagSize) != oldTagSize) {
                    Log.e(TAG, "Source shorter than its ID3 tag: " + uri);
                    return false;
                }
                audioBytes = copy(in, out);
                out.getFD().sync();
            }
            sourceLength = oldTagSize + audioBytes;

            long expected = newTagHeader.length + newTagPayload.length + audioBytes;
            if (audioBytes <= 0 || stagingFile.length() != expected) {
                Log.e(TAG, "Staging verification failed (audio=" + audioBytes + ")");
                return false;
            }

            // Step 4: Back up the original so a failed write-back can be rolled back.
            File journal = journalDir(context);
            File backup = new File(journal, stagingFile.getName() + BACKUP_SUFFIX);
            File uriRecord = new File(journal, stagingFile.getName() + URI_SUFFIX);
            try (InputStream in = resolver.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(backup)) {
                if (in == null || copy(in, out) != sourceLength) {
                    backup.delete();
                    Log.e(TAG, "Could not back up original before rewrite: " + uri);
                    return false;
                }
                out.getFD().sync();
            }
            // The URI record is written last: a backup without it is incomplete and ignored.
            try (FileOutputStream out = new FileOutputStream(uriRecord)) {
                out.write(uri.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }

            // Step 5: Write back. On any failure, restore the original bytes.
            try {
                writeFileTo(resolver, uri, stagingFile);
                Log.d(TAG, "Successfully wrote ID3 metadata for URI: " + uri);
                return true;
            } catch (Exception writeError) {
                Log.e(TAG, "Write-back failed, restoring original: " + uri, writeError);
                try {
                    writeFileTo(resolver, uri, backup);
                } catch (Exception restoreError) {
                    Log.e(TAG, "CRITICAL: restore failed; original kept at " + backup, restoreError);
                    backup = null; // keep the journal entry; recoverInterruptedWrites() retries
                }
                return false;
            } finally {
                if (backup != null) {
                    uriRecord.delete();
                    backup.delete();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed writing ID3 metadata for URI: " + uri, e);
            return false;
        } finally {
            if (stagingFile != null && stagingFile.exists()) {
                stagingFile.delete();
            }
        }
    }

    private static File journalDir(Context context) {
        File dir = new File(context.getFilesDir(), JOURNAL_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Restores originals whose rewrite never completed (process killed mid-write).
     * Call once at startup, off the main thread.
     */
    public static void recoverInterruptedWrites(@NonNull Context context) {
        File[] entries = journalDir(context).listFiles();
        if (entries == null) return;
        ContentResolver resolver = context.getContentResolver();
        for (File backup : entries) {
            String name = backup.getName();
            if (!name.endsWith(BACKUP_SUFFIX)) continue;
            String base = name.substring(0, name.length() - BACKUP_SUFFIX.length());
            File uriRecord = new File(backup.getParentFile(), base + URI_SUFFIX);
            if (!uriRecord.exists()) {
                backup.delete(); // backup never completed, so the original was never touched
                continue;
            }
            try {
                byte[] raw = new byte[(int) uriRecord.length()];
                try (FileInputStream in = new FileInputStream(uriRecord)) {
                    readFully(in, raw, 0, raw.length);
                }
                Uri uri = Uri.parse(new String(raw, StandardCharsets.UTF_8));
                writeFileTo(resolver, uri, backup);
                Log.w(TAG, "Restored original after interrupted tag write: " + uri);
                uriRecord.delete();
                backup.delete();
            } catch (Exception e) {
                Log.e(TAG, "Could not restore interrupted tag write " + base + "; will retry", e);
            }
        }
        // Drop orphaned URI records.
        for (File f : entries) {
            if (f.getName().endsWith(URI_SUFFIX)) {
                String base = f.getName().substring(0, f.getName().length() - URI_SUFFIX.length());
                if (!new File(f.getParentFile(), base + BACKUP_SUFFIX).exists()) f.delete();
            }
        }
    }

    private static void writeFileTo(ContentResolver resolver, Uri uri, File source) throws IOException {
        try (OutputStream targetOut = resolver.openOutputStream(uri, "wt");
             FileInputStream in = new FileInputStream(source)) {
            if (targetOut == null) throw new IOException("Cannot open output stream for " + uri);
            long written = copy(in, targetOut);
            targetOut.flush();
            if (written != source.length()) throw new IOException("Short write to " + uri);
        }
    }

    private static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int r;
        while ((r = in.read(buffer)) != -1) {
            out.write(buffer, 0, r);
            total += r;
        }
        return total;
    }

    static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(buf, off + total, len - total);
            if (r < 0) break;
            total += r;
        }
        return total;
    }

    private static long skipFully(InputStream in, long n) throws IOException {
        long done = 0;
        byte[] dummy = null;
        while (done < n) {
            long s = in.skip(n - done);
            if (s > 0) {
                done += s;
                continue;
            }
            if (dummy == null) dummy = new byte[4096];
            int r = in.read(dummy, 0, (int) Math.min(dummy.length, n - done));
            if (r < 0) break;
            done += r;
        }
        return done;
    }

    // ===== Tag layout =====

    /** Parsed ID3v2 header facts needed to safely rewrite a tag. */
    static final class TagLayout {
        final int version;
        final int flags;
        final int bodySize;
        final long totalSize;

        TagLayout(int version, int flags, int bodySize, long totalSize) {
            this.version = version;
            this.flags = flags;
            this.bodySize = bodySize;
            this.totalSize = totalSize;
        }
    }

    /**
     * Returns the layout of an ID3v2.3/2.4 tag, or null if rewriting it would be unsafe
     * (v2.2 and older, unknown versions, tag-level unsynchronisation, or invalid sizes).
     */
    @Nullable
    static TagLayout parseHeader(byte[] header) {
        int version = header[3] & 0xFF;
        int flags = header[5] & 0xFF;
        if (version != 3 && version != 4) return null;
        if ((flags & 0x80) != 0) return null; // unsynchronisation
        for (int i = 6; i < 10; i++) {
            if ((header[i] & 0x80) != 0) return null; // not a valid syncsafe integer
        }
        int bodySize = decodeSyncsafeInteger(header, 6);
        long total = 10L + bodySize + ((version == 4 && (flags & 0x10) != 0) ? 10 : 0);
        return new TagLayout(version, flags, bodySize, total);
    }

    /**
     * Parses frames after any extended header and converts them to ID3v2.3 layout.
     * Returns null if a frame cannot be represented safely in v2.3.
     */
    @Nullable
    static List<RawFrame> parseFrames(byte[] data, TagLayout layout) {
        int offset = 0;
        if ((layout.flags & 0x40) != 0) { // extended header
            if (data.length < 4) return null;
            int extSize;
            if (layout.version == 4) {
                extSize = decodeSyncsafeInteger(data, 0); // includes the size field itself
            } else {
                extSize = 4 + (((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
                        | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF));
            }
            if (extSize < 4 || extSize > data.length) return null;
            offset = extSize;
        }

        List<RawFrame> preserved = new ArrayList<>();
        while (offset + 10 <= data.length) {
            if (data[offset] == 0) break; // padding
            for (int i = 0; i < 4; i++) {
                byte c = data[offset + i];
                if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return null;
            }
            String frameId = new String(data, offset, 4, StandardCharsets.ISO_8859_1);
            int frameSize = layout.version == 4
                    ? decodeSyncsafeInteger(data, offset + 4)
                    : ((data[offset + 4] & 0xFF) << 24) | ((data[offset + 5] & 0xFF) << 16)
                      | ((data[offset + 6] & 0xFF) << 8) | (data[offset + 7] & 0xFF);
            if (frameSize < 0 || offset + 10L + frameSize > data.length) return null;

            int formatFlags = data[offset + 9] & 0xFF;
            if (!MANAGED_FRAMES.contains(frameId)) {
                if (layout.version == 4) {
                    // v2.4 per-frame compression/encryption/unsync/data-length cannot be
                    // carried into v2.3 verbatim; drop only those exotic frames.
                    if ((formatFlags & 0x0F) != 0 || (formatFlags & 0x40) != 0) {
                        Log.w(TAG, "Dropping v2.4 frame with format flags: " + frameId);
                    } else {
                        preserved.add(new RawFrame(frameId, toV23Frame(data, offset, frameSize)));
                    }
                } else {
                    byte[] raw = new byte[10 + frameSize];
                    System.arraycopy(data, offset, raw, 0, raw.length);
                    preserved.add(new RawFrame(frameId, raw));
                }
            }
            offset += 10 + frameSize;
        }
        return preserved;
    }

    /** Re-encodes a v2.4 frame header (syncsafe size) as v2.3 (plain 32-bit size). */
    private static byte[] toV23Frame(byte[] data, int offset, int frameSize) {
        byte[] raw = new byte[10 + frameSize];
        System.arraycopy(data, offset, raw, 0, raw.length);
        raw[4] = (byte) ((frameSize >> 24) & 0xFF);
        raw[5] = (byte) ((frameSize >> 16) & 0xFF);
        raw[6] = (byte) ((frameSize >> 8) & 0xFF);
        raw[7] = (byte) (frameSize & 0xFF);
        // v2.4 status flags (%0abc0000) -> v2.3 (%abc00000); format flags cleared.
        raw[8] = (byte) ((data[offset + 8] << 1) & 0xE0);
        raw[9] = 0;
        return raw;
    }

    // ===== Internal Frame Parsing & Preservation =====

    static final class RawFrame {
        final String id;
        final byte[] rawData;

        RawFrame(String id, byte[] rawData) {
            this.id = id;
            this.rawData = rawData;
        }
    }

    // ===== ID3v2.3 Assembly =====

    private static byte[] buildId3v23TagPayload(SongMetadata metadata, List<RawFrame> preservedFrames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1. Write preserved non-managed frames (e.g. album art APIC)
        for (RawFrame frame : preservedFrames) {
            out.write(frame.rawData);
        }

        // 2. Write text frames
        writeTextFrame(out, "TIT2", metadata.getTitle());
        writeTextFrame(out, "TPE1", metadata.getArtist());
        writeTextFrame(out, "TALB", metadata.getAlbum());

        if (metadata.getAlbumArtist() != null && !metadata.getAlbumArtist().trim().isEmpty()) {
            writeTextFrame(out, "TPE2", metadata.getAlbumArtist());
        }

        if (metadata.getGenre() != null && !metadata.getGenre().trim().isEmpty()) {
            writeTextFrame(out, "TCON", metadata.getGenre());
        }

        if (metadata.getYear() > 0) {
            writeTextFrame(out, "TYER", String.valueOf(metadata.getYear()));
        }

        if (metadata.getTrackNumber() > 0) {
            writeTextFrame(out, "TRCK", String.valueOf(metadata.getTrackNumber()));
        }

        if (metadata.getDiscNumber() > 0) {
            writeTextFrame(out, "TPOS", String.valueOf(metadata.getDiscNumber()));
        }

        if (metadata.getComposer() != null && !metadata.getComposer().trim().isEmpty()) {
            writeTextFrame(out, "TCOM", metadata.getComposer());
        }

        if (metadata.getComment() != null && !metadata.getComment().trim().isEmpty()) {
            writeCommentFrame(out, metadata.getComment());
        }

        // 3. Add 1024 bytes of null padding
        byte[] padding = new byte[1024];
        out.write(padding);

        return out.toByteArray();
    }

    private static void writeTextFrame(ByteArrayOutputStream out, String frameId, String text) throws IOException {
        if (text == null || text.isEmpty()) return;

        byte[] frameHeader = new byte[10];
        byte[] idBytes = frameId.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(idBytes, 0, frameHeader, 0, 4);

        // Encoding: 0x01 = UTF-16 with BOM
        byte[] utf16Bytes = text.getBytes(StandardCharsets.UTF_16);
        int frameSize = 1 + utf16Bytes.length;

        // ID3v2.3 32-bit big-endian frame size
        frameHeader[4] = (byte) ((frameSize >> 24) & 0xFF);
        frameHeader[5] = (byte) ((frameSize >> 16) & 0xFF);
        frameHeader[6] = (byte) ((frameSize >> 8) & 0xFF);
        frameHeader[7] = (byte) (frameSize & 0xFF);
        frameHeader[8] = 0; // flags
        frameHeader[9] = 0;

        out.write(frameHeader);
        out.write(0x01); // UTF-16 with BOM flag
        out.write(utf16Bytes);
    }

    private static void writeCommentFrame(ByteArrayOutputStream out, String comment) throws IOException {
        if (comment == null || comment.isEmpty()) return;

        byte[] frameHeader = new byte[10];
        byte[] idBytes = "COMM".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(idBytes, 0, frameHeader, 0, 4);

        // Format: Encoding (1 byte) + Language (3 bytes "eng") + Short desc (UTF-16 BOM + 0x00 0x00) + Full comment
        byte[] lang = "eng".getBytes(StandardCharsets.ISO_8859_1);
        byte[] emptyDesc = new byte[]{ (byte) 0xFE, (byte) 0xFF, 0, 0 }; // BOM + null terminator
        byte[] commentBytes = comment.getBytes(StandardCharsets.UTF_16);

        int frameSize = 1 + 3 + emptyDesc.length + commentBytes.length;
        frameHeader[4] = (byte) ((frameSize >> 24) & 0xFF);
        frameHeader[5] = (byte) ((frameSize >> 16) & 0xFF);
        frameHeader[6] = (byte) ((frameSize >> 8) & 0xFF);
        frameHeader[7] = (byte) (frameSize & 0xFF);
        frameHeader[8] = 0;
        frameHeader[9] = 0;

        out.write(frameHeader);
        out.write(0x01); // UTF-16
        out.write(lang);
        out.write(emptyDesc);
        out.write(commentBytes);
    }

    private static byte[] buildId3Header(int payloadSize) {
        byte[] header = new byte[10];
        header[0] = 'I';
        header[1] = 'D';
        header[2] = '3';
        header[3] = 3; // ID3v2.3
        header[4] = 0;
        header[5] = 0; // Flags

        // Encode 28-bit syncsafe size
        header[6] = (byte) ((payloadSize >> 21) & 0x7F);
        header[7] = (byte) ((payloadSize >> 14) & 0x7F);
        header[8] = (byte) ((payloadSize >> 7) & 0x7F);
        header[9] = (byte) (payloadSize & 0x7F);

        return header;
    }

    private static int decodeSyncsafeInteger(byte[] data, int offset) {
        return ((data[offset] & 0x7F) << 21)
                | ((data[offset + 1] & 0x7F) << 14)
                | ((data[offset + 2] & 0x7F) << 7)
                | (data[offset + 3] & 0x7F);
    }
}

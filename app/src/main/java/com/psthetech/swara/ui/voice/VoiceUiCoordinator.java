package com.psthetech.swara.ui.voice;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.psthetech.swara.BuildConfig;
import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.voice.VoiceModels;
import com.psthetech.swara.voice.VoicePermissionPolicy;
import com.psthetech.swara.voice.audio.MicSource;
import com.psthetech.swara.voice.exec.ViewModelVoicePlayback;
import com.psthetech.swara.voice.exec.VoiceCommandExecutor;
import com.psthetech.swara.voice.exec.VoiceOutcome;
import com.psthetech.swara.voice.exec.VoiceReplies;
import com.psthetech.swara.voice.intent.IntentParser;
import com.psthetech.swara.voice.intent.VoiceIntent;
import com.psthetech.swara.voice.match.Candidate;
import com.psthetech.swara.voice.match.LibraryIndex;
import com.psthetech.swara.voice.match.LibraryVoiceMatcher;
import com.psthetech.swara.voice.match.MatchResult;
import com.psthetech.swara.voice.session.VoiceSessionController;
import com.psthetech.swara.voice.session.VoiceState;
import com.psthetech.swara.voice.stt.SpeechToText;
import com.psthetech.swara.voice.tts.SystemTtsVoice;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single owner of the tap-to-talk voice UI: mic, session controller, TTS, the library index
 * cache and the microphone permission flow. Created by MainActivity, main thread only.
 * <p>
 * The mic runs only while the controller is LISTENING (the wake word arrives in a later task).
 */
public final class VoiceUiCoordinator implements VoiceSessionController.Listener {

    /** Implemented by the activity so fragments can reach the coordinator. */
    public interface Host {
        @Nullable VoiceUiCoordinator getVoiceCoordinator();
    }

    @Nullable
    public static VoiceUiCoordinator from(@Nullable Fragment f) {
        if (f == null || !(f.getActivity() instanceof Host)) return null;
        return ((Host) f.getActivity()).getVoiceCoordinator();
    }

    /** What the listening sheet shows. Immutable. */
    public static final class SheetModel {
        public enum Mode { HIDDEN, PREPARING, LISTENING, THINKING, REPLY, PERMISSION, SETTINGS, MESSAGE }

        public final Mode mode;
        public final float level;
        @Nullable public final VoiceOutcome outcome;
        @Nullable public final String message;
        /** Debug readout of a typed command (intent and ranked candidates); null otherwise. */
        @Nullable public final String debug;

        SheetModel(Mode mode, float level, @Nullable VoiceOutcome outcome,
                   @Nullable String message, @Nullable String debug) {
            this.mode = mode;
            this.level = level;
            this.outcome = outcome;
            this.message = message;
            this.debug = debug;
        }

        static SheetModel of(Mode m) { return new SheetModel(m, 0f, null, null, null); }

        public boolean hasChoices() {
            return mode == Mode.REPLY && outcome != null && !outcome.choices.isEmpty();
        }
    }

    private static final String TAG = "SwaraVoice";
    private static final String PREFS = "swara_voice";
    private static final String KEY_ASKED_MIC = "asked_mic_permission";

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<String> permissionLauncher;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final VoiceModels models;
    private final MicSource mic;
    private final SystemTtsVoice voice;
    private final VoiceCommandExecutor executor;
    private final PlaybackViewModel playback;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "swara-voice-stt");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private final MutableLiveData<SheetModel> sheet = new MutableLiveData<>(SheetModel.of(SheetModel.Mode.HIDDEN));
    private final MutableLiveData<Boolean> micVisible = new MutableLiveData<>(true);

    @Nullable private VoiceSessionController controller;
    private boolean pendingStart;      // a tap is waiting for models to load
    private boolean typedChoicesPending; // debug typed entry: choices are handled here, not by the controller
    private boolean destroyed;

    // LibraryIndex cache, keyed by the repository's library version. Guarded by indexLock.
    private final Object indexLock = new Object();
    @Nullable private LibraryIndex index;
    private int indexVersion = -1;
    private long indexAliasVersion = -1;

    /**
     * @param permissionLauncher RequestPermission launcher registered by the activity before onStart;
     *                           its result must be forwarded to {@link #onPermissionResult}.
     */
    @MainThread
    public VoiceUiCoordinator(@NonNull AppCompatActivity activity,
                              @NonNull PlaybackViewModel playback,
                              @NonNull ActivityResultLauncher<String> permissionLauncher) {
        this.activity = activity;
        this.playback = playback;
        this.permissionLauncher = permissionLauncher;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.models = VoiceModels.get(activity);
        this.mic = new MicSource(activity);
        if (BuildConfig.DEBUG) com.psthetech.swara.voice.debug.VoiceDiag.init(activity.getCacheDir());
        this.voice = new SystemTtsVoice(activity);
        this.executor = new VoiceCommandExecutor(new ViewModelVoicePlayback(playback), new Random());
        refreshMicVisibility();
    }

    // --- public API ---

    public LiveData<SheetModel> sheet() { return sheet; }

    /** False once the voice models failed to load; the mic buttons hide. */
    public LiveData<Boolean> micVisible() { return micVisible; }

    /** The user tapped a mic button. */
    @MainThread
    public void onMicTapped() {
        if (destroyed) return;
        VoiceSessionController c = controller;
        VoiceState st = c != null ? c.state() : VoiceState.IDLE;
        if (st == VoiceState.LISTENING || st == VoiceState.THINKING) {
            showSheet();
            return;
        }
        boolean granted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean rationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
        boolean asked = prefs.getBoolean(KEY_ASKED_MIC, false);
        switch (VoicePermissionPolicy.decide(granted, rationale, asked)) {
            case START:
                startWhenReady();
                break;
            case REQUEST:
                requestPermission();
                break;
            case EXPLAIN_SETTINGS:
                publish(SheetModel.of(SheetModel.Mode.SETTINGS));
                showSheet();
                break;
        }
    }

    /** Wake word heard (used by the wake-word task): same as a tap. */
    @MainThread
    public void onWake() {
        onMicTapped();
    }

    /** Forwarded from the activity's RequestPermission launcher. */
    @MainThread
    public void onPermissionResult(boolean granted) {
        if (destroyed) return;
        if (granted) {
            startWhenReady();
            return;
        }
        boolean rationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
        VoicePermissionPolicy.Action a = VoicePermissionPolicy.decide(false, rationale, true);
        publish(SheetModel.of(a == VoicePermissionPolicy.Action.EXPLAIN_SETTINGS
                ? SheetModel.Mode.SETTINGS : SheetModel.Mode.PERMISSION));
        showSheet();
    }

    /** The sheet's "Allow microphone" button. */
    @MainThread
    public void requestPermission() {
        prefs.edit().putBoolean(KEY_ASKED_MIC, true).apply();
        publish(SheetModel.of(SheetModel.Mode.PERMISSION));
        showSheet();
        try {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } catch (RuntimeException e) {
            publish(SheetModel.of(SheetModel.Mode.SETTINGS));
        }
    }

    /** The sheet's "Open settings" button. */
    @MainThread
    public void openAppSettings() {
        try {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.getPackageName(), null)));
        } catch (RuntimeException ignored) { }
    }

    /** A chip or "Yes" in the sheet. */
    @MainThread
    public void onChoice(@NonNull Candidate c) {
        if (typedChoicesPending) {
            typedChoicesPending = false;
            VoiceOutcome out;
            try {
                out = executor.choose(c);
            } catch (RuntimeException e) {
                out = VoiceOutcome.spoken(VoiceReplies.didntCatch());
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "typed choice " + c + " -> \"" + out.text + "\"");
            publish(new SheetModel(SheetModel.Mode.REPLY, 0f, out, null, null));
            return;
        }
        if (controller != null) controller.onChoice(c);
    }

    /**
     * The sheet went away. A user dismissal while listening/thinking or with open choices cancels
     * the session; a reply that is still being spoken is left to finish.
     */
    @MainThread
    public void onSheetDismissed() {
        pendingStart = false;
        typedChoicesPending = false;
        VoiceSessionController c = controller;
        SheetModel m = sheet.getValue();
        boolean choicesOpen = m != null && m.hasChoices();
        if (c != null) {
            VoiceState st = c.state();
            if (st == VoiceState.LISTENING || st == VoiceState.THINKING
                    || (st == VoiceState.REPLY && choicesOpen)) {
                c.cancel();
            }
        }
        sheet.setValue(SheetModel.of(SheetModel.Mode.HIDDEN));
    }

    @MainThread
    public void onResume() {
        refreshMicVisibility();
    }

    @MainThread
    public void onPause() {
        if (pendingStart) {
            pendingStart = false;
            publish(SheetModel.of(SheetModel.Mode.HIDDEN));
        }
        if (controller != null) controller.cancel();
        mic.stop();
    }

    @MainThread
    public void onDestroy() {
        destroyed = true;
        pendingStart = false;
        if (controller != null) {
            controller.setListener(null);
            controller.cancel();
        }
        mic.stop();
        voice.shutdown();
        main.removeCallbacksAndMessages(null);
        // A rotation recreates the activity straight away; keep the models loaded for it.
        // release() is safe while swara-voice-stt is still inside transcribe(): the model handed out
        // by VoiceModels serialises close() behind a running transcription (on the loader thread,
        // so this never blocks the main thread), and refuses transcriptions queued after it.
        if (!activity.isChangingConfigurations()) models.release();
        worker.shutdown();
    }

    // --- debug typed entry ---

    /**
     * Long-press on a mic button. Debug builds open the typed voice-test dialog; release builds
     * do nothing. Returns whether the long-press was consumed.
     */
    @MainThread
    public boolean onMicLongPressed() {
        if (!BuildConfig.DEBUG || destroyed) return false;
        float density = activity.getResources().getDisplayMetrics().density;
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setHint(R.string.voice_debug_hint);
        input.setSingleLine(true);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        android.widget.FrameLayout box = new android.widget.FrameLayout(activity);
        int pad = Math.round(20 * density);
        box.setPadding(pad, Math.round(8 * density), pad, 0);
        box.addView(input);
        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.voice_debug_title)
                        .setView(box)
                        .setPositiveButton(R.string.voice_debug_run, (d, w) -> {
                            String text = input.getText().toString().trim();
                            if (!text.isEmpty()) runTyped(text);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .create();
        input.setOnEditorActionListener((v, actionId, event) -> {
            String text = input.getText().toString().trim();
            dialog.dismiss();
            if (!text.isEmpty()) runTyped(text);
            return true;
        });
        dialog.show();
        input.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return true;
    }

    /**
     * Debug builds only: run a typed command straight through the executor (no STT), log the
     * intent, ranked candidates and outcome, and show the result in the sheet.
     */
    @MainThread
    public void runTyped(@NonNull String text) {
        if (!BuildConfig.DEBUG || destroyed) return;
        if (controller != null) controller.cancel();
        mic.stop();
        publish(SheetModel.of(SheetModel.Mode.THINKING));
        showSheet();
        worker.execute(() -> {
            LibraryIndex idx = libraryIndex();
            main.post(() -> {
                if (destroyed) return;
                VoiceIntent intent = IntentParser.parse(text);
                MatchResult match = idx == null ? MatchResult.none() : LibraryVoiceMatcher.match(intent, idx);
                VoiceOutcome out;
                try {
                    out = executor.handle(intent, idx);
                } catch (RuntimeException e) {
                    Log.d(TAG, "typed command threw", e);
                    out = VoiceOutcome.spoken(VoiceReplies.didntCatch());
                }
                String readout = debugReadout(text, intent, match, out, idx);
                Log.d(TAG, readout);
                typedChoicesPending = !out.choices.isEmpty();
                publish(new SheetModel(SheetModel.Mode.REPLY, 0f, out, null, readout));
            });
        });
    }

    private static String debugReadout(String text, VoiceIntent intent, MatchResult match,
                                       VoiceOutcome out, @Nullable LibraryIndex idx) {
        StringBuilder sb = new StringBuilder();
        sb.append("typed: \"").append(text).append("\"\n");
        sb.append("intent: ").append(intent.getType());
        if (!intent.getQuery().isEmpty()) sb.append(" query=\"").append(intent.getQuery()).append('"');
        if (intent.isArtistThenAlbum()) sb.append(" (artist, then album)");
        sb.append('\n');
        if (idx == null) sb.append("library: not loaded\n");
        sb.append("confidence: ").append(match.confidence).append('\n');
        for (int i = 0; i < match.ranked.size(); i++) {
            Candidate c = match.ranked.get(i);
            sb.append(String.format(Locale.ROOT, "  %d. %-6s %.3f  %s", i + 1, c.kind, c.score, c.display));
            if (c.detail != null && !c.detail.isEmpty()) sb.append(" · ").append(c.detail);
            sb.append(" (").append(c.songs.size()).append(" songs)\n");
        }
        sb.append("outcome: \"").append(out.text).append('"');
        sb.append(out.speak ? " [spoken]" : " [silent]");
        if (!out.choices.isEmpty()) sb.append(" choices=").append(out.choices.size());
        return sb.toString();
    }

    // --- session ---

    private void startWhenReady() {
        if (models.isReady()) {
            startSession();
            return;
        }
        if (models.failed()) {
            refreshMicVisibility();
            showMessage(activity.getString(R.string.voice_unavailable));
            return;
        }
        pendingStart = true;
        publish(SheetModel.of(SheetModel.Mode.PREPARING));
        showSheet();
        models.loadAsync(() -> {
            if (destroyed) return;
            refreshMicVisibility();
            boolean go = pendingStart;
            pendingStart = false;
            if (!go) return;
            if (models.isReady()) {
                startSession();
            } else {
                showMessage(activity.getString(R.string.voice_unavailable));
            }
        });
    }

    private void startSession() {
        VoiceSessionController c = ensureController();
        if (c == null) {
            showMessage(activity.getString(R.string.voice_unavailable));
            return;
        }
        typedChoicesPending = false;
        if (!mic.start(micListener)) {
            showMessage(VoiceReplies.micBusy());
            return;
        }
        if (!c.startCommand()) {
            mic.stop();
            showSheet();
            return;
        }
        // Build/refresh the library index on the STT thread. The worker is single-threaded and this
        // is queued before the session's transcription task, so it is ready by the time the
        // controller reads cachedLibraryIndex() on the main thread.
        worker.execute(this::libraryIndex);
        showSheet();
    }

    private final MicSource.Listener micListener = new MicSource.Listener() {
        @Override public void onFrame(float[] frame1280) {
            VoiceSessionController c = controller;
            if (c != null) c.onFrame(frame1280);
        }

        @Override public void onError(Exception e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "mic error", e);
            main.post(() -> {
                if (destroyed) return;
                if (controller != null) controller.cancel();
                mic.stop();
                showMessage(VoiceReplies.micBusy());
            });
        }
    };

    @Nullable
    private VoiceSessionController ensureController() {
        if (controller != null) return controller;
        final SpeechToText stt = models.stt();
        if (stt == null) return null;
        VoiceSessionController.Deps deps = new VoiceSessionController.Deps(
                stt,
                executor,
                voice,
                this::cachedLibraryIndex, // main thread: never builds, see startSession()
                playback::duckForVoice,
                playback::unduckForVoice,
                worker,
                main::post,
                () -> true,
                (r, delayMs) -> {
                    main.postDelayed(r, delayMs);
                    return () -> main.removeCallbacks(r);
                });
        controller = new VoiceSessionController(deps);
        controller.setListener(this);
        return controller;
    }

    @Override
    public void onState(VoiceState s, @Nullable VoiceOutcome outcome, float level) {
        switch (s) {
            case LISTENING:
                publish(new SheetModel(SheetModel.Mode.LISTENING, level, null, null, null));
                break;
            case THINKING:
                mic.stop();
                publish(SheetModel.of(SheetModel.Mode.THINKING));
                break;
            case REPLY:
                mic.stop();
                if (BuildConfig.DEBUG && outcome != null) {
                    Log.d(TAG, "reply \"" + outcome.text + "\" choices=" + outcome.choices);
                }
                publish(new SheetModel(SheetModel.Mode.REPLY, 0f, outcome, null, null));
                break;
            case IDLE:
            default:
                mic.stop();
                SheetModel m = sheet.getValue();
                // After a reply the sheet keeps showing it until its own auto-dismiss; choices
                // from a cancelled session are dead, so those close.
                if (m == null || m.mode != SheetModel.Mode.REPLY || (m.hasChoices() && !typedChoicesPending)) {
                    publish(SheetModel.of(SheetModel.Mode.HIDDEN));
                }
                break;
        }
    }

    // --- library index ---

    /** Main thread safe: the last built index, or null. Never runs LibraryIndex.build. */
    @Nullable
    private LibraryIndex cachedLibraryIndex() {
        synchronized (indexLock) {
            return index;
        }
    }

    /** Worker thread only. Rebuilds only when the repository's cached library changed. */
    @Nullable
    private LibraryIndex libraryIndex() {
        synchronized (indexLock) {
            int v = MusicRepository.getLibraryCacheVersion();
            com.psthetech.swara.data.repository.ArtistAliasRepository aliases =
                    com.psthetech.swara.data.repository.ArtistAliasRepository.get(activity);
            com.psthetech.swara.data.repository.ArtistAliasRepository.Snapshot snap = aliases.snapshot();
            com.psthetech.swara.util.ArtistAliasResolver resolver = snap.resolver;
            long aliasVersion = snap.version;
            if (index != null && v == indexVersion && aliasVersion == indexAliasVersion) return index;
            List<Song> songs = MusicRepository.getCachedLibrarySongs();
            if (songs.isEmpty()) return index; // not loaded yet (or keep the last good one)
            long t0 = System.nanoTime();
            index = LibraryIndex.build(songs, resolver);
            indexVersion = v;
            indexAliasVersion = aliasVersion;
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "library index built: " + songs.size() + " songs in "
                        + (System.nanoTime() - t0) / 1_000_000 + " ms");
            }
            return index;
        }
    }

    // --- helpers ---

    private void refreshMicVisibility() {
        boolean visible = !models.failed();
        if (!Boolean.valueOf(visible).equals(micVisible.getValue())) micVisible.setValue(visible);
    }

    private void showMessage(String text) {
        publish(new SheetModel(SheetModel.Mode.MESSAGE, 0f, null, text, null));
        showSheet();
    }

    private void publish(SheetModel m) {
        sheet.setValue(m);
    }

    private void showSheet() {
        if (destroyed) return;
        FragmentManager fm = activity.getSupportFragmentManager();
        if (fm.isStateSaved()) return;
        androidx.fragment.app.Fragment existing = fm.findFragmentByTag(VoiceSheetFragment.TAG);
        // A sheet that is still being dismissed (its removal is async) doesn't count.
        if (existing instanceof VoiceSheetFragment && !((VoiceSheetFragment) existing).isDismissing()) return;
        new VoiceSheetFragment().show(fm, VoiceSheetFragment.TAG);
    }
}

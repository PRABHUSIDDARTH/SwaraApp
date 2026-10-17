package com.psthetech.swara.ui.debug;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.psthetech.swara.R;
import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.MoodOverride;
import com.psthetech.swara.data.db.entity.SongMood;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.vibe.android.MixRefreshWorker;
import com.psthetech.swara.vibe.android.MoodAnalysisWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Debug-only list of every analyzed song with its valence/energy, for calibration. */
public class MoodDebugActivity extends AppCompatActivity {

    private static final class Row {
        String title;
        String artist;
        double valence;
        double energy;
        double tempo;
        int status;
        /** User override label, or null. */
        String override;
    }

    private final List<Row> rows = new ArrayList<>();
    private boolean sortByValence = true;
    private ArrayAdapter<String> adapter;
    private TextView tvCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mood_debug);
        tvCount = findViewById(R.id.tvCount);
        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);

        findViewById(R.id.btnAnalyze).setOnClickListener(v -> MoodAnalysisWorker.enqueueNow(this));
        findViewById(R.id.btnRebuild).setOnClickListener(v -> MixRefreshWorker.refreshNow(this));
        findViewById(R.id.btnRefresh).setOnClickListener(v -> load());
        findViewById(R.id.btnSortValence).setOnClickListener(v -> { sortByValence = true; render(); });
        findViewById(R.id.btnSortEnergy).setOnClickListener(v -> { sortByValence = false; render(); });
        load();
    }

    private void load() {
        android.content.Context app = getApplicationContext();
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(app);
            List<SongMood> moods = new ArrayList<>(db.moodDao().getAll());
            java.util.Map<Long, String> overrides = new java.util.HashMap<>();
            for (MoodOverride o : db.moodOverrideDao().getAll()) overrides.put(o.songId, o.label);
            // Overridden songs without an analysis row still get a line.
            java.util.Set<Long> analyzed = new java.util.HashSet<>();
            for (SongMood m : moods) analyzed.add(m.songId);
            for (Long id : overrides.keySet()) {
                if (!analyzed.contains(id)) {
                    SongMood placeholder = SongMood.failed(id, 0, 0);
                    placeholder.status = -1; // not analyzed
                    moods.add(placeholder);
                }
            }
            MusicRepository repo = new MusicRepository(app);
            List<Row> loaded = new ArrayList<>();
            for (SongMood m : moods) {
                Song s = MusicRepository.getCanonicalSong(m.songId);
                if (s == null) {
                    try { s = repo.getSongById(m.songId); } catch (Exception ignored) { }
                }
                Row r = new Row();
                r.title = s != null ? s.getTitle() : "#" + m.songId;
                r.artist = s != null ? s.getArtist() : "?";
                r.valence = m.valence;
                r.energy = m.energy;
                r.tempo = m.tempoBpm;
                r.status = m.status;
                r.override = overrides.get(m.songId);
                loaded.add(r);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                rows.clear();
                rows.addAll(loaded);
                render();
            });
        });
    }

    private void render() {
        List<Row> sorted = new ArrayList<>(rows);
        final boolean byV = sortByValence;
        Collections.sort(sorted, new Comparator<Row>() {
            @Override public int compare(Row a, Row b) {
                return Double.compare(byV ? a.valence : a.energy, byV ? b.valence : b.energy);
            }
        });
        List<String> lines = new ArrayList<>();
        for (Row r : sorted) {
            String status = r.status == SongMood.STATUS_OK ? "OK"
                    : r.status == SongMood.STATUS_FAILED ? "FAILED" : "NOT ANALYZED";
            lines.add(String.format(Locale.US, "%s - %s\nv=%.2f  e=%.2f  tempo=%.0f  %s%s",
                    r.title, r.artist, r.valence, r.energy, r.tempo, status,
                    r.override != null ? "  override=" + r.override : ""));
        }
        adapter.clear();
        adapter.addAll(lines);
        adapter.notifyDataSetChanged();
        tvCount.setText(rows.size() + " songs, sorted by " + (byV ? "valence" : "energy") + " (ascending)");
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Document acquisition and lifecycle for the private desktop batch engine. */
public final class BatchScanActivity extends Activity {
    private static final int PICK_INPUTS = 1, PICK_EXPORT = 2;
    private State state;
    private TextView status, settings;
    private Button choose, cancel, results, export, saved, profiles, interpretation;
    private AlertDialog interpretationDialog;

    /**
     * Survives configuration recreation without restarting native work or retaining an Activity.
     */
    private static final class State {
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final Handler main = new Handler(Looper.getMainLooper());
        final Context context;
        volatile BatchScanActivity owner;
        volatile String
                message = "Choose 1–64 documents. Every selected input keeps its own outcome.",
                id;
        volatile boolean busy, picker, running, exportLaunchNeeded;
        volatile BatchScanSession.Control cancellation = new BatchScanSession.Control();
        PreparedExport prepared;
        ScanTarget.Request draftInterpretation = ScanTarget.AUTOMATIC;
        boolean interpretationReady = true;

        State(Context context) {
            this.context = context.getApplicationContext();
        }

        void changed(String text) {
            message = text;
            main.post(
                    () -> {
                        BatchScanActivity activity = owner;
                        if (activity != null && !activity.isDestroyed()) activity.present();
                    });
        }

        File root() {
            return new File(context.getFilesDir(), "desktop-batches");
        }

        File exports() {
            return new File(context.getCacheDir(), "batch-exports");
        }

        void start(List<Uri> uris, ScanOptions options, ScanTarget.Request interpretation) {
            busy = running = true;
            id = null;
            cancellation = new BatchScanSession.Control();
            changed(
                    "Preparing captured options / database and "
                            + uris.size()
                            + " input declarations…");
            worker.execute(
                    () -> {
                        try {
                            EngineDatabase database = EngineDatabase.prepare(context);
                            EvidenceRows.check(cancellation);
                            List<BatchScanSession.Source> sources = new ArrayList<>();
                            for (Uri uri : uris) {
                                String name = uri.getLastPathSegment();
                                if (name == null) name = "Unnamed document";
                                try (Cursor cursor =
                                        context.getContentResolver()
                                                .query(
                                                        uri,
                                                        new String[] {OpenableColumns.DISPLAY_NAME},
                                                        null,
                                                        null,
                                                        null)) {
                                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                                        name = cursor.getString(0);
                                } catch (Exception ignored) {
                                    /* A missing label does not prevent acquisition. */
                                }
                                sources.add(
                                        new BatchScanSession.Source(
                                                name,
                                                uri.toString(),
                                                () ->
                                                        context.getContentResolver()
                                                                .openInputStream(uri)));
                            }
                            BatchScanSession session =
                                    BatchScanSession.create(
                                            root(),
                                            sources,
                                            options,
                                            interpretation,
                                            database.engineRevision,
                                            database.signatureRevision,
                                            database.customDigest);
                            id = session.id;
                            session.run(
                                    sources,
                                    new DesktopBatchAnalyzer(database, options, interpretation),
                                    cancellation,
                                    this::changed);
                            changed(
                                    "Batch settled • "
                                            + session.metadata().getJSONObject("outcomes")
                                            + " • inspect Results for every scope and diagnostic");
                        } catch (Exception | LinkageError failure) {
                            changed(
                                    "Batch stopped: "
                                            + failure
                                            + " • any saved journal remains available; inputs are"
                                            + " not resumed automatically");
                        } finally {
                            busy = running = false;
                            changed(message);
                        }
                    });
        }
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Object retained = getLastNonConfigurationInstance();
        state = retained instanceof State ? (State) retained : new State(this);
        state.owner = this;
        if (retained == null && savedState != null) {
            state.id = savedState.getString("batchId");
            try {
                String savedRequest = savedState.getString("interpretation");
                if (savedRequest != null)
                    state.draftInterpretation = ScanTarget.Request.restore(savedRequest);
                state.interpretationReady = savedState.getBoolean("interpretationReady", true);
            } catch (Exception failure) {
                state.interpretationReady = false;
                state.message =
                        "Saved interpretation is invalid; choose the next batch interpretation"
                            + " again: "
                                + failure;
            }
            state.picker = savedState.getBoolean("picker");
            state.exportLaunchNeeded = savedState.getBoolean("exportLaunchNeeded");
            if (savedState.getString("prepared") != null)
                try {
                    state.prepared =
                            PreparedExport.restore(
                                    state.exports(),
                                    savedState.getString("prepared"),
                                    "die-desktop-batch.json",
                                    "application/json",
                                    savedState.getLong("preparedSize"));
                } catch (IOException failure) {
                    state.picker = false;
                    state.message = "Prepared batch export expired; prepare it again";
                }
            if (savedState.getBoolean("running"))
                state.message =
                        "The previous process stopped during this batch. Inspect its saved journal;"
                                + " it is not automatically resumed.";
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        TextView title = WorkbenchUi.section(this, "Desktop batch scanning");
        root.addView(title);
        TextView context = WorkbenchUi.prose(this, "");
        context.setText(
                "Serial CDiE signature scans, native facts, whole-file SHA-256 and bounded generic"
                    + " previews. Options and database revisions are captured once. Each document"
                    + " is privately copied before analysis. 256 MiB/file, 512 MiB/batch; 16"
                    + " MiB/report, 64 MiB stored reports. Cancellation is cooperative. Results are"
                    + " static observations, not safety or execution verdicts. Saved batches keep"
                    + " their inputs until you delete them; exports contain sensitive local"
                    + " information.");
        root.addView(context);
        settings = WorkbenchUi.text(this, "", 13);
        root.addView(settings);
        interpretation = button("Next batch interpretation", this::chooseInterpretation);
        root.addView(interpretation);
        profiles =
                button(
                        "Saved scan option profiles",
                        () -> startActivity(new Intent(this, ScanProfilesActivity.class)));
        root.addView(profiles);
        choose = button("Choose files / start batch", this::chooseFiles);
        root.addView(choose);
        cancel =
                button(
                        "Cancel this batch",
                        () -> {
                            state.cancellation.cancel();
                            state.changed(
                                    "Cancellation requested • waiting for this batch's current"
                                            + " operation to settle");
                        });
        root.addView(cancel);
        results = button("Inspect captured results", this::openResults);
        root.addView(results);
        export = button("Export complete batch JSON", this::prepareExport);
        root.addView(export);
        saved = button("Saved batches / open / delete", this::savedBatches);
        root.addView(saved);
        status = WorkbenchUi.text(this, "", 13);
        status.setTextIsSelectable(true);
        root.addView(status);
        ScrollView scroll = WorkbenchUi.scroll(this);
        scroll.addView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                        return insets;
                    });
        }
        setContentView(scroll);
        present();
    }

    @Override
    protected void onResume() {
        super.onResume();
        present();
    }

    private void present() {
        if (status == null) return;
        status.setText(state.message);
        ScanOptions options = ScanPreferences.load(this);
        settings.setText(
                "Next batch: flags="
                        + options.flags
                        + " • string minimum="
                        + options.minimumStringLength
                        + " • include="
                        + options.includeTags
                        + " • exclude="
                        + options.excludeTags
                        + " • interpretation="
                        + state.draftInterpretation.label()
                        + "\nSaved reports retain the settings captured when their batch started.");
        boolean available = !state.busy && !state.picker;
        choose.setEnabled(available && state.interpretationReady);
        interpretation.setEnabled(available);
        interpretation.setText(
                "Next batch interpretation: " + state.draftInterpretation.label() + " • choose…");
        saved.setEnabled(available);
        profiles.setEnabled(available);
        cancel.setEnabled(state.busy);
        results.setEnabled(available && state.id != null);
        export.setEnabled(available && state.id != null);
        if (state.exportLaunchNeeded && state.prepared != null && state.owner == this) {
            state.exportLaunchNeeded = false;
            startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, "die-desktop-batch.json"),
                    PICK_EXPORT);
        }
    }

    private void chooseInterpretation() {
        if (state.busy || state.picker) return;
        State owner = state;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding, padding, padding);
        TextView explanation = WorkbenchUi.prose(this, "");
        explanation.setText(
                "Automatic lets CDiE choose for every file. A required exact native format name"
                    + " (for example Binary) is resolved separately against each acquired input's"
                    + " compatible catalog. Unavailable or ambiguous names retain a failed report;"
                    + " they never fall back to Automatic. Copy names from a primary scan's"
                    + " interpretation choices. This does not force arbitrary formats or validate"
                    + " their structures.");
        form.addView(explanation);
        EditText name = new EditText(this);
        name.setTag("batch-interpretation-name");
        name.setSingleLine(true);
        name.setHint("Exact compatible native format name");
        name.setText(owner.draftInterpretation.formatName);
        form.addView(name);
        interpretationDialog =
                new AlertDialog.Builder(this)
                        .setTitle("Next batch interpretation")
                        .setView(form)
                        .setPositiveButton("Require name", null)
                        .setNeutralButton(
                                "Automatic",
                                (dialog, which) -> applyInterpretation(owner, ScanTarget.AUTOMATIC))
                        .setNegativeButton("Cancel", null)
                        .show();
        interpretationDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        view -> {
                            try {
                                ScanTarget.Request request =
                                        new ScanTarget.Request(name.getText().toString());
                                if (request.automatic())
                                    throw new IllegalArgumentException(
                                            "Enter a name or choose Automatic");
                                applyInterpretation(owner, request);
                                interpretationDialog.dismiss();
                            } catch (IllegalArgumentException failure) {
                                name.setError(failure.getMessage());
                            }
                        });
    }

    private void applyInterpretation(State owner, ScanTarget.Request request) {
        if (state != owner || owner.owner != this || owner.busy || owner.picker) return;
        owner.draftInterpretation = request;
        owner.interpretationReady = true;
        owner.changed(
                "Next batch interpretation: "
                        + request.label()
                        + " • saved batches retain their captured request");
    }

    private void chooseFiles() {
        if (state.busy || state.picker || !state.interpretationReady) return;
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        state.picker = true;
        present();
        startActivityForResult(intent, PICK_INPUTS);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_INPUTS && request != PICK_EXPORT) return;
        state.picker = false;
        if (request == PICK_INPUTS) {
            if (result == RESULT_OK && data != null) {
                List<Uri> inputs = new ArrayList<>();
                ClipData clip = data.getClipData();
                if (clip != null) {
                    if (clip.getItemCount() > BatchScanSession.MAX_INPUTS) {
                        state.changed("More than 64 files selected; no batch started");
                        return;
                    }
                    for (int i = 0; i < clip.getItemCount(); i++)
                        inputs.add(clip.getItemAt(i).getUri());
                } else if (data.getData() != null) inputs.add(data.getData());
                if (inputs.isEmpty() || inputs.contains(null))
                    state.changed("The picker returned no valid document plan");
                else if (!state.interpretationReady)
                    state.changed("Choose a valid batch interpretation before starting");
                else
                    state.start(
                            Collections.unmodifiableList(inputs),
                            ScanPreferences.load(this),
                            state.draftInterpretation);
            }
            present();
            return;
        }
        PreparedExport captured = state.prepared;
        state.prepared = null;
        if (captured == null) {
            state.changed("No prepared batch export remains");
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) {
            captured.discard();
            state.changed("Batch export cancelled; saved information retained");
            return;
        }
        Uri destination = data.getData();
        state.busy = true;
        state.cancellation = new BatchScanSession.Control();
        state.changed("Writing the already captured complete batch export…");
        State owner = state;
        owner.worker.execute(
                () -> {
                    try (OutputStream output =
                            owner.context
                                    .getContentResolver()
                                    .openOutputStream(destination, "wt")) {
                        if (output == null)
                            throw new IOException("Destination returned no output stream");
                        captured.copyTo(output, owner.cancellation);
                        owner.changed("Complete batch JSON exported");
                    } catch (Exception failure) {
                        owner.changed("Export failed; destination may be partial: " + failure);
                    } finally {
                        captured.discard();
                        owner.busy = false;
                        owner.changed(owner.message);
                    }
                });
    }

    private void prepareExport() {
        if (state.busy || state.picker || state.id == null) return;
        State owner = state;
        String id = owner.id;
        owner.busy = true;
        owner.cancellation = new BatchScanSession.Control();
        owner.changed("Staging the complete captured batch before opening the destination picker…");
        owner.worker.execute(
                () -> {
                    PreparedExport prepared = null;
                    try {
                        BatchScanSession session =
                                BatchScanSession.open(owner.root(), id, owner.cancellation);
                        prepared =
                                PreparedExport.stream(
                                        owner.exports(),
                                        "die-desktop-batch.json",
                                        "application/json",
                                        out -> session.writeReport(out, owner.cancellation),
                                        owner.cancellation);
                        EvidenceRows.check(owner.cancellation);
                        owner.prepared = prepared;
                        owner.picker = true;
                        owner.exportLaunchNeeded = true;
                        owner.busy = false;
                        owner.changed("Complete batch captured; choose an export destination");
                    } catch (Exception failure) {
                        if (prepared != null) prepared.discard();
                        owner.busy = false;
                        owner.changed("Cannot stage complete batch: " + failure);
                    }
                });
    }

    private void openResults() {
        if (state.id == null || state.busy || state.picker) return;
        startActivity(
                new Intent(this, InvestigationActivity.class)
                        .putExtra("provider", "desktop-batch")
                        .putExtra("batchId", state.id)
                        .putExtra("name", "Captured desktop batch")
                        .putExtra(
                                "path",
                                new File(state.root(), "batch-" + state.id + "/state.json")
                                        .getAbsolutePath()));
    }

    private void savedBatches() {
        if (state.busy || state.picker) return;
        State owner = state;
        owner.busy = true;
        owner.changed("Reading saved batch journals…");
        owner.worker.execute(
                () -> {
                    List<BatchScanSession> sessions = new ArrayList<>();
                    List<String> labels = new ArrayList<>();
                    File[] directories =
                            owner.root()
                                    .listFiles(
                                            file ->
                                                    file.isDirectory()
                                                            && file.getName().startsWith("batch-"));
                    if (directories != null) {
                        Arrays.sort(directories, Comparator.comparing(File::getName));
                        for (File directory : directories)
                            try {
                                BatchScanSession session =
                                        BatchScanSession.open(
                                                owner.root(),
                                                directory.getName().substring(6),
                                                () -> false);
                                sessions.add(session);
                                labels.add(
                                        session.createdAt
                                                + " • "
                                                + session.entries.size()
                                                + " inputs • "
                                                + session.id);
                            } catch (Exception failure) {
                                // Retain a visible diagnostic; never silently delete damaged
                                // evidence.
                                labels.add(directory.getName() + " • unavailable: " + failure);
                                sessions.add(null);
                            }
                    }
                    owner.busy = false;
                    owner.changed(
                            labels.isEmpty()
                                    ? "No saved batch journals yet"
                                    : "Choose a saved batch to inspect or delete");
                    owner.main.post(
                            () -> {
                                BatchScanActivity activity = owner.owner;
                                if (activity == null || activity.isDestroyed() || labels.isEmpty())
                                    return;
                                new AlertDialog.Builder(activity)
                                        .setTitle("Saved desktop batches")
                                        .setItems(
                                                labels.toArray(new String[0]),
                                                (dialog, selected) -> {
                                                    BatchScanSession session =
                                                            sessions.get(selected);
                                                    if (session == null) {
                                                        String damagedId =
                                                                directories[selected]
                                                                        .getName()
                                                                        .substring(6);
                                                        new AlertDialog.Builder(activity)
                                                                .setTitle("Unavailable saved batch")
                                                                .setMessage(
                                                                        labels.get(selected)
                                                                                + "\n"
                                                                                + "Delete this"
                                                                                + " private batch's"
                                                                                + " owned"
                                                                                + " directory?"
                                                                                + " Exported"
                                                                                + " documents"
                                                                                + " remain"
                                                                                + " independent.")
                                                                .setPositiveButton(
                                                                        "Delete batch",
                                                                        (d, w) ->
                                                                                activity.delete(
                                                                                        damagedId))
                                                                .setNegativeButton("Keep", null)
                                                                .show();
                                                        return;
                                                    }
                                                    new AlertDialog.Builder(activity)
                                                            .setTitle(
                                                                    "Captured batch " + session.id)
                                                            .setMessage(
                                                                    "Inputs and reports remain"
                                                                        + " private until deletion."
                                                                        + " Deletion removes this"
                                                                        + " saved batch's owned"
                                                                        + " bytes; exported"
                                                                        + " documents are"
                                                                        + " independent.")
                                                            .setPositiveButton(
                                                                    "Open",
                                                                    (d, w) -> {
                                                                        owner.id = session.id;
                                                                        activity.present();
                                                                        activity.openResults();
                                                                    })
                                                            .setNeutralButton(
                                                                    "Delete batch",
                                                                    (d, w) ->
                                                                            activity.delete(
                                                                                    session))
                                                            .setNegativeButton("Cancel", null)
                                                            .show();
                                                })
                                        .setNegativeButton("Close", null)
                                        .show();
                            });
                });
    }

    private void delete(BatchScanSession session) {
        delete(session.id);
    }

    private void delete(String id) {
        State owner = state;
        owner.busy = true;
        owner.changed("Deleting this saved batch's owned information…");
        owner.worker.execute(
                () -> {
                    try {
                        BatchScanSession.delete(owner.root(), id);
                        if (id.equals(owner.id)) owner.id = null;
                        owner.changed("Saved batch deleted");
                    } catch (Exception failure) {
                        owner.changed("Cannot delete saved batch: " + failure);
                    } finally {
                        owner.busy = false;
                        owner.changed(owner.message);
                    }
                });
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return state;
    }

    @Override
    protected void onSaveInstanceState(Bundle output) {
        super.onSaveInstanceState(output);
        output.putString("batchId", state.id);
        output.putBoolean("interpretationReady", state.interpretationReady);
        try {
            output.putString("interpretation", state.draftInterpretation.toJson().toString());
        } catch (Exception failure) {
            output.putBoolean("interpretationReady", false);
        }
        output.putBoolean("picker", state.picker);
        output.putBoolean("running", state.running);
        output.putBoolean("exportLaunchNeeded", state.exportLaunchNeeded);
        if (state.prepared != null) {
            output.putString("prepared", state.prepared.file.getAbsolutePath());
            output.putLong("preparedSize", state.prepared.size);
        }
    }

    @Override
    protected void onDestroy() {
        if (interpretationDialog != null) interpretationDialog.dismiss();
        if (state.owner == this) state.owner = null;
        if (!isChangingConfigurations()) {
            state.cancellation.cancel();
            if (state.prepared != null && !state.picker) state.prepared.discard();
            state.worker.shutdown();
        }
        super.onDestroy();
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-controlled viewing/copy/export/clear of bounded, private program diagnostics. */
public final class DebugLogActivity extends Activity {
    private static final int EXPORT = 1;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private TextView status;
    private ListView list;
    private Button refresh, copy, export, clear;
    private DebugJournal.Snapshot snapshot;
    private File exportDirectory;
    private PreparedExport prepared;
    private boolean busy, pickerOpen;
    private int generation;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Private program debug log");
        exportDirectory = new File(getCacheDir(), "debug-log-exports");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = WorkbenchUi.dp(this, 10);
        root.setPadding(padding, padding, padding, padding);
        root.addView(WorkbenchUi.text(this, "Private program debug log", 20));
        root.addView(
                WorkbenchUi.prose(
                        this,
                        "Operational metadata only · no file contents, names, paths or text"
                            + " queries. Kept in private storage excluded from backup. Copy/save"
                            + " only when you choose. Refresh to see newer events."));
        root.addView(
                WorkbenchUi.text(
                        this,
                        "Retention: latest "
                                + DebugJournal.MAX_ENTRIES
                                + " entries / "
                                + (DebugJournal.MAX_BYTES / 1024)
                                + " KiB; older entries are removed.",
                        12));
        LinearLayout actions = new LinearLayout(this);
        refresh = button("Refresh", "debug-refresh", () -> load("Log refreshed"));
        copy = button("Copy", "debug-copy", () -> new TextActions(this).copy(snapshotText()));
        export = button("Save log…", "debug-export", this::exportLog);
        clear = button("Clear", "debug-clear", this::clearLog);
        for (Button button : new Button[] {refresh, copy, export, clear})
            actions.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        status = WorkbenchUi.text(this, "Loading private debug log…", 12);
        status.setTag("debug-status");
        root.addView(status);
        list = WorkbenchUi.scrolling(new ListView(this));
        list.setTag("debug-rows");
        WorkbenchUi.list(this, list);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener(
                (parent, view, position, id) -> {
                    if (snapshot == null || position >= snapshot.entries.size()) return;
                    DebugJournal.Entry entry = snapshot.entries.get(position);
                    TextView details = WorkbenchUi.prose(this, entry.line());
                    details.setPadding(padding, padding, padding, padding);
                    new AlertDialog.Builder(this)
                            .setTitle("Program diagnostic")
                            .setView(details)
                            .setNeutralButton(
                                    "Copy entry",
                                    (dialog, which) -> new TextActions(this).copy(entry.line()))
                            .setPositiveButton("Close", null)
                            .show();
                });
        setContentView(root);

        String initialMessage = "";
        if (state != null && state.getString("exportPath") != null) {
            try {
                prepared =
                        PreparedExport.restore(
                                exportDirectory,
                                state.getString("exportPath"),
                                state.getString("exportName"),
                                state.getString("exportMime"),
                                state.getLong("exportSize"));
                pickerOpen = true;
            } catch (IOException unavailable) {
                initialMessage = "Pending debug export is unavailable; save again";
            }
        }
        load(initialMessage);
    }

    private Button button(String label, String tag, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTag(tag);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void controls() {
        boolean ready = !busy && !pickerOpen;
        refresh.setEnabled(ready);
        copy.setEnabled(ready && snapshot != null && snapshot.available);
        export.setEnabled(ready && snapshot != null && snapshot.available);
        clear.setEnabled(ready);
    }

    private void load(String message) {
        if (busy) return;
        final int ticket = ++generation;
        busy = true;
        controls();
        worker.execute(
                () -> {
                    DebugJournal.Snapshot result = DebugJournal.read(getApplicationContext());
                    runOnUiThread(
                            () -> {
                                if (isDestroyed() || ticket != generation) return;
                                busy = false;
                                snapshot = result;
                                render(message);
                            });
                });
    }

    private void render(String message) {
        list.setAdapter(
                WorkbenchUi.adapter(
                        this,
                        snapshot.entries,
                        entry ->
                                new WorkbenchUi.Lines(this)
                                        .heading(entry.event)
                                        .code(entry.timestamp)
                                        .line(entry.detail)
                                        .build()));
        String empty =
                snapshot.available && snapshot.entries.isEmpty()
                        ? "\nNo events recorded. Run an analysis, then refresh."
                        : "";
        status.setText((message.isEmpty() ? "" : message + "\n") + snapshot.status + empty);
        controls();
    }

    private String snapshotText() {
        return AboutActivity.buildDetails() + "\n\n" + snapshot.text();
    }

    private void clearLog() {
        if (busy || pickerOpen) return;
        final int ticket = ++generation;
        busy = true;
        status.setText("Clearing private debug log…");
        controls();
        worker.execute(
                () -> {
                    boolean removed = DebugJournal.clear(getApplicationContext());
                    DebugJournal.Snapshot result = DebugJournal.read(getApplicationContext());
                    runOnUiThread(
                            () -> {
                                if (isDestroyed() || ticket != generation) return;
                                busy = false;
                                snapshot = result;
                                render(
                                        removed
                                                ? "Debug log cleared"
                                                : "Debug log could not be cleared");
                            });
                });
    }

    private void exportLog() {
        if (busy || pickerOpen || snapshot == null || !snapshot.available) return;
        final String captured = snapshotText();
        final int ticket = ++generation;
        busy = true;
        status.setText("Preparing displayed debug log…");
        controls();
        worker.execute(
                () -> {
                    PreparedExport staged = null;
                    try {
                        staged =
                                PreparedExport.stream(
                                        exportDirectory,
                                        "glassdigestor-debug-log.txt",
                                        "text/plain",
                                        output -> output.write(captured),
                                        closed::get);
                        PreparedExport ready = staged;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) {
                                        ready.discard();
                                        return;
                                    }
                                    prepared = ready;
                                    busy = false;
                                    pickerOpen = true;
                                    controls();
                                    try {
                                        startActivityForResult(
                                                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                                        .setType(ready.mime)
                                                        .putExtra(Intent.EXTRA_TITLE, ready.name),
                                                EXPORT);
                                        status.setText(
                                                "Choose a destination for the displayed log"
                                                    + " snapshot");
                                    } catch (RuntimeException unavailable) {
                                        ready.discard();
                                        prepared = null;
                                        pickerOpen = false;
                                        status.setText(
                                                "No document picker accepted the export. Copy the"
                                                    + " log or retry.");
                                        controls();
                                    }
                                });
                    } catch (Exception failure) {
                        if (staged != null) staged.discard();
                        complete(
                                ticket,
                                "Debug log staging failed ("
                                        + failure.getClass().getSimpleName()
                                        + "); copy the log or retry");
                    }
                });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT) return;
        PreparedExport captured = prepared;
        prepared = null;
        pickerOpen = false;
        if (captured == null) {
            status.setText("Prepared debug export is unavailable; save again");
            controls();
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) {
            captured.discard();
            status.setText("Debug log export cancelled");
            controls();
            return;
        }
        Uri destination = data.getData();
        final int ticket = ++generation;
        busy = true;
        status.setText("Writing displayed log snapshot…");
        controls();
        worker.execute(
                () -> {
                    String message;
                    try (OutputStream output =
                            getContentResolver().openOutputStream(destination, "wt")) {
                        if (output == null) throw new IOException("Destination unavailable");
                        captured.copyTo(output, closed::get);
                        message = "Debug log saved · " + captured.size + " bytes";
                    } catch (Exception failure) {
                        message =
                                "Debug log export failed ("
                                        + failure.getClass().getSimpleName()
                                        + "); destination may contain partial data";
                    } finally {
                        captured.discard();
                    }
                    complete(ticket, message);
                });
    }

    private void complete(int ticket, String message) {
        runOnUiThread(
                () -> {
                    if (isDestroyed() || ticket != generation) return;
                    busy = false;
                    if (snapshot == null) {
                        load(message);
                        return;
                    }
                    status.setText(message);
                    controls();
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (prepared != null) {
            state.putString("exportPath", prepared.file.getAbsolutePath());
            state.putString("exportName", prepared.name);
            state.putString("exportMime", prepared.mime);
            state.putLong("exportSize", prepared.size);
        }
    }

    @Override
    protected void onDestroy() {
        closed.set(true);
        worker.shutdown();
        if (prepared != null && (!pickerOpen || isFinishing())) prepared.discard();
        super.onDestroy();
    }
}

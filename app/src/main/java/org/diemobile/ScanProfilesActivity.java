// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Named option snapshots shared by Scan and the desktop laboratory. */
public final class ScanProfilesActivity extends Activity {
    private static final int IMPORT = 1, EXPORT = 2;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private ScanProfileStore store;
    private ScanProfiles catalog;
    private TextView status;
    private ListView list;
    private Button save, importButton, exportButton, reloadButton;
    private File exports;
    private PreparedExport prepared;
    private String pendingImportRevision;
    private boolean busy, pickerOpen;
    private int generation;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Button button(String title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView text(String value, int size) {
        TextView view = WorkbenchUi.text(this,value,size);
        view.setTextIsSelectable(true);
        return view;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        exports = new File(getCacheDir(), "profile-exports");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(text("Saved scan option profiles", 20));
        root.addView(
                text(
                        "Capture the shared flags, tag filters and string minimum. Applying a"
                            + " profile updates Scan and the laboratory; rescan to collect new"
                            + " information. Engine/database identities are captured when scanning.",
                        13));
        save = button("Save current shared settings", () -> edit(null, false));
        root.addView(save);
        LinearLayout actions = new LinearLayout(this);
        importButton = button("Import / replace catalog", this::importCatalog);
        exportButton = button("Export complete catalog", this::exportCatalog);
        actions.addView(importButton, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(exportButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        reloadButton = button("Reload saved catalog", this::load);
        root.addView(reloadButton);
        status = text("Loading private profile catalog…", 12);
        root.addView(status);
        list = WorkbenchUi.scrolling(new ListView(this));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        list.setOnItemClickListener(
                (parent, view, index, id) -> {
                    if (!busy && !pickerOpen && catalog != null)
                        details(catalog.profiles.get(index));
                });
        if (state != null) {
            pendingImportRevision = state.getString("pendingImportRevision");
            pickerOpen = pendingImportRevision != null;
            String path = state.getString("exportPath");
            if (path != null)
                try {
                    prepared =
                            PreparedExport.restore(
                                    exports,
                                    path,
                                    state.getString("exportName"),
                                    state.getString("exportMime"),
                                    state.getLong("exportSize"));
                    pickerOpen = true;
                } catch (IOException error) {
                    status.setText("Pending profile export is unavailable: " + error.getMessage());
                }
        }
        try {
            store = new ScanProfileStore(new File(getFilesDir(), "scan-profiles"));
            load();
        } catch (IOException error) {
            status.setText(error.getMessage());
            controls();
        }
    }

    private void controls() {
        boolean available = !busy && !pickerOpen && catalog != null;
        save.setEnabled(available);
        importButton.setEnabled(available);
        exportButton.setEnabled(available);
        list.setEnabled(available);
        reloadButton.setEnabled(!busy && !pickerOpen && store != null);
    }

    private void render(String message) {
        List<String> rows = new ArrayList<>();
        for (ScanProfiles.Profile profile : catalog.profiles)
            rows.add(
                    profile.name
                            + "\n"
                            + profile.id
                            + " • flags "
                            + String.format(Locale.ROOT, "0x%04X", profile.options.flags)
                            + " • minimum "
                            + profile.options.minimumStringLength);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
        status.setText(
                message
                        + "\n"
                        + catalog.profiles.size()
                        + " profiles • catalog SHA-256 "
                        + catalog.sha256);
        controls();
    }

    private interface CatalogWork {
        ScanProfiles run() throws Exception;
    }

    private void perform(String progress, CatalogWork task) {
        perform(progress, task, false);
    }

    private void perform(String progress, CatalogWork task, boolean allowPicker) {
        if (busy || pickerOpen && !allowPicker) return;
        final int ticket = ++generation;
        busy = true;
        status.setText(progress);
        controls();
        worker.execute(
                () -> {
                    try {
                        ScanProfiles result = task.run();
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed()) return;
                                    catalog = result;
                                    if (ticket != generation) return;
                                    busy = false;
                                    render("Profile catalog ready");
                                });
                    } catch (Exception error) {
                        failed(ticket, "Profile operation failed: " + error.getMessage());
                    }
                });
    }

    private void failed(int ticket, String message) {
        runOnUiThread(
                () -> {
                    if (isDestroyed() || ticket != generation) return;
                    busy = false;
                    status.setText(message);
                    controls();
                });
    }

    private void load() {
        perform("Loading private profile catalog…", () -> store.load(closed::get), true);
    }

    static String settings(ScanOptions options) {
        List<String> enabled = new ArrayList<>();
        for (int i = 0; i < ScanOptions.LABELS.length; i++)
            if ((options.flags & (1 << i)) != 0) enabled.add(ScanOptions.LABELS[i]);
        return "Flags: "
                + String.format(Locale.ROOT, "0x%04X", options.flags)
                + " • "
                + String.join("; ", enabled)
                + "\nMinimum preview string length: "
                + options.minimumStringLength
                + "\nInclude tags: "
                + options.includeTags
                + "\nExclude tags: "
                + options.excludeTags;
    }

    private void details(ScanProfiles.Profile profile) {
        String capturedRevision = catalog.sha256;
        String[] actions = {
            "Apply captured settings and return",
            "Rename / describe profile",
            "Replace settings with current shared options",
            "Delete profile",
            "Inspect complete profile record"
        };
        new AlertDialog.Builder(this)
                .setTitle(profile.name)
                .setItems(
                        actions,
                        (dialog, index) -> {
                            switch (index) {
                                case 0:
                                    ScanPreferences.save(this, profile.options);
                                    Toast.makeText(
                                                    this,
                                                    "Applied "
                                                            + profile.name
                                                            + " • rescan to refresh information",
                                                    Toast.LENGTH_LONG)
                                            .show();
                                    finish();
                                    break;
                                case 1:
                                    edit(profile, false);
                                    break;
                                case 2:
                                    edit(profile, true);
                                    break;
                                case 3:
                                    new AlertDialog.Builder(this)
                                            .setTitle("Delete saved profile?")
                                            .setMessage(profile.name + "\nID: " + profile.id)
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton(
                                                    "Delete",
                                                    (d, w) ->
                                                            perform(
                                                                    "Publishing profile deletion…",
                                                                    () ->
                                                                            store.delete(
                                                                                    capturedRevision,
                                                                                    profile.id,
                                                                                    closed::get)))
                                            .show();
                                    break;
                                case 4:
                                    show(
                                            "Profile record / effective settings",
                                            settings(profile.options)
                                                    + "\n\nID: "
                                                    + profile.id
                                                    + "\nRecord SHA-256: "
                                                    + profile.sha256
                                                    + "\n\n"
                                                    + profile.original);
                                    break;
                            }
                        })
                .show();
    }

    private void edit(ScanProfiles.Profile existing, boolean useCurrent) {
        if (catalog == null || busy || pickerOpen) return;
        String revision = catalog.sha256;
        ScanOptions captured =
                existing == null || useCurrent ? ScanPreferences.load(this) : existing.options;
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText name = new EditText(this), description = new EditText(this);
        name.setSingleLine();
        name.setHint("Profile name (256 UTF-16 units)");
        description.setHint("Description / purpose");
        description.setMinLines(2);
        if (existing != null) {
            name.setText(existing.name);
            description.setText(existing.description);
        }
        fields.addView(name);
        fields.addView(description);
        fields.addView(text(settings(captured), 12));
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                existing == null
                                        ? "Save current option profile"
                                        : "Update captured profile")
                        .setView(fields)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save", null)
                        .create();
        dialog.setOnShowListener(
                ignored ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        view -> {
                                            String title = name.getText().toString(),
                                                    detail = description.getText().toString();
                                            if (title.trim().isEmpty()
                                                    || title.length() > 256
                                                    || detail.length() > 16384) {
                                                name.setError(
                                                        "Name required, at most 256 units;"
                                                                + " description at most 16,384");
                                                return;
                                            }
                                            dialog.dismiss();
                                            perform(
                                                    "Publishing captured profile settings…",
                                                    () ->
                                                            store.save(
                                                                    revision,
                                                                    existing == null
                                                                            ? null
                                                                            : existing.id,
                                                                    title,
                                                                    detail,
                                                                    captured,
                                                                    closed::get));
                                        }));
        dialog.show();
    }

    private void show(String title, String value) {
        ScrollView scroll = WorkbenchUi.scroll(this);
        TextView content =
                text(
                        value.length() > 65536
                                ? value.substring(0, 65536)
                                        + "\n"
                                        + "[Preview limited to 65,536 UTF-16 units; catalog export"
                                        + " retains complete values.]"
                                : value,
                        12);
        content.setTypeface(Typeface.MONOSPACE);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void importCatalog() {
        if (catalog == null || busy || pickerOpen) return;
        pendingImportRevision = catalog.sha256;
        pickerOpen = true;
        controls();
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*");
        startActivityForResult(intent, IMPORT);
    }

    private void importFrom(Uri uri, String revision) {
        final int ticket = ++generation;
        busy = true;
        status.setText("Reading / validating complete profile catalog…");
        controls();
        worker.execute(
                () -> {
                    try {
                        ScanProfiles before = store.load(closed::get);
                        ScanProfiles incoming;
                        try (InputStream input = getContentResolver().openInputStream(uri)) {
                            if (input == null)
                                throw new IOException("Selected document is unavailable");
                            incoming = ScanProfiles.read(input, closed::get);
                        }
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    catalog = before;
                                    busy = false;
                                    render("Imported catalog validated; review before replacement");
                                    StringBuilder summary =
                                            new StringBuilder(
                                                    "Captured "
                                                            + incoming.profiles.size()
                                                            + " profiles\nSHA-256: "
                                                            + incoming.sha256
                                                            + "\n\n");
                                    for (int i = 0; i < Math.min(32, incoming.profiles.size()); i++)
                                        summary.append(incoming.profiles.get(i).name).append('\n');
                                    if (incoming.profiles.size() > 32)
                                        summary.append("[First 32 names shown]\n");
                                    summary.append(
                                            "\n"
                                                + "Replace the saved catalog with these captured"
                                                + " bytes. Apply a profile to update shared scan"
                                                + " options.");
                                    AlertDialog confirmation =
                                            new AlertDialog.Builder(this)
                                                    .setTitle("Replace saved profile catalog?")
                                                    .setMessage(summary)
                                                    .setNegativeButton("Cancel", null)
                                                    .setNeutralButton(
                                                            "Inspect captured catalog", null)
                                                    .setPositiveButton(
                                                            "Replace catalog",
                                                            (d, w) ->
                                                                    perform(
                                                                            "Publishing validated"
                                                                                    + " imported"
                                                                                    + " catalog…",
                                                                            () ->
                                                                                    store.replace(
                                                                                            revision,
                                                                                            incoming,
                                                                                            closed
                                                                                                    ::get)))
                                                    .create();
                                    confirmation.setOnShowListener(
                                            ignored ->
                                                    confirmation
                                                            .getButton(AlertDialog.BUTTON_NEUTRAL)
                                                            .setOnClickListener(
                                                                    view ->
                                                                            show(
                                                                                    "Captured"
                                                                                        + " imported"
                                                                                        + " catalog",
                                                                                    incoming.original)));
                                    confirmation.show();
                                });
                    } catch (Exception error) {
                        failed(
                                ticket,
                                "Profile import failed; prior catalog retained: "
                                        + error.getMessage());
                    }
                });
    }

    private void exportCatalog() {
        if (catalog == null || busy || pickerOpen) return;
        ScanProfiles captured = catalog;
        final int ticket = ++generation;
        busy = true;
        status.setText("Staging complete profile catalog…");
        controls();
        worker.execute(
                () -> {
                    PreparedExport result = null;
                    try {
                        result =
                                PreparedExport.stream(
                                        exports,
                                        "die-scan-profiles.json",
                                        "application/json",
                                        output -> captured.write(output, closed::get),
                                        closed::get);
                        PreparedExport ready = result;
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
                                    startActivityForResult(
                                            new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                                    .setType("application/json")
                                                    .putExtra(Intent.EXTRA_TITLE, ready.name),
                                            EXPORT);
                                });
                    } catch (Exception error) {
                        if (result != null) result.discard();
                        failed(ticket, "Profile export staging failed: " + error.getMessage());
                    }
                });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == IMPORT) {
            String revision = pendingImportRevision;
            pendingImportRevision = null;
            pickerOpen = false;
            controls();
            if (result == RESULT_OK && data != null && data.getData() != null && revision != null)
                importFrom(data.getData(), revision);
            else status.setText("Profile import cancelled");
        } else if (request == EXPORT) {
            PreparedExport captured = prepared;
            prepared = null;
            pickerOpen = false;
            if (captured == null) {
                status.setText("Prepared profile export is unavailable; export again");
                controls();
                return;
            }
            if (result != RESULT_OK || data == null || data.getData() == null) {
                captured.discard();
                status.setText("Profile export cancelled");
                controls();
                return;
            }
            final int ticket = ++generation;
            busy = true;
            status.setText("Writing captured profile catalog…");
            controls();
            Uri destination = data.getData();
            worker.execute(
                    () -> {
                        String message;
                        try (OutputStream output =
                                getContentResolver().openOutputStream(destination, "wt")) {
                            if (output == null) throw new IOException("Destination unavailable");
                            captured.copyTo(output, closed::get);
                            message = "Profile catalog exported • " + captured.size + " bytes";
                        } catch (Exception error) {
                            message =
                                    "Profile export failed; destination may contain partial data: "
                                            + error.getMessage();
                        } finally {
                            captured.discard();
                        }
                        String outcome = message;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    busy = false;
                                    if (catalog != null) render(outcome);
                                    else {
                                        status.setText(outcome);
                                        controls();
                                    }
                                });
                    });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pendingImportRevision", pendingImportRevision);
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
        if (prepared != null && !pickerOpen && !isChangingConfigurations()) {
            prepared.discard();
            prepared = null;
        }
        super.onDestroy();
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Offline acquisition/activation UI; archive validation and persistence live in CustomRuleStore. */
public final class RuleDatabaseActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private LinearLayout snapshots;
    private File store, exportFile;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new File(getFilesDir(), "custom-rule-snapshots");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        root.addView(text("Offline custom rule databases", 20));
        root.addView(text("Import a ZIP containing format folders such as ELF/*.sg or PE/*.sg, optionally under db/ or db_custom/. Helpers, metadata and license notices are retained. Imports are bounded to 32 MiB ZIP, 64 MiB expanded, 1 MiB/member and 10,000 entries.", 13));
        root.addView(text("DiE-JS rules run in the native scanner. Review their source and licensing. A loaded script is not a verified detector; test it against known samples in the laboratory. No inspected application is launched. Native worker isolation is pending.", 13));
        root.addView(button("Import ZIP and activate", this::chooseImport));
        root.addView(button("Export active original ZIP", this::chooseExport));
        root.addView(button("Toggle use of custom rules", () -> perform("Updating options…", () -> {
            ScanOptions options = ScanPreferences.load(this);
            if ((options.flags & ScanOptions.CUSTOM_DB) == 0 && CustomRuleStore.active(store) == null) {
                throw new IOException("Import and activate a database first");
            }
            ScanPreferences.save(this, new ScanOptions(options.flags ^ ScanOptions.CUSTOM_DB,
                options.minimumStringLength, options.includeTags, options.excludeTags));
            return "Custom-rule option updated. Rescan the input to apply.";
        })));
        status = text("Reading private snapshots…", 13); root.addView(status);
        ScrollView scroll = new ScrollView(this);
        snapshots = new LinearLayout(this); snapshots.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(snapshots); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume(); if (!busy) perform("Reading snapshots…", () -> "Database inventory");
    }

    private TextView text(String value, int size) {
        TextView view = WorkbenchUi.text(this,value,size);
        view.setTextIsSelectable(true); return view;
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false);
        view.setOnClickListener(v -> action.run()); return view;
    }

    private interface Operation { String run() throws Exception; }
    private void perform(String progress, Operation operation) {
        if (busy) return;
        busy = true; status.setText(progress);
        worker.execute(() -> {
            String message; List<CustomRuleStore.Snapshot> available = null;
            CustomRuleStore.Snapshot active = null; boolean enabled = false;
            try { message = operation.run(); }
            catch (Exception | LinkageError e) { message = "Database operation failed: " + e.getMessage(); }
            try {
                available = CustomRuleStore.list(store); active = CustomRuleStore.active(store);
                enabled = (ScanPreferences.load(this).flags & ScanOptions.CUSTOM_DB) != 0;
            } catch (Exception e) { message += "\nInventory unavailable: " + e.getMessage(); }
            final String result = message;
            final List<CustomRuleStore.Snapshot> records = available;
            final CustomRuleStore.Snapshot selected = active;
            final boolean useCustom = enabled;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false; status.setText(result);
                if (records != null) render(records, selected, useCustom);
            });
        });
    }

    private void render(List<CustomRuleStore.Snapshot> records, CustomRuleStore.Snapshot active, boolean enabled) {
        snapshots.removeAllViews();
        snapshots.addView(text("Custom rules contribute on next scan: " + enabled + "\n"
            + records.size() + " private snapshots retained for rollback. Selecting a prior snapshot activates it without modifying its bytes.", 13));
        for (CustomRuleStore.Snapshot record : records) {
            boolean selected = active != null && active.digest.equals(record.digest);
            snapshots.addView(text((selected ? "ACTIVE • " : "") + record.source + "\nSHA-256: " + record.digest
                + "\nFormat rules: " + record.ruleCount + " • expanded bytes: " + record.expandedBytes, 13));
            snapshots.addView(button(selected ? "Validate active snapshot" : "Activate / roll back to this snapshot", () ->
                perform("Loading rules through CDiE…", () -> activate(record))));
        }
    }

    private String activate(CustomRuleStore.Snapshot snapshot) throws Exception {
        EngineDatabase bundled = EngineDatabase.prepare(this);
        ScanOptions options = ScanPreferences.load(this);
        JSONObject inventory = new JSONObject(Engine.desktop("", bundled.main.getAbsolutePath(),
            snapshot.database.getAbsolutePath(), 7, options.flags, 0, "", "", "", ""));
        if (!inventory.optBoolean("ok") || inventory.optInt("customCount") < 1) {
            throw new IOException(inventory.optString("error", "CDiE loaded no scripts from this snapshot"));
        }
        CustomRuleStore.activate(store, snapshot.digest);
        ScanPreferences.save(this, new ScanOptions(options.flags | ScanOptions.CUSTOM_DB,
            options.minimumStringLength, options.includeTags, options.excludeTags));
        return "Activated " + inventory.getInt("customCount") + " loadable custom scripts (including helpers). Syntax and detection correctness need sample tests. Rescan the input to apply.";
    }

    private void chooseImport() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        startActivityForResult(intent, 1);
    }
    private void chooseExport() {
        if (busy) return;
        try {
            CustomRuleStore.Snapshot active = CustomRuleStore.active(store);
            if (active == null) throw new IOException("No active imported database");
            exportFile = active.archive;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip"); intent.putExtra(Intent.EXTRA_TITLE, "die-custom-" + active.digest.substring(0, 12) + ".zip");
            startActivityForResult(intent, 2);
        } catch (IOException e) { status.setText(e.getMessage()); }
    }

    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (request == 1) perform("Validating local ZIP…", () -> {
            File input = File.createTempFile("custom-rules-", ".zip", getCacheDir());
            try {
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(input)) {
                    if (in == null) throw new IOException("Cannot open selected archive");
                    transfer(in, out, CustomRuleStore.MAX_ARCHIVE);
                }
                String name = "Imported rule ZIP";
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
                }
                return activate(CustomRuleStore.install(store, input, name));
            } finally { input.delete(); }
        });
        else if (request == 2 && exportFile != null) {
            File source = exportFile;
            perform("Exporting original archive…", () -> {
                try (InputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Cannot open export destination");
                    transfer(in, out, CustomRuleStore.MAX_ARCHIVE);
                }
                return "Original rule ZIP exported with the same archive SHA-256.";
            });
        }
    }

    private static void transfer(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[32768]; int count; long total = 0;
        while ((count = in.read(buffer)) != -1) {
            if ((total += count) > limit) throw new IOException("Rule archive exceeds 32 MiB");
            out.write(buffer, 0, count);
        }
    }

    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}

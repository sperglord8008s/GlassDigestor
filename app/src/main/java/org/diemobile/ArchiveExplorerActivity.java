// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, read-only ZIP exploration; nested snapshots have explicit ownership. */
public final class ArchiveExplorerActivity extends Activity {
    static final int MAX_LEVELS = 16, MAX_PATH_CHARACTERS = 48 * 1024;
    static final long MAX_RETAINED_BYTES = 512L * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Location> trail = new ArrayList<>();
    private File snapshotDirectory;
    private ArchiveStore.Inventory inventory;
    private List<Row> visible = Collections.emptyList();
    private ListView list;
    private TextView title, status;
    private EditText filter;
    private Button parent, containerBack, previous, next, cancel, reload;
    private boolean busy, binding;
    private volatile int generation;
    private AtomicBoolean cancellation = new AtomicBoolean();

    private static final class Location {
        final File file;
        final String name;
        final boolean owned;
        long size;
        String prefix = "", filter = "";
        int page;
        Location(File file, String name, boolean owned, long size) {
            this.file = file; this.name = name; this.owned = owned; this.size = size;
        }
    }
    private static final class Row {
        final String label, path;
        final ArchiveStore.Member member;
        Row(String label, String path, ArchiveStore.Member member) {
            this.label = label; this.path = path; this.member = member;
        }
        @Override public String toString() { return label; }
    }
    private Location current() { return trail.get(trail.size() - 1); }
    private String displayName() {
        StringBuilder name = new StringBuilder();
        for (Location location : trail) { if (name.length() > 0) name.append("!/"); name.append(location.name); }
        return name.toString();
    }
    private Button button(String text, Runnable action) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextSize(12);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private void addButton(LinearLayout row, Button view) { row.addView(view, new LinearLayout.LayoutParams(0, -2, 1)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String path = getIntent().getStringExtra("path");
        if (path == null || !new File(path).isFile()) { finish(); return; }
        snapshotDirectory = new File(getCacheDir(), "archive-snapshots");
        String name = getIntent().getStringExtra("name"); if (name == null) name = new File(path).getName();
        trail.add(new Location(new File(path), name, false, new File(path).length()));
        String restoreMessage = restore(saved);
        createViews(); bind(); load(restoreMessage);
    }
    private void createViews() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left + dp(8), bars.top + dp(8), bars.right + dp(8), bars.bottom + dp(8)); return insets;
            });
        }
        title = new TextView(this); title.setTextSize(17); title.setMaxLines(3); title.setEllipsize(TextUtils.TruncateAt.MIDDLE); root.addView(title);
        TextView help = new TextView(this);
        help.setText("ZIP directory evidence: sizes, method and CRC32 are declarations until extraction checks them. Nested ZIPs use private snapshots. No member is installed or executed; names are never output paths."); root.addView(help);
        LinearLayout navigation = new LinearLayout(this);
        parent = button("Parent folder", this::parentFolder); addButton(navigation, parent);
        containerBack = button("Back archive", this::backArchive); addButton(navigation, containerBack);
        reload = button("Reload", () -> load("")); addButton(navigation, reload);
        cancel = button("Cancel", () -> { if (busy) { cancellation.set(true); status.setText("Cancellation requested • waiting for archive I/O"); } }); addButton(navigation, cancel); root.addView(navigation);
        filter = new EditText(this); filter.setSingleLine(); filter.setHint("Filter this folder (up to 1024 characters)");
        filter.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1024)}); root.addView(filter);
        LinearLayout pages = new LinearLayout(this);
        previous = button("Previous 50", () -> { if (!busy) { current().page--; render(); } }); addButton(pages, previous);
        next = button("Next 50", () -> { if (!busy) { current().page++; render(); } }); addButton(pages, next); root.addView(pages);
        status = new TextView(this); root.addView(status); list = new ListView(this); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int before, int after) {}
            public void afterTextChanged(Editable value) {}
            public void onTextChanged(CharSequence value, int a, int before, int count) {
                if (!binding) { current().filter = value.toString(); current().page = 0; render(); }
            }
        });
        list.setOnItemClickListener((p, view, index, id) -> {
            if (busy) return; Row row = visible.get(index);
            if (row.member == null) {
                if (pathCharacters(row.path, null) > MAX_PATH_CHARACTERS) { status.setText("Navigation path budget: 48 Ki-characters across retained locations"); return; }
                current().prefix = row.path; current().filter = ""; current().page = 0; bind(); render();
            } else details(row.member);
        });
    }
    private void bind() {
        binding = true; filter.setText(current().filter); title.setText(displayName() + "\n/" + current().prefix); binding = false;
    }
    private int pathCharacters(String currentPrefix, String newName) {
        int total = newName == null ? 0 : newName.length();
        for (int i = 0; i < trail.size(); i++) {
            Location location = trail.get(i);
            if (i > 0) total += location.name.length();
            total += i + 1 == trail.size() ? currentPrefix.length() : location.prefix.length();
        }
        return total;
    }
    private int begin(String message) {
        cancellation.set(true); cancellation = new AtomicBoolean(); int ticket = ++generation; busy = true;
        for (View view : new View[]{parent, containerBack, previous, next, reload, filter, list}) view.setEnabled(false);
        cancel.setEnabled(true); status.setText(message); return ticket;
    }
    private void settled() {
        busy = false; reload.setEnabled(true); filter.setEnabled(true); list.setEnabled(true); cancel.setEnabled(false);
    }
    private void load(String notice) {
        if (busy) return;
        final Location location = current(); final int ticket = begin("Reading complete ZIP directory…"); final AtomicBoolean stop = cancellation;
        inventory = null; visible = Collections.emptyList(); list.setAdapter(null);
        worker.execute(() -> {
            ArchiveStore.Inventory result = null; String error = "";
            try {
                result = ArchiveStore.read(location.file, stop::get);
                if (result.sourceSize != location.size) throw new IOException("Archive length changed; reopen the source from Scan");
            } catch (Exception e) { result = null; error = e.getMessage(); }
            final ArchiveStore.Inventory loaded = result; final String failure = error;
            runOnUiThread(() -> {
                if (isDestroyed() || ticket != generation) return; settled(); inventory = stop.get() ? null : loaded; render();
                if (inventory == null) status.setText(stop.get() ? "Archive operation cancelled" : "Cannot read archive: " + failure);
                else if (!notice.isEmpty()) status.append(" • " + notice);
            });
        });
    }
    private void render() {
        if (list == null) return;
        title.setText(displayName() + "\n/" + current().prefix);
        parent.setEnabled(!busy && !current().prefix.isEmpty()); containerBack.setEnabled(!busy && trail.size() > 1);
        previous.setEnabled(false); next.setEnabled(false);
        if (inventory == null) return;
        String query = current().filter.toLowerCase(Locale.ROOT);
        TreeMap<String, Row> directories = new TreeMap<>(); List<Row> files = new ArrayList<>();
        for (ArchiveStore.Member member : inventory.members) {
            if (!member.name.startsWith(current().prefix) || member.name.equals(current().prefix)) continue;
            String rest = member.name.substring(current().prefix.length()); int slash = rest.indexOf('/');
            if (slash >= 0) {
                String folder = rest.substring(0, slash + 1);
                if (folder.toLowerCase(Locale.ROOT).contains(query)) directories.putIfAbsent(folder, new Row("Directory: " + (folder.length() > 160 ? folder.substring(0, 160) + "… [open directory]" : folder), current().prefix + folder, null));
            } else if (rest.toLowerCase(Locale.ROOT).contains(query)) {
                String info = "#" + member.index + " " + abbreviated(rest) + "\n" + member.size + " bytes • compressed=" + member.compressedSize
                    + " • method=" + member.method + " • CRC32=" + String.format(Locale.ROOT, "%08X", member.crc32)
                    + (member.occurrences > 1 ? " • DUPLICATE NAME (" + member.occurrences + ")" : "");
                files.add(new Row(info, member.name, member));
            }
        }
        files.sort(Comparator.comparing(row -> row.path)); ArrayList<Row> rows = new ArrayList<>(directories.values()); rows.addAll(files);
        int pages = Math.max(1, (rows.size() + 49) / 50); current().page = Math.max(0, Math.min(current().page, pages - 1));
        int from = current().page * 50; visible = new ArrayList<>(rows.subList(from, Math.min(rows.size(), from + 50)));
        list.setAdapter(WorkbenchUi.adapter(this, visible, row -> {
            if (row.member == null) return new WorkbenchUi.Lines(this).heading(row.label).muted("Virtual directory · open to browse").build();
            ArchiveStore.Member member = row.member;
            return new WorkbenchUi.Lines(this).heading("#" + member.index + " " + WorkbenchUi.preview(member.name, 160))
                    .field("Decoded / compressed bytes", member.size + " / " + member.compressedSize, true)
                    .field("ZIP method / CRC32", member.method + " / " + String.format(Locale.ROOT, "%08X", member.crc32), true)
                    .muted(member.occurrences > 1 ? "Duplicate name · " + member.occurrences + " occurrences" : "Tap for complete member evidence").build();
        })); list.setSelection(0);
        previous.setEnabled(!busy && current().page > 0); next.setEnabled(!busy && current().page + 1 < pages);
        if (!busy) status.setText(rows.size() + " folder matches • page " + (current().page + 1) + "/" + pages + " • " + inventory.members.size()
            + " archive entries • level " + trail.size() + "/" + MAX_LEVELS + " • " + retainedBytes() + " retained nested bytes");
    }
    private static String abbreviated(String name) { return name.length() > 160 ? name.substring(0, 160) + "… [tap for full name]" : name; }
    private void parentFolder() {
        if (busy || current().prefix.isEmpty()) return;
        int slash = current().prefix.lastIndexOf('/', Math.max(0, current().prefix.length() - 2));
        current().prefix = slash < 0 ? "" : current().prefix.substring(0, slash + 1); current().filter = ""; current().page = 0; bind(); render();
    }
    private void backArchive() {
        if (busy || trail.size() < 2) return;
        Location removed = trail.remove(trail.size() - 1); if (removed.owned) removed.file.delete(); bind(); load("");
    }
    private long retainedBytes() { long total = 0; for (Location location : trail) if (location.owned) total += location.size; return total; }
    private void details(ArchiveStore.Member member) {
        TextView fields = new TextView(this); fields.setTextIsSelectable(true); fields.setPadding(dp(12), dp(8), dp(12), dp(8));
        fields.setText("Central-directory index: " + member.index + "\nName: " + member.name + "\nDeclared expanded size: " + member.size
            + " bytes\nDeclared compressed size: " + member.compressedSize + " bytes\nCompression method: " + member.method
            + (member.method == 0 ? " (stored)" : member.method == 8 ? " (deflate)" : " (not supported by this reader)")
            + "\nDeclared CRC32: " + String.format(Locale.ROOT, "%08X", member.crc32) + "\nName occurrences: " + member.occurrences
            + "\n\nExtraction checks the complete directory, expanded length and CRC32, and computes SHA-256. CRC32 does not authenticate a publisher. Duplicate names cannot be selected unambiguously. Stored/compressed sizes do not establish behavior.");
        ScrollView scroll = new ScrollView(this); scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Archive member evidence").setView(scroll).setNegativeButton("Close", null)
            .setNeutralButton("Browse nested ZIP", (d, which) -> select(member, true)).setPositiveButton("Analyze member", (d, which) -> select(member, false)).show();
        boolean allowed = member.occurrences == 1 && member.size >= 0 && member.size <= ArchiveStore.MAX_MEMBER
            && member.crc32 >= 0 && (member.method == 0 || member.method == 8);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(allowed); dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(allowed);
    }
    private void select(ArchiveStore.Member member, boolean nested) {
        if (busy || inventory == null) return;
        if (nested && (trail.size() >= MAX_LEVELS || member.size > MAX_RETAINED_BYTES - retainedBytes()
            || pathCharacters(current().prefix, member.name) > MAX_PATH_CHARACTERS)) {
            status.setText("Nested navigation budget: 16 levels, 512 MiB retained snapshots and 48 Ki-characters of location paths"); return;
        }
        final ArchiveStore.Inventory source = inventory; final int ticket = begin("Validating and extracting one member…"); final AtomicBoolean stop = cancellation;
        worker.execute(() -> {
            ArchiveStore.Extracted extracted = null; ArchiveStore.Inventory opened = null; String error = "";
            try {
                extracted = ArchiveStore.extract(source, member, snapshotDirectory, stop::get);
                if (nested) opened = ArchiveStore.read(extracted.file, stop::get);
                if (stop.get()) throw new InterruptedIOException("Archive operation cancelled");
            } catch (Exception e) { if (extracted != null) extracted.file.delete(); extracted = null; error = e.getMessage(); }
            final ArchiveStore.Extracted result = extracted; final ArchiveStore.Inventory child = opened; final String failure = error;
            runOnUiThread(() -> {
                if (isDestroyed() || ticket != generation) { if (result != null) result.file.delete(); return; }
                settled(); render();
                if (result == null) { status.setText("Member extraction failed: " + failure); return; }
                if (stop.get()) { result.file.delete(); status.setText("Archive operation cancelled"); return; }
                if (nested) {
                    trail.add(new Location(result.file, member.name, true, result.size)); inventory = child; bind(); render();
                } else {
                    setResult(RESULT_OK, new Intent().putExtra("path", result.file.getAbsolutePath()).putExtra("display", displayName() + "!/" + member.name)
                        .putExtra("memberSha256", result.sha256)); finish(); // Ownership of this leaf transfers to MainActivity.
                }
            });
        });
    }
    @Override public void onBackPressed() {
        if (busy) { cancellation.set(true); status.setText("Cancellation requested • wait for archive I/O before leaving"); }
        else if (!current().prefix.isEmpty()) parentFolder(); else if (trail.size() > 1) backArchive(); else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); ArrayList<Bundle> locations = new ArrayList<>();
        for (int i = 0; i < trail.size(); i++) {
            Location location = trail.get(i); Bundle item = new Bundle(); item.putString("path", location.file.getAbsolutePath());
            if (i > 0) item.putString("name", location.name);
            item.putLong("size", location.size); item.putString("prefix", location.prefix); item.putString("filter", location.filter); item.putInt("page", location.page); locations.add(item);
        }
        state.putParcelableArrayList("archiveTrail", locations);
    }
    private String restore(Bundle state) {
        if (state == null) return "";
        ArrayList<Bundle> locations = state.getParcelableArrayList("archiveTrail");
        if (locations == null || locations.isEmpty() || locations.size() > MAX_LEVELS) return "Navigation state unavailable; reopened outer archive";
        try {
            if (!current().file.getCanonicalPath().equals(locations.get(0).getString("path"))) throw new IOException("Different root");
            for (int i = 0; i < locations.size(); i++) {
                Bundle item = locations.get(i); Location location;
                if (i == 0) location = current();
                else {
                    File cached = new File(item.getString("path", "")).getCanonicalFile(); long size = item.getLong("size", -1);
                    if (!snapshotDirectory.getCanonicalFile().equals(cached.getParentFile()) || !cached.getName().startsWith("archive-member-")
                        || !cached.isFile() || cached.length() != size || size < 0 || size > ArchiveStore.MAX_MEMBER
                        || size > MAX_RETAINED_BYTES - retainedBytes()) throw new IOException("Nested snapshot expired");
                    location = new Location(cached, item.getString("name", ""), true, size); trail.add(location);
                }
                location.prefix = item.getString("prefix", ""); location.filter = item.getString("filter", ""); location.page = Math.max(0, item.getInt("page"));
                if (location.filter.length() > 1024 || pathCharacters(location.prefix, null) > MAX_PATH_CHARACTERS) throw new IOException("Navigation state exceeds budgets");
            }
            return "Restored archive navigation";
        } catch (IOException e) {
            while (trail.size() > 1) { Location removed = trail.remove(trail.size() - 1); removed.file.delete(); }
            current().prefix = ""; current().filter = ""; current().page = 0;
            return "Nested navigation expired; reopened outer archive";
        }
    }
    @Override protected void onDestroy() {
        generation++; cancellation.set(true); worker.shutdown();
        if (!isChangingConfigurations()) for (Location location : trail) if (location.owned) location.file.delete();
        super.onDestroy();
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Content-identified native archives, virtual folders and checked nested member acquisition. */
public final class NativeArchiveExplorerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private NativeArchiveSession session;
    private ArchiveRunner runner;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private volatile int generation;
    private boolean busy, binding, galleryMode;
    private String restoreState, stableState;
    private PreparedExport pendingExport;
    private TextView title, status;
    private EditText filter;
    private ListView list;
    private GridView gallery;
    private NativeArchiveGalleryAdapter galleryAdapter;
    private Button galleryToggle;
    private Button parent, back, viewMode, previous, next, cancel;
    private final List<View> actions = new ArrayList<>();
    private List<ArchiveListing.Entry> visible = new ArrayList<>();

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private File base() {
        return new File(getCacheDir(), "native-archives");
    }

    private File exports() {
        return new File(getCacheDir(), "tool-exports");
    }

    private Button button(LinearLayout row, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        actions.add(button);
        return button;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        runner = new BinaryWorkerClient(this, "archive");
        galleryMode = state != null && state.getBoolean("galleryMode", false);
        createViews();
        if (state != null) {
            restoreState = state.getString("archiveState");
            stableState = restoreState;
            if (state.containsKey("exportPath"))
                try {
                    pendingExport =
                            PreparedExport.restore(
                                    exports(),
                                    state.getString("exportPath"),
                                    state.getString("exportName"),
                                    state.getString("exportMime"),
                                    state.getLong("exportSize"));
                } catch (Exception failure) {
                    status.setText("Prepared report expired: " + failure);
                }
        }
        load();
    }

    private void createViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        view.setPadding(
                                bars.left + dp(8),
                                bars.top + dp(8),
                                bars.right + dp(8),
                                bars.bottom + dp(8));
                        return insets;
                    });
        }
        title = new TextView(this);
        title.setTextSize(17);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(title);
        LinearLayout navigation = new LinearLayout(this);
        root.addView(navigation);
        parent = button(navigation, "Parent folder", this::parentFolder);
        back = button(navigation, "Back archive", this::backArchive);
        viewMode =
                button(
                        navigation,
                        "All entries",
                        () -> {
                            if (session != null) {
                                session.current().allEntries = !session.current().allEntries;
                                session.current().page = 0;
                                render();
                            }
                        });
        LinearLayout files = new LinearLayout(this);
        root.addView(files);
        galleryToggle =
                button(
                        files,
                        "Gallery",
                        () -> {
                            galleryMode = !galleryMode;
                            if (session != null) session.current().page = 0;
                            render();
                        });
        button(files, "Export files ZIP", () -> stageExport(1, null));
        button(files, "Tools / evidence", this::tools);
        filter = new EditText(this);
        filter.setSingleLine();
        filter.setHint("Filter names / entry indices in this view");
        filter.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1024)});
        root.addView(filter);
        actions.add(filter);
        LinearLayout pages = new LinearLayout(this);
        root.addView(pages);
        previous =
                button(
                        pages,
                        "Previous 50",
                        () -> {
                            session.current().page--;
                            render();
                        });
        next =
                button(
                        pages,
                        "Next 50",
                        () -> {
                            session.current().page++;
                            render();
                        });
        cancel =
                button(
                        pages,
                        "Cancel",
                        () -> {
                            cancellation.set(true);
                            status.setText("Cancellation requested; waiting for archive worker");
                        });
        actions.remove(cancel);
        status = new TextView(this);
        status.setTextIsSelectable(true);
        status.setMaxLines(2);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setContentDescription("Archive status. Tap for complete source, scope and limits.");
        status.setOnClickListener(view -> statusDetails());
        root.addView(status);
        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        actions.add(list);
        gallery = new GridView(this);
        gallery.setColumnWidth(dp(180));
        gallery.setNumColumns(GridView.AUTO_FIT);
        gallery.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gallery.setHorizontalSpacing(dp(6));
        gallery.setVerticalSpacing(dp(6));
        gallery.setVisibility(View.GONE);
        root.addView(gallery, new LinearLayout.LayoutParams(-1, 0, 1));
        actions.add(gallery);
        setContentView(root);
        filter.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void afterTextChanged(Editable s) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (!binding && !busy && session != null) {
                            session.current().filter = s.toString();
                            session.current().page = 0;
                            render();
                        }
                    }
                });
        list.setOnItemClickListener((parent, view, index, id) -> openEntry(index, false));
        gallery.setOnItemClickListener((parent, view, index, id) -> openEntry(index, true));
        gallery.setOnItemLongClickListener(
                (parent, view, index, id) -> {
                    if (busy || session == null) return true;
                    ArchiveListing.Entry entry = visible.get(index);
                    if (entry.member != null) details(entry.member);
                    return true;
                });
    }

    private void openEntry(int index, boolean direct) {
        if (busy || session == null) return;
        ArchiveListing.Entry row = visible.get(index);
        if (row.member == null) {
            session.current().prefix = row.folder;
            session.current().filter = "";
            session.current().page = 0;
            bind();
            render();
        } else if (direct && session.current().inventory.complete && row.member.canAcquire())
            select(row.member, 0);
        else details(row.member);
    }

    private void closeGalleryPage() {
        if (galleryAdapter != null) {
            galleryAdapter.close();
            galleryAdapter = null;
        }
    }

    private int begin(String text) {
        closeGalleryPage();
        cancellation.set(true);
        cancellation = new AtomicBoolean();
        busy = true;
        for (View view : actions) view.setEnabled(false);
        cancel.setEnabled(true);
        status.setText(text);
        return ++generation;
    }

    private boolean current(int ticket) {
        return ticket == generation && !isDestroyed();
    }

    private void settled() {
        busy = false;
        for (View view : actions) view.setEnabled(true);
        cancel.setEnabled(false);
        render();
    }

    private void load() {
        if (busy) return;
        if (session == null) {
            runner.close();
            runner = new BinaryWorkerClient(this, "archive");
        }
        int ticket =
                begin(
                        restoreState == null
                                ? "Capturing input and reading archive metadata…"
                                : "Checking retained archive sources and reports…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    NativeArchiveSession loaded = null;
                    String failure = null;
                    try {
                        if (session != null) {
                            session.changeScope(
                                    session.current().inventory.offset,
                                    session.current().inventory.length,
                                    stop::get);
                            loaded = session;
                        } else if (restoreState != null)
                            loaded =
                                    NativeArchiveSession.restore(
                                            base(), restoreState, runner, stop::get);
                        else {
                            String path = getIntent().getStringExtra("path");
                            if (path == null) throw new IOException("No archive input selected");
                            File file = new File(path);
                            String name = getIntent().getStringExtra("name");
                            long offset = getIntent().getLongExtra("offset", 0),
                                    length =
                                            getIntent()
                                                    .getLongExtra("length", file.length() - offset);
                            ArchiveLaunch launch = requestedLaunch(file, offset, length);
                            loaded =
                                    NativeArchiveSession.open(
                                            file,
                                            name == null ? file.getName() : name,
                                            base(),
                                            offset,
                                            length,
                                            runner,
                                            launch,
                                            stop::get);
                        }
                        if (loaded != null) {
                            NativeArchiveSession.Location root = loaded.locations().get(0);
                            ArchiveLaunch expected =
                                    requestedLaunch(
                                            root.input.file,
                                            root.inventory.offset,
                                            root.inventory.length);
                            if (expected != null) expected.requireSource(root.input);
                        }
                    } catch (Exception error) {
                        if (loaded != null && loaded != session) loaded.close(true);
                        loaded = null;
                        failure = error.toString();
                    }
                    NativeArchiveSession result = loaded;
                    String error = failure;
                    runOnUiThread(
                            () -> {
                                if (!current(ticket)) {
                                    if (result != null && result != session) result.close();
                                    return;
                                }
                                if (result != null) {
                                    session = result;
                                    restoreState = null;
                                    bind();
                                }
                                settled();
                                if (error != null)
                                    status.setText(
                                            "Archive operation failed: "
                                                    + error
                                                    + (restoreState == null
                                                            ? ""
                                                            : " • Retained state was not replaced."
                                                                    + " Reopen from the source to"
                                                                    + " start a new session."));
                            });
                });
    }

    private ArchiveLaunch requestedLaunch(File file, long offset, long length) throws Exception {
        String raw = getIntent().getStringExtra("archiveLaunch");
        if (raw != null) return ArchiveLaunch.parse(raw);
        String sha = getIntent().getStringExtra("sha256");
        if (sha == null && !getIntent().hasExtra("sourceSize")) return null;
        if (sha == null) throw new IOException("Archive source expectation has no SHA-256");
        return ArchiveLaunch.forInput(
                sha, getIntent().getLongExtra("sourceSize", -1), offset, length);
    }

    private void bind() {
        if (session == null) return;
        binding = true;
        filter.setText(session.current().filter);
        binding = false;
    }

    private void render() {
        if (session == null) {
            parent.setEnabled(false);
            back.setEnabled(false);
            previous.setEnabled(false);
            next.setEnabled(false);
            return;
        }
        NativeArchiveSession.Location location = session.current();
        NativeArchiveStore.Inventory inventory = location.inventory;
        title.setText(
                inventory.format
                        + " · level "
                        + session.depth()
                        + "/16\n"
                        + (location.allEntries
                                ? "All entries"
                                : "/" + NativeArchiveSession.abbreviated(location.prefix, 160))
                        + " · "
                        + NativeArchiveSession.abbreviated(location.name, 160));
        viewMode.setText(location.allEntries ? "Folder tree" : "All entries");
        parent.setEnabled(!busy && !location.allEntries && !location.prefix.isEmpty());
        back.setEnabled(!busy && session.depth() > 1);
        List<ArchiveListing.Entry> rows = ArchiveListing.entries(location);
        int pageSize = galleryMode ? 24 : 50;
        int pages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        location.page = Math.max(0, Math.min(location.page, pages - 1));
        int first = location.page * pageSize;
        visible = new ArrayList<>(rows.subList(first, Math.min(rows.size(), first + pageSize)));
        closeGalleryPage();
        galleryToggle.setText(galleryMode ? "List" : "Gallery");
        previous.setText("Previous " + pageSize);
        next.setText("Next " + pageSize);
        list.setVisibility(galleryMode ? View.GONE : View.VISIBLE);
        gallery.setVisibility(galleryMode ? View.VISIBLE : View.GONE);
        if (galleryMode) {
            galleryAdapter =
                    new NativeArchiveGalleryAdapter(
                            this,
                            visible,
                            inventory,
                            runner,
                            new File(getCacheDir(), "archive-gallery-members"),
                            worker,
                            member -> stageExport(2, member),
                            member -> select(member, 2));
            gallery.setAdapter(galleryAdapter);
        } else
            list.setAdapter(WorkbenchUi.adapter(this, visible, entry -> {
                if (entry.member == null) return new WorkbenchUi.Lines(this)
                        .heading(entry.label).muted("Virtual directory · open to browse").build();
                NativeArchiveStore.Member member = entry.member;
                return new WorkbenchUi.Lines(this)
                        .heading("#" + member.index + " " + WorkbenchUi.preview(member.name, 160))
                        .field("Type", member.directory ? "Directory" : member.linked ? "Link (never materialized)"
                                : member.regular ? "Regular member" : "Special / unknown", false)
                        .field("Declared bytes", member.size == null ? "unknown" : member.size.toString(), true)
                        .muted((member.encrypted ? "Encrypted · " : "")
                                + (!member.treeName() ? "Name outside virtual tree · " : "")
                                + "Tap for member actions / complete evidence").build();
            }));
        previous.setEnabled(!busy && location.page > 0);
        next.setEnabled(!busy && location.page + 1 < pages);
        String retentionWarning = "";
        if (!busy)
            try {
                stableState = session.retentionState();
            } catch (Exception failure) {
                stableState = null;
                retentionWarning = " · Navigation retention unavailable: " + failure;
            }
        if (!busy)
            status.setText(
                    inventory.members.size()
                            + " entries · "
                            + rows.size()
                            + " matches · page "
                            + (location.page + 1)
                            + "/"
                            + pages
                            + (inventory.complete ? " · complete" : " · FAILED / PARTIAL")
                            + "\nScope: "
                            + inventory.offset
                            + " + "
                            + inventory.length
                            + " bytes"
                            + "\nTap for source, status and limits\n\n"
                            + "Source: "
                            + location.name
                            + "\nSHA-256 "
                            + location.input.sha256
                            + "\n"
                            + (session.launch == null ? "" : session.launch.summary() + "\n")
                            + (inventory.complete
                                    ? "Complete returned inventory"
                                    : "FAILED / PARTIAL inventory: " + inventory.failure)
                            + " · "
                            + inventory.members.size()
                            + " entries · page "
                            + (location.page + 1)
                            + "/"
                            + pages
                            + " · "
                            + rows.size()
                            + " matches\n"
                            + "Parsing interval ["
                            + inventory.offset
                            + ", "
                            + (inventory.offset + inventory.length)
                            + ") of "
                            + location.input.size
                            + " bytes; this is not a recovered extent.\n"
                            + "All entries retains explicit directory records, duplicates and"
                            + " unusual names. Metadata iteration does not verify every payload. "
                            + session.retainedBytes()
                            + " nested bytes retained."
                            + (galleryMode
                                    ? "\n"
                                          + "Gallery previews acquire checked files (≤32 MiB each,"
                                          + " ≤96 MiB member bytes and ≤512 MiB sum of archive"
                                          + " source sizes per page). Explicit preview/save uses"
                                          + " the ordinary 256 MiB member limit. ZIP exports span"
                                          + " every matching page and include displayed folders"
                                          + " recursively."
                                    : "")
                            + retentionWarning);
    }

    private void parentFolder() {
        if (busy || session == null) return;
        String prefix = session.current().prefix;
        int slash = prefix.lastIndexOf('/', Math.max(0, prefix.length() - 2));
        session.current().prefix = slash < 0 ? "" : prefix.substring(0, slash + 1);
        session.current().filter = "";
        session.current().page = 0;
        bind();
        render();
    }

    private void backArchive() {
        if (busy || session == null || session.depth() < 2) return;
        int ticket = begin("Returning to retained parent evidence…");
        worker.execute(
                () -> {
                    session.back();
                    runOnUiThread(
                            () -> {
                                if (current(ticket)) {
                                    bind();
                                    settled();
                                }
                            });
                });
    }

    private void details(NativeArchiveStore.Member member) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView facts = new TextView(this);
        facts.setTextIsSelectable(true);
        facts.setText(
                "Entry #"
                        + member.index
                        + "\n"
                        + NativeArchiveSession.abbreviated(member.name, 4096)
                        + "\nDeclared logical bytes: "
                        + (member.size == null ? "unknown" : member.size)
                        + "\n"
                        + "Physical offset: unknown (derived member)\n"
                        + "SHA-256: computed only after complete acquisition.\n"
                        + (member.canAcquire() && session.current().inventory.complete
                                ? "Eligible for checked acquisition by exact index."
                                : "Acquisition unavailable: incomplete inventory, link/special"
                                        + " type, encryption or size budget."));
        content.addView(facts);
        ScrollView detailScroll = new ScrollView(this);
        detailScroll.addView(content);
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Archive member evidence")
                        .setView(detailScroll)
                        .setNegativeButton("Close", null)
                        .create();
        addAction(
                content,
                "Full entry metadata / raw returned fields",
                () -> raw("Entry #" + member.index, member.raw));
        boolean allowed = member.canAcquire() && session.current().inventory.complete;
        Button preview =
                addAction(
                        content,
                        "Open preview / media / export bytes",
                        () -> {
                            dialog.dismiss();
                            select(member, 0);
                        });
        preview.setEnabled(allowed);
        Button analyze =
                addAction(
                        content,
                        "Analyze member in parent workbench",
                        () -> {
                            dialog.dismiss();
                            select(member, 1);
                        });
        analyze.setEnabled(allowed);
        Button nested =
                addAction(
                        content,
                        "Browse as nested archive / compressed stream",
                        () -> {
                            dialog.dismiss();
                            select(member, 2);
                        });
        nested.setEnabled(allowed);
        addAction(
                content,
                "Definitions and limits",
                () ->
                        new AlertDialog.Builder(this)
                                .setTitle("Archive evidence explained")
                                .setMessage(
                                        "A member is a logical byte stream decoded from an archive."
                                            + " Its stored bytes may be compressed, sparse or"
                                            + " split, so an uncompressed header position is not a"
                                            + " physical file offset. Names and ownership are"
                                            + " declarations; no path, link, permission or"
                                            + " application is installed. Duplicate names remain"
                                            + " distinguishable by entry index. Upstream checksum"
                                            + " failures block publication, but a successful read"
                                            + " is not publisher authentication. Digest accessor"
                                            + " buffers do not establish whether a digest was"
                                            + " stored. Limits: 1 GiB input, 256 MiB per derived"
                                            + " member, 20,000 entries, 16 nested levels, 512 MiB"
                                            + " retained nested bytes, 64 MiB reports; native"
                                            + " decoder/work/deadline limits are recorded in full"
                                            + " evidence. Password support and external programs"
                                            + " are disabled.")
                                .setPositiveButton("Close", null)
                                .show());
        dialog.show();
    }

    private Button addAction(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        parent.addView(button);
        return button;
    }

    private void select(NativeArchiveStore.Member member, int mode) {
        if (busy || session == null) return;
        int ticket =
                begin(
                        mode == 2
                                ? "Acquiring member and identifying nested content…"
                                : "Acquiring and hashing complete member bytes…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    File handoff = null;
                    String sha = null, error = null;
                    ContentOrigin origin = null;
                    try {
                        if (mode == 2) session.browse(member, stop::get);
                        else {
                            File target = new File(getCacheDir(), "archive-snapshots");
                            try (NativeArchiveStore.Acquired acquired =
                                    session.acquire(member, target, stop::get)) {
                                if (mode == 0
                                        || getIntent()
                                                .getBooleanExtra("originPreviewResult", false)) {
                                    origin =
                                            ContentOrigin.create(
                                                    new File(getCacheDir(), "content-origins"),
                                                    acquired.input,
                                                    ContentOrigin.member(
                                                            session.current().input, member.index),
                                                    records -> {
                                                        records.json(
                                                                "archiveTrail",
                                                                out ->
                                                                        session
                                                                                .writeCapturedEvidence(
                                                                                        out,
                                                                                        stop::get));
                                                        records.value(
                                                                "selectedMember",
                                                                new org.json.JSONObject()
                                                                        .put(
                                                                                "entryIndex",
                                                                                member.index)
                                                                        .put(
                                                                                "entryRawJson",
                                                                                member.raw)
                                                                        .put(
                                                                                "acquisitionRawJson",
                                                                                acquired.extractionReport));
                                                    },
                                                    stop::get);
                                }
                                handoff =
                                        File.createTempFile("archive-member-", ".snapshot", target);
                                Files.move(
                                        acquired.input.file.toPath(),
                                        handoff.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                                sha = acquired.input.sha256;
                                EvidenceRows.check(stop::get);
                            }
                        }
                    } catch (Exception failure) {
                        if (handoff != null) handoff.delete();
                        if (origin != null) origin.close();
                        origin = null;
                        handoff = null;
                        error = failure.toString();
                    }
                    File result = handoff;
                    ContentOrigin capturedOrigin = origin;
                    String identity = sha, failure = error;
                    runOnUiThread(
                            () -> {
                                if (!current(ticket)) {
                                    if (result != null) result.delete();
                                    if (capturedOrigin != null) capturedOrigin.close();
                                    return;
                                }
                                settled();
                                bind();
                                if (failure != null) {
                                    status.setText("Member acquisition failed: " + failure);
                                    return;
                                }
                                if (stop.get()) {
                                    if (result != null) result.delete();
                                    if (capturedOrigin != null) capturedOrigin.close();
                                    status.setText("Archive operation cancelled");
                                    return;
                                }
                                if (mode == 2) {
                                    render();
                                    return;
                                }
                                Intent intent =
                                        new Intent()
                                                .putExtra("path", result.getAbsolutePath())
                                                .putExtra(
                                                        "display",
                                                        NativeArchiveSession.abbreviated(
                                                                        session.current().name, 512)
                                                                + "!/"
                                                                + NativeArchiveSession.abbreviated(
                                                                        member.name, 2048))
                                                .putExtra("memberSha256", identity)
                                                .putExtra(
                                                        "archiveRootSha256",
                                                        session.locations().get(0).input.sha256)
                                                .putExtra(
                                                        "archiveRootSize",
                                                        session.locations().get(0).input.size)
                                                .putExtra("archiveEntryIndex", member.index)
                                                .putExtra(
                                                        "archiveSourceSha256",
                                                        session.current().input.sha256);
                                try {
                                    if (capturedOrigin != null)
                                        intent.putExtra(
                                                "contentOrigin", capturedOrigin.descriptor());
                                } catch (Exception invalid) {
                                    result.delete();
                                    if (capturedOrigin != null) capturedOrigin.close();
                                    status.setText("Origin handoff failed: " + invalid);
                                    return;
                                }
                                if (mode == 1) {
                                    setResult(RESULT_OK, intent);
                                    finish();
                                } else
                                    ArchivePreviewLauncher.launch(
                                            this,
                                            worker,
                                            intent,
                                            session.current().input.sha256,
                                            session.current().input.size,
                                            () -> isDestroyed(),
                                            () -> !isDestroyed(),
                                            () -> {},
                                            status::setText);
                            });
                });
    }

    private void tools() {
        if (busy || session == null) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Archive tools / evidence")
                        .setView(scroll)
                        .setNegativeButton("Close", null)
                        .create();
        String[] labels = {
            "Evidence",
            "Export report",
            "Parsing interval",
            "Reload",
            "Classic ZIP",
            "Status / limits"
        };
        Runnable[] commands = {
            this::evidence,
            this::exportReport,
            this::scope,
            this::load,
            this::classicZip,
            this::statusDetails
        };
        for (int i = 0; i < labels.length; i++) {
            Runnable command = commands[i];
            addAction(
                    content,
                    labels[i],
                    () -> {
                        dialog.dismiss();
                        command.run();
                    });
        }
        dialog.show();
    }

    private void statusDetails() {
        if (!busy) raw("Archive source, status and limits", status.getText().toString());
    }

    private void evidence() {
        if (session == null || busy) return;
        raw(
                "Native archive inspection · source SHA-256 " + session.current().input.sha256,
                session.current().inventory.raw);
    }

    private void raw(String heading, String text) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView page = new TextView(this);
        page.setTextIsSelectable(true);
        page.setTypeface(Typeface.MONOSPACE);
        page.setTextSize(12);
        TextView range = new TextView(this);
        content.addView(range);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(320)));
        LinearLayout controls = new LinearLayout(this);
        content.addView(controls);
        int[] offset = {0};
        Button previous = new Button(this), next = new Button(this);
        previous.setText("Previous");
        next.setText("Next");
        controls.addView(previous);
        controls.addView(next);
        Runnable render =
                () -> {
                    int end = Math.min(text.length(), offset[0] + 16384);
                    page.setText(text.substring(offset[0], end));
                    range.setText(
                            "Characters "
                                    + offset[0]
                                    + "–"
                                    + end
                                    + " / "
                                    + text.length()
                                    + " · complete report export available");
                    previous.setEnabled(offset[0] > 0);
                    next.setEnabled(end < text.length());
                    scroll.scrollTo(0, 0);
                };
        previous.setOnClickListener(
                v -> {
                    offset[0] = Math.max(0, offset[0] - 16384);
                    render.run();
                });
        next.setOnClickListener(
                v -> {
                    offset[0] += 16384;
                    render.run();
                });
        render.run();
        new AlertDialog.Builder(this)
                .setTitle(heading)
                .setView(content)
                .setPositiveButton("Close", null)
                .show();
    }

    private void scope() {
        if (session == null || busy) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        TextView help = new TextView(this);
        help.setText(
                "Explicit parsing interval in this captured input. Decimal or 0x hexadecimal."
                    + " Useful for embedded or prefixed archives; length is a parsing budget, never"
                    + " an inferred archive extent.");
        form.addView(help);
        EditText offset = new EditText(this), length = new EditText(this);
        offset.setSingleLine();
        length.setSingleLine();
        offset.setHint("Offset in bytes");
        length.setHint("Length in bytes");
        offset.setText("" + session.current().inventory.offset);
        length.setText("" + session.current().inventory.length);
        form.addView(offset);
        form.addView(length);
        new AlertDialog.Builder(this)
                .setTitle("Archive parsing interval")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Inspect interval",
                        (d, w) -> {
                            long start, count;
                            try {
                                start = parseNumber(offset.getText().toString());
                                count = parseNumber(length.getText().toString());
                                NativeArchiveStore.requireScope(
                                        session.current().input, start, count);
                            } catch (Exception failure) {
                                status.setText("Invalid parsing interval: " + failure.getMessage());
                                return;
                            }
                            int ticket = begin("Inspecting explicit archive interval…");
                            AtomicBoolean stop = cancellation;
                            worker.execute(
                                    () -> {
                                        String error = null;
                                        try {
                                            session.changeScope(start, count, stop::get);
                                        } catch (Exception failure) {
                                            error = failure.toString();
                                        }
                                        String failure = error;
                                        runOnUiThread(
                                                () -> {
                                                    if (current(ticket)) {
                                                        bind();
                                                        settled();
                                                        if (failure != null)
                                                            status.setText(
                                                                    "Interval failed; previous"
                                                                            + " evidence retained: "
                                                                            + failure);
                                                    }
                                                });
                                    });
                        })
                .show();
    }

    private static long parseNumber(String value) {
        String text = value.trim();
        return text.startsWith("0x") || text.startsWith("0X")
                ? Long.parseLong(text.substring(2), 16)
                : Long.parseLong(text, 10);
    }

    private void classicZip() {
        if (session == null || busy) return;
        startActivityForResult(
                new Intent(this, ArchiveExplorerActivity.class)
                        .putExtra("path", session.current().input.file.getAbsolutePath())
                        .putExtra("name", session.current().name),
                2);
    }

    private void exportReport() {
        stageExport(0, null);
    }

    private void stageExport(int mode, NativeArchiveStore.Member member) {
        if (session == null || busy || pendingExport != null) return;
        int ticket =
                begin(
                        mode == 0
                                ? "Staging complete archive trail and original reports…"
                                : mode == 1
                                        ? "Acquiring all matching archive files and staging ZIP…"
                                        : "Acquiring complete member and staging file…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    PreparedExport staged = null;
                    String error = null;
                    try {
                        if (mode == 1) staged = ArchiveFiles.zip(session, exports(), stop::get);
                        else if (mode == 2)
                            staged = ArchiveFiles.file(session, member, exports(), stop::get);
                        else
                            staged =
                                    PreparedExport.stream(
                                            exports(),
                                            "GlassDigestor-archive-evidence.json",
                                            "application/json",
                                            out ->
                                                    EvidenceJson.write(
                                                            out, session.evidence(), stop::get),
                                            stop::get);
                    } catch (Exception failure) {
                        error = failure.toString();
                    }
                    PreparedExport result = staged;
                    String failure = error;
                    runOnUiThread(
                            () -> {
                                if (!current(ticket)) {
                                    if (result != null) result.discard();
                                    return;
                                }
                                settled();
                                if (result == null || stop.get()) {
                                    if (result != null) result.discard();
                                    status.setText("Archive export was not staged: " + failure);
                                    return;
                                }
                                pendingExport = result;
                                try {
                                    startActivityForResult(
                                            new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                                    .setType(result.mime)
                                                    .putExtra(Intent.EXTRA_TITLE, result.name),
                                            1);
                                } catch (RuntimeException failureToOpen) {
                                    result.discard();
                                    pendingExport = null;
                                    status.setText("Document picker unavailable: " + failureToOpen);
                                }
                            });
                });
    }

    @Override
    protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request == 2) {
            if (code == RESULT_OK && data != null) {
                if (session != null)
                    data.putExtra("archiveRootSha256", session.locations().get(0).input.sha256)
                            .putExtra("archiveRootSize", session.locations().get(0).input.size);
                setResult(code, data);
                finish();
            }
            return;
        }
        if (request != 1 || pendingExport == null) return;
        PreparedExport staged = pendingExport;
        pendingExport = null;
        if (code != RESULT_OK || data == null || data.getData() == null) {
            staged.discard();
            status.setText("Archive export cancelled");
            return;
        }
        int ticket = begin("Writing staged archive export…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    String message;
                    try (OutputStream output =
                            getContentResolver().openOutputStream(data.getData(), "w")) {
                        if (output == null)
                            throw new IOException("Provider returned no writable stream");
                        staged.copyTo(output, stop::get);
                        message = "Exported staged archive content · " + staged.size + " bytes";
                    } catch (Exception failure) {
                        message =
                                "Archive export failed; provider may contain partial output: "
                                        + failure;
                    } finally {
                        staged.discard();
                    }
                    String result = message;
                    runOnUiThread(
                            () -> {
                                if (current(ticket)) {
                                    settled();
                                    status.setText(result);
                                }
                            });
                });
    }

    @Override
    public void onBackPressed() {
        if (busy) {
            cancellation.set(true);
            status.setText("Cancellation requested; wait for archive worker before leaving");
        } else if (session != null
                && !session.current().allEntries
                && !session.current().prefix.isEmpty()) parentFolder();
        else if (session != null && session.depth() > 1) backArchive();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("galleryMode", galleryMode);
        if (stableState != null) state.putString("archiveState", stableState);
        if (pendingExport != null) {
            state.putString("exportPath", pendingExport.file.getAbsolutePath());
            state.putString("exportName", pendingExport.name);
            state.putString("exportMime", pendingExport.mime);
            state.putLong("exportSize", pendingExport.size);
        }
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        closeGalleryPage();
        generation++;
        cancellation.set(true);
        runner.close();
        boolean retain = !isFinishing() && stableState != null;
        if (!retain && pendingExport != null) {
            pendingExport.discard();
            pendingExport = null;
        }
        worker.execute(
                () -> {
                    if (session != null) session.close(retain);
                });
        worker.shutdown();
        super.onDestroy();
    }
}

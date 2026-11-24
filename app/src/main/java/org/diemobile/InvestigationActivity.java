// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared lazy-table navigation/export UI; binary/metadata logic lives in providers. */
public final class InvestigationActivity extends Activity {
    private static final int HISTORY_LIMIT = 64;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayDeque<Location> history = new ArrayDeque<>();
    private InvestigationBackend backend;
    private Location location, produced;
    private EvidenceRows source;
    private EvidenceRows.Selection selection;
    private EvidenceRows.Page shown;
    private List<InvestigationBackend.Link> roots = Collections.emptyList();
    private String provenance = "{}", metadata = "{}";
    private File bytes, exportDirectory;
    private String inputName;
    private String retentionState, pendingInputRequest;
    private String inputNotice = "";
    private long byteSize;
    private volatile int generation;
    private boolean busy, binding, pickerOpen, expired, communityAttempted;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private PreparedExport prepared, preview;
    private EditText filter;
    private Spinner pageSize;
    private TextView title, context, status, mechanism;
    private Button mechanismToggle;
    private ExpandableHelp explanations;
    private ListView records;
    private Button back, views, reload, cancel, previous, next, search, export;

    private static final class Location {
        final String request, filter, draft;
        final int page, pageSize;
        Location(String request, String filter, String draft, int page, int pageSize) {
            if (request != null && request.length() > 16384
                    || filter.length() > 1024
                    || draft.length() > 1024
                    || page < 0
                    || !(pageSize == 1 || pageSize == 10 || pageSize == 50))
                throw new IllegalArgumentException("Invalid investigation location");
            this.request = request; this.filter = filter; this.draft = draft; this.page = page; this.pageSize = pageSize;
        }
        Bundle bundle() {
            Bundle value = new Bundle();
            value.putString("request", request);
            value.putString("filter", filter);
            value.putString("draft", draft);
            value.putInt("page", page);
            value.putInt("pageSize", pageSize);
            return value;
        }
        static Location restore(Bundle value) {
            return new Location(
                    value.getString("request"),
                    value.getString("filter", ""),
                    value.getString("draft", ""),
                    Math.max(0, value.getInt("page")),
                    value.getInt("pageSize", 50));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size) {
        return WorkbenchUi.text(this, value, size);
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(12);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void add(LinearLayout row, View view) { row.addView(view, new LinearLayout.LayoutParams(0, -2, 1)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        explanations = new ExpandableHelp(this);
        communityAttempted = state != null && state.getBoolean("communityAttempted");
        String path = getIntent().getStringExtra("path"); if (path == null) { finish(); return; }
        File root = new File(path);
        inputName = getIntent().getStringExtra("name");
        if (inputName == null) inputName = root.getName();
        if(state!=null) {
            retentionState = state.getString("providerState");
            pendingInputRequest = state.getString("pendingInputRequest");
            if(pendingInputRequest!=null)pickerOpen=true; }
        exportDirectory = new File(getCacheDir(), "tool-exports");
        if ("comparison".equals(getIntent().getStringExtra("provider")))
            backend =
                    new ComparisonBackend(
                            root,
                            new File(getCacheDir(), "comparison-snapshots"),
                            inputName,
                            getIntent().getStringExtra("sha256"),
                            getIntent().getLongExtra("sourceSize", -1),
                            retentionState);
        else if ("desktop-batch".equals(getIntent().getStringExtra("provider")))
            backend =
                    new BatchScanBackend(
                            new File(getFilesDir(), "desktop-batches"),
                            new File(getCacheDir(), "batch-view-snapshots"),
                            getIntent().getStringExtra("batchId"));
        else if ("binder-registry".equals(getIntent().getStringExtra("provider")))
            backend =
                    new BinderRegistryBackend(
                            root,
                            new File(getCacheDir(), "binder-registry-snapshots"),
                            BinderServiceCapture.directory(this),
                            getIntent().getStringExtra("sha256"),
                            getIntent().getLongExtra("sourceSize", -1),
                            () -> getAssets().open("references/android-15-service.cpp"));
        else if ("binwalk".equals(getIntent().getStringExtra("provider")))
            backend =
                    new BinwalkBackend(
                            root,
                            new File(getCacheDir(), "binary-snapshots"),
                            inputName,
                            new BinaryWorkerClient(this));
        else if ("photorec".equals(getIntent().getStringExtra("provider")))
            backend =
                    new PhotoRecBackend(
                            root,
                            new File(getCacheDir(), "carving-snapshots"),
                            inputName,
                            new BinaryWorkerClient(this, "photorec"));
        else if ("mpv".equals(getIntent().getStringExtra("provider")))
            backend =
                    new MpvBackend(
                            root,
                            new File(getCacheDir(), "media-snapshots"),
                            inputName,
                            new BinaryWorkerClient(this, "mpv"));
        else if ("resources".equals(getIntent().getStringExtra("provider")))
            backend =
                    new AndroidResourceBackend(
                            root,
                            new File(getCacheDir(), "resource-snapshots"),
                            inputName,
                            new BinaryWorkerClient(this, "resources"));
        else if ("hashes".equals(getIntent().getStringExtra("provider")))
            backend =
                    new HashBackend(
                            root,
                            new File(getCacheDir(), "hash-snapshots"),
                            inputName,
                            new BinaryWorkerClient(this, "hashes"));
        else if ("apk-signing".equals(getIntent().getStringExtra("provider")))
            backend =
                    new ApkSigningBackend(
                            root,
                            new File(getCacheDir(), "signing-snapshots"),
                            inputName,
                            getIntent().getStringExtra("sha256"),
                            getIntent().getLongExtra("sourceSize", -1),
                            retentionState);
        else if ("package-set".equals(getIntent().getStringExtra("provider")))
            backend =
                    new PackageSetBackend(
                            root,
                            new File(getCacheDir(), "package-snapshots"),
                            inputName,
                            getIntent().getStringExtra("sha256"),
                            getIntent().getLongExtra("sourceSize", -1),
                            getIntent().getStringArrayListExtra("packagePaths"),
                            getIntent().getStringExtra("inventoryPackage"),
                            retentionState,
                            new BinaryWorkerClient(this, "resources"));
        else if ("package-knowledge".equals(getIntent().getStringExtra("provider")))
            backend =
                    new PackageKnowledgeBackend(
                            () -> getAssets().open("knowledge/uad.json"),
                            new File(getCacheDir(), "package-knowledge-snapshots"),
                            getIntent().getStringExtra("knowledgePackageId"),
                            retentionState,
                            new File(getCacheDir(), "package-knowledge-subjects"),
                            getIntent().getStringExtra("knowledgeSubject"));
        else if ("sdk-rules".equals(getIntent().getStringExtra("provider")))
            backend =
                    new SdkCatalogBackend(
                            () -> getAssets().open("knowledge/libchecker-v5.json"),
                            new File(getCacheDir(), "knowledge-snapshots"));
        else if ("tracker-rules".equals(getIntent().getStringExtra("provider")))
            backend =
                    new TrackerCatalogBackend(
                            () ->
                                    new FileInputStream(
                                            new File(getFilesDir(), "exodus-trackers.json")),
                            new File(getCacheDir(), "tracker-snapshots"));
        else if ("metadata".equals(getIntent().getStringExtra("provider")))
            backend =
                    new MetadataBackend(
                            root, new File(getCacheDir(), "metadata-snapshots"), inputName);
        else if ("strings".equals(getIntent().getStringExtra("provider")))
            backend =
                    new StringBackend(
                            root,
                            new File(getCacheDir(), "string-snapshots"),
                            inputName,
                            ScanPreferences.load(this).minimumStringLength);
        else
            backend =
                    new DexBackend(
                            root,
                            new File(getCacheDir(), "investigation-inputs"),
                            inputName,
                            getIntent().getStringExtra("sha256"),
                            getIntent().getLongExtra("sourceSize", root.length()));
        if (state != null) inputNotice = state.getString("inputNotice", "");
        createViews(); location = new Location(null, "", "", 0, 50);
        if (state != null) {
            try {
                Bundle saved = state.getBundle("location");
                if (saved != null) location = Location.restore(saved);
                ArrayList<Bundle> trail = state.getParcelableArrayList("history");
                if (trail != null && trail.size() <= HISTORY_LIMIT) for (Bundle item : trail) history.addLast(Location.restore(item));
                expired = state.getBoolean("expired");
            } catch (RuntimeException e) { history.clear();
                status.setText("Navigation state expired; reopening input");
            }
            if (state.getString("prepared") != null) try {
                    prepared =
                            PreparedExport.restore(
                                    exportDirectory,
                                    state.getString("prepared"),
                                    state.getString("exportName"),
                                    state.getString("exportMime"),
                                    state.getLong("exportSize"));
                    pickerOpen = true;
            } catch (IOException e) { status.setText("Prepared export expired; prepare it again"); }
        }
        bind(location);
        if (root.isFile()
                || "comparison".equals(getIntent().getStringExtra("provider"))
                        && retentionState != null
                || "desktop-batch".equals(getIntent().getStringExtra("provider"))
                || "package-set".equals(getIntent().getStringExtra("provider"))
                || "apk-signing".equals(getIntent().getStringExtra("provider"))
                        && retentionState != null
                || "sdk-rules".equals(getIntent().getStringExtra("provider"))
                || "tracker-rules".equals(getIntent().getStringExtra("provider"))
                || "package-knowledge".equals(getIntent().getStringExtra("provider")))
            load(location, false);
        else {
            status.setText(
                    "Input snapshot expired; reopen it from Scan. An available prepared export can"
                        + " still finish.");
            controls(); }
    }
    private void createViews() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xff15191e); root.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left + dp(8), bars.top + dp(8), bars.right + dp(8), bars.bottom + dp(8)); return insets;
            });
        }
        title = text(inputName, 17); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.MIDDLE); root.addView(title);
        context = text("Loading investigation…", 12);
        context.setMaxLines(3); context.setEllipsize(TextUtils.TruncateAt.END);
        context.setOnClickListener(
                v ->
                        showText(
                                "Scope / provenance / interpretation",
                                context.getText()
                                        + "\n\n"
                                        + mechanism.getText()
                                        + "\n\n"
                                        + pretty(metadata)
                                        + "\n\n"
                                        + pretty(provenance)));
        root.addView(context);
        root.addView(explanations.controls(this));
        mechanismToggle = button("Scope and interpretation", () -> {});
        root.addView(mechanismToggle);
        mechanism = text("", 12);
        mechanism.setTextIsSelectable(true);
        mechanism.setTag("explanation:investigation.scope");
        root.addView(mechanism);
        LinearLayout navigation = new LinearLayout(this);
        back = button("Back", this::goBack);
        add(navigation, back);
        views = button("Root views", this::rootViews);
        add(navigation, views);
        reload = button("Reload view", () -> load(capture(), false)); add(navigation, reload);
        cancel =
                button(
                        "Cancel",
                        () -> {
                            cancellation.set(true);
                            status.setText(
                                    "Cancellation requested • waiting for current engine/I/O"
                                        + " operation");
                        });
        add(navigation, cancel); root.addView(navigation);
        LinearLayout filtering = new LinearLayout(this); filter = new EditText(this); filter.setSingleLine();
        filter.setHint("Search current evidence (press Search)");
        filter.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1024)}); filtering.addView(filter, new LinearLayout.LayoutParams(0, -2, 3));
        search =
                button(
                        "Search",
                        () -> {
                            Location current = capture();
                            load(
                                    new Location(
                                            current.request,
                                            current.draft,
                                            current.draft,
                                            0,
                                            current.pageSize),
                                    true);
                        });
        add(filtering, search); root.addView(filtering);
        LinearLayout paging = new LinearLayout(this);
        previous = button("Previous", () -> page(-1));
        add(paging, previous);
        next = button("Next", () -> page(1));
        add(paging, next);
        pageSize = new Spinner(this);
        pageSize.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[] {"50 rows", "10 rows", "1 row"}));
        add(paging, pageSize);
        export = button("Export all", this::exportTable); add(paging, export); root.addView(paging);
        status = text("Ready", 12);
        status.setMaxLines(3); status.setTextIsSelectable(true); root.addView(status);
        records = new ListView(this); root.addView(records, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        WorkbenchUi.list(this, records);
        filter.setId(R.id.investigation_filter); records.setId(R.id.investigation_records); status.setId(R.id.investigation_status);
        filter.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (!binding && !busy && produced != null)
                            status.setText(
                                    "Search text changed • press Search; displaying captured"
                                        + " results");
                    }

                    public void afterTextChanged(Editable value) {}
                });
        filter.setOnEditorActionListener((v, action, event) -> { if (!busy && !pickerOpen) search.performClick(); return true; });
        pageSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding && !busy && !pickerOpen && produced != null) {
                    int count = new int[]{50,10,1}[position];
                    if (produced.pageSize != count) load(new Location(produced.request, produced.filter, filter.getText().toString(), 0, count), true);
                }
            }
        });
        records.setOnItemClickListener((parent, view, index, id) -> { if (!busy && shown != null) details(shown.rows.get(index)); });
    }
    private void bind(Location value) {
        binding = true; filter.setText(value.draft); pageSize.setSelection(value.pageSize == 50 ? 0 : value.pageSize == 10 ? 1 : 2); binding = false;
    }
    private Location capture() {
        Location current = produced == null ? location : produced;
        return new Location(current.request, current.filter, filter.getText().toString(), current.page, current.pageSize);
    }
    private int begin(String message) {
        cancellation.set(true); cancellation = new AtomicBoolean(); int ticket = ++generation; busy = true; controls(); status.setText(message); return ticket;
    }
    private void controls() {
        boolean enabled = !busy && !pickerOpen;
        back.setEnabled(enabled && !history.isEmpty()); views.setEnabled(enabled && !roots.isEmpty()); reload.setEnabled(enabled); cancel.setEnabled(busy);
        search.setEnabled(enabled && source != null); filter.setEnabled(enabled); pageSize.setEnabled(enabled); export.setEnabled(enabled && source != null);
        records.setEnabled(enabled); previous.setEnabled(enabled && shown != null && shown.number > 0); next.setEnabled(enabled && shown != null && shown.number + 1 < shown.pageCount);
    }
    private void load(Location target, boolean reuse) {
        if (pickerOpen && prepared == null) return;
        final EvidenceRows retained = reuse && produced != null && Objects.equals(produced.request, target.request) ? source : null;
        final EvidenceRows.Selection retainedSelection = retained != null && produced.filter.equals(target.filter) ? selection : null;
        final int ticket = begin("Reading selected evidence scope…");
        final AtomicBoolean stop = cancellation;
        location = target; produced = null; source = null; selection = null; shown = null; records.setAdapter(null); bind(target);
        worker.execute(
                () -> {
                    try {
                        EvidenceRows.check(stop::get);
                        String request =
                                target.request == null
                                        ? backend.initialRequest(stop::get)
                                        : target.request;
                        EvidenceRows rows =
                                retained == null ? backend.open(request, stop::get) : retained;
                        EvidenceRows.Selection selected =
                                retainedSelection == null
                                        ? rows.select(target.filter, stop::get)
                                        : retainedSelection;
                        EvidenceRows.Page page =
                                selected.page(target.page, target.pageSize, stop::get);
                        String origin = backend.provenance().toString(),
                                meta = rows.metadata().toString();
                        File input = backend.byteSource();
                        String display = backend.sourceName();
                        long size = input.length();
                        String retainedState = backend.retentionState();
                        List<InvestigationBackend.Link> rootViews =
                                backend.roots(request, stop::get);
                        EvidenceRows.check(stop::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    busy = false;
                                    source = rows;
                                    selection = selected;
                                    shown = page;
                                    roots = rootViews;
                                    bytes = input;
                                    inputName = display;
                                    byteSize = size;
                                    provenance = origin;
                                    metadata = meta;
                                    retentionState = retainedState;
                                    produced =
                                            new Location(
                                                    request,
                                                    target.filter,
                                                    target.draft,
                                                    page.number,
                                                    target.pageSize);
                                    location = produced;
                                    present();
                                    if (!communityAttempted
                                            && backend instanceof PackageSetBackend
                                            && getIntent()
                                                    .getBooleanExtra(
                                                            "openCapturedPackageKnowledge",
                                                            false)) {
                                        communityAttempted = true;
                                        openCapturedPackageKnowledge(0);
                                    }
                                });
                    } catch (Exception | LinkageError | StackOverflowError error) {
                        failed(ticket, "Investigation failed: " + error.getMessage());
                    }
                });
    }
    private void failed(int ticket, String message) { runOnUiThread(() -> { if (!isDestroyed() && ticket == generation) { busy = false; controls(); status.setText(message); } }); }
    private void present() {
        title.setText(source.title);
        context.setText(
                (backend.statusSummary().isEmpty() ? "" : backend.statusSummary() + "\n")
                        + inputName
                        + " • "
                        + byteSize
                        + " bytes • "
                        + source.size()
                        + " records in scope");
        mechanism.setText(backend.help(produced.request));
        explanations.bind(
                "investigation." + getIntent().getStringExtra("provider") + "." + source.kind,
                "Scope and interpretation",
                mechanismToggle,
                mechanism,
                true,
                false);
        records.setAdapter(
                new BaseAdapter() {
                    public int getCount() {
                        return shown.rows.size();
                    }

                    public Object getItem(int i) {
                        return shown.rows.get(i);
                    }

                    public long getItemId(int i) {
                        return shown.rows.get(i).index;
                    }

                    public View getView(int i, View old, ViewGroup parent) {
                        EvidenceRows.Row row = shown.rows.get(i);
                        WorkbenchUi.Lines lines =
                                new WorkbenchUi.Lines(InvestigationActivity.this)
                                        .heading(
                                                "#"
                                                        + row.index
                                                        + " "
                                                        + WorkbenchUi.preview(row.name, 180));
                        // String summaries were produced with the evidence on the worker; do not
                        // parse/reclassify potentially large match arrays on every UI row bind.
                        String[] description = row.description.split("\n", 2);
                        lines.line(WorkbenchUi.preview(description[0], 300));
                        if (description.length > 1) {
                            String why = WorkbenchUi.preview(description[1], 550);
                            int colon = why.indexOf(':');
                            if ((source.kind.startsWith("strings-")
                                            || source.kind.equals("dex-classified-strings"))
                                    && why.startsWith("Why ")
                                    && colon > 0)
                                lines.field(
                                        why.substring(0, colon),
                                        why.substring(colon + 1).trim(),
                                        false);
                            else lines.line(why);
                        }
                        lines.field(
                                        "Physical offset",
                                        NativeTable.hexOffset(row.physicalOffset),
                                        true)
                                .field(
                                        "Stored bytes",
                                        row.sizeBytes == null
                                                ? "unknown / associated location"
                                                : row.sizeBytes.toString(),
                                        true);
                        return WorkbenchUi.row(InvestigationActivity.this, old, lines.build());
                    }
                });
        status.setText(
                shown.matches
                        + " matches of "
                        + source.size()
                        + " records • page "
                        + (shown.number + 1)
                        + "/"
                        + shown.pageCount
                        + " • "
                        + produced.pageSize
                        + " rows/page"
                        + (expired ? " • oldest Back location expired (64 retained)" : "")
                        + (!filter.getText().toString().equals(produced.filter)
                                ? " • edited search not applied"
                                : "")
                        + (inputNotice.isEmpty() ? "" : "\n" + inputNotice));
        controls();
    }
    private void page(int delta) { if (produced == null || busy) return; Location current = capture(); load(new Location(current.request, current.filter, current.draft, Math.max(0,current.page + delta), current.pageSize), true); }
    private void navigate(InvestigationBackend.Link link) {
        if (busy || pickerOpen) return;
        if (history.size() == HISTORY_LIMIT) { history.removeFirst(); expired = true; } history.addLast(capture());
        int rows = location.pageSize;
        load(new Location(link.request, "", "", link.row < 0 ? 0 : link.row / rows, rows), false);
    }
    private void goBack() { if (busy || pickerOpen) return; Location target = history.pollLast(); if (target == null) { finish(); return; } load(target, false); }
    @Override public void onBackPressed() {
        if (busy) { cancellation.set(true);
            status.setText("Cancellation requested • wait for the operation to settle");
        }
        else if (!history.isEmpty() && !pickerOpen) goBack(); else super.onBackPressed();
    }
    private void rootViews() {
        if (busy || pickerOpen) return;
        InputSnapshot owned = backend.ownedByteSource();
        String[] names = new String[roots.size() + (owned == null ? 0 : 1)];
        for (int i = 0; i < roots.size(); i++) names[i] = roots.get(i).label;
        if (owned != null)
            names[roots.size()] = "Gallerize this captured input / gallery and byte grids";
        new AlertDialog.Builder(this)
                .setTitle("Root evidence views")
                .setItems(
                        names,
                        (dialog, which) -> {
                            if (which < roots.size()) followLink(roots.get(which));
                            else
                                openStoredContent(
                                        owned,
                                        new FileRange(owned.size, 0, owned.size),
                                        inputName,
                                        true,
                                        null);
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void followLink(InvestigationBackend.Link link) {
        if(link.input) selectAdditionalInput(link.request);
        else if (link.binary) prepare(stop -> PreparedExport.bytes(exportDirectory,link.textName,backend.binaryValue(link.request,stop::get),stop::get));
        else if (link.textName != null) renderText(link); else navigate(link);
    }
    private void selectAdditionalInput(String request) {
        if (busy || pickerOpen) return;
        if (!backend.inputRequiresDocument(request)) {
            acceptAdditionalInput(request, null);
            return;
        }
        pendingInputRequest = request;
        pickerOpen = true;
        controls();
        try {
            startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*"),
                    2);
        } catch (RuntimeException error) {
            pendingInputRequest = null;
            pickerOpen = false;
            controls();
            status.setText("Cannot open input picker: " + error.getMessage());
        }
    }

    private void acceptAdditionalInput(String request, Uri uri) {
        inputNotice = "";
        int ticket = begin("Acquiring / verifying selected supplemental input…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    boolean adopted = false;
                    try {
                        if (uri == null) {
                            backend.acceptInput(request, null, "", stop::get);
                            adopted = true;
                        } else {
                            String name = "Selected supplemental input";
                            try (android.database.Cursor cursor =
                                    getContentResolver()
                                            .query(
                                                    uri,
                                                    new String[] {
                                                        android.provider.OpenableColumns
                                                                .DISPLAY_NAME
                                                    },
                                                    null,
                                                    null,
                                                    null)) {
                                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                                    name = cursor.getString(0);
                            } catch (Exception ignored) {
                                // Some readable providers do not implement metadata queries.
                            }
                            try (InputStream input = getContentResolver().openInputStream(uri)) {
                                if (input == null)
                                    throw new IOException("Selected document is unavailable");
                                backend.acceptInput(request, input, name, stop::get);
                                adopted = true;
                            }
                        }
                        acceptedInput(ticket, "");
                    } catch (Exception | LinkageError | StackOverflowError error) {
                        if (adopted) {
                            try {
                                acceptedInput(
                                        ticket,
                                        "Selected input was adopted; provider close / follow-up"
                                            + " failed: "
                                                + error.getMessage());
                            } catch (Exception recovery) {
                                failed(
                                        ticket,
                                        "Selected input was adopted, but its new view could not be"
                                            + " restored: "
                                                + recovery.getMessage());
                            }
                        } else
                            failed(
                                    ticket,
                                    "Supplemental input failed; prior captured evidence retained: "
                                            + error.getMessage());
                    }
                });
    }

    /** Called only after the backend commit point, even if provider close subsequently fails. */
    private void acceptedInput(int ticket, String notice) throws Exception {
        String retained = backend.retentionState();
        String capturedNotice =
                notice.length() > 16384
                        ? notice.substring(0, 16384) + " [diagnostic preview capped]"
                        : notice;
        runOnUiThread(() -> {
            if (isDestroyed() || ticket != generation) return;
            retentionState = retained;
            inputNotice = capturedNotice;
            busy = false;
            history.clear();
            expired = false;
            load(new Location(null, "", "", 0, location.pageSize), false);
        });
    }

    private void showText(String caption, String value) {
        ScrollView scroll = new ScrollView(this);
        String displayed =
                value.length() > 128 * 1024
                        ? value.substring(0, 128 * 1024)
                                + "\n\n"
                                + "DISPLAY PREVIEW: full scoped JSON remains available through"
                                + " export."
                        : value;
        TextView body = text(displayed, 13); body.setTextIsSelectable(true); body.setPadding(dp(12),dp(8),dp(12),dp(8)); scroll.addView(body);
        body.setText(visible(displayed));
        new AlertDialog.Builder(this)
                .setTitle(caption)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private static String pretty(String value) {
        try {
            return new JSONObject(value).toString(2);
        } catch (JSONException e) {
            return value;
        }
    }

    /** Keep invisible direction/control characters legible; exported values remain exact. */
    private static String visible(String value) {
        return WorkbenchUi.visible(value);
    }

    private void details(EvidenceRows.Row row) {
        ScrollView scroll = new ScrollView(this);
        String value = pretty(row.encoded);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(body);
        body.addView(explanations.controls(this));
        StringExplanation classification = null;
        try {
            classification =
                    StringExplanation.read(
                            row,
                            source.kind,
                            new JSONObject(metadata).optString("selectedGroup", ""));
            if (classification != null)
                StringFindingView.add(this, body, row, classification, explanations);
        } catch (Exception error) {
            body.addView(
                    WorkbenchUi.prose(
                            this,
                            "Classification explanation unavailable: "
                                    + error.getMessage()
                                    + ". Original evidence is retained below."));
        }
        if (classification == null) {
            TextView name = text(visible(row.name), 16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextIsSelectable(true);
            body.addView(name);
            TextView facts =
                    text(
                            "Physical offset: "
                                    + NativeTable.hexOffset(row.physicalOffset)
                                    + "\nStored bytes: "
                                    + (row.sizeBytes == null
                                            ? "unknown / associated location"
                                            : row.sizeBytes),
                            13);
            facts.setTypeface(Typeface.MONOSPACE);
            facts.setTextIsSelectable(true);
            body.addView(facts);
            TextView description = text(visible(row.description), 13);
            description.setTextIsSelectable(true);
            description.setPadding(0, dp(8), 0, dp(8));
            body.addView(description);
        }
        body.addView(WorkbenchUi.section(this, "Original record fields"));
        TextView raw =
                text(
                        visible(
                                value.length() > 128 * 1024
                                        ? value.substring(0, 128 * 1024)
                                                + "\n"
                                                + "DISPLAY PREVIEW • export the complete"
                                                + " record/table for all values."
                                        : value),
                        12);
        raw.setTypeface(Typeface.MONOSPACE);
        raw.setTextIsSelectable(true);
        Button rawToggle = button("Original record JSON", () -> {});
        body.addView(rawToggle);
        body.addView(raw);
        explanations.bind(
                "record.raw." + source.kind, "Original record JSON", rawToggle, raw, false, true);
        explanations.paragraph(
                this,
                body,
                "detail." + source.kind,
                "Interpretation / mechanisms / limits",
                backend.help(produced.request),
                true,
                false);
        new AlertDialog.Builder(this)
                .setTitle("Record " + row.index + " • " + source.kind)
                .setView(scroll)
                .setNegativeButton("Close", null)
                .setPositiveButton("Actions", (dialog, which) -> rowActions(row))
                .show();
    }

    private void rowActions(EvidenceRows.Row row) {
        if (busy || pickerOpen || produced == null) return;
        final String request = produced.request; final EvidenceRows captured = source;
        final int ticket = begin("Resolving record relationships…");
        final AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        List<InvestigationBackend.Link> links =
                                backend.related(request, captured, row, stop::get);
                        EvidenceRows.check(stop::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    busy = false;
                                    controls();
                                    actionDialog(row, links);
                                });
                    } catch (Exception | StackOverflowError error) {
                        failed(ticket, "Relationship lookup failed: " + error.getMessage());
                    }
                });
    }
    private void actionDialog(EvidenceRows.Row row, List<InvestigationBackend.Link> links) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (InvestigationBackend.Link link : links) {
            labels.add(link.label);
            actions.add(() -> followLink(link));
        }
        if (backend instanceof PackageSetBackend) {
            try {
                Integer sourceIndex = ((PackageSetBackend) backend).knowledgeSource(source, row);
                if (sourceIndex != null) {
                    labels.add("What does this captured package do? / sourced community records");
                    actions.add(() -> openCapturedPackageKnowledge(sourceIndex));
                }
            } catch (Exception error) {
                status.setText("Package association unavailable: " + error.getMessage());
            }
        }
        if (row.physicalOffset != null && row.physicalOffset < byteSize) {
            labels.add("Hex at associated physical offset");
            actions.add(
                    () -> {
                        if (!bytes.isFile() || bytes.length() != byteSize) {
                            status.setText(
                                    "Input snapshot expired or changed; reopen it from Scan");
                            return;
                        }
                        openHex(row.physicalOffset);
                    });
        }
        if (row.physicalOffset != null
                && row.physicalOffset < byteSize
                && backend.ownedByteSource() != null
                && (source.kind.equals("binwalk-candidates")
                        || source.kind.equals("photorec-candidates"))) {
            labels.add("Inspect archive / compressed stream at this offset (parsing interval)");
            actions.add(() -> openArchiveHypothesis(row));
        }
        if (row.physicalOffset != null && row.sizeBytes != null)
            try {
                FileRange range = new FileRange(byteSize, row.physicalOffset, row.sizeBytes);
                if (range.length > 0 && range.length <= FileRange.MAX_OPERATION_BYTES) {
                    labels.add("Analyze stored range: entropy / SHA-256 / CRC32");
                    actions.add(() -> rangeAnalysis(range));
                    InputSnapshot owned = backend.ownedByteSource();
                    String recoveredName = recoveredName(row, range);
                    if (owned != null) {
                        labels.add("View exact recovered bytes / media / text");
                        actions.add(
                                () ->
                                        openStoredContent(
                                                owned,
                                                range,
                                                inputName + " · " + row.name + " #" + row.index,
                                                false,
                                                recoveredName,
                                                row));
                    }
                    labels.add("Export exact stored range");
                    actions.add(
                            () ->
                                    prepare(
                                            stop ->
                                                    owned == null
                                                            ? PreparedExport.range(
                                                                    exportDirectory,
                                                                    "range-"
                                                                            + Long.toHexString(
                                                                                    range.offset)
                                                                            + ".bin",
                                                                    bytes,
                                                                    range,
                                                                    stop::get)
                                                            : RecoveredFiles.namedRange(
                                                                    owned,
                                                                    range,
                                                                    exportDirectory,
                                                                    recoveredName,
                                                                    stop::get)));
                }
            } catch (Exception error) {
                status.setText("Stored-file actions unavailable: " + error.getMessage());
            }
        labels.add("Export complete selected record JSON");
        actions.add(
                () -> {
                    final String origin = provenance, meta = metadata;
                    final EvidenceRows captured = source;
                    EvidenceRows one =
                            new EvidenceRows(
                                    capturedKind(), "Selected original record " + row.index) {
                                int size() {
                                    return 1;
                                }

                                Row row(int ignored, java.util.function.BooleanSupplier stop)
                                        throws Exception {
                                    EvidenceRows.check(stop);
                                    return row;
                                }

                                JSONObject metadata() throws Exception {
                                    return new JSONObject(meta).put("recordSelection", row.index);
                                }

                                @Override
                                void writeSelectedRow(
                                        Writer out,
                                        Row selected,
                                        java.util.function.BooleanSupplier stop)
                                        throws Exception {
                                    captured.writeSelectedRow(out, selected, stop);
                                }
                            };
                    prepare(
                            stop ->
                                    PreparedExport.stream(
                                            exportDirectory,
                                            "evidence-record.json",
                                            "application/json",
                                            out ->
                                                    one.writeReport(
                                                            out,
                                                            new JSONObject(origin),
                                                            "",
                                                            0,
                                                            stop::get),
                                            stop::get));
                });
        new AlertDialog.Builder(this)
                .setTitle("Record actions")
                .setItems(
                        labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void openCapturedPackageKnowledge(int sourceIndex) {
        if (busy || pickerOpen || !(backend instanceof PackageSetBackend)) return;
        PackageSetBackend capturedBackend = (PackageSetBackend) backend;
        int ticket = begin("Capturing the exact APK package declaration for community lookup…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        PackageKnowledgeSubject captured =
                                capturedBackend.captureKnowledgeSubject(
                                        sourceIndex,
                                        new File(getCacheDir(), "package-knowledge-subjects"),
                                        stop::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation || stop.get()) {
                                        captured.close();
                                        return;
                                    }
                                    busy = false;
                                    controls();
                                    try {
                                        startActivity(
                                                new Intent(this, InvestigationActivity.class)
                                                        .putExtra("provider", "package-knowledge")
                                                        .putExtra(
                                                                "path",
                                                                new File(
                                                                                getCacheDir(),
                                                                                "package-knowledge-catalog")
                                                                        .getAbsolutePath())
                                                        .putExtra("name", captured.packageId)
                                                        .putExtra(
                                                                "knowledgePackageId",
                                                                captured.packageId)
                                                        .putExtra(
                                                                "knowledgeSubject",
                                                                captured.descriptor()));
                                        status.setText(
                                                "Community lookup for captured manifest ID "
                                                        + visible(captured.packageId)
                                                        + " · exact-ID association, not verified"
                                                        + " runtime behavior");
                                    } catch (Exception error) {
                                        captured.close();
                                        status.setText(
                                                "Community lookup could not open: "
                                                        + error.getMessage());
                                    }
                                });
                    } catch (Exception error) {
                        failed(ticket, "APK package association failed: " + error.getMessage());
                    }
                });
    }

    private void openArchiveHypothesis(EvidenceRows.Row row) {
        if (busy || pickerOpen) return;
        InputSnapshot input = backend.ownedByteSource();
        if (input == null) { status.setText("Captured input is unavailable"); return; }
        String kind = source.kind;
        int ticket =
                begin("Checking the captured source for an explicit archive parsing interval…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        input.checkIdentity(stop::get);
                        ArchiveLaunch launch =
                                ArchiveLaunch.fromFinding(input, kind, row, backend.provenance());
                        EvidenceRows.check(stop::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    busy = false;
                                    pickerOpen = true;
                                    controls();
                                    try {
                                        startActivityForResult(
                                                new Intent(
                                                                this,
                                                                NativeArchiveExplorerActivity.class)
                                                        .putExtra(
                                                                "path",
                                                                input.file.getAbsolutePath())
                                                        .putExtra("name", inputName)
                                                        .putExtra("sha256", input.sha256)
                                                        .putExtra("sourceSize", input.size)
                                                        .putExtra("offset", launch.offset)
                                                        .putExtra("length", launch.length)
                                                        .putExtra("archiveLaunch", launch.raw)
                                                        .putExtra("originPreviewResult", true),
                                                3);
                                        status.setText(
                                                "Opened an explicit parsing tail at 0x"
                                                        + Long.toHexString(launch.offset)
                                                        + "; the original finding's extent is"
                                                        + " unchanged");
                                    } catch (RuntimeException failure) {
                                        pickerOpen = false;
                                        controls();
                                        status.setText(
                                                "Archive browser could not open: " + failure);
                                    }
                                });
                    } catch (Exception failure) {
                        failed(
                                ticket,
                                "Archive hypothesis could not open: " + failure.getMessage());
                    }
                });
    }

    private void archiveResult(int result, Intent intent) {
        pickerOpen = false;
        if (result != RESULT_OK || intent == null) { controls();
            status.setText("Archive member selection cancelled");
            return; }
        String sha = intent.getStringExtra("archiveRootSha256");
        long size = intent.getLongExtra("archiveRootSize", -1);
        if (sha == null
                || !sha.matches("[0-9a-f]{64}")
                || size < 0
                || size > NativeArchiveStore.MAX_INPUT) {
            String path = intent.getStringExtra("path");
            if (path != null)
                ArchiveMemberHandoff.discard(
                        new File(path), new File(getCacheDir(), "archive-snapshots"));
            controls();
            status.setText("Archive result has no valid captured parent identity");
            return;
        }
        int ticket = begin("Acquiring checked archive member for local preview…");
        AtomicBoolean stop = cancellation;
        ArchivePreviewLauncher.launch(this, worker, intent, sha, size, stop::get,
                () -> !isDestroyed() && ticket == generation,
                () -> { busy = false; controls(); }, status::setText);
    }

    private String capturedKind() {
        return source == null ? "selected-record" : source.kind + "-record";
    }
    private String recoveredName(EvidenceRows.Row row, FileRange range) throws Exception {
        String extension =
                RecoveredFiles.extension(row.json().optString("reportedExtension", row.name));
        String engine = source.kind.startsWith("photorec") ? "photorec" : "binwalk";
        return engine
                + "-"
                + row.index
                + "-at-0x"
                + Long.toHexString(range.offset)
                + "."
                + extension;
    }

    private void openStoredContent(
            InputSnapshot sourceInput,
            FileRange range,
            String display,
            boolean gallery,
            String suggestedName) {
        openStoredContent(sourceInput, range, display, gallery, suggestedName, null);
    }

    private void openStoredContent(InputSnapshot sourceInput, FileRange range, String display,
            boolean gallery, String suggestedName, EvidenceRows.Row finding) {
        if (busy || pickerOpen) return;
        final String findingKind = source == null ? "unavailable" : source.kind;
        final String capturedProvenance = provenance, capturedMetadata = metadata;
        int ticket =
                begin(
                        gallery
                                ? "Owning captured input for the gallery…"
                                : "Acquiring exact recovered bytes for local viewing…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    InputSnapshot staged = null;
                    ContentOrigin origin = null;
                    try {
                        staged =
                                OwnedContent.stageRange(
                                        sourceInput,
                                        range,
                                        new File(
                                                getCacheDir(),
                                                gallery ? "gallery-handoffs" : "content-previews"),
                                        stop::get);
                        if (!gallery && finding != null) {
                            origin =
                                    ContentOrigin.create(
                                            new File(getCacheDir(), "content-origins"),
                                            staged,
                                            ContentOrigin.stored(sourceInput, range),
                                            records ->
                                                    records.value(
                                                            "selectedFinding",
                                                            new JSONObject()
                                                                    .put("kind", findingKind)
                                                                    .put("index", finding.index)
                                                                    .put(
                                                                            "originalRecordJson",
                                                                            finding.encoded)
                                                                    .put(
                                                                            "provenanceJson",
                                                                            capturedProvenance)
                                                                    .put(
                                                                            "metadataJson",
                                                                            capturedMetadata)),
                                            stop::get);
                        }
                        EvidenceRows.check(stop::get);
                        ContentOrigin capturedOrigin = origin;
                        InputSnapshot owned = staged;
                        staged = null;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) {
                                        owned.close();
                                        if (capturedOrigin != null) capturedOrigin.close();
                                        return;
                                    }
                                    busy = false;
                                    controls();
                                    try {
                                        Intent intent =
                                                new Intent(
                                                                this,
                                                                gallery
                                                                        ? GallerizeActivity.class
                                                                        : ContentPreviewActivity
                                                                                .class)
                                                        .putExtra(
                                                                "path",
                                                                owned.file.getAbsolutePath())
                                                        .putExtra("sha256", owned.sha256)
                                                        .putExtra("sourceSize", owned.size)
                                                        .putExtra("name", display)
                                                        .putExtra(
                                                                "originalSha256",
                                                                sourceInput.sha256)
                                                        .putExtra("originalSize", sourceInput.size)
                                                        .putExtra("originalOffset", range.offset)
                                                        .putExtra("originalKind", "stored-range");
                                        if (capturedOrigin != null)
                                            intent.putExtra(
                                                    "contentOrigin", capturedOrigin.descriptor());
                                        if (gallery) intent.putExtra("ownedInputHandoff", true);
                                        if (suggestedName != null)
                                            intent.putExtra("exportName", suggestedName);
                                        startActivity(intent);
                                        status.setText(
                                                "Independent content acquired · "
                                                        + owned.size
                                                        + " bytes · SHA-256 "
                                                        + owned.sha256);
                                    } catch (Exception error) {
                                        owned.close();
                                        if (capturedOrigin != null) capturedOrigin.close();
                                        status.setText("Content view could not open: " + error);
                                    }
                                });
                    } catch (Exception error) {
                        if (staged != null) staged.close();
                        if (origin != null) origin.close();
                        failed(ticket, "Content acquisition failed: " + error.getMessage());
                    }
                });
    }
    private void openHex(long offset) {
        File input = bytes; String name = inputName;
        int ticket = begin("Preparing independent byte view…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    InputSnapshot snapshot = null;
                    try {
                        snapshot =
                                InputSnapshot.acquire(
                                        input,
                                        new File(getCacheDir(), "shared-analysis-inputs"),
                                        FileRange.MAX_OPERATION_BYTES,
                                        stop::get);
                        EvidenceRows.check(stop::get);
                        final InputSnapshot owned = snapshot;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) {
                                        owned.close();
                                        return;
                                    }
                                    busy = false;
                                    controls();
                                    status.setText("Independent byte snapshot prepared");
                                    try {
                                        startActivity(
                                                new Intent(this, NativeToolsActivity.class)
                                                        .putExtra(
                                                                "path",
                                                                owned.file.getAbsolutePath())
                                                        .putExtra("name", name)
                                                        .putExtra("tool", 9)
                                                        .putExtra("offset", offset)
                                                        .putExtra("sha256", owned.sha256)
                                                        .putExtra("sourceSize", owned.size)
                                                        .putExtra("ownedSnapshot", true));
                                    } catch (RuntimeException error) {
                                        owned.close();
                                        status.setText(
                                                "Cannot open byte viewer: " + error.getMessage());
                                    }
                                });
                    } catch (Exception error) {
                        if (snapshot != null) snapshot.close();
                        failed(ticket, "Byte view preparation failed: " + error.getMessage());
                    }
                });
    }
    private void rangeAnalysis(FileRange range) {
        File input = bytes;
        int ticket = begin("Measuring stored physical range…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        RangeAnalysis.Result result =
                                RangeAnalysis.inspect(input, range, stop::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    busy = false;
                                    controls();
                                    showText(
                                            "Range evidence",
                                            "Physical offset: "
                                                    + NativeTable.hexOffset(range.offset)
                                                    + "\nBytes: "
                                                    + range.length
                                                    + "\nSHA-256: "
                                                    + result.sha256
                                                    + "\nCRC32: "
                                                    + result.crc32
                                                    + "\nShannon entropy: "
                                                    + result.entropy
                                                    + " bits/byte\n\n"
                                                    + "These measurements identify stored bytes."
                                                    + " Entropy alone does not establish"
                                                    + " encryption, packing or behavior.");
                                });
                    } catch (Exception e) {
                        failed(ticket, "Range analysis failed: " + e.getMessage());
                    }
                });
    }
    private interface Preparation { PreparedExport create(AtomicBoolean stop) throws Exception; }
    private void prepare(Preparation preparation) {
        if (busy || pickerOpen) return;
        int ticket = begin("Preparing immutable export…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    PreparedExport result = null;
                    try {
                        result = preparation.create(stop);
                        EvidenceRows.check(stop::get);
                    } catch (Exception | StackOverflowError e) {
                        if (result != null) result.discard();
                        failed(ticket, "Export preparation failed: " + e.getMessage());
                        return;
                    }
                    final PreparedExport staged = result;
                    runOnUiThread(
                            () -> {
                                if (isDestroyed() || ticket != generation) {
                                    staged.discard();
                                    return;
                                }
                                busy = false;
                                launchExport(staged);
                            });
                });
    }
    private void exportTable() {
        if (source == null || produced == null) return; EvidenceRows captured = source; Location view = produced; String origin = provenance;
        prepare(
                stop ->
                        PreparedExport.stream(
                                exportDirectory,
                                "evidence-" + captured.kind + ".json",
                                "application/json",
                                out ->
                                        captured.writeReport(
                                                out,
                                                new JSONObject(origin),
                                                view.filter,
                                                view.page,
                                                view.pageSize,
                                                stop::get),
                                stop::get));
    }
    private void launchExport(PreparedExport staged) {
        if (prepared != null) prepared.discard(); prepared = staged; pickerOpen = true; controls();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(staged.mime).putExtra(Intent.EXTRA_TITLE, staged.name); startActivityForResult(intent, 1);
    }
    private void renderText(InvestigationBackend.Link link) {
        int ticket = begin("Rendering complete text…"); AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    PreparedExport staged = null;
                    try {
                        String mime =
                                link.textName.toLowerCase(Locale.ROOT).endsWith(".json")
                                        ? "application/json"
                                        : "text/plain";
                        staged =
                                PreparedExport.stream(
                                        exportDirectory,
                                        link.textName,
                                        mime,
                                        out -> backend.writeText(link.request, out, stop::get),
                                        stop::get);
                        final PreparedExport ready = staged;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) {
                                        ready.discard();
                                        return;
                                    }
                                    if (preview != null) preview.discard();
                                    preview = ready;
                                    PagedTextDialog.show(
                                            this,
                                            worker,
                                            "Rendered text • " + ready.size + " UTF-8 bytes",
                                            ready.file,
                                            ready.size,
                                            this::isDestroyed,
                                            () -> {
                                                if (!isDestroyed() && ticket == generation) {
                                                    busy = false;
                                                    controls();
                                                    status.setText(
                                                            "Captured complete text • "
                                                                    + ready.size
                                                                    + " UTF-8 bytes");
                                                }
                                            },
                                            () -> {
                                                busy = false;
                                                preview = null;
                                                launchExport(ready);
                                            },
                                            () -> {
                                                if (preview == ready) {
                                                    preview.discard();
                                                    preview = null;
                                                }
                                                if (!isDestroyed() && ticket == generation) {
                                                    busy = false;
                                                    controls();
                                                }
                                            });
                                });
                    } catch (Exception | StackOverflowError e) {
                        if (staged != null) staged.discard();
                        failed(ticket, "Text rendering failed: " + e.getMessage());
                    }
                });
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request,result,intent);
        if (request == 3) { archiveResult(result, intent); return; }
        if(request==2) {
            String pending=pendingInputRequest;pendingInputRequest=null;pickerOpen=false;
            if(pending==null||result!=RESULT_OK||intent==null||intent.getData()==null) {
                controls();
                status.setText("Supplemental input selection cancelled; prior evidence retained");
                if(source==null)load(location,false);return;
            }
            acceptAdditionalInput(pending,intent.getData());return;
        }
        if (request != 1) return; pickerOpen = false;
        PreparedExport staged = prepared; prepared = null;
        if (staged == null) { controls();
            status.setText("Prepared export expired; prepare it again");
            return; }
        if (result != RESULT_OK || intent == null || intent.getData() == null) { staged.discard(); controls();
            status.setText("Export cancelled");
            return; }
        int ticket = begin("Writing prepared export…");
        AtomicBoolean stop = cancellation; Uri destination = intent.getData();
        worker.execute(
                () -> {
                    String message;
                    try (OutputStream out =
                            getContentResolver().openOutputStream(destination, "wt")) {
                        if (out == null) throw new IOException("Destination unavailable");
                        staged.copyTo(out, stop::get);
                        message = "Export saved • " + staged.size + " bytes";
                    } catch (Exception e) {
                        message =
                                "Export failed; destination may contain partial data: "
                                        + e.getMessage();
                    } finally {
                        staged.discard();
                    }
                    final String response = message;
                    runOnUiThread(
                            () -> {
                                if (!isDestroyed() && ticket == generation) {
                                    busy = false;
                                    controls();
                                    status.setText(
                                            response
                                                    + (source == null
                                                            ? "\n"
                                                                  + "Reload view to resume"
                                                                  + " investigation"
                                                            : ""));
                                }
                            });
                });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("communityAttempted", communityAttempted);
        if (location == null) return;
        state.putBundle("location", capture().bundle());
        ArrayList<Bundle> trail = new ArrayList<>(); for (Location value : history) trail.add(value.bundle());
        state.putParcelableArrayList("history", trail);
        state.putBoolean("expired", expired);
        state.putString("inputNotice", inputNotice);
        if(retentionState!=null)state.putString("providerState",retentionState);
        if(pendingInputRequest!=null)state.putString("pendingInputRequest",pendingInputRequest);
        if (prepared != null && pickerOpen) {
            state.putString("prepared", prepared.file.getAbsolutePath());
            state.putString("exportName", prepared.name);
            state.putString("exportMime", prepared.mime);
            state.putLong("exportSize", prepared.size);
        }
    }
    @Override protected void onDestroy() {
        generation++; cancellation.set(true); if (preview != null) preview.discard(); if (!pickerOpen && prepared != null) prepared.discard();
        boolean retainInputs=isChangingConfigurations();
        worker.execute(() -> { try { backend.close(retainInputs); } catch (Exception ignored) {} }); worker.shutdown(); super.onDestroy();
    }
}

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
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Native workbench: detached records, physical navigation and explicit exports. */
public final class NativeToolsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ToolHistory history = new ToolHistory();
    private File file, exportDirectory;
    private String inputName, inputSha256, engineRevision = "", raw = "";
    private String measuredHashInputSha;
    private long initialSize, producedSize, elapsedMs, lastOffset, nextOffset = -1;
    private NativeTable table, mapCache;
    private ToolRequest produced;
    private int currentTool = 1, page;
    private volatile int generation;
    private boolean binding, busy, pickerOpen, historyExpired;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private PreparedExport prepared;

    private ExpandableHelp explanations;
    private Button helpToggle;
    private Spinner tools, mode;
    private EditText offset, minimum, selector, filter;
    private CheckBox json, att;
    private TextView help, status, metadata, output;
    private ListView records;
    private ScrollView textScroll;
    private Button reload, cancel, previous, next, nextBlock, back, export;
    private NativeTable.Page visiblePage;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private TextView text(String value, int size) {
        return WorkbenchUi.text(this, value, size);
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(this); view.setText(label); view.setTextSize(12); view.setAllCaps(false);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private void addButton(LinearLayout row, Button button) { row.addView(button, new LinearLayout.LayoutParams(0, -2, 1)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        explanations = new ExpandableHelp(this);
        String path = getIntent().getStringExtra("path");
        if (path == null || !new File(path).isFile()) { finish(); return; }
        file = new File(path); initialSize = getIntent().getLongExtra("sourceSize", file.length());
        inputName = getIntent().getStringExtra("name"); if (inputName == null) inputName = file.getName();
        inputSha256 = getIntent().getStringExtra("sha256");
        if (inputSha256 != null && !inputSha256.matches("[0-9a-fA-F]{64}")) inputSha256 = null;
        exportDirectory = new File(getCacheDir(), "tool-exports");
        createViews();
        int tool = state == null ? getIntent().getIntExtra("tool", 1) : state.getInt("tool", 1);
        long start = state == null ? getIntent().getLongExtra("offset", -1) : state.getLong("offset", -1);
        int selectedMode = state == null ? tool == 8 ? 14 : 0 : state.getInt("mode", 0);
        int min = state == null ? ScanPreferences.load(this).minimumStringLength : state.getInt("minimum", 4);
        String query = state == null ? "" : state.getString("selector", "");
        boolean nativeJson = state != null && state.getBoolean("json");
        boolean syntax = state != null && state.getBoolean("att");
        ToolRequest initial = new ToolRequest(Math.max(0, Math.min(10, tool)), selectedMode, start, min, query, nativeJson, syntax);
        ToolRequest displayed = initial;
        if (state != null && state.getString("displayed") != null) try {
            ToolRequest saved = ToolRequest.fromJson(state.getString("displayed"));
            if (saved.tool == initial.tool) displayed = saved;
        } catch (JSONException | IllegalArgumentException ignored) { /* Reload the valid controls if saved data is incomplete. */ }
        bind(new ToolHistory.Location(displayed, initial, state == null ? "" : state.getString("filter", ""), state == null ? 0 : state.getInt("page", 0)));
        if (state != null && state.getString("prepared") != null) {
            try {
                prepared = PreparedExport.restore(exportDirectory, state.getString("prepared"), state.getString("exportName"),
                    state.getString("exportMime"), state.getLong("exportSize"));
                pickerOpen = true;
            } catch (IOException e) { Toast.makeText(this, "Prepared export expired; prepare it again", Toast.LENGTH_LONG).show(); }
        }
        analyze(displayed);
    }

    private void createViews() {
        LinearLayout root = vertical(); root.setBackgroundColor(0xff15191e); root.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left + dp(8), bars.top + dp(8), bars.right + dp(8), bars.bottom + dp(8)); return insets;
            });
        }
        TextView title = text("Native analysis • " + inputName, 18); title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END); root.addView(title);
        // Bound configuration space by weight so small windows retain usable
        // results. Analyze/cancel/paging and search stay outside this scroll area.
        ScrollView parameters = new ScrollView(this); parameters.setScrollbarFadingEnabled(false);
        parameters.setContentDescription("Tool parameters and actions; scroll for more");
        LinearLayout controls = vertical(); parameters.addView(controls);
        root.addView(parameters, new LinearLayout.LayoutParams(-1, 0, 1));
        tools = new Spinner(this); tools.setId(R.id.native_tool);
        tools.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ToolReference.TITLES)); controls.addView(tools);
        controls.addView(explanations.controls(this));
        helpToggle = button("Tool mechanism / interpretation", () -> {}); controls.addView(helpToggle);
        help = text("", 13); help.setTag("explanation:native.tool"); help.setTextIsSelectable(true); controls.addView(help);
        mode = new Spinner(this); mode.setId(R.id.native_mode); controls.addView(mode);
        LinearLayout inputs = new LinearLayout(this);
        offset = new EditText(this); offset.setId(R.id.native_offset); offset.setSingleLine(); offset.setHint("Physical offset: auto / 0x…");
        minimum = new EditText(this); minimum.setSingleLine(); minimum.setInputType(2); minimum.setHint("Minimum length");
        inputs.addView(offset, new LinearLayout.LayoutParams(0, -2, 2)); inputs.addView(minimum, new LinearLayout.LayoutParams(0, -2, 1)); controls.addView(inputs);
        selector = new EditText(this); selector.setSingleLine(); controls.addView(selector);
        LinearLayout switches = new LinearLayout(this);
        json = new CheckBox(this); json.setText("Native JSON"); att = new CheckBox(this); att.setText("AT&T syntax");
        switches.addView(json); switches.addView(att); controls.addView(switches);
        LinearLayout analysis = new LinearLayout(this);
        reload = button("Analyze / reload", this::analyze); reload.setId(R.id.native_reload); addButton(analysis, reload);
        cancel = button("Cancel", () -> { if (busy) { cancellation.set(true); status.setText("Cancellation requested • waiting for current native/provider operation"); } }); addButton(analysis, cancel);
        previous = button("Previous", () -> changePage(-1)); previous.setId(R.id.native_previous); addButton(analysis, previous);
        next = button("Next", () -> changePage(1)); next.setId(R.id.native_next); addButton(analysis, next); root.addView(analysis);
        LinearLayout navigation = new LinearLayout(this);
        back = button("Back", this::goBack); back.setId(R.id.native_back); addButton(navigation, back);
        addButton(navigation, button("Go to offset / VA", this::goToDialog));
        nextBlock = button("Next block", () -> {
            if (produced != null && nextOffset >= 0) navigate(new ToolRequest(6, produced.mode, nextOffset, produced.minimum, "", true, produced.attSyntax));
        }); nextBlock.setId(R.id.native_next_block); addButton(navigation, nextBlock); controls.addView(navigation);
        LinearLayout more = new LinearLayout(this);
        export = button("Export report", this::exportReport); addButton(more, export);
        addButton(more, button("Physical range…", this::rangeDialog)); controls.addView(more);
        filter = new EditText(this); filter.setId(R.id.native_filter); filter.setSingleLine(); filter.setHint("Search all record fields / offsets"); root.addView(filter);
        metadata = text("", 12); metadata.setTextIsSelectable(true); metadata.setOnClickListener(v -> showMetadata()); root.addView(metadata);
        status = text("Ready", 12); status.setId(R.id.native_status); root.addView(status);
        FrameLayout results = new FrameLayout(this);
        records = new ListView(this); records.setId(R.id.native_records); results.addView(records, new FrameLayout.LayoutParams(-1, -1));
        textScroll = new ScrollView(this); HorizontalScrollView horizontal = new HorizontalScrollView(this);
        output = text("", 12); output.setId(R.id.native_output); output.setTypeface(Typeface.MONOSPACE); output.setTextIsSelectable(true);
        horizontal.addView(output); textScroll.addView(horizontal); results.addView(textScroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(results, new LinearLayout.LayoutParams(-1, 0, 2)); setContentView(root);
        tools.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding && currentTool != position) navigate(new ToolRequest(position, position == 8 ? 14 : 0, -1,
                    ScanPreferences.load(NativeToolsActivity.this).minimumStringLength, "", false, false));
            }
        });
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { parametersChanged(); }
        });
        for (EditText input : new EditText[]{offset, minimum, selector}) input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int count, int after) {}
            public void afterTextChanged(Editable value) { parametersChanged(); }
            public void onTextChanged(CharSequence s, int a, int before, int count) {}
        });
        json.setOnCheckedChangeListener((view, checked) -> parametersChanged());
        att.setOnCheckedChangeListener((view, checked) -> parametersChanged());
        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int count, int after) {}
            public void afterTextChanged(Editable value) {}
            public void onTextChanged(CharSequence value, int a, int before, int count) {
                if (!binding) { page = 0; render(); }
            }
        });
        records.setOnItemClickListener((parent, view, position, id) -> {
            if (!busy && table != null && visiblePage != null) showRow(table, visiblePage.rows.get(position));
        });
    }

    private void bind(ToolHistory.Location location) {
        binding = true;
        ToolRequest request = location.controls; currentTool = request.tool; page = location.page;
        tools.setSelection(currentTool); help.setText(ToolReference.HELP[currentTool]);
        explanations.bind("native.tool."+currentTool,"Tool mechanism / interpretation",helpToggle,help,true,false);
        mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ToolReference.modes(currentTool)));
        int selection = request.mode;
        if (currentTool == 8) { int[] encodings = {14, 1, 2, 4, 8}; selection = 0; for (int i = 0; i < encodings.length; i++) if (encodings[i] == request.mode) selection = i; }
        mode.setSelection(selection);
        mode.setVisibility(ToolReference.modes(currentTool).length > 1 ? View.VISIBLE : View.GONE);
        offset.setText(request.offset < 0 ? "" : "0x" + Long.toHexString(request.offset)); minimum.setText(Integer.toString(request.minimum));
        offset.setVisibility(currentTool == 0 || currentTool == 6 || currentTool == 9 ? View.VISIBLE : View.GONE);
        offset.setHint(currentTool == 0 ? "Mach-O fat slice index (blank = host / first)" : "Physical offset (blank = entry point)");
        minimum.setVisibility(currentTool == 8 ? View.VISIBLE : View.GONE);
        selector.setText(request.selector); selector.setVisibility(currentTool == 0 || currentTool == 7 ? View.VISIBLE : View.GONE);
        selector.setHint(currentTool == 0 ? "NAME[#FIELD] (blank = all)" : "CRC32 / MD4 / MD5 / SHA1 / SHA224 / SHA256 / SHA384 / SHA512");
        json.setChecked(request.json); json.setVisibility(currentTool == 7 || currentTool == 8 ? View.VISIBLE : View.GONE);
        att.setChecked(request.attSyntax); att.setVisibility(currentTool == 6 ? View.VISIBLE : View.GONE);
        filter.setText(location.filter); back.setEnabled(history.hasBack()); binding = false;
    }

    private ToolRequest capture() {
        int selected = mode.getSelectedItemPosition();
        int nativeMode = currentTool == 8 ? new int[]{14, 1, 2, 4, 8}[Math.max(0, selected)] : Math.max(0, selected);
        return new ToolRequest(currentTool, nativeMode, currentTool == 0 || currentTool == 6 || currentTool == 9 ? parseOffset(offset.getText().toString(), true) : -1,
            Integer.parseInt(minimum.getText().toString()), selector.getText().toString().trim(), json.isShown() && json.isChecked(), currentTool == 6 && att.isChecked());
    }
    private ToolHistory.Location location() {
        ToolRequest controls = capture();
        return new ToolHistory.Location(produced == null ? controls : produced, controls, filter.getText().toString(), page);
    }
    private void parametersChanged() { if (!binding && !busy && produced != null) render(); }
    private static long parseOffset(String value, boolean auto) {
        String text = value.trim(); if (text.isEmpty() && auto) return -1;
        long number = text.startsWith("0x") || text.startsWith("0X") ? Long.parseLong(text.substring(2), 16) : Long.parseLong(text);
        if (number < 0) throw new IllegalArgumentException("Physical offsets/lengths must be nonnegative"); return number;
    }

    private int beginTask(String message) {
        cancellation.set(true); cancellation = new AtomicBoolean(); int ticket = ++generation;
        busy = true; reload.setEnabled(false); cancel.setEnabled(true); export.setEnabled(false); controlsEnabled(false);
        previous.setEnabled(false); next.setEnabled(false); nextBlock.setEnabled(false); status.setText(message); return ticket;
    }
    private void settled() {
        busy = false; reload.setEnabled(true); cancel.setEnabled(false); export.setEnabled(produced != null); controlsEnabled(true); back.setEnabled(history.hasBack());
    }
    private void controlsEnabled(boolean enabled) {
        for (View view : new View[]{tools, mode, offset, minimum, selector, json, att, records}) view.setEnabled(enabled);
        back.setEnabled(enabled && history.hasBack());
    }

    private void analyze() {
        final ToolRequest request;
        try { request = capture(); } catch (RuntimeException e) { status.setText("Invalid tool parameters: " + e.getMessage()); return; }
        analyze(request);
    }
    private void analyze(ToolRequest request) {
        final int ticket = beginTask("Reading " + ToolReference.TITLES[request.tool] + "…");
        final AtomicBoolean stop = cancellation;
        final long started = SystemClock.elapsedRealtime(), size = file.length();
        table = null; raw = ""; measuredHashInputSha = null; produced = null; metadata.setText(""); records.setVisibility(View.GONE); textScroll.setVisibility(View.VISIBLE); output.setText("Analyzing on worker…");
        worker.execute(() -> {
            NativeTable parsed = null; String result = "", revision = "", hashSource = null; long resultSize = size;
            try {
                if (stop.get() || ticket != generation) throw new InterruptedIOException("Analysis cancelled");
                if (request.tool == 7) {
                    try (BinaryWorkerClient client = new BinaryWorkerClient(this,"hashes");
                         HashDocument hashes = HashDocument.read(file,new File(getCacheDir(),"hash-snapshots"),inputName,client,stop::get)) {
                        result = hashes.legacyReport(request.selector,request.json);
                        hashSource = hashes.snapshot.sha256; resultSize = hashes.snapshot.size;
                    }
                } else if (request.tool == 0) parsed = NativeTable.parse(Engine.structures(file.getAbsolutePath(), request.selector, request.mode, (int)request.offset, stop::get));
                else if (request.records()) parsed = NativeTable.parse(Engine.records(file.getAbsolutePath(), request.tool, request.mode, request.offset, request.attSyntax, stop::get));
                else result = request.tool == 9 ? hex(file, Math.max(0, request.offset)) : Engine.tool(file.getAbsolutePath(), request.tool, request.offset,
                    request.mode, request.minimum, request.selector, request.json, request.attSyntax);
                Properties properties = new Properties();
                try (InputStream in = getAssets().open("engine-revisions.properties")) { properties.load(in); revision = properties.getProperty("cdie", ""); }
                catch (IOException ignored) { /* An unbundled inspector has no engine provenance. */ }
                if (request.tool == 7) revision = "xxfclib@" + HashDocument.XXFCLIB_SOURCE + ";RHash@" + HashDocument.RHASH_SOURCE;
            } catch (Exception | LinkageError e) { result = "ERROR: " + e; }
            final NativeTable snapshot = parsed; final String text = result, engine = revision, hashIdentity = hashSource; final long measuredSize = resultSize;
            runOnUiThread(() -> {
                if (isDestroyed() || ticket != generation) return;
                settled(); produced = request; elapsedMs = SystemClock.elapsedRealtime() - started; producedSize = snapshot != null && snapshot.ok ? snapshot.fileSize : measuredSize; measuredHashInputSha = hashIdentity;
                engineRevision = engine; table = stop.get() ? null : snapshot; raw = stop.get() ? "ERROR: Analysis cancelled" : text;
                lastOffset = Math.max(0, request.offset); nextOffset = -1;
                if (table != null && table.ok && request.tool == 6) try {
                    JSONObject meta = table.metadata(); lastOffset = meta.getLong("pageOffset"); if (!meta.isNull("nextOffset")) nextOffset = meta.getLong("nextOffset");
                } catch (JSONException e) { raw = "ERROR: Invalid native continuation: " + e.getMessage(); table = null; }
                if (request.tool == 9) nextOffset = Math.min(size, lastOffset + 4096);
                export.setEnabled(true); render();
            });
        });
    }

    private void render() {
        if (table != null && table.ok) {
            records.setVisibility(View.VISIBLE); textScroll.setVisibility(View.GONE);
            visiblePage = table.page(filter.getText().toString(), page, 50); page = visiblePage.number;
            records.setAdapter(new RecordAdapter(visiblePage.rows));
            String actualMode = "";
            try { actualMode = table.metadata().optString("mode"); } catch (JSONException ignored) {}
            metadata.setText(table.fileType + (actualMode.isEmpty() ? "" : " • " + actualMode) + " • " + table.scope + " • " + table.fileSize + " bytes • tap for native metadata");
            if (!busy) status.setText(visiblePage.totalMatches + " matches of " + table.rows.size() + " rows • page " + (page + 1) + "/" + visiblePage.pageCount
                + " • " + elapsedMs + " ms" + (historyExpired ? " • oldest history expired (64 locations retained)" : ""));
            previous.setText("Previous 50"); next.setText("Next 50");
            previous.setEnabled(!busy && page > 0); next.setEnabled(!busy && page + 1 < visiblePage.pageCount);
        } else {
            records.setVisibility(View.GONE); textScroll.setVisibility(View.VISIBLE);
            String content = table != null ? "ERROR: " + table.error : raw;
            String query = filter.getText().toString().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) output.setText(content);
            else { StringBuilder found = new StringBuilder(); for (String line : content.split("\n")) if (line.toLowerCase(Locale.ROOT).contains(query)) found.append(line).append('\n'); output.setText(found); }
            previous.setText("Previous 4 KiB"); next.setText("Next 4 KiB");
            previous.setEnabled(!busy && produced != null && produced.tool == 9 && lastOffset > 0);
            next.setEnabled(!busy && produced != null && produced.tool == 9 && nextOffset >= 0 && nextOffset < producedSize);
            if (!busy) status.setText(content.startsWith("ERROR:") ? content : "Result • " + content.length() + " characters • " + elapsedMs + " ms");
        }
        previous.setVisibility(table != null && table.ok || currentTool == 9 ? View.VISIBLE : View.GONE);
        next.setVisibility(table != null && table.ok || currentTool == 9 ? View.VISIBLE : View.GONE);
        nextBlock.setVisibility(currentTool == 6 ? View.VISIBLE : View.GONE);
        nextBlock.setEnabled(!busy && table != null && table.ok && nextOffset >= 0);
        if (!busy && produced != null) try {
            if (!capture().sameAs(produced)) status.append(" • parameters changed; Analyze / reload to apply (showing captured results)");
        } catch (RuntimeException e) { status.append(" • invalid edited parameters; showing captured results"); }
    }

    private final class RecordAdapter extends BaseAdapter {
        private final List<NativeTable.Row> rows;
        RecordAdapter(List<NativeTable.Row> rows) { this.rows = rows; }
        public int getCount() { return rows.size(); }
        public Object getItem(int position) { return rows.get(position); }
        public long getItemId(int position) { return rows.get(position).index; }
        public View getView(int position, View recycled, android.view.ViewGroup parent) {
            NativeTable.Row row = rows.get(position);
            String name = row.name.isEmpty() ? "(unnamed)" : row.name;
            if (name.length() > 160) name = name.substring(0, 160) + "… [tap for full name]";
            String heading = "#" + row.index + " " + (row.text("library").isEmpty() ? "" : row.text("library") + "!") + name;
            String numbers = "off=" + NativeTable.hexOffset(row.physicalOffset) + "  VA=" + (row.virtualAddress == null ? "unknown" : "0x" + row.virtualAddress.toString(16).toUpperCase(Locale.ROOT))
                + "  size=" + (row.sizeBytes == null ? "unknown" : row.sizeBytes);
            String extra = table.kind.equals("entropy") ? row.text("rowType") + (row.text("rowType").equals("byte-frequency") ? " value=" + row.text("byteValue") + " count=" + row.text("count") + " p=" + row.text("probability") : " H=" + row.text("entropyBitsPerByte") + " bits/byte • native display: " + row.text("upstreamDisplayLabel"))
                : table.kind.equals("structures") ? row.text("rowType") + (row.text("rowType").equals("field") ? " parent=#" + row.text("parentStructureIndex") + " " + row.text("nativeType") + " = " + row.text("displayValue") + "\n" + row.text("propertyNames") : " type=" + row.text("nativeStructureType") + " count=" + row.text("count"))
                : table.kind.equals("memory-map") ? "part=0x" + Integer.toHexString(row.code("filePartFlags")) + " virtual=" + row.isVirtual + " invisible=" + row.flag("isInvisible")
                : table.kind.equals("symbols") ? "kind=" + row.code("kind") + " binding=" + row.code("binding") + " record=" + NativeTable.hexOffset(row.recordOffset)
                : table.kind.equals("resources") ? "type=" + row.text("typeId") + " name=" + row.text("nameId") + " lang=" + row.text("languageId") + " cp=" + row.text("codePage")
                : table.kind.equals("disassembly") ? row.text("bytes") + " | " + row.text("addressDomain") + "\n" + row.text("comment")
                : "ordinal=" + row.text("ordinal") + (row.flag("isDelay") ? " delay-load" : "") + (row.text("forwarder").isEmpty() ? "" : " forwarder=" + row.text("forwarder"));
            WorkbenchUi.Lines display = new WorkbenchUi.Lines(NativeToolsActivity.this).heading(heading).code(numbers);
            if (table.kind.equals("disassembly")) display.code(extra); else display.line(extra);
            return WorkbenchUi.row(NativeToolsActivity.this, recycled, display.build());
        }
    }

    private void changePage(int delta) {
        if (busy) return;
        if (table != null && table.ok) { page = Math.max(0, page + delta); render(); records.setSelection(0); }
        else if (produced != null && produced.tool == 9) {
            offset.setText("0x" + Long.toHexString(delta < 0 ? Math.max(0, lastOffset - 4096) : nextOffset)); analyze();
        }
    }
    private void navigate(ToolRequest request) {
        if (pickerOpen) return;
        try { historyExpired |= history.push(location()); } catch (RuntimeException e) { status.setText("Invalid current parameters: " + e.getMessage()); return; }
        bind(new ToolHistory.Location(request, "", 0)); analyze();
    }
    private void goBack() {
        ToolHistory.Location destination = history.back(); if (destination == null) { finish(); return; }
        bind(destination); analyze(destination.request);
    }
    @Override public void onBackPressed() {
        if (busy) { cancellation.set(true); status.setText("Cancellation requested • wait for the operation to settle before leaving"); }
        else if (history.hasBack() && !pickerOpen) goBack(); else super.onBackPressed();
    }

    private void showMetadata() {
        if (table == null) return;
        try { showText("Native metadata", table.metadata().toString(2) + "\n\nEngine revision: " + engineRevision
            + "\nComplete exports retain all returned rows. Physical ranges are half-open. Native tables do not use signature databases."); }
        catch (JSONException e) { status.setText(e.toString()); }
    }
    private void showText(String title, String value) {
        ScrollView scroll = new ScrollView(this); TextView view = text(value, 13); view.setTextIsSelectable(true);
        view.setPadding(dp(12), dp(8), dp(12), dp(8)); scroll.addView(view);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }
    private void showRow(NativeTable source, NativeTable.Row row) {
        try {
            LinearLayout body = vertical(); body.setPadding(dp(12), dp(8), dp(12), dp(8));
            body.addView(explanations.controls(this));
            JSONObject fields = row.toJson(); Iterator<String> keys = fields.keys();
            while (keys.hasNext()) {
                String key = keys.next(); Object value = fields.get(key);
                TextView rawField = WorkbenchUi.fact(this, key, value == JSONObject.NULL ? "null (unavailable)" : value.toString(), true);
                body.addView(rawField);
                explanations.paragraph(this,body,"native.field."+source.kind+"."+key,"Explain "+key,ToolReference.field(key),true,false);
            }
            if (!row.hasFileByte(source.fileSize)) body.addView(text("This record has no validated physical target byte. Source-table records and virtual-address mapping may still provide useful evidence.", 13));
            ScrollView scroll = new ScrollView(this); scroll.addView(body);
            new AlertDialog.Builder(this).setTitle(source.kind + " • row " + row.index).setView(scroll)
                .setNegativeButton("Close", null).setPositiveButton("Actions", (dialog, which) -> rowActions(source, row)).show();
        } catch (JSONException e) { status.setText("Record unavailable: " + e.getMessage()); }
    }
    private void rowActions(NativeTable source, NativeTable.Row row) {
        ArrayList<String> labels = new ArrayList<>(); ArrayList<Runnable> actions = new ArrayList<>();
        if (source.kind.equals("structures")) try {
            JSONObject field = row.toJson();
            if (field.getString("rowType").equals("field")) {
                int parent = field.getInt("parentStructureIndex");
                labels.add("Inspect parent structure #" + parent); actions.add(() -> showRow(source, source.rows.get(parent)));
                NativeStructures.Reference reference = NativeStructures.reference(source, row);
                if (reference != null) {
                    if (reference.fileOffset && reference.value.bitLength() <= 63 && reference.value.longValue() < source.fileSize) {
                        labels.add("Hex at resolved field value (" + reference.basis + ")");
                        actions.add(() -> navigate(new ToolRequest(9, 0, reference.value.longValue(), 4, "", false, false)));
                    }
                    if (reference.virtualAddress) {
                        labels.add("Map resolved field VA (" + reference.basis + ")");
                        actions.add(() -> mapAddress("0x" + reference.value.toString(16)));
                    }
                }
            }
        } catch (JSONException e) { status.setText("Invalid structure relationship: " + e.getMessage()); return; }
        if (row.hasFileByte(source.fileSize)) {
            labels.add("Hex at physical offset"); actions.add(() -> navigate(new ToolRequest(9, 0, row.physicalOffset, 4, "", false, false)));
            labels.add("Disassemble bytes"); actions.add(() -> navigate(new ToolRequest(6, 0, row.physicalOffset, 4, "", true, false)));
        }
        FileRange range = row.contentRange(source.fileSize);
        if (range != null) {
            labels.add("Analyze range: entropy / SHA-256 / CRC32"); actions.add(() -> analyzeRange(range));
            labels.add("Export stored bytes"); actions.add(() -> prepareRange(range, source.kind + "-" + row.index + "-" + Long.toHexString(range.offset) + ".bin"));
        }
        if (row.recordOffset != null && row.recordOffset < source.fileSize) {
            labels.add("Hex at symbol-table record"); actions.add(() -> navigate(new ToolRequest(9, 0, row.recordOffset, 4, "", false, false)));
        }
        if (row.virtualAddress != null) {
            labels.add("Map this preferred VA"); actions.add(() -> mapAddress("0x" + row.virtualAddress.toString(16)));
        }
        if (labels.isEmpty()) { showText("No byte navigation", "The native record has no mapped target/source-table offset or usable range. Its metadata remains available and exportable."); return; }
        new AlertDialog.Builder(this).setTitle("Record actions").setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
            .setNegativeButton("Cancel", null).show();
    }

    private void goToDialog() {
        if (busy || pickerOpen) return;
        LinearLayout body = vertical(); Spinner domain = new Spinner(this);
        domain.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Physical file offset", "Preferred virtual address (0x…)"})); body.addView(domain);
        EditText value = new EditText(this); value.setSingleLine(); value.setText("0x" + Long.toHexString(lastOffset)); body.addView(value);
        new AlertDialog.Builder(this).setTitle("Navigate address").setMessage("Physical offsets locate this file. Preferred VAs require a native map; runtime ASLR addresses need separate load evidence. Overlaps remain explicit.")
            .setView(body).setNegativeButton("Cancel", null).setPositiveButton("Open hex", (dialog, which) -> {
                if (domain.getSelectedItemPosition() == 1) { mapAddress(value.getText().toString().trim()); return; }
                try { long at = parseOffset(value.getText().toString(), false); if (at >= file.length()) throw new IOException("Offset has no byte in this input"); navigate(new ToolRequest(9, 0, at, 4, "", false, false)); }
                catch (Exception e) { status.setText(e.getMessage()); }
            }).show();
    }
    private void mapAddress(String address) {
        if (busy) return;
        final int ticket = beginTask("Resolving preferred VA through native memory map…"); final AtomicBoolean stop = cancellation;
        final boolean selectedMap = table != null && table.ok && table.kind.equals("memory-map");
        final NativeTable cached = selectedMap ? table : mapCache;
        worker.execute(() -> {
            NativeTable map = cached; List<NativeTable.Mapping> matches = null; String error = "";
            try {
                if (map == null) map = NativeTable.parse(Engine.records(file.getAbsolutePath(), 1, 0, -1, false, stop::get));
                if (!map.ok) throw new IOException(map.error); matches = map.mapVirtualAddress(address);
            } catch (Exception | LinkageError e) { error = e.getMessage(); }
            final NativeTable mapping = map; final List<NativeTable.Mapping> found = matches; final String failure = error;
            runOnUiThread(() -> {
                if (isDestroyed() || ticket != generation) return; settled(); render();
                if (stop.get()) { status.setText("Address lookup cancelled"); return; }
                if (found == null) { status.setText("Address lookup failed: " + failure); return; }
                if (!selectedMap) mapCache = mapping;
                if (found.isEmpty()) { showText("No mapping", "No native map record covers " + address + ". It may be unmapped, a runtime address or an unsupported interpretation."); return; }
                if (found.size() == 1 && found.get(0).physicalOffset != null) { navigate(new ToolRequest(9, 0, found.get(0).physicalOffset, 4, "", false, false)); return; }
                String[] choices = new String[found.size()];
                for (int i = 0; i < found.size(); i++) choices[i] = "#" + found.get(i).record.index + " " + found.get(i).record.name + " • "
                    + (found.get(i).physicalOffset == null ? found.get(i).limitation : NativeTable.hexOffset(found.get(i).physicalOffset));
                new AlertDialog.Builder(this).setTitle(found.size() + " covering map records")
                    .setItems(choices, (dialog, which) -> {
                        NativeTable.Mapping selected = found.get(which);
                        if (selected.physicalOffset == null) showRow(mapping, selected.record);
                        else navigate(new ToolRequest(9, 0, selected.physicalOffset, 4, "", false, false));
                    }).setNegativeButton("Cancel", null).show();
            });
        });
    }

    private void rangeDialog() {
        if (busy || pickerOpen) return;
        LinearLayout fields = vertical(); EditText start = new EditText(this), length = new EditText(this);
        start.setSingleLine(); length.setSingleLine(); start.setHint("Physical offset"); length.setHint("Byte count");
        start.setText("0x" + Long.toHexString(lastOffset)); length.setText(Long.toString(Math.min(4096, Math.max(0, file.length() - lastOffset))));
        fields.addView(start); fields.addView(length);
        Runnable inspect = () -> { try { analyzeRange(new FileRange(file.length(), parseOffset(start.getText().toString(), false), parseOffset(length.getText().toString(), false))); } catch (Exception e) { status.setText(e.getMessage()); } };
        Runnable save = () -> { try { FileRange range = new FileRange(file.length(), parseOffset(start.getText().toString(), false), parseOffset(length.getText().toString(), false)); prepareRange(range, "range-" + Long.toHexString(range.offset) + ".bin"); } catch (Exception e) { status.setText(e.getMessage()); } };
        new AlertDialog.Builder(this).setTitle("Physical range").setMessage("Stored bytes only. Up to 256 MiB per analysis/export. No virtual tail synthesis or implicit clipping.")
            .setView(fields).setNegativeButton("Cancel", null).setNeutralButton("Analyze", (d, w) -> inspect.run()).setPositiveButton("Export bytes", (d, w) -> save.run()).show();
    }
    private void analyzeRange(FileRange range) {
        if (busy) return; final int ticket = beginTask("Streaming selected range…"); final AtomicBoolean stop = cancellation;
        worker.execute(() -> {
            RangeAnalysis.Result result = null; String error = "";
            try { result = RangeAnalysis.inspect(file, range, stop::get); } catch (Exception e) { error = e.getMessage(); }
            final RangeAnalysis.Result analysis = result; final String failure = error;
            runOnUiThread(() -> {
                if (isDestroyed() || ticket != generation) return; settled(); render();
                if (analysis == null) { status.setText("Range analysis failed: " + failure); return; }
                String details = "Physical offset: " + NativeTable.hexOffset(range.offset) + "\nLength: " + range.length + " bytes\n"
                    + String.format(Locale.ROOT, "Shannon entropy: %.6f bits/byte\n", analysis.entropy)
                    + "Range SHA-256: " + analysis.sha256 + "\nRange CRC32: " + analysis.crc32
                    + "\n\nThese digests identify only this physical range. Compression, encryption and ordinary media can all have high entropy; no behavior or safety verdict follows.";
                TextView body = text(details, 13); body.setTextIsSelectable(true); ScrollView scroll = new ScrollView(this); scroll.addView(body);
                new AlertDialog.Builder(this).setTitle("Physical range evidence").setView(scroll).setNegativeButton("Close", null)
                    .setPositiveButton("Export JSON", (dialog, which) -> {
                        try { prepareText("die-range-report.json", ToolReport.range(inputName, analysis.range.fileSize == initialSize ? inputSha256 : null, analysis)); } catch (JSONException e) { status.setText(e.getMessage()); }
                    }).show();
            });
        });
    }

    private interface ExportPreparation { PreparedExport run(AtomicBoolean stop) throws Exception; }
    private void prepareExport(ExportPreparation preparation) {
        if (busy || pickerOpen) return; final int ticket = beginTask("Preparing immutable private export…"); final AtomicBoolean stop = cancellation;
        worker.execute(() -> {
            PreparedExport result = null; String error = "";
            try { result = preparation.run(stop); if (stop.get()) throw new InterruptedIOException("Export preparation cancelled"); }
            catch (Exception e) { if (result != null) result.discard(); result = null; error = e.getMessage(); }
            final PreparedExport snapshot = result; final String failure = error;
            runOnUiThread(() -> {
                if (isDestroyed() || ticket != generation) { if (snapshot != null) snapshot.discard(); return; }
                settled(); render();
                if (snapshot == null) { status.setText("Export preparation failed: " + failure); return; }
                if (prepared != null) prepared.discard(); prepared = snapshot; pickerOpen = true;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(snapshot.mime); intent.putExtra(Intent.EXTRA_TITLE, snapshot.name); startActivityForResult(intent, 1);
            });
        });
    }
    private void prepareText(String name, String value) { prepareExport(stop -> PreparedExport.text(exportDirectory, name, value)); }
    private void prepareRange(FileRange range, String name) { prepareExport(stop -> PreparedExport.range(exportDirectory, name, file, range, stop::get)); }
    private void exportReport() {
        if (busy || produced == null) return;
        final ToolRequest request = produced; final NativeTable snapshot = table; final String text = raw, revision = engineRevision;
        final String query = filter.getText().toString(), digest = request.tool == 7 ? measuredHashInputSha : producedSize == initialSize ? inputSha256 : null; final int currentPage = page;
        final long duration = elapsedMs, size = producedSize;
        prepareExport(stop -> PreparedExport.text(exportDirectory, "die-" + ToolReference.TITLES[request.tool].toLowerCase(Locale.ROOT).replace(' ', '-') + ".json",
            ToolReport.encode(inputName, digest, size, revision, request, snapshot, text, duration, query, currentPage)));
    }

    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (request != 1) return; pickerOpen = false;
        final PreparedExport snapshot = prepared; prepared = null;
        if (snapshot == null) { status.setText("Prepared export unavailable; prepare it again"); return; }
        if (result != RESULT_OK || intent == null || intent.getData() == null) { snapshot.discard(); status.setText("Export cancelled"); return; }
        Uri destination = intent.getData(); final int ticket = beginTask("Writing prepared export…"); final AtomicBoolean stop = cancellation;
        worker.execute(() -> {
            String message;
            try (OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new IOException("Destination unavailable"); snapshot.copyTo(out, stop::get); message = "Export saved • " + snapshot.size + " bytes";
            } catch (Exception e) { message = "Export failed; destination may contain partial data: " + e.getMessage(); }
            finally { snapshot.discard(); }
            final String response = message;
            runOnUiThread(() -> { if (!isDestroyed() && ticket == generation) { settled(); render(); status.setText(response); } });
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        try {
            ToolRequest request = capture(); state.putInt("tool", request.tool); state.putInt("mode", request.mode); state.putLong("offset", request.offset);
            state.putInt("minimum", request.minimum); state.putString("selector", request.selector); state.putBoolean("json", request.json); state.putBoolean("att", request.attSyntax);
            state.putString("filter", filter.getText().toString()); state.putInt("page", page);
        } catch (RuntimeException ignored) { state.putInt("tool", currentTool); }
        if (produced != null) try { state.putString("displayed", produced.toJson().toString()); } catch (JSONException ignored) {}
        if (prepared != null && pickerOpen) {
            state.putString("prepared", prepared.file.getAbsolutePath()); state.putString("exportName", prepared.name);
            state.putString("exportMime", prepared.mime); state.putLong("exportSize", prepared.size);
        }
    }
    static String hex(File file, long position) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            if (position < 0 || position > input.length()) throw new IOException("Offset outside input");
            input.seek(position); StringBuilder result = new StringBuilder(); byte[] row = new byte[16];
            for (int i = 0; i < 256; i++) {
                int count = input.read(row); if (count < 0) break;
                result.append(String.format(Locale.ROOT, "%016X  ", position + i * 16L));
                for (int j = 0; j < 16; j++) result.append(j < count ? String.format(Locale.ROOT, "%02X ", row[j] & 255) : "   ");
                result.append(" |"); for (int j = 0; j < count; j++) { int value = row[j] & 255; result.append(value >= 32 && value < 127 ? (char)value : '.'); }
                result.append("|\n");
            }
            return result.toString();
        }
    }
    @Override protected void onDestroy() {
        generation++; cancellation.set(true);
        if (file != null && isFinishing() && getIntent().getBooleanExtra("ownedSnapshot",false)) worker.execute(() -> {
            try {
                File owned = file.getCanonicalFile(), directory = new File(getCacheDir(),"shared-analysis-inputs").getCanonicalFile();
                if (directory.equals(owned.getParentFile()) && owned.getName().startsWith("analysis-input-")) owned.delete();
            } catch (IOException ignored) { /* Expired private cache ownership; never delete an arbitrary caller path. */ }
        });
        worker.shutdown();
        if (!pickerOpen && prepared != null) prepared.discard(); super.onDestroy();
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.*;
import android.content.*;
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

/** Content-first view over one owned input, complete native evidence and exact byte grids. */
public final class GallerizeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ContentGalleryAdapter galleryAdapter;
    private GallerizeSession session;
    private ContentMap map;
    private String retained, name, mode = "gallery", group = "Files";
    private int page, generation;
    private boolean busy, pickerOpen;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private TextView title, summary, status, cellInfo;
    private LinearLayout content, gridControls, pager;
    private Button cancel, previous, next, apply;
    private EditText width, height;
    private Spinner groups;
    private PreparedExport prepared;
    private ByteGrid.Result grid;
    private File exportDirectory, previewDirectory;
    private final List<ContentMap.Card> selected = new ArrayList<>();
    private android.content.SharedPreferences preferences;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xffe5eaf0);
        return view;
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(12);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void add(LinearLayout row, View view) {
        row.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        name = getIntent().getStringExtra("name");
        if (name == null) name = new File(path).getName();
        exportDirectory = new File(getCacheDir(), "tool-exports");
        previewDirectory = new File(getCacheDir(), "content-previews");
        preferences = getSharedPreferences("gallerize-grid", MODE_PRIVATE);
        if (saved != null) {
            retained = saved.getString("session");
            mode = saved.getString("mode", "gallery");
            group = saved.getString("group", "Files");
            page = Math.max(0, saved.getInt("page"));
            if (saved.getString("prepared") != null)
                try {
                    prepared =
                            PreparedExport.restore(
                                    exportDirectory,
                                    saved.getString("prepared"),
                                    saved.getString("exportName"),
                                    saved.getString("exportMime"),
                                    saved.getLong("exportSize"));
                    pickerOpen = true;
                } catch (IOException ignored) {
                    /* Explicitly reported after views exist. */
                }
        }
        createViews();
        if (saved != null) {
            width.setText(saved.getString("width", "640"));
            height.setText(saved.getString("height", "640"));
            if (saved.getString("prepared") != null && prepared == null)
                status.setText("Prepared export expired; prepare it again");
        }
        load(new File(path), saved == null);
    }

    private void createViews() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackgroundColor(0xff15191e);
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
        title = text("Gallerize · " + name, 18);
        title.setMaxLines(2);
        root.addView(title);
        summary = text("Acquiring exact bytes…", 13);
        root.addView(summary);
        LinearLayout tools = new LinearLayout(this);
        add(tools, button("Views", this::chooseView));
        add(tools, button("Tools / evidence", this::tools));
        add(tools, button("Export files (ZIP)", this::exportFiles));
        cancel =
                button(
                        "Cancel",
                        () -> {
                            cancellation.set(true);
                            status.setText(
                                    "Cancellation requested · waiting for current IO/engine"
                                            + " operation");
                        });
        add(tools, cancel);
        root.addView(tools);
        ExpandableHelp help = new ExpandableHelp(this);
        help.paragraph(
                this,
                root,
                "gallerize.scope",
                "Evidence and presentation limits",
                "Files combine PhotoRec and Binwalk recoveries. Cards retain every accepted header,"
                    + " reported bounded artifact, nested/overlapping range and unassigned gap."
                    + " Header acceptance is not complete-file validation. Raw findings include"
                    + " every rejection and the full catalog. Text candidates are strict printable"
                    + " UTF-8 runs outside bounded artifacts; other encodings and text within"
                    + " artifacts remain in Classified strings. Thumbnail/preview decoding is a"
                    + " separate platform observation. Grid resolution partitions all bytes without"
                    + " sampling; entropy from one byte is always zero. Tools and view modes remain"
                    + " manually selectable.",
                true,
                false);
        gridControls = new LinearLayout(this);
        gridControls.setOrientation(LinearLayout.VERTICAL);
        LinearLayout dimensions = new LinearLayout(this);
        width = new EditText(this);
        height = new EditText(this);
        for (EditText value : new EditText[] {width, height}) {
            value.setSingleLine();
            value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            value.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});
        }
        width.setHint("Width");
        height.setHint("Height");
        width.setText(Integer.toString(preferences.getInt("width", 640)));
        height.setText(Integer.toString(preferences.getInt("height", 640)));
        add(dimensions, width);
        add(dimensions, height);
        apply = button("Apply", () -> showGrid(false));
        add(dimensions, apply);
        add(dimensions, button("Sizes / Auto", this::gridSizes));
        gridControls.addView(dimensions);
        cellInfo = text("", 12);
        cellInfo.setTextIsSelectable(true);
        gridControls.addView(cellInfo);
        root.addView(gridControls);
        pager = new LinearLayout(this);
        groups = new Spinner(this);
        groups.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[] {
                            "Files",
                            "All",
                            "Images",
                            "Audio / video",
                            "Text",
                            "Archives",
                            "Documents",
                            "Unassigned",
                            "Header only"
                        }));
        add(pager, groups);
        previous =
                button(
                        "Previous",
                        () -> {
                            if (!busy) {
                                page--;
                                showGallery();
                            }
                        });
        add(pager, previous);
        next =
                button(
                        "Next",
                        () -> {
                            if (!busy) {
                                page++;
                                showGallery();
                            }
                        });
        add(pager, next);
        root.addView(pager);
        groups.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        String value = parent.getItemAtPosition(position).toString();
                        if (!value.equals(group)) {
                            group = value;
                            page = 0;
                            if (!busy && mode.equals("gallery")) showGallery();
                        }
                    }

                    public void onNothingSelected(AdapterView<?> parent) {}
                });
        for (int i = 0; i < groups.getCount(); i++)
            if (groups.getItemAtPosition(i).equals(group)) groups.setSelection(i);
        status = text("Loading…", 12);
        root.addView(status);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void clearContent() {
        content.removeAllViews();
        if (galleryAdapter != null) {
            galleryAdapter.close();
            galleryAdapter = null;
        }
    }

    private int begin(String message) {
        cancellation.set(true);
        cancellation = new AtomicBoolean();
        busy = true;
        cancel.setEnabled(true);
        previous.setEnabled(false);
        next.setEnabled(false);
        apply.setEnabled(false);
        groups.setEnabled(false);
        status.setText(message);
        return ++generation;
    }

    private void end() {
        busy = false;
        cancel.setEnabled(false);
        apply.setEnabled(true);
        groups.setEnabled(true);
        if (mode.equals("gallery")) {
            previous.setEnabled(page > 0);
            next.setEnabled((long) (page + 1) * 24 < selected.size());
        }
    }

    private void load(File input, boolean autoView) {
        int ticket = begin("Acquiring input and scanning the supported whole blob…");
        AtomicBoolean stop = cancellation;
        boolean incomingHandoff =
                retained == null && getIntent().getBooleanExtra("ownedInputHandoff", false);
        worker.execute(
                () -> {
                    String failure = "";
                    ContentMap digest = null;
                    try {
                        if (incomingHandoff)
                            InputSnapshot.restore(
                                    input,
                                    new File(getCacheDir(), "gallery-handoffs"),
                                    getIntent().getLongExtra("sourceSize", -1),
                                    getIntent().getStringExtra("sha256"),
                                    stop::get);
                        session =
                                GallerizeSession.open(
                                        input,
                                        new File(getCacheDir(), "gallerize-inputs"),
                                        name,
                                        getIntent().getStringExtra("sha256"),
                                        getIntent().getLongExtra("sourceSize", -1),
                                        retained,
                                        new BinaryWorkerClient(this, "photorec"),
                                        new BinaryWorkerClient(this, "binwalk"),
                                        stop::get);
                        digest = session.digest(stop::get);
                    } catch (Exception error) {
                        failure = error.toString();
                    } finally {
                        if (incomingHandoff)
                            OwnedContent.discardHandoff(
                                    input, new File(getCacheDir(), "gallery-handoffs"));
                    }
                    final ContentMap loaded = digest;
                    final String error = failure;
                    String state = null;
                    try {
                        if (session != null) state = session.retentionState();
                    } catch (Exception ignored) {
                    }
                    final String descriptor = state;
                    runOnUiThread(
                            () -> {
                                if (isDestroyed() || ticket != generation) return;
                                end();
                                retained = descriptor;
                                map = loaded;
                                if (session == null) {
                                    status.setText("Gallerize acquisition failed: " + error);
                                    return;
                                }
                                summary.setText(
                                        map == null
                                                ? "Content scan incomplete · bytes and manual views"
                                                        + " remain available"
                                                : map.primaryType + "\n" + map.composition);
                                if (autoView
                                        && map != null
                                        && map.boundedCount == 0
                                        && map.textCount == 0)
                                    mode =
                                            session.input.size > ByteGrid.MAX_CELLS
                                                    ? "entropy"
                                                    : "bytes";
                                if (mode.equals("gallery") && map != null) showGallery();
                                else {
                                    if (mode.equals("gallery")) mode = "bytes";
                                    showGrid(!preferences.getBoolean("manual", false));
                                }
                                if (!error.isEmpty())
                                    status.setText(
                                            "Content scan failed: "
                                                    + error
                                                    + " · grid/source views remain usable");
                            });
                });
    }

    private boolean matches(ContentMap.Card card) {
        switch (group) {
            case "Files":
                return RecoveredFiles.fileCard(card)
                        || card.kind == ContentMap.Kind.INPUT
                                && map.boundedCount == 0
                                && map.textCount == 0;
            case "Images":
                return card.kind == ContentMap.Kind.IMAGE;
            case "Audio / video":
                return card.kind == ContentMap.Kind.AUDIO || card.kind == ContentMap.Kind.VIDEO;
            case "Text":
                return card.kind == ContentMap.Kind.TEXT;
            case "Archives":
                return card.kind == ContentMap.Kind.ARCHIVE;
            case "Documents":
                return card.kind == ContentMap.Kind.DOCUMENT;
            case "Unassigned":
                return card.kind == ContentMap.Kind.UNASSIGNED;
            case "Header only":
                return card.length == null;
            default:
                return true;
        }
    }

    private void showGallery() {
        if (busy || map == null) return;
        mode = "gallery";
        gridControls.setVisibility(View.GONE);
        pager.setVisibility(View.VISIBLE);
        clearContent();
        grid = null;
        selected.clear();
        for (ContentMap.Card card : map.cards) if (matches(card)) selected.add(card);
        int pages = Math.max(1, (selected.size() + 23) / 24);
        page = Math.max(0, Math.min(page, pages - 1));
        List<ContentMap.Card> shown =
                new ArrayList<>(
                        selected.subList(page * 24, Math.min(selected.size(), (page + 1) * 24)));
        GridView gallery = new GridView(this);
        gallery.setTag("gallerize-gallery");
        gallery.setNumColumns(GridView.AUTO_FIT);
        gallery.setColumnWidth(dp(156));
        gallery.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gallery.setHorizontalSpacing(dp(6));
        gallery.setVerticalSpacing(dp(6));
        galleryAdapter =
                new ContentGalleryAdapter(this, shown, session.input, worker, this::exportRange);
        gallery.setAdapter(galleryAdapter);
        gallery.setOnItemClickListener((parent, view, index, id) -> openFile(shown.get(index)));
        gallery.setOnItemLongClickListener(
                (parent, view, index, id) -> {
                    details(shown.get(index));
                    return true;
                });
        content.addView(gallery, new LinearLayout.LayoutParams(-1, -1));
        previous.setEnabled(page > 0);
        next.setEnabled(page + 1 < pages);
        status.setText(
                "Page "
                        + (page + 1)
                        + "/"
                        + pages
                        + " · "
                        + selected.size()
                        + " cards · "
                        + map.rawCount
                        + " raw findings\n"
                        + session.statusSummary());
    }

    private void chooseView() {
        if (busy || session == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Gallerize views")
                .setItems(
                        new String[] {
                            "Content gallery",
                            "Mean-byte grid",
                            "Local-entropy grid",
                            "Preview entire selected input"
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                if (map == null)
                                    status.setText(
                                            "Content scan did not complete; no partial gallery"
                                                    + " published");
                                else showGallery();
                            } else if (which == 3)
                                preview(
                                        new ContentMap.Card(
                                                ContentMap.Kind.INPUT,
                                                name,
                                                "selected input",
                                                0,
                                                session.input.size,
                                                "",
                                                -1));
                            else {
                                mode = which == 1 ? "bytes" : "entropy";
                                showGrid(!preferences.getBoolean("manual", false));
                            }
                        })
                .show();
    }

    private void gridSizes() {
        if (busy) return;
        new AlertDialog.Builder(this)
                .setTitle("Grid dimensions · width × height")
                .setItems(
                        new String[] {
                            "Automatic",
                            "64 × 64",
                            "128 × 128",
                            "256 × 256",
                            "320 × 240",
                            "512 × 512",
                            "640 × 640",
                            "640 × 480",
                            "1024 × 1024"
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                preferences.edit().putBoolean("manual", false).apply();
                                showGrid(true);
                                return;
                            }
                            int[][] sizes = {
                                {64, 64},
                                {128, 128},
                                {256, 256},
                                {320, 240},
                                {512, 512},
                                {640, 640},
                                {640, 480},
                                {1024, 1024}
                            };
                            width.setText(Integer.toString(sizes[which - 1][0]));
                            height.setText(Integer.toString(sizes[which - 1][1]));
                            showGrid(false);
                        })
                .show();
    }

    private void showGrid(boolean automatic) {
        if (busy || session == null) return;
        ByteGrid.Mode gridMode =
                mode.equals("entropy") ? ByteGrid.Mode.ENTROPY : ByteGrid.Mode.BYTES;
        ByteGrid.Layout layout;
        try {
            layout =
                    automatic
                            ? ByteGrid.automatic(session.input.size, gridMode)
                            : new ByteGrid.Layout(
                                    session.input.size,
                                    Integer.parseInt(width.getText().toString()),
                                    Integer.parseInt(height.getText().toString()));
        } catch (RuntimeException error) {
            status.setText(error.toString());
            return;
        }
        width.setText(Integer.toString(layout.width));
        height.setText(Integer.toString(layout.height));
        preferences
                .edit()
                .putBoolean("manual", !automatic)
                .putInt("width", layout.width)
                .putInt("height", layout.height)
                .apply();
        pager.setVisibility(View.GONE);
        gridControls.setVisibility(View.VISIBLE);
        clearContent();
        grid = null;
        int ticket = begin("Measuring every source byte for the grid…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        ByteGrid.Result result =
                                ByteGrid.measure(session.input, layout, gridMode, stop::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    end();
                                    grid = result;
                                    ByteGridView view = new ByteGridView(this, grid, this::cell);
                                    view.setTag("gallerize-grid");
                                    content.addView(view, new LinearLayout.LayoutParams(-1, -1));
                                    cellInfo.setText(
                                            gridMode.legend
                                                    + "\n"
                                                    + "Magenta unused cells have no bytes."
                                                    + " Row-major exact partitions; tap for"
                                                    + " range/hex.");
                                    status.setText(
                                            layout.width
                                                    + " × "
                                                    + layout.height
                                                    + " · "
                                                    + layout.used
                                                    + " populated cells · all "
                                                    + session.input.size
                                                    + " bytes accounted\nSHA-256 "
                                                    + result.sha256);
                                });
                    } catch (Exception failure) {
                        failed(ticket, failure);
                    }
                });
    }

    private void cell(int index) {
        if (grid == null || busy) return;
        FileRange range = grid.layout.range(index);
        cellInfo.setText(
                "Cell ("
                        + index % grid.layout.width
                        + ", "
                        + index / grid.layout.width
                        + ") · "
                        + (range.length == 0
                                ? "unused; no range"
                                : "[0x"
                                        + Long.toHexString(range.offset)
                                        + ", 0x"
                                        + Long.toHexString(range.offset + range.length)
                                        + ") · "
                                        + range.length
                                        + " bytes · "
                                        + grid.values[index]));
        if (range.length == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Exact grid cell")
                .setMessage(cellInfo.getText())
                .setPositiveButton(
                        "Hex at original offset", (dialog, which) -> nativeHex(range.offset))
                .setNeutralButton(
                        "Preview exact bytes",
                        (dialog, which) ->
                                preview(
                                        new ContentMap.Card(
                                                ContentMap.Kind.OTHER,
                                                "Grid cell " + index,
                                                "bytes",
                                                range.offset,
                                                range.length,
                                                "",
                                                -1,
                                                true)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void nativeHex(long offset) {
        startActivity(
                new Intent(this, NativeToolsActivity.class)
                        .putExtra("path", session.input.file.getAbsolutePath())
                        .putExtra("name", name)
                        .putExtra("sha256", session.input.sha256)
                        .putExtra("sourceSize", session.input.size)
                        .putExtra("tool", 9)
                        .putExtra("offset", offset));
    }

    private void openFile(ContentMap.Card card) {
        if (busy) return;
        if (card.kind == ContentMap.Kind.ARCHIVE) openArchive(card);
        else if (card.length != null) preview(card);
        else actions(card);
    }

    private void details(ContentMap.Card card) {
        if (busy) return;
        try {
            JSONObject details = card.json();
            if (galleryAdapter != null) {
                JSONObject observation = galleryAdapter.platformObservation(card);
                if (observation != null) details.put("transientPlatformPreview", observation);
            }
            TextView value = text(details.toString(2), 13);
            value.setTextIsSelectable(true);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(value);
            new AlertDialog.Builder(this)
                    .setTitle(card.label)
                    .setView(scroll)
                    .setNegativeButton("Close", null)
                    .setPositiveButton(
                            card.length == null ? "Inspect / actions" : "Preview / actions",
                            (dialog, which) -> actions(card))
                    .show();
        } catch (Exception error) {
            status.setText(error.toString());
        }
    }

    private void actions(ContentMap.Card card) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (card.length != null) {
            labels.add("View in app");
            actions.add(() -> preview(card));
            labels.add("Export exact stored bytes");
            actions.add(() -> exportRange(card));
        }
        if (card.offset < session.input.size
                && (card.nativeRow >= 0 || card.length != null && card.length > 0)) {
            labels.add("Inspect archive / compressed stream here (parsing interval)");
            actions.add(() -> openArchive(card));
        }
        labels.add("Choose inspection byte interval… (does not set a recovered file boundary)");
        actions.add(() -> chooseRange(card));
        labels.add("Hex at original offset");
        actions.add(() -> nativeHex(card.offset));
        labels.add("Inspect native witness");
        actions.add(() -> showWitness(card));
        if (card.length != null) {
            labels.add("Inspect range with all tools");
            actions.add(() -> preview(card));
        }
        new AlertDialog.Builder(this)
                .setTitle(card.label)
                .setItems(
                        labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void openArchive(ContentMap.Card card) {
        if (busy || session == null) return;
        int ticket = begin("Checking the card and its source for archive inspection…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        ArchiveLaunch launch = session.archiveLaunch(card, stop::get);
                        EvidenceRows.check(stop::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    end();
                                    try {
                                        startActivityForResult(
                                                new Intent(
                                                                this,
                                                                NativeArchiveExplorerActivity.class)
                                                        .putExtra(
                                                                "path",
                                                                session.input.file
                                                                        .getAbsolutePath())
                                                        .putExtra("name", name)
                                                        .putExtra("sha256", launch.sha256)
                                                        .putExtra("sourceSize", launch.size)
                                                        .putExtra("scopeOffset", launch.offset)
                                                        .putExtra("scopeLength", launch.length)
                                                        .putExtra("archiveLaunch", launch.raw)
                                                        .putExtra("originPreviewResult", true),
                                                2);
                                        status.setText(
                                                "Archive inspection bytes ["
                                                        + launch.offset
                                                        + ", "
                                                        + (launch.offset + launch.length)
                                                        + ") · original card extent unchanged");
                                    } catch (RuntimeException error) {
                                        status.setText("Archive view could not open: " + error);
                                    }
                                });
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void chooseRange(ContentMap.Card associated) {
        if (busy || session == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(4), dp(12), dp(4));
        form.addView(
                text(
                        "Choose stored inspection bytes, not a recovered-file boundary. Source: "
                                + session.input.size
                                + " bytes; preview maximum 256 MiB. Decimal or 0x hexadecimal.",
                        13));
        EditText offset = new EditText(this), length = new EditText(this);
        for (EditText value : new EditText[] {offset, length}) {
            value.setSingleLine();
            value.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            value.setFilters(new InputFilter[] {new InputFilter.LengthFilter(32)});
        }
        offset.setTag("gallery-range-offset");
        length.setTag("gallery-range-length");
        offset.setHint("Source byte offset");
        length.setHint("Selected byte length");
        offset.setText("0x" + Long.toHexString(associated.offset));
        long available = Math.max(0, session.input.size - associated.offset);
        length.setText(
                Long.toString(
                        Math.min(
                                FileRange.MAX_OPERATION_BYTES,
                                associated.length == null ? available : associated.length)));
        form.addView(offset);
        form.addView(length);
        new AlertDialog.Builder(this)
                .setTitle("Explicit inspection interval")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Preview selected bytes",
                        (dialog, which) -> {
                            try {
                                preview(
                                        ContentMap.Card.inspection(
                                                associated,
                                                session.input.size,
                                                byteNumber(offset.getText().toString()),
                                                byteNumber(length.getText().toString())));
                            } catch (Exception error) {
                                status.setText(
                                        "Inspection interval refused: " + error.getMessage());
                            }
                        })
                .show();
    }

    private static long byteNumber(String value) {
        String text = value.trim();
        return text.startsWith("0x") || text.startsWith("0X")
                ? Long.parseLong(text.substring(2), 16)
                : Long.parseLong(text, 10);
    }

    private void showWitness(ContentMap.Card card) {
        if (card.nativeRow < 0) {
            status.setText("This source/text/gap card has no native format witness");
            return;
        }
        int ticket = begin("Loading exact native witness…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        String raw = session.findings(card, stop::get).toString(2);
                        runOnUiThread(
                                () -> {
                                    if (!isDestroyed() && ticket == generation) {
                                        end();
                                        TextView value = text(raw, 13);
                                        value.setTextIsSelectable(true);
                                        ScrollView scroll = new ScrollView(this);
                                        scroll.addView(value);
                                        new AlertDialog.Builder(this)
                                                .setTitle(
                                                        "Original native findings · "
                                                                + card.findings.size())
                                                .setView(scroll)
                                                .setPositiveButton("Close", null)
                                                .show();
                                    }
                                });
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void preview(ContentMap.Card card) {
        if (busy || card.length == null) return;
        int ticket = begin("Owning exact selected bytes for preview…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    InputSnapshot input = null;
                    ContentOrigin origin = null;
                    try {
                        input =
                                GallerizeSession.stageRange(
                                        session.input,
                                        card.range(session.input.size),
                                        previewDirectory,
                                        stop::get);
                        origin =
                                ContentOrigin.create(
                                        new File(getCacheDir(), "content-origins"),
                                        input,
                                        ContentOrigin.stored(
                                                session.input, card.range(session.input.size)),
                                        records -> {
                                            records.value("galleryCard", card.json());
                                            records.value(
                                                    "galleryProvenance", session.provenance());
                                            if (card.nativeRow >= 0) {
                                                records.value(
                                                        "selectedFinding",
                                                        new JSONObject()
                                                                .put("engine", card.nativeEngine)
                                                                .put("index", card.nativeRow)
                                                                .put(
                                                                        "originalRecordJson",
                                                                        session.primaryFinding(
                                                                                        card,
                                                                                        stop::get)
                                                                                .encoded));
                                                records.value(
                                                        "nativeFindings",
                                                        session.findings(card, stop::get));
                                            }
                                        },
                                        stop::get);
                        ContentOrigin capturedOrigin = origin;
                        InputSnapshot staged = input;
                        input = null;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation || stop.get()) {
                                        staged.close();
                                        capturedOrigin.close();
                                        return;
                                    }
                                    end();
                                    try {
                                        startActivity(
                                                new Intent(this, ContentPreviewActivity.class)
                                                        .putExtra(
                                                                "path",
                                                                staged.file.getAbsolutePath())
                                                        .putExtra("sha256", staged.sha256)
                                                        .putExtra("sourceSize", staged.size)
                                                        .putExtra("name", RecoveredFiles.name(card))
                                                        .putExtra(
                                                                "exportName",
                                                                RecoveredFiles.name(card))
                                                        .putExtra(
                                                                "originalSha256",
                                                                session.input.sha256)
                                                        .putExtra("originalOffset", card.offset)
                                                        .putExtra(
                                                                "originalSize", session.input.size)
                                                        .putExtra("preferred", card.kind.name())
                                                        .putExtra(
                                                                "contentOrigin",
                                                                capturedOrigin.descriptor()));
                                    } catch (Exception error) {
                                        staged.close();
                                        capturedOrigin.close();
                                        status.setText(error.toString());
                                    }
                                });
                    } catch (Exception error) {
                        if (input != null) input.close();
                        if (origin != null) origin.close();
                        failed(ticket, error);
                    }
                });
    }

    private void tools() {
        if (busy || session == null) return;
        String[] labels = {
            "PhotoRec raw findings / full catalog",
            "Binwalk",
            "Classified strings",
            "Collected metadata / EXIF",
            "MPV codecs / streams / tags",
            "Browse archives / compressed streams",
            "Native hex",
            "All native tools",
            "Reports / grid exports"
        };
        new AlertDialog.Builder(this)
                .setTitle("Source tools")
                .setItems(
                        labels,
                        (dialog, which) -> {
                            if (which == 8) {
                                export();
                                return;
                            }
                            if (which == 6) {
                                nativeHex(0);
                                return;
                            }
                            Class<?> target =
                                    which == 5
                                            ? NativeArchiveExplorerActivity.class
                                            : which == 7
                                                    ? NativeToolsActivity.class
                                                    : InvestigationActivity.class;
                            Intent intent =
                                    new Intent(this, target)
                                            .putExtra("path", session.input.file.getAbsolutePath())
                                            .putExtra("name", name)
                                            .putExtra("sha256", session.input.sha256)
                                            .putExtra("sourceSize", session.input.size);
                            if (which < 5)
                                intent.putExtra(
                                        "provider",
                                        new String[] {
                                                    "photorec",
                                                    "binwalk",
                                                    "strings",
                                                    "metadata",
                                                    "mpv"
                                                }
                                                [which]);
                            if (which == 5)
                                startActivityForResult(
                                        intent.putExtra("originPreviewResult", true), 2);
                            else startActivity(intent);
                        })
                .show();
    }

    private void export() {
        if (busy || pickerOpen || session == null) return;
        if (grid != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Complete export scope")
                    .setItems(
                            new String[] {
                                "Every grid cell / exact ranges / values",
                                "Content map / every raw finding / full catalog"
                            },
                            (dialog, which) -> {
                                if (which == 0) exportGrid();
                                else exportMap();
                            })
                    .show();
        } else exportMap();
    }

    private void exportGrid() {
        if (busy || pickerOpen || grid == null) return;
        ByteGrid.Result captured = grid;
        int ticket = begin("Staging every grid cell and its exact source interval…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        staged(
                                ticket,
                                PreparedExport.stream(
                                        exportDirectory,
                                        "byte-grid.json",
                                        "application/json",
                                        output ->
                                                ByteGrid.writeReport(
                                                        captured, name, output, stop::get),
                                        stop::get));
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void exportMap() {
        if (busy || pickerOpen || session == null) return;
        int ticket = begin("Staging complete content map, raw findings and catalog…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        PreparedExport result =
                                PreparedExport.stream(
                                        exportDirectory,
                                        "gallerize.json",
                                        "application/json",
                                        output -> session.writeReport(output, stop::get),
                                        stop::get);
                        staged(ticket, result);
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void exportFiles() {
        if (busy || pickerOpen || session == null || map == null) return;
        List<ContentMap.Card> files =
                new ArrayList<>(mode.equals("gallery") ? selected : map.cards);
        int ticket = begin("Staging recovered files from all pages of this group…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        PreparedExport result =
                                RecoveredFiles.zip(
                                        session.input, files, exportDirectory, stop::get);
                        staged(ticket, result);
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void exportRange(ContentMap.Card card) {
        if (busy || pickerOpen) return;
        int ticket = begin("Staging exact original range…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        PreparedExport result =
                                RecoveredFiles.file(
                                        session.input, card, exportDirectory, stop::get);
                        staged(ticket, result);
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void staged(int ticket, PreparedExport result) {
        runOnUiThread(
                () -> {
                    if (isDestroyed() || ticket != generation) {
                        result.discard();
                        return;
                    }
                    end();
                    if (prepared != null) prepared.discard();
                    prepared = result;
                    try {
                        pickerOpen = true;
                        startActivityForResult(
                                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType(result.mime)
                                        .putExtra(Intent.EXTRA_TITLE, result.name),
                                1);
                        status.setText("Prepared " + result.size + " bytes · choose destination");
                    } catch (RuntimeException error) {
                        pickerOpen = false;
                        result.discard();
                        prepared = null;
                        status.setText(error.toString());
                    }
                });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 2) {
            if (result != RESULT_OK || data == null) {
                status.setText("Archive selection cancelled");
                return;
            }
            if (session == null) {
                status.setText("Archive parent session expired");
                return;
            }
            int ticket = begin("Acquiring returned archive member…");
            AtomicBoolean stop = cancellation;
            ArchivePreviewLauncher.launch(
                    this,
                    worker,
                    data,
                    session.input.sha256,
                    session.input.size,
                    stop::get,
                    () -> !isDestroyed() && ticket == generation,
                    this::end,
                    message -> status.setText(message));
            return;
        }
        if (request != 1) return;
        pickerOpen = false;
        PreparedExport captured = prepared;
        prepared = null;
        if (captured == null) {
            status.setText("Prepared export expired");
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) {
            captured.discard();
            status.setText("Export cancelled");
            return;
        }
        Uri destination = data.getData();
        int ticket = begin("Saving prepared bytes…");
        AtomicBoolean stop = cancellation;
        worker.execute(
                () -> {
                    try {
                        try (OutputStream output =
                                getContentResolver().openOutputStream(destination, "wt")) {
                            if (output == null) throw new IOException("No output stream");
                            captured.copyTo(output, stop::get);
                        }
                        runOnUiThread(
                                () -> {
                                    if (!isDestroyed() && ticket == generation) {
                                        end();
                                        status.setText(
                                                "Export saved · " + captured.size + " bytes");
                                    }
                                });
                    } catch (Exception error) {
                        failed(ticket, error);
                    } finally {
                        captured.discard();
                    }
                });
    }

    private void failed(int ticket, Exception failure) {
        runOnUiThread(
                () -> {
                    if (!isDestroyed() && ticket == generation) {
                        end();
                        status.setText(failure.toString());
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle saved) {
        super.onSaveInstanceState(saved);
        saved.putString("session", retained);
        saved.putString("mode", mode);
        saved.putString("group", group);
        saved.putInt("page", page);
        saved.putString("width", width.getText().toString());
        saved.putString("height", height.getText().toString());
        if (prepared != null) {
            saved.putString("prepared", prepared.file.getAbsolutePath());
            saved.putString("exportName", prepared.name);
            saved.putString("exportMime", prepared.mime);
            saved.putLong("exportSize", prepared.size);
        }
    }

    @Override
    public void onBackPressed() {
        if (busy) {
            cancellation.set(true);
            status.setText("Cancellation requested · wait for the current operation");
        } else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        cancellation.set(true);
        generation++;
        boolean retain = isChangingConfigurations() && retained != null;
        worker.execute(
                () -> {
                    try {
                        if (session != null) session.close(retain);
                    } catch (Exception ignored) {
                    }
                });
        worker.shutdown();
        if (prepared != null && !isChangingConfigurations()) prepared.discard();
        if (galleryAdapter != null) galleryAdapter.close();
        super.onDestroy();
    }
}

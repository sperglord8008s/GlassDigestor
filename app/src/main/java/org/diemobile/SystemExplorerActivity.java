// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only navigator. Package/component facts are declarations, never live-state claims. */
public final class SystemExplorerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Entry> rows = new ArrayList<>();
    private TextView heading, detail, count;
    private EditText search;
    private ListView list;
    private volatile int generation;

    private static final class Fact {
        final String key, value;
        final boolean code;

        Fact(String key, Object value, boolean code) {
            this.key = key;
            this.value = value == null ? "not provided" : value.toString();
            this.code = code;
        }
    }

    private static final class Entry {
        final String section, label, description, packageId;
        final Runnable open;
        final List<Fact> facts;
        final boolean header;

        Entry(String section, String label, String description, Runnable open, Fact... facts) {
            this(section, label, description, open, null, facts);
        }

        Entry(
                String section,
                String label,
                String description,
                Runnable open,
                String packageId,
                Fact... facts) {
            this.section = section;
            this.label = label;
            this.description = description;
            this.packageId = packageId;
            this.open = open;
            this.facts = Arrays.asList(facts);
            this.header = false;
        }

        Entry(String section, int count) {
            this.section = section;
            label = section + " · " + count;
            description = "";
            packageId = null;
            open = null;
            facts = Collections.emptyList();
            header = true;
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder(label).append('\n').append(description);
            for (Fact fact : facts)
                text.append('\n').append(fact.key).append(": ").append(fact.value);
            return text.toString();
        }
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = WorkbenchUi.dp(this, 8);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(getColor(R.color.workbench_background));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        android.view.WindowInsets.Type.systemBars()
                                                | android.view.WindowInsets.Type.displayCutout());
                        view.setPadding(
                                bars.left + padding,
                                bars.top + padding,
                                bars.right + padding,
                                bars.bottom + padding);
                        return insets;
                    });
        }
        heading = WorkbenchUi.section(this, "Explore");
        heading.setMaxLines(2);
        root.addView(heading);
        heading.setOnClickListener(
                view -> message("Current location", heading.getText().toString()));
        detail = WorkbenchUi.text(this, "", 12);
        detail.setMaxLines(2);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setTag("explore-scope");
        detail.setOnClickListener(
                view -> message("Inventory scope / complete status", detail.getText().toString()));
        root.addView(detail);
        LinearLayout navigation = new LinearLayout(this);
        for (String label : new String[] {"User apps", "System apps", "Files", "Services"}) {
            navigation.addView(
                    button(
                            label,
                            () -> {
                                if (label.equals("Files")) browse(new File("/"));
                                else if (label.equals("Services")) serviceChoices();
                                else packages(label.equals("System apps") ? 1 : 0);
                            }),
                    new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(navigation);
        search = new EditText(this);
        search.setSingleLine();
        search.setHint("Filter name, package, path or field");
        search.setTag("explore-filter");
        root.addView(search);
        count = WorkbenchUi.text(this, "", 12);
        count.setTag("explore-count");
        root.addView(count);
        list = WorkbenchUi.scrolling(new ListView(this));
        list.setTag("explore-records");
        WorkbenchUi.list(this, list);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        search.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        render();
                    }

                    @Override
                    public void afterTextChanged(Editable value) {}
                });
        packages(0);
    }

    private void serviceChoices() {
        new AlertDialog.Builder(this)
                .setTitle("Service inventory")
                .setItems(
                        new String[] {"App-declared services", "OS Binder service registry"},
                        (dialog, which) -> {
                            if (which == 0) packages(2);
                            else binderServices();
                        })
                .show();
    }

    private void render() {
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        Map<String, List<Entry>> sections = new LinkedHashMap<>();
        int matched = 0;
        for (Entry entry : rows)
            if (entry.toString().toLowerCase(Locale.ROOT).contains(query)) {
                sections.computeIfAbsent(entry.section, key -> new ArrayList<>()).add(entry);
                matched++;
            }
        List<Entry> visible = new ArrayList<>();
        for (var section : sections.entrySet()) {
            visible.add(new Entry(section.getKey(), section.getValue().size()));
            visible.addAll(section.getValue());
        }
        count.setText(
                matched
                        + " matching entries of "
                        + rows.size()
                        + " · hold an entry for complete fields");
        list.setAdapter(
                new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return visible.size();
                    }

                    @Override
                    public Entry getItem(int position) {
                        return visible.get(position);
                    }

                    @Override
                    public long getItemId(int position) {
                        return position;
                    }

                    @Override
                    public int getViewTypeCount() {
                        return 2;
                    }

                    @Override
                    public int getItemViewType(int position) {
                        return getItem(position).header ? 0 : 1;
                    }

                    @Override
                    public boolean areAllItemsEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isEnabled(int position) {
                        return !getItem(position).header;
                    }

                    @Override
                    public View getView(int position, View recycled, ViewGroup parent) {
                        Entry entry = getItem(position);
                        if (entry.header) {
                            TextView title =
                                    WorkbenchUi.section(SystemExplorerActivity.this, entry.label);
                            title.setTag("explore-section");
                            return title;
                        }
                        WorkbenchUi.Lines text =
                                new WorkbenchUi.Lines(SystemExplorerActivity.this)
                                        .heading(WorkbenchUi.preview(entry.label, 180));
                        if (!entry.description.isEmpty())
                            text.muted(WorkbenchUi.preview(entry.description, 240));
                        for (Fact fact : entry.facts)
                            text.field(fact.key, WorkbenchUi.preview(fact.value, 240), fact.code);
                        return WorkbenchUi.row(SystemExplorerActivity.this, recycled, text.build());
                    }
                });
        list.setOnItemClickListener(
                (parent, view, position, id) -> {
                    Entry entry = visible.get(position);
                    if (entry.header) return;
                    if (entry.open == null) entryDetails(entry);
                    else entry.open.run();
                });
        list.setOnItemLongClickListener(
                (parent, view, position, id) -> {
                    Entry entry = visible.get(position);
                    if (entry.header) return false;
                    entryDetails(entry);
                    return true;
                });
    }

    private void entryDetails(Entry entry) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = WorkbenchUi.dp(this, 12);
        body.setPadding(padding, padding, padding, padding);
        body.addView(WorkbenchUi.section(this, entry.label));
        if (!entry.description.isEmpty()) body.addView(WorkbenchUi.prose(this, entry.description));
        for (Fact fact : entry.facts)
            body.addView(WorkbenchUi.fact(this, fact.key, fact.value, fact.code));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(body);
        AlertDialog.Builder dialog =
                new AlertDialog.Builder(this)
                        .setTitle(entry.section)
                        .setView(scroll)
                        .setNeutralButton(
                                "Copy fields",
                                (ignored, which) -> new TextActions(this).copy(entry.toString()))
                        .setPositiveButton("Close", null);
        if (entry.packageId != null)
            dialog.setNegativeButton(
                    "Package", (ignored, which) -> packageDetails(entry.packageId));
        else if (entry.open != null)
            dialog.setNegativeButton("Open", (ignored, which) -> entry.open.run());
        dialog.show();
    }

    private void message(String title, String value) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        TextView body = WorkbenchUi.prose(this, value);
        int pad = WorkbenchUi.dp(this, 12);
        body.setPadding(pad, pad, pad, pad);
        scroll.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private int begin(String title, String description) {
        heading.setText(WorkbenchUi.inline(title));
        detail.setText(WorkbenchUi.visible(description));
        rows.clear();
        search.setText("");
        render();
        return ++generation;
    }

    private void finishRows(int ticket, List<Entry> result, String message) {
        runOnUiThread(
                () -> {
                    if (isDestroyed() || ticket != generation) return;
                    rows.clear();
                    rows.addAll(result);
                    detail.setText(WorkbenchUi.visible(message));
                    render();
                });
    }

    private void packages(int mode) {
        int ticket =
                begin(
                        mode == 2
                                ? "Declared Android services"
                                : mode == 1 ? "System packages" : "User packages",
                        "Reading local PackageManager inventory…");
        worker.execute(
                () -> {
                    List<Entry> entries = new ArrayList<>();
                    try {
                        PackageManager pm = getPackageManager();
                        List<ApplicationInfo> apps =
                                new ArrayList<>(pm.getInstalledApplications(0));
                        apps.sort(Comparator.comparing(app -> app.packageName));
                        boolean capped = false;
                        for (ApplicationInfo app : apps) {
                            boolean system =
                                    (app.flags
                                                    & (ApplicationInfo.FLAG_SYSTEM
                                                            | ApplicationInfo
                                                                    .FLAG_UPDATED_SYSTEM_APP))
                                            != 0;
                            if (mode != 2 && system != (mode == 1)) continue;
                            String name = String.valueOf(pm.getApplicationLabel(app)),
                                    pkg = app.packageName;
                            if (mode != 2) {
                                entries.add(
                                        new Entry(
                                                "Applications",
                                                name,
                                                "",
                                                () -> packageDetails(pkg),
                                                new Fact("Package", pkg, true),
                                                new Fact(
                                                        "Type",
                                                        system ? "System app" : "User app",
                                                        false),
                                                new Fact("Enabled flag", app.enabled, false)));
                                continue;
                            }
                            try {
                                PackageInfo info =
                                        pm.getPackageInfo(
                                                pkg,
                                                PackageManager.GET_SERVICES
                                                        | PackageManager.MATCH_DISABLED_COMPONENTS);
                                if (info.services == null) continue;
                                for (ServiceInfo service : info.services)
                                    entries.add(
                                            component(pkg, "Declared services · " + pkg, service));
                            } catch (Exception unavailable) {
                                entries.add(
                                        new Entry(
                                                "Unavailable declarations",
                                                pkg,
                                                "PackageManager could not return services",
                                                () -> message(pkg, unavailable.toString()),
                                                new Fact("Diagnostic", unavailable, false)));
                            }
                            if (entries.size() > 20000) {
                                capped = true;
                                break;
                            }
                        }
                        finishRows(
                                ticket,
                                entries,
                                entries.size()
                                        + " entries · current Android user/profile and package"
                                        + " visibility"
                                        + (capped
                                                ? " · listing stopped after the 20,000-entry"
                                                        + " threshold"
                                                : "")
                                        + (mode == 2
                                                ? " · declarations, not running services"
                                                : " · flags do not establish trust or runtime"
                                                        + " behavior"));
                    } catch (Exception error) {
                        finishRows(ticket, entries, "Inventory error: " + error);
                    }
                });
    }

    private Entry component(String pkg, String section, ComponentInfo component) {
        List<Fact> facts =
                new ArrayList<>(
                        Arrays.asList(
                                new Fact("Package", pkg, true),
                                new Fact(
                                        "Component type",
                                        component.getClass().getSimpleName(),
                                        false),
                                new Fact("Exported flag", component.exported, false),
                                new Fact("Enabled flag", component.enabled, false),
                                new Fact("Process", component.processName, true)));
        if (component instanceof ServiceInfo)
            facts.add(new Fact("Permission", ((ServiceInfo) component).permission, true));
        return new Entry(
                section,
                component.name,
                "Manifest declaration, not information that the component is running. Exported"
                        + " components may be addressable by other apps subject to permissions and"
                        + " platform rules.",
                null,
                pkg,
                facts.toArray(new Fact[0]));
    }

    private void binderServices() {
        int ticket =
                begin(
                        "OS Binder service registry",
                        "Capturing the fixed platform service-list command as this app's normal"
                                + " UID…");
        worker.execute(
                () -> {
                    File captured = null;
                    try {
                        BinderServiceCapture.Captured value =
                                BinderServiceCapture.capture(
                                        this,
                                        () ->
                                                isDestroyed()
                                                        || ticket != generation
                                                        || Thread.currentThread().isInterrupted());
                        captured = value.file;
                        File input = captured;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) {
                                        input.delete();
                                        return;
                                    }
                                    try {
                                        startActivity(
                                                new Intent(this, InvestigationActivity.class)
                                                        .putExtra("provider", "binder-registry")
                                                        .putExtra("path", input.getAbsolutePath())
                                                        .putExtra(
                                                                "name",
                                                                "Normal-UID Binder registry"
                                                                        + " capture")
                                                        .putExtra("sha256", value.sha256)
                                                        .putExtra("sourceSize", value.size));
                                        detail.setText(
                                                "Captured output opened · registrations and"
                                                        + " diagnostics remain separate; no global"
                                                        + " visibility or client-use claim");
                                    } catch (RuntimeException failure) {
                                        input.delete();
                                        detail.setText("Cannot open captured registry: " + failure);
                                    }
                                });
                    } catch (Exception failure) {
                        if (captured != null) captured.delete();
                        finishRows(
                                ticket,
                                Collections.emptyList(),
                                "Binder capture failed: " + failure);
                    }
                });
    }

    private void packageDetails(String pkg) {
        int ticket = begin(pkg, "Reading package metadata…");
        worker.execute(
                () -> {
                    List<Entry> entries = new ArrayList<>();
                    try {
                        PackageInfo info =
                                getPackageManager()
                                        .getPackageInfo(
                                                pkg,
                                                PackageManager.GET_SERVICES
                                                        | PackageManager.GET_ACTIVITIES
                                                        | PackageManager.GET_PROVIDERS
                                                        | PackageManager.GET_RECEIVERS
                                                        | PackageManager.GET_PERMISSIONS
                                                        | PackageManager.MATCH_DISABLED_COMPONENTS);
                        ApplicationInfo app = info.applicationInfo;
                        String metadata =
                                "Package: "
                                        + pkg
                                        + "\nVersion: "
                                        + info.versionName
                                        + "\nUID: "
                                        + app.uid
                                        + "\nMinimum / target SDK: "
                                        + app.minSdkVersion
                                        + " / "
                                        + app.targetSdkVersion
                                        + "\nData directory: "
                                        + app.dataDir
                                        + "\nNative library directory: "
                                        + app.nativeLibraryDir
                                        + "\n\n"
                                        + "UID is the Android/Linux security identity. Paths"
                                        + " identify configured directories, not permission to read"
                                        + " them. System-app status is not a trust guarantee.";
                        entries.add(
                                new Entry(
                                        "Identity / context",
                                        "Package identity",
                                        "Tap for complete technical fields",
                                        () -> message(pkg, metadata),
                                        new Fact("Package", pkg, true),
                                        new Fact("Version", info.versionName, false),
                                        new Fact("UID", app.uid, true),
                                        new Fact(
                                                "Minimum / target SDK",
                                                app.minSdkVersion + " / " + app.targetSdkVersion,
                                                true)));
                        entries.add(
                                new Entry(
                                        "Identity / context",
                                        "What does this package do? / community knowledge",
                                        "Sourced descriptions and declared relationships; separate"
                                                + " from device observations",
                                        () ->
                                                startActivity(
                                                        new Intent(
                                                                        this,
                                                                        InvestigationActivity.class)
                                                                .putExtra(
                                                                        "provider",
                                                                        "package-knowledge")
                                                                .putExtra(
                                                                        "path",
                                                                        new File(
                                                                                        getCacheDir(),
                                                                                        "package-knowledge-catalog")
                                                                                .getAbsolutePath())
                                                                .putExtra("name", pkg)
                                                                .putExtra(
                                                                        "knowledgePackageId", pkg)),
                                        new Fact("Exact package ID", pkg, true)));
                        if (app.sourceDir != null)
                            entries.add(path("APK files", "Base APK", app.sourceDir, false));
                        ArrayList<String> paths = new ArrayList<>();
                        if (app.sourceDir != null) paths.add(app.sourceDir);
                        if (app.splitSourceDirs != null)
                            Collections.addAll(paths, app.splitSourceDirs);
                        entries.add(
                                new Entry(
                                        "APK files",
                                        "Investigate captured base + split APK set",
                                        "Compiled declarations, dependencies and independent"
                                                + " signing information",
                                        () -> {
                                            if (paths.isEmpty()) {
                                                toast(
                                                        "No APK source paths exposed for this"
                                                                + " package");
                                                return;
                                            }
                                            startActivity(
                                                    new Intent(this, InvestigationActivity.class)
                                                            .putExtra("provider", "package-set")
                                                            .putExtra("path", paths.get(0))
                                                            .putExtra("name", pkg)
                                                            .putExtra("inventoryPackage", pkg)
                                                            .putStringArrayListExtra(
                                                                    "packagePaths", paths));
                                        },
                                        new Fact("PackageManager paths", paths.size(), true)));
                        if (app.splitSourceDirs != null)
                            for (String split : app.splitSourceDirs)
                                entries.add(path("APK files", "Split APK", split, false));
                        if (app.nativeLibraryDir != null)
                            entries.add(
                                    path(
                                            "Directories",
                                            "Native library directory",
                                            app.nativeLibraryDir,
                                            true));
                        if (app.dataDir != null)
                            entries.add(
                                    path(
                                            "Directories",
                                            "Data directory (may be inaccessible)",
                                            app.dataDir,
                                            true));
                        ComponentInfo[][] components = {
                            info.activities, info.services, info.receivers, info.providers
                        };
                        String[] sections = {
                            "Declared activities",
                            "Declared services",
                            "Declared receivers",
                            "Declared providers"
                        };
                        for (int i = 0; i < components.length; i++)
                            if (components[i] != null)
                                for (ComponentInfo component : components[i])
                                    entries.add(component(pkg, sections[i], component));
                        if (info.requestedPermissions != null)
                            for (String permission : info.requestedPermissions)
                                entries.add(
                                        new Entry(
                                                "Requested permissions",
                                                permission,
                                                "Declared request · not proof of a grant or use",
                                                () ->
                                                        message(
                                                                permission,
                                                                "Declared permission, not proof of"
                                                                    + " use. Analyze the base APK"
                                                                    + " for additional permission"
                                                                    + " interpretation.")));
                        finishRows(
                                ticket,
                                entries,
                                "Package "
                                        + pkg
                                        + " · version "
                                        + info.versionName
                                        + " · UID "
                                        + app.uid
                                        + "\n"
                                        + "Long-press for complete fields; declarations do not"
                                        + " establish runtime use.");
                    } catch (Exception error) {
                        finishRows(ticket, entries, "Package metadata unavailable: " + error);
                    }
                });
    }

    private Entry path(String section, String label, String path, boolean directory) {
        return new Entry(
                section,
                label,
                "",
                () -> {
                    if (directory) browse(new File(path));
                    else select(new File(path));
                },
                new Fact("Path", path, true),
                new Fact("Type", directory ? "Directory" : "APK file", false));
    }

    private void browse(File directory) {
        int ticket =
                begin(
                        directory.getAbsolutePath(),
                        "Read-only filesystem · only OS-permitted paths");
        worker.execute(
                () -> {
                    List<Entry> entries = new ArrayList<>();
                    File parent = directory.getParentFile();
                    if (parent != null)
                        entries.add(
                                new Entry(
                                        "Navigation",
                                        "..",
                                        "Parent directory",
                                        () -> browse(parent),
                                        new Fact("Path", parent, true)));
                    try {
                        File[] children = directory.listFiles();
                        if (children == null) {
                            finishRows(
                                    ticket,
                                    entries,
                                    "Directory inaccessible or unavailable. Android sandbox/SELinux"
                                            + " may restrict listing.");
                            return;
                        }
                        Arrays.sort(
                                children,
                                Comparator.comparing((File file) -> !file.isDirectory())
                                        .thenComparing(
                                                File::getName, String.CASE_INSENSITIVE_ORDER));
                        for (int i = 0; i < Math.min(children.length, 20000); i++) {
                            File file = children[i];
                            boolean folder = file.isDirectory();
                            List<Fact> facts = new ArrayList<>();
                            facts.add(new Fact("Type", folder ? "Directory" : "File", false));
                            if (!folder) facts.add(new Fact("Size (bytes)", file.length(), true));
                            facts.add(new Fact("Readable flag", file.canRead(), false));
                            facts.add(new Fact("Path", file.getAbsolutePath(), true));
                            entries.add(
                                    new Entry(
                                            folder ? "Directories" : "Files",
                                            file.getName(),
                                            "",
                                            () -> {
                                                if (folder) browse(file);
                                                else select(file);
                                            },
                                            facts.toArray(new Fact[0])));
                        }
                        finishRows(
                                ticket,
                                entries,
                                children.length
                                        + " directory entries"
                                        + (children.length > 20000
                                                ? " · display capped at 20,000"
                                                : "")
                                        + " · read-only");
                    } catch (Exception error) {
                        finishRows(ticket, entries, "Filesystem error: " + error);
                    }
                });
    }

    private void select(File file) {
        // Virtual kernel files and device nodes may be non-regular or never-ending streams.
        try {
            android.system.StructStat stat = android.system.Os.stat(file.getAbsolutePath());
            if (!android.system.OsConstants.S_ISREG(stat.st_mode) || !file.canRead()) {
                toast("Only readable regular files can be scanned");
                return;
            }
            String path = file.getCanonicalPath();
            if (path.startsWith("/proc/") || path.startsWith("/sys/") || path.startsWith("/dev/")) {
                toast("Virtual kernel files are excluded from binary scanning");
                return;
            }
            setResult(RESULT_OK, new Intent().putExtra("path", path));
            finish();
        } catch (Exception error) {
            toast("Cannot access: " + error.getMessage());
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        generation++;
        worker.shutdown();
        super.onDestroy();
    }
}

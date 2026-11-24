// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Looper;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Real inventory widgets and file metadata; PackageManager observations are authored fixtures. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28, qualifiers = "w360dp-h640dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class ExplorerWorkbenchTest {
    private ActivityController<SystemExplorerActivity> controller;
    private SystemExplorerActivity activity;
    private Path temporary;
    private PackageInfo user;

    @Before
    public void setup() throws Exception {
        temporary = Files.createTempDirectory("explore-formatting");
        user = install("org.fixture.explore.user", "Fixture app", false);
        install("org.fixture.explore.other", "Fixture app", false);
        install("org.fixture.explore.system", "Fixture system app", true);
        controller = Robolectric.buildActivity(SystemExplorerActivity.class).setup().visible();
        activity = controller.get();
        settle();
    }

    private PackageInfo install(String pkg, String label, boolean system) throws Exception {
        PackageInfo info = new PackageInfo();
        info.packageName = pkg;
        info.versionName = "fixture-1";
        ApplicationInfo app = new ApplicationInfo();
        app.packageName = pkg;
        app.nonLocalizedLabel = label;
        app.sourceDir =
                Files.write(temporary.resolve(pkg + ".apk"), new byte[] {1, 2, 3}).toString();
        app.splitSourceDirs =
                new String[] {
                    Files.write(temporary.resolve(pkg + "-split.apk"), new byte[] {4, 5}).toString()
                };
        app.nativeLibraryDir = temporary.toString();
        app.dataDir = temporary.resolve("private-data").toString();
        app.enabled = true;
        app.uid = 12345;
        app.minSdkVersion = 26;
        app.targetSdkVersion = 35;
        app.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        info.applicationInfo = app;
        ServiceInfo service = new ServiceInfo();
        service.name = pkg + ".FixtureService";
        service.packageName = pkg;
        service.applicationInfo = app;
        service.enabled = false;
        service.exported = true;
        service.processName = pkg + ":worker";
        service.permission = "org.fixture.permission.SERVICE";
        info.services = new ServiceInfo[] {service};
        ActivityInfo screen = new ActivityInfo();
        screen.name = pkg + ".Screen";
        screen.packageName = pkg;
        screen.applicationInfo = app;
        info.activities = new ActivityInfo[] {screen};
        info.receivers = new ActivityInfo[] {screen};
        ProviderInfo provider = new ProviderInfo();
        provider.name = pkg + ".Provider";
        provider.packageName = pkg;
        provider.applicationInfo = app;
        info.providers = new ProviderInfo[] {provider};
        info.requestedPermissions = new String[] {"android.permission.CAMERA"};
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager())
                .installPackage(info);
        return info;
    }

    private void settle() throws Exception {
        for (int i = 0; i < 2; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private ListView list() {
        return ReflectionHelpers.getField(activity, "list");
    }

    private EditText search() {
        return ReflectionHelpers.getField(activity, "search");
    }

    private int find(String text) {
        for (int i = 0; i < list().getAdapter().getCount(); i++)
            if (list().getAdapter().isEnabled(i)
                    && list().getAdapter().getItem(i).toString().contains(text)) return i;
        fail("Missing explorer entry " + text);
        return -1;
    }

    private void packageDetails() throws Exception {
        ReflectionHelpers.callInstanceMethod(
                activity,
                "packageDetails",
                ReflectionHelpers.ClassParameter.from(String.class, user.packageName));
        settle();
    }

    private static String text(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                text.append(text(((ViewGroup) view).getChildAt(i)));
        return text.toString();
    }

    @After
    public void cleanup() throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
        controller.pause().stop().destroy();
        assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        try (var paths = Files.walk(temporary)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    @Test
    public void appNamesIdentifiersAndFlagsHaveDistinctFormattingAndHeadersDoNotActLikeRecords() {
        search().setText("org.fixture.explore.");
        assertEquals(3, list().getAdapter().getCount());
        assertFalse(list().getAdapter().isEnabled(0));
        TextView header = (TextView) list().getAdapter().getView(0, null, list());
        assertTrue(header.isAccessibilityHeading());
        TextView row =
                (TextView) list().getAdapter().getView(find(user.packageName), header, list());
        assertFalse(row.isAccessibilityHeading());
        Spanned formatted = (Spanned) row.getText();
        assertTrue(formatted.toString().contains("Package: " + user.packageName));
        assertTrue(formatted.toString().contains("Enabled flag: true"));
        assertTrue(formatted.getSpans(0, formatted.length(), StyleSpan.class).length > 0);
        assertTrue(formatted.getSpans(0, formatted.length(), TypefaceSpan.class).length > 0);
        View decor = activity.getWindow().getDecorView();
        decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 360, 640);
        assertTrue("Explore list collapsed on a compact screen", list().getHeight() >= 180);
    }

    @Test
    public void packageSectionsRetainAllWorkflowsAndFilteringDoesNotLeaveEmptyHeadings()
            throws Exception {
        packageDetails();
        StringBuilder sections = new StringBuilder();
        for (int i = 0; i < list().getAdapter().getCount(); i++)
            if (!list().getAdapter().isEnabled(i)) sections.append(list().getAdapter().getItem(i));
        for (String label :
                new String[] {
                    "Identity / context",
                    "APK files",
                    "Directories",
                    "Declared activities",
                    "Declared services",
                    "Declared receivers",
                    "Declared providers",
                    "Requested permissions"
                }) assertTrue(sections.toString(), sections.toString().contains(label));
        find("What does this package do?");
        find("captured base + split");
        find("Split APK");
        search().setText("android.permission.CAMERA");
        assertEquals(2, list().getAdapter().getCount());
        assertFalse(list().getAdapter().isEnabled(0));
        assertTrue(list().getAdapter().getItem(0).toString().contains("Requested permissions"));
        assertTrue(
                ReflectionHelpers.<TextView>getField(activity, "count")
                        .getText()
                        .toString()
                        .startsWith("1 matching entries"));
        search().setText("");
        int base = find("Base APK");
        list().performItemClick(null, base, base);
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        assertEquals(
                new File(user.applicationInfo.sourceDir).getCanonicalPath(),
                Shadows.shadowOf(activity).getResultIntent().getStringExtra("path"));
    }

    @Test
    public void declaredServiceFactsAndPackageActionStaySeparateFromRunningState()
            throws Exception {
        ReflectionHelpers.callInstanceMethod(
                activity, "packages", ReflectionHelpers.ClassParameter.from(int.class, 2));
        settle();
        search().setText(user.packageName);
        int service = find("FixtureService");
        TextView row = (TextView) list().getAdapter().getView(service, null, list());
        assertTrue(row.getText().toString().contains("Exported flag: true"));
        assertTrue(row.getText().toString().contains("Enabled flag: false"));
        assertTrue(row.getText().toString().contains(user.packageName + ":worker"));
        assertTrue(
                ReflectionHelpers.<TextView>getField(activity, "detail")
                        .getText()
                        .toString()
                        .contains("not running services"));
        list().performItemClick(null, service, service);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(
                text(dialog.findViewById(android.R.id.content))
                        .contains("org.fixture.permission.SERVICE"));
        assertEquals("Package", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        settle();
        find("Package identity");
    }

    @Test
    @Config(sdk = 26)
    public void filesUseSeparatedTypesSizesPathsAndExactCopyOnOldAndroid() throws Exception {
        Path folder = Files.createDirectory(temporary.resolve("subfolder"));
        Path file =
                Files.write(temporary.resolve("unsafe\u202Ename.txt"), new byte[] {8, 9, 10, 11});
        ReflectionHelpers.callInstanceMethod(
                activity,
                "browse",
                ReflectionHelpers.ClassParameter.from(File.class, temporary.toFile()));
        settle();
        boolean directorySection = false;
        for (int i = 0; i < list().getAdapter().getCount(); i++)
            if (!list().getAdapter().isEnabled(i)
                    && list().getAdapter().getItem(i).toString().contains("Directories"))
                directorySection = true;
        assertTrue(directorySection);
        search().setText("unsafe");
        int entry = find("unsafe");
        TextView row = (TextView) list().getAdapter().getView(entry, null, list());
        assertTrue(row.getText().toString().contains("\\u202E"));
        assertTrue(row.getText().toString().contains("Size (bytes): 4"));
        assertTrue(row.getText().toString().contains("Readable flag: true"));
        list().getOnItemLongClickListener().onItemLongClick(list(), row, entry, entry);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertTrue(
                clipboard
                        .getPrimaryClip()
                        .getItemAt(0)
                        .getText()
                        .toString()
                        .contains(file.toString()));
        assertTrue(Files.isDirectory(folder));
        assertArrayEquals(new byte[] {8, 9, 10, 11}, Files.readAllBytes(file));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
}

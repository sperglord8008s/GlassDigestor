// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Real private-file diagnostics and immutable document staging; no native engine or network. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class DiagnosticsWorkbenchTest {
    private Context context;
    private ActivityController<DebugLogActivity> controller;
    private DebugLogActivity activity;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        assertTrue(DebugJournal.clear(context));
    }

    @After
    public void cleanup() throws Exception {
        if (controller != null) {
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            controller.pause().stop().destroy();
            assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS));
        }
        DebugJournal.clear(context);
    }

    private void openLog() throws Exception {
        controller = Robolectric.buildActivity(DebugLogActivity.class).setup().visible();
        activity = controller.get();
        settle();
    }

    private void settle() throws Exception {
        for (int pass = 0; pass < 8; pass++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!ReflectionHelpers.<Boolean>getField(activity, "busy")) return;
        }
        fail("Debug log operation did not settle");
    }

    private Button button(String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    private String status() {
        return ((TextView) activity.getWindow().getDecorView().findViewWithTag("debug-status"))
                .getText()
                .toString();
    }

    private PreparedExport prepare() throws Exception {
        assertTrue(button("debug-export").performClick());
        settle();
        var picker = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(picker);
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.getAction());
        assertEquals("text/plain", picker.intent.getType());
        PreparedExport result = ReflectionHelpers.getField(activity, "prepared");
        assertNotNull(result);
        assertTrue(result.file.isFile());
        return result;
    }

    @Test
    public void journalFiltersPrivateTextAndKeepsOnlyStructuralMetadata() throws Exception {
        DebugJournal.record(
                context,
                "worker_done\ninjected",
                "engine=hashes token=uuid-1 seconds=60 analysisMs=300 requestMs=1000 ok=true"
                        + " exception=IOException filename=private.bin path=/private/selected.bin"
                        + " query=needle url=https://example.test/auth raw-inspected-string");
        DebugJournal.Snapshot snapshot = DebugJournal.read(context);
        assertTrue(snapshot.status, snapshot.available);
        assertEquals(1, snapshot.entries.size());
        DebugJournal.Entry entry = snapshot.entries.get(0);
        assertEquals("diagnostic", entry.event);
        assertTrue(
                entry.timestamp.matches(
                        "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"));
        String text = snapshot.text();
        for (String safe :
                new String[] {
                    "engine=hashes",
                    "token=uuid-1",
                    "seconds=60",
                    "analysisMs=300",
                    "requestMs=1000",
                    "ok=true",
                    "exception=IOException"
                }) assertTrue(text, text.contains(safe));
        for (String privateValue :
                new String[] {
                    "injected",
                    "private.bin",
                    "selected.bin",
                    "needle",
                    "example.test",
                    "raw-inspected-string"
                }) assertFalse(text, text.contains(privateValue));
        assertTrue(text.contains("[omitted]"));
        IOException failure = new IOException("secret input contents and private_filename.bin");
        failure.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("thirdparty.Parser", "read", "sensitive-source.bin", 8),
                    new StackTraceElement(
                            "org.diemobile.BinaryWorkerClient",
                            "perform",
                            "private-source.bin",
                            123)
                });
        DebugJournal.recordFailure(context, "worker_failed", "engine=hashes", failure);
        String failureText = DebugJournal.read(context).text();
        assertTrue(failureText.contains("exception=IOException"));
        assertTrue(failureText.contains("class=org.diemobile.BinaryWorkerClient"));
        assertTrue(failureText.contains("method=perform"));
        assertTrue(failureText.contains("line=123"));
        assertFalse(failureText.contains("secret input"));
        assertFalse(failureText.contains("private_filename.bin"));
        assertFalse(failureText.contains("private-source.bin"));
        assertFalse(failureText.contains("sensitive-source.bin"));
        File file = new File(context.getNoBackupFilesDir(), "program-debug/events.log");
        assertTrue(file.isFile());
        assertFalse(new File(context.getFilesDir(), "program-debug/events.log").exists());
        assertTrue(DebugJournal.clear(context));
        assertFalse(file.exists());
        assertTrue(DebugJournal.read(context).entries.isEmpty());
    }

    @Test
    public void retentionBoundsRowsBytesAndDetailsAndPreservesNewestEvents() {
        String event = "worker_finished_" + "x".repeat(48);
        String details =
                " engine=hashes token="
                        + "a".repeat(96)
                        + " exception="
                        + "X".repeat(96)
                        + " status="
                        + "x".repeat(64);
        for (int i = 0; i < 300; i++) DebugJournal.record(context, event, "count=" + i + details);
        DebugJournal.Snapshot snapshot = DebugJournal.read(context);
        assertTrue(snapshot.status, snapshot.available);
        assertTrue(snapshot.entries.size() <= DebugJournal.MAX_ENTRIES);
        assertTrue(snapshot.entries.size() > 100);
        assertTrue(snapshot.entries.get(0).detail.startsWith("count=299 "));
        assertFalse(
                snapshot.entries.get(snapshot.entries.size() - 1).detail.startsWith("count=0 "));
        for (DebugJournal.Entry entry : snapshot.entries)
            assertTrue(entry.detail.length() <= DebugJournal.MAX_DETAIL_UNITS);
        assertTrue(
                new File(context.getNoBackupFilesDir(), "program-debug/events.log").length()
                        <= DebugJournal.MAX_BYTES);
        DebugJournal.record(context, "bounded", "engine=hashes ".repeat(300));
        snapshot = DebugJournal.read(context);
        assertTrue(snapshot.status, snapshot.available);
        assertTrue(snapshot.entries.get(0).detail.contains("[bounded]"));
        assertTrue(snapshot.entries.get(0).detail.length() <= DebugJournal.MAX_DETAIL_UNITS);
    }

    @Test
    public void concurrentRequestsDoNotLoseEventsAndStorageFailuresCannotEscape() throws Exception {
        ExecutorService writers = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < 40; i++) {
                final int number = i;
                writers.submit(
                        () -> DebugJournal.record(context, "worker_started", "count=" + number));
            }
        } finally {
            writers.shutdown();
            assertTrue(writers.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(40, DebugJournal.read(context).entries.size());
        File unavailable = File.createTempFile("debug-unavailable-", ".file");
        try {
            Context badStorage =
                    new ContextWrapper(context) {
                        @Override
                        public File getNoBackupFilesDir() {
                            return unavailable;
                        }
                    };
            DebugJournal.record(badStorage, "worker_failed", "exception=IOException");
            assertFalse(DebugJournal.read(badStorage).available);
            assertFalse(DebugJournal.clear(badStorage));
            assertEquals(40, DebugJournal.read(context).entries.size());
            DebugJournal.record(null, "ignored", "");
        } finally {
            assertTrue(unavailable.delete());
        }
        File journal = new File(context.getNoBackupFilesDir(), "program-debug/events.log");
        Files.write(
                journal.toPath(), "damaged untrusted log text\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(DebugJournal.read(context).available);
        DebugJournal.record(context, "worker_started", "engine=hashes");
        DebugJournal.Snapshot recovered = DebugJournal.read(context);
        assertTrue(recovered.status, recovered.available);
        assertEquals(2, recovered.entries.size());
        assertEquals("worker_started", recovered.entries.get(0).event);
        assertEquals("journal_reset", recovered.entries.get(1).event);
        assertFalse(recovered.text().contains("untrusted log text"));
    }

    @Test
    public void trustedAncestorAliasSharesTheJournalAndStillRefusesLinkedLeaves() throws Exception {
        Path temporary = Files.createTempDirectory("debug-journal-alias-");
        Path physicalRoot = temporary.resolve("physical-private");
        Path privateRoot = physicalRoot.resolve("no_backup");
        Path alias = temporary.resolve("context-private-alias");
        Path journal = privateRoot.resolve("program-debug/events.log");
        Path sentinel = temporary.resolve("outside-private-sentinel.txt");
        byte[] sentinelBytes =
                "private fixture bytes must remain untouched".getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(privateRoot);
            Files.createSymbolicLink(alias, physicalRoot);
            File aliasedRoot = alias.resolve("no_backup").toFile();
            Context aliasedContext =
                    new ContextWrapper(context) {
                        @Override
                        public File getNoBackupFilesDir() {
                            return aliasedRoot;
                        }
                    };
            Context physicalContext =
                    new ContextWrapper(context) {
                        @Override
                        public File getNoBackupFilesDir() {
                            return privateRoot.toFile();
                        }
                    };
            assertNotEquals(aliasedRoot.getAbsoluteFile(), aliasedRoot.getCanonicalFile());
            DebugJournal.record(aliasedContext, "worker_started", "engine=hashes count=1");
            DebugJournal.Snapshot physical = DebugJournal.read(physicalContext);
            assertTrue(physical.status, physical.available);
            assertEquals(1, physical.entries.size());
            assertEquals("worker_started", physical.entries.get(0).event);
            assertTrue(Files.isRegularFile(journal));
            assertEquals(
                    journal.toFile().getCanonicalFile(),
                    new File(aliasedRoot, "program-debug/events.log").getCanonicalFile());
            DebugJournal.record(physicalContext, "worker_finished", "engine=hashes ok=true");
            DebugJournal.Snapshot aliased = DebugJournal.read(aliasedContext);
            assertTrue(aliased.status, aliased.available);
            assertEquals(2, aliased.entries.size());
            assertEquals("worker_finished", aliased.entries.get(0).event);
            assertFalse(aliased.text().contains(temporary.toString()));
            assertTrue(DebugJournal.clear(aliasedContext));
            assertFalse(Files.exists(journal));
            assertTrue(DebugJournal.read(physicalContext).entries.isEmpty());

            Files.write(sentinel, sentinelBytes);
            Files.createSymbolicLink(journal, sentinel);
            DebugJournal.record(aliasedContext, "refused_link", "engine=hashes");
            assertFalse(DebugJournal.read(aliasedContext).available);
            assertFalse(DebugJournal.clear(aliasedContext));
            assertTrue(Files.isSymbolicLink(journal));
            assertArrayEquals(sentinelBytes, Files.readAllBytes(sentinel));
            Files.delete(journal);
            DebugJournal.record(aliasedContext, "worker_recovered", "engine=hashes");
            DebugJournal.Snapshot recovered = DebugJournal.read(physicalContext);
            assertTrue(recovered.status, recovered.available);
            assertEquals(1, recovered.entries.size());
            assertEquals("worker_recovered", recovered.entries.get(0).event);
            assertFalse(
                    recovered.text().contains(new String(sentinelBytes, StandardCharsets.UTF_8)));
        } finally {
            // Files.walk does not follow symbolic links, including the trusted ancestor alias.
            try (var paths = Files.walk(temporary)) {
                var ordered = paths.sorted(Comparator.reverseOrder()).iterator();
                while (ordered.hasNext()) Files.deleteIfExists(ordered.next());
            }
        }
    }

    @Test
    public void debugCopyClearAndExportUseTheDisplayedSnapshotAcrossRecreation() throws Exception {
        DebugJournal.record(context, "worker_started", "engine=hashes count=1");
        openLog();
        ListView rows = activity.getWindow().getDecorView().findViewWithTag("debug-rows");
        assertEquals(1, rows.getAdapter().getCount());
        // Real overflow/fit thumb gestures are covered in WorkbenchControlsTest.
        button("debug-copy").performClick();
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        String copied = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
        assertTrue(copied.contains("worker_started"));
        assertTrue(copied.contains(BuildConfig.VERSION_NAME));
        assertTrue(copied.contains(BuildConfig.SOURCE_REVISION));
        PreparedExport staged = prepare();
        byte[] expected = Files.readAllBytes(staged.file.toPath());
        assertEquals(copied, new String(expected, StandardCharsets.UTF_8));
        assertFalse(button("debug-copy").isEnabled());
        assertTrue(DebugJournal.clear(context));
        DebugJournal.record(context, "later_event", "engine=archive count=2");
        controller.recreate();
        activity = controller.get();
        settle();
        PreparedExport restored = ReflectionHelpers.getField(activity, "prepared");
        assertEquals(staged.file, restored.file);
        Uri destination = Uri.parse("content://org.diemobile.test/debug-log");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle();
        assertArrayEquals(expected, output.toByteArray());
        assertTrue(status(), status().contains("saved"));
        assertFalse(staged.file.exists());
        assertFalse(
                new String(output.toByteArray(), StandardCharsets.UTF_8).contains("later_event"));
        button("debug-refresh").performClick();
        settle();
        assertEquals(
                "later_event",
                ReflectionHelpers.<DebugJournal.Snapshot>getField(activity, "snapshot")
                        .entries
                        .get(0)
                        .event);
        button("debug-clear").performClick();
        settle();
        assertTrue(DebugJournal.read(context).entries.isEmpty());
        assertTrue(status(), status().contains("cleared"));
    }

    @Test
    public void cancelledAndFailedExportsDiscardStagesAndAllowRetry() throws Exception {
        DebugJournal.record(context, "worker_finished", "engine=hashes ok=true");
        openLog();
        PreparedExport cancelled = prepare();
        activity.onActivityResult(1, Activity.RESULT_CANCELED, null);
        settle();
        assertFalse(cancelled.file.exists());
        assertTrue(button("debug-export").isEnabled());
        PreparedExport failed = prepare();
        Uri destination = Uri.parse("content://org.diemobile.test/debug-log-failed");
        Shadows.shadowOf(activity.getContentResolver())
                .registerOutputStream(
                        destination,
                        new OutputStream() {
                            @Override
                            public void write(int value) throws IOException {
                                throw new IOException("Destination refused bytes");
                            }
                        });
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle();
        assertFalse(failed.file.exists());
        assertTrue(status(), status().contains("partial data"));
        assertTrue(button("debug-export").isEnabled());
        assertEquals(1, DebugJournal.read(context).entries.size());
    }

    @Test
    public void aboutUsesActualBuildIdentityAndOnlyExplicitFixedExternalLinks() throws Exception {
        ActivityController<AboutActivity> aboutController =
                Robolectric.buildActivity(AboutActivity.class).setup().visible();
        AboutActivity about = aboutController.get();
        try {
            String details = AboutActivity.buildDetails();
            assertTrue(details.contains(BuildConfig.VERSION_NAME));
            assertTrue(details.contains("Version code: " + BuildConfig.VERSION_CODE));
            assertTrue(details.contains("Source revision: " + BuildConfig.SOURCE_REVISION));
            assertTrue(details.endsWith(Boolean.toString(BuildConfig.SOURCE_DIRTY)));
            assertNull(Shadows.shadowOf(about).getNextStartedActivity());
            ((Button) about.getWindow().getDecorView().findViewWithTag("about-license"))
                    .performClick();
            AlertDialog license = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(
                    allText(license.getWindow().getDecorView())
                            .contains("Version 3, 29 June 2007"));
            license.dismiss();
            String[] tags = {"about-source", "about-releases", "about-docs"};
            String[] urls = {
                AboutActivity.PROJECT_URL,
                AboutActivity.RELEASES_URL,
                AboutActivity.DOCUMENTATION_URL
            };
            for (int i = 0; i < tags.length; i++) {
                ((Button) about.getWindow().getDecorView().findViewWithTag(tags[i])).performClick();
                Intent chooser = Shadows.shadowOf(about).getNextStartedActivity();
                assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
                Intent browser = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
                assertEquals(Intent.ACTION_VIEW, browser.getAction());
                assertEquals(urls[i], browser.getDataString());
                assertEquals("https", browser.getData().getScheme());
                assertEquals("github.com", browser.getData().getHost());
                assertNull(browser.getData().getQuery());
            }
            ((Button) about.getWindow().getDecorView().findViewWithTag("about-debug"))
                    .performClick();
            assertEquals(
                    new ComponentName(about, DebugLogActivity.class),
                    Shadows.shadowOf(about).getNextStartedActivity().getComponent());
            PackageManager manager = context.getPackageManager();
            for (Class<?> type : new Class<?>[] {AboutActivity.class, DebugLogActivity.class})
                assertFalse(manager.getActivityInfo(new ComponentName(context, type), 0).exported);
            PackageInfo app =
                    manager.getPackageInfo(
                            context.getPackageName(), PackageManager.GET_PERMISSIONS);
            assertFalse(
                    Arrays.asList(
                                    app.requestedPermissions == null
                                            ? new String[0]
                                            : app.requestedPermissions)
                            .contains("android.permission.INTERNET"));
        } finally {
            aboutController.pause().stop().destroy();
        }
    }

    private static String allText(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                text.append(allText(group.getChildAt(i)));
        }
        return text.toString();
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.*;

/** Actual MainActivity/CDiE and persisted presentation state; no analysis data is mocked. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class ContentPresentationWorkbenchTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private Path temporary;
    private File input;

    @Before
    public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("workbench-presentation", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        ScanPreferences.save(context, new ScanOptions(ScanOptions.DEFAULT_FLAGS, 4, "", ""));
        temporary = Files.createTempDirectory("die-content-presentation");
        input = temporary.resolve("mislabeled.dat").toFile();
        Files.copy(Path.of(System.getProperty("die.fixture.dir"), "elf-fixture"), input.toPath());
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        scan(input);
        settle();
    }

    @After
    public void cleanup() throws Exception {
        if (controller != null) {
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            controller.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        }
        if (temporary != null)
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    private void scan(File file) {
        ReflectionHelpers.callInstanceMethod(
                activity,
                "scan",
                ReflectionHelpers.ClassParameter.from(Uri.class, Uri.fromFile(file)));
    }

    private void settle() throws Exception {
        for (int i = 0; i < 8; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(45, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!ReflectionHelpers.<Boolean>getField(activity, "busy")) return;
        }
        fail("Content presentation did not settle");
    }

    private void page(int page) {
        ReflectionHelpers.setField(activity, "page", page);
        ReflectionHelpers.callInstanceMethod(activity, "show");
    }

    private View tagged(String value) {
        return activity.getWindow().getDecorView().findViewWithTag(value);
    }

    private void choose(int index) {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null, index, index);
    }

    @Test
    public void selectedApkKnowledgeRouteKeepsCapturedInputIdentityAndExplicitLookupIntent()
            throws Exception {
        page(3);
        Button lookup = (Button) tagged("apk-community-lookup");
        assertNotNull(lookup);
        lookup.performClick();
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        Inspector.Result captured = ReflectionHelpers.getField(activity, "result");
        File source = ReflectionHelpers.getField(activity, "retainedFile");
        assertEquals("package-set", intent.getStringExtra("provider"));
        assertEquals(source.getAbsolutePath(), intent.getStringExtra("path"));
        assertEquals(captured.sha256, intent.getStringExtra("sha256"));
        assertEquals(captured.size, intent.getLongExtra("sourceSize", -1));
        assertTrue(intent.getBooleanExtra("openCapturedPackageKnowledge", false));
    }

    @Test
    public void identityPrecedesGroupedToolsAndTheGuideIsPresentedOnlyOnce() throws Exception {
        page(1);
        LinearLayout pane = ReflectionHelpers.getField(activity, "pane");
        assertEquals("File type", ((TextView) pane.getChildAt(0)).getText().toString());
        assertTrue(((TextView) pane.getChildAt(1)).getText().toString().startsWith("ELF"));
        LinearLayout tabs = ReflectionHelpers.getField(activity, "tabs");
        for (int i = 0; i < tabs.getChildCount(); i++)
            assertNotEquals("Reference", ((Button) tabs.getChildAt(i)).getText().toString());
        assertEquals(View.VISIBLE, tagged("tool-group:general").getVisibility());
        assertEquals(View.GONE, tagged("tool-group:executable").getVisibility());
        tagged("tool-group-toggle:executable").performClick();
        assertEquals(View.VISIBLE, tagged("tool-group:executable").getVisibility());
        tabs.findViewWithTag(8).performClick();
        Intent gallery = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(GallerizeActivity.class.getName(), gallery.getComponent().getClassName());
        assertEquals(
                ReflectionHelpers.<Inspector.Result>getField(activity, "result").sha256,
                gallery.getStringExtra("sha256"));
        // A restored old Reference page resolves to the same File info view.
        page(2);
        assertEquals(1, ReflectionHelpers.<Integer>getField(activity, "page").intValue());
        assertEquals("File type", ((TextView) pane.getChildAt(0)).getText().toString());
    }

    @Test
    public void definitionsCollapseIndividuallyAndGloballyWithoutChangingEvidenceOrToolGroups()
            throws Exception {
        page(1);
        String sha = ReflectionHelpers.<Inspector.Result>getField(activity, "result").sha256;
        View why = tagged("explanation:main.1.Layout");
        assertEquals(View.GONE, why.getVisibility());
        tagged("explanation-options").performClick();
        ShadowPopupMenu.getLatestPopupMenu().getMenu()
                .performIdentifierAction(ExpandableHelp.EXPAND_ALL, 0);
        assertEquals(View.VISIBLE, why.getVisibility());
        assertEquals(View.GONE, tagged("tool-group:executable").getVisibility());
        tagged("explanation-options").performClick();
        ShadowPopupMenu.getLatestPopupMenu().getMenu()
                .performIdentifierAction(ExpandableHelp.COLLAPSE_ALL, 0);
        assertEquals(View.GONE, why.getVisibility());
        // Individual overrides and the global setting survive an actual Main recreation.
        ExpandableHelp help = ReflectionHelpers.getField(activity, "explanations");
        help.setAll(true);
        controller.recreate();
        activity = controller.get();
        settle();
        page(1);
        assertEquals(View.VISIBLE, tagged("explanation:main.1.Layout").getVisibility());
        assertEquals(sha, ReflectionHelpers.<Inspector.Result>getField(activity, "result").sha256);
        assertTrue(
                ScanPreferences.load(activity)
                        .sameAs(new ScanOptions(ScanOptions.DEFAULT_FLAGS, 4, "", "")));
    }

    @Test
    public void groupedMenuRetainsExactNativeActionsAndRejectsAnExpiredSource() throws Exception {
        ReflectionHelpers.<Button>getField(activity, "nativeTools").performClick();
        assertEquals(
                ToolCatalog.GROUPS.get(0).label,
                ShadowAlertDialog.getLatestAlertDialog()
                        .getListView()
                        .getAdapter()
                        .getItem(0)
                        .toString());
        choose(0);
        choose(0);
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(NativeToolsActivity.class.getName(), target.getComponent().getClassName());
        assertEquals(9, target.getIntExtra("tool", -1));
        assertEquals(
                ReflectionHelpers.<Inspector.Result>getField(activity, "result").sha256,
                target.getStringExtra("sha256"));
        ReflectionHelpers.<Button>getField(activity, "nativeTools").performClick();
        AlertDialog stale = ShadowAlertDialog.getLatestAlertDialog();
        File changed = temporary.resolve("changed.bin").toFile();
        Files.write(changed.toPath(), new byte[] {1, 2, 3});
        scan(changed);
        stale.getListView().performItemClick(null, 0, 0);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        settle();
    }

    @Test
    public void mainArchiveActionAcceptsMislabeledTarAndScansAnIndependentMemberHandoff()
            throws Exception {
        File archive = temporary.resolve("chosen-as-image.jpeg").toFile();
        Files.copy(
                Path.of(System.getProperty("die.archive.fixture.dir"), "mislabeled.bin"),
                archive.toPath());
        scan(archive);
        settle();
        Inspector.Result parent = ReflectionHelpers.getField(activity, "result");
        assertNotNull(parent);
        assertFalse(parent.type, parent.type.contains("ZIP"));
        Button archives = ReflectionHelpers.getField(activity, "archiveTools");
        assertTrue(archives.isEnabled());
        archives.performClick();
        var request = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(request);
        assertEquals(5, request.requestCode);
        assertEquals(
                NativeArchiveExplorerActivity.class.getName(),
                request.intent.getComponent().getClassName());
        // The native browser's indexed result is separately checked with actual libarchive JNI.
        // This independent known payload exercises the main scanner's result/ownership boundary.
        byte[] payload =
                "Exact archive member 世界 🔬 with embedded NUL\0 and end.\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        File directory = new File(activity.getCacheDir(), "archive-snapshots");
        directory.mkdirs();
        File member = File.createTempFile("archive-member-", ".snapshot", directory);
        Files.write(member.toPath(), payload);
        String sha =
                Inspector.toHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload));
        activity.onActivityResult(
                5,
                Activity.RESULT_OK,
                new Intent()
                        .putExtra("path", member.getAbsolutePath())
                        .putExtra("display", "chosen-as-image.jpeg!/folder/世界.txt")
                        .putExtra("memberSha256", sha));
        settle();
        Inspector.Result analyzed = ReflectionHelpers.getField(activity, "result");
        assertNotNull(analyzed);
        assertEquals(sha, analyzed.sha256);
        assertEquals(payload.length, analyzed.size);
        File captured = ReflectionHelpers.getField(activity, "retainedFile");
        assertArrayEquals(payload, Files.readAllBytes(captured.toPath()));
        assertTrue(ReflectionHelpers.<String>getField(activity, "name").contains("世界.txt"));
        ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
        activity.finish();
        controller.pause().stop().destroy();
        controller = null;
        assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        assertFalse(member.exists());
        assertFalse(captured.exists());
    }
}

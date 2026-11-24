// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.*;

/** Primary controls and reports with actual file acquisition/CDiE, not scanner mocks. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class PrimaryScanTargetTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private Path temporary;
    private File input;

    @Before
    public void setup() throws Exception {
        assertTrue(Engine.AVAILABLE);
        temporary = Files.createTempDirectory("die-primary-target");
        input = temporary.resolve("elf-fixture").toFile();
        Files.copy(Path.of(System.getProperty("die.fixture.dir"), "elf-fixture"), input.toPath());
        ScanPreferences.save(
                RuntimeEnvironment.getApplication(),
                new ScanOptions(ScanOptions.DEFAULT_FLAGS, 4, "", ""));
        controller =
                Robolectric.buildActivity(
                                MainActivity.class,
                                new Intent(RuntimeEnvironment.getApplication(), MainActivity.class))
                        .setup()
                        .visible();
        activity = controller.get();
        ReflectionHelpers.callInstanceMethod(
                activity,
                "scan",
                ReflectionHelpers.ClassParameter.from(Uri.class, Uri.fromFile(input)));
        settle();
        assertTrue(
                ReflectionHelpers.<String>getField(activity, "error"),
                ReflectionHelpers.<String>getField(activity, "error").isEmpty());
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
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(p);
            }
    }

    private void scanCompressedFixture() throws Exception {
        File compressed = temporary.resolve("L0_Video.dd.bz2").toFile();
        Files.copy(
                Path.of(System.getProperty("die.archive.fixture.dir"), "raw.bz2"),
                compressed.toPath());
        ReflectionHelpers.callInstanceMethod(
                activity,
                "scan",
                ReflectionHelpers.ClassParameter.from(Uri.class, Uri.fromFile(compressed)));
        settle();
        assertTrue(
                ReflectionHelpers.<String>getField(activity, "error"),
                ReflectionHelpers.<String>getField(activity, "error").isEmpty());
    }

    @Test
    public void bzipHeaderShowsImmediateSourceBoundBrowseDecompressAction() throws Exception {
        scanCompressedFixture();
        Inspector.Result captured = ReflectionHelpers.getField(activity, "result");
        assertEquals("bzip2 compressed stream", captured.type);
        Button contents =
                activity.getWindow().getDecorView().findViewWithTag("main-open-archive-contents");
        assertNotNull("Archive discovery was buried behind generic Binary facts", contents);
        contents.performClick();
        Intent request = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(request);
        assertEquals(
                NativeArchiveExplorerActivity.class.getName(),
                request.getComponent().getClassName());
        assertEquals(captured.sha256, request.getStringExtra("sha256"));
        assertEquals(captured.size, request.getLongExtra("sourceSize", -1));
        assertEquals(
                ReflectionHelpers.<File>getField(activity, "retainedFile").getAbsolutePath(),
                request.getStringExtra("path"));
    }

    @Test
    public void actualBzipDetectionDialogOpensItsCapturedSourceDespiteLaterUiChanges()
            throws Exception {
        scanCompressedFixture();
        ScanEvidence evidence = ReflectionHelpers.getField(activity, "evidence");
        ScanEvidence.Row archive =
                evidence.rows.stream()
                        .filter(row -> "Archive".equalsIgnoreCase(row.category))
                        .findFirst()
                        .orElseThrow();
        Button record =
                activity.getWindow()
                        .getDecorView()
                        .findViewWithTag("detection-evidence-" + archive.index);
        assertNotNull(record);
        record.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        Button browse = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        assertNotNull("Archive detection offered no next action", browse);
        assertEquals("Browse / extract", browse.getText().toString());
        File captured = ReflectionHelpers.getField(activity, "retainedFile");
        ReflectionHelpers.setField(
                activity, "retainedFile", new File(temporary.toFile(), "other-input.bin"));
        try {
            browse.performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Intent request = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(request);
            assertEquals(captured.getAbsolutePath(), request.getStringExtra("path"));
        } finally {
            ReflectionHelpers.setField(activity, "retainedFile", captured);
        }
    }

    private void settle() throws Exception {
        for (int i = 0; i < 8; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(45, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!ReflectionHelpers.<Boolean>getField(activity, "busy")
                    && !ReflectionHelpers.<Boolean>getField(activity, "preparingReport")
                    && !ReflectionHelpers.<Boolean>getField(activity, "writingReport")) return;
        }
        fail("Primary worker did not settle");
    }

    private void choose(String name) {
        ReflectionHelpers.callInstanceMethod(activity, "chooseInterpretation");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ListView choices = dialog.getListView();
        assertNotNull(choices);
        assertTrue("Compatible choices must actually be shown", choices.isShown());
        for (int i = 0; i < choices.getAdapter().getCount(); i++)
            if (choices.getAdapter().getItem(i).toString().startsWith(name + " •")) {
                choices.performItemClick(null, i, i);
                return;
            }
        fail("Missing compatible choice " + name);
    }

    private void rescan() throws Exception {
        ReflectionHelpers.<Button>getField(activity, "rescan").performClick();
        settle();
    }

    private JSONObject evidence() throws Exception {
        return ReflectionHelpers.<ScanEvidence>getField(activity, "evidence").toJson();
    }

    @Test
    public void explicitPrimaryPassAndPendingReportKeepTheirCapturedSourceAndSelector()
            throws Exception {
        assertEquals(0, evidence().getInt("typeId"));
        choose("Binary");
        assertTrue(ReflectionHelpers.<Boolean>getField(activity, "settingsDirty"));
        assertEquals(0, evidence().getInt("typeId"));
        rescan();
        int selector = evidence().getInt("typeId");
        assertNotEquals(0, selector);
        ScanTarget.Capture captured = ReflectionHelpers.getField(activity, "scannedTarget");
        assertEquals(Integer.valueOf(selector), captured.nativeTypeId);
        assertEquals("Binary", captured.request.formatName);
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "settingsDirty"));
        ReflectionHelpers.<Button>getField(activity, "export").performClick();
        settle();
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        choose("Automatic");
        assertTrue(ReflectionHelpers.<Boolean>getField(activity, "settingsDirty"));
        controller.recreate();
        activity = controller.get();
        settle();
        Uri destination = Uri.parse("content://org.diemobile.test/target-report");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, bytes);
        activity.onActivityResult(2, Activity.RESULT_OK, new Intent().setData(destination));
        settle();
        JSONObject report = new JSONObject(bytes.toString("UTF-8"));
        assertEquals(selector, report.getJSONObject("options").getInt("typeId"));
        assertEquals(selector, report.getJSONObject("scan").getInt("typeId"));
        JSONObject target = report.getJSONObject("scanTarget");
        assertEquals("Binary", target.getJSONObject("request").getString("formatName"));
        assertEquals(selector, target.getInt("resolvedTypeId"));
        assertEquals(
                report.getJSONObject("source").getString("sha256"),
                target.getJSONObject("source").getString("sha256"));
        assertTrue(target.getJSONObject("compatibleFormats").getJSONArray("types").length() > 1);
        assertTrue(
                ReflectionHelpers.<ScanTarget.Request>getField(activity, "scanTarget").automatic());
        rescan();
        assertEquals(0, evidence().getInt("typeId"));
    }

    @Test
    public void requestRestoresByNameAndNewInputDefaultsToAutomatic() throws Exception {
        choose("Binary");
        rescan();
        controller.recreate();
        activity = controller.get();
        settle();
        assertEquals(
                "Binary",
                ReflectionHelpers.<ScanTarget.Request>getField(activity, "scanTarget").formatName);
        assertNotEquals(0, evidence().getInt("typeId"));
        Path different = temporary.resolve("pe32.exe");
        Files.copy(Path.of(System.getProperty("die.fixture.dir"), "pe32.exe"), different);
        ReflectionHelpers.callInstanceMethod(
                activity,
                "scan",
                ReflectionHelpers.ClassParameter.from(Uri.class, Uri.fromFile(different.toFile())));
        settle();
        assertTrue(
                ReflectionHelpers.<ScanTarget.Request>getField(activity, "scanTarget").automatic());
        assertEquals(0, evidence().getInt("typeId"));
    }

    @Test
    public void staleChoiceCannotRetargetNewInputAndUnresolvedNameCanRecover() throws Exception {
        ReflectionHelpers.callInstanceMethod(activity, "chooseInterpretation");
        AlertDialog stale = ShadowAlertDialog.getLatestAlertDialog();
        ListView choices = stale.getListView();
        int binary = -1;
        for (int i = 0; i < choices.getAdapter().getCount(); i++)
            if (choices.getAdapter().getItem(i).toString().startsWith("Binary •")) binary = i;
        assertTrue(binary >= 0);
        Path different = temporary.resolve("pe64.exe");
        Files.copy(Path.of(System.getProperty("die.fixture.dir"), "pe64.exe"), different);
        synchronized (Engine.class) {
            ReflectionHelpers.callInstanceMethod(
                    activity,
                    "scan",
                    ReflectionHelpers.ClassParameter.from(
                            Uri.class, Uri.fromFile(different.toFile())));
            choices.performItemClick(null, binary, binary);
            assertTrue(
                    ReflectionHelpers.<ScanTarget.Request>getField(activity, "scanTarget")
                            .automatic());
        }
        settle();
        assertEquals(0, evidence().getInt("typeId"));
        ReflectionHelpers.setField(
                activity, "scanTarget", new ScanTarget.Request("missing-interpretation"));
        rescan();
        ScanTarget.Capture unresolved = ReflectionHelpers.getField(activity, "scannedTarget");
        assertNull(unresolved.nativeTypeId);
        assertTrue(unresolved.error.contains("not available"));
        assertNull(ReflectionHelpers.getField(activity, "evidence"));
        assertTrue(ReflectionHelpers.<String>getField(activity, "error").contains("not available"));
        ReflectionHelpers.<Button>getField(activity, "export").performClick();
        settle();
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        Uri destination = Uri.parse("content://org.diemobile.test/unresolved-target");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(2, Activity.RESULT_OK, new Intent().setData(destination));
        settle();
        JSONObject failedReport = new JSONObject(output.toString("UTF-8"));
        assertTrue(failedReport.getJSONObject("options").isNull("typeId"));
        assertEquals(
                "missing-interpretation",
                failedReport
                        .getJSONObject("scanTarget")
                        .getJSONObject("request")
                        .getString("formatName"));
        assertFalse(failedReport.getJSONObject("scan").getBoolean("ok"));
        choose("Automatic");
        rescan();
        assertEquals(0, evidence().getInt("typeId"));
        assertTrue(ReflectionHelpers.<String>getField(activity, "error").isEmpty());
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Primary Strings controls feed the actual whole-input classifier without relabeling old output.
 */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class StringsWorkflowWorkbenchTest {
    private ActivityController<MainActivity> main;
    private MainActivity activity;
    private ActivityController<InvestigationActivity> browser;
    private InvestigationActivity investigation;
    private Path temporary;
    private File input;
    private ScanOptions original;

    @Before
    public void open() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("workbench-presentation", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        original = new ScanOptions(ScanOptions.DEFAULT_FLAGS, 4, "original-tag", "exclude-tag");
        ScanPreferences.save(context, original);
        temporary = Files.createTempDirectory("die-strings-workflow");
        input = temporary.resolve("strings.bin").toFile();
        Files.write(
                input.toPath(),
                ("short\0ordinary fixture text\0" + "https://target.fixture.invalid/世界😀\0")
                        .getBytes(StandardCharsets.UTF_8));
        main = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = main.get();
    }

    @After
    public void close() throws Exception {
        if (browser != null) {
            ExecutorService worker = ReflectionHelpers.getField(investigation, "worker");
            browser.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        }
        if (main != null) {
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            main.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        }
        ScanPreferences.save(
                RuntimeEnvironment.getApplication(),
                new ScanOptions(ScanOptions.DEFAULT_FLAGS, 4, "", ""));
        if (temporary != null)
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    private void stringsPage() {
        ReflectionHelpers.setField(activity, "page", 5);
        ReflectionHelpers.callInstanceMethod(activity, "show");
    }

    private <T extends View> T tagged(String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    private void capturedPreview() throws Exception {
        ReflectionHelpers.setField(activity, "result", Inspector.scan(input, 4));
        ReflectionHelpers.setField(activity, "retainedFile", input);
        ReflectionHelpers.setField(activity, "scannedOptions", original);
        ReflectionHelpers.setField(activity, "scannedMin", 4);
        ReflectionHelpers.setField(activity, "name", input.getName());
        stringsPage();
    }

    private void settleBrowser() throws Exception {
        for (int i = 0; i < 8; i++) {
            ReflectionHelpers.<ExecutorService>getField(investigation, "worker")
                    .submit(() -> {})
                    .get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!ReflectionHelpers.<Boolean>getField(investigation, "busy")) return;
        }
        fail("String browser did not settle");
    }

    @Test
    public void primaryMinimumAndFilterReachCompleteListAndRemainCapturedAfterRecreation()
            throws Exception {
        capturedPreview();
        Inspector.Result captured = ReflectionHelpers.getField(activity, "result");
        String originalPreview = captured.strings;
        this.<EditText>tagged("strings-filter").setText("TARGET");
        assertTrue(
                this.<TextView>tagged("strings-preview-count")
                        .getText()
                        .toString()
                        .contains("1 of 3 lines"));
        assertTrue(
                this.<TextView>tagged("strings-preview")
                        .getText()
                        .toString()
                        .contains("target.fixture.invalid"));
        assertFalse(
                this.<TextView>tagged("strings-preview")
                        .getText()
                        .toString()
                        .contains("ordinary fixture"));
        this.<EditText>tagged("strings-minimum").setText("10");
        this.<Button>tagged("strings-apply-minimum").performClick();
        assertEquals(10, ScanPreferences.load(activity).minimumStringLength);
        assertEquals(original.includeTags, ScanPreferences.load(activity).includeTags);
        assertEquals(original.excludeTags, ScanPreferences.load(activity).excludeTags);
        assertTrue(original.sameAs(ReflectionHelpers.getField(activity, "scannedOptions")));
        assertEquals(originalPreview, captured.strings);
        assertTrue(ReflectionHelpers.<Boolean>getField(activity, "settingsDirty"));
        assertEquals("TARGET", this.<EditText>tagged("strings-filter").getText().toString());
        this.<Button>tagged("strings-browse").performClick();
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("all", intent.getStringExtra("stringInitialView"));
        assertEquals("TARGET", intent.getStringExtra("stringFilter"));
        assertEquals(10, intent.getIntExtra("stringMinimum", -1));
        assertEquals(captured.sha256, intent.getStringExtra("sha256"));
        assertEquals(captured.size, intent.getLongExtra("sourceSize", -1));
        browser = Robolectric.buildActivity(InvestigationActivity.class, intent).setup().visible();
        investigation = browser.get();
        settleBrowser();
        assertEquals(
                "Categories",
                ReflectionHelpers.<Button>getField(investigation, "views").getText().toString());
        assertEquals(
                "strings-all",
                ReflectionHelpers.<EvidenceRows>getField(investigation, "source").kind);
        JSONObject metadata =
                new JSONObject(ReflectionHelpers.<String>getField(investigation, "metadata"));
        assertEquals(10, metadata.getInt("minimumUnicodeCodePoints"));
        assertEquals(captured.sha256, metadata.getString("sourceSha256"));
        ListView records = investigation.findViewById(R.id.investigation_records);
        assertTrue(records.getAdapter().getCount() > 0);
        for (int i = 0; i < records.getAdapter().getCount(); i++)
            assertTrue(
                    ((EvidenceRows.Row) records.getAdapter().getItem(i))
                            .name
                            .toLowerCase(Locale.ROOT)
                            .contains("target"));
        // A later profile/preference edit cannot relabel this captured classifier request.
        ScanPreferences.save(activity, original);
        browser.recreate();
        investigation = browser.get();
        settleBrowser();
        metadata = new JSONObject(ReflectionHelpers.<String>getField(investigation, "metadata"));
        assertEquals(10, metadata.getInt("minimumUnicodeCodePoints"));
        assertEquals(
                "TARGET",
                ((EditText) investigation.findViewById(R.id.investigation_filter))
                        .getText()
                        .toString());
        ReflectionHelpers.<Button>getField(investigation, "views").performClick();
        assertEquals(
                "String categories",
                Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());
    }

    @Test
    public void minimumIsAvailableBeforeFileSelectionAndRejectsInvalidValues() {
        stringsPage();
        EditText minimum = tagged("strings-minimum");
        for (String invalid : new String[] {"", "2", "65", "not-a-number"}) {
            minimum.setText(invalid);
            this.<Button>tagged("strings-apply-minimum").performClick();
            assertNotNull(minimum.getError());
            assertTrue(original.sameAs(ScanPreferences.load(activity)));
        }
        minimum.setText("12");
        this.<Button>tagged("strings-apply-minimum").performClick();
        assertEquals(12, ScanPreferences.load(activity).minimumStringLength);
        // Returning from a profile page refreshes the visible editor as well as the shared flags.
        ScanPreferences.save(activity, original.withBasicFlags(original.flags, 9));
        main.pause().stop().start().resume().visible();
        assertEquals("9", this.<EditText>tagged("strings-minimum").getText().toString());
    }
}

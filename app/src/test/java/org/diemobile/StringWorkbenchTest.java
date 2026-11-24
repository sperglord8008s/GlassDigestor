// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;
import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

/** Actual classifier and byte scanning through the shared Android viewer. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class StringWorkbenchTest {
    private ActivityController<InvestigationActivity> controller;
    private InvestigationActivity activity;
    private Path temporary;
    private File input;
    private static final String LE = "https://utf16.fixture.invalid/研究😀";
    @Before public void open() throws Exception {
        temporary = Files.createTempDirectory("die-string-ui"); ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int i = 0; i < 71; i++) { data.write(String.format(Locale.ROOT,"https://fixture-%03d.invalid/api/v1",i).getBytes(StandardCharsets.UTF_8)); data.write(0); }
        data.write("é😀 https://unicode.fixture.invalid/\0ptrace CVE-2024-12345 strcpy\0client_secret=EXAMPLE_ONLY_VALUE\0ordinary fixture text\0".getBytes(StandardCharsets.UTF_8));
        data.write(("many-evasion " + "ptrace ".repeat(20) + "\0").getBytes(StandardCharsets.UTF_8));
        data.write(new byte[4]); data.write(LE.getBytes(StandardCharsets.UTF_16LE)); data.write(new byte[4]); input = temporary.resolve("mixed.bin").toFile(); Files.write(input.toPath(),data.toByteArray());
        Intent intent = new Intent(RuntimeEnvironment.getApplication(),InvestigationActivity.class).putExtra("provider","strings").putExtra("path",input.getAbsolutePath()).putExtra("name","mixed.bin");
        controller = Robolectric.buildActivity(InvestigationActivity.class,intent).setup().visible(); activity = controller.get(); settle();
    }
    @After public void close() throws Exception {
        if (controller != null) { ExecutorService worker = ReflectionHelpers.getField(activity,"worker"); controller.pause().stop().destroy(); assertTrue(worker.awaitTermination(30,TimeUnit.SECONDS)); }
        if (temporary != null) try (var paths = Files.walk(temporary)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
    private void settle() throws Exception {
        for (int i = 0; i < 8; i++) { ReflectionHelpers.<ExecutorService>getField(activity,"worker").submit(() -> {}).get(30,TimeUnit.SECONDS); Shadows.shadowOf(Looper.getMainLooper()).idle(); if (!ReflectionHelpers.<Boolean>getField(activity,"busy")) return; }
        fail("String worker did not settle: " + status());
    }
    private Button button(String field) { return ReflectionHelpers.getField(activity,field); }
    private ListView records() { return activity.findViewById(R.id.investigation_records); }
    private EditText filter() { return activity.findViewById(R.id.investigation_filter); }
    private String status() { return ((TextView)activity.findViewById(R.id.investigation_status)).getText().toString(); }
    private EvidenceRows.Row row(int i) { return (EvidenceRows.Row)records().getAdapter().getItem(i); }
    private void choose(String label) {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); ListView list = dialog.getListView();
        for (int i = 0; i < list.getAdapter().getCount(); i++) if (list.getAdapter().getItem(i).toString().contains(label)) { list.performItemClick(null,i,i); dialog.dismiss(); return; } fail("Missing choice " + label);
    }
    private void root(String label) throws Exception { button("views").performClick(); choose(label); settle(); }
    private void action(int row, String label) throws Exception { records().performItemClick(null,row,row); ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); settle(); choose(label); settle(); }
    private byte[] finishExport() throws Exception {
        Uri destination = Uri.parse("content://org.diemobile.test/string-export"); ByteArrayOutputStream output = new ByteArrayOutputStream(); Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination,output);
        activity.onActivityResult(1,Activity.RESULT_OK,new Intent().setData(destination)); settle(); assertTrue(status(),status().contains("Export saved")); return output.toByteArray();
    }
    @Test public void groupsSearchFullScopeAndBackSurviveRecreation() throws Exception {
        root("URLs / URIs"); assertEquals(50,records().getAdapter().getCount()); button("next").performClick(); settle(); assertTrue(status(),status().contains("page 2/"));
        filter().setText("https://fixture-070.invalid/api/v1"); button("search").performClick(); settle(); assertEquals(1,records().getAdapter().getCount()); filter().setText("unapplied draft");
        action(0,"Group: Service / API endpoints"); controller.recreate(); activity = controller.get(); settle(); button("back").performClick(); settle();
        assertEquals("unapplied draft",filter().getText().toString()); assertEquals(1,records().getAdapter().getCount()); assertTrue(row(0).name.contains("fixture-070"));
    }
    @Test public void classifierCatalogAndRiskGroupsExposeEvidenceWithoutVerdicts() throws Exception {
        root("Vulnerability / exploit"); assertTrue(row(0).json().getString("value").contains("CVE-2024-12345")); JSONObject value = row(0).json();
        assertTrue(value.getJSONArray("classificationMatches").length() >= 3); root("Rule catalog"); assertEquals(StringClassifier.RULES.size(),ReflectionHelpers.<EvidenceRows>getField(activity,"source").size());
        JSONObject metadata = new JSONObject(ReflectionHelpers.<String>getField(activity,"metadata")); assertFalse(metadata.getBoolean("vulnerabilityVerified")); assertEquals(StringClassifier.RULES_SHA256,metadata.getString("rulesSha256"));
    }
    @Test public void unicodeMatchOffsetsAndExactUtf16RangeExportArePreserved() throws Exception {
        root("URLs / URIs"); filter().setText("utf16.fixture.invalid"); button("search").performClick(); settle();
        int selected = -1; for (int i = 0; i < records().getAdapter().getCount(); i++) if (row(i).json().getString("encoding").equals("UTF16LE") && row(i).name.equals(LE)) selected = i;
        assertTrue(selected >= 0); JSONObject fields = row(selected).json(); JSONArray matches = fields.getJSONArray("classificationMatches");
        for (int i = 0; i < matches.length(); i++) if (matches.getJSONObject(i).getString("ruleId").equals("net.uri")) assertEquals(fields.getLong("physicalTextOffset"),matches.getJSONObject(i).getLong("physicalMatchOffset"));
        action(selected,"Export exact stored range"); assertArrayEquals(LE.getBytes(StandardCharsets.UTF_16LE),finishExport());
    }
    @Test public void completeGroupExportSurvivesExpiredInputAndPickerRecreation() throws Exception {
        root("URLs / URIs"); int count = ReflectionHelpers.<EvidenceRows>getField(activity,"source").size(); filter().setText("fixture-070"); button("search").performClick(); settle(); button("export").performClick(); settle();
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult()); assertTrue(input.delete()); controller.recreate(); activity = controller.get(); settle();
        JSONObject report = new JSONObject(new String(finishExport(),StandardCharsets.UTF_8)); assertEquals(count,report.getJSONArray("rows").length()); assertEquals("fixture-070",report.getJSONObject("view").getString("filter"));
        assertEquals(StringClassifier.RULES_SHA256,report.getJSONObject("provenance").getString("rulesSha256")); assertEquals(5,report.getJSONObject("metadata").getInt("completeEncodingPasses"));
    }
    @Test public void hexHandoffOwnsIndependentBytesAcrossParentDestructionAndChildRecreation() throws Exception {
        root("URLs / URIs"); filter().setText("fixture-070"); button("search").performClick(); settle(); long offset = row(0).physicalOffset;
        action(0,"Hex at associated physical offset"); Intent intent = Shadows.shadowOf(activity).getNextStartedActivity(); assertNotNull(intent);
        File handed = new File(intent.getStringExtra("path")); assertTrue(handed.isFile()); assertTrue(intent.getBooleanExtra("ownedSnapshot",false)); assertEquals(offset,intent.getLongExtra("offset",-1));
        ExecutorService worker = ReflectionHelpers.getField(activity,"worker"); controller.pause().stop().destroy(); controller = null; assertTrue(worker.awaitTermination(30,TimeUnit.SECONDS)); assertTrue(handed.isFile());
        ActivityController<NativeToolsActivity> child = Robolectric.buildActivity(NativeToolsActivity.class,intent).setup().visible();
        try {
            ExecutorService childWorker = ReflectionHelpers.getField(child.get(),"worker"); childWorker.submit(() -> {}).get(30,TimeUnit.SECONDS); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(((TextView)child.get().findViewById(R.id.native_output)).getText().toString().contains("https"));
            child.recreate(); assertTrue(handed.isFile()); child.get().finish(); childWorker = ReflectionHelpers.getField(child.get(),"worker"); child.pause().stop().destroy(); assertTrue(childWorker.awaitTermination(30,TimeUnit.SECONDS)); assertFalse(handed.exists());
        } finally { handed.delete(); }
    }
    @Test public void cancellationReloadAndSpoolOwnershipRemainUsable() throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(activity,"worker"); CountDownLatch release = new CountDownLatch(1); worker.submit(() -> { try { release.await(30,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        try { button("reload").performClick(); button("cancel").performClick(); } finally { release.countDown(); } settle(); assertTrue(status(),status().contains("cancelled"));
        button("reload").performClick(); settle(); assertEquals(50,records().getAdapter().getCount());
        File directory = new File(activity.getCacheDir(),"string-snapshots"); assertEquals(2,Objects.requireNonNull(directory.list()).length);
        controller.pause().stop().destroy(); controller = null; assertTrue(worker.awaitTermination(30,TimeUnit.SECONDS)); assertEquals(0,Objects.requireNonNull(directory.list()).length); assertTrue(input.isFile());
    }

    @Test public void openedRiskCategoryShowsItsActualReasonBeforeRawJson() throws Exception {
        root("Vulnerability / exploit");
        EvidenceRows.Row captured = row(0);
        String encoded = captured.encoded;
        TextView displayed = (TextView) records().getAdapter().getView(0, null, records());
        assertTrue(displayed.getText().toString(), displayed.getText().toString().contains("Common Vulnerabilities and Exposures"));
        assertTrue(displayed.getText() instanceof android.text.Spanned);
        records().performItemClick(null, 0, 0);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        JSONObject fields = captured.json();
        int cve = -1;
        JSONArray matches = fields.getJSONArray("classificationMatches");
        for (int i = 0; i < matches.length(); i++) if (matches.getJSONObject(i).getString("ruleId").equals("vuln.cve")) cve = i;
        assertTrue(cve >= 0);
        TextView why = dialog.findViewById(android.R.id.content).findViewWithTag("string-why-" + cve);
        assertNotNull(why);
        assertEquals(android.view.View.VISIBLE, why.getVisibility());
        assertTrue(why.getText().toString().contains("does not show that this file contains"));
        Button whyToggle = dialog.findViewById(android.R.id.content).findViewWithTag("string-why-toggle-" + cve);
        assertTrue(whyToggle.getText().toString().contains("Why this classification?"));
        whyToggle.performClick(); assertEquals(android.view.View.GONE, why.getVisibility());
        whyToggle.performClick(); assertEquals(android.view.View.VISIBLE, why.getVisibility());
        assertFalse(new JSONObject(ReflectionHelpers.<String>getField(activity, "provenance")).getBoolean("vulnerabilityVerified"));
        assertEquals(encoded, captured.encoded);
        assertEquals(fields.getJSONArray("groups").length(), fields.getJSONArray("categoryRationales").length());
        assertFalse(matches.getJSONObject(cve).has("categoryRationales"));
    }

    @Test public void exactWholeAndMatchedCopyDoNotUseTheEscapedPreviewOrOpenABrowser() throws Exception {
        root("Vulnerability / exploit");
        EvidenceRows.Row captured = row(0); String encoded = captured.encoded;
        records().performItemClick(null, 0, 0);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        ((Button) dialog.findViewById(android.R.id.content).findViewWithTag("string-value-copy")).performClick();
        assertEquals(captured.json().getString("value"), clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        JSONArray matches = captured.json().getJSONArray("classificationMatches");
        int cve = -1;
        for (int i = 0; i < matches.length(); i++) if (matches.getJSONObject(i).getString("ruleId").equals("vuln.cve")) cve = i;
        ((Button) dialog.findViewById(android.R.id.content).findViewWithTag("string-match-" + cve + "-copy")).performClick();
        assertEquals("CVE-2024-12345", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertEquals(encoded, captured.encoded);
    }

    @Test public void engineChoicesSendOnlyExactTextAndEnglishTargetAfterExplicitSelection() throws Exception {
        root("URLs / URIs"); filter().setText("utf16.fixture.invalid"); button("search").performClick(); settle();
        int selected = -1;
        for (int i = 0; i < records().getAdapter().getCount(); i++) if (row(i).name.equals(LE)) selected = i;
        assertTrue(selected >= 0);
        records().performItemClick(null, selected, selected);
        AlertDialog detail = ShadowAlertDialog.getLatestAlertDialog();
        ((Button) detail.findViewById(android.R.id.content).findViewWithTag("string-value-search")).performClick();
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        choose("Brave Search");
        Intent chooser = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        Intent view = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_VIEW, view.getAction());
        assertEquals("search.brave.com", view.getData().getHost());
        assertEquals(LE, view.getData().getQueryParameter("q"));
        assertFalse(view.hasExtra("path"));
        ((Button) detail.findViewById(android.R.id.content).findViewWithTag("string-value-translate")).performClick();
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        choose("Bing Translator");
        view = Shadows.shadowOf(activity).getNextStartedActivity().getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals("en", view.getData().getQueryParameter("to"));
        assertEquals(LE, view.getData().getQueryParameter("text"));
        assertFalse(view.hasExtra("path"));
    }

    @Test public void everyCapturedMatchRemainsAvailableThroughBoundedExplanationPages() throws Exception {
        root("Anti-analysis / introspection"); filter().setText("many-evasion"); button("search").performClick(); settle();
        EvidenceRows.Row captured = row(0); String encoded = captured.encoded;
        assertEquals(20, captured.json().getJSONArray("classificationMatches").length());
        records().performItemClick(null, 0, 0);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        android.view.View body = dialog.findViewById(android.R.id.content);
        TextView count = body.findViewWithTag("string-match-count");
        assertTrue(count.getText().toString(), count.getText().toString().contains("1–8 of 20"));
        Button next = body.findViewWithTag("string-matches-next"); next.performClick(); next.performClick();
        assertTrue(count.getText().toString(), count.getText().toString().contains("17–20 of 20"));
        assertFalse(next.isEnabled());
        assertNotNull(body.findViewWithTag("string-why-19"));
        assertEquals(encoded, captured.encoded);
    }
}

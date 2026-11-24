// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/**
 * Actual libarchive bytes and Android widgets. OS IPC/providers/graphics remain host substitutes.
 */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28, shadows = NativeArchiveWorkbenchTest.HostTransport.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class NativeArchiveGalleryWorkbenchTest {
    private Path temporary, fixtures;
    private ActivityController<NativeArchiveExplorerActivity> controller;
    private NativeArchiveExplorerActivity activity;

    @Before
    public void setup() throws Exception {
        assertTrue(ArchiveEngine.ERROR, ArchiveEngine.AVAILABLE);
        temporary = Files.createTempDirectory("glassdigestor-archive-gallery");
        fixtures = Path.of(System.getProperty("die.archive.fixture.dir"));
        NativeArchiveWorkbenchTest.nativeCalls.set(0);
    }

    @After
    public void cleanup() throws Exception {
        if (controller != null) {
            activity.finish();
            close();
        }
        try (var paths = Files.walk(temporary)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private void close() throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
        controller.pause().stop().destroy();
        assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        controller = null;
    }

    private void settle() throws Exception {
        for (int i = 0; i < 5; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private void open(File source, Bundle state) throws Exception {
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), NativeArchiveExplorerActivity.class)
                        .putExtra("path", source.getAbsolutePath())
                        .putExtra("name", "mislabeled.jpeg");
        controller = Robolectric.buildActivity(NativeArchiveExplorerActivity.class, intent);
        if (state == null) controller.setup().visible();
        else controller.create(state).start().restoreInstanceState(state).resume().visible();
        activity = controller.get();
        settle();
    }

    private NativeArchiveSession session() {
        return ReflectionHelpers.getField(activity, "session");
    }

    private GridView grid() {
        return ReflectionHelpers.getField(activity, "gallery");
    }

    private PreparedExport staged() {
        return ReflectionHelpers.getField(activity, "pendingExport");
    }

    private <T extends View> T tagged(View view, String tag) {
        return view.findViewWithTag(tag);
    }

    private Button button(View view, String name) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(name))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button found = button(((ViewGroup) view).getChildAt(i), name);
                if (found != null) return found;
            }
        return null;
    }

    private void click(String name) throws Exception {
        Button found = button(activity.getWindow().getDecorView(), name);
        assertNotNull(name, found);
        assertTrue(name, found.isEnabled());
        found.performClick();
        settle();
    }

    private File zip(Map<String, byte[]> entries) throws Exception {
        File source = temporary.resolve("selected-as-image.jpg").toFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(source))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return source;
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertNull("Duplicate ZIP output", files.put(entry.getName(), zip.readAllBytes()));
            }
        }
        return files;
    }

    @Test
    @Config(
            shadows = {
                NativeArchiveWorkbenchTest.HostTransport.class,
                PdfPagePreviewWorkbenchTest.Renderer.class,
                PdfPagePreviewWorkbenchTest.Page.class
            })
    public void pdfMemberHasPageThumbnailAndExactTypedSaveDespiteMisleadingName() throws Exception {
        PdfPagePreviewWorkbenchTest.Renderer.count = 2;
        PdfPagePreviewWorkbenchTest.Renderer.width = 144;
        PdfPagePreviewWorkbenchTest.Renderer.height = 96;
        PdfPagePreviewWorkbenchTest.Renderer.fail = false;
        PdfPagePreviewWorkbenchTest.Renderer.afterRender = null;
        byte[] pdf = MetadataFixtureFactory.coloredPdf();
        open(zip(Map.of("report.mp3", pdf)), null);
        click("Gallery");
        View tile = grid().getAdapter().getView(0, null, grid());
        settle();
        tile = grid().getAdapter().getView(0, tile, grid());
        assertNotNull(tagged(tile, "archive-thumbnail:0"));
        TextView status = tagged(tile, "archive-preview-status:0");
        assertTrue(
                status.getText().toString(),
                status.getText().toString().contains("PDF page 1 / 2"));
        tagged(tile, "save-archive-file:0").performClick();
        settle();
        assertNotNull(staged());
        assertEquals("entry-000000-report.pdf", staged().name);
        assertEquals("application/pdf", staged().mime);
        assertArrayEquals(pdf, Files.readAllBytes(staged().file.toPath()));
    }

    @Test
    public void mislabeledImageGetsCheckedThumbnailTypedSaveAndDirectPreview() throws Exception {
        byte[] png = MetadataFixtureFactory.png();
        File source = zip(Map.of("folder/photo.wav", png));
        open(source, null);
        click("Gallery");
        assertEquals(1, grid().getAdapter().getCount());
        grid().performItemClick(null, 0, 0);
        settle();
        assertEquals("folder/", session().current().prefix);
        View tile = grid().getAdapter().getView(0, null, grid());
        settle();
        tile = grid().getAdapter().getView(0, tile, grid());
        assertNotNull(tagged(tile, "archive-thumbnail:0"));
        TextView status = tagged(tile, "archive-preview-status:0");
        assertTrue(status.getText().toString(), status.getText().toString().contains("checked"));
        assertTrue(status.getText().toString().contains("Platform decoded first frame"));
        Button save = tagged(tile, "save-archive-file:0");
        save.performClick();
        settle();
        PreparedExport saved = staged();
        assertNotNull(saved);
        assertEquals("entry-000000-photo.png", saved.name);
        assertEquals("image/png", saved.mime);
        assertArrayEquals(png, Files.readAllBytes(saved.file.toPath()));
        activity.onActivityResult(1, Activity.RESULT_CANCELED, null);
        assertFalse(saved.file.exists());
        grid().performItemClick(null, 0, 0);
        settle();
        Intent preview = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(preview);
        // The first started intent may be the canceled save picker.
        if (Intent.ACTION_CREATE_DOCUMENT.equals(preview.getAction()))
            preview = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ContentPreviewActivity.class.getName(), preview.getComponent().getClassName());
        File child = new File(preview.getStringExtra("path"));
        assertArrayEquals(png, Files.readAllBytes(child.toPath()));
        child.delete();
        ContentOrigin.discard(
                new File(activity.getCacheDir(), "content-origins"),
                preview.getStringExtra("contentOrigin"));
        assertEquals(
                0,
                Objects.requireNonNull(
                                new File(activity.getCacheDir(), "archive-gallery-members").list())
                        .length);
    }

    @Test
    public void zipSpansEveryGalleryPageAndRetainsBytesEvidenceAndPickerAcrossExpiry()
            throws Exception {
        Map<String, byte[]> expected = new LinkedHashMap<>();
        for (int i = 0; i < 29; i++)
            expected.put("images/image-" + i + ".wrong", MetadataFixtureFactory.png());
        expected.put("images/readme", "FOSS 世界\n".getBytes(StandardCharsets.UTF_8));
        expected.put("outside.txt", "excluded by folder".getBytes(StandardCharsets.UTF_8));
        File source = zip(expected);
        open(source, null);
        click("Gallery");
        // Root tree sorts the virtual images folder before outside.txt.
        grid().performItemClick(null, 0, 0);
        settle();
        assertEquals(24, grid().getAdapter().getCount());
        click("Next 24");
        assertEquals(6, grid().getAdapter().getCount());
        click("Export files ZIP");
        PreparedExport bundle = staged();
        assertNotNull(bundle);
        assertEquals("application/zip", bundle.mime);
        byte[] bytes = Files.readAllBytes(bundle.file.toPath());
        Map<String, byte[]> files = unzip(bytes);
        JSONObject manifest =
                new JSONObject(new String(files.get("_archive.json"), StandardCharsets.UTF_8));
        JSONArray members = manifest.getJSONArray("files");
        assertEquals(30, members.length());
        assertTrue(manifest.getBoolean("allMatchingPages"));
        assertEquals("images/", manifest.getString("folder"));
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.getJSONObject(i);
            byte[] wanted = expected.get(member.getString("originalName"));
            assertNotNull(wanted);
            assertArrayEquals(wanted, files.get(member.getString("file")));
            assertEquals(
                    Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(wanted)),
                    member.getString("sha256"));
            assertTrue(member.isNull("physicalOffset"));
            JSONObject acquisition =
                    new JSONObject(
                            new String(
                                    files.get(member.getString("acquisitionEvidence")),
                                    StandardCharsets.UTF_8));
            assertTrue(acquisition.getBoolean("complete"));
            assertEquals(member.getInt("entryIndex"), acquisition.getInt("selectedIndex"));
        }
        assertTrue(files.containsKey("_evidence/archive-trail.json"));
        Bundle state = new Bundle();
        controller.saveInstanceState(state);
        session().current().input.file.delete();
        close();
        source.delete();
        open(source, state);
        assertNotNull(staged());
        assertTrue(ReflectionHelpers.getField(activity, "galleryMode"));
        Uri destination = Uri.parse("content://org.diemobile.test/archive-gallery-zip");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle();
        assertArrayEquals(bytes, output.toByteArray());
        assertFalse(bundle.file.exists());
    }

    @Test
    public void duplicateAndUnsafeNamesRemainDistinctFlatFilesWithOriginalNames() throws Exception {
        File source = temporary.resolve("duplicates.bin").toFile();
        Files.copy(fixtures.resolve("mislabeled.apk"), source.toPath());
        open(source, null);
        click("Export files ZIP");
        assertNotNull(staged());
        Map<String, byte[]> files = unzip(Files.readAllBytes(staged().file.toPath()));
        JSONArray members =
                new JSONObject(new String(files.get("_archive.json"), StandardCharsets.UTF_8))
                        .getJSONArray("files");
        assertEquals(3, members.length());
        Set<String> outputNames = new HashSet<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.getJSONObject(i);
            String name = member.getString("file");
            assertEquals(name, Path.of(name).getFileName().toString());
            assertFalse(name.contains(".."));
            assertTrue(outputNames.add(name));
            if (member.getInt("entryIndex") == 0)
                assertEquals("../outside.txt", member.getString("originalName"));
            else
                assertArrayEquals(
                        (member.getInt("entryIndex") == 1 ? "first" : "second")
                                .getBytes(StandardCharsets.UTF_8),
                        files.get(name));
        }
    }

    @Test
    public void displayedFoldersExportDescendantsAndLinksAreExplicitlyExcluded() throws Exception {
        File source = temporary.resolve("mislabeled.bin").toFile();
        Files.copy(fixtures.resolve("mislabeled.bin"), source.toPath());
        open(source, null);
        click("Export files ZIP");
        Map<String, byte[]> files = unzip(Files.readAllBytes(staged().file.toPath()));
        JSONObject manifest =
                new JSONObject(new String(files.get("_archive.json"), StandardCharsets.UTF_8));
        assertEquals(2, manifest.getJSONArray("files").length());
        assertEquals(3, manifest.getJSONArray("excluded").length());
        assertTrue(manifest.getBoolean("displayedFoldersIncludeDescendants"));
        // Changing filters cannot change an already prepared bundle.
        byte[] saved = Files.readAllBytes(staged().file.toPath());
        ReflectionHelpers.<EditText>getField(activity, "filter").setText("link");
        assertArrayEquals(saved, Files.readAllBytes(staged().file.toPath()));
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    public void galleryKeepsUsableFileSpaceOnAPhoneSizedWindow() throws Exception {
        open(zip(Map.of("image.png", MetadataFixtureFactory.png())), null);
        click("Gallery");
        assertEquals(View.VISIBLE, grid().getVisibility());
        assertTrue(
                "Gallery has only " + grid().getHeight() + " px on a 360×640 phone",
                grid().getHeight() >= 200);
        click("Tools / details");
        View tools = ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();
        for (String label :
                new String[] {
                    "Evidence",
                    "Export report",
                    "Parsing interval",
                    "Reload",
                    "Classic ZIP",
                    "Status / limits"
                }) assertNotNull("Missing existing tool " + label, button(tools, label));
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
        ReflectionHelpers.<TextView>getField(activity, "status").performClick();
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
    }

    @Test
    public void largeMembersRemainListedWithExplicitAutomaticPreviewLimit() throws Exception {
        File source = temporary.resolve("large-compressed.blob").toFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("large-image.png"));
            byte[] block = new byte[65536];
            for (int i = 0; i < 33 * 16; i++) zip.write(block);
            zip.closeEntry();
        }
        open(source, null);
        assertEquals(
                33L * 1024 * 1024, session().current().inventory.members.get(0).size.longValue());
        int calls = NativeArchiveWorkbenchTest.nativeCalls.get();
        click("Gallery");
        View tile = grid().getAdapter().getView(0, null, grid());
        settle();
        tile = grid().getAdapter().getView(0, tile, grid());
        TextView status = tagged(tile, "archive-preview-status:0");
        assertTrue(status.getText().toString(), status.getText().toString().contains("≤32 MiB"));
        assertEquals(calls, NativeArchiveWorkbenchTest.nativeCalls.get());
        assertNotNull(tagged(tile, "save-archive-file:0"));
    }

    @Test
    public void canceledPartlyWrittenBundleDiscardsPayloadAndTemporaryMembers() throws Exception {
        File source = zip(Map.of("image.png", MetadataFixtureFactory.png()));
        open(source, null);
        File exports = temporary.resolve("failed-stage").toFile();
        assertTrue(exports.mkdir());
        try {
            ArchiveFiles.zip(
                    session(),
                    exports,
                    () -> {
                        File[] stages =
                                exports.listFiles(
                                        (directory, name) -> name.startsWith("tool-export-"));
                        return stages != null
                                && Arrays.stream(stages).anyMatch(file -> file.length() > 0);
                    });
            fail("Cancellation accepted a partial ZIP");
        } catch (InterruptedIOException expected) {
            assertEquals(0, Objects.requireNonNull(exports.list()).length);
        }
    }
}

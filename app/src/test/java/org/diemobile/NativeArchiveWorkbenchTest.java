// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Real pinned libarchive/codec JNI and widgets; only OS transport/providers are substituted. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28, shadows = NativeArchiveWorkbenchTest.HostTransport.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class NativeArchiveWorkbenchTest {
    static final AtomicInteger nativeCalls = new AtomicInteger();

    @Implements(value = BinaryWorkerClient.class, isInAndroidSdk = false)
    public static final class HostTransport {
        @org.robolectric.annotation.RealObject private BinaryWorkerClient client;

        @Implementation
        public String scan(File input, BooleanSupplier stop) throws Exception {
            EvidenceRows.check(stop);
            String engine = ReflectionHelpers.getField(client, "engine");
            try (FileInputStream source = new FileInputStream(input)) {
                if ("binwalk".equals(engine)) return BinwalkEngine.scanFd(fd(source.getFD()));
                if ("photorec".equals(engine)) return PhotoRecEngine.scanFd(fd(source.getFD()));
                throw new IOException("Unexpected host discovery engine " + engine);
            }
        }

        private int fd(FileDescriptor value) throws Exception {
            Field field = FileDescriptor.class.getDeclaredField("fd");
            field.setAccessible(true);
            return field.getInt(value);
        }

        @Implementation
        public String inspect(File source, long offset, long length, BooleanSupplier stop)
                throws Exception {
            EvidenceRows.check(stop);
            nativeCalls.incrementAndGet();
            try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
                return ArchiveEngine.inspectFd(fd(input.getFD()), offset, length);
            }
        }

        @Implementation
        public String extract(
                File source,
                File target,
                long offset,
                long length,
                int member,
                BooleanSupplier stop)
                throws Exception {
            EvidenceRows.check(stop);
            nativeCalls.incrementAndGet();
            try (RandomAccessFile input = new RandomAccessFile(source, "r");
                    RandomAccessFile output = new RandomAccessFile(target, "rw")) {
                return ArchiveEngine.extractFd(
                        fd(input.getFD()), fd(output.getFD()), offset, length, member);
            }
        }
    }

    private Path temporary, fixtures;
    private ActivityController<NativeArchiveExplorerActivity> controller;
    private NativeArchiveExplorerActivity activity;
    private ActivityController<ContentPreviewActivity> previewController;
    private ContentPreviewActivity preview;

    @Before
    public void setup() throws Exception {
        assertTrue(ArchiveEngine.ERROR, ArchiveEngine.AVAILABLE);
        temporary = Files.createTempDirectory("glassdigestor-native-archive-ui");
        fixtures = Path.of(System.getProperty("die.archive.fixture.dir"));
        assertTrue(Files.isRegularFile(fixtures.resolve("mislabeled.bin")));
        nativeCalls.set(0);
    }

    @After
    public void cleanup() throws Exception {
        if (previewController != null) {
            preview.finish();
            close(previewController);
        }
        if (controller != null) {
            activity.finish();
            close(controller);
        }
        try (var paths = Files.walk(temporary)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private void close(ActivityController<? extends Activity> item) throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(item.get(), "worker");
        item.pause().stop().destroy();
        assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void settle(Activity owner) throws Exception {
        for (int i = 0; i < 6; i++) {
            ReflectionHelpers.<ExecutorService>getField(owner, "worker")
                    .submit(() -> {})
                    .get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private File open(String fixture) throws Exception {
        File source = temporary.resolve("chosen-as-image.jpeg").toFile();
        Files.copy(fixtures.resolve(fixture), source.toPath());
        open(source, null);
        return source;
    }

    private void open(File source, Bundle state) throws Exception {
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), NativeArchiveExplorerActivity.class)
                        .putExtra("path", source.getAbsolutePath())
                        .putExtra("name", "chosen-as-image.jpeg");
        controller = Robolectric.buildActivity(NativeArchiveExplorerActivity.class, intent);
        if (state == null) controller.setup().visible();
        else controller.create(state).start().restoreInstanceState(state).resume().visible();
        activity = controller.get();
        settle(activity);
    }

    private NativeArchiveSession session() {
        return ReflectionHelpers.getField(activity, "session");
    }

    private String status(Activity owner) {
        return ReflectionHelpers.<TextView>getField(owner, "status").getText().toString();
    }

    private ListView list() {
        return ReflectionHelpers.getField(activity, "list");
    }

    private Button button(View view, String label) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(label))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button result = button(((ViewGroup) view).getChildAt(i), label);
                if (result != null) return result;
            }
        return null;
    }

    private void click(String label) throws Exception {
        Button target = button(activity.getWindow().getDecorView(), label);
        if (target == null) {
            Button tools = button(activity.getWindow().getDecorView(), "Tools / evidence");
            assertNotNull("Missing tools menu", tools);
            tools.performClick();
            target =
                    button(
                            ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),
                            label);
        }
        assertNotNull("Missing button " + label, target);
        assertTrue(target.isEnabled());
        target.performClick();
        settle(activity);
    }

    private void row(String part) throws Exception {
        for (int i = 0; i < list().getAdapter().getCount(); i++)
            if (list().getAdapter().getItem(i).toString().contains(part)) {
                list().performItemClick(null, i, i);
                settle(activity);
                return;
            }
        fail("Missing row " + part + ": " + status(activity));
    }

    private void action(String label) throws Exception {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        Button target = button(dialog.getWindow().getDecorView(), label);
        assertNotNull(label, target);
        assertTrue(label, target.isEnabled());
        target.performClick();
        settle(activity);
    }

    private File zip(String name, byte[] bytes) throws Exception {
        File target = temporary.resolve("outer.blob").toFile();
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(target))) {
            output.putNextEntry(new ZipEntry(name));
            output.write(bytes);
            output.closeEntry();
        }
        return target;
    }

    @Test
    public void singleDecodedFileOpensGallerizeWithIndependentBytesAfterPreviewCloses()
            throws Exception {
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        blob.write("prefix bytes ".getBytes(StandardCharsets.UTF_8));
        blob.write(MetadataFixtureFactory.png());
        blob.write(new byte[19]);
        byte[] decoded = blob.toByteArray();
        open(zip("disk.dd", decoded), null);
        Button quickOpen =
                activity.getWindow().getDecorView().findViewWithTag("archive-open-single");
        assertNotNull(quickOpen);
        assertTrue(quickOpen.isShown());
        assertTrue(quickOpen.isEnabled());
        quickOpen.performClick();
        settle(activity);
        Intent previewIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(status(activity), previewIntent);
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, previewIntent)
                        .setup()
                        .visible();
        preview = previewController.get();
        settle(preview);
        InputSnapshot previewBytes = ReflectionHelpers.getField(preview, "input");
        Button discover = preview.getWindow().getDecorView().findViewWithTag("content-gallerize");
        assertNotNull(discover);
        assertTrue(discover.isEnabled());
        discover.performClick();
        settle(preview);
        Intent handoff = Shadows.shadowOf(preview).getNextStartedActivity();
        assertNotNull(status(preview), handoff);
        assertEquals(GallerizeActivity.class.getName(), handoff.getComponent().getClassName());
        assertTrue(handoff.getBooleanExtra("ownedInputHandoff", false));
        File independent = new File(handoff.getStringExtra("path"));
        assertNotEquals(previewBytes.file, independent);
        assertArrayEquals(decoded, Files.readAllBytes(independent.toPath()));
        preview.finish();
        close(previewController);
        previewController = null;
        assertFalse(previewBytes.file.exists());
        ActivityController<GallerizeActivity> child =
                Robolectric.buildActivity(GallerizeActivity.class, handoff).setup().visible();
        try {
            settle(child.get());
            GallerizeSession loaded = ReflectionHelpers.getField(child.get(), "session");
            assertNotNull(status(child.get()), loaded);
            assertArrayEquals(decoded, Files.readAllBytes(loaded.input.file.toPath()));
            ContentMap map = loaded.digest(() -> false);
            assertTrue(
                    "Embedded PNG was not recovered from decoded archive bytes",
                    map.boundedCount > 0);
            assertFalse("Consumed handoff was not cleaned", independent.exists());
        } finally {
            child.get().finish();
            close(child);
        }
    }

    @Test
    public void compressedStreamOffersDirectOpenAndSaveBeforeEvidence() throws Exception {
        open("raw.bz2");
        assertTrue(status(activity), session().current().inventory.complete);
        assertEquals(1, session().current().inventory.members.size());
        row("data");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        Button open = button(dialog.getWindow().getDecorView(), "Open decoded file / preview");
        Button save = button(dialog.getWindow().getDecorView(), "Save decoded file…");
        Button raw =
                button(
                        dialog.getWindow().getDecorView(),
                        "Full entry metadata / raw returned fields");
        assertNotNull(open);
        assertNotNull(save);
        assertNotNull(raw);
        ViewGroup contents = (ViewGroup) open.getParent();
        assertTrue(contents.indexOfChild(open) < contents.indexOfChild(raw));
        assertTrue(contents.indexOfChild(save) < contents.indexOfChild(raw));
        action("Save decoded file…");
        Intent picker = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(status(activity), picker);
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.getAction());
        PreparedExport staged = ReflectionHelpers.getField(activity, "pendingExport");
        assertNotNull(staged);
        assertArrayEquals(
                "Exact archive member 世界 🔬 with embedded NUL\0 and end.\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Files.readAllBytes(staged.file.toPath()));
    }

    @Test
    public void duplicateIndexOpensExactIndependentPreviewAndByteExportAfterParentExpiry()
            throws Exception {
        File original = open("mislabeled.apk");
        assertTrue(status(activity), session().current().inventory.complete);
        assertEquals(3, session().current().inventory.members.size());
        assertFalse(session().current().inventory.members.get(0).treeName());
        click("All entries");
        row("#2 duplicate");
        action("Open decoded file / preview");
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(status(activity), intent);
        assertEquals(ContentPreviewActivity.class.getName(), intent.getComponent().getClassName());
        File child = new File(intent.getStringExtra("path"));
        assertEquals("second", readText(child.toPath()));
        assertEquals("archive-member", intent.getStringExtra("originalKind"));
        assertFalse(intent.hasExtra("originalOffset"));
        File archiveDirectory = session().directory;
        activity.finish();
        close(controller);
        controller = null;
        original.delete();
        assertFalse(archiveDirectory.exists());
        assertTrue(child.isFile());
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, intent).setup().visible();
        preview = previewController.get();
        settle(preview);
        ReflectionHelpers.<Button>getField(preview, "exportBytes").performClick();
        settle(preview);
        PreparedExport staged = ReflectionHelpers.getField(preview, "contentExport");
        assertNotNull(staged);
        assertEquals("second", readText(staged.file.toPath()));
        Uri destination = Uri.parse("content://org.diemobile.test/archive-member-output");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(preview.getContentResolver()).registerOutputStream(destination, output);
        preview.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle(preview);
        assertEquals("second", output.toString(StandardCharsets.UTF_8));
        assertFalse(staged.file.exists());
    }

    @Test
    public void nestedZipToMislabeledTarRestoresAndBackUsesOriginalCapturedReports()
            throws Exception {
        String longName = "a".repeat(180) + "needle世界-actually-tar.jpeg";
        File original = zip(longName, Files.readAllBytes(fixtures.resolve("mislabeled.bin")));
        open(original, null);
        ReflectionHelpers.<EditText>getField(activity, "filter").setText("needle世界");
        assertEquals(1, list().getAdapter().getCount());
        row("#0 ");
        action("Browse as nested archive / compressed stream");
        assertEquals(status(activity), 2, session().depth());
        assertEquals(longName, session().current().name);
        assertEquals(
                session().current().inventory.format,
                0x30000,
                session().current().inventory.json().getInt("formatCode") & 0xff0000);
        assertEquals(3, nativeCalls.get());
        row("Directory: folder/");
        assertEquals("folder/", session().current().prefix);
        Bundle state = new Bundle();
        controller.saveInstanceState(state);
        String childSha = session().current().input.sha256;
        close(controller);
        controller = null;
        original.delete();
        open(original, state);
        assertEquals(status(activity), childSha, session().current().input.sha256);
        assertEquals("folder/", session().current().prefix);
        assertEquals(3, nativeCalls.get());
        assertEquals(2, session().evidence().getJSONArray("locations").length());
        assertEquals(0, session().current().inventory.members.get(0).index);
        click("Back archive");
        assertEquals(1, session().depth());
        assertEquals(3, nativeCalls.get());
        // A descriptor captured before Back still restores the retained, independently checked
        // child.
        NativeArchiveSession old =
                NativeArchiveSession.restore(
                        session().directory.getParentFile(),
                        state.getString("archiveState"),
                        new HostRunner(),
                        () -> false);
        assertEquals(childSha, old.current().input.sha256);
        assertEquals(2, old.depth());
        old.close(true);
        assertEquals(3, nativeCalls.get());
    }

    @Test
    public void nestedMemberRetainsCompleteNativeTrailAndAcquisitionAfterBrowserExpiry()
            throws Exception {
        String nestedName = "nested-世界.tar";
        File original = zip(nestedName, Files.readAllBytes(fixtures.resolve("mislabeled.bin")));
        open(original, null);
        click("All entries");
        row("#0 ");
        action("Browse as nested archive / compressed stream");
        assertEquals(2, session().depth());
        String rootRaw = session().locations().get(0).inventory.raw;
        String childRaw = session().current().inventory.raw;
        String childAcquisition = session().current().acquisition;
        String childSha = session().current().input.sha256;
        click("All entries");
        row("#0 ");
        action("Open decoded file / preview");
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(status(activity), target);
        String descriptor = target.getStringExtra("contentOrigin");
        assertNotNull(descriptor);
        ContentOrigin evidence =
                ContentOrigin.restore(
                        new File(activity.getCacheDir(), "content-origins"),
                        descriptor,
                        target.getStringExtra("sha256"),
                        target.getLongExtra("sourceSize", -1),
                        () -> false);
        activity.finish();
        close(controller);
        controller = null;
        assertTrue(original.delete());
        var lines = Files.readAllLines(evidence.file.toPath());
        assertEquals(4, lines.size());
        JSONObject relation = new JSONObject(lines.get(0)).getJSONObject("relationship");
        assertEquals("archive-member", relation.getString("kind"));
        assertEquals(childSha, relation.getString("parentSha256"));
        assertTrue(relation.isNull("physicalOffset"));
        JSONObject trail = new JSONObject(lines.get(1)).getJSONObject("value");
        assertEquals(2, trail.getJSONArray("locations").length());
        assertEquals(
                rootRaw,
                trail.getJSONArray("locations").getJSONObject(0).getString("inspectionRaw"));
        JSONObject child = trail.getJSONArray("locations").getJSONObject(1);
        assertEquals(childRaw, child.getString("inspectionRaw"));
        assertEquals(childAcquisition, child.getString("acquisitionFromParentRaw"));
        assertEquals(nestedName, child.getString("displayName"));
        JSONObject selected = new JSONObject(lines.get(2)).getJSONObject("value");
        assertEquals(0, selected.getInt("entryIndex"));
        JSONObject acquisition = new JSONObject(selected.getString("acquisitionRawJson"));
        assertEquals(0, acquisition.getInt("selectedIndex"));
        assertTrue(acquisition.getBoolean("complete"));
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, target).setup().visible();
        preview = previewController.get();
        settle(preview);
        ContentOrigin owned = ReflectionHelpers.getField(preview, "origin");
        assertNotNull(status(preview), owned);
        PreparedExport export =
                owned.export(new File(preview.getCacheDir(), "tool-exports"), () -> false);
        assertArrayEquals(
                Files.readAllBytes(evidence.file.toPath()),
                Files.readAllBytes(export.file.toPath()));
        export.discard();
    }

    /** Restore cannot ask the transport to rerun native work. */
    private static final class HostRunner implements ArchiveRunner {
        public String inspect(File f, long o, long l, BooleanSupplier stop) {
            throw new AssertionError("Restore rescanned");
        }

        public String extract(File f, File t, long o, long l, int m, BooleanSupplier stop) {
            throw new AssertionError("Restore extracted");
        }
    }

    @Test
    public void crcFailureKeepsRawDiagnosticAndExportSurvivesPickerRecreationAndFailure()
            throws Exception {
        File original = open("bad-crc.zip");
        click("All entries");
        row("#0 ");
        action("Open decoded file / preview");
        assertTrue(status(activity), status(activity).contains("failed"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        JSONObject evidence = session().evidence();
        assertEquals(1, evidence.getJSONArray("failedAttempts").length());
        String raw =
                evidence.getJSONArray("failedAttempts").getJSONObject(0).getString("nativeReport");
        assertFalse(new JSONObject(raw).getBoolean("ok"));
        assertTrue(new JSONObject(raw).isNull("acquiredSizeBytes"));
        click("Export report");
        PreparedExport first = ReflectionHelpers.getField(activity, "pendingExport");
        assertNotNull(first);
        activity.onActivityResult(1, Activity.RESULT_CANCELED, null);
        assertFalse(first.file.exists());
        click("Export report");
        PreparedExport staged = ReflectionHelpers.getField(activity, "pendingExport");
        byte[] bytes = Files.readAllBytes(staged.file.toPath());
        Bundle state = new Bundle();
        controller.saveInstanceState(state);
        close(controller);
        controller = null;
        original.delete();
        open(original, state);
        assertEquals(1, session().evidence().getJSONArray("failedAttempts").length());
        Uri broken = Uri.parse("content://org.diemobile.test/archive-report-failure");
        Shadows.shadowOf(activity.getContentResolver())
                .registerOutputStream(
                        broken,
                        new OutputStream() {
                            public void write(int value) throws IOException {
                                throw new IOException("fixture provider failure");
                            }
                        });
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(broken));
        settle(activity);
        assertTrue(status(activity), status(activity).contains("partial output"));
        assertFalse(staged.file.exists());
        click("Export report");
        Uri destination = Uri.parse("content://org.diemobile.test/archive-report-success");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle(activity);
        assertArrayEquals(bytes, output.toByteArray());
    }

    @Test
    public void explicitEmbeddedScopeAndSameLengthSourceMutationRemainVisible() throws Exception {
        open("embedded.blob");
        long offset =
                new JSONObject(readText(fixtures.resolve("expected.json")))
                        .getJSONObject("embedded.blob")
                        .getLong("offset");
        click("Parsing interval");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ArrayList<EditText> fields = new ArrayList<>();
        editFields(dialog.getWindow().getDecorView(), fields);
        assertEquals(2, fields.size());
        fields.get(0).setText("0x" + Long.toHexString(offset));
        fields.get(1).setText("" + (session().current().input.size - offset));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        settle(activity);
        assertEquals(status(activity), offset, session().current().inventory.offset);
        assertTrue(session().current().inventory.complete);
        assertEquals(5, session().current().inventory.members.size());
        assertEquals(
                offset, new JSONObject(session().current().inventory.raw).getLong("scopeOffset"));
        int calls = nativeCalls.get();
        try (RandomAccessFile bytes = new RandomAccessFile(session().current().input.file, "rw")) {
            bytes.write(0x7f);
        }
        click("All entries");
        row("#0 ");
        action("Open decoded file / preview");
        assertTrue(status(activity), status(activity).contains("SHA-256 changed"));
        assertEquals(calls, nativeCalls.get());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void selectedTarMemberReturnsCheckedIndependentBytesToItsParent() throws Exception {
        File original = open("mislabeled.bin");
        click("All entries");
        row("#0 ");
        action("Analyze member in parent workbench");
        Intent result = Shadows.shadowOf(activity).getResultIntent();
        assertNotNull(status(activity), result);
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        File member = new File(result.getStringExtra("path"));
        String expectedSha =
                new JSONObject(readText(fixtures.resolve("expected.json")))
                        .getJSONObject("mislabeled.bin")
                        .getJSONArray("entries")
                        .getJSONObject(0)
                        .getString("sha256");
        assertEquals(expectedSha, result.getStringExtra("memberSha256"));
        assertEquals(
                new JSONObject(readText(fixtures.resolve("expected.json")))
                        .getJSONObject("mislabeled.bin")
                        .getString("sha256"),
                result.getStringExtra("archiveRootSha256"));
        assertEquals(original.length(), result.getLongExtra("archiveRootSize", -1));
        assertTrue(result.getStringExtra("display").contains("世界.txt"));
        File directory = session().directory;
        close(controller);
        controller = null;
        original.delete();
        assertFalse(directory.exists());
        assertTrue(member.isFile());
        try (InputSnapshot acquired =
                ArchiveMemberHandoff.acquire(
                        member,
                        expectedSha,
                        member.getParentFile(),
                        temporary.resolve("independent-parent-input").toFile(),
                        () -> false)) {
            assertEquals(expectedSha, acquired.sha256);
            assertEquals(
                    "Exact archive member 世界 🔬 with embedded NUL\0 and end.\n",
                    readText(acquired.file.toPath()));
        }
        assertFalse(member.exists());
    }

    @Test
    public void callerIdentityMismatchIsRefusedBeforeAnyNativeInspection() throws Exception {
        File original = temporary.resolve("different-source.bin").toFile();
        Files.copy(fixtures.resolve("mislabeled.bin"), original.toPath());
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), NativeArchiveExplorerActivity.class)
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("sha256", "0".repeat(64))
                        .putExtra("sourceSize", original.length());
        controller =
                Robolectric.buildActivity(NativeArchiveExplorerActivity.class, intent)
                        .setup()
                        .visible();
        activity = controller.get();
        settle(activity);
        assertNull(session());
        assertEquals(0, nativeCalls.get());
        assertTrue(
                status(activity),
                status(activity).contains("requested captured source SHA-256/size"));
        assertTrue(original.isFile());
        File base = new File(activity.getCacheDir(), "native-archives");
        assertEquals(0, base.list().length);
    }

    @Test
    public void findingContextKeepsItsUnknownExtentAcrossManualScopeAndRecreation()
            throws Exception {
        File original = temporary.resolve("origin.blob").toFile();
        Files.copy(fixtures.resolve("embedded.blob"), original.toPath());
        long offset =
                new JSONObject(readText(fixtures.resolve("expected.json")))
                        .getJSONObject("embedded.blob")
                        .getLong("offset");
        ArchiveLaunch launch;
        try (InputSnapshot expected =
                InputSnapshot.acquire(
                        original,
                        temporary.resolve("expectation").toFile(),
                        NativeArchiveStore.MAX_INPUT,
                        () -> false)) {
            EvidenceRows.Row row =
                    new EvidenceRows.Row(
                            17,
                            "controlled TAR origin",
                            "Independent origin metadata fixture; no Binwalk scan is substituted",
                            offset,
                            null,
                            new JSONObject().put("parserAccepted", false));
            JSONObject provenance =
                    new JSONObject()
                            .put(
                                    "input",
                                    new JSONObject()
                                            .put("sha256", expected.sha256)
                                            .put("sizeBytes", expected.size));
            launch = ArchiveLaunch.fromFinding(expected, "binwalk-candidates", row, provenance);
        }
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), NativeArchiveExplorerActivity.class)
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("offset", launch.offset)
                        .putExtra("length", launch.length)
                        .putExtra("archiveLaunch", launch.raw);
        controller =
                Robolectric.buildActivity(NativeArchiveExplorerActivity.class, intent)
                        .setup()
                        .visible();
        activity = controller.get();
        settle(activity);
        assertTrue(status(activity), session().current().inventory.complete);
        assertEquals(1, nativeCalls.get());
        assertTrue(status(activity), status(activity).contains("row #17"));
        session().changeScope(0, session().current().input.size, () -> false);
        ReflectionHelpers.callInstanceMethod(activity, "render");
        Bundle state = new Bundle();
        controller.saveInstanceState(state);
        close(controller);
        controller = null;
        original.delete();
        controller =
                Robolectric.buildActivity(NativeArchiveExplorerActivity.class, intent)
                        .create(state)
                        .start()
                        .restoreInstanceState(state)
                        .resume()
                        .visible();
        activity = controller.get();
        settle(activity);
        assertEquals(2, nativeCalls.get());
        assertEquals(0, session().current().inventory.offset);
        assertEquals(launch.raw, session().evidence().getString("originalLaunchRaw"));
        assertTrue(
                new JSONObject(session().launch.json().getString("findingRecordRaw"))
                        .isNull("sizeBytes"));
        assertEquals(offset, session().launch.offset);
        assertEquals(Integer.valueOf(17), session().launch.findingIndex);
    }

    private String readText(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private void editFields(View view, List<EditText> result) {
        if (view instanceof EditText) result.add((EditText) view);
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                editFields(((ViewGroup) view).getChildAt(i), result);
    }
}

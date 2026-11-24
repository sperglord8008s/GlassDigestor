// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
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
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Actual PhotoRec, content/byte models and widgets; OS transport/media runtime/providers are
 * substituted.
 */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28, shadows = GallerizeWorkbenchTest.HostPhotoRec.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class GallerizeWorkbenchTest {
    static final AtomicInteger nativeCalls = new AtomicInteger(),
            binwalkCalls = new AtomicInteger();

    @Implements(value = BinaryWorkerClient.class, isInAndroidSdk = false)
    public static final class HostPhotoRec {
        static boolean binwalkUnavailable;
        @RealObject private BinaryWorkerClient client;

        @Implementation
        public String scan(File input, BooleanSupplier stop) throws Exception {
            boolean binwalk =
                    "binwalk".equals(ReflectionHelpers.<String>getField(client, "engine"));
            (binwalk ? binwalkCalls : nativeCalls).incrementAndGet();
            if (binwalk && binwalkUnavailable)
                throw new IOException(
                        "Deliberately unavailable Binwalk for header-only action coverage");
            EvidenceRows.check(stop);
            try (RandomAccessFile source = new RandomAccessFile(input, "r")) {
                Field field = FileDescriptor.class.getDeclaredField("fd");
                field.setAccessible(true);
                return binwalk
                        ? BinwalkEngine.scanFd(field.getInt(source.getFD()))
                        : PhotoRecEngine.scanFd(field.getInt(source.getFD()));
            }
        }
    }

    private ActivityController<GallerizeActivity> controller;
    private GallerizeActivity activity;
    private ActivityController<ContentPreviewActivity> previewController;
    private ContentPreviewActivity preview;
    private Path temporary;
    private File original;
    private byte[] blob;
    private String text;

    @Before
    public void setup() throws Exception {
        assertTrue(PhotoRecEngine.ERROR, PhotoRecEngine.AVAILABLE);
        nativeCalls.set(0);
        binwalkCalls.set(0);
        HostPhotoRec.binwalkUnavailable = false;
        assertTrue(BinwalkEngine.ERROR, BinwalkEngine.AVAILABLE);
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("gallerize-grid", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("workbench-presentation", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        temporary = Files.createTempDirectory("die-gallery-ui");
        original = temporary.resolve("mixed.not-media").toFile();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(MetadataFixtureFactory.png());
        output.write(MetadataFixtureFactory.jpeg());
        output.write(MetadataFixtureFactory.wav());
        output.write(
                Base64.getDecoder()
                        .decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"));
        text = "Unicode content 世界 🔬 with https://example.invalid/path for the gallery.\n";
        output.write(text.getBytes(StandardCharsets.UTF_8));
        blob = output.toByteArray();
        Files.write(original.toPath(), blob);
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), GallerizeActivity.class)
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("name", original.getName())
                        .putExtra("sha256", Inspector.scan(original, 4).sha256)
                        .putExtra("sourceSize", blob.length);
        controller = Robolectric.buildActivity(GallerizeActivity.class, intent).setup().visible();
        activity = controller.get();
        settle(activity);
    }

    private void settle(Activity target) throws Exception {
        for (int i = 0; i < 12; i++) {
            ReflectionHelpers.<ExecutorService>getField(target, "worker")
                    .submit(() -> {})
                    .get(45, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!(target instanceof GallerizeActivity)
                    || !ReflectionHelpers.<Boolean>getField(target, "busy")) {
                // Thumbnail/render callbacks can enqueue a second wave after initial acquisition.
                ReflectionHelpers.<ExecutorService>getField(target, "worker")
                        .submit(() -> {})
                        .get(45, TimeUnit.SECONDS);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                return;
            }
        }
        fail("Viewer did not settle: " + status(target));
    }

    private String status(Activity target) {
        return ReflectionHelpers.<TextView>getField(target, "status").getText().toString();
    }

    private View tagged(Activity target, String value) {
        return target.getWindow().getDecorView().findViewWithTag(value);
    }

    private void invoke(String method) {
        ReflectionHelpers.callInstanceMethod(activity, method);
    }

    private void closeGallery() throws Exception {
        if (controller != null) {
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            controller.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
            controller = null;
        }
    }

    @After
    public void cleanup() throws Exception {
        if (previewController != null) {
            ExecutorService worker = ReflectionHelpers.getField(preview, "worker");
            previewController.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        }
        closeGallery();
        if (temporary != null)
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    @Test
    public void concatenatedMediaAndTextBecomeLabeledCardsAndActualImageThumbnails()
            throws Exception {
        ContentMap map = ReflectionHelpers.getField(activity, "map");
        assertNotNull(status(activity), map);
        assertTrue(
                map.composition, map.composition.startsWith("Multiple separate stored artifacts"));
        assertTrue(
                map.cards.stream()
                        .anyMatch(c -> c.kind == ContentMap.Kind.AUDIO && c.length != null));
        assertTrue(
                map.cards.stream()
                        .anyMatch(c -> c.kind == ContentMap.Kind.TEXT && c.preview.equals(text)));
        GridView gallery = (GridView) tagged(activity, "gallerize-gallery");
        assertNotNull(gallery);
        int image = -1;
        for (int i = 0; i < gallery.getAdapter().getCount(); i++)
            if (((ContentMap.Card) gallery.getAdapter().getItem(i)).kind == ContentMap.Kind.IMAGE) {
                image = i;
                break;
            }
        assertTrue(image >= 0);
        LinearLayout tile = (LinearLayout) gallery.getAdapter().getView(image, null, gallery);
        settle(activity);
        tile = (LinearLayout) gallery.getAdapter().getView(image, null, gallery);
        ImageView thumbnail = null;
        for (int i = 0; i < tile.getChildCount(); i++)
            if (tile.getChildAt(i) instanceof ImageView) thumbnail = (ImageView) tile.getChildAt(i);
        assertNotNull(thumbnail);
        assertNotNull("A label is not an image preview", thumbnail.getDrawable());
        assertTrue(((BitmapDrawable) thumbnail.getDrawable()).getBitmap().getWidth() > 0);
        assertTrue(status(activity).contains("581 / 774"));
        assertEquals(1, nativeCalls.get());
        assertEquals(1, binwalkCalls.get());
    }

    @Test
    public void fileTileOpensActualPreviewAndBothSavePathsPreservePngBytesAndMime()
            throws Exception {
        GridView gallery = (GridView) tagged(activity, "gallerize-gallery");
        int index = -1;
        for (int i = 0; i < gallery.getAdapter().getCount(); i++) {
            ContentMap.Card item = (ContentMap.Card) gallery.getAdapter().getItem(i);
            assertTrue("Default gallery contains non-file card", RecoveredFiles.fileCard(item));
            if (item.format.equals("png")) index = i;
        }
        assertTrue(index >= 0);
        ContentMap.Card card = (ContentMap.Card) gallery.getAdapter().getItem(index);
        assertTrue(card.findings.stream().anyMatch(r -> r.engine.equals("photorec")));
        assertTrue(card.findings.stream().anyMatch(r -> r.engine.equals("binwalk")));
        View tile = gallery.getAdapter().getView(index, null, gallery);
        tile.findViewWithTag("save-recovered-file:" + card.offset + ":" + card.length)
                .performClick();
        settle(activity);
        Intent picker = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.getAction());
        assertEquals("image/png", picker.getType());
        assertEquals(RecoveredFiles.name(card), picker.getStringExtra(Intent.EXTRA_TITLE));
        Uri destination = Uri.parse("content://org.diemobile.test/recovered-png");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle(activity);
        assertArrayEquals(MetadataFixtureFactory.png(), output.toByteArray());
        gallery.performItemClick(tile, index, index);
        settle(activity);
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ContentPreviewActivity.class.getName(), target.getComponent().getClassName());
        assertEquals(RecoveredFiles.name(card), target.getStringExtra("exportName"));
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, target).setup().visible();
        preview = previewController.get();
        settle(preview);
        assertNotNull(status(preview), tagged(preview, "content-image"));
        ReflectionHelpers.callInstanceMethod(preview, "exportBytes");
        settle(preview);
        Intent previewPicker = Shadows.shadowOf(preview).getNextStartedActivity();
        assertEquals("image/png", previewPicker.getType());
        assertEquals(RecoveredFiles.name(card), previewPicker.getStringExtra(Intent.EXTRA_TITLE));
        PreparedExport staged = ReflectionHelpers.getField(preview, "contentExport");
        assertArrayEquals(MetadataFixtureFactory.png(), Files.readAllBytes(staged.file.toPath()));
    }

    @Test
    public void recoveredZipContainsActualFilesAfterGalleryAndPickerRecreation() throws Exception {
        closeGallery();
        ByteArrayOutputStream many = new ByteArrayOutputStream();
        for (int i = 0; i < 29; i++) {
            many.write(MetadataFixtureFactory.png());
            many.write(new byte[17]);
        }
        blob = many.toByteArray();
        Files.write(original.toPath(), blob);
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), GallerizeActivity.class)
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("name", "29-images.blob")
                        .putExtra("sha256", Inspector.scan(original, 4).sha256)
                        .putExtra("sourceSize", blob.length);
        nativeCalls.set(0);
        binwalkCalls.set(0);
        controller = Robolectric.buildActivity(GallerizeActivity.class, intent).setup().visible();
        activity = controller.get();
        settle(activity);
        ContentMap map = ReflectionHelpers.getField(activity, "map");
        List<ContentMap.Card> files = map.cards.stream().filter(RecoveredFiles::fileCard).toList();
        assertTrue(files.size() >= 29);
        assertEquals(29, files.stream().filter(c -> c.format.equals("png")).count());
        assertEquals(
                24, ((GridView) tagged(activity, "gallerize-gallery")).getAdapter().getCount());
        invoke("exportFiles");
        settle(activity);
        Intent picker = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("application/zip", picker.getType());
        PreparedExport stage = ReflectionHelpers.getField(activity, "prepared");
        assertNotNull(status(activity), stage);
        byte[] expected = Files.readAllBytes(stage.file.toPath());
        Files.delete(original.toPath());
        controller.recreate();
        activity = controller.get();
        settle(activity);
        assertEquals(1, nativeCalls.get());
        assertEquals(1, binwalkCalls.get());
        Uri destination = Uri.parse("content://org.diemobile.test/recovered-files-zip");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle(activity);
        assertArrayEquals(expected, output.toByteArray());
        Map<String, byte[]> entries = new HashMap<>();
        try (java.util.zip.ZipInputStream zip =
                new java.util.zip.ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            for (java.util.zip.ZipEntry entry; (entry = zip.getNextEntry()) != null; )
                entries.put(entry.getName(), zip.readAllBytes());
        }
        for (ContentMap.Card card : files) {
            byte[] bytes =
                    Arrays.copyOfRange(blob, (int) card.offset, (int) (card.offset + card.length));
            assertArrayEquals(
                    RecoveredFiles.name(card), bytes, entries.remove(RecoveredFiles.name(card)));
        }
        assertNotNull(entries.remove("_recovery.json"));
        assertTrue(entries.toString(), entries.isEmpty());
        assertFalse(stage.file.exists());
    }

    @Test
    public void recoveredPdfOpensAnActualFileAndSavesWithItsNativeBoundaryAndPdfType()
            throws Exception {
        closeGallery();
        byte[] pdf = MetadataFixtureFactory.pdf(9 * 1024 * 1024);
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        source.write(new byte[37]);
        source.write(pdf);
        source.write(new byte[97]);
        blob = source.toByteArray();
        Files.write(original.toPath(), blob);
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), GallerizeActivity.class)
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("name", "not-a-pdf.jpg")
                        .putExtra("sha256", Inspector.scan(original, 4).sha256)
                        .putExtra("sourceSize", blob.length);
        controller = Robolectric.buildActivity(GallerizeActivity.class, intent).setup().visible();
        activity = controller.get();
        settle(activity);
        GridView gallery = (GridView) tagged(activity, "gallerize-gallery");
        int index = -1;
        for (int i = 0; i < gallery.getAdapter().getCount(); i++) {
            ContentMap.Card card = (ContentMap.Card) gallery.getAdapter().getItem(i);
            if (card.format.equals("pdf")
                    && card.offset == 37
                    && card.length != null
                    && card.length == pdf.length) index = i;
        }
        assertTrue(status(activity), index >= 0);
        ContentMap.Card card = (ContentMap.Card) gallery.getAdapter().getItem(index);
        assertEquals(ContentMap.Kind.DOCUMENT, card.kind);
        assertEquals("photorec", card.nativeEngine);
        JSONObject finding =
                ReflectionHelpers.<GallerizeSession>getField(activity, "session")
                        .primaryFinding(card, () -> false)
                        .json();
        assertTrue(finding.getBoolean("fileCheckMetadataReadLimited"));
        assertTrue(finding.getBoolean("fileCheckBoundaryEstablished"));
        View tile = gallery.getAdapter().getView(index, null, gallery);
        tile.findViewWithTag("save-recovered-file:" + card.offset + ":" + card.length)
                .performClick();
        settle(activity);
        Intent picker = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("application/pdf", picker.getType());
        PreparedExport export = ReflectionHelpers.getField(activity, "prepared");
        assertArrayEquals(pdf, Files.readAllBytes(export.file.toPath()));
        activity.onActivityResult(1, Activity.RESULT_CANCELED, null);
        settle(activity);
        gallery.performItemClick(tile, index, index);
        settle(activity);
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ContentPreviewActivity.class.getName(), target.getComponent().getClassName());
        assertEquals("DOCUMENT", target.getStringExtra("preferred"));
        assertTrue(target.getStringExtra("exportName").endsWith(".pdf"));
        File child = new File(target.getStringExtra("path"));
        assertArrayEquals(pdf, Files.readAllBytes(child.toPath()));
        ContentOrigin origin =
                ContentOrigin.restore(
                        new File(activity.getCacheDir(), "content-origins"),
                        target.getStringExtra("contentOrigin"),
                        target.getStringExtra("sha256"),
                        pdf.length,
                        () -> false);
        origin.close();
        child.delete();
    }

    @Test
    public void manualGridDimensionsExactCellHexAndStateRemainBoundToOriginalSource()
            throws Exception {
        ReflectionHelpers.setField(activity, "mode", "bytes");
        ReflectionHelpers.<EditText>getField(activity, "width").setText("7");
        ReflectionHelpers.<EditText>getField(activity, "height").setText("5");
        ReflectionHelpers.callInstanceMethod(
                activity, "showGrid", ReflectionHelpers.ClassParameter.from(boolean.class, false));
        settle(activity);
        ByteGrid.Result grid = ReflectionHelpers.getField(activity, "grid");
        assertEquals(7, grid.layout.width);
        assertEquals(5, grid.layout.height);
        ByteGridView view = (ByteGridView) tagged(activity, "gallerize-grid");
        assertNotNull(view);
        view.layout(0, 0, 320, 240);
        android.graphics.Bitmap raster =
                android.graphics.Bitmap.createBitmap(
                        320, 240, android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(raster));
        // Cell 13 is x=6,y=1. The 7:5 grid is centered with its aspect ratio preserved.
        float scale = 320f / 7;
        android.view.MotionEvent touch =
                android.view.MotionEvent.obtain(
                        0,
                        0,
                        android.view.MotionEvent.ACTION_UP,
                        6.5f * scale,
                        (240 - 5 * scale) / 2 + 1.5f * scale,
                        0);
        view.onTouchEvent(touch);
        touch.recycle();
        raster.recycle();
        // Selection stays on the grid so subsequent taps can extend it before opening actions.
        tagged(activity, "grid-range-actions").performClick();
        ShadowAlertDialog.getLatestAlertDialog()
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Intent hex = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(NativeToolsActivity.class.getName(), hex.getComponent().getClassName());
        assertEquals(grid.layout.range(13).offset, hex.getLongExtra("offset", -1));
        assertEquals(grid.sha256, hex.getStringExtra("sha256"));
        String sha = grid.sha256;
        controller.recreate();
        activity = controller.get();
        settle(activity);
        grid = ReflectionHelpers.getField(activity, "grid");
        assertEquals(7, grid.layout.width);
        assertEquals(5, grid.layout.height);
        assertEquals(sha, grid.sha256);
        ByteGridView restored = (ByteGridView) tagged(activity, "gallerize-grid");
        assertEquals(13, restored.anchor());
        assertEquals(13, restored.end());
        assertEquals("Restoration should reuse the exact native capture", 1, nativeCalls.get());
        invoke("gridSizes");
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 6, 6);
        settle(activity);
        grid = ReflectionHelpers.getField(activity, "grid");
        assertEquals(640, grid.layout.width);
        assertEquals(640, grid.layout.height);
    }

    @Test
    public void zoomedContiguousTailExportAndSourceIdentitySurvivePickerRecreation()
            throws Exception {
        ReflectionHelpers.setField(activity, "mode", "entropy");
        ReflectionHelpers.<EditText>getField(activity, "width").setText("7");
        ReflectionHelpers.<EditText>getField(activity, "height").setText("5");
        ReflectionHelpers.callInstanceMethod(
                activity, "showGrid", ReflectionHelpers.ClassParameter.from(boolean.class, false));
        settle(activity);
        ByteGridView view = (ByteGridView) tagged(activity, "gallerize-grid");
        view.layout(0, 0, 320, 240);
        view.setSelection(30, 34);
        tagged(activity, "grid-zoom-in").performClick();
        tagged(activity, "grid-zoom-in").performClick();
        tagged(activity, "grid-zoom-in").performClick();
        assertEquals(8, view.viewport.zoom, 0);
        long expectedStart = 30L * blob.length / 35;
        assertEquals(expectedStart, view.selection().offset);
        assertEquals(blob.length, view.selection().offset + view.selection().length);
        String sha = ReflectionHelpers.<ByteGrid.Result>getField(activity, "grid").sha256;
        ContentMap.Card card = ReflectionHelpers.callInstanceMethod(activity, "gridRangeCard");
        assertTrue(card.explicitInterval);
        assertEquals(expectedStart, card.offset);
        assertEquals(Long.valueOf(blob.length - expectedStart), card.length);
        tagged(activity, "grid-range-actions").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 1, 1);
        settle(activity);
        PreparedExport staged = ReflectionHelpers.getField(activity, "prepared");
        assertNotNull(status(activity), staged);
        byte[] expected = Arrays.copyOfRange(blob, (int) expectedStart, blob.length);
        assertArrayEquals(expected, Files.readAllBytes(staged.file.toPath()));
        assertTrue(original.delete());
        controller.recreate();
        activity = controller.get();
        settle(activity);
        view = (ByteGridView) tagged(activity, "gallerize-grid");
        assertEquals(30, view.anchor());
        assertEquals(34, view.end());
        assertEquals(8, view.viewport.zoom, 0);
        assertEquals(sha, ReflectionHelpers.<ByteGrid.Result>getField(activity, "grid").sha256);
        assertEquals("Recreation must reuse the original native capture", 1, nativeCalls.get());
        Uri destination = Uri.parse("content://org.diemobile.test/selected-grid-tail");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle(activity);
        assertArrayEquals(expected, output.toByteArray());
        assertFalse(staged.file.exists());
    }

    @Test
    public void completeMapExportSurvivesFilterSourceExpiryAndPickerRecreation() throws Exception {
        ContentMap map = ReflectionHelpers.getField(activity, "map");
        String sourceSha =
                ReflectionHelpers.<GallerizeSession>getField(activity, "session").input.sha256;
        ReflectionHelpers.<Spinner>getField(activity, "groups").setSelection(3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        invoke("export");
        settle(activity);
        PreparedExport staged = ReflectionHelpers.getField(activity, "prepared");
        assertNotNull(status(activity), staged);
        byte[] expected = Files.readAllBytes(staged.file.toPath());
        Files.delete(original.toPath());
        controller.recreate();
        activity = controller.get();
        settle(activity);
        assertEquals(1, nativeCalls.get());
        assertEquals(1, binwalkCalls.get());
        Uri destination = Uri.parse("content://org.diemobile.test/gallerize-export");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(destination, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle(activity);
        assertTrue(status(activity), status(activity).contains("Export saved"));
        assertArrayEquals(expected, output.toByteArray());
        JSONObject report = new JSONObject(output.toString(StandardCharsets.UTF_8));
        assertEquals(map.cards.size(), report.getJSONArray("cards").length());
        assertEquals(
                map.rawCount,
                report.getJSONObject("nativeEvidence").getJSONArray("rows").length()
                        + report.getJSONObject("binwalkNativeEvidence")
                                .getJSONArray("rows")
                                .length());
        assertEquals(774, report.getJSONObject("nativeCatalog").getJSONArray("rows").length());
        assertEquals(sourceSha, report.getJSONObject("source").getString("sha256"));
        assertFalse(staged.file.exists());
    }

    @Test
    public void derivedUnicodePreviewOwnsExactBytesAfterGalleryClosesAndRecreates()
            throws Exception {
        ContentMap map = ReflectionHelpers.getField(activity, "map");
        ContentMap.Card card =
                map.cards.stream()
                        .filter(c -> c.kind == ContentMap.Kind.TEXT && c.preview.equals(text))
                        .findFirst()
                        .orElseThrow();
        ReflectionHelpers.callInstanceMethod(
                activity,
                "preview",
                ReflectionHelpers.ClassParameter.from(ContentMap.Card.class, card));
        settle(activity);
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ContentPreviewActivity.class.getName(), target.getComponent().getClassName());
        ContentOrigin capturedOrigin =
                ContentOrigin.restore(
                        new File(activity.getCacheDir(), "content-origins"),
                        target.getStringExtra("contentOrigin"),
                        target.getStringExtra("sha256"),
                        target.getLongExtra("sourceSize", -1),
                        () -> false);
        var originLines = Files.readAllLines(capturedOrigin.file.toPath());
        assertEquals("galleryCard", new org.json.JSONObject(originLines.get(1)).getString("kind"));
        assertEquals(
                "galleryProvenance", new org.json.JSONObject(originLines.get(2)).getString("kind"));

        File bytes = new File(target.getStringExtra("path"));
        assertArrayEquals(
                text.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(bytes.toPath()));
        String parentSha = target.getStringExtra("originalSha256");
        closeGallery();
        Files.delete(original.toPath());
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, target).setup().visible();
        preview = previewController.get();
        settle(preview);
        TextView shown = (TextView) tagged(preview, "content-text");
        assertNotNull(status(preview), shown);
        assertEquals(text, shown.getText().toString());
        assertTrue(
                ReflectionHelpers.<TextView>getField(preview, "identity")
                        .getText()
                        .toString()
                        .contains(parentSha));
        previewController.recreate();
        preview = previewController.get();
        settle(preview);
        assertEquals(text, ((TextView) tagged(preview, "content-text")).getText().toString());
        assertTrue(bytes.isFile());
        ExecutorService worker = ReflectionHelpers.getField(preview, "worker");
        previewController.pause().stop().destroy();
        assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        previewController = null;
        assertFalse(bytes.exists());
    }

    @Test
    public void fullImagePreviewAndManualDecoderFailureRetainExactSourceAndRecover()
            throws Exception {
        ContentMap.Card image =
                ReflectionHelpers.<ContentMap>getField(activity, "map").cards.stream()
                        .filter(
                                card ->
                                        card.kind == ContentMap.Kind.IMAGE
                                                && card.length != null
                                                && card.format.equals("png"))
                        .findFirst()
                        .orElseThrow();
        ReflectionHelpers.callInstanceMethod(
                activity,
                "preview",
                ReflectionHelpers.ClassParameter.from(ContentMap.Card.class, image));
        settle(activity);
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, target).setup().visible();
        preview = previewController.get();
        settle(preview);
        ImageView rendered = (ImageView) tagged(preview, "content-image");
        assertNotNull(status(preview), rendered);
        assertNotNull(rendered.getDrawable());
        assertTrue(status(preview), status(preview).contains("decoded first frame"));
        InputSnapshot owned = ReflectionHelpers.getField(preview, "input");
        String expected = owned.sha256;
        ReflectionHelpers.setField(preview, "mode", "UTF-8 text");
        ReflectionHelpers.callInstanceMethod(preview, "render");
        settle(preview);
        assertTrue(status(preview), status(preview).contains("Preview unavailable"));
        assertTrue(owned.file.isFile());
        ReflectionHelpers.setField(preview, "mode", "Image");
        ReflectionHelpers.callInstanceMethod(preview, "render");
        settle(preview);
        assertNotNull(tagged(preview, "content-image"));
        assertEquals(expected, ReflectionHelpers.<InputSnapshot>getField(preview, "input").sha256);
    }

    @Test
    public void mediaIsLocalExplicitlyStartedPausedAndReportsDecoderFailure() throws Exception {
        ContentMap.Card audio =
                ReflectionHelpers.<ContentMap>getField(activity, "map").cards.stream()
                        .filter(c -> c.kind == ContentMap.Kind.AUDIO && c.length != null)
                        .findFirst()
                        .orElseThrow();
        ReflectionHelpers.callInstanceMethod(
                activity,
                "preview",
                ReflectionHelpers.ClassParameter.from(ContentMap.Card.class, audio));
        settle(activity);
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(10000, 0));
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, target).setup().visible();
        preview = previewController.get();
        settle(preview);
        MediaPlayer player = ReflectionHelpers.getField(preview, "player");
        assertNotNull(status(preview), player);
        ShadowMediaPlayer shadow = Shadows.shadowOf(player);
        assertNull(shadow.getSourceUri());
        assertNotNull(shadow.getDataSource());
        assertFalse(player.isPlaying());
        tagged(preview, "content-play").performClick();
        assertTrue(player.isPlaying());
        previewController.pause();
        assertFalse(player.isPlaying());
        previewController.resume();
        shadow.invokeErrorListener(1, -1004);
        assertTrue(status(preview), status(preview).contains("decoder failed"));
        assertNull(ReflectionHelpers.getField(preview, "player"));
    }

    private byte[] zip(String name, byte[] bytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream stream = new java.util.zip.ZipOutputStream(output)) {
            stream.putNextEntry(new java.util.zip.ZipEntry(name));
            stream.write(bytes);
            stream.closeEntry();
        }
        return output.toByteArray();
    }

    private Intent memberResult(File source) throws Exception {
        ArchiveStore.Inventory inventory = ArchiveStore.read(source, () -> false);
        ArchiveStore.Extracted leaf =
                ArchiveStore.extract(
                        inventory,
                        inventory.members.get(0),
                        new File(activity.getCacheDir(), "archive-snapshots"),
                        () -> false);
        return new Intent()
                .putExtra("path", leaf.file.getAbsolutePath())
                .putExtra("display", "archive!/" + inventory.members.get(0).name)
                .putExtra("memberSha256", leaf.sha256);
    }

    @Test
    public void archiveMemberResultsReachOwnedPreviewsFromGalleryAndNestedPreview()
            throws Exception {
        closeGallery();
        byte[] inner = zip("message.txt", text.getBytes(StandardCharsets.UTF_8));
        Files.write(original.toPath(), zip("nested.zip", inner));
        Intent opening =
                new Intent(RuntimeEnvironment.getApplication(), GallerizeActivity.class)
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("name", "outer.zip")
                        .putExtra("sha256", Inspector.scan(original, 4).sha256)
                        .putExtra("sourceSize", original.length());
        controller = Robolectric.buildActivity(GallerizeActivity.class, opening).setup().visible();
        activity = controller.get();
        settle(activity);
        // Forged paths must neither launch a preview nor remove arbitrary selected files.
        activity.onActivityResult(
                2,
                Activity.RESULT_OK,
                new Intent()
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("memberSha256", opening.getStringExtra("sha256")));
        settle(activity);
        assertTrue(original.isFile());
        assertTrue(status(activity), status(activity).contains("owned member directory"));
        invoke("tools");
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 5, 5);
        var browser = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(browser);
        assertEquals(2, browser.requestCode);
        assertEquals(
                NativeArchiveExplorerActivity.class.getName(),
                browser.intent.getComponent().getClassName());
        GallerizeSession session = ReflectionHelpers.getField(activity, "session");
        Intent member = memberResult(session.input.file);
        File oldLeaf = new File(member.getStringExtra("path"));
        activity.onActivityResult(2, Activity.RESULT_OK, member);
        settle(activity);
        assertFalse(oldLeaf.exists());
        Intent first = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(status(activity), first);
        assertEquals(ContentPreviewActivity.class.getName(), first.getComponent().getClassName());
        assertEquals(session.input.sha256, first.getStringExtra("originalSha256"));
        assertFalse(first.hasExtra("originalOffset"));
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, first).setup().visible();
        preview = previewController.get();
        settle(preview);
        assertArrayEquals(
                inner,
                Files.readAllBytes(
                        ReflectionHelpers.<InputSnapshot>getField(preview, "input").file.toPath()));
        ReflectionHelpers.callInstanceMethod(preview, "tools");
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 3, 3);
        var nestedBrowser = Shadows.shadowOf(preview).getNextStartedActivityForResult();
        assertNotNull(nestedBrowser);
        assertEquals(2, nestedBrowser.requestCode);
        Intent nested =
                memberResult(ReflectionHelpers.<InputSnapshot>getField(preview, "input").file);
        preview.onActivityResult(2, Activity.RESULT_OK, nested);
        settle(preview);
        Intent second = Shadows.shadowOf(preview).getNextStartedActivity();
        assertNotNull(status(preview), second);
        assertEquals("archive-member", second.getStringExtra("originalKind"));
        assertFalse(second.hasExtra("originalOffset"));
        ExecutorService oldWorker = ReflectionHelpers.getField(preview, "worker");
        previewController.pause().stop().destroy();
        assertTrue(oldWorker.awaitTermination(30, TimeUnit.SECONDS));
        closeGallery();
        Files.delete(original.toPath());
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, second).setup().visible();
        preview = previewController.get();
        settle(preview);
        assertEquals(text, ((TextView) tagged(preview, "content-text")).getText().toString());
        assertTrue(
                ReflectionHelpers.<TextView>getField(preview, "identity")
                        .getText()
                        .toString()
                        .contains("physical offset unavailable"));
    }

    @Test
    public void cancelledGridAndFailedDestinationCleanStagesAndAllowRecovery() throws Exception {
        ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        worker.execute(
                () -> {
                    entered.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        ReflectionHelpers.setField(activity, "mode", "entropy");
        ReflectionHelpers.callInstanceMethod(
                activity, "showGrid", ReflectionHelpers.ClassParameter.from(boolean.class, true));
        ReflectionHelpers.<Button>getField(activity, "cancel").performClick();
        release.countDown();
        settle(activity);
        assertNull(ReflectionHelpers.getField(activity, "grid"));
        assertTrue(status(activity), status(activity).contains("cancelled"));
        ReflectionHelpers.callInstanceMethod(
                activity, "showGrid", ReflectionHelpers.ClassParameter.from(boolean.class, true));
        settle(activity);
        assertNotNull(ReflectionHelpers.getField(activity, "grid"));
        invoke("export");
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
        settle(activity);
        PreparedExport staged = ReflectionHelpers.getField(activity, "prepared");
        assertNotNull(staged);
        JSONObject gridReport =
                new JSONObject(
                        new String(
                                Files.readAllBytes(staged.file.toPath()), StandardCharsets.UTF_8));
        ByteGrid.Result grid = ReflectionHelpers.getField(activity, "grid");
        assertEquals(grid.sha256, gridReport.getString("sha256"));
        assertEquals(grid.values.length, gridReport.getJSONArray("cells").length());
        assertFalse(gridReport.getBoolean("sampling"));
        Uri destination = Uri.parse("content://org.diemobile.test/failed-grid-output");
        Shadows.shadowOf(activity.getContentResolver())
                .registerOutputStream(
                        destination,
                        new OutputStream() {
                            public void write(int b) throws IOException {
                                throw new IOException("forced destination failure");
                            }
                        });
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(destination));
        settle(activity);
        assertTrue(status(activity), status(activity).contains("forced destination failure"));
        assertFalse(staged.file.exists());
        assertNotNull(ReflectionHelpers.getField(activity, "grid"));
    }

    private ContentMap.Card openGzipCard() throws Exception {
        closeGallery();
        // Keep the original unknown-extent action contract covered when only PhotoRec completes.
        HostPhotoRec.binwalkUnavailable = true;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(compressed)) {
            gzip.write(
                    ("Independent gallery gzip payload 世界 😀\n".repeat(1000))
                            .getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        source.write(new byte[37]);
        source.write(compressed.toByteArray());
        blob = source.toByteArray();
        Files.write(original.toPath(), blob);
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), GallerizeActivity.class)
                        .putExtra("path", original.getAbsolutePath())
                        .putExtra("name", "mislabeled.image")
                        .putExtra("sha256", Inspector.scan(original, 4).sha256)
                        .putExtra("sourceSize", original.length());
        controller = Robolectric.buildActivity(GallerizeActivity.class, intent).setup().visible();
        activity = controller.get();
        settle(activity);
        ReflectionHelpers.callInstanceMethod(activity, "showGallery");
        settle(activity);
        ContentMap map = ReflectionHelpers.getField(activity, "map");
        ContentMap.Card card =
                map.cards.stream()
                        .filter(
                                c ->
                                        c.nativeRow >= 0
                                                && c.offset == 37
                                                && c.kind == ContentMap.Kind.ARCHIVE)
                        .findFirst()
                        .orElseThrow();
        assertNull(card.length);
        return card;
    }

    private void cardAction(ContentMap.Card card, String part) throws Exception {
        ReflectionHelpers.callInstanceMethod(
                activity,
                "details",
                ReflectionHelpers.ClassParameter.from(ContentMap.Card.class, card));
        AlertDialog detail = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(
                "Inspect / actions",
                detail.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        detail.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog actions = ShadowAlertDialog.getLatestAlertDialog();
        ListView list = actions.getListView();
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            String name = list.getAdapter().getItem(i).toString();
            assertFalse(
                    "An unknown extent must not become an automatic carve",
                    name.equals("Export exact stored bytes"));
            if (name.contains(part)) {
                list.performItemClick(null, i, i);
                actions.dismiss();
                settle(activity);
                return;
            }
        }
        fail("Missing card action " + part);
    }

    @Test
    public void headerOnlyArchiveCardOpensItsObservedParsingTailWithCompleteContext()
            throws Exception {
        ContentMap.Card card = openGzipCard();
        int calls = nativeCalls.get();
        cardAction(card, "Inspect archive / compressed stream here");
        var request = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(status(activity), request);
        assertEquals(2, request.requestCode);
        assertEquals(
                NativeArchiveExplorerActivity.class.getName(),
                request.intent.getComponent().getClassName());
        ArchiveLaunch launch = ArchiveLaunch.parse(request.intent.getStringExtra("archiveLaunch"));
        assertEquals(card.offset, launch.offset);
        assertEquals(blob.length - card.offset, launch.length);
        assertEquals(card.nativeRow, launch.findingIndex.intValue());
        assertTrue(request.intent.getBooleanExtra("originPreviewResult", false));
        org.json.JSONObject context = launch.json().getJSONObject("galleryContext");
        assertTrue(context.getJSONObject("card").isNull("sizeBytes"));
        assertTrue(
                new org.json.JSONObject(launch.json().getString("findingRecordRaw"))
                        .isNull("reportedSizeBytes"));
        assertTrue(launch.json().isNull("recoveredExtentBytes"));
        assertNull(card.length);
        assertEquals(calls, nativeCalls.get());
        activity.onActivityResult(2, Activity.RESULT_CANCELED, null);
        settle(activity);
        assertTrue(status(activity), status(activity).contains("cancelled"));
        assertTrue(
                ReflectionHelpers.<GallerizeSession>getField(activity, "session")
                        .input
                        .file
                        .isFile());
    }

    @Test
    public void manualHeaderInspectionOwnsOnlyChosenBytesAndRetainsTheUnchangedFinding()
            throws Exception {
        ContentMap.Card card = openGzipCard();
        cardAction(card, "Choose inspection byte interval");
        AlertDialog range = ShadowAlertDialog.getLatestAlertDialog();
        ((EditText) range.getWindow().getDecorView().findViewWithTag("gallery-range-offset"))
                .setText("0x26");
        ((EditText) range.getWindow().getDecorView().findViewWithTag("gallery-range-length"))
                .setText("0x15");
        range.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        settle(activity);
        Intent target = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(status(activity), target);
        assertEquals(ContentPreviewActivity.class.getName(), target.getComponent().getClassName());
        assertEquals(38, target.getLongExtra("originalOffset", -1));
        assertEquals(21, target.getLongExtra("sourceSize", -1));
        File selected = new File(target.getStringExtra("path"));
        assertArrayEquals(Arrays.copyOfRange(blob, 38, 59), Files.readAllBytes(selected.toPath()));
        ContentOrigin origin =
                ContentOrigin.restore(
                        new File(activity.getCacheDir(), "content-origins"),
                        target.getStringExtra("contentOrigin"),
                        target.getStringExtra("sha256"),
                        21,
                        () -> false);
        var lines = Files.readAllLines(origin.file.toPath());
        org.json.JSONObject selection =
                new org.json.JSONObject(lines.get(1)).getJSONObject("value");
        assertTrue(selection.getBoolean("explicitInspectionInterval"));
        assertTrue(selection.getString("extentMeaning").contains("not an inferred"));
        org.json.JSONObject witness = new org.json.JSONObject(lines.get(3)).getJSONObject("value");
        assertTrue(
                new org.json.JSONObject(witness.getString("originalRecordJson"))
                        .isNull("reportedSizeBytes"));
        closeGallery();
        assertTrue(original.delete());
        previewController =
                Robolectric.buildActivity(ContentPreviewActivity.class, target).setup().visible();
        preview = previewController.get();
        settle(preview);
        assertNotNull(ReflectionHelpers.getField(preview, "origin"));
        assertArrayEquals(
                Arrays.copyOfRange(blob, 38, 59),
                Files.readAllBytes(
                        ReflectionHelpers.<InputSnapshot>getField(preview, "input").file.toPath()));
        assertNull(card.length);
    }

    @Test
    public void invalidManualCoordinatesRefuseBeforeAcquisitionAndLeaveTheGalleryUsable()
            throws Exception {
        ContentMap.Card card = openGzipCard();
        GallerizeSession session = ReflectionHelpers.getField(activity, "session");
        String sha = session.input.sha256;
        cardAction(card, "Choose inspection byte interval");
        AlertDialog range = ShadowAlertDialog.getLatestAlertDialog();
        ((EditText) range.getWindow().getDecorView().findViewWithTag("gallery-range-offset"))
                .setText("-1");
        ((EditText) range.getWindow().getDecorView().findViewWithTag("gallery-range-length"))
                .setText("2");
        range.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        settle(activity);
        assertTrue(status(activity), status(activity).contains("Inspection interval refused"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "busy"));
        assertEquals(sha, session.input.sha256);
        assertTrue(session.input.file.isFile());
        cardAction(card, "Choose inspection byte interval");
        ShadowAlertDialog.getLatestAlertDialog()
                .getButton(AlertDialog.BUTTON_NEGATIVE)
                .performClick();
        assertNull(card.length);
    }
}

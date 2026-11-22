// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Guards and UI ownership only. Real PDF rasterization has a separate Android runtime case. */
@RunWith(WorkbenchTestRunner.class)
@Config(
        sdk = 28,
        shadows = {
            PdfPagePreviewWorkbenchTest.Renderer.class,
            PdfPagePreviewWorkbenchTest.Page.class
        })
@LooperMode(LooperMode.Mode.PAUSED)
public final class PdfPagePreviewWorkbenchTest {
    @Implements(PdfRenderer.class)
    public static final class Renderer {
        static int opened, closed, pages, pageCloses, renders;
        static int count = 2, width = 144, height = 96, selected;
        static boolean fail;
        static byte[] received;
        static FileDescriptor descriptor;
        static Bitmap lastBitmap;
        static Runnable afterRender;

        @Implementation
        protected void __constructor__(ParcelFileDescriptor file) throws Exception {
            opened++;
            descriptor = file.getFileDescriptor();
            assertTrue(descriptor.valid());
            // Borrow this descriptor; the helper owns and closes it, not this stream wrapper.
            received = new FileInputStream(descriptor).readNBytes(65536);
        }

        @Implementation
        protected int getPageCount() {
            return count;
        }

        @Implementation
        protected PdfRenderer.Page openPage(int index) {
            selected = index;
            pages++;
            return Shadow.newInstanceOf(PdfRenderer.Page.class);
        }

        @Implementation
        protected void close() {
            closed++;
        }
    }

    @Implements(PdfRenderer.Page.class)
    public static final class Page {
        @Implementation
        protected int getWidth() {
            return Renderer.width;
        }

        @Implementation
        protected int getHeight() {
            return Renderer.height;
        }

        @Implementation
        protected void render(Bitmap bitmap, Rect clip, Matrix matrix, int mode) {
            assertNull(clip);
            assertNull(matrix);
            assertEquals(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY, mode);
            Renderer.renders++;
            Renderer.lastBitmap = bitmap;
            if (Renderer.fail) throw new IllegalStateException("Fixture platform render refusal");
            bitmap.eraseColor(Renderer.selected == 0 ? Color.RED : Color.GREEN);
            if (Renderer.afterRender != null) Renderer.afterRender.run();
        }

        @Implementation
        protected void close() {
            Renderer.pageCloses++;
        }
    }

    private Path directory;
    private InputSnapshot input;
    private ActivityController<ContentPreviewActivity> controller;
    private ContentPreviewActivity activity;

    @Before
    public void setup() throws Exception {
        Renderer.opened =
                Renderer.closed = Renderer.pages = Renderer.pageCloses = Renderer.renders = 0;
        Renderer.count = 2;
        Renderer.width = 144;
        Renderer.height = 96;
        Renderer.fail = false;
        Renderer.afterRender = null;
        Renderer.lastBitmap = null;
        directory = Files.createTempDirectory("glassdigestor-pdf-preview");
        Path source = directory.resolve("document.wav");
        Files.write(source, MetadataFixtureFactory.coloredPdf());
        input = InputSnapshot.acquire(source.toFile(), directory.toFile(), 65536, () -> false);
    }

    @After
    public void cleanup() throws Exception {
        if (controller != null) {
            activity.finish();
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            controller.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        if (input != null) input.close();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private void settle() throws Exception {
        for (int i = 0; i < 4; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private Button button(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button result = button(((ViewGroup) view).getChildAt(i), text);
                if (result != null) return result;
            }
        return null;
    }

    @Test
    public void pagePixelsLogicalPointsAndReportOwnershipRemainDistinct() throws Exception {
        PdfPagePreview.Result result = PdfPagePreview.read(input, 1, 64, () -> false);
        try {
            assertEquals(64, result.bitmap.getWidth());
            assertEquals(43, result.bitmap.getHeight());
            assertEquals(144, result.widthPoints);
            assertEquals(96, result.heightPoints);
            assertEquals(1, result.pageIndex);
            assertEquals(2, result.pageCount);
            assertEquals(Color.GREEN, result.bitmap.getPixel(32, 20));
            JSONObject report = result.report();
            assertEquals(input.sha256, report.getString("sourceSha256"));
            assertFalse(report.getBoolean("allReturnedPagesRendered"));
            assertFalse(report.getBoolean("completeFileValidityVerified"));
            report.put("pageCount", 99);
            assertEquals(2, result.report().getInt("pageCount"));
            assertEquals(1, Renderer.closed);
            assertEquals(1, Renderer.pageCloses);
            assertFalse(Renderer.descriptor.valid());
            input.checkIdentity(() -> false);
        } finally {
            result.bitmap.recycle();
        }
    }

    @Test
    public void embeddedThumbnailReceivesOnlyExactPdfAndDeletesItsPrivateStage() throws Exception {
        byte[] pdf = MetadataFixtureFactory.coloredPdf();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[37]);
        bytes.write(pdf);
        bytes.write(new byte[97]);
        Path source = directory.resolve("blob.bin");
        Files.write(source, bytes.toByteArray());
        try (InputSnapshot blob =
                InputSnapshot.acquire(source.toFile(), directory.toFile(), 65536, () -> false)) {
            File stages = directory.resolve("thumbnails").toFile();
            PdfPagePreview.Result result =
                    PdfPagePreview.thumbnail(
                            blob,
                            new FileRange(blob.size, 37, pdf.length),
                            stages,
                            256,
                            () -> false);
            try {
                assertArrayEquals(pdf, Renderer.received);
                JSONObject report = result.report();
                assertEquals(input.sha256, report.getString("sourceSha256"));
                assertEquals(blob.sha256, report.getString("parentSourceSha256"));
                assertEquals(37, report.getLong("physicalOffset"));
                assertEquals(pdf.length, report.getLong("storedSizeBytes"));
                assertEquals(256, result.bitmap.getWidth());
                assertEquals(171, result.bitmap.getHeight());
                assertEquals(0, Objects.requireNonNull(stages.list()).length);
                blob.checkIdentity(() -> false);
            } finally {
                result.bitmap.recycle();
            }
        }
    }

    @Test
    public void nativeFailureCancellationAndMutationCloseResourcesAndDiscardBitmap()
            throws Exception {
        Renderer.fail = true;
        try {
            PdfPagePreview.read(input, 0, 64, () -> false);
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(Renderer.lastBitmap.isRecycled());
        }
        assertEquals(1, Renderer.pageCloses);
        assertEquals(1, Renderer.closed);
        assertFalse(Renderer.descriptor.valid());
        Renderer.fail = false;
        Renderer.afterRender =
                () -> {
                    try (RandomAccessFile source = new RandomAccessFile(input.file, "rw")) {
                        source.writeByte(0);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                };
        try {
            PdfPagePreview.read(input, 0, 64, () -> false);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
        }
        assertTrue(Renderer.lastBitmap.isRecycled());
        assertEquals(2, Renderer.pageCloses);
        assertEquals(2, Renderer.closed);
        int opened = Renderer.opened;
        try {
            PdfPagePreview.read(input, 0, 64, () -> true);
            fail();
        } catch (InterruptedIOException expected) {
            assertEquals(opened, Renderer.opened);
        }
    }

    @Test
    public void dimensionsPageBoundsAndForeignOrLargeScopesFailExplicitly() throws Exception {
        try {
            PdfPagePreview.read(input, -1, 64, () -> false);
            fail();
        } catch (IOException expected) {
        }
        try {
            PdfPagePreview.read(input, 0, 1537, () -> false);
            fail();
        } catch (IOException expected) {
        }
        assertEquals(0, Renderer.opened);
        try {
            PdfPagePreview.read(input, 2, 64, () -> false);
            fail();
        } catch (IOException expected) {
        }
        assertEquals(0, Renderer.pages);
        assertEquals(1, Renderer.closed);
        Renderer.width = 0;
        try {
            PdfPagePreview.read(input, 0, 64, () -> false);
            fail();
        } catch (IOException expected) {
        }
        assertEquals(1, Renderer.pageCloses);
        assertEquals(2, Renderer.closed);
        assertNull(Renderer.lastBitmap);
        try {
            PdfPagePreview.thumbnail(
                    input,
                    new FileRange(input.size + 1, 0, 1),
                    directory.toFile(),
                    256,
                    () -> false);
            fail();
        } catch (IOException expected) {
        }
        File large = directory.resolve("large-pdf.snapshot").toFile();
        try (RandomAccessFile file = new RandomAccessFile(large, "rw")) {
            file.setLength(33L * 1024 * 1024);
        }
        // A size-bound refusal is independent of sparsity; no positive payload result is claimed.
        try (InputSnapshot snapshot =
                InputSnapshot.acquire(large, directory.toFile(), 34L * 1024 * 1024, () -> false)) {
            int before = Renderer.opened;
            try {
                PdfPagePreview.thumbnail(
                        snapshot,
                        new FileRange(snapshot.size, 0, snapshot.size),
                        directory.toFile(),
                        256,
                        () -> false);
                fail();
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("32 MiB"));
            }
            assertEquals(before, Renderer.opened);
            var card =
                    new ContentMap.Card(
                            ContentMap.Kind.DOCUMENT, "Large PDF", "pdf", 0, snapshot.size, "", 0);
            var context = RuntimeEnvironment.getApplication();
            try (ContentGalleryAdapter adapter =
                    new ContentGalleryAdapter(
                            context, List.of(card), snapshot, Runnable::run, selected -> {})) {
                LinearLayout parent = new LinearLayout(context);
                adapter.getView(0, null, parent);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                View tile = adapter.getView(0, null, parent);
                for (int i = 0; i < ((ViewGroup) tile).getChildCount(); i++) {
                    View child = ((ViewGroup) tile).getChildAt(i);
                    if (child instanceof ImageView) assertEquals(View.GONE, child.getVisibility());
                }
                assertNotNull(button(tile, "Save file"));
                assertEquals(before, Renderer.opened);
            }
        }
    }

    @Test
    public void galleryPdfThumbnailIsRequestedOnceAndUnknownExtentNeverReachesRenderer()
            throws Exception {
        var context = RuntimeEnvironment.getApplication();
        var known =
                new ContentMap.Card(ContentMap.Kind.DOCUMENT, "PDF", "pdf", 0, input.size, "", 0);
        try (ContentGalleryAdapter adapter =
                new ContentGalleryAdapter(context, List.of(known), input, Runnable::run)) {
            LinearLayout parent = new LinearLayout(context);
            adapter.getView(0, null, parent);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            adapter.getView(0, null, parent);
            assertEquals(1, Renderer.opened);
            assertEquals(2, adapter.platformObservation(known).getInt("pageCount"));
        }
        var unknown =
                new ContentMap.Card(ContentMap.Kind.DOCUMENT, "PDF header", "pdf", 0, null, "", 0);
        try (ContentGalleryAdapter adapter =
                new ContentGalleryAdapter(context, List.of(unknown), input, Runnable::run)) {
            adapter.getView(0, null, new LinearLayout(context));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, Renderer.opened);
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    public void fullViewerPagesAndRecreationUseTheSharedBoundedRenderer() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        InputSnapshot handoff =
                InputSnapshot.acquire(
                        input.file,
                        new File(context.getCacheDir(), "content-previews"),
                        65536,
                        () -> false);
        Intent intent =
                new Intent(context, ContentPreviewActivity.class)
                        .putExtra("path", handoff.file.getAbsolutePath())
                        .putExtra("sha256", handoff.sha256)
                        .putExtra("sourceSize", handoff.size)
                        .putExtra("name", "disguised-as-audio.wav");
        controller =
                Robolectric.buildActivity(ContentPreviewActivity.class, intent).setup().visible();
        activity = controller.get();
        settle();
        assertNotNull(activity.getWindow().getDecorView().findViewWithTag("content-pdf"));
        assertEquals(1536, Renderer.lastBitmap.getWidth());
        assertEquals(1024, Renderer.lastBitmap.getHeight());
        Button pendingNext = button(activity.getWindow().getDecorView(), "Next page");
        pendingNext.performClick();
        pendingNext.performClick(); // A queued click on removed controls must not skip a page.
        settle();
        assertEquals(1, Renderer.selected);
        assertFalse(button(activity.getWindow().getDecorView(), "Next page").isEnabled());
        int parsed = Renderer.renders;
        button(activity.getWindow().getDecorView(), "Actual pixels").performClick();
        settle();
        assertEquals(parsed, Renderer.renders);
        ImageView image = activity.getWindow().getDecorView().findViewWithTag("content-pdf");
        assertEquals(1536, image.getLayoutParams().width);
        assertEquals(1024, image.getLayoutParams().height);
        HorizontalScrollView pan =
                activity.getWindow().getDecorView().findViewWithTag("content-pdf-pan");
        ScrollView scroll =
                activity.getWindow().getDecorView().findViewWithTag("content-pdf-scroll");
        pan.scrollTo(100, 0);
        scroll.scrollTo(0, 120);
        TextView identity = ReflectionHelpers.getField(activity, "identity");
        String completeIdentity =
                identity.getText().toString() + "\n" + "Captured origin detail ".repeat(200);
        identity.setText(completeIdentity);
        assertEquals(2, identity.getMaxLines());
        identity.performClick();
        assertEquals(
                completeIdentity,
                Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getMessage().toString());
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
        assertTrue(
                "PDF pane too small: "
                        + ReflectionHelpers.<LinearLayout>getField(activity, "content").getHeight(),
                ReflectionHelpers.<LinearLayout>getField(activity, "content").getHeight() >= 180);
        controller.recreate();
        activity = controller.get();
        settle();
        assertEquals(1, ReflectionHelpers.<Integer>getField(activity, "pdfPage").intValue());
        assertTrue(ReflectionHelpers.<Boolean>getField(activity, "pdfActualPixels"));
        pan = activity.getWindow().getDecorView().findViewWithTag("content-pdf-pan");
        scroll = activity.getWindow().getDecorView().findViewWithTag("content-pdf-scroll");
        assertEquals(100, pan.getScrollX());
        assertEquals(120, scroll.getScrollY());
        parsed = Renderer.renders;
        button(activity.getWindow().getDecorView(), "Fit page").performClick();
        settle();
        assertEquals(parsed, Renderer.renders);
        assertNull(activity.getWindow().getDecorView().findViewWithTag("content-pdf-pan"));
        button(activity.getWindow().getDecorView(), "Previous page").performClick();
        settle();
        assertEquals(0, Renderer.selected);
        assertTrue(
                ReflectionHelpers.<TextView>getField(activity, "status")
                        .getText()
                        .toString()
                        .contains("points"));
    }
}

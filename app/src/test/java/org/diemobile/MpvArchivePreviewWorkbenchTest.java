// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
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
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Real archive acquisition and MPV pixels; platform image refusal and OS transport are substituted. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28, shadows = {MpvArchivePreviewWorkbenchTest.HostTransport.class,
        MpvPreviewWorkbenchTest.Images.class})
@LooperMode(LooperMode.Mode.PAUSED)
public final class MpvArchivePreviewWorkbenchTest {
    @Implements(value = BinaryWorkerClient.class, isInAndroidSdk = false)
    public static final class HostTransport {
        static final AtomicInteger extracts = new AtomicInteger();

        @Implementation public String inspect(File source, long offset, long length,
                BooleanSupplier stop) throws Exception {
            return new NativeArchiveWorkbenchTest.HostTransport().inspect(source, offset, length, stop);
        }

        @Implementation public String extract(File source, File target, long offset, long length,
                int member, BooleanSupplier stop) throws Exception {
            extracts.incrementAndGet();
            return new NativeArchiveWorkbenchTest.HostTransport()
                    .extract(source, target, offset, length, member, stop);
        }

        @Implementation public String capture(File input, File output, int target,
                BooleanSupplier stop) throws Exception {
            return new MpvPreviewWorkbenchTest.HostFrame().capture(input, output, target, stop);
        }
    }

    private Path temporary;
    private ActivityController<NativeArchiveExplorerActivity> controller;
    private NativeArchiveExplorerActivity activity;

    @Before public void setup() throws Exception {
        assertTrue(ArchiveEngine.ERROR, ArchiveEngine.AVAILABLE);
        assertTrue(MpvEngine.ERROR, MpvEngine.AVAILABLE);
        temporary = Files.createTempDirectory("glassdigestor-mpv-archive");
        HostTransport.extracts.set(0);
        MpvPreviewWorkbenchTest.HostFrame.calls.set(0);
        MpvPreviewWorkbenchTest.HostFrame.captured = MpvPreviewWorkbenchTest.HostFrame.release = null;
        MpvPreviewWorkbenchTest.Images.calls.set(0);
        MpvPreviewWorkbenchTest.Images.succeeds = MpvPreviewWorkbenchTest.Images.oversized = false;
        MpvPreviewWorkbenchTest.Images.onBounds = null;
    }

    @After public void close() throws Exception {
        if (controller != null) {
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            activity.finish(); controller.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        try (var paths = Files.walk(temporary)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private void open(File archive) throws Exception {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), NativeArchiveExplorerActivity.class)
                .putExtra("path", archive.getAbsolutePath()).putExtra("name", "mislabeled.bin");
        controller = Robolectric.buildActivity(NativeArchiveExplorerActivity.class, intent).setup().visible();
        activity = controller.get(); settle();
        ReflectionHelpers.<Button>getField(activity, "galleryToggle").performClick();
        settle();
    }

    private void settle() throws Exception {
        for (int i = 0; i < 6; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {}).get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private File archive(byte[] image) throws Exception {
        File file = temporary.resolve("source.zip").toFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("file.mp3")); zip.write(image); zip.closeEntry();
        }
        return file;
    }

    private Button button(View view, String label) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(label))
            return (Button) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                Button found = button(((ViewGroup) view).getChildAt(i), label);
                if (found != null) return found;
            }
        return null;
    }

    private String text(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof TextView) result.append(((TextView) view).getText());
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                result.append(text(((ViewGroup) view).getChildAt(i))).append('\n');
        return result.toString();
    }

    @Test public void acquiredArchiveImageUsesRealMpvAndDetailsKeepNativeAndDecoderScopesSeparate()
            throws Exception {
        byte[] png = MpvPreviewWorkbenchTest.png();
        open(archive(png));
        NativeArchiveSession session = ReflectionHelpers.getField(activity, "session");
        NativeArchiveStore.Member member = session.current().inventory.members.get(0);
        String original = member.raw;
        GridView gallery = ReflectionHelpers.getField(activity, "gallery");
        View tile = gallery.getAdapter().getView(0, null, gallery); settle();
        tile = gallery.getAdapter().getView(0, tile, gallery);
        ImageView image = tile.findViewWithTag("archive-thumbnail:0");
        assertNotNull(text(tile), image);
        MpvPreviewWorkbenchTest.redGreen(((BitmapDrawable) image.getDrawable()).getBitmap());
        assertEquals(1, HostTransport.extracts.get());
        assertEquals(1, MpvPreviewWorkbenchTest.HostFrame.calls.get());
        NativeArchiveGalleryAdapter adapter = ReflectionHelpers.getField(activity, "galleryAdapter");
        JSONObject information = adapter.previewInformation(member);
        assertFalse(information.has("platform"));
        assertTrue(information.has("platformDecoderFailure"));
        JSONObject mpv = information.getJSONObject("mpvStill");
        assertEquals(Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(png)),
                mpv.getString("sourceSha256"));
        assertEquals(0, mpv.getLong("physicalOffset"));
        assertEquals(png.length, mpv.getLong("storedSizeBytes"));
        assertEquals(original, member.raw);
        session.current().input.checkIdentity(() -> false);
        ReflectionHelpers.callInstanceMethod(activity, "details",
                ReflectionHelpers.ClassParameter.from(NativeArchiveStore.Member.class, member));
        AlertDialog details = ShadowAlertDialog.getLatestAlertDialog();
        Button decoder = button(details.getWindow().getDecorView(), "Thumbnail decoder information");
        assertNotNull(decoder); decoder.performClick();
        String displayed = text(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView());
        assertTrue(displayed, displayed.contains("transientDecoderPreview") && displayed.contains("mpvStill"));
        assertFalse(displayed.contains("transientPlatformPreview"));
    }

    @Test public void declaredArchivePreviewBudgetRefusalDoesNotExtractOrInvokeMpv() throws Exception {
        File file = temporary.resolve("large.zip").toFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("large.png"));
            byte[] zeros = new byte[65536];
            long remaining = NativeArchiveGalleryAdapter.MAX_MEMBER_PREVIEW + 1;
            while (remaining > 0) {
                int count = (int) Math.min(remaining, zeros.length);
                zip.write(zeros, 0, count); remaining -= count;
            }
            zip.closeEntry();
        }
        open(file);
        GridView gallery = ReflectionHelpers.getField(activity, "gallery");
        View tile = gallery.getAdapter().getView(0, null, gallery); settle();
        tile = gallery.getAdapter().getView(0, tile, gallery);
        assertTrue(text(tile), text(tile).contains("32 MiB"));
        assertEquals(0, HostTransport.extracts.get());
        assertEquals(0, MpvPreviewWorkbenchTest.HostFrame.calls.get());
    }
}

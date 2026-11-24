// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Looper;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowBitmapFactory;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Forced platform refusals feed real libmpv; expected pixels come from authored source bytes. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28, shadows = {MpvPreviewWorkbenchTest.HostFrame.class,
        MpvPreviewWorkbenchTest.Images.class, MpvPreviewWorkbenchTest.Video.class})
@LooperMode(LooperMode.Mode.PAUSED)
public final class MpvPreviewWorkbenchTest {
    @Implements(value = BinaryWorkerClient.class, isInAndroidSdk = false)
    public static final class HostFrame {
        static final AtomicInteger calls = new AtomicInteger();
        static volatile CountDownLatch captured, release;

        @Implementation public String capture(File input, File output, int target,
                BooleanSupplier stop) throws Exception {
            assertNotSame(Looper.getMainLooper(), Looper.myLooper());
            EvidenceRows.check(stop);
            calls.incrementAndGet();
            String report;
            try (FileInputStream source = new FileInputStream(input);
                    RandomAccessFile destination = new RandomAccessFile(output, "rw")) {
                report = MpvEngine.captureFd(fd(source.getFD()), fd(destination.getFD()), target);
            }
            if (captured != null) captured.countDown();
            if (release != null) assertTrue(release.await(30, TimeUnit.SECONDS));
            EvidenceRows.check(stop);
            return report;
        }
    }

    @Implements(BitmapFactory.class)
    public static final class Images extends ShadowBitmapFactory {
        static final AtomicInteger calls = new AtomicInteger();
        static boolean succeeds, oversized;
        static Runnable onBounds;

        @Implementation protected static Bitmap decodeStream(InputStream source, Rect padding,
                BitmapFactory.Options options) {
            // Framework drawable inflation also uses this overload. Preserve the standard
            // resource renderer; the forced refusal applies to our bounded preview requests.
            if (options == null) return ShadowBitmapFactory.decodeStream(source, padding, null);
            calls.incrementAndGet();
            if (options.inJustDecodeBounds) {
                if (onBounds != null) onBounds.run();
                options.outWidth = succeeds || oversized ? 64 : -1;
                options.outHeight = succeeds || oversized ? 48 : -1;
                return null;
            }
            return Bitmap.createBitmap(oversized ? 257 : 64, 48, Bitmap.Config.ARGB_8888);
        }
    }

    @Implements(MediaMetadataRetriever.class)
    public static final class Video {
        static int frames;
        static boolean oversized;

        @Implementation protected void setDataSource(FileDescriptor fd, long offset, long length) {
            assertTrue(fd.valid());
            assertTrue(length > 0);
        }

        @Implementation protected String extractMetadata(int key) {
            switch (key) {
                case MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO: return "yes";
                case MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH: return "64";
                case MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT: return "48";
                case MediaMetadataRetriever.METADATA_KEY_DURATION: return "731";
                case MediaMetadataRetriever.METADATA_KEY_MIMETYPE: return "video/fixture-platform";
                default: return null;
            }
        }

        @Implementation(minSdk = 27) protected Bitmap getScaledFrameAtTime(long time, int option,
                int width, int height) {
            frames++;
            return oversized ? Bitmap.createBitmap(width + 1, 1, Bitmap.Config.ARGB_8888) : null;
        }

        @Implementation protected Bitmap getFrameAtTime(long time, int option) {
            throw new AssertionError("An unbounded platform frame must never be requested");
        }

        @Implementation protected void release() {}
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Path temporary;
    private InputSnapshot input;
    private ContentGalleryAdapter adapter;
    private ActivityController<ContentPreviewActivity> controller;
    private ContentPreviewActivity activity;

    @Before public void setup() throws Exception {
        assertTrue("Real host MPV is required: " + MpvEngine.ERROR, MpvEngine.AVAILABLE);
        temporary = Files.createTempDirectory("glassdigestor-mpv-preview");
        HostFrame.calls.set(0);
        HostFrame.captured = HostFrame.release = null;
        Images.calls.set(0);
        Images.succeeds = Images.oversized = false;
        Images.onBounds = null;
        Video.frames = 0;
        Video.oversized = false;
    }

    @After public void close() throws Exception {
        if (HostFrame.release != null) HostFrame.release.countDown();
        if (adapter != null) adapter.close();
        if (controller != null) {
            ExecutorService uiWorker = ReflectionHelpers.getField(activity, "worker");
            activity.finish();
            controller.pause().stop().destroy();
            assertTrue(uiWorker.awaitTermination(30, TimeUnit.SECONDS));
        }
        worker.shutdown();
        assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        if (input != null) input.close();
        if (temporary != null) try (var paths = Files.walk(temporary)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    static int fd(FileDescriptor descriptor) throws Exception {
        Field field = FileDescriptor.class.getDeclaredField("fd");
        field.setAccessible(true);
        return field.getInt(descriptor);
    }

    static byte[] png() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream dimensions = new DataOutputStream(header);
        dimensions.writeInt(64); dimensions.writeInt(48);
        dimensions.write(new byte[] {8, 2, 0, 0, 0});
        chunk(bytes, "IHDR", header.toByteArray());
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(pixels)) {
            for (int y = 0; y < 48; y++) {
                compressed.write(0);
                for (int x = 0; x < 64; x++)
                    compressed.write(x < 32 ? new byte[] {(byte) 255, 0, 0}
                            : new byte[] {0, (byte) 255, 0});
            }
        }
        chunk(bytes, "IDAT", pixels.toByteArray());
        chunk(bytes, "IEND", new byte[0]);
        return bytes.toByteArray();
    }

    private static void chunk(OutputStream target, String kind, byte[] data) throws Exception {
        DataOutputStream output = new DataOutputStream(target);
        byte[] type = kind.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(data.length); output.write(type); output.write(data);
        CRC32 crc = new CRC32(); crc.update(type); crc.update(data);
        output.writeInt((int) crc.getValue());
    }

    private static byte[] video() throws Exception {
        for (Path start : new Path[] {Path.of(System.getProperty("user.dir")),
                Path.of(System.getProperty("die.fixture.dir"))})
            for (Path root = start; root != null; root = root.getParent()) {
                Path file = root.resolve("tests/fixtures/media/green-64x48.mp4");
                if (Files.isRegularFile(file)) return Files.readAllBytes(file);
            }
        throw new IOException("Owned pinned green-64x48.mp4 fixture is unavailable");
    }

    private ContentMap.Card card(byte[] payload, ContentMap.Kind kind) throws Exception {
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        blob.write(new byte[37]); blob.write(payload); blob.write(new byte[19]);
        File file = temporary.resolve("parent.bin").toFile();
        Files.write(file.toPath(), blob.toByteArray());
        input = InputSnapshot.acquire(file, temporary.toFile(), 1024 * 1024, () -> false);
        return new ContentMap.Card(kind, "Exact bounded fixture", kind == ContentMap.Kind.IMAGE
                ? "png" : "mp4", 37, (long) payload.length, "", 0);
    }

    private void thumbnail(ContentMap.Card card) throws Exception {
        adapter = new ContentGalleryAdapter(RuntimeEnvironment.getApplication(),
                Collections.singletonList(card), input, worker);
        adapter.getView(0, null, new LinearLayout(RuntimeEnvironment.getApplication()));
        worker.submit(() -> {}).get(30, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private Bitmap thumbnail() {
        return ReflectionHelpers.<Map<Integer, Bitmap>>getField(adapter, "thumbnails").get(0);
    }

    static void redGreen(Bitmap bitmap) {
        assertNotNull(bitmap);
        assertEquals(64, bitmap.getWidth()); assertEquals(48, bitmap.getHeight());
        assertEquals(Color.RED, bitmap.getPixel(8, 24));
        assertEquals(Color.GREEN, bitmap.getPixel(56, 24));
    }

    private static void green(Bitmap bitmap) {
        assertNotNull(bitmap);
        assertEquals(64, bitmap.getWidth()); assertEquals(48, bitmap.getHeight());
        int color = bitmap.getPixel(32, 24);
        assertTrue(Color.red(color) < 10 && Color.blue(color) < 10);
        assertTrue(Color.green(color) >= 110 && Color.green(color) <= 145);
    }

    @Test public void imageDecoderRefusalUsesRealMpvPixelsFromOnlyTheSelectedRange() throws Exception {
        ContentMap.Card card = card(png(), ContentMap.Kind.IMAGE);
        thumbnail(card);
        redGreen(thumbnail());
        assertEquals(1, Images.calls.get()); assertEquals(1, HostFrame.calls.get());
        assertNull(adapter.platformObservation(card));
        JSONObject report = adapter.mpvObservation(card);
        assertEquals("org.diemobile.mpv-frame-preview", report.getString("schema"));
        assertEquals(input.sha256, report.getString("sourceSha256"));
        assertEquals(37, report.getLong("physicalOffset"));
        assertEquals(card.length.longValue(), report.getLong("storedSizeBytes"));
        assertFalse(report.getJSONObject("nativeReport").getBoolean("everyFrameRendered"));
        input.checkIdentity(() -> false);
    }

    @Test public void successfulPlatformImageDoesNotInvokeTheFallback() throws Exception {
        Images.succeeds = true;
        ContentMap.Card card = card(png(), ContentMap.Kind.IMAGE);
        thumbnail(card);
        assertNotNull(thumbnail()); assertEquals(0, HostFrame.calls.get());
        assertNull(adapter.mpvObservation(card));
    }

    void videoThumbnail() throws Exception {
        ContentMap.Card card = card(video(), ContentMap.Kind.VIDEO);
        thumbnail(card);
        green(thumbnail());
        assertEquals(1, HostFrame.calls.get());
        JSONObject platform = adapter.platformObservation(card), mpv = adapter.mpvObservation(card);
        assertEquals("org.diemobile.platform-media-preview", platform.getString("schema"));
        assertEquals("731", platform.getJSONObject("metadata").getString("durationMs"));
        assertTrue(platform.isNull("decodedWidth"));
        assertTrue(platform.isNull("actualTimestampUs"));
        assertEquals("org.diemobile.mpv-frame-preview", mpv.getString("schema"));
        assertFalse(mpv.getJSONObject("nativeReport").getBoolean("frameTimestampVerified"));
    }

    @Test public void refusedVideoFrameKeepsPlatformMetadataSeparateFromRealMpvPixels() throws Exception {
        videoThumbnail(); assertEquals(1, Video.frames);
    }

    @Test public void platformPixelPolicyRefusalDoesNotAdmitAnotherDecoder() throws Exception {
        Images.oversized = true;
        ContentMap.Card image = card(png(), ContentMap.Kind.IMAGE);
        thumbnail(image);
        assertNull(thumbnail()); assertEquals(0, HostFrame.calls.get());
        adapter.close(); input.close();
        Video.oversized = true;
        ContentMap.Card video = card(video(), ContentMap.Kind.VIDEO);
        thumbnail(video);
        assertNull(thumbnail()); assertEquals(0, HostFrame.calls.get());
        JSONObject platform = adapter.platformObservation(video);
        assertTrue(platform.getBoolean("framePolicyRefused"));
        assertTrue(platform.getString("frameError").contains("pixel budget"));
        assertEquals("731", platform.getJSONObject("metadata").getString("durationMs"));
    }

    @Test public void sourceChangesAndCancellationAfterRefusalNeverInvokeMpv() throws Exception {
        ContentMap.Card card = card(png(), ContentMap.Kind.IMAGE);
        AtomicBoolean stop = new AtomicBoolean();
        Images.onBounds = () -> stop.set(true);
        refusal(() -> StillImagePreview.image(RuntimeEnvironment.getApplication(), input,
                card.range(input.size), 256, true, stop::get));
        assertEquals(0, HostFrame.calls.get());
        Images.onBounds = () -> { throw new android.os.OperationCanceledException("Fixture cancellation"); };
        refusal(() -> StillImagePreview.image(RuntimeEnvironment.getApplication(), input,
                card.range(input.size), 256, true, () -> false));
        assertEquals(0, HostFrame.calls.get());
        Images.onBounds = () -> {
            try (RandomAccessFile changed = new RandomAccessFile(input.file, "rw")) {
                changed.write(1);
            } catch (IOException error) { throw new IllegalStateException(error); }
        };
        refusal(() -> StillImagePreview.image(RuntimeEnvironment.getApplication(), input,
                card.range(input.size), 256, true, () -> false));
        assertEquals(0, HostFrame.calls.get());
    }

    @Test public void emptyForeignUnknownAndOversizeAutomaticRangesNeverReachDecoders() throws Exception {
        ContentMap.Card card = card(png(), ContentMap.Kind.IMAGE);
        refusal(() -> StillImagePreview.image(RuntimeEnvironment.getApplication(), input,
                new FileRange(input.size, 0, 0), 256, true, () -> false));
        refusal(() -> StillImagePreview.image(RuntimeEnvironment.getApplication(), input,
                new FileRange(input.size + 1, 0, 1), 256, true, () -> false));
        ContentMap.Card unknown = new ContentMap.Card(ContentMap.Kind.IMAGE, "Unknown extent",
                "png", 0, null, "", 0);
        AtomicInteger scheduled = new AtomicInteger();
        adapter = new ContentGalleryAdapter(RuntimeEnvironment.getApplication(),
                Collections.singletonList(unknown), input, task -> scheduled.incrementAndGet());
        adapter.getView(0, null, new LinearLayout(RuntimeEnvironment.getApplication()));
        assertEquals(0, scheduled.get());
        input.close();
        File large = temporary.resolve("large.bin").toFile();
        try (RandomAccessFile file = new RandomAccessFile(large, "rw")) {
            file.setLength(StillImagePreview.MAX_AUTOMATIC_BYTES + 1);
        }
        input = InputSnapshot.acquire(large, temporary.toFile(), large.length(), () -> false);
        refusal(() -> StillImagePreview.image(RuntimeEnvironment.getApplication(), input,
                new FileRange(input.size, 0, input.size), 256, true, () -> false));
        assertEquals(0, Images.calls.get()); assertEquals(0, HostFrame.calls.get());
    }

    @Test public void closingAPageAfterDecodePreventsLatePublication() throws Exception {
        ContentMap.Card card = card(png(), ContentMap.Kind.IMAGE);
        adapter = new ContentGalleryAdapter(RuntimeEnvironment.getApplication(),
                Collections.singletonList(card), input, worker);
        adapter.getView(0, null, new LinearLayout(RuntimeEnvironment.getApplication()));
        worker.submit(() -> {}).get(30, TimeUnit.SECONDS);
        assertEquals(1, HostFrame.calls.get());
        adapter.close();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(thumbnail()); assertNull(adapter.mpvObservation(card));
        input.checkIdentity(() -> false);
    }

    private void openPreview(byte[] payload, String preferred) throws Exception {
        File source = temporary.resolve("preview.bin").toFile();
        Files.write(source.toPath(), payload);
        input = InputSnapshot.acquire(source,
                new File(RuntimeEnvironment.getApplication().getCacheDir(), "content-previews"),
                1024 * 1024, () -> false);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), ContentPreviewActivity.class)
                .putExtra("path", input.file.getAbsolutePath()).putExtra("sourceSize", input.size)
                .putExtra("sha256", input.sha256).putExtra("name", "owned-mpv-fixture")
                .putExtra("preferred", preferred);
        controller = Robolectric.buildActivity(ContentPreviewActivity.class, intent).setup().visible();
        activity = controller.get();
        settlePreview();
    }

    private void settlePreview() throws Exception {
        for (int i = 0; i < 8; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {}).get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private Bitmap previewBitmap() {
        ImageView view = activity.getWindow().getDecorView().findViewWithTag("content-image");
        assertNotNull(ReflectionHelpers.<TextView>getField(activity, "status").getText().toString(), view);
        return ((BitmapDrawable) view.getDrawable()).getBitmap();
    }

    @Test public void explicitImageFallbackAndManualStillModeSurviveRecreationWithoutLosingBytes()
            throws Exception {
        openPreview(png(), "IMAGE");
        redGreen(previewBitmap());
        assertEquals(1, HostFrame.calls.get());
        assertNotNull(ReflectionHelpers.getField(activity, "platformPreview"));
        assertNotNull(ReflectionHelpers.getField(activity, "mpvPreview"));
        int platformCalls = Images.calls.get();
        ReflectionHelpers.setField(activity, "mode", "MPV still image");
        ReflectionHelpers.callInstanceMethod(activity, "render");
        settlePreview();
        redGreen(previewBitmap());
        assertEquals(platformCalls, Images.calls.get());
        controller.recreate(); activity = controller.get(); settlePreview();
        redGreen(previewBitmap());
        assertEquals("MPV still image", ReflectionHelpers.getField(activity, "mode"));
        assertArrayEquals(png(), Files.readAllBytes(input.file.toPath()));
    }

    @Test public void asynchronousVideoErrorSchedulesWorkerStillAndStaleResultsCannotReplaceHex()
            throws Exception {
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(1000, 0));
        openPreview(video(), "VIDEO");
        MediaPlayer player = ReflectionHelpers.getField(activity, "player");
        assertNotNull(player); assertFalse(player.isPlaying());
        assertEquals(0, HostFrame.calls.get());
        Shadows.shadowOf(player).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN,
                MediaPlayer.MEDIA_ERROR_UNSUPPORTED);
        settlePreview();
        green(previewBitmap());
        assertNull(ReflectionHelpers.getField(activity, "player"));
        JSONObject failure = ReflectionHelpers.getField(activity, "platformPreview");
        assertEquals(MediaPlayer.MEDIA_ERROR_UNSUPPORTED, failure.getInt("extra"));
        HostFrame.captured = new CountDownLatch(1); HostFrame.release = new CountDownLatch(1);
        ReflectionHelpers.callInstanceMethod(activity, "render");
        assertTrue(HostFrame.captured.await(30, TimeUnit.SECONDS));
        ReflectionHelpers.setField(activity, "mode", "Hex");
        ReflectionHelpers.callInstanceMethod(activity, "render");
        HostFrame.release.countDown();
        settlePreview();
        assertNull(activity.getWindow().getDecorView().findViewWithTag("content-image"));
        assertTrue(ReflectionHelpers.<TextView>getField(activity, "status").getText().toString().startsWith("Hex"));
    }

    @Test public void ioTimeoutServerAndUnknownMediaErrorsDoNotAdmitStillFallback() throws Exception {
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(1000, 0));
        openPreview(video(), "VIDEO");
        int[][] refused = {
            {MediaPlayer.MEDIA_ERROR_UNKNOWN, MediaPlayer.MEDIA_ERROR_IO},
            {MediaPlayer.MEDIA_ERROR_UNKNOWN, MediaPlayer.MEDIA_ERROR_TIMED_OUT},
            {MediaPlayer.MEDIA_ERROR_SERVER_DIED, 0},
            {MediaPlayer.MEDIA_ERROR_SERVER_DIED, MediaPlayer.MEDIA_ERROR_UNSUPPORTED},
            {MediaPlayer.MEDIA_ERROR_UNKNOWN, 0},
            {MediaPlayer.MEDIA_ERROR_UNKNOWN, -12345},
            {0, MediaPlayer.MEDIA_ERROR_MALFORMED},
            {MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK,
                    MediaPlayer.MEDIA_ERROR_UNSUPPORTED}
        };
        for (int[] error : refused) {
            MediaPlayer player = ReflectionHelpers.getField(activity, "player");
            assertNotNull(player);
            Shadows.shadowOf(player).invokeErrorListener(error[0], error[1]);
            settlePreview();
            assertNull(ReflectionHelpers.getField(activity, "player"));
            assertEquals(0, HostFrame.calls.get());
            assertEquals("Automatic", ReflectionHelpers.getField(activity, "mode"));
            assertNull(activity.getWindow().getDecorView().findViewWithTag("content-image"));
            String status = ReflectionHelpers.<TextView>getField(activity, "status")
                    .getText().toString();
            assertTrue(status, status.contains("what=" + error[0])
                    && status.contains("extra=" + error[1]));
            ReflectionHelpers.callInstanceMethod(activity, "render");
            settlePreview();
        }
        // A later documented malformed-data refusal can still produce a real still from the
        // unchanged owned bytes; prior IO/service errors do not poison the next request.
        MediaPlayer player = ReflectionHelpers.getField(activity, "player");
        Shadows.shadowOf(player).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN,
                MediaPlayer.MEDIA_ERROR_MALFORMED);
        settlePreview();
        green(previewBitmap());
        assertEquals(1, HostFrame.calls.get());
        assertEquals(MediaPlayer.MEDIA_ERROR_MALFORMED,
                ReflectionHelpers.<JSONObject>getField(activity, "platformPreview").getInt("extra"));
        input.checkIdentity(() -> false);
    }

    private interface Refused { void run() throws Exception; }
    private void refusal(Refused action) throws Exception {
        try {
            worker.submit(() -> { action.run(); return null; }).get(30, TimeUnit.SECONDS);
            fail("A refused preview was published");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause().toString(), expected.getCause() instanceof IOException
                    || expected.getCause() instanceof android.os.OperationCanceledException);
        }
    }
}

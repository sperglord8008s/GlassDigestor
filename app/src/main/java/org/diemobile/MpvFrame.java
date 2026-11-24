// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;

/**
 * One separately owned libmpv image observation; platform previews and native findings stay
 * separate.
 */
final class MpvFrame {
    static final long MAX_AUTOMATIC_BYTES = 32L * 1024 * 1024;

    /** Caller owns the bitmap; the detached report never owns a file or descriptor. */
    static final class Result {
        final Bitmap bitmap;
        private final String captured;

        private Result(Bitmap bitmap, JSONObject report) {
            this.bitmap = bitmap;
            captured = report.toString();
        }

        JSONObject json() throws Exception {
            return new JSONObject(captured);
        }

        String description() {
            return "MPV still image · "
                    + bitmap.getWidth()
                    + " × "
                    + bitmap.getHeight()
                    + " pixels\nOne current decoded image; actual timestamp unknown";
        }
    }

    static Result capture(
            Context context,
            InputSnapshot input,
            FileRange range,
            int maximumDimensionPixels,
            boolean automatic,
            BooleanSupplier stop)
            throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IOException("MPV image capture must run off the UI thread");
        try (BinaryWorkerClient runner = new BinaryWorkerClient(context, "mpv-frame")) {
            return capture(context, input, range, maximumDimensionPixels, automatic, stop, runner);
        }
    }

    /** Injected transport has the same file contract; its lifecycle belongs to the caller. */
    static Result capture(
            Context context,
            InputSnapshot input,
            FileRange range,
            int maximumDimensionPixels,
            boolean automatic,
            BooleanSupplier stop,
            MpvFrameRunner runner)
            throws Exception {
        MpvFrameReport.requireMaximum(maximumDimensionPixels);
        EvidenceRows.check(stop);
        if (context == null
                || input == null
                || range == null
                || runner == null
                || range.fileSize != input.size
                || range.length <= 0
                || range.length > MpvFrameReport.MAX_INPUT_BYTES)
            throw new IOException("MPV image needs a nonempty exact owned interval within 256 MiB");
        if (automatic && range.length > MAX_AUTOMATIC_BYTES)
            throw new IOException(
                    "Automatic MPV images are limited to 32 MiB; open the file explicitly");
        input.checkIdentity(stop);
        File root = new File(context.getCacheDir().getCanonicalFile(), "mpv-frames");
        if (!root.getCanonicalFile().equals(root))
            throw new IOException("MPV image cache root is an unexpected filesystem alias");
        if (!root.isDirectory() && !root.mkdirs())
            throw new IOException("Cannot create the private MPV image cache");
        File directory = Files.createTempDirectory(root.toPath(), "capture-").toFile();
        InputSnapshot staged = null;
        File raster = null;
        Bitmap bitmap = null;
        boolean published = false;
        try {
            Result result;
            Throwable pending = null;
            try {
                staged = OwnedContent.stageRange(input, range, directory, stop);
                raster = File.createTempFile("frame-", ".rgba", directory);
                EvidenceRows.check(stop);
                String encoded = runner.capture(staged.file, raster, maximumDimensionPixels, stop);
                EvidenceRows.check(stop);
                staged.checkIdentity(stop);
                input.checkIdentity(stop);
                MpvFrameReport report =
                        new MpvFrameReport(encoded, staged.size, maximumDimensionPixels, stop);
                MpvFrameReport.Raster pixels = report.readRaster(raster, stop);
                bitmap =
                        Bitmap.createBitmap(
                                pixels.argb, report.width, report.height, Bitmap.Config.ARGB_8888);
                bitmap.setHasAlpha(false);
                staged.checkIdentity(stop);
                input.checkIdentity(stop);
                report.verifyRaster(raster, pixels.sha256, stop);
                JSONObject observation =
                        new JSONObject()
                                .put("schema", "org.diemobile.mpv-frame-preview")
                                .put("schemaVersion", 1)
                                .put("sourceSha256", input.sha256)
                                .put("sourceSizeBytes", input.size)
                                .put("physicalOffset", range.offset)
                                .put("storedSizeBytes", range.length)
                                .put("decodedInputSha256", staged.sha256)
                                .put("decodedInputSizeBytes", staged.size)
                                .put("maximumDimensionPixels", maximumDimensionPixels)
                                .put("automatic", automatic)
                                .put("maximumAutomaticSourceBytes", MAX_AUTOMATIC_BYTES)
                                .put("rasterSha256", pixels.sha256)
                                .put("rasterSizeBytes", report.pixelBytes)
                                .put("renderedWidthPixels", report.width)
                                .put("renderedHeightPixels", report.height)
                                .put("nativeReport", report.json())
                                .put(
                                        "meaning",
                                        "A separate libmpv observation of one current decoded image"
                                            + " from exactly the selected stored bytes. Its"
                                            + " timestamp is unknown; source alpha is not"
                                            + " preserved. It does not change native finding"
                                            + " boundaries, verify every frame or complete file"
                                            + " integrity, or establish playback support.");
                result = new Result(bitmap, observation);
            } catch (Exception | Error failure) {
                pending = failure;
                throw failure;
            } finally {
                IOException cleanup = cleanup(directory, staged, raster);
                if (cleanup != null) {
                    if (pending != null) pending.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
            // Source/pixel checking, report serialization and private-file cleanup settle first.
            EvidenceRows.check(stop);
            published = true;
            return result;
        } finally {
            if (!published && bitmap != null) bitmap.recycle();
        }
    }

    private static IOException cleanup(File directory, InputSnapshot staged, File raster) {
        IOException failure = null;
        for (File file : new File[] {raster, staged == null ? null : staged.file, directory}) {
            if (file == null) continue;
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException error) {
                if (failure == null)
                    failure = new IOException("Cannot remove a private MPV image temporary", error);
                else failure.addSuppressed(error);
            }
        }
        return failure;
    }

    private MpvFrame() {}
}

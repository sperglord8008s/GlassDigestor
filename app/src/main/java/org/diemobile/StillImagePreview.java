// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Platform-first still previews; only a decoder refusal admits the isolated MPV fallback. */
final class StillImagePreview {
    static final long MAX_AUTOMATIC_BYTES = 32L * 1024 * 1024;
    static final int MAX_DIMENSION = MpvFrameRunner.MAXIMUM_DIMENSION_PIXELS;

    static final class Result {
        final Bitmap bitmap;
        final MediaFrame.Result platform;
        final MpvFrame.Result mpv;
        final String platformFailure, mpvFailure;

        Result(Bitmap bitmap, MediaFrame.Result platform, MpvFrame.Result mpv,
                String platformFailure, String mpvFailure) {
            this.bitmap = bitmap;
            this.platform = platform;
            this.mpv = mpv;
            this.platformFailure = platformFailure;
            this.mpvFailure = mpvFailure;
        }

        String description() {
            String value = platform != null ? platform.description() : platformFailure;
            if (mpv != null) return (value.isEmpty() ? "" : value + "\n") + mpv.description();
            if (!mpvFailure.isEmpty())
                return value + "\nMPV still image unavailable: " + mpvFailure;
            if (bitmap != null && platform == null)
                return "Platform decoded first frame " + bitmap.getWidth() + " × "
                        + bitmap.getHeight();
            return value;
        }
    }

    private static void check(InputSnapshot input, FileRange range, int target,
            boolean automatic, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        range.requireCurrentLength(input.size);
        range.requireOperationBudget();
        if (range.length <= 0) throw new IOException("A still preview needs a positive exact range");
        if (target < 1 || target > MAX_DIMENSION)
            throw new IOException("Still preview target must be 1–1536 pixels");
        if (automatic && target > 256)
            throw new IOException("Automatic still previews are limited to 256 pixels per edge");
        if (automatic && range.length > MAX_AUTOMATIC_BYTES)
            throw new IOException("Automatic still previews are limited to 32 MiB; open explicitly");
        input.checkIdentity(stop);
    }

    static Result image(Context context, InputSnapshot input, FileRange range, int target,
            boolean automatic, BooleanSupplier stop) throws Exception {
        check(input, range, target, automatic, stop);
        Bitmap bitmap = null;
        boolean success = false;
        try {
            try {
                bitmap = ContentPreview.image(input.file, range, target);
            } catch (ContentPreview.DecoderUnavailableException refusal) {
                // Recheck after the platform call: cancellation or changed bytes cannot become
                // an apparent codec refusal and must never start another decoder.
                input.checkIdentity(stop);
                return fallback(context, input, range, target, automatic, stop, null,
                        refusal.toString());
            }
            input.checkIdentity(stop);
            success = true;
            return new Result(bitmap, null, null, "", "");
        } finally {
            if (!success && bitmap != null) bitmap.recycle();
        }
    }

    static Result media(Context context, InputSnapshot input, FileRange range, int target,
            boolean automatic, boolean videoCandidate, BooleanSupplier stop) throws Exception {
        check(input, range, target, automatic, stop);
        MediaFrame.Result platform;
        try {
            platform = MediaFrame.read(input, range, target, stop);
        } catch (MediaFrame.DecoderUnavailableException refusal) {
            input.checkIdentity(stop);
            if (videoCandidate)
                return fallback(context, input, range, target, automatic, stop, null,
                        refusal.toString());
            return new Result(null, null, null, refusal.toString(), "");
        }
        try {
            input.checkIdentity(stop);
            if (platform.bitmap == null && !platform.framePolicyRefused
                    && ("yes".equals(platform.metadata.get("hasVideo"))
                            || videoCandidate && !"no".equals(platform.metadata.get("hasVideo"))))
                return fallback(context, input, range, target, automatic, stop, platform, "");
            return new Result(platform.bitmap, platform, null, "", "");
        } catch (Exception failure) {
            if (platform.bitmap != null) platform.bitmap.recycle();
            throw failure;
        }
    }

    private static Result fallback(Context context, InputSnapshot input, FileRange range, int target,
            boolean automatic, BooleanSupplier stop, MediaFrame.Result platform,
            String platformFailure) throws Exception {
        check(input, range, target, automatic, stop);
        MpvFrame.Result mpv = null;
        try {
            mpv = MpvFrame.capture(context, input, range, target, automatic, stop);
            input.checkIdentity(stop);
            return new Result(mpv.bitmap, platform, mpv, platformFailure, "");
        } catch (Exception refusal) {
            if (mpv != null && mpv.bitmap != null) mpv.bitmap.recycle();
            // Keep any platform metadata when the second decoder cannot produce a still. IO,
            // source changes and cancellation still fail rather than publishing a stale result.
            input.checkIdentity(stop);
            return new Result(null, platform, null, platformFailure, refusal.toString());
        }
    }

    private StillImagePreview() {}
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Platform preview observations of one exact stored interval, separate from native findings. */
final class MediaFrame {
    static final class DecoderUnavailableException extends IOException {
        DecoderUnavailableException(RuntimeException cause) {
            super("Platform media decoder refused these bytes", cause);
        }
    }
    private static final Map<String, Integer> KEYS;

    static {
        Map<String, Integer> keys = new LinkedHashMap<>();
        keys.put("mimeType", MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
        keys.put("durationMs", MediaMetadataRetriever.METADATA_KEY_DURATION);
        keys.put("videoWidth", MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        keys.put("videoHeight", MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        keys.put("rotationDegrees", MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        keys.put("bitrate", MediaMetadataRetriever.METADATA_KEY_BITRATE);
        keys.put("trackCount", MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
        keys.put("hasVideo", MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
        keys.put("hasAudio", MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
        KEYS = Collections.unmodifiableMap(keys);
    }

    /** Caller owns the returned bitmap. Raw platform values retain null/unknown states. */
    static final class Result {
        final Bitmap bitmap;
        final Map<String, String> metadata;
        final String frameStatus;
        final boolean framePolicyRefused;
        private final String report;

        private Result(
                Bitmap bitmap, Map<String, String> metadata, String status,
                boolean framePolicyRefused, JSONObject report)
                throws Exception {
            this.bitmap = bitmap;
            this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            frameStatus = status;
            this.framePolicyRefused = framePolicyRefused;
            this.report = report.toString();
        }

        JSONObject report() throws Exception {
            return new JSONObject(report);
        }

        String description() {
            StringBuilder text = new StringBuilder("Platform preview · ");
            text.append(value("mimeType"));
            if (metadata.get("durationMs") != null)
                text.append(" · ").append(value("durationMs")).append(" ms");
            if (metadata.get("videoWidth") != null && metadata.get("videoHeight") != null)
                text.append("\n")
                        .append(value("videoWidth"))
                        .append(" × ")
                        .append(value("videoHeight"));
            if (metadata.get("rotationDegrees") != null)
                text.append(" · rotation ").append(value("rotationDegrees")).append("°");
            return text.append("\n").append(frameStatus).toString();
        }

        private String value(String key) {
            String value = metadata.get(key);
            return value == null ? "unknown" : value;
        }
    }

    static Result read(InputSnapshot input, FileRange range, int target, BooleanSupplier stop)
            throws Exception {
        if (target < 1 || target > 512)
            throw new IOException("Media thumbnail target must be 1–512 pixels");
        range.requireCurrentLength(input.size);
        range.requireOperationBudget();
        if (range.length == 0) throw new IOException("A media preview needs a nonempty interval");
        input.checkIdentity(stop);
        Map<String, String> metadata = new LinkedHashMap<>();
        JSONObject errors = new JSONObject();
        Bitmap bitmap = null;
        boolean success = false;
        String status;
        String frameError = null;
        boolean framePolicyRefused = false;
        try {
            try (FileInputStream source = new FileInputStream(input.file)) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    // No path/URI overload: the decoder receives only the selected stored bytes.
                    try { retriever.setDataSource(source.getFD(), range.offset, range.length); }
                    catch (RuntimeException refusal) {
                        if (refusal instanceof android.os.OperationCanceledException
                                || refusal instanceof SecurityException) throw refusal;
                        throw new DecoderUnavailableException(refusal);
                    }
                    EvidenceRows.check(stop);
                    for (Map.Entry<String, Integer> key : KEYS.entrySet()) {
                        String value = null;
                        try {
                            value = retriever.extractMetadata(key.getValue());
                        } catch (RuntimeException unavailable) {
                            if (unavailable instanceof android.os.OperationCanceledException
                                    || unavailable instanceof SecurityException) throw unavailable;
                            errors.put(key.getKey(), unavailable.toString());
                        }
                        metadata.put(key.getKey(), value);
                    }
                    EvidenceRows.check(stop);
                    if (!"yes".equals(metadata.get("hasVideo"))) {
                        status = "No platform video-track confirmation; no frame requested";
                    } else if (Build.VERSION.SDK_INT < 27) {
                        status =
                                "Scaled video frames require Android API 27+; in-app playback"
                                        + " remains available";
                    } else {
                        try {
                            bitmap =
                                    retriever.getScaledFrameAtTime(
                                            0,
                                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                            target,
                                            target);
                        } catch (RuntimeException unsupported) {
                            if (unsupported instanceof android.os.OperationCanceledException
                                    || unsupported instanceof SecurityException) throw unsupported;
                            frameError = unsupported.toString();
                        }
                        if (bitmap == null) {
                            status =
                                    frameError == null
                                            ? "Platform decoder returned no video frame; metadata"
                                                    + " remains available"
                                            : "Platform decoder refused the frame; metadata remains"
                                                    + " available";
                        } else {
                            if (bitmap.getWidth() <= 0
                                    || bitmap.getHeight() <= 0
                                    || bitmap.getWidth() > target
                                    || bitmap.getHeight() > target
                                    || (long) bitmap.getWidth() * bitmap.getHeight()
                                            > (long) target * target) {
                                frameError = "Decoded media frame exceeded its pixel budget";
                                framePolicyRefused = true;
                                bitmap.recycle();
                                bitmap = null;
                                status =
                                        "Platform frame exceeded its budget; metadata remains"
                                            + " available";
                            } else {
                                status =
                                        "Decoded keyframe requested at 0 µs · actual frame"
                                            + " timestamp unknown";
                            }
                        }
                    }
                } finally {
                    retriever.release();
                }
            }
            input.checkIdentity(stop);
            JSONObject values = new JSONObject();
            for (Map.Entry<String, String> entry : metadata.entrySet())
                values.put(
                        entry.getKey(),
                        entry.getValue() == null ? JSONObject.NULL : entry.getValue());
            JSONObject report =
                    new JSONObject()
                            .put("schema", "org.diemobile.platform-media-preview")
                            .put("schemaVersion", 1)
                            .put("sourceSha256", input.sha256)
                            .put("sourceSizeBytes", input.size)
                            .put("physicalOffset", range.offset)
                            .put("storedSizeBytes", range.length)
                            .put("sdk", Build.VERSION.SDK_INT)
                            .put("metadata", values)
                            .put("metadataErrors", errors)
                            .put("frameError", frameError == null ? JSONObject.NULL : frameError)
                            .put("framePolicyRefused", framePolicyRefused)
                            .put("frameStatus", status)
                            .put("requestedTimeUs", 0)
                            .put("selection", "OPTION_CLOSEST_SYNC")
                            .put("actualTimestampUs", JSONObject.NULL)
                            .put("maximumWidth", target)
                            .put("maximumHeight", target)
                            .put(
                                    "decodedWidth",
                                    bitmap == null ? JSONObject.NULL : bitmap.getWidth())
                            .put(
                                    "decodedHeight",
                                    bitmap == null ? JSONObject.NULL : bitmap.getHeight())
                            .put(
                                    "meaning",
                                    "Transient platform preview observation of the selected stored"
                                        + " interval. It does not change native finding bounds,"
                                        + " validate every stream/frame, or establish playback"
                                        + " support, complete validity or safety. Full media"
                                        + " information remains available in MPV codecs / streams /"
                                        + " tags.");
            Result result = new Result(bitmap, metadata, status, framePolicyRefused, report);
            success = true;
            return result;
        } finally {
            if (!success && bitmap != null) bitmap.recycle();
        }
    }

    private MediaFrame() {}
}

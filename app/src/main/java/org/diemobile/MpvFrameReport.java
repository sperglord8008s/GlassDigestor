// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Detached native image contract and exact packed-pixel validation, with no Android dependency. */
final class MpvFrameReport {
    static final int MAX_DIMENSION = MpvFrameRunner.MAXIMUM_DIMENSION_PIXELS;
    static final long MAX_INPUT_BYTES = MpvFrameRunner.MAXIMUM_INPUT_BYTES;
    static final long MAX_READ_BYTES = 32L * 1024 * 1024;
    static final int MAX_PIXEL_BYTES = MAX_DIMENSION * MAX_DIMENSION * 4;
    static final int MAX_NATIVE_RASTER_BYTES = 16 * 1024 * 1024;
    static final int MAX_REPORT_BYTES = 32 * 1024 * 1024;
    private static final String[] PROPERTIES = {
        "mpv-version",
        "ffmpeg-version",
        "file-format",
        "video-params",
        "video-out-params",
        "video-frame-info",
        "track-list"
    };
    private static final String[][] GUARDS = {
        {"config", "no"},
        {"load-scripts", "no"},
        {"load-auto-profiles", "no"},
        {"autoload-files", "no"},
        {"access-references", "no"},
        {"ytdl", "no"},
        {"osc", "no"},
        {"input-default-bindings", "no"},
        {"input-vo-keyboard", "no"},
        {"input-terminal", "no"},
        {"terminal", "no"},
        {"hwdec", "no"},
        {"vo", "null"},
        {"ao", "null"},
        {"pause", "yes"},
        {"idle", "yes"},
        {"keep-open", "always"},
        {"sid", "no"},
        {"cache", "no"},
        {"demuxer", "+lavf"},
        {"demuxer-lavf-o", "protocol_whitelist=dieblob"},
        {"demuxer-max-bytes", "16MiB"},
        {"demuxer-max-back-bytes", "0"},
        {"demuxer-readahead-secs", "0"},
        {"demuxer-lavf-probesize", "8388608"},
        {"demuxer-lavf-analyzeduration", "10"}
    };
    final int width, height, rowBytes, pixelBytes;
    private final String captured;

    static void requireMaximum(int maximumDimensionPixels) throws IOException {
        MpvFrameRunner.requireMaximum(maximumDimensionPixels);
    }

    MpvFrameReport(String encoded, long inputSize, int maximumDimensionPixels, BooleanSupplier stop)
            throws Exception {
        requireMaximum(maximumDimensionPixels);
        EvidenceRows.check(stop);
        if (inputSize <= 0 || inputSize > MAX_INPUT_BYTES)
            throw new IOException("MPV image input must be nonempty and at most 256 MiB");
        if (encoded == null || encoded.length() > MAX_REPORT_BYTES)
            throw new IOException("MPV image worker returned no complete bounded report");
        JSONObject report = new JSONObject(encoded);
        if (!(report.get("ok") instanceof Boolean) || !report.getBoolean("ok"))
            throw new IOException(report.optString("error", "MPV returned no decoded image"));
        if (!text(report, "schema").equals("org.diemobile.mpv-frame")
                || integer(report, "schemaVersion") != 1
                || integer(report, "adapterVersion") != 1
                || !text(report, "engine").equals("libmpv")
                || !text(report, "headersSourceRevision").equals(MpvDocument.SOURCE)
                || integer(report, "headersApiVersion") != ((2L << 16) | 5)
                || integer(report, "fileSizeBytes") != inputSize
                || integer(report, "maximumDimensionPixels") != maximumDimensionPixels)
            throw new IOException("MPV image schema, source or request identity differs");
        long runtime = integer(report, "runtimeApiVersion");
        if (runtime < (2L << 16) || runtime > 0xffffffffL || runtime >> 16 != 2)
            throw new IOException("MPV image runtime API is outside the compatible 2.x contract");
        String origin = text(report, "engineBinaryOrigin");
        if (origin.equals("mpv-android-2026-09-17-arm64")) {
            if (!text(report, "engineSourceRevision").equals(MpvDocument.SOURCE))
                throw new IOException("MPV Android image engine differs from its pinned source");
        } else if (!origin.equals("external-host-runtime")
                || !report.has("engineSourceRevision")
                || !report.isNull("engineSourceRevision"))
            throw new IOException("MPV image runtime origin is unknown");
        for (String flag :
                new String[] {
                    "referenceAccessEnabled",
                    "configEnabled",
                    "scriptsEnabled",
                    "sidecarLoadingEnabled",
                    "networkPermissionRequired",
                    "sourceAlphaPreserved",
                    "everyFrameRendered",
                    "completeFileValidityVerified",
                    "frameTimestampVerified"
                }) flag(report, flag, false);
        for (String flag :
                new String[] {
                    "decodedCurrentFrameConfirmed",
                    "displayGeometryPreflightPassed",
                    "squarePixelRaster"
                }) flag(report, flag, true);
        if (!text(report, "pixelFormat").equals("rgba8")
                || !text(report, "alphaMode").equals("opaque")
                || !text(report, "nativeScreenshotFormat").equals("bgr0")
                || !text(report, "audioVideoOutput").equals("null/paused")
                || integer(report, "maxInputBytes") != MAX_INPUT_BYTES
                || integer(report, "maxReadBytes") != MAX_READ_BYTES
                || integer(report, "nativeDeadlineMs") != 25000
                || integer(report, "maxNativeRasterBytes") != MAX_NATIVE_RASTER_BYTES
                || integer(report, "maxPixelBytes") != MAX_PIXEL_BYTES
                || integer(report, "maxOutputBytes") != MAX_REPORT_BYTES
                || integer(report, "maxNodes") != 100000
                || integer(report, "ungrantedCallbackUris") != 0
                || integer(report, "streamOpenCalls") <= 0
                || integer(report, "seekCalls") < 0
                || integer(report, "bytesRead") <= 0
                || integer(report, "bytesRead") > MAX_READ_BYTES)
            throw new IOException("MPV image pixel, guard or work budget contract differs");
        long wide = integer(report, "widthPixels"), high = integer(report, "heightPixels");
        if (wide < 1
                || high < 1
                || wide > maximumDimensionPixels
                || high > maximumDimensionPixels
                || integer(report, "rowBytes") != wide * 4
                || integer(report, "pixelBytes") != wide * high * 4
                || wide * high * 4 > MAX_PIXEL_BYTES)
            throw new IOException(
                    "MPV image geometry exceeds the requested edge or packed-byte bounds");
        width = (int) wide;
        height = (int) high;
        rowBytes = width * 4;
        pixelBytes = rowBytes * height;
        validateProperties(report, stop);
        validateOptions(report, stop);
        if (report.has("worker")) {
            JSONObject worker = report.getJSONObject("worker");
            flag(worker, "isolatedUid", true);
            if (!text(worker, "engine").equals("mpv-frame")
                    || integer(worker, "workerUid") < 0
                    || integer(worker, "applicationUid") < 0
                    || integer(worker, "workerUid") == integer(worker, "applicationUid")
                    || integer(worker, "analysisDeadlineMs") != 0
                    || integer(worker, "requestDeadlineMs") != 45000
                    || integer(worker, "maximumDimensionPixels") != maximumDimensionPixels
                    || text(worker, "requestToken").isEmpty())
                throw new IOException(
                        "MPV image worker capture has an inconsistent process or request scope");
        }
        EvidenceRows.check(stop);
        captured = report.toString();
        EvidenceRows.check(stop);
    }

    JSONObject json() throws Exception {
        return new JSONObject(captured);
    }

    private static void validateProperties(JSONObject report, BooleanSupplier stop)
            throws Exception {
        JSONArray properties = report.getJSONArray("properties");
        if (properties.length() != PROPERTIES.length
                || integer(report, "propertyCount") != PROPERTIES.length)
            throw new IOException("MPV image property capture is incomplete");
        Set<String> wanted = new HashSet<>(Arrays.asList(PROPERTIES));
        MpvNodeValidator nodes = new MpvNodeValidator();
        for (int i = 0; i < properties.length(); i++) {
            EvidenceRows.check(stop);
            JSONObject property = properties.getJSONObject(i);
            if (!wanted.remove(text(property, "name")) || !property.has("node"))
                throw new IOException(
                        "MPV image property capture is duplicated or outside its scope");
            long error = integer(property, "errorCode");
            if (error == 0) nodes.validate(property.getJSONObject("node"), 0, stop);
            else if (error > 0 || !property.isNull("node"))
                throw new IOException("MPV image property availability is inconsistent");
        }
        if (!wanted.isEmpty() || integer(report, "nodeCount") != nodes.count())
            throw new IOException("MPV image typed-node count differs from its complete capture");
    }

    private static void validateOptions(JSONObject report, BooleanSupplier stop) throws Exception {
        String filter = text(report, "filter");
        if (filter.isEmpty() || filter.length() > 1024)
            throw new IOException("MPV image filter is absent or outside its fixed command budget");
        Map<String, String> wanted = new HashMap<>();
        for (String[] guard : GUARDS) wanted.put(guard[0], guard[1]);
        wanted.put("vf", filter);
        wanted.put("screenshot-sw", "yes");
        JSONArray options = report.getJSONArray("options");
        if (options.length() != wanted.size())
            throw new IOException("MPV image guard-option capture is incomplete");
        for (int i = 0; i < options.length(); i++) {
            EvidenceRows.check(stop);
            JSONObject option = options.getJSONObject(i);
            String expected = wanted.remove(text(option, "name"));
            if (expected == null || !expected.equals(text(option, "value")))
                throw new IOException("MPV image option differs from its mandatory guard");
        }
    }

    static final class Raster {
        final int[] argb;
        final String sha256;

        private Raster(int[] argb, String sha256) {
            this.argb = argb;
            this.sha256 = sha256;
        }
    }

    Raster readRaster(File file, BooleanSupplier stop) throws Exception {
        if (!file.isFile() || file.length() != pixelBytes)
            throw new IOException(
                    "MPV raster output is missing, truncated or exceeds its packed length");
        int[] colors = new int[width * height];
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] row = new byte[rowBytes];
        try (InputStream input = new FileInputStream(file)) {
            for (int y = 0; y < height; y++) {
                EvidenceRows.check(stop);
                int at = 0;
                while (at < row.length) {
                    EvidenceRows.check(stop);
                    int count = input.read(row, at, row.length - at);
                    if (count < 0)
                        throw new EOFException("MPV raster shortened while reading pixels");
                    if (count == 0) continue;
                    at += count;
                }
                digest.update(row);
                for (int x = 0; x < width; x++) {
                    int index = x * 4;
                    if ((row[index + 3] & 255) != 255)
                        throw new IOException("MPV rgba8 raster contains non-opaque alpha bytes");
                    // Bitmap integer colors are ARGB values, independent of native byte order.
                    colors[y * width + x] =
                            0xff000000
                                    | (row[index] & 255) << 16
                                    | (row[index + 1] & 255) << 8
                                    | (row[index + 2] & 255);
                }
            }
            if (input.read() != -1)
                throw new IOException("MPV raster grew beyond its packed output");
        }
        String sha256 = Inspector.toHex(digest.digest());
        verifyRaster(file, sha256, stop);
        return new Raster(colors, sha256);
    }

    void verifyRaster(File file, String expectedSha256, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (!file.isFile() || file.length() != pixelBytes)
            throw new IOException("MPV raster output length changed");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (InputStream input = new FileInputStream(file)) {
            byte[] block = new byte[65536];
            int length;
            while ((length = input.read(block)) >= 0) {
                EvidenceRows.check(stop);
                if (length > pixelBytes - count) throw new IOException("MPV raster output grew");
                digest.update(block, 0, length);
                count += length;
            }
        }
        if (count != pixelBytes
                || file.length() != pixelBytes
                || !expectedSha256.equals(Inspector.toHex(digest.digest())))
            throw new IOException("MPV raster output SHA-256 changed");
        EvidenceRows.check(stop);
    }

    private static long integer(JSONObject value, String field) throws Exception {
        Object number = value.get(field);
        if (!(number instanceof Integer) && !(number instanceof Long))
            throw new IOException("MPV image accounting requires exact integers: " + field);
        return ((Number) number).longValue();
    }

    private static String text(JSONObject value, String field) throws Exception {
        Object text = value.get(field);
        if (!(text instanceof String))
            throw new IOException("MPV image field is not text: " + field);
        return (String) text;
    }

    private static void flag(JSONObject value, String field, boolean expected) throws Exception {
        Object flag = value.get(field);
        if (!(flag instanceof Boolean) || ((Boolean) flag) != expected)
            throw new IOException("MPV image scope flag differs: " + field);
    }
}

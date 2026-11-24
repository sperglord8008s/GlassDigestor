// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Arrays;

/** Real 80 MiB/256 MiB sparse blobs and scan/cache seams without full Java arrays. */
public final class PhotoRecStreamingTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static JSONObject candidate(JSONObject report, String name, long offset)
            throws Exception {
        PhotoRecReport rows = new PhotoRecReport(report);
        for (int i = 0; i < report.getJSONArray("rows").length(); i++) {
            JSONObject row = rows.row(i);
            if (row.getString("name").equals(name)
                    && row.optLong("candidateStartPhysicalOffset", -1) == offset) return row;
        }
        throw new AssertionError("Missing " + name + " at " + offset);
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        byte[] png = MetadataFixtureFactory.png();
        long size = 80L * 1024 * 1024 + 17;
        long[] positions = {
            65536 - 5L, 1048576 - 5L, 64L * 1024 * 1024 + 65536 - 5L, size - png.length
        };
        File source = directory.resolve("large-sparse-blob.bin").toFile();
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.setLength(size);
            for (long position : positions) {
                file.seek(position);
                file.write(png);
            }
        }
        JSONObject report = new JSONObject(PhotoRecEngine.scanPath(source.toString()));
        require(report.optBoolean("ok"), report.optString("error"));
        require(
                report.getString("adapter").equals("diemobile-photorec-6")
                        && report.getLong("fileSizeBytes") == size,
                "Streaming source/version mismatch");
        require(
                !report.getBoolean("inputSizedBufferAllocation")
                        && report.getLong("maximumInputReadBufferBytes") == 1376256,
                "Input-size allocation returned");
        require(
                report.getLong("sourceReadBytes") >= size
                        && report.getLong("sourceReadOperations") >= 81,
                "Complete source scan accounting absent");
        for (long position : positions) {
            JSONObject row = candidate(report, "png", position);
            require(
                    row.getLong("reportedSizeBytes") == png.length
                            && row.getBoolean("headerAccepted"),
                    "Seam/late/EOF PNG extent changed");
            require(
                    row.getLong("headerWindowSourceBytes") == Math.min(65536, size - position),
                    "Header source/padding interval changed");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            RangeAnalysis.copy(
                    source, new FileRange(size, position, png.length), bytes, () -> false);
            require(Arrays.equals(bytes.toByteArray(), png), "Recovered source bytes differ");
        }
        try (RandomAccessFile file = new RandomAccessFile(source, "r")) {
            Field descriptor = FileDescriptor.class.getDeclaredField("fd");
            descriptor.setAccessible(true);
            file.seek(12345);
            JSONObject direct =
                    new JSONObject(PhotoRecEngine.scanFd(descriptor.getInt(file.getFD())));
            require(
                    direct.similar(report)
                            && file.getFilePointer() == 12345
                            && file.getFD().valid(),
                    "Large descriptor identity/position changed");
        }
        File snapshots = directory.resolve("large-snapshot").toFile();
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        source,
                        snapshots,
                        "large.bin",
                        (file, stop) -> PhotoRecEngine.scanPath(file.toString()))) {
            EvidenceRows bounded = backend.open("bounded", () -> false);
            require(
                    bounded.size() >= positions.length
                            && backend.provenance().getJSONObject("input").getLong("sizeBytes")
                                    == size,
                    "Provider retained its old 64 MiB acquisition cap");
        }
        require(snapshots.list().length == 0, "Large private snapshot leaked");
        // Same transport remains readable for an original <=64 MiB adapter-2 capture.
        File small = directory.resolve("legacy.png").toFile();
        Files.write(small.toPath(), png);
        JSONObject legacy = new JSONObject(PhotoRecEngine.scanPath(small.toString()));
        legacy.put("adapter", "diemobile-photorec-2")
                .put("maximumInputBytes", 64L * 1024 * 1024)
                .put("maximumCandidateDataBufferBytes", 8L * 1024 * 1024);
        require(new PhotoRecReport(legacy).row(0) != null, "Legacy compact capture rejected");
        JSONObject bad = new JSONObject(report.toString());
        bad.put("maximumInputReadBufferBytes", size);
        try {
            new PhotoRecReport(bad);
            throw new AssertionError("Unbounded worker buffer claim accepted");
        } catch (IOException expected) {
        }
        // Write every byte physically with a nonzero filler, rather than relying
        // only on sparse-zero reads. Avoid a constant searched magic explosion.
        boolean[] unsafe = new boolean[256];
        JSONArray patterns = report.getJSONArray("patterns");
        for (int i = 0; i < patterns.length(); i++) {
            JSONObject pattern = patterns.getJSONObject(i);
            if (!pattern.getBoolean("searched")) continue;
            byte[] magic = java.util.HexFormat.of().parseHex(pattern.getString("magicHex"));
            boolean same = true;
            for (byte value : magic) if (value != magic[0]) same = false;
            if (same) unsafe[magic[0] & 255] = true;
        }
        int filler = 0xa5;
        while (unsafe[filler]) filler = (filler + 1) & 255;
        byte[] chunk = new byte[1024 * 1024];
        Arrays.fill(chunk, (byte) filler);
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.setLength(0);
            long remaining = size;
            while (remaining > 0) {
                int count = (int) Math.min(chunk.length, remaining);
                file.write(chunk, 0, count);
                remaining -= count;
            }
            for (long position : positions) {
                file.seek(position);
                file.write(png);
            }
        }
        JSONObject written = new JSONObject(PhotoRecEngine.scanPath(source.toString()));
        require(written.optBoolean("ok"), written.optString("error"));
        for (long position : positions)
            require(
                    candidate(written, "png", position).getLong("reportedSizeBytes") == png.length,
                    "Fully written large-file extent changed");
        require(
                written.getLong("sourceReadBytes") >= size,
                "Fully written file was not completely read");
        Files.delete(source.toPath());
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.setLength(PhotoRecBackend.MAX_BYTES);
        }
        JSONObject maximum = new JSONObject(PhotoRecEngine.scanPath(source.toString()));
        require(maximum.optBoolean("ok"), maximum.optString("error"));
        require(
                maximum.getLong("fileSizeBytes") == PhotoRecBackend.MAX_BYTES
                        && maximum.getInt("candidateCount") == 0,
                "Maximum sparse blob was not completely searched");
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.setLength(PhotoRecBackend.MAX_BYTES + 1);
        }
        require(
                !new JSONObject(PhotoRecEngine.scanPath(source.toString())).getBoolean("ok"),
                "Oversized stream accepted");
        Files.delete(source.toPath());
        System.out.println(
                "PhotoRec streamed JNI passed: 80 MiB exact PNGs at header/1 MiB/64 MiB/EOF seams,"
                    + " independent bytes, fully written 80 MiB data, large provider ownership, 256"
                    + " MiB complete scan, caps and legacy transport; 1.3125 MiB input-read buffer"
                    + " bound.");
    }
}

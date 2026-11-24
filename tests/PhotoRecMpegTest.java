// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Generated packet streams, exact exported bytes and strict optional-work/owner contracts. */
public final class PhotoRecMpegTest {
    private static final long MIB = 1024L * 1024;
    private static final int PREFIX = 37;
    private static final byte[] PACK = HexFormat.of().parseHex("000001ba4400040004014338dbf8");
    private static final String SUPPRESSED = PhotoRecReport.MPEG_DATA_MODE;

    private interface Mutation {
        void apply(JSONObject report) throws Exception;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static byte[] pack(boolean restart) {
        byte[] result = PACK.clone();
        if (!restart) result[5] = 1; // Valid nonzero SCR, independent of packet lengths.
        return result;
    }

    private static byte[] sector(boolean restart) {
        byte[] bytes = new byte[2048];
        Arrays.fill(bytes, (byte) 0xa7);
        System.arraycopy(pack(restart), 0, bytes, 0, PACK.length);
        int at = PACK.length, payload = bytes.length - at - 6;
        bytes[at] = 0;
        bytes[at + 1] = 0;
        bytes[at + 2] = 1;
        bytes[at + 3] = (byte) 0xbe;
        bytes[at + 4] = (byte) (payload >>> 8);
        bytes[at + 5] = (byte) payload;
        return bytes;
    }

    private static void gap(OutputStream output, int count) throws Exception {
        byte[] bytes = new byte[count];
        Arrays.fill(bytes, (byte) 0x5a); // Not a supported MPEG packet start.
        output.write(bytes);
    }

    private static String sectors(File file, int count, boolean firstRestart, int secondRestart)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (OutputStream output = Files.newOutputStream(file.toPath())) {
            gap(output, PREFIX);
            for (int i = 0; i < count; i++) {
                byte[] bytes = sector(i == 0 ? firstRestart : i == secondRestart);
                output.write(bytes);
                digest.update(bytes);
            }
            gap(output, 64);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** One pack plus large padding packets avoids tens of thousands of optional pack rows. */
    private static void longPackets(File file, long size) throws Exception {
        try (OutputStream output = Files.newOutputStream(file.toPath())) {
            gap(output, PREFIX);
            output.write(pack(true));
            long remaining = size - PACK.length;
            while (remaining > 0) {
                int packetSize = (int) Math.min(remaining, 65541);
                if (remaining - packetSize > 0 && remaining - packetSize < 6) packetSize -= 6;
                require(packetSize >= 6, "Generated padding packet is too short");
                byte[] packet = new byte[packetSize];
                Arrays.fill(packet, (byte) 0xa7);
                packet[0] = 0;
                packet[1] = 0;
                packet[2] = 1;
                packet[3] = (byte) 0xbe;
                packet[4] = (byte) ((packetSize - 6) >>> 8);
                packet[5] = (byte) (packetSize - 6);
                output.write(packet);
                remaining -= packetSize;
            }
            gap(output, 64);
        }
    }

    private static JSONObject scan(File source) throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(source, "r")) {
            Field descriptor = FileDescriptor.class.getDeclaredField("fd");
            descriptor.setAccessible(true);
            file.seek(17);
            JSONObject report =
                    new JSONObject(PhotoRecEngine.scanFd(descriptor.getInt(file.getFD())));
            require(report.optBoolean("ok"), report.optString("error"));
            require(
                    file.getFD().valid() && file.getFilePointer() == 17,
                    "MPEG scan changed caller descriptor position/ownership");
            return report;
        }
    }

    private static List<Integer> mpeg(JSONObject report) throws Exception {
        PhotoRecReport decoded = new PhotoRecReport(report);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < report.getInt("candidateCount"); i++)
            if (decoded.row(i).getString("name").equals("mpg")) rows.add(i);
        return rows;
    }

    private static JSONObject row(JSONObject report, int ordinal) throws Exception {
        return new PhotoRecReport(report).row(ordinal);
    }

    private static void validate(File source, File snapshots, JSONObject report) throws Exception {
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        source, snapshots, "generated", (file, stop) -> report.toString())) {
            require(
                    backend.open("all", () -> false).size() == report.getInt("candidateCount"),
                    "Typed model dropped an original candidate");
        }
        require(snapshots.list().length == 0, "Validated input snapshot leaked");
    }

    private static void rejected(File source, File snapshots, JSONObject report) throws Exception {
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        source, snapshots, "forged", (file, stop) -> report.toString())) {
            try {
                backend.open("all", () -> false);
                throw new AssertionError("Invalid optional MPEG callback scope accepted");
            } catch (IOException expected) {
                /* No forged scope may authorize a stored range. */
            }
        }
        require(snapshots.list().length == 0, "Rejected input snapshot leaked");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file.toPath())) {
            byte[] bytes = new byte[65536];
            int count;
            while ((count = input.read(bytes)) >= 0) digest.update(bytes, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void main(String[] arguments) throws Exception {
        File directory = Path.of(arguments[0]).resolve("mpeg-contracts").toFile();
        require(directory.mkdirs(), "Cannot create MPEG fixture directory");
        File source = new File(directory, "generated.mpg"),
                snapshots = new File(directory, "inputs");
        int count = 4608;
        String expectedSha = sectors(source, count, true, -1);
        require(
                expectedSha.equals(
                        "a78ec64f310dbedba7ef3c37c0e20b213559a1a55fd562095833c2f7a40b82ba"),
                "Generated MPEG bytes differ from the independent Python packet/hash reference");
        JSONObject large = scan(source);
        List<Integer> largeRows = mpeg(large);
        require(largeRows.size() == count, "Raw MPEG pack occurrences were pruned or invented");
        int ownerIndex = largeRows.get(0);
        JSONObject owner = row(large, ownerIndex);
        require(
                owner.getLong("physicalOffset") == PREFIX
                        && owner.getLong("reportedSizeBytes") == 9 * MIB,
                "Generated >8 MiB MPEG did not obtain its exact upstream boundary");
        require(
                large.getInt("dataCallbacksSuppressedCount") == count - 1,
                "Continuation optional-work count differs");
        for (int index : largeRows.subList(1, largeRows.size())) {
            JSONObject candidate = row(large, index);
            require(
                    candidate.getBoolean("headerAccepted")
                            && candidate.isNull("reportedSizeBytes")
                            && !candidate.getBoolean("dataCheckExecuted")
                            && candidate.isNull("dataCheckStatus")
                            && candidate.getLong("dataCheckSourceBytesProcessed") == 0
                            && candidate.getString("dataCheckMode").equals(SUPPRESSED)
                            && candidate.getInt("dataCheckSuppressedByCandidate") == ownerIndex,
                    "Continuation lost its original header or invented an independent extent");
        }
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        source, snapshots, "large.mpg", (file, stop) -> large.toString())) {
            backend.open("bounded", () -> false);
            require(
                    backend.provenance().getInt("dataCallbacksSuppressedCount") == count - 1
                            && backend.provenance()
                                    .getString("mpegContinuationPolicy")
                                    .equals(PhotoRecReport.MPEG_POLICY)
                            && backend.statusSummary()
                                    .contains("independent lengths remain unknown"),
                    "Optional MPEG work or unknown independent extents disappeared from context");
            PreparedExport exported =
                    PreparedExport.range(
                            new File(directory, "exports"),
                            "stream.mpg",
                            backend.byteSource(),
                            new FileRange(source.length(), PREFIX, 9 * MIB),
                            () -> false);
            require(
                    exported.file.length() == 9 * MIB && sha256(exported.file).equals(expectedSha),
                    "Actual >8 MiB exported MPEG bytes differ from generated packet oracle");
            exported.discard();
        }
        // A nonzero-SCR first pack without an owner still runs and establishes its own stream.
        sectors(source, 8, false, -1);
        JSONObject suffix = scan(source);
        require(
                row(suffix, mpeg(suffix).get(0)).getLong("reportedSizeBytes") == 8 * 2048,
                "Standalone nonzero-SCR suffix was suppressed without an owner");
        validate(source, snapshots, suffix);
        // SCR=0 deliberately starts an independent stream even inside a bounded stream.
        sectors(source, 8, true, 3);
        JSONObject overlapping = scan(source);
        List<Integer> overlappingRows = mpeg(overlapping);
        JSONObject restart = row(overlapping, overlappingRows.get(3));
        require(
                restart.getBoolean("dataCheckExecuted")
                        && restart.getLong("reportedSizeBytes") == 5 * 2048
                        && restart.isNull("dataCheckSuppressedByCandidate")
                        && row(overlapping, overlappingRows.get(0)).getLong("reportedSizeBytes")
                                == 8 * 2048,
                "SCR=0 was suppressed or the outer upstream extent was manually split");
        require(
                overlapping.getInt("dataCallbacksSuppressedCount") == 6,
                "Restart work count differs");
        validate(source, snapshots, overlapping);
        // Invalid bytes stop one owner; a later nonzero-SCR stream must remain independently
        // checked.
        try (OutputStream output = Files.newOutputStream(source.toPath())) {
            gap(output, PREFIX);
            for (int i = 0; i < 4; i++) output.write(sector(i == 0));
            gap(output, 64);
            for (int i = 0; i < 4; i++) output.write(sector(false));
            gap(output, 64);
        }
        JSONObject separated = scan(source);
        List<Integer> separatedRows = mpeg(separated);
        JSONObject later = row(separated, separatedRows.get(4));
        require(
                later.getLong("physicalOffset") == PREFIX + 4 * 2048 + 64
                        && later.getLong("reportedSizeBytes") == 4 * 2048
                        && later.isNull("dataCheckSuppressedByCandidate"),
                "Owner escaped its native endpoint");
        validate(source, snapshots, separated);
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        source,
                        snapshots,
                        "complete-capture",
                        (file, stop) -> separated.toString())) {
            StringWriter encodedCapture = new StringWriter();
            backend.writeText("compact-capture", encodedCapture, () -> false);
            JSONObject capture = new JSONObject(encodedCapture.toString());
            JSONObject restored = capture.getJSONObject("engineReportMetadata");
            for (String field : new String[] {"patterns", "rows", "rejectedRecoveryStates"})
                restored.put(field, capture.getJSONArray(field));
            require(
                    restored.similar(separated),
                    "Complete capture changed MPEG owner ordinals, omitted rows or raw metadata");
        }
        final int suppressedIndex = separatedRows.get(1), first = separatedRows.get(0);
        List<Mutation> mutations =
                List.of(
                        x -> x.put("dataCallbacksSuppressedCount", 0),
                        x -> x.put("maximumCandidateDataBufferBytes", 8 * MIB),
                        x -> x.put("maximumCandidateFileReadBytes", 64 * MIB),
                        x -> x.put("mpegContinuationPolicy", "skip-all-nested-headers"),
                        x ->
                                row(x, suppressedIndex)
                                        .put("dataCheckSuppressedByCandidate", suppressedIndex),
                        x ->
                                row(x, suppressedIndex)
                                        .put("dataCheckSuppressedByCandidate", first + 0.5),
                        x -> row(x, suppressedIndex).remove("dataCheckSuppressedByCandidate"),
                        x -> {
                            row(x, suppressedIndex).remove("dataCheckSuppressedByCandidate");
                            row(x, suppressedIndex).put("dataCheckMode", "not-run");
                            x.put(
                                    "dataCallbacksSuppressedCount",
                                    x.getInt("dataCallbacksSuppressedCount") - 1);
                        },
                        x -> row(x, suppressedIndex).put("dataCheckSourceBytesProcessed", 1),
                        x -> row(x, suppressedIndex).put("dataCheckStatus", 2),
                        x -> row(x, suppressedIndex).put("reportedSizeBytes", 2048),
                        x -> row(x, suppressedIndex).put("dataCheckLimited", true),
                        x ->
                                row(x, suppressedIndex)
                                        .put("dataCheckMode", "upstream-streaming-data-callback"),
                        x -> row(x, first).put("reportedSizeBytes", JSONObject.NULL),
                        x -> {
                            row(x, first)
                                    .put("reportedSizeBytes", 2048)
                                    .put("finalCalculatedSizeBytes", "2048");
                        },
                        x ->
                                x.put("adapter", "diemobile-photorec-5")
                                        .put("maximumCandidateDataBufferBytes", 8 * MIB));
        for (Mutation mutation : mutations) {
            JSONObject corrupt = new JSONObject(separated.toString());
            mutation.apply(corrupt);
            rejected(source, snapshots, corrupt);
        }
        // A truncated packet never establishes an owner to suppress subsequent headers.
        sectors(source, 5, true, -1);
        try (RandomAccessFile truncated = new RandomAccessFile(source, "rw")) {
            truncated.setLength(PREFIX + 5 * 2048 - 17);
        }
        JSONObject truncated = scan(source);
        require(
                truncated.getInt("dataCallbacksSuppressedCount") == 0,
                "Unbounded stream acquired an owner");
        for (int index : mpeg(truncated))
            require(
                    row(truncated, index).isNull("reportedSizeBytes")
                            && row(truncated, index).getBoolean("dataCheckExecuted"),
                    "Truncated stream acquired an extent");
        validate(source, snapshots, truncated);
        // Work bounds count read blocks: an exact 64 MiB stream still needs a stop/lookahead read.
        longPackets(source, 64 * MIB - 65536);
        JSONObject belowLimit = scan(source);
        require(
                row(belowLimit, mpeg(belowLimit).get(0)).getLong("reportedSizeBytes")
                        == 64 * MIB - 65536,
                "Within-budget packet boundary was lost");
        longPackets(source, 64 * MIB);
        JSONObject limited = scan(source);
        JSONObject limitedRow = row(limited, mpeg(limited).get(0));
        require(
                limitedRow.isNull("reportedSizeBytes")
                        && limitedRow.getBoolean("dataCheckLimited")
                        && limitedRow.getLong("dataCheckSourceBytesProcessed") == 64 * MIB
                        && limitedRow
                                .getString("dataCheckLimitReason")
                                .equals("per-candidate-data-buffer-budget")
                        && limited.getInt("dataCallbacksSuppressedCount") == 0,
                "Stop/lookahead work cap was bypassed or an unresolved stream became an owner");
        validate(source, snapshots, limited);
        Files.write(source.toPath(), MetadataFixtureFactory.png());
        JSONObject legacy = scan(source);
        legacy.put("adapter", "diemobile-photorec-5")
                .put("maximumCandidateDataBufferBytes", 8 * MIB);
        legacy.remove("dataCallbacksSuppressedCount");
        legacy.remove("mpegContinuationPolicy");
        validate(source, snapshots, legacy);
        try (PhotoRecBackend cancelled =
                new PhotoRecBackend(
                        source,
                        snapshots,
                        "cancelled",
                        (file, stop) -> {
                            throw new AssertionError("Cancelled acquisition reached native work");
                        })) {
            try {
                cancelled.open("all", () -> true);
                throw new AssertionError("Cancellation ignored");
            } catch (InterruptedIOException expected) {
                /* Request-owned cancellation remains explicit. */
            }
        }
        require(snapshots.list().length == 0, "Cancelled request leaked its source");
        Files.delete(source.toPath());
        System.out.println(
                "MPEG adapter6 passed: >8 MiB exact payload export, retained pack headers,"
                        + " SCR=0/suffix/owner endpoints, truncation, 64 MiB stop-lookahead budget,"
                        + " strict typed owner refusals, adapter5 compatibility, FD position and"
                        + " cancellation.");
    }
}

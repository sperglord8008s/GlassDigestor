// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

/** Real upstream callbacks plus a separate naive magic oracle and exact exports. */
public final class PhotoRecInvestigationTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static JSONObject at(JSONObject report, String name, long start) throws Exception {
        JSONArray rows = report.getJSONArray("rows");
        PhotoRecReport decoded = new PhotoRecReport(report);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = decoded.row(i);
            if (row.getString("name").equals(name)
                    && row.optLong("candidateStartPhysicalOffset", -1) == start) return row;
        }
        throw new AssertionError("Missing " + name + " at " + start);
    }

    private static final class CountingWriter extends Writer {
        private final String token = "\"patternIndex\":";
        private int matched;
        long records, characters;

        @Override
        public void write(char[] text, int start, int length) {
            characters += length;
            for (int i = start; i < start + length; i++) {
                char c = text[i];
                if (c == token.charAt(matched)) {
                    if (++matched == token.length()) {
                        records++;
                        matched = 0;
                    }
                } else matched = c == token.charAt(0) ? 1 : 0;
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }

    public static void main(String[] args) throws Exception {
        require(PhotoRecEngine.AVAILABLE, "Real PhotoRec JNI unavailable: " + PhotoRecEngine.ERROR);
        Path directory = Path.of(args[0]);
        CarvingFixture fixture = new CarvingFixture();
        File source = directory.resolve("whole-blob.bin").toFile();
        Files.write(source.toPath(), fixture.bytes);
        JSONObject report = new JSONObject(PhotoRecEngine.scanPath(source.getAbsolutePath()));
        require(report.optBoolean("ok"), report.toString());
        require(
                report.getInt("patternCount") == 774 && report.getInt("signatureCount") == 349,
                "Actual upstream format registrations differ from pinned catalog");
        require(
                report.getInt("searchedPatternCount") == 581
                        && report.getInt("excludedPatternCount") == 193,
                "Weak signature coverage changed without updating the reviewed policy");
        require(
                !report.getBoolean("fileCheckCallbacksExecuted")
                        && !report.getBoolean("filesystemRecoveryExecuted")
                        && !report.getBoolean("auxiliarySignatureFiles")
                        && report.getBoolean("safeHeaderOnly"),
                "Adapter executed unsupported acquisition/recovery callbacks");
        JSONArray catalog = report.getJSONArray("patterns"), rows = report.getJSONArray("rows");
        Set<String> expected = new HashSet<>(), actual = new HashSet<>();
        int searched = 0;
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject pattern = catalog.getJSONObject(i);
            require(pattern.getInt("index") == i, "Original pattern identity differs");
            if (!pattern.getBoolean("searched")) {
                require(!pattern.isNull("searchExclusion"), "Excluded magic disappeared silently");
                continue;
            }
            searched++;
            byte[] magic = HexFormat.of().parseHex(pattern.getString("magicHex"));
            outer:
            for (int at = 0; at <= fixture.bytes.length - magic.length; at++) {
                for (int j = 0; j < magic.length; j++)
                    if (fixture.bytes[at + j] != magic[j]) continue outer;
                expected.add(i + ":" + at);
            }
        }
        require(
                searched == report.getInt("searchedPatternCount"),
                "Coverage count differs from actual patterns");
        PhotoRecReport decoded = new PhotoRecReport(report);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = decoded.row(i);
            String identity = row.getInt("patternIndex") + ":" + row.getLong("magicOffset");
            require(actual.add(identity), "Duplicate magic occurrence invented");
            JSONObject pattern = catalog.getJSONObject(row.getInt("patternIndex"));
            long start = row.getLong("magicOffset") - pattern.getLong("magicRelativeOffset");
            if (start < 0) {
                require(
                        row.isNull("candidateStartPhysicalOffset")
                                && row.getLong("derivedCandidateStart") == start
                                && !row.getBoolean("headerAccepted"),
                        "Negative-start magic was parsed or hidden");
            } else
                require(
                        row.getLong("physicalOffset") == start,
                        "Relative magic offset mapped incorrectly");
        }
        require(
                actual.equals(expected),
                "Whole-blob/overlap/short/relative magic scan differs from naive oracle");
        require(report.getInt("negativeStartMagicMatches") == 2, "Negative start witnesses lost");
        require(
                at(report, "jpg", fixture.jpegAt).getLong("reportedSizeBytes")
                        == fixture.jpeg.length,
                "JPEG stopped at metadata EOI or nested media");
        require(
                at(report, "png", fixture.nestedPngAt).getLong("reportedSizeBytes")
                        == fixture.png.length,
                "Nested PNG was pruned");
        require(
                at(report, "png", fixture.pngAt).getLong("reportedSizeBytes") == fixture.png.length,
                "PNG endian/chunk extent differs");
        require(
                at(report, "riff", fixture.wavAt).getLong("reportedSizeBytes")
                        == fixture.wav.length,
                "Little-endian RIFF declared length differs");
        require(
                at(report, "gif", fixture.gifAt).getLong("reportedSizeBytes") == fixture.gif.length,
                "GIF trailer/subblock length differs");
        require(
                at(report, "dcm", fixture.dcmAt).isNull("reportedSizeBytes"),
                "DICOM extent invented from EOF");
        require(
                at(report, "png", fixture.brokenPngAt).isNull("reportedSizeBytes"),
                "Out-of-source PNG length is extractable");
        require(
                at(report, "jpg", fixture.truncatedJpegAt).isNull("reportedSizeBytes"),
                "Missing JPEG EOI got a fabricated extent");
        // A data-stop is explicitly weaker than full format integrity: CRC is not checked here.
        byte[] wrongCrc = fixture.png.clone();
        wrongCrc[29] ^= 1;
        File crc = directory.resolve("wrong-crc.png").toFile();
        Files.write(crc.toPath(), wrongCrc);
        JSONObject crcRow =
                at(new JSONObject(PhotoRecEngine.scanPath(crc.getAbsolutePath())), "png", 0);
        require(
                crcRow.getLong("reportedSizeBytes") == wrongCrc.length
                        && !crcRow.getBoolean("fileCheckExecuted"),
                "Data-stop was substituted for a full integrity check");
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            Field field = FileDescriptor.class.getDeclaredField("fd");
            field.setAccessible(true);
            input.seek(23);
            JSONObject fd = new JSONObject(PhotoRecEngine.scanFd(field.getInt(input.getFD())));
            require(
                    fd.similar(report) && input.getFilePointer() == 23 && input.getFD().valid(),
                    "Descriptor API altered caller ownership/position or results");
        }
        require(
                !new JSONObject(PhotoRecEngine.scanFd(-1)).getBoolean("ok"),
                "Invalid descriptor returned empty success");
        File empty = directory.resolve("empty.bin").toFile();
        Files.write(empty.toPath(), new byte[0]);
        require(
                new JSONObject(PhotoRecEngine.scanPath(empty.getAbsolutePath()))
                                .getJSONArray("rows")
                                .length()
                        == 0,
                "Empty input invented signatures");
        File large = directory.resolve("large.bin").toFile();
        try (RandomAccessFile output = new RandomAccessFile(large, "rw")) {
            output.setLength(PhotoRecBackend.MAX_BYTES + 1);
        }
        require(
                !new JSONObject(PhotoRecEngine.scanPath(large.getAbsolutePath())).getBoolean("ok"),
                "Input cap ignored");
        Files.delete(large.toPath());
        File bomb = directory.resolve("magic-bomb.bin").toFile();
        byte[] repeated = new byte[250001 * 16];
        for (int i = 0; i < repeated.length; i += 16) {
            repeated[i] = 'B';
            repeated[i + 1] = 'M';
        }
        Files.write(bomb.toPath(), repeated);
        JSONObject refused = new JSONObject(PhotoRecEngine.scanPath(bomb.getAbsolutePath()));
        require(
                !refused.getBoolean("ok") && !refused.has("rows"),
                "Budget failure returned a successful prefix");
        // Real upstream 7z header accepts two individually bounded uint64 words
        // whose sum is beyond signed int64. Preserve it exactly and do not call
        // data callbacks outside PhotoRec's documented calculated-size contract.
        byte[] hugeHeader = new byte[32];
        byte[] sevenZip = HexFormat.of().parseHex("377abcaf271c");
        System.arraycopy(sevenZip, 0, hugeHeader, 0, sevenZip.length);
        java.nio.ByteBuffer.wrap(hugeHeader)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putLong(12, 0x7000000000000000L)
                .putLong(20, 0x7000000000000000L);
        File huge = directory.resolve("huge-calculated.7z").toFile();
        Files.write(huge.toPath(), hugeHeader);
        JSONObject hugeRow =
                at(new JSONObject(PhotoRecEngine.scanPath(huge.getAbsolutePath())), "7z", 0);
        require(
                hugeRow.get("headerCalculatedSizeBytes") instanceof String
                        && hugeRow.getString("headerCalculatedSizeBytes")
                                .equals("16140901064495857696")
                        && hugeRow.isNull("reportedSizeBytes")
                        && !hugeRow.getBoolean("dataCheckExecuted"),
                "Unsigned raw size rounded or invalid data callback contract invoked");
        // Many plausible nested chunk headers must not consume discovery or
        // prevent a later declared-size WAV from obtaining its actual range.
        byte[] budgetBlob = new byte[9 * 1024 * 1024];
        for (int i = 0; i < 34; i++) {
            int offset = i * 512;
            System.arraycopy(fixture.png, 0, budgetBlob, offset, 33);
            java.nio.ByteBuffer out =
                    java.nio.ByteBuffer.wrap(budgetBlob).order(java.nio.ByteOrder.BIG_ENDIAN);
            out.putInt(offset + 33, budgetBlob.length - 64);
            byte[] idat = {'I', 'D', 'A', 'T'};
            System.arraycopy(idat, 0, budgetBlob, offset + 37, 4);
        }
        int lateWav = budgetBlob.length - fixture.wav.length;
        System.arraycopy(fixture.wav, 0, budgetBlob, lateWav, fixture.wav.length);
        File budgetFile = directory.resolve("partial-data.bin").toFile();
        Files.write(budgetFile.toPath(), budgetBlob);
        JSONObject partial = new JSONObject(PhotoRecEngine.scanPath(budgetFile.getAbsolutePath()));
        require(
                partial.getBoolean("ok")
                        && partial.getBoolean("fullBlobSearch")
                        && partial.getInt("dataCallbacksLimitedCount") > 0
                        && partial.getLong("callbackBufferWorkBytes") <= 256L * 1024 * 1024,
                "Data work exhaustion stopped discovery or exceeded its explicit budget");
        int observedLimited = 0;
        for (int i = 0; i < 34; i++) {
            JSONObject limited = at(partial, "png", i * 512);
            if (limited.getBoolean("dataCheckLimited")) observedLimited++;
            require(
                    limited.isNull("reportedSizeBytes"),
                    "Budget-limited or incomplete data callback manufactured a stored extent");
        }
        require(
                observedLimited == partial.getInt("dataCallbacksLimitedCount"),
                "Aggregate-limited candidate count differs from the original rows");
        require(
                at(partial, "riff", lateWav).getLong("reportedSizeBytes") == fixture.wav.length,
                "Earlier speculative headers starved the later declared-size callback");
        File partialSnapshots = directory.resolve("partial-snapshots").toFile();
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        budgetFile,
                        partialSnapshots,
                        "partial-data.bin",
                        (input, stop) -> partial.toString())) {
            require(
                    backend.open("bounded", () -> false).size() > 0
                            && backend.statusSummary().contains("PARTIAL DATA CHECKS"),
                    "Provider hid explicit partial data coverage or valid later ranges");
            require(
                    backend.open("unresolved", () -> false).size() >= 34,
                    "Provider dropped budget-limited header evidence");
        }
        require(
                Objects.requireNonNull(partialSnapshots.list()).length == 0,
                "Partial coverage snapshot leaked");
        // More rejected occurrences than the old hard cap must remain complete,
        // with shared raw state represented once and rows expanded lazily.
        int repeatedHeaders = 75000;
        byte[] denseBytes = new byte[repeatedHeaders * 16 + fixture.wav.length];
        for (int i = 0; i < repeatedHeaders * 16; i += 16) {
            denseBytes[i] = 'B';
            denseBytes[i + 1] = 'M';
        }
        System.arraycopy(fixture.wav, 0, denseBytes, repeatedHeaders * 16, fixture.wav.length);
        File denseFile = directory.resolve("dense-magic.bin").toFile();
        Files.write(denseFile.toPath(), denseBytes);
        String denseEncoded = PhotoRecEngine.scanPath(denseFile.getAbsolutePath());
        JSONObject dense = new JSONObject(denseEncoded);
        require(
                dense.getBoolean("ok")
                        && dense.getInt("compactRejectedCandidateCount") >= repeatedHeaders
                        && denseEncoded.length() < 8 * 1024 * 1024
                        && dense.getJSONArray("rejectedRecoveryStates").length() < 50,
                "Dense discovery/state failure: "
                        + dense.optString("error")
                        + "; compact="
                        + dense.optInt("compactRejectedCandidateCount")
                        + "; bytes="
                        + denseEncoded.length());
        PhotoRecReport denseDecoded = new PhotoRecReport(dense);
        int exactBmpHeaders = 0;
        for (int i = 0; i < dense.getInt("candidateCount"); i++) {
            JSONObject row = denseDecoded.row(i);
            long magic = row.getLong("magicOffset");
            if (row.getString("name").equals("bmp") && magic < repeatedHeaders * 16) {
                require(
                        magic % 16 == 0
                                && !row.getBoolean("headerAccepted")
                                && row.isNull("reportedSizeBytes"),
                        "Compact rejected coordinate or callback outcome changed");
                exactBmpHeaders++;
            }
        }
        require(exactBmpHeaders == repeatedHeaders, "Dense occurrence inventory is incomplete");
        File denseSnapshots = directory.resolve("dense-snapshots").toFile();
        String denseName =
                "dense-" + (char) 0xD800 + "-" + (char) 0xDFFF + "-" + (char) 0 + "-研究.bin";
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        denseFile, denseSnapshots, denseName, (input, stop) -> denseEncoded)) {
            EvidenceRows all = backend.open("all", () -> false);
            require(
                    all.size() == dense.getInt("candidateCount"),
                    "Lazy model changed dense row count");
            EvidenceRows.Page middle = all.select("", () -> false).page(1000, () -> false);
            require(
                    middle.rows.get(0).index == 50000
                            && middle.rows.get(0).sizeBytes == null
                            && middle.rows.get(0).json().has("headerCalculatedSizeBytes"),
                    "Dense lazy paging lost original ordinals or raw expanded state");
            CountingWriter counter = new CountingWriter();
            all.writeReport(counter, backend.provenance(), "no matches", 1000, 1, () -> false);
            require(
                    counter.records == all.size() && counter.characters > denseEncoded.length(),
                    "Complete dense export was restricted by filter/page or omitted expanded"
                            + " records");
            StringWriter compact = new StringWriter();
            backend.writeText("compact-capture", compact, () -> false);
            JSONObject capture = new JSONObject(compact.toString());
            require(
                    capture.getString("schema").equals("org.diemobile.photorec-capture")
                            && capture.getJSONObject("provenance")
                                            .getJSONObject("input")
                                            .getString("sha256")
                                            .length()
                                    == 64,
                    "Compact capture lost its source identity or explicit schema");
            JSONObject reconstructed =
                    capture.getJSONObject("engineReportMetadata")
                            .put("rows", capture.getJSONArray("rows"))
                            .put("patterns", capture.getJSONArray("patterns"))
                            .put(
                                    "rejectedRecoveryStates",
                                    capture.getJSONArray("rejectedRecoveryStates"));
            require(
                    reconstructed.similar(dense),
                    "Compact capture changed original engine evidence or row order");
            require(
                    capture.getJSONObject("provenance")
                            .getJSONObject("input")
                            .getString("name")
                            .equals(denseName),
                    "Compact capture changed source-name UTF-16 units");
            java.util.concurrent.atomic.AtomicInteger cancellationCalls =
                    new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.BooleanSupplier cancel =
                    () -> cancellationCalls.incrementAndGet() > 100;
            File cancelledExports = directory.resolve("cancelled-capture").toFile();
            try {
                PreparedExport.stream(
                        cancelledExports,
                        "capture.json",
                        "application/json",
                        output -> backend.writeText("compact-capture", output, cancel),
                        cancel);
                throw new AssertionError("Cancelled compact capture returned a successful stage");
            } catch (InterruptedIOException expectedCancellation) {
                require(
                        Objects.requireNonNull(cancelledExports.list()).length == 0,
                        "Cancelled compact capture left staged bytes");
            }

            require(
                    compact.getBuffer().length() < 8 * 1024 * 1024,
                    "Complete compact capture unexpectedly expanded rejected rows");
        }
        require(Objects.requireNonNull(denseSnapshots.list()).length == 0, "Dense snapshot leaked");
        JSONObject malformed = new JSONObject(denseEncoded);
        malformed.getJSONArray("rows").getJSONArray(0).put(2, Integer.MAX_VALUE);
        try (PhotoRecBackend backend =
                new PhotoRecBackend(
                        denseFile,
                        denseSnapshots,
                        "bad-state-index.bin",
                        (input, stop) -> malformed.toString())) {
            try {
                backend.open("all", () -> false);
                throw new AssertionError("Out-of-dictionary compact reference accepted");
            } catch (IOException expectedFailure) {
                require(
                        expectedFailure.getMessage().contains("dictionary"),
                        expectedFailure.toString());
            }
        }
        JSONObject fractional = new JSONObject(denseEncoded);
        fractional.getJSONArray("rows").getJSONArray(0).put(1, 0.5);
        try {
            new PhotoRecReport(fractional).row(0);
            throw new AssertionError("Fractional magic offset rounded");
        } catch (IOException expectedFailure) {
            require(
                    expectedFailure.getMessage().contains("exact integer"),
                    expectedFailure.toString());
        }
        JSONObject overflowing = new JSONObject(denseEncoded);
        overflowing
                .getJSONArray("rejectedRecoveryStates")
                .getJSONArray(0)
                .put(0, "18446744073709551616");
        try {
            new PhotoRecReport(overflowing);
            throw new AssertionError("Raw unsigned state overflow accepted");
        } catch (IOException expectedFailure) {
            require(expectedFailure.getMessage().contains("uint64"), expectedFailure.toString());
        }
        File snapshots = directory.resolve("snapshots").toFile();
        BinaryRunner runner =
                (input, stop) -> {
                    EvidenceRows.check(stop);
                    return PhotoRecEngine.scanPath(input.getAbsolutePath());
                };
        try (PhotoRecBackend backend =
                new PhotoRecBackend(source, snapshots, source.getName(), runner)) {
            EvidenceRows bounded = backend.open(backend.initialRequest(() -> false), () -> false);
            require(bounded.size() >= 5, "Bounded view lost media ranges");
            EvidenceRows.Row jpeg = null;
            for (int i = 0; i < bounded.size(); i++) {
                EvidenceRows.Row row = bounded.row(i, () -> false);
                require(row.sizeBytes != null, "Unknown extent in bounded view");
                if (row.physicalOffset == fixture.jpegAt
                        && row.json().getString("name").equals("jpg")) jpeg = row;
            }
            require(jpeg != null, "JPEG source identity lost");
            EvidenceRows unknown = backend.open("unresolved", () -> false);
            require(unknown.size() > 0, "Header-only/data errors hidden");
            for (int i = 0; i < unknown.size(); i++)
                require(unknown.row(i, () -> false).sizeBytes == null, "Unknown scope got extent");
            EvidenceRows all = backend.open("all", () -> false);
            StringWriter out = new StringWriter();
            all.writeReport(out, backend.provenance(), "no matches", 0, 1, () -> false);
            require(
                    new JSONObject(out.toString()).getJSONArray("rows").length() == rows.length(),
                    "Filter/page truncated full export");
            PreparedExport export =
                    PreparedExport.range(
                            directory.resolve("exports").toFile(),
                            "carved.jpg",
                            backend.byteSource(),
                            new FileRange(
                                    fixture.bytes.length, jpeg.physicalOffset, jpeg.sizeBytes),
                            () -> false);
            Files.delete(source.toPath());
            ByteArrayOutputStream recovered = new ByteArrayOutputStream();
            export.copyTo(recovered, () -> false);
            export.discard();
            require(
                    Arrays.equals(fixture.jpeg, recovered.toByteArray()),
                    "Immutable stored-byte carving changed bytes");
            require(
                    backend.statusSummary().contains("581 / 774"),
                    "Partial catalog coverage absent from visible status");
        }
        require(Objects.requireNonNull(snapshots.list()).length == 0, "Owned snapshot leaked");
        Files.write(source.toPath(), fixture.bytes);
        BinaryRunner forged =
                (input, stop) -> {
                    JSONObject value = new JSONObject(report.toString());
                    at(value, "png", fixture.pngAt).put("dataCheckStatus", 1);
                    return value.toString();
                };
        try (PhotoRecBackend backend =
                new PhotoRecBackend(source, snapshots, "bad-witness.bin", forged)) {
            try {
                backend.open("bounded", () -> false);
                throw new AssertionError("Extent without data-stop accepted");
            } catch (IOException expectedFailure) {
                require(
                        expectedFailure.getMessage().contains("data-stop"),
                        expectedFailure.toString());
            }
        }
        require(
                Objects.requireNonNull(snapshots.list()).length == 0,
                "Failed report snapshot leaked");
        System.out.println(
                "PhotoRec JNI passed: 349 registered format groups / 774 catalog patterns / 581"
                        + " searched; "
                        + rows.length()
                        + " exact naive-oracle occurrences, nested JPEG/PNG/GIF/WAV extents,"
                        + " negative/relative starts, unknown/truncated/CRC semantics, FD"
                        + " ownership, budgets, complete exports and exact immutable carving");
    }
}

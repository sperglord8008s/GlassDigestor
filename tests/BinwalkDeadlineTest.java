// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real configurable JNI propagation plus strict captured-report compatibility/refusal. */
public final class BinwalkDeadlineTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void rejected(File input, File snapshots, JSONObject report) throws Exception {
        try (BinwalkBackend backend =
                new BinwalkBackend(
                        input, snapshots, "limit-fixture", (source, stop) -> report.toString())) {
            try {
                backend.open("accepted", () -> false);
                throw new AssertionError("Invalid deadline capture accepted: " + report);
            } catch (IOException expected) {
                require(expected.getMessage().contains("accounting differs"), expected.toString());
            }
        }
        require(snapshots.list().length == 0, "Rejected deadline report leaked private input");
    }

    public static void main(String[] arguments) throws Exception {
        Path directory = Path.of(arguments[0]);
        File input = directory.resolve("binwalk-deadline.png").toFile();
        File snapshots = directory.resolve("binwalk-deadline-snapshots").toFile();
        Files.write(input.toPath(), MetadataFixtureFactory.png());
        JSONObject report;
        try (RandomAccessFile source = new RandomAccessFile(input, "r")) {
            Field descriptor = FileDescriptor.class.getDeclaredField("fd");
            descriptor.setAccessible(true);
            int fd = descriptor.getInt(source.getFD());
            source.seek(17);
            report = new JSONObject(BinwalkEngine.scanFdWithDeadline(fd, 240000));
            require(report.getBoolean("ok"), report.toString());
            require(
                    report.getString("adapter").equals("diemobile-binwalk-3"),
                    "Wrong configurable adapter");
            require(
                    report.getLong("requestedDeadlineMs") == 240000
                            && report.getLong("cooperativeDeadlineMs") == 240000,
                    "Native report lost the selected deadline");
            for (long invalid : new long[] {-1, 0, 999, 1001, 1800001, Long.MAX_VALUE}) {
                JSONObject failure = new JSONObject(BinwalkEngine.scanFdWithDeadline(fd, invalid));
                require(
                        !failure.getBoolean("ok")
                                && failure.getString("error").contains("whole seconds"),
                        "Unbounded/fractional native request accepted");
            }
            for (long boundary : new long[] {1000, 1800000}) {
                JSONObject captured =
                        new JSONObject(BinwalkEngine.scanFdWithDeadline(fd, boundary));
                require(
                        captured.getBoolean("ok")
                                && captured.getLong("requestedDeadlineMs") == boundary,
                        "Supported native boundary was not propagated");
            }
            require(
                    source.getFD().valid() && source.getFilePointer() == 17,
                    "Configurable request/refusal changed caller descriptor ownership or position");
        }
        report.put(
                "worker",
                new JSONObject()
                        .put("engine", "binwalk")
                        .put("analysisDeadlineMs", 240000)
                        .put("requestDeadlineMs", 270000));
        try (BinwalkBackend backend =
                new BinwalkBackend(
                        input, snapshots, "configured", (source, stop) -> report.toString())) {
            require(backend.open("accepted", () -> false).size() > 0, "Configured report rejected");
            require(
                    backend.provenance().getLong("requestedDeadlineMs") == 240000,
                    "Captured provenance lost its requested limit");
        }
        for (String field :
                new String[] {
                    "requestedDeadlineMs",
                    "cooperativeDeadlineMs",
                    "minimumCooperativeDeadlineMs",
                    "maximumCooperativeDeadlineMs"
                }) {
            JSONObject bad = new JSONObject(report.toString());
            bad.put(field, bad.getLong(field) + 1);
            rejected(input, snapshots, bad);
            bad = new JSONObject(report.toString());
            bad.put(field, bad.getLong(field) + 0.5);
            rejected(input, snapshots, bad);
        }
        for (long invalid : new long[] {0, 999, 1001, 1800001, Long.MAX_VALUE}) {
            JSONObject bad = new JSONObject(report.toString());
            bad.put("requestedDeadlineMs", invalid).put("cooperativeDeadlineMs", invalid);
            rejected(input, snapshots, bad);
        }
        JSONObject badWorker = new JSONObject(report.toString());
        badWorker.getJSONObject("worker").put("requestDeadlineMs", 45000);
        rejected(input, snapshots, badWorker);
        JSONObject legacy = new JSONObject(BinwalkEngine.scanPath(input.toString()));
        require(
                legacy.getString("adapter").equals("diemobile-binwalk-2")
                        && legacy.getLong("cooperativeDeadlineMs") == 30000,
                "Legacy API contract changed");
        try (BinwalkBackend backend =
                new BinwalkBackend(
                        input, snapshots, "legacy", (source, stop) -> legacy.toString())) {
            require(backend.open("accepted", () -> false).size() > 0, "Legacy capture rejected");
        }
        Files.delete(input.toPath());
        System.out.println(
                "Binwalk deadlines passed: exact configured JNI/report propagation, bound/type"
                    + " refusal, descriptor ownership, captured worker consistency, legacy reading"
                    + " and cleanup.");
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Real owned files and independent JSON reconstruction, without an Android runtime. */
public final class ContentOriginTest {
    interface Task {
        void run() throws Exception;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void refuse(Task action, String name) throws Exception {
        try {
            action.run();
            throw new AssertionError("Accepted " + name);
        } catch (IOException expected) {
            require(expected.getMessage() != null, name);
        }
    }

    private static long count(File directory) throws IOException {
        try (var files = Files.list(directory.toPath())) {
            return files.count();
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Files.createDirectories(root);
        File source = root.resolve("original.bin").toFile();
        byte[] bytes =
                "prefix\u0000 exact selected 世界 😀 bytes and suffix"
                        .getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), bytes);
        Path physicalCache = Files.createDirectories(root.resolve("physical-cache"));
        Path contextCache = root.resolve("context-cache-alias");
        Files.createSymbolicLink(contextCache, physicalCache);
        File origins = contextCache.resolve("origins").toFile(), exports = root.resolve("exports").toFile();
        StringBuilder units = new StringBuilder();
        for (int i = 0; i < 65536; i++) units.append((char) i);
        String text = "Unclipped original witness 世界\n".repeat(10000) + units;
        try (DataOutputStream expected =
                new DataOutputStream(Files.newOutputStream(root.resolve("expected-utf16be")))) {
            for (int i = 0; i < text.length(); i++) expected.writeChar(text.charAt(i));
        }
        try (InputSnapshot parent =
                        InputSnapshot.acquire(
                                source,
                                root.resolve("inputs").toFile(),
                                FileRange.MAX_OPERATION_BYTES,
                                () -> false);
                InputSnapshot subject =
                        OwnedContent.stageRange(
                                parent,
                                new FileRange(parent.size, 7, parent.size - 14),
                                root.resolve("previews").toFile(),
                                () -> false)) {
            ContentOrigin origin =
                    ContentOrigin.create(
                            origins,
                            subject,
                            ContentOrigin.stored(
                                    parent, new FileRange(parent.size, 7, subject.size)),
                            records -> {
                                records.value(
                                        "originalFinding",
                                        new JSONObject()
                                                .put("unknownField", text)
                                                .put("unknownNull", JSONObject.NULL)
                                                .put(
                                                        "exactInteger",
                                                        (EvidenceJson.RawNumber)
                                                                () -> "9007199254740993")
                                                .put(
                                                        "originalRawJson",
                                                        "{\"unknown\":1.2300e+009,\"s\":\"\\ud800\"}"));
                                records.value(
                                        "coverage",
                                        new JSONObject().put("completeDecodingVerified", false));
                            },
                            () -> false);
            require(origin.size > 65536, "Witness was clipped");
            String saved = origin.descriptor();
            require(
                    ContentOrigin.restore(origins, saved, subject.sha256, subject.size, () -> false)
                            .sha256
                            .equals(origin.sha256),
                    "Restore identity differs");
            require(source.delete(), "Original input deletion failed");
            parent.close();
            PreparedExport staged = origin.export(exports, () -> false);
            require(staged.mime.equals("application/x-ndjson"), "NDJSON MIME missing");
            byte[] original = Files.readAllBytes(origin.file.toPath());
            origin.close();
            ByteArrayOutputStream copy = new ByteArrayOutputStream();
            staged.copyTo(copy, () -> false);
            require(
                    Arrays.equals(original, copy.toByteArray()),
                    "Export changed after origin expiry");
            Files.write(root.resolve("capture.ndjson"), copy.toByteArray());
            staged.discard();
            require(!staged.file.exists(), "Export stage leaked");

            ContentOrigin logical =
                    ContentOrigin.create(
                            origins,
                            subject,
                            ContentOrigin.member(subject, 99),
                            records -> records.value("entry", "unchanged"),
                            () -> false);
            String logicalState = logical.descriptor();
            JSONObject header = new JSONObject(Files.readAllLines(logical.file.toPath()).get(0));
            require(
                    header.getJSONObject("relationship").isNull("physicalOffset"),
                    "Logical member gained physical offset");
            refuse(
                    () ->
                            ContentOrigin.restore(
                                    origins,
                                    logicalState,
                                    "0".repeat(64),
                                    subject.size,
                                    () -> false),
                    "wrong subject SHA");
            refuse(
                    () ->
                            ContentOrigin.restore(
                                    origins,
                                    logicalState,
                                    subject.sha256,
                                    subject.size + 1,
                                    () -> false),
                    "wrong subject size");
            JSONObject coercion = new JSONObject(logicalState).put("sizeBytes", "123");
            refuse(
                    () ->
                            ContentOrigin.restore(
                                    origins,
                                    coercion.toString(),
                                    subject.sha256,
                                    subject.size,
                                    () -> false),
                    "coerced size");
            JSONObject huge =
                    new JSONObject(logicalState).put("sizeBytes", ContentOrigin.MAX_BYTES + 1);
            refuse(
                    () ->
                            ContentOrigin.restore(
                                    origins,
                                    huge.toString(),
                                    subject.sha256,
                                    subject.size,
                                    () -> false),
                    "oversize capture");
            byte[] changed = Files.readAllBytes(logical.file.toPath());
            changed[changed.length - 2] ^= 1;
            Files.write(logical.file.toPath(), changed);
            refuse(
                    () -> logical.export(exports, () -> false),
                    "same-length changed witness export");
            refuse(
                    () ->
                            ContentOrigin.restore(
                                    origins,
                                    logicalState,
                                    subject.sha256,
                                    subject.size,
                                    () -> false),
                    "same-length changed witness restore");
            logical.close();

            File outside = root.resolve("content-origin-outside.ndjson").toFile();
            Files.writeString(outside.toPath(), "outside private ownership");
            for (String filename :
                    List.of(
                            "../content-origin-outside.ndjson",
                            outside.getAbsolutePath(),
                            "other-file")) {
                String descriptor = new JSONObject(logicalState).put("file", filename).toString();
                refuse(
                        () ->
                                ContentOrigin.restore(
                                        origins,
                                        descriptor,
                                        subject.sha256,
                                        subject.size,
                                        () -> false),
                        "foreign origin path");
                ContentOrigin.discard(origins, descriptor);
                require(outside.isFile(), "Foreign file deleted");
            }
            Path link = origins.toPath().resolve("content-origin-link.ndjson");
            Files.createSymbolicLink(link, outside.toPath());
            String linked =
                    new JSONObject(logicalState)
                            .put("file", link.getFileName().toString())
                            .toString();
            refuse(
                    () ->
                            ContentOrigin.restore(
                                    origins, linked, subject.sha256, subject.size, () -> false),
                    "symlinked origin");
            ContentOrigin.discard(origins, linked);
            require(
                    outside.isFile() && Files.isSymbolicLink(link),
                    "Symlink cleanup escaped ownership");
            Files.delete(link);
            long before = count(origins);
            refuse(
                    () ->
                            ContentOrigin.create(
                                    origins,
                                    subject,
                                    ContentOrigin.member(subject, 0),
                                    records -> {
                                        records.value("partial", text);
                                        throw new IOException("fixture producer failed");
                                    },
                                    () -> false),
                    "failed producer");
            require(count(origins) == before, "Failed capture left a partial stage");
            refuse(
                    () ->
                            ContentOrigin.create(
                                    origins,
                                    subject,
                                    ContentOrigin.member(subject, 0),
                                    records -> {
                                        for (int i = 0; i < 129; i++) records.value("witness", i);
                                    },
                                    () -> false),
                    "witness budget");
            require(count(origins) == before, "Over-budget capture leaked");
            refuse(
                    () ->
                            ContentOrigin.create(
                                    origins,
                                    subject,
                                    ContentOrigin.member(subject, 0),
                                    records -> {},
                                    () -> true),
                    "cancellation");
            require(count(origins) == before, "Cancelled capture leaked");
            require(count(exports) == 0, "Failed export leaked");
        }
        System.out.println(
                "Origin ownership, complete Unicode/unknown-field witness, source expiry,"
                    + " byte-identical export, identity/type/path/symlink/budget/cancel refusal"
                    + " checks passed.");
    }
}

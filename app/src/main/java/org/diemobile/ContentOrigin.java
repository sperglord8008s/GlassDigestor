// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;

/** Independently owned JSON Lines evidence for an exact preview, never a claim about behavior. */
final class ContentOrigin implements AutoCloseable {
    static final String SCHEMA = "org.diemobile.content-origin";
    static final long MAX_BYTES = FileRange.MAX_OPERATION_BYTES;
    private static final int HEADER_LIMIT = 8192;
    final File file;
    final long size, subjectSize;
    final String sha256, subjectSha256, relationship;

    interface Producer {
        void write(Records records) throws Exception;
    }

    /** Each witness is one JSON value, with no display clipping or normalization of raw strings. */
    static final class Records {
        private final Writer output;
        private final BooleanSupplier stop;
        private int count;

        private Records(Writer output, BooleanSupplier stop) {
            this.output = output;
            this.stop = stop;
        }

        void value(String kind, Object value) throws Exception {
            json(kind, out -> EvidenceJson.write(out, value, stop));
        }

        /** Producer must write one compact JSON value, as the existing report writers do. */
        void json(String kind, PreparedExport.TextProducer producer) throws Exception {
            EvidenceRows.check(stop);
            if (kind == null || !kind.matches("[A-Za-z][A-Za-z0-9-]{0,63}") || count >= 128)
                throw new IOException("Origin witness name/count exceeds its budget");
            output.write("{\"record\":\"witness\",\"kind\":");
            EvidenceJson.quoted(output, kind, stop);
            output.write(",\"value\":");
            producer.write(output);
            output.write("}\n");
            count++;
        }
    }

    private ContentOrigin(File file, long size, String sha, JSONObject header) throws Exception {
        this.file = file;
        this.size = size;
        sha256 = sha;
        subjectSha256 = string(header, "subjectSha256");
        subjectSize = integer(header, "subjectSizeBytes");
        relationship = string(header.getJSONObject("relationship"), "kind");
    }

    static JSONObject stored(InputSnapshot parent, FileRange range) throws Exception {
        if (range.fileSize != parent.size)
            throw new IOException("Stored origin range belongs to another input size");
        return new JSONObject()
                .put("kind", "stored-range")
                .put("parentSha256", parent.sha256)
                .put("parentSizeBytes", parent.size)
                .put("physicalOffset", range.offset)
                .put("storedSizeBytes", range.length)
                .put("entryIndex", JSONObject.NULL);
    }

    static JSONObject member(InputSnapshot parent, int index) throws Exception {
        if (index < 0) throw new IOException("Archive origin has no selected entry index");
        return new JSONObject()
                .put("kind", "archive-member")
                .put("parentSha256", parent.sha256)
                .put("parentSizeBytes", parent.size)
                .put("physicalOffset", JSONObject.NULL)
                .put("storedSizeBytes", JSONObject.NULL)
                .put("entryIndex", index);
    }

    static ContentOrigin create(
            File directory,
            InputSnapshot subject,
            JSONObject relationship,
            Producer producer,
            BooleanSupplier stop)
            throws Exception {
        subject.checkIdentity(stop);
        JSONObject header =
                new JSONObject()
                        .put("record", "capture")
                        .put("schema", SCHEMA)
                        .put("schemaVersion", 1)
                        .put("subjectSha256", subject.sha256)
                        .put("subjectSizeBytes", subject.size)
                        .put("relationship", relationship)
                        .put(
                                "meaning",
                                "Captured discovery/acquisition information for these exact bytes."
                                    + " SHA-256 identifies the capture; it does not authenticate an"
                                    + " engine or establish complete validity, runtime behavior or"
                                    + " safety.");
        validateHeader(header, subject.sha256, subject.size);
        StringWriter first = new StringWriter();
        EvidenceJson.write(first, header, stop);
        if (first.getBuffer().length() >= HEADER_LIMIT)
            throw new IOException("Origin identity header exceeds 8192 characters");
        PreparedExport staged = null;
        File destination = null;
        boolean success = false;
        try {
            staged =
                    PreparedExport.stream(
                            directory,
                            "content-origin.ndjson",
                            "application/x-ndjson",
                            out -> {
                                out.write(first.toString());
                                out.write('\n');
                                Records records = new Records(out, stop);
                                producer.write(records);
                                EvidenceJson.write(
                                        out,
                                        new JSONObject()
                                                .put("record", "end")
                                                .put("witnessCount", records.count),
                                        stop);
                                out.write('\n');
                            },
                            stop);
            subject.checkIdentity(stop);
            destination = File.createTempFile("content-origin-", ".ndjson", directory);
            Files.move(
                    staged.file.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            String sha = digest(destination, staged.size, stop);
            ContentOrigin result = new ContentOrigin(destination, staged.size, sha, header);
            success = true;
            return result;
        } finally {
            if (staged != null) staged.discard();
            if (!success && destination != null) destination.delete();
        }
    }

    String descriptor() throws Exception {
        return new JSONObject()
                .put("schema", SCHEMA)
                .put("schemaVersion", 1)
                .put("file", file.getName())
                .put("sizeBytes", size)
                .put("sha256", sha256)
                .toString();
    }

    static ContentOrigin restore(
            File directory,
            String descriptor,
            String subjectSha,
            long subjectSize,
            BooleanSupplier stop)
            throws Exception {
        JSONObject saved = descriptor(descriptor);
        File file = leaf(directory, string(saved, "file"));
        long size = integer(saved, "sizeBytes");
        String sha = string(saved, "sha256");
        if (!sha.matches("[0-9a-f]{64}") || !digest(file, size, stop).equals(sha))
            throw new IOException("Origin evidence expired or changed SHA-256");
        JSONObject header;
        try (Reader input =
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder line = new StringBuilder();
            int next;
            while ((next = input.read()) != '\n') {
                if (next < 0 || line.length() >= HEADER_LIMIT)
                    throw new IOException("Origin identity header is absent or exceeds its budget");
                line.append((char) next);
            }
            header = new JSONObject(line.toString());
        }
        validateHeader(header, subjectSha, subjectSize);
        return new ContentOrigin(file, size, sha, header);
    }

    private static void validateHeader(JSONObject header, String sha, long size) throws Exception {
        if (!SCHEMA.equals(string(header, "schema"))
                || integer(header, "schemaVersion") != 1
                || !"capture".equals(string(header, "record"))
                || sha == null
                || !sha.matches("[0-9a-f]{64}")
                || size < 0
                || !sha.equals(string(header, "subjectSha256"))
                || size != integer(header, "subjectSizeBytes"))
            throw new IOException("Origin evidence belongs to another captured input");
        JSONObject relation = header.getJSONObject("relationship");
        String kind = string(relation, "kind"), parent = string(relation, "parentSha256");
        long parentSize = integer(relation, "parentSizeBytes");
        if (!parent.matches("[0-9a-f]{64}") || parentSize < 0)
            throw new IOException("Origin has no exact parent identity");
        if (kind.equals("stored-range")) {
            FileRange range =
                    new FileRange(
                            parentSize,
                            integer(relation, "physicalOffset"),
                            integer(relation, "storedSizeBytes"));
            if (range.length != size
                    || !relation.has("entryIndex")
                    || !relation.isNull("entryIndex"))
                throw new IOException("Origin stored range differs from its captured bytes");
        } else if (kind.equals("archive-member")) {
            if (integer(relation, "entryIndex") < 0
                    || !relation.has("physicalOffset")
                    || !relation.isNull("physicalOffset")
                    || !relation.has("storedSizeBytes")
                    || !relation.isNull("storedSizeBytes"))
                throw new IOException("Derived member origin cannot assert a physical byte range");
        } else throw new IOException("Unsupported content origin relationship");
    }

    void checkIdentity(BooleanSupplier stop) throws Exception {
        if (!digest(file, size, stop).equals(sha256))
            throw new IOException("Captured origin SHA-256 changed");
    }

    PreparedExport export(File directory, BooleanSupplier stop) throws Exception {
        checkIdentity(stop);
        PreparedExport staged =
                PreparedExport.range(
                        directory,
                        "origin-" + subjectSha256.substring(0, 12) + ".ndjson",
                        file,
                        new FileRange(size, 0, size),
                        stop);
        try {
            checkIdentity(stop);
            if (!digest(staged.file, staged.size, stop).equals(sha256))
                throw new IOException("Staged origin differs from its captured SHA-256");
            return PreparedExport.restore(
                    directory,
                    staged.file.getAbsolutePath(),
                    staged.name,
                    "application/x-ndjson",
                    staged.size);
        } catch (Exception failure) {
            staged.discard();
            throw failure;
        }
    }

    static void discard(File directory, String descriptor) {
        try {
            leaf(directory, string(descriptor(descriptor), "file")).delete();
        } catch (Exception ignored) {
            /* No cleanup through unvalidated paths. */
        }
    }

    private static JSONObject descriptor(String encoded) throws Exception {
        if (encoded == null || encoded.length() > 2048)
            throw new IOException("Origin evidence descriptor is absent or exceeds its budget");
        JSONObject result = new JSONObject(encoded);
        if (!SCHEMA.equals(string(result, "schema")) || integer(result, "schemaVersion") != 1)
            throw new IOException("Unsupported origin evidence descriptor");
        return result;
    }

    private static File leaf(File directory, String name) throws IOException {
        if (!name.matches("content-origin-[A-Za-z0-9._-]+\\.ndjson"))
            throw new IOException("Invalid private origin filename");
        // Canonicalize the trusted Context root, not an untrusted leaf. Android can
        // expose an app directory through an ancestor alias such as /data/user/0.
        File expected = new File(directory.getCanonicalFile(), name);
        File file = expected.getCanonicalFile();
        if (!file.equals(expected) || !file.getParentFile().equals(directory.getCanonicalFile()))
            throw new IOException("Origin evidence is outside its private directory");
        return file;
    }

    private static String digest(File file, long size, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (size < 0 || size > MAX_BYTES || !file.isFile() || file.length() != size)
            throw new IOException("Origin evidence expired or exceeds its 256 MiB budget");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                EvidenceRows.check(stop);
                if (read > size - count) throw new IOException("Origin grew during hashing");
                digest.update(buffer, 0, read);
                count += read;
            }
        }
        if (count != size || file.length() != size)
            throw new IOException("Origin length changed while hashing");
        EvidenceRows.check(stop);
        return Inspector.toHex(digest.digest());
    }

    private static String string(JSONObject value, String key) throws Exception {
        Object result = value.get(key);
        if (!(result instanceof String)) throw new IOException("Origin " + key + " is not text");
        return (String) result;
    }

    private static long integer(JSONObject value, String key) throws Exception {
        Object result = value.get(key);
        if (!(result instanceof Integer) && !(result instanceof Long))
            throw new IOException("Origin " + key + " is not an exact signed integer");
        return ((Number) result).longValue();
    }

    @Override
    public void close() {
        file.delete();
    }
}

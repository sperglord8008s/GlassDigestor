// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.function.BooleanSupplier;

/** One retained native capture. Engine failures never erase another engine's recovered files. */
final class GalleryDiscovery {
    final String engine;
    private final File directory;
    private final BinaryRunner runner;
    private File capture;
    private String captureSha;
    private long captureSize;
    private String failure;
    CarvingBackend backend;
    EvidenceRows rows, catalog;

    GalleryDiscovery(String engine, File directory, BinaryRunner runner) {
        this.engine = engine;
        this.directory = directory;
        this.runner = runner;
        if (runner == null) failure = "Not recorded in this session";
    }

    void restore(JSONObject state, BooleanSupplier stop) throws Exception {
        if (state.has("failure")) failure = state.getString("failure");
        if (!state.has("capturePath")) return;
        File file = new File(state.getString("capturePath")).getCanonicalFile();
        if (!directory.getCanonicalFile().equals(file.getParentFile())
                || !file.getName().matches("gallerize-capture-[A-Za-z0-9._-]+\\.json"))
            throw new IOException("Native capture escaped the private Gallerize directory");
        capture = file;
        captureSha = state.getString("captureSha256");
        captureSize = state.getLong("captureSizeBytes");
        read(stop); // Never silently replace a missing, changed or corrupt retained capture.
        if (failure == null || failure.equals("Not recorded in this session")) failure = null;
    }

    void load(InputSnapshot input, String name, BooleanSupplier stop) throws Exception {
        if (rows != null || failure != null) return;
        if (engine.equals("binwalk") && input.size > CarvingBackend.MAX_BYTES) {
            failure =
                    "Not scanned: input exceeds Binwalk's 64 MiB whole-input budget; PhotoRec"
                            + " retains its separate 256 MiB budget";
            return;
        }
        File previous = capture;
        BinaryRunner retainedRunner =
                (file, cancelled) -> {
                    if (capture != null) return read(cancelled);
                    String encoded = runner.scan(file, cancelled);
                    if (encoded == null || encoded.length() > 32 * 1024 * 1024)
                        throw new IOException("Native gallery capture exceeds 32 Mi-characters");
                    byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > 64 * 1024 * 1024)
                        throw new IOException("Native capture exceeds 64 MiB");
                    File stage = File.createTempFile("gallerize-capture-", ".json", directory);
                    try {
                        try (OutputStream output = new FileOutputStream(stage)) {
                            output.write(bytes);
                        }
                        EvidenceRows.check(cancelled);
                        captureSha =
                                Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                        captureSize = bytes.length;
                        capture = stage;
                    } finally {
                        if (capture != stage) stage.delete();
                    }
                    return encoded;
                };
        CarvingBackend candidate =
                engine.equals("photorec")
                        ? new PhotoRecBackend(input, name, retainedRunner)
                        : new BinwalkBackend(input, name, retainedRunner);
        boolean success = false;
        try {
            EvidenceRows evidence = candidate.open("all", stop);
            EvidenceRows patterns = candidate.open("catalog", stop);
            input.checkIdentity(stop);
            rows = evidence;
            catalog = patterns;
            backend = candidate;
            success = true;
        } catch (Exception error) {
            EvidenceRows.check(stop);
            if (error instanceof InterruptedIOException) throw error;
            String diagnostic = error.toString();
            failure =
                    diagnostic.length() <= 8192
                            ? diagnostic
                            : diagnostic.substring(0, 8192)
                                    + " [diagnostic truncated; original "
                                    + diagnostic.length()
                                    + " characters]";
        } finally {
            if (!success) {
                candidate.close();
                if (previous == null && capture != null) {
                    capture.delete();
                    capture = null;
                }
            }
        }
    }

    private String read(BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (captureSize < 0
                || captureSize > 64L * 1024 * 1024
                || !capture.isFile()
                || capture.length() != captureSize
                || captureSha == null
                || !captureSha.matches("[0-9a-f]{64}"))
            throw new IOException("Original native capture expired or exceeded its budget");
        byte[] bytes = Files.readAllBytes(capture.toPath());
        if (bytes.length != captureSize
                || !captureSha.equals(
                        Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
            throw new IOException("Original native capture identity changed");
        EvidenceRows.check(stop);
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    JSONObject state() throws Exception {
        JSONObject state = new JSONObject();
        if (capture != null)
            state.put("capturePath", capture.getAbsolutePath())
                    .put("captureSha256", captureSha)
                    .put("captureSizeBytes", captureSize);
        if (failure != null) state.put("failure", failure);
        return state;
    }

    JSONObject captureIdentity() throws Exception {
        return capture == null
                ? null
                : new JSONObject().put("sha256", captureSha).put("sizeBytes", captureSize);
    }

    JSONObject provenance() throws Exception {
        return new JSONObject()
                .put("engine", engine)
                .put("status", status())
                .put("completed", rows != null)
                .put("capture", capture == null ? JSONObject.NULL : captureIdentity())
                .put("native", backend == null ? JSONObject.NULL : backend.provenance());
    }

    String status() {
        return rows != null
                ? backend.statusSummary()
                : failure != null ? failure : "Native content analysis has not completed";
    }

    void close(boolean retain) throws Exception {
        try {
            if (backend != null) backend.close();
        } finally {
            try {
                if (runner != null) runner.close();
            } finally {
                if (!retain && capture != null) capture.delete();
            }
        }
    }
}

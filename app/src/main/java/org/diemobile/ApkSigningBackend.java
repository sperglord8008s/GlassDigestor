// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Complete, owned APK signing evidence and explicitly selected detached signature input. */
final class ApkSigningBackend extends InvestigationBackend {
    private final File input, directory;
    private final String name, expectedSha, restoredState;
    private final long expectedSize;
    private InputSnapshot apk, idsig;
    private File session;
    private String idsigName = "";
    private ApkSigningEvidence embedded, selected;
    private String capturedAt;
    private boolean showDetached;

    ApkSigningBackend(
            File input,
            File directory,
            String name,
            String expectedSha,
            long expectedSize,
            String restoredState) {
        this.input = input;
        this.directory = directory;
        this.name = name;
        this.expectedSha = expectedSha;
        this.expectedSize = expectedSize;
        this.restoredState = restoredState;
    }

    private static String request(String mode, String table) throws Exception {
        return new JSONObject().put("mode", mode).put("table", table).toString();
    }

    private void ensure(BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (apk != null) return;
        InputSnapshot acquired = null, detached = null;
        boolean adopted = false;
        try {
            if (restoredState == null) {
                if (!directory.isDirectory() && !directory.mkdirs())
                    throw new IOException("Cannot create signing snapshot directory");
                session =
                        Files.createTempDirectory(directory.toPath(), "signing-session-").toFile();
                acquired =
                        InputSnapshot.acquire(input, session, ApkSigningInput.MAX_APK_BYTES, stop);
            } else {
                File restored =
                        new File(new JSONObject(restoredState).getString("sessionDirectory"));
                if (!restored.getCanonicalFile()
                                .getParentFile()
                                .equals(directory.getCanonicalFile())
                        || !restored.getName().startsWith("signing-session-"))
                    throw new IOException("Invalid signing session restoration directory");
                session = restored;
                File manifest = new File(session, "source-state.json");
                if (!manifest.isFile() || manifest.length() > 32768)
                    throw new IOException(
                            "Signing source restoration state expired / exceeds budget");
                JSONObject state =
                        new JSONObject(
                                new String(
                                        Files.readAllBytes(manifest.toPath()),
                                        StandardCharsets.UTF_8));
                acquired = restore(state.getJSONObject("apk"), ApkSigningInput.MAX_APK_BYTES, stop);
                if (!state.isNull("idsig")) {
                    detached =
                            restore(
                                    state.getJSONObject("idsig"),
                                    ApkSigningInput.MAX_IDSIG_BYTES,
                                    stop);
                    idsigName = state.getString("idsigName");
                }
            }
            if (expectedSize >= 0 && acquired.size != expectedSize
                    || expectedSha != null && !expectedSha.equals(acquired.sha256))
                throw new IOException("Selected APK differs from its captured scan size / SHA-256");
            ApkSigningEvidence base =
                    ApkSigningEvidence.capture(
                            ApkSigningReport.verify(acquired.file, null, stop), false, stop);
            ApkSigningEvidence current =
                    detached == null
                            ? base
                            : ApkSigningEvidence.capture(
                                    ApkSigningReport.verify(acquired.file, detached.file, stop),
                                    true,
                                    stop);
            acquired.checkIdentity(stop);
            if (detached != null) detached.checkIdentity(stop);
            EvidenceRows.check(stop);
            persistSources(acquired, detached, idsigName, stop);
            apk = acquired;
            idsig = detached;
            embedded = base;
            selected = current;
            capturedAt = java.time.Instant.now().toString();
            adopted = true;
        } finally {
            if (!adopted) {
                if (acquired != null) acquired.close();
                if (detached != null) detached.close();
                if (restoredState == null && session != null) session.delete();
            }
        }
    }

    private InputSnapshot restore(JSONObject state, long maximum, BooleanSupplier stop)
            throws Exception {
        long size = state.getLong("sizeBytes");
        if (size < 0 || size > maximum)
            throw new IOException("Restored signing input exceeds its size budget");
        return InputSnapshot.restore(
                new File(state.getString("path")), session, size, state.getString("sha256"), stop);
    }

    @Override
    String initialRequest(BooleanSupplier stop) throws Exception {
        return request("selected", "summary");
    }

    @Override
    EvidenceRows open(String encoded, BooleanSupplier stop) throws Exception {
        ensure(stop);
        JSONObject query = new JSONObject(encoded);
        String mode = query.getString("mode"), table = query.getString("table");
        if (!mode.equals("selected") && !mode.equals("embedded"))
            throw new IOException("Unknown signing evidence source mode");
        ApkSigningEvidence evidence = mode.equals("embedded") ? embedded : selected;
        showDetached = table.equals("detached-input");
        if (showDetached && idsig == null) throw new IOException("No detached signature selected");
        JSONObject origin = provenance();
        if (table.equals("detached-input")) {
            idsig.checkIdentity(stop);
            JSONObject fields =
                    input(idsig, idsigName)
                            .put("name", idsigName)
                            .put("kind", "explicit detached v4 input")
                            .put(
                                    "layout",
                                    selected.table("summary")
                                            .getJSONObject(0)
                                            .opt("detachedLayout"));
            return single(
                    "apk-signing-detached-input",
                    "Selected detached signature / full input identity",
                    fields,
                    origin,
                    idsig.size);
        }
        List<String> tables =
                table.equals("all")
                        ? Arrays.asList(ApkSigningEvidence.TABLES)
                        : Collections.singletonList(table);
        int count = 0;
        for (String key : tables) count += evidence.table(key).length();
        final int size = count;
        return new EvidenceRows(
                "apk-signing-" + table,
                (mode.equals("embedded") ? "Embedded APK only • " : "Selected signing inputs • ")
                        + title(table)) {
            int size() {
                return size;
            }

            Row row(int index, BooleanSupplier cancelled) throws Exception {
                EvidenceRows.check(cancelled);
                if (index < 0 || index >= size)
                    throw new IOException("Signing record index outside captured table");
                int offset = index;
                for (String key : tables) {
                    JSONArray rows = evidence.table(key);
                    if (offset < rows.length()) {
                        JSONObject fields =
                                new JSONObject(rows.getJSONObject(offset).toString())
                                        .put("table", key)
                                        .put("tableIndex", offset)
                                        .put("sourceMode", mode);
                        if (key.equals("summary"))
                            fields.put(
                                    "embeddedApkVerifiedWithoutDetached",
                                    embedded.table("summary")
                                            .getJSONObject(0)
                                            .getBoolean("verified"));
                        String name = fields.getString("name");
                        String description =
                                key.equals("summary")
                                        ? "completed="
                                                + fields.getBoolean("completed")
                                                + " • verified="
                                                + fields.getBoolean("verified")
                                                + " • detached supplied="
                                                + fields.getBoolean("idsigSupplied")
                                        : key.equals("diagnostics")
                                                ? fields.getString("scope")
                                                        + " • "
                                                        + fields.getString("severity")
                                                        + " • "
                                                        + fields.getString("message")
                                                : key.equals("certificates")
                                                        ? "SHA-256 "
                                                                + fields.getString(
                                                                        "certificateSha256")
                                                        : key.equals("signers")
                                                                ? fields.getString("scheme")
                                                                        + " • certificate"
                                                                        + " references "
                                                                        + fields.getJSONArray(
                                                                                "certificateIndices")
                                                                : key.equals("lineages")
                                                                        ? fields.getJSONArray(
                                                                                                "nodes")
                                                                                        .length()
                                                                                + " predecessor-to-descendant"
                                                                                + " entries"
                                                                        : key;
                        return new Row(index, name, description, null, null, fields);
                    }
                    offset -= rows.length();
                }
                throw new IOException("Signing table changed during read");
            }

            JSONObject metadata() throws Exception {
                return new JSONObject()
                        .put("sourceMode", mode)
                        .put("table", table)
                        .put("source", origin)
                        .put(
                                "recordCoverage",
                                "All returned public-API records for this captured verification,"
                                    + " within declared budgets; parser failure is not a completed"
                                    + " negative verification")
                        .put("limits", limits());
            }
        };
    }

    private EvidenceRows single(
            String kind, String title, JSONObject fields, JSONObject origin, long size) {
        return new EvidenceRows(kind, title) {
            int size() {
                return 1;
            }

            Row row(int index, BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);
                if (index != 0) throw new IOException("Input record index outside scope");
                return new Row(
                        0,
                        fields.getString("name"),
                        "Exact acquired input • SHA-256 " + fields.getString("sha256"),
                        0L,
                        size,
                        new JSONObject(fields.toString()));
            }

            JSONObject metadata() throws Exception {
                return origin;
            }
        };
    }

    private static String title(String table) {
        switch (table) {
            case "summary":
                return "verification / platform range / coverage";
            case "signers":
                return "all scheme signer records / SDK targeting";
            case "certificates":
                return "all collected certificates / DER / SPKI / extensions";
            case "lineages":
                return "every scoped rotation lineage / capabilities";
            case "digests":
                return "returned signing content digests";
            case "diagnostics":
                return "all scoped errors / warnings / information";
            case "stamp":
                return "distribution source stamp / signer / timestamp";
            default:
                return "complete signing information";
        }
    }

    @Override
    List<Link> roots(String query, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        List<Link> result = new ArrayList<>();
        for (String table : ApkSigningEvidence.TABLES)
            result.add(Link.view(title(table), request("selected", table)));
        result.add(
                Link.view(
                        "All collected signing information (complete combined export)",
                        request("selected", "all")));
        result.add(
                Link.view(
                        "Embedded APK information without detached signature",
                        request("embedded", "all")));
        result.add(Link.input("Select / replace detached v4 .idsig (offline)", "idsig"));
        if (idsig != null) {
            result.add(
                    Link.view(
                            "Selected detached input / identity / layout",
                            request("selected", "detached-input")));
            result.add(
                    Link.blob(
                            "Export exact selected .idsig bytes",
                            request("selected", "idsig-bytes"),
                            "selected-signature.idsig"));
            result.add(
                    Link.input(
                            "Clear detached signature; keep embedded APK information", "clear-idsig"));
        }
        return result;
    }

    @Override
    List<Link> related(
            String request, EvidenceRows source, EvidenceRows.Row row, BooleanSupplier stop)
            throws Exception {
        EvidenceRows.check(stop);
        JSONObject fields = row.json();
        List<Link> links = new ArrayList<>();
        if (source.kind.equals("apk-signing-detached-input")) {
            if (idsig == null) throw new IOException("Detached source expired");
            idsig.checkIdentity(stop);
        }
        if (!fields.has("table")) return links;
        String mode = fields.getString("sourceMode"), table = fields.getString("table");
        int index = fields.getInt("tableIndex");
        for (String key :
                new String[] {
                    "certificateIndices",
                    "verifiedSignerCertificateIndices",
                    "certificateLineageIndices"
                }) {
            JSONArray ids = fields.optJSONArray(key);
            if (ids != null)
                for (int i = 0; i < ids.length(); i++) certificateLink(links, mode, ids.getInt(i));
        }
        if (fields.has("certificateIndex") && !fields.isNull("certificateIndex"))
            certificateLink(links, mode, fields.getInt("certificateIndex"));
        for (String key : new String[] {"lineageIndex", "combinedLineageIndex"})
            if (fields.has(key) && !fields.isNull(key))
                links.add(
                        Link.jump(
                                "Inspect original scoped rotation lineage " + fields.getInt(key),
                                request(mode, "lineages"),
                                fields.getInt(key)));
        if (table.equals("lineages")) {
            JSONArray nodes = fields.getJSONArray("nodes");
            for (int i = 0; i < nodes.length(); i++)
                certificateLink(links, mode, nodes.getJSONObject(i).getInt("certificateIndex"));
            links.add(
                    blob(
                            "Export upstream re-encoded proof-of-rotation attribute",
                            mode,
                            table,
                            index,
                            "encodedAttributeBase64",
                            -1,
                            "rotation-attribute.bin"));
        }
        JSONArray digests = fields.optJSONArray("digestIndices");
        if (digests != null)
            for (int i = 0; i < digests.length(); i++)
                links.add(
                        Link.jump(
                                "Inspect content digest " + digests.getInt(i),
                                request(mode, "digests"),
                                digests.getInt(i)));
        if (table.equals("certificates")) {
            for (String key :
                    new String[] {
                        "derBase64",
                        "tbsCertificateBase64",
                        "signatureBase64",
                        "signatureAlgorithmParametersBase64",
                        "subjectDerBase64",
                        "issuerDerBase64"
                    })
                if (!fields.isNull(key))
                    links.add(
                            blob(
                                    "Export " + key,
                                    mode,
                                    table,
                                    index,
                                    key,
                                    -1,
                                    "certificate-" + index + "-" + key + ".der"));
            if (!fields.getJSONObject("publicKey").isNull("spkiBase64"))
                links.add(
                        blob(
                                "Export SubjectPublicKeyInfo DER",
                                mode,
                                table,
                                index,
                                "spkiBase64",
                                -1,
                                "certificate-" + index + "-spki.der"));
            JSONArray extensions = fields.getJSONArray("extensions");
            for (int i = 0; i < extensions.length(); i++)
                links.add(
                        blob(
                                "Export extension "
                                        + extensions.getJSONObject(i).getString("oid")
                                        + " DER OCTET STRING",
                                mode,
                                table,
                                index,
                                "extension",
                                i,
                                "certificate-" + index + "-extension-" + i + ".der"));
        }
        if (table.equals("digests"))
            links.add(
                    blob(
                            "Export returned content digest bytes",
                            mode,
                            table,
                            index,
                            "valueBase64",
                            -1,
                            "content-digest.bin"));
        return links;
    }

    private static void certificateLink(List<Link> links, String mode, int index) throws Exception {
        links.add(
                Link.jump(
                        "Inspect certificate " + index + " / full DER / SPKI / extensions",
                        request(mode, "certificates"),
                        index));
    }

    private static Link blob(
            String label,
            String mode,
            String table,
            int row,
            String field,
            int extension,
            String name)
            throws Exception {
        return Link.blob(
                label,
                new JSONObject()
                        .put("mode", mode)
                        .put("table", table)
                        .put("row", row)
                        .put("field", field)
                        .put("extension", extension)
                        .toString(),
                name);
    }

    @Override
    byte[] binaryValue(String encoded, BooleanSupplier stop) throws Exception {
        ensure(stop);
        JSONObject query = new JSONObject(encoded);
        String table = query.getString("table");
        if (table.equals("idsig-bytes")) {
            if (idsig == null || !idsig.file.isFile() || idsig.file.length() != idsig.size)
                throw new IOException("Detached input expired / changed");
            byte[] bytes = new byte[(int) idsig.size];
            try (DataInputStream input = new DataInputStream(new FileInputStream(idsig.file))) {
                input.readFully(bytes);
                if (input.read() != -1) throw new IOException("Detached input grew during export");
            }
            if (bytes.length != idsig.size
                    || !Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                            .equals(idsig.sha256))
                throw new IOException("Detached input identity changed");
            EvidenceRows.check(stop);
            return bytes;
        }
        String mode = query.getString("mode");
        if (!mode.equals("selected") && !mode.equals("embedded"))
            throw new IOException("Unknown signing source mode");
        ApkSigningEvidence evidence = mode.equals("embedded") ? embedded : selected;
        JSONObject row = evidence.table(table).getJSONObject(query.getInt("row"));
        String field = query.getString("field");
        Object value;
        if (table.equals("certificates") && field.equals("spkiBase64"))
            value = row.getJSONObject("publicKey").get(field);
        else if (table.equals("certificates") && field.equals("extension"))
            value =
                    row.getJSONArray("extensions")
                            .getJSONObject(query.getInt("extension"))
                            .get("derOctetStringBase64");
        else if (table.equals("certificates")
                        && Arrays.asList(
                                        "derBase64",
                                        "tbsCertificateBase64",
                                        "signatureBase64",
                                        "signatureAlgorithmParametersBase64",
                                        "subjectDerBase64",
                                        "issuerDerBase64")
                                .contains(field)
                || table.equals("lineages") && field.equals("encodedAttributeBase64")
                || table.equals("digests") && field.equals("valueBase64")) value = row.get(field);
        else throw new IOException("Unknown signing binary value selector");
        if (!(value instanceof String))
            throw new IOException("No binary value returned for selector");
        byte[] result = Base64.getDecoder().decode((String) value);
        EvidenceRows.check(stop);
        return result;
    }

    @Override
    void acceptInput(String request, InputStream stream, String displayName, BooleanSupplier stop)
            throws Exception {
        ensure(stop);
        if (request.equals("clear-idsig")) {
            EvidenceRows.check(stop);
            persistSources(apk, null, "", stop);
            if (idsig != null) idsig.close();
            idsig = null;
            idsigName = "";
            selected = embedded;
            capturedAt = java.time.Instant.now().toString();
            showDetached = false;
            return;
        }
        if (!request.equals("idsig")
                || stream == null
                || displayName == null
                || displayName.length() > 4096)
            throw new IOException("Invalid detached signature input request/name");
        InputSnapshot candidate =
                InputSnapshot.acquire(stream, session, ApkSigningInput.MAX_IDSIG_BYTES, stop);
        boolean adopted = false;
        try {
            apk.checkIdentity(stop);
            ApkSigningEvidence evidence =
                    ApkSigningEvidence.capture(
                            ApkSigningReport.verify(apk.file, candidate.file, stop), true, stop);
            apk.checkIdentity(stop);
            candidate.checkIdentity(stop);
            EvidenceRows.check(stop);
            persistSources(apk, candidate, displayName, stop);
            InputSnapshot old = idsig;
            idsig = candidate;
            idsigName = displayName;
            selected = evidence;
            capturedAt = java.time.Instant.now().toString();
            showDetached = false;
            adopted = true;
            if (old != null) old.close();
        } finally {
            if (!adopted) candidate.close();
        }
    }

    @Override
    boolean inputRequiresDocument(String request) {
        return !request.equals("clear-idsig");
    }

    @Override
    JSONObject provenance() throws Exception {
        if (apk == null) throw new IOException("APK signing source not acquired");
        return new JSONObject()
                .put("schema", "org.diemobile.apk-signing")
                .put("schemaVersion", 1)
                .put("adapterVersion", ApkSigningEvidence.ADAPTER_VERSION)
                .put("engine", "AOSP apksig")
                .put("engineVersion", ApkSigningReport.VERSION)
                .put("capturedAt", capturedAt)
                .put("buildVerifiedEngineJarSha256", ApkSigningReport.BINARY_SHA256)
                .put("reviewedSourceJarSha256", ApkSigningReport.SOURCES_SHA256)
                .put("input", input(apk, name))
                .put("detachedInput", idsig == null ? JSONObject.NULL : input(idsig, idsigName))
                .put("limits", limits())
                .put(
                        "process",
                        "In-process Java parsing/cryptographic verification; cooperative"
                            + " read/deadline checks, not hard worker isolation")
                .put(
                        "runtimeVerification",
                        "Host and Robolectric fixtures are separate from handset/real SAF/platform"
                            + " acceptance");
    }

    private static JSONObject input(InputSnapshot snapshot, String name) throws Exception {
        return new JSONObject()
                .put("name", name)
                .put("sizeBytes", snapshot.size)
                .put("sha256", snapshot.sha256);
    }

    private static JSONObject limits() throws Exception {
        return new JSONObject()
                .put("apkBytes", ApkSigningInput.MAX_APK_BYTES)
                .put("idsigBytes", ApkSigningInput.MAX_IDSIG_BYTES)
                .put("contiguousApkBufferBytes", ApkSigningInput.MAX_BUFFER_BYTES)
                .put("cumulativeApkReadBytes", ApkSigningInput.MAX_READ_BYTES)
                .put("verificationDeadlineSeconds", 30)
                .put("records", ApkSigningEvidence.MAX_RECORDS)
                .put("evidenceCharacters", ApkSigningEvidence.MAX_CHARACTERS)
                .put("certificateBytes", ApkSigningEvidence.MAX_CERTIFICATE_BYTES)
                .put(
                        "limitMeaning",
                        "Exceeded acquisition/result budgets fail explicitly; deadlines and"
                            + " cancellation are cooperative, including potentially long upstream"
                            + " CPU/inflate operations");
    }

    @Override
    String retentionState() throws Exception {
        if (apk == null) return null;
        return new JSONObject().put("sessionDirectory", session.getAbsolutePath()).toString();
    }

    private void persistSources(
            InputSnapshot input, InputSnapshot detached, String detachedName, BooleanSupplier stop)
            throws Exception {
        JSONObject state =
                new JSONObject()
                        .put("apk", descriptor(input))
                        .put("idsig", detached == null ? JSONObject.NULL : descriptor(detached))
                        .put("idsigName", detachedName);
        File stage = File.createTempFile("source-state-", ".json", session);
        try {
            try (Writer out =
                    new OutputStreamWriter(new FileOutputStream(stage), StandardCharsets.UTF_8)) {
                EvidenceJson.write(out, state, stop);
            }
            EvidenceRows.check(stop);
            Files.move(
                    stage.toPath(),
                    new File(session, "source-state.json").toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            stage.delete();
        }
    }

    private static JSONObject descriptor(InputSnapshot snapshot) throws Exception {
        return new JSONObject()
                .put("path", snapshot.file.getAbsolutePath())
                .put("sizeBytes", snapshot.size)
                .put("sha256", snapshot.sha256);
    }

    @Override
    File byteSource() {
        return showDetached && idsig != null ? idsig.file : apk == null ? input : apk.file;
    }

    @Override
    String sourceName() {
        return showDetached ? idsigName : name;
    }

    @Override
    String help(String request) {
        return "AOSP apksig • every returned signer, certificate, scoped lineage, digest and"
                   + " diagnostic. Root views select full tables and optional detached v4 input."
                   + " False scheme flags do not establish absence; parsed certificates do not"
                   + " establish trust. Tap for checked API range, source identities and limits."
                   + " Derived records have no invented APK byte offsets.";
    }

    @Override
    public void close() {
        close(false);
    }

    @Override
    void close(boolean retainInputs) {
        if (retainInputs) return;
        if (apk != null) apk.close();
        if (idsig != null) idsig.close();
        if (session != null) {
            File[] files = session.listFiles();
            if (files != null)
                for (File file : files)
                    if (file.getName().equals("source-state.json")
                            || file.getName().startsWith("analysis-input-")
                                    && file.getName().endsWith(".snapshot")
                            || file.getName().startsWith("source-state-")
                                    && file.getName().endsWith(".json")) file.delete();
            session.delete();
        }
    }
}

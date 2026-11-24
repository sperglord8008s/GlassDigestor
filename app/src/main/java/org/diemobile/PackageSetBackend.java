// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Read-only captured base/split workbench. Acquisition, declarations, relationships and UI stay
 * separate.
 */
final class PackageSetBackend extends InvestigationBackend {
    private final File root, directory;
    private final String name, sha, inventoryPackage, restoredState;
    private final long size;
    private final List<String> paths;
    private final BinaryRunner runner;
    private File session, indexFile;
    private long indexSize;
    private String indexSha, capturedAt, graphError = "";
    private PackageSetDocument document;
    private PackageSetGraph graph;
    private File activeBytes;
    private String activeName;

    PackageSetBackend(
            File root,
            File directory,
            String name,
            String sha,
            long size,
            List<String> paths,
            String inventoryPackage,
            String restoredState,
            BinaryRunner runner) {
        this.root = root;
        this.directory = directory;
        this.name = name;
        this.sha = sha;
        this.size = size;
        this.paths = paths == null ? null : new ArrayList<>(paths);
        this.inventoryPackage = inventoryPackage;
        this.restoredState = restoredState;
        this.runner = runner;
    }

    private static String query(String table, int source) throws Exception {
        return new JSONObject().put("table", table).put("source", source).toString();
    }

    @Override
    String initialRequest(BooleanSupplier stop) throws Exception {
        return query("summary", -1);
    }

    private void ensure(BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (document != null) return;
        PackageSetDocument candidate = null;
        boolean adopted = false;
        try {
            List<PackageSetDocument.Input> inputs = new ArrayList<>();
            if (restoredState == null) {
                if (!directory.isDirectory() && !directory.mkdirs())
                    throw new IOException("Cannot create package-set snapshot root");
                session =
                        Files.createTempDirectory(directory.toPath(), "package-session-").toFile();
                if (paths == null) inputs.add(new PackageSetDocument.Input(root, name, sha, size));
                else
                    for (String path : paths) {
                        if (path == null || path.length() > 8192)
                            throw new IOException("Invalid package source path");
                        File file = new File(path);
                        inputs.add(new PackageSetDocument.Input(file, file.getName(), null, -1));
                    }
            } else {
                File restored =
                        new File(new JSONObject(restoredState).getString("sessionDirectory"));
                if (!Objects.equals(
                                restored.getCanonicalFile().getParentFile(),
                                directory.getCanonicalFile())
                        || !restored.getName().startsWith("package-session-"))
                    throw new IOException("Invalid package-session restoration directory");
                session = restored;
                File stateFile = new File(session, "source-state.json");
                if (!stateFile.isFile() || stateFile.length() > 2 * 1024 * 1024)
                    throw new IOException(
                            "Package-set private source state expired / exceeds 2 MiB");
                JSONObject state =
                        new JSONObject(
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .decode(
                                                ByteBuffer.wrap(
                                                        Files.readAllBytes(stateFile.toPath())))
                                        .toString());
                JSONArray saved = state.getJSONArray("inputs");
                if (saved.length() < 1 || saved.length() > PackageSetDocument.MAX_INPUTS)
                    throw new IOException("Invalid retained package input count");
                for (int i = 0; i < saved.length(); i++) {
                    JSONObject input = saved.getJSONObject(i);
                    String error = input.optString("error", "");
                    inputs.add(
                            new PackageSetDocument.Input(
                                    error.isEmpty() ? new File(input.getString("path")) : null,
                                    input.getString("name"),
                                    error.isEmpty() ? input.getString("sha256") : null,
                                    error.isEmpty()
                                            ? input.getLong("sizeBytes")
                                            : input.optLong("declaredBytes", -1),
                                    true,
                                    error));
                }
            }
            candidate = PackageSetDocument.read(inputs, session, runner, stop);
            if (inventoryPackage != null)
                for (PackageSetDocument.Source source : candidate.sources)
                    if (source.manifest != null
                            && source.manifest.packageName() != null
                            && !inventoryPackage.equals(source.manifest.packageName()))
                        source.error(
                                "inventory-package-association",
                                new IOException(
                                        "Literal manifest package differs from the selected"
                                                + " PackageManager inventory association; compare"
                                                + " provenance.observedInventoryPackageName"));
            try {
                graph = new PackageSetGraph(candidate, stop);
            } catch (Exception | StackOverflowError error) {
                EvidenceRows.check(stop);
                graphError = error.toString();
                graph = null;
            }
            persistSources(candidate, stop);
            capturedAt = java.time.Instant.now().toString();
            writeIndex(candidate, stop);
            EvidenceRows.check(stop);
            document = candidate;
            activeBytes = indexFile;
            activeName = "Captured APK input index";
            adopted = true;
        } finally {
            if (!adopted) {
                if (candidate != null) candidate.close();
                cleanupSession();
            }
        }
    }

    private void persistSources(PackageSetDocument captured, BooleanSupplier stop)
            throws Exception {
        JSONArray inputs = new JSONArray();
        for (PackageSetDocument.Source source : captured.sources) {
            JSONObject input = new JSONObject().put("name", source.name);
            if (source.apk != null && source.identityStable)
                input.put("path", source.apk.file.getAbsolutePath())
                        .put("sizeBytes", source.apk.size)
                        .put("sha256", source.apk.sha256);
            else
                input.put("error", source.errors.toString())
                        .put(
                                "declaredBytes",
                                source.declaredBytes == null
                                        ? JSONObject.NULL
                                        : source.declaredBytes);
            inputs.put(input);
        }
        File stage = File.createTempFile("source-state-", ".json", session);
        try {
            try (OutputStream output = new FileOutputStream(stage);
                    Writer writer =
                            new OutputStreamWriter(
                                    new FilterOutputStream(output) {
                                        private int written;

                                        @Override
                                        public void write(int value) throws IOException {
                                            write(new byte[] {(byte) value}, 0, 1);
                                        }

                                        @Override
                                        public void write(byte[] value, int offset, int count)
                                                throws IOException {
                                            if (count > 2 * 1024 * 1024 - written)
                                                throw new IOException(
                                                        "Package-set private source state exceeds 2"
                                                                + " MiB");
                                            out.write(value, offset, count);
                                            written += count;
                                        }
                                    },
                                    StandardCharsets.UTF_8)) {
                EvidenceJson.write(writer, new JSONObject().put("inputs", inputs), stop);
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

    private void writeIndex(PackageSetDocument captured, BooleanSupplier stop) throws Exception {
        JSONArray inputs = new JSONArray();
        for (PackageSetDocument.Source source : captured.sources) {
            EvidenceRows.check(stop);
            inputs.put(source.identity());
        }
        File stage = File.createTempFile("input-index-", ".json", session);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try {
            try (Writer writer =
                    new OutputStreamWriter(
                            new DigestOutputStream(new FileOutputStream(stage), digest),
                            StandardCharsets.UTF_8)) {
                EvidenceJson.write(
                        writer,
                        new JSONObject()
                                .put("schema", "org.diemobile.package-input-index")
                                .put("schemaVersion", 1)
                                .put("capturedAt", capturedAt)
                                .put("inputs", inputs),
                        stop);
            }
            EvidenceRows.check(stop);
            indexSize = stage.length();
            indexSha = Inspector.toHex(digest.digest());
            indexFile = new File(session, "public-input-index.json");
            Files.move(
                    stage.toPath(),
                    indexFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            stage.delete();
        }
    }

    @Override
    EvidenceRows open(String encoded, BooleanSupplier stop) throws Exception {
        ensure(stop);
        JSONObject request = new JSONObject(encoded);
        String table = request.getString("table");
        int source = request.optInt("source", -1);
        activeBytes = indexFile;
        activeName = "Captured APK input index";
        if (table.equals("apk-input") || table.equals("manifest-input"))
            return inputRows(source, table.equals("manifest-input"), stop);
        List<PackageSetRows.Record> rows = new ArrayList<>();
        if (table.equals("all")) {
            for (String key : PackageSetGraph.TABLES) addGraph(rows, key, stop);
            for (PackageSetDocument.Source input : document.sources) {
                addSource(rows, input);
                addManifest(rows, input, "manifest-fields");
                addManifest(rows, input, "manifest-elements");
                addManifest(rows, input, "uses-splits");
                if (input.signing != null)
                    for (String key : ApkSigningEvidence.TABLES)
                        addSigning(rows, input, "signing-" + key);
            }
        } else if (Arrays.asList(PackageSetGraph.TABLES).contains(table))
            addGraph(rows, table, stop);
        else if (table.equals("sources")) {
            for (PackageSetDocument.Source input : document.sources) addSource(rows, input);
        } else if (table.startsWith("signing-")) addSigning(rows, source(source), table);
        else if (table.equals("manifest-fields")
                || table.equals("manifest-elements")
                || table.equals("uses-splits")) addManifest(rows, source(source), table);
        else throw new IOException("Unknown package-set table");
        EvidenceRows.check(stop);
        JSONObject metadata =
                new JSONObject()
                        .put("table", table)
                        .put("selectedSourceIndex", source < 0 ? JSONObject.NULL : source)
                        .put("graphAvailable", graph != null)
                        .put("graphError", graphError)
                        .put(
                                "scope",
                                "Complete selected captured table, independent of search/page;"
                                        + " preview limits do not shorten JSON exports")
                        .put("limits", limits());
        return new PackageSetRows(table, "Package set • " + table, rows, metadata);
    }

    private static void addRow(List<PackageSetRows.Record> rows, PackageSetRows.Record row)
            throws IOException {
        if (rows.size() >= PackageSetRows.MAX_ROWS)
            throw new IOException(
                    "Selected package-set scope exceeds 100,000 records; choose an individual"
                            + " source/table");
        rows.add(row);
    }

    private void addGraph(List<PackageSetRows.Record> rows, String table, BooleanSupplier stop)
            throws Exception {
        if (graph == null) {
            if (table.equals("summary"))
                addRow(
                        rows,
                        new PackageSetRows.Record(
                                -1,
                                table,
                                0,
                                new JSONObject()
                                        .put(
                                                "name",
                                                "Relationship analysis unavailable; source information"
                                                        + " remains inspectable")
                                        .put("error", graphError)
                                        .put("graphAvailable", false)));
            return;
        }
        JSONArray values = graph.tables.getJSONArray(table);
        for (int i = 0; i < values.length(); i++) {
            EvidenceRows.check(stop);
            JSONObject value = values.getJSONObject(i);
            addRow(
                    rows,
                    new PackageSetRows.Record(value.optInt("sourceIndex", -1), table, i, value));
        }
    }

    private void addSource(List<PackageSetRows.Record> rows, PackageSetDocument.Source source)
            throws Exception {
        addRow(
                rows,
                new PackageSetRows.Record(
                        source.index, "sources", source.index, source.identity()));
    }

    private void addManifest(
            List<PackageSetRows.Record> rows, PackageSetDocument.Source source, String table)
            throws Exception {
        if (source.manifest == null) {
            addRow(
                    rows,
                    new PackageSetRows.Record(
                            source.index,
                            table,
                            0,
                            new JSONObject()
                                    .put(
                                            "name",
                                            "Manifest declarations unavailable for " + source.name)
                                    .put("errors", source.errors)
                                    .put("available", false)));
            return;
        }
        PackageManifest manifest = source.manifest;
        if (table.equals("manifest-fields")) {
            int i = 0;
            for (PackageManifest.Scalar field : manifest.fields.values())
                addRow(rows, new PackageSetRows.Record(source.index, table, i++, field.json()));
        } else if (table.equals("uses-splits")) {
            for (int i = 0; i < manifest.usesSplits.size(); i++)
                addRow(
                        rows,
                        new PackageSetRows.Record(
                                source.index, table, i, manifest.usesSplits.get(i).json()));
        } else
            for (int i = 0; i < manifest.nodes.length(); i++)
                addRow(
                        rows,
                        new PackageSetRows.Record(
                                source.index, table, i, manifest.nodes.getJSONObject(i)));
    }

    private void addSigning(
            List<PackageSetRows.Record> rows, PackageSetDocument.Source source, String table)
            throws Exception {
        if (source.signing == null) {
            addRow(
                    rows,
                    new PackageSetRows.Record(
                            source.index,
                            table,
                            0,
                            new JSONObject()
                                    .put("name", "Signing information unavailable for " + source.name)
                                    .put("errors", source.errors)
                                    .put("available", false)));
            return;
        }
        JSONArray values = source.signing.table(table.substring("signing-".length()));
        for (int i = 0; i < values.length(); i++)
            addRow(
                    rows,
                    new PackageSetRows.Record(source.index, table, i, values.getJSONObject(i)));
    }

    private PackageSetDocument.Source source(int index) throws IOException {
        if (index < 0 || index >= document.sources.size())
            throw new IOException("Package source index outside captured set");
        return document.sources.get(index);
    }

    private EvidenceRows inputRows(int index, boolean manifest, BooleanSupplier stop)
            throws Exception {
        PackageSetDocument.Source source = source(index);
        if (source.apk == null) throw new IOException("APK snapshot unavailable for this source");
        if (manifest && source.xml == null)
            throw new IOException("No CRC-verified manifest snapshot for this source");
        File file = manifest ? source.xml.file : source.apk.file;
        long size = manifest ? source.xml.size : source.apk.size;
        String sha = manifest ? source.xml.sha256 : source.apk.sha256;
        checkFile(file, size, sha, stop);
        activeBytes = file;
        activeName = source.name + (manifest ? "!/AndroidManifest.xml" : "");
        JSONObject parent =
                new JSONObject()
                        .put("sourceIndex", index)
                        .put("name", source.name)
                        .put("sizeBytes", source.apk.size)
                        .put("sha256", source.apk.sha256)
                        .put("stableDuringAcquisition", source.identityStable)
                        .put(
                                "snapshotAvailableByPathAndLengthOnly",
                                source.apk.file.isFile()
                                        && source.apk.file.length() == source.apk.size);
        JSONObject fields =
                new JSONObject()
                        .put("name", activeName)
                        .put("sourceIndex", index)
                        .put("sizeBytes", size)
                        .put("sha256", sha)
                        .put("parentApk", parent)
                        .put(
                                "coordinateDomain",
                                manifest
                                        ? "Exact expanded AndroidManifest.xml bytes"
                                        : "Exact captured whole APK bytes");
        String title = activeName;
        return new EvidenceRows(manifest ? "package-manifest-input" : "package-apk-input", title) {
            int size() {
                return 1;
            }

            Row row(int row, BooleanSupplier cancelled) throws Exception {
                EvidenceRows.check(cancelled);
                if (row != 0) throw new IOException("Input record outside scope");
                return new Row(
                        0, title, "SHA-256 " + sha, 0L, size, new JSONObject(fields.toString()));
            }

            JSONObject metadata() throws Exception {
                return new JSONObject()
                        .put("sourceIndex", index)
                        .put("sourceSha256", sha)
                        .put("sourceSizeBytes", size);
            }
        };
    }

    @Override
    List<Link> roots(String request, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        List<Link> links = new ArrayList<>();
        for (String table : PackageSetGraph.TABLES)
            links.add(Link.view("Package set • " + table, query(table, -1)));
        links.add(Link.view("Captured APK sources / failures", query("sources", -1)));
        links.add(
                Link.view(
                        "All collected package-set information / complete export", query("all", -1)));
        return links;
    }

    @Override
    List<Link> related(
            String request, EvidenceRows evidence, EvidenceRows.Row row, BooleanSupplier stop)
            throws Exception {
        EvidenceRows.check(stop);
        List<Link> links = new ArrayList<>();
        if (!(evidence instanceof PackageSetRows)) {
            JSONObject fields = row.json();
            int source = fields.getInt("sourceIndex");
            PackageSetDocument.Source input = source(source);
            if (evidence.kind.equals("package-manifest-input"))
                checkFile(input.xml.file, input.xml.size, input.xml.sha256, stop);
            else input.apk.checkIdentity(stop);
            return sourceLinks(source);
        }
        PackageSetRows.Record record = ((PackageSetRows) evidence).record(row.index);
        JSONObject fields = record.value;
        if (record.source >= 0) links.addAll(sourceLinks(record.source));
        for (String key :
                new String[] {"sourceIndices", "baseSourceIndices", "targetSourceIndices"}) {
            JSONArray indices = fields.optJSONArray(key);
            if (indices != null)
                for (int i = 0; i < indices.length(); i++)
                    links.add(
                            Link.jump(
                                    "Inspect captured APK source " + indices.getInt(i),
                                    query("sources", -1),
                                    indices.getInt(i)));
        }
        if (fields.has("referenceSourceIndex"))
            links.add(
                    Link.jump(
                            "Inspect reference APK source " + fields.getInt("referenceSourceIndex"),
                            query("sources", -1),
                            fields.getInt("referenceSourceIndex")));
        if (record.source >= 0 && record.table.startsWith("signing-")) {
            for (String key :
                    new String[] {
                        "certificateIndices",
                        "verifiedSignerCertificateIndices",
                        "certificateLineageIndices"
                    }) {
                JSONArray indices = fields.optJSONArray(key);
                if (indices != null)
                    for (int i = 0; i < indices.length(); i++)
                        links.add(
                                Link.jump(
                                        "Inspect original certificate " + indices.getInt(i),
                                        query("signing-certificates", record.source),
                                        indices.getInt(i)));
            }
            if (record.table.equals("signing-certificates")) {
                links.add(
                        Link.blob(
                                "Export exact certificate DER",
                                blob(record.source, "der", record.index),
                                "certificate.der"));
                if (!fields.getJSONObject("publicKey").isNull("spkiBase64"))
                    links.add(
                            Link.blob(
                                    "Export exact SubjectPublicKeyInfo DER",
                                    blob(record.source, "spki", record.index),
                                    "public-key.der"));
            }
        }
        return links;
    }

    /** Only one known literal declaration can seed a community-ID lookup. */
    Integer knowledgeSource(EvidenceRows rows, EvidenceRows.Row row) throws Exception {
        int index;
        if (rows instanceof PackageSetRows)
            index = ((PackageSetRows) rows).record(row.index).source;
        else if (rows.kind.equals("package-manifest-input")
                || rows.kind.equals("package-apk-input")) index = row.json().getInt("sourceIndex");
        else return null;
        if (index < 0) return null;
        PackageSetDocument.Source input = source(index);
        if (input.apk == null
                || input.xml == null
                || input.manifest == null
                || !input.identityStable) return null;
        String id = input.manifest.packageName();
        return id == null || id.isEmpty() || id.length() > 1024 ? null : index;
    }

    PackageKnowledgeSubject captureKnowledgeSubject(int index, File directory, BooleanSupplier stop)
            throws Exception {
        ensure(stop);
        PackageSetDocument.Source input = source(index);
        if (input.apk == null
                || input.xml == null
                || input.manifest == null
                || !input.identityStable
                || input.manifest.packageName() == null
                || input.manifest.packageName().isEmpty())
            throw new IOException(
                    "No single captured literal APK package declaration is available");
        input.apk.checkIdentity(stop);
        checkFile(input.xml.file, input.xml.size, input.xml.sha256, stop);
        JSONObject context =
                new JSONObject()
                        .put("schema", PackageKnowledgeSubject.SCHEMA)
                        .put("schemaVersion", 1)
                        .put("kind", "apk-literal-package")
                        .put("packageId", input.manifest.packageName())
                        .put("capturedAt", java.time.Instant.now().toString())
                        .put("capturedApk", input.identity())
                        .put("manifestEvidence", new JSONObject(input.manifest.source.toString()))
                        .put("packageDeclaration", input.manifest.fields.get("package").json())
                        .put("meaning", PackageKnowledgeSubject.MEANING);
        PackageKnowledgeSubject captured =
                PackageKnowledgeSubject.capture(directory, context, stop);
        boolean success = false;
        try {
            input.apk.checkIdentity(stop);
            checkFile(input.xml.file, input.xml.size, input.xml.sha256, stop);
            success = true;
            return captured;
        } finally {
            if (!success) captured.close();
        }
    }

    private List<Link> sourceLinks(int index) throws Exception {
        List<Link> links = new ArrayList<>();
        PackageSetDocument.Source source = source(index);
        if (source.apk != null)
            links.add(
                    Link.view(
                            "Exact acquired APK bytes / hex / ranges", query("apk-input", index)));
        if (source.xml != null)
            links.add(
                    Link.view(
                            "Exact expanded manifest bytes / hex / ranges",
                            query("manifest-input", index)));
        links.add(
                Link.view(
                        "Original package / split / version / SDK fields",
                        query("manifest-fields", index)));
        links.add(
                Link.view(
                        "Original relevant manifest nodes / every attribute",
                        query("manifest-elements", index)));
        links.add(Link.view("Every uses-split declaration", query("uses-splits", index)));
        for (String table : ApkSigningEvidence.TABLES)
            links.add(Link.view("This APK signing • " + table, query("signing-" + table, index)));
        return links;
    }

    private static String blob(int source, String kind, int row) throws Exception {
        return new JSONObject().put("source", source).put("blob", kind).put("row", row).toString();
    }

    @Override
    byte[] binaryValue(String request, BooleanSupplier stop) throws Exception {
        ensure(stop);
        JSONObject query = new JSONObject(request);
        PackageSetDocument.Source source = source(query.getInt("source"));
        if (source.signing == null) throw new IOException("Signing record unavailable");
        JSONObject certificate =
                source.signing.table("certificates").getJSONObject(query.getInt("row"));
        String kind = query.getString("blob"), value;
        if (kind.equals("der")) value = certificate.getString("derBase64");
        else if (kind.equals("spki"))
            value = certificate.getJSONObject("publicKey").getString("spkiBase64");
        else throw new IOException("Unknown package signing blob");
        byte[] bytes = Base64.getDecoder().decode(value);
        EvidenceRows.check(stop);
        return bytes;
    }

    private static void checkFile(File file, long size, String sha, BooleanSupplier stop)
            throws Exception {
        if (!file.isFile() || file.length() != size)
            throw new IOException("Captured source expired or changed length");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (InputStream input = new FileInputStream(file)) {
            byte[] block = new byte[65536];
            int amount;
            while ((amount = input.read(block)) >= 0) {
                EvidenceRows.check(stop);
                if (amount > size - count)
                    throw new IOException("Captured source grew during identity check");
                digest.update(block, 0, amount);
                count += amount;
            }
        }
        if (count != size || file.length() != size || !Inspector.toHex(digest.digest()).equals(sha))
            throw new IOException("Captured source SHA-256 changed");
    }

    @Override
    JSONObject provenance() throws Exception {
        JSONArray inputs = new JSONArray();
        for (PackageSetDocument.Source source : document.sources) inputs.put(source.identity());
        return new JSONObject()
                .put("schema", "org.diemobile.package-set")
                .put("schemaVersion", 1)
                .put("capturedAt", capturedAt)
                .put(
                        "observedInventoryPackageName",
                        inventoryPackage == null ? JSONObject.NULL : inventoryPackage)
                .put("inputs", inputs)
                .put(
                        "inputIndex",
                        new JSONObject().put("sizeBytes", indexSize).put("sha256", indexSha))
                .put("limits", limits())
                .put(
                        "manifestEngine",
                        "Google binary-resources 32.5.1 with pinned wire adapter; existing"
                                + " descriptor worker")
                .put(
                        "signingEngine",
                        "AOSP apksig "
                                + ApkSigningReport.VERSION
                                + "; in-process; no detached signatures supplied in this set scope")
                .put("declarationReferenceSha256", PackageManifest.REFERENCE_SHA)
                .put("dependencyReferenceSha256", PackageSetGraph.REFERENCE_SHA)
                .put(
                        "referencePlatform",
                        "AOSP android-15.0.0_r1; declaration knowledge, not platform execution")
                .put("graphAvailable", graph != null)
                .put("graphError", graphError)
                .put(
                        "scope",
                        "Sequentially acquired explicit inputs. No APK installation/execution,"
                                + " missing-split discovery, device targeting, framework/resource"
                                + " resolution, runtime linkage or trusted-publisher decision.");
    }

    private static JSONObject limits() throws Exception {
        return new JSONObject()
                .put("apkInputs", PackageSetDocument.MAX_INPUTS)
                .put("apkBytesEach", PackageSetDocument.MAX_APK)
                .put("apkDeclaredBytesTotal", PackageSetDocument.MAX_APK_TOTAL)
                .put("manifestBytesEach", PackageSetDocument.MAX_MANIFEST)
                .put("manifestDeclaredBytesTotal", PackageSetDocument.MAX_MANIFEST_TOTAL)
                .put("manifestAttributeIdAssociations", PackageManifest.MAX_ID_ASSOCIATIONS)
                .put("collectedModelCharacters", PackageSetDocument.MAX_MODEL_CHARACTERS)
                .put("relationshipRows", PackageSetGraph.MAX_ROWS)
                .put("relationshipAssociationReferences", PackageSetGraph.MAX_REFERENCES)
                .put("relationshipCharacters", PackageSetGraph.MAX_CHARACTERS)
                .put("acquisitionCooperativeSeconds", 90)
                .put("relationshipCooperativeSeconds", 30);
    }

    @Override
    String retentionState() throws Exception {
        return document == null
                ? null
                : new JSONObject().put("sessionDirectory", session.getAbsolutePath()).toString();
    }

    @Override
    File byteSource() {
        return activeBytes == null ? root : activeBytes;
    }

    @Override
    String sourceName() {
        return activeName == null ? name : activeName;
    }

    @Override
    String help(String request) {
        return "Captured base/split declarations • exact package/version/split candidates,"
                + " configuration targets, every uses-split node, declared split types and"
                + " verified certificate comparisons. Failed/ambiguous scopes remain visible."
                + " Root views expose sources and full tables; source actions reach original"
                + " fields, signing data and independently identified APK/manifest bytes. These"
                + " observations do not establish installability, trust or runtime loading.";
    }

    private void cleanupSession() {
        if (session == null) return;
        File[] files = session.listFiles();
        if (files != null)
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("analysis-input-")
                        || name.startsWith("archive-member-")
                        || name.startsWith("source-state-")
                        || name.startsWith("input-index-")
                        || name.equals("source-state.json")
                        || name.equals("public-input-index.json")) file.delete();
            }
        session.delete();
    }

    @Override
    public void close() throws Exception {
        close(false);
    }

    @Override
    void close(boolean retainInputs) throws Exception {
        try {
            runner.close();
        } finally {
            if (retainInputs) {
                if (document != null)
                    for (PackageSetDocument.Source source : document.sources)
                        if (source.xml != null) source.xml.file.delete();
            } else {
                if (document != null) document.close();
                cleanupSession();
            }
        }
    }
}

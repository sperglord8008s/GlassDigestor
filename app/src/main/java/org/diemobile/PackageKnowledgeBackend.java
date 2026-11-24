// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Owned exact community catalog with navigable claims; it never changes installed packages. */
final class PackageKnowledgeBackend extends InvestigationBackend {
    interface Source {
        InputStream open() throws Exception;
    }

    private final Source source;
    private final File directory;
    private final String initialPackage, retained, expectedSha;
    private InputSnapshot input;
    private PackageKnowledge knowledge;
    private final File subjectDirectory;
    private final String incomingSubject;
    private PackageKnowledgeSubject subject;

    PackageKnowledgeBackend(Source source, File directory, String initialPackage, String retained) {
        this(source, directory, initialPackage, retained, PackageKnowledge.PINNED_SHA256);
    }

    PackageKnowledgeBackend(
            Source source,
            File directory,
            String initialPackage,
            String retained,
            String expectedSha) {
        this(source, directory, initialPackage, retained, expectedSha, null, null);
    }

    PackageKnowledgeBackend(
            Source source,
            File directory,
            String initialPackage,
            String retained,
            File subjectDirectory,
            String subjectDescriptor) {
        this(
                source,
                directory,
                initialPackage,
                retained,
                PackageKnowledge.PINNED_SHA256,
                subjectDirectory,
                subjectDescriptor);
    }

    PackageKnowledgeBackend(
            Source source,
            File directory,
            String initialPackage,
            String retained,
            String expectedSha,
            File subjectDirectory,
            String subjectDescriptor) {
        this.source = source;
        this.directory = directory;
        this.initialPackage = initialPackage;
        this.retained = retained;
        this.expectedSha = expectedSha;
        this.subjectDirectory = subjectDirectory;
        incomingSubject = subjectDescriptor;
    }

    private void acquire(BooleanSupplier stop) throws Exception {
        if (knowledge != null) return;
        InputSnapshot candidate = null;
        try {
            String subjectDescriptor = incomingSubject;
            if (retained == null) {
                try (InputStream in = source.open()) {
                    candidate =
                            InputSnapshot.acquire(in, directory, PackageKnowledge.MAX_BYTES, stop);
                }
            } else {
                if (retained.length() > 16384)
                    throw new IOException("Package knowledge state exceeds its budget");
                JSONObject saved = new JSONObject(retained);
                if (!"org.diemobile.package-knowledge-state".equals(string(saved, "schema"))
                        || !(saved.get("schemaVersion") instanceof Integer)
                        || saved.getInt("schemaVersion") != 1)
                    throw new IOException("Unsupported package knowledge state");
                if (saved.has("subject")) subjectDescriptor = string(saved, "subject");
                Object size = saved.get("sizeBytes");
                if (!(size instanceof Integer) && !(size instanceof Long))
                    throw new IOException("Knowledge state size is not an exact integer");
                candidate =
                        InputSnapshot.restore(
                                new File(string(saved, "path")),
                                directory,
                                ((Number) size).longValue(),
                                string(saved, "sha256"),
                                stop);
            }
            if (subjectDescriptor != null) {
                if (subjectDirectory == null || initialPackage == null)
                    throw new IOException(
                            "Captured APK association has no explicit package ID/directory");
                subject =
                        PackageKnowledgeSubject.restore(
                                subjectDirectory, subjectDescriptor, initialPackage, stop);
            }
            PackageKnowledge parsed;
            try (InputStream in = new FileInputStream(candidate.file)) {
                parsed = PackageKnowledge.read(in, stop);
            }
            if (!candidate.sha256.equals(parsed.sha256) || !parsed.sha256.equals(expectedSha))
                throw new IOException(
                        "Package knowledge bytes differ from their captured/pinned source"
                                + " identity");
            candidate.checkIdentity(stop);
            input = candidate;
            candidate = null;
            knowledge = parsed;
        } finally {
            if (candidate != null && retained == null) candidate.close();
        }
    }

    static String query(
            String view, String id, String category, String removal, boolean diagnostics)
            throws Exception {
        if (id == null || id.length() > 1024)
            throw new IOException("Package ID exceeds 1024 UTF-16 units");
        return new JSONObject()
                .put("view", view)
                .put("packageId", id)
                .put("list", category)
                .put("removal", removal)
                .put("diagnostics", diagnostics)
                .toString();
    }

    private JSONObject request(String encoded) throws Exception {
        if (encoded == null || encoded.length() > 16384)
            throw new IOException("Package knowledge request is absent or exceeds its budget");
        JSONObject value = new JSONObject(encoded);
        if (string(value, "packageId").length() > 1024)
            throw new IOException("Package ID exceeds its budget");
        return value;
    }

    @Override
    String initialRequest(BooleanSupplier stop) throws Exception {
        return query(
                initialPackage == null ? "catalog" : "card",
                initialPackage == null ? "" : initialPackage,
                "",
                "",
                false);
    }

    @Override
    EvidenceRows open(String encoded, BooleanSupplier stop) throws Exception {
        acquire(stop);
        JSONObject value = request(encoded);
        String id = string(value, "packageId");
        switch (string(value, "view")) {
            case "catalog":
                Object diagnostic = value.get("diagnostics");
                if (!(diagnostic instanceof Boolean))
                    throw new IOException("Diagnostic filter is not boolean");
                return knowledge.catalog(
                        string(value, "list"), string(value, "removal"), (Boolean) diagnostic);
            case "card":
                return knowledge.card(id, recordIndex(value));
            case "relationships":
                return knowledge.relationships(id);
            default:
                throw new IOException("Unknown package knowledge view");
        }
    }

    @Override
    List<Link> roots(String encoded, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        String id = string(request(encoded), "packageId");
        List<Link> links = new ArrayList<>();
        if (subject != null)
            links.add(
                    Link.text(
                            "Captured APK declaration / association details and limits",
                            query("subject", subject.packageId, "", "", false),
                            "captured-apk-association.json"));
        if (!id.isEmpty()) {
            links.add(
                    Link.view(
                            "What does this exact package ID do? / original community fields",
                            query("card", id, "", "", false)));
            links.add(
                    Link.view(
                            "Declared dependencies / needed by / reverse references",
                            query("relationships", id, "", "", false)));
        }
        links.add(Link.view("Every community package record", query("catalog", "", "", "", false)));
        for (String list : new String[] {"Aosp", "Oem", "Google", "Carrier", "Misc"})
            links.add(Link.view("Upstream list: " + list, query("catalog", "", list, "", false)));
        for (String category : new String[] {"Recommended", "Advanced", "Expert", "Unsafe"})
            links.add(
                    Link.view(
                            "Community removal category: " + category + " / not a device verdict",
                            query("catalog", "", "", category, false)));
        links.add(
                Link.view(
                        "Coverage diagnostics / unresolved relationships",
                        query("catalog", "", "", "", true)));
        links.add(
                Link.text(
                        "Full source identity / interpretation / licensing",
                        query("source", "", "", "", false),
                        "package-knowledge-source.json"));
        links.add(
                Link.blob(
                        "Export exact original UAD-NG catalog bytes",
                        "catalog-bytes",
                        "uad-ng-catalog.json"));
        return links;
    }

    @Override
    List<Link> related(
            String encoded, EvidenceRows rows, EvidenceRows.Row row, BooleanSupplier stop)
            throws Exception {
        EvidenceRows.check(stop);
        JSONObject fields = row.json();
        List<Link> links = new ArrayList<>();
        String id = fields.optString("packageId", string(request(encoded), "packageId"));
        int selectedIndex =
                fields.has("sourceRecordIndex") ? fields.getInt("sourceRecordIndex") : -1;
        links.add(
                Link.view(
                        "What does "
                                + PackageKnowledge.preview(id, 100)
                                + " do? / community record",
                        query("card", id, "", "", false)));
        links.add(
                Link.view(
                        "All declared and reverse-indexed relationships",
                        query("relationships", id, "", "", false)));
        if (knowledge.byId.containsKey(id))
            links.add(
                    Link.text(
                            "Full original record / source identity",
                            new JSONObject(query("record", id, "", "", false))
                                    .put("recordIndex", selectedIndex)
                                    .toString(),
                            "package-knowledge-record.json"));
        if (fields.has("sourceField")) {
            String request =
                    new JSONObject(query("field", id, "", "", false))
                            .put("field", fields.getString("sourceField"))
                            .put("recordIndex", selectedIndex)
                            .toString();
            links.add(
                    Link.text(
                            "Read complete original field value",
                            request,
                            "package-knowledge-value.json"));
        }
        Object target = fields.opt("targetPackageId");
        if (target instanceof String && ((String) target).length() <= 1024)
            links.add(
                    Link.view(
                            "Follow exact referenced ID: "
                                    + PackageKnowledge.preview((String) target, 100),
                            query("card", (String) target, "", "", false)));
        return links;
    }

    @Override
    JSONObject provenance() throws Exception {
        JSONObject result = new JSONObject(knowledge.provenance().toString());
        if (subject != null)
            result.put("capturedApkAssociation", subject.json())
                    .put("capturedAssociationSizeBytes", subject.input.size)
                    .put("capturedAssociationSha256", subject.input.sha256)
                    .put("associationMeaning", PackageKnowledgeSubject.MEANING);
        return result;
    }

    @Override
    File byteSource() {
        return input == null ? new File(directory, "unacquired") : input.file;
    }

    @Override
    String statusSummary() {
        return subject == null
                ? ""
                : "Captured APK declared "
                        + subject.packageId
                        + " · community association uses that exact ID; linked IDs remain separate"
                        + " claims";
    }

    @Override
    String sourceName() {
        return "UAD-NG offline community package knowledge";
    }

    @Override
    String help(String request) {
        return (subject == null ? "" : PackageKnowledgeSubject.MEANING + " ")
                + PackageKnowledge.MEANING
                + " Practical descriptions are the original community text, with every"
                + " original/unknown field available separately. Removal categories are attributed"
                + " source values. Dependencies and neededBy preserve directions, duplicate"
                + " occurrences, absent target records and missing reciprocity; they are not"
                + " measured runtime calls. Source coordinates use UTF-16 units, not physical file"
                + " offsets. Use package, split, signer, component and permission investigations"
                + " for observed static information. This viewer provides no disabling, uninstalling,"
                + " shell execution, automatic URL fetch or network operation.";
    }

    @Override
    void writeText(String encoded, Writer output, BooleanSupplier stop) throws Exception {
        acquire(stop);
        JSONObject value = request(encoded);
        String view = string(value, "view"), id = string(value, "packageId");
        if (view.equals("subject")) {
            if (subject == null)
                throw new IOException("No captured APK association accompanied this catalog");
            subject.write(output, stop);
            return;
        }
        if (view.equals("source")) {
            EvidenceJson.write(output, provenance(), stop);
            return;
        }
        PackageKnowledge.Entry entry = knowledge.record(id, recordIndex(value));
        output.write("{\"provenance\":");
        EvidenceJson.write(output, provenance(), stop);
        output.write(",\"packageId\":");
        EvidenceJson.write(output, id, stop);
        if (view.equals("record")) {
            output.write(",\"originalRecord\":");
            PackageKnowledge.raw(output, entry.slice.raw, stop);
        } else if (view.equals("field")) {
            String field = string(value, "field");
            if (entry.value == null || !entry.value.has(field))
                throw new IOException("Original field unavailable");
            output.write(",\"field\":");
            EvidenceJson.write(output, field, stop);
            output.write(",\"originalValue\":");
            EvidenceJson.write(output, entry.value.get(field), stop);
        } else throw new IOException("Unknown package knowledge text renderer");
        output.write('}');
    }

    @Override
    byte[] binaryValue(String request, BooleanSupplier stop) throws Exception {
        acquire(stop);
        EvidenceRows.check(stop);
        if (!request.equals("catalog-bytes"))
            throw new IOException("Unknown package knowledge byte export");
        return knowledge.bytes.clone();
    }

    @Override
    String retentionState() throws Exception {
        if (input == null) return null;
        JSONObject state =
                new JSONObject()
                        .put("schema", "org.diemobile.package-knowledge-state")
                        .put("schemaVersion", 1)
                        .put("path", input.file.getAbsolutePath())
                        .put("sizeBytes", input.size)
                        .put("sha256", input.sha256);
        if (subject != null) state.put("subject", subject.descriptor());
        return state.toString();
    }

    @Override
    public void close() {
        close(false);
    }

    @Override
    void close(boolean retain) {
        if (input != null && !retain) input.close();
        if (!retain) {
            if (subject != null) subject.close();
            else if (retained == null && incomingSubject != null && subjectDirectory != null)
                PackageKnowledgeSubject.discard(subjectDirectory, incomingSubject);
        }
    }

    private static int recordIndex(JSONObject value) throws Exception {
        if (!value.has("recordIndex")) return -1;
        Object result = value.get("recordIndex");
        if (!(result instanceof Integer) && !(result instanceof Long))
            throw new IOException("Original record index is not an exact integer");
        long index = ((Number) result).longValue();
        if (index < -1 || index >= PackageKnowledge.MAX_PACKAGES)
            throw new IOException("Original record index exceeds its budget");
        return (int) index;
    }

    private static String string(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IOException("Knowledge " + key + " is not text");
        return (String) value;
    }
}

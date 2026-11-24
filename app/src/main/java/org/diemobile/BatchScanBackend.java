// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Searchable captured batch outcomes and original native trees, with per-input byte ownership. */
final class BatchScanBackend extends InvestigationBackend {
    private final File root, snapshots;
    private final String id;
    private BatchScanSession session;
    private InputSnapshot index;
    private File active;
    private String activeName = "Batch journal";
    private int selectedSource = -1;
    private int comparedLeft = -1, comparedRight = -1;
    private ScanComparison comparison;

    BatchScanBackend(File root, File snapshots, String id) {
        this.root = root;
        this.snapshots = snapshots;
        this.id = id;
    }

    private static String request(String table, int source) throws JSONException {
        return new JSONObject().put("table", table).put("source", source).toString();
    }

    private void ensure(BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (session != null) return;
        BatchScanSession loaded = BatchScanSession.open(root, id, stop);
        InputSnapshot captured =
                InputSnapshot.acquire(
                        new ByteArrayInputStream(loaded.journalBytes()),
                        snapshots,
                        2L * 1024 * 1024,
                        stop);
        session = loaded;
        index = captured;
        active = index.file;
    }

    @Override
    String initialRequest(BooleanSupplier stop) throws Exception {
        ensure(stop);
        return request("inputs", -1);
    }

    @Override
    EvidenceRows open(String value, BooleanSupplier stop) throws Exception {
        ensure(stop);
        JSONObject query = new JSONObject(value);
        String table = query.getString("table");
        int source = query.optInt("source", -1);
        selectedSource = -1;
        active = index.file;
        activeName = "Captured batch journal";
        List<JSONObject> records = new ArrayList<>();
        List<String> names = new ArrayList<>(), descriptions = new ArrayList<>();
        if (table.equals("comparison"))
            return comparisonRows(source, query.getInt("other"), query.getString("scope"), stop);
        comparedLeft = comparedRight = -1;
        Long range = null;
        if (table.equals("inputs")) {
            for (BatchScanSession.Entry entry : session.entries) {
                records.add(entry.json());
                names.add("#" + entry.index + " " + entry.name);
                descriptions.add(entry.status + " • " + entry.stage + " • " + entry.error);
            }
        } else {
            BatchScanSession.Entry entry = session.entry(source);
            if (table.equals("input")) {
                active = session.input(entry, stop);
                activeName = entry.name;
                selectedSource = entry.index;
                records.add(
                        entry.json().put("coordinateDomain", "Exact captured whole-file bytes"));
                names.add(entry.name);
                descriptions.add("Owned input • SHA-256 " + entry.sha256);
                range = entry.size;
            } else if (table.equals("detections")) {
                JSONObject report = session.report(entry, stop);
                // Reading the report does not require its input to remain available. Byte actions
                // do.
                ScanEvidence evidence = ScanEvidence.parse(report.getJSONObject("scan").toString());
                JSONArray rows = evidence.toJson().optJSONArray("rows");
                for (ScanEvidence.Row row : evidence.rows) {
                    EvidenceRows.check(stop);
                    records.add(
                            new JSONObject()
                                    .put("sourceIndex", source)
                                    .put("inputSha256", entry.sha256)
                                    .put("nativeRow", rows.getJSONObject(row.index))
                                    .put("nativeIndex", row.index)
                                    .put("depth", row.depth));
                    names.add(row.text);
                    descriptions.add(
                            "parent=" + row.parent + " • " + row.category + " • " + row.signature);
                }
                if (!evidence.ok) {
                    records.add(
                            new JSONObject()
                                    .put("sourceIndex", source)
                                    .put("error", evidence.error));
                    names.add("Native scan failed");
                    descriptions.add(evidence.error);
                }
            } else throw new IOException("Unknown batch evidence scope");
        }
        JSONObject metadata =
                session.metadata()
                        .put("table", table)
                        .put("selectedSourceIndex", source < 0 ? JSONObject.NULL : source);
        Long inputRange = range;
        return new EvidenceRows("desktop-batch-" + table, "Desktop batch • " + table) {
            @Override
            int size() {
                return records.size();
            }

            @Override
            JSONObject metadata() {
                return metadata;
            }

            @Override
            Row row(int position, BooleanSupplier cancelled) throws Exception {
                EvidenceRows.check(cancelled);
                if (position < 0 || position >= records.size())
                    throw new IOException("Batch evidence row outside scope");
                return new Row(
                        position,
                        names.get(position),
                        descriptions.get(position),
                        inputRange == null ? null : 0L,
                        inputRange,
                        new JSONObject(records.get(position).toString()));
            }
        };
    }

    @Override
    List<Link> roots(String value, BooleanSupplier stop) throws Exception {
        ensure(stop);
        List<Link> links = new ArrayList<>();
        links.add(Link.view("Every planned input / outcome", request("inputs", -1)));
        links.add(
                Link.text(
                        "Preview / export complete batch JSON",
                        "complete-batch",
                        "die-desktop-batch.json"));
        JSONObject selected =
                value == null || value.isEmpty() ? new JSONObject() : new JSONObject(value);
        if (selected.optString("table").equals("comparison")) {
            int left = selected.getInt("source"), right = selected.getInt("other");
            links.add(
                    Link.view(
                            "Comparison context / settings / rule identities",
                            comparisonRequest(left, right, "summary")));
            for (String table : ScanComparison.TABLES)
                links.add(
                        Link.view(
                                "Native comparison • " + table,
                                comparisonRequest(left, right, table)));
            links.add(
                    Link.text(
                            "Preview / export complete native-tree comparison JSON",
                            comparisonRequest(left, right, "complete"),
                            "die-scan-comparison.json"));
        }
        return links;
    }

    @Override
    List<Link> related(String value, EvidenceRows rows, EvidenceRows.Row row, BooleanSupplier stop)
            throws Exception {
        EvidenceRows.check(stop);
        JSONObject record = row.json();
        if (rows.kind.startsWith("desktop-scan-comparison-"))
            return comparisonLinks(new JSONObject(value), record);
        int source = record.getInt("sourceIndex");
        BatchScanSession.Entry entry = session.entry(source);
        List<Link> links = new ArrayList<>();
        if (!entry.snapshotName.isEmpty())
            links.add(
                    Link.view("Exact owned input / hex / range export", request("input", source)));
        if (!entry.reportName.isEmpty()) {
            links.add(Link.view("Original native detection tree", request("detections", source)));
            links.add(
                    Link.text(
                            "Preview / export complete original scan report",
                            "report:" + source,
                            "die-batch-input-" + source + ".json"));
            for (BatchScanSession.Entry other : session.entries)
                if (other.index != source && !other.reportName.isEmpty())
                    links.add(
                            Link.view(
                                    "Compare native report with #" + other.index + " " + other.name,
                                    comparisonRequest(source, other.index, "summary")));
        }
        return links;
    }

    @Override
    void writeText(String value, Writer output, BooleanSupplier stop) throws Exception {
        ensure(stop);
        if (value.startsWith("{")) {
            JSONObject query = new JSONObject(value);
            if (!query.getString("table").equals("comparison")
                    || !query.getString("scope").equals("complete"))
                throw new IOException("Unknown comparison export scope");
            compare(query.getInt("source"), query.getInt("other"), stop).writeReport(output, stop);
        } else if (value.equals("complete-batch")) session.writeReport(output, stop);
        else if (value.startsWith("report:"))
            EvidenceJson.write(
                    output,
                    session.report(session.entry(Integer.parseInt(value.substring(7))), stop),
                    stop);
        else throw new IOException("Unknown batch report scope");
    }

    private static String comparisonRequest(int left, int right, String scope)
            throws JSONException {
        return new JSONObject()
                .put("table", "comparison")
                .put("source", left)
                .put("other", right)
                .put("scope", scope)
                .toString();
    }

    private ScanComparison compare(int left, int right, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (left == right) throw new IOException("Choose two separate captured input ordinals");
        if (comparison != null && comparedLeft == left && comparedRight == right) return comparison;
        BatchScanSession.Entry first = session.entry(left), second = session.entry(right);
        JSONObject context =
                new JSONObject()
                        .put("batchId", session.id)
                        .put("left", first.json())
                        .put("right", second.json());
        ScanComparison captured =
                ScanComparison.compare(
                        session.report(first, stop), session.report(second, stop), context, stop);
        comparison = captured;
        comparedLeft = left;
        comparedRight = right;
        return captured;
    }

    private EvidenceRows comparisonRows(int left, int right, String scope, BooleanSupplier stop)
            throws Exception {
        ScanComparison captured = compare(left, right, stop);
        JSONObject metadata = captured.metadata().put("selectedTable", scope);
        List<ScanComparison.Record> records =
                scope.equals("summary") ? Collections.emptyList() : captured.table(scope);
        return new EvidenceRows(
                "desktop-scan-comparison-" + scope, "Captured native scan comparison • " + scope) {
            @Override
            int size() {
                return scope.equals("summary") ? 1 : records.size();
            }

            @Override
            JSONObject metadata() {
                return metadata;
            }

            @Override
            Row row(int index, BooleanSupplier cancelled) throws Exception {
                EvidenceRows.check(cancelled);
                if (index < 0 || index >= size())
                    throw new IOException("Comparison row outside selected scope");
                JSONObject fields =
                        scope.equals("summary")
                                ? captured.metadata()
                                : captured.record(records.get(index));
                JSONObject nativeRow = fields.optJSONObject("leftNativeRow");
                if (nativeRow == null) nativeRow = fields.optJSONObject("rightNativeRow");
                String name =
                        nativeRow == null
                                ? "Captured native-tree comparison / provenance"
                                : nativeRow.getString("text");
                String description =
                        scope.equals("summary")
                                ? "performed="
                                        + captured.performed
                                        + " • matched="
                                        + captured.matched
                                        + " • left-only="
                                        + captured.leftOnly
                                        + " • right-only="
                                        + captured.rightOnly
                                        + " • uncompared="
                                        + captured.uncompared
                                : fields.getString("kind")
                                        + " • left row="
                                        + fields.opt("leftRowIndex")
                                        + " • right row="
                                        + fields.opt("rightRowIndex");
                return new Row(
                        index,
                        name,
                        description,
                        null,
                        null,
                        fields.put("leftSourceIndex", left).put("rightSourceIndex", right));
            }
        };
    }

    private List<Link> comparisonLinks(JSONObject query, JSONObject record) throws Exception {
        int left = query.getInt("source"), right = query.getInt("other");
        List<Link> links = new ArrayList<>();
        if (record.has("leftRowIndex") && !record.isNull("leftRowIndex"))
            links.add(
                    Link.jump(
                            "Original left native row #" + record.getInt("leftRowIndex"),
                            request("detections", left),
                            record.getInt("leftRowIndex")));
        if (record.has("rightRowIndex") && !record.isNull("rightRowIndex"))
            links.add(
                    Link.jump(
                            "Original right native row #" + record.getInt("rightRowIndex"),
                            request("detections", right),
                            record.getInt("rightRowIndex")));
        links.add(
                Link.text(
                        "Complete original left scan report",
                        "report:" + left,
                        "die-batch-input-" + left + ".json"));
        links.add(
                Link.text(
                        "Complete original right scan report",
                        "report:" + right,
                        "die-batch-input-" + right + ".json"));
        return links;
    }

    @Override
    JSONObject provenance() throws Exception {
        JSONObject result = session.metadata();
        if (selectedSource >= 0) result.put("byteSource", session.entry(selectedSource).json());
        else
            result.put(
                    "byteSource",
                    new JSONObject()
                            .put("name", "Captured batch journal")
                            .put("sizeBytes", index.size)
                            .put("sha256", index.sha256)
                            .put(
                                    "coordinateDomain",
                                    "Journal JSON bytes; aggregate rows have no input-file"
                                            + " offsets"));
        if (comparedLeft >= 0 && comparison != null)
            result.put(
                    "selectedNativeComparison",
                    new JSONObject()
                            .put("leftSourceIndex", comparedLeft)
                            .put("rightSourceIndex", comparedRight)
                            .put("algorithm", "exact-native-tree-paths/v1"));
        return result;
    }

    @Override
    File byteSource() {
        return active;
    }

    @Override
    String sourceName() {
        return activeName;
    }

    @Override
    String help(String request) {
        if (request != null)
            try {
                if (new JSONObject(request).optString("table").equals("comparison"))
                    return "Exact captured native tree comparison: every row field except its"
                               + " numeric parent position, full ancestry and occurrence order"
                               + " among identical siblings participate. Original ordinals and both"
                               + " report hashes remain available. Duplicate parent"
                               + " insertion/reordering can change alignment. Failed scans have"
                               + " unknown coverage; records remain uncompared."
                               + " Left-only/right-only describes returned records, never file"
                               + " absence or behavior. Captured native options/rule/engine"
                               + " differences are separate from preview-only settings. This view"
                               + " does not reread input bytes or infer physical offsets.";
            } catch (JSONException ignored) {
                /* Non-table text requests use ordinary batch guidance. */
            }
        return "Every selected document has an independent outcome. Complete means the native"
                + " request returned; inspect its raw tree, diagnostics, bounds and exact"
                + " options. Duplicate names remain separate ordinal inputs. Detection rows have"
                + " no invented physical offset. Select the owned input view for its bytes. A"
                + " persisted running/planned journal is incomplete; jobs are never silently"
                + " resumed. A match alone does not establish execution, safety or malicious"
                + " behavior.";
    }

    @Override
    public void close() {
        if (index != null) index.close();
    }
}

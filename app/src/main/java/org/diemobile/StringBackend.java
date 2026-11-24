// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Grouping/catalog navigation over a fully scanned immutable string snapshot. */
final class StringBackend extends InvestigationBackend {
    private final File input, directory;
    private final String name;
    private final int minimum;
    private StringDocument document;
    StringBackend(File input, File directory, String name, int minimum) { this.input = input; this.directory = directory; this.name = name; this.minimum = minimum; }
    @Override String initialRequest(BooleanSupplier stop) { return "interesting"; }
    private void acquire(BooleanSupplier stop) throws Exception { if (document == null) document = StringDocument.scan(input,directory,minimum,stop); }
    @Override EvidenceRows open(String request, BooleanSupplier stop) throws Exception {
        if (request.equals("catalog")) return StringClassifier.catalog();
        acquire(stop); return document.rows(request);
    }
    @Override List<Link> roots(String request, BooleanSupplier stop) throws Exception {
        List<Link> values = new ArrayList<>();
        values.add(Link.view("All interesting strings","interesting")); values.add(Link.view("All decoded string candidates","all")); values.add(Link.view("Unclassified strings","unclassified"));
        for (StringClassifier.Group group : StringClassifier.Group.values()) values.add(Link.view(group.label,group.name()));
        values.add(Link.view("Rule catalog / expressions / provenance","catalog")); return values;
    }
    @Override List<Link> related(String request, EvidenceRows source, EvidenceRows.Row row, BooleanSupplier stop) throws Exception {
        List<Link> values = new ArrayList<>(); JSONArray groups = row.json().optJSONArray("groups");
        if (groups != null) for (int i = 0; i < groups.length(); i++) { String group = groups.getString(i); values.add(Link.view("Group: " + StringClassifier.Group.valueOf(group).label,group)); }
        values.add(Link.view("Inspect classification rules","catalog")); return values;
    }
    @Override JSONObject provenance() throws Exception {
        return StringClassifier.provenance().put("input",new JSONObject().put("name",name).put("sizeBytes",document == null ? input.length() : document.inputSize).put("sha256",document == null ? JSONObject.NULL : document.sha256))
            .put("textSnapshot",document == null ? "Catalog only; input has not been scanned" : "Private acquired text; all five encoding passes agree on input SHA-256");
    }
    @Override File byteSource() { return document == null ? input : document.input; }
    @Override String sourceName() { return name; }
    @Override String help(String request) {
        return "Offline string classification • Categories group URLs/endpoints, commands/APIs, secret candidates, vulnerability/exploit and other indicators. Search filters every record in the current category, across all pages; open a string for Why this classification?, exact matched text, offsets and rule details. A match does not prove execution, a valid credential or a vulnerability. Category counts overlap. Captured minimum: " + minimum + " Unicode code points. Decoding coverage and limits remain in the source information and exports.";
    }
    @Override public void close() throws Exception { if (document != null) document.close(); }
}

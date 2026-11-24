// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Directory/tag/value navigation; values have no invented physical locations. */
final class MetadataBackend extends InvestigationBackend {
    private final File input, directory;
    private final String name;
    private MetadataDocument document;
    MetadataBackend(File input, File directory, String name) { this.input = input; this.directory = directory; this.name = name; }
    private static String request(String kind, int directory, int tag) throws JSONException { return new JSONObject().put("kind",kind).put("directory",directory).put("tag",tag).toString(); }
    @Override String initialRequest(BooleanSupplier stop) throws Exception { return request("tags",-1,0); }
    @Override EvidenceRows open(String encoded, BooleanSupplier stop) throws Exception {
        if (document == null) document = MetadataDocument.read(input,directory,name,stop); JSONObject query = new JSONObject(encoded); int selected = query.getInt("directory");
        switch (query.getString("kind")) {
            case "tags": return document.tags(selected);
            case "directories": return document.directories();
            case "errors": return document.errors(selected);
            case "xmp": return document.xmp(selected);
            case "value": return document.value(selected,query.getInt("tag"));
            case "summary": return new EvidenceRows("metadata-summary","Metadata source / scope / engine details") {
                int size() { return 1; }
                Row row(int i, BooleanSupplier cancelled) throws Exception { EvidenceRows.check(cancelled); return new Row(0,name,document.detectedType+" • "+document.parseStatus,0L,document.snapshot.size,document.metadata()); }
                JSONObject metadata() throws Exception { return document.metadata(); }
            };
            default: throw new IOException("Unknown metadata view");
        }
    }
    @Override List<Link> roots(String request, BooleanSupplier stop) throws Exception {
        return Arrays.asList(Link.view("All collected tags",request("tags",-1,0)),Link.view("Directory instances / relationships",request("directories",-1,0)),
            Link.view("All engine diagnostics",request("errors",-1,0)),Link.view("All XMP schemas / properties / qualifiers",request("xmp",-1,0)),Link.view("Input / engine / coverage summary",request("summary",-1,0)));
    }
    @Override List<Link> related(String request, EvidenceRows source, EvidenceRows.Row row, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop); List<Link> links = new ArrayList<>(); JSONObject fields = row.json();
        if (fields.has("propertyIndex") && document.xmpText(fields.getInt("propertyIndex")) != null) links.add(Link.text("View / export complete XMP property text",request("xmp-value",fields.getInt("propertyIndex"),0),"xmp-"+fields.getInt("propertyIndex")+".txt"));
        if (fields.has("directoryIndex") && fields.getInt("directoryIndex") >= 0) {
            int id = fields.getInt("directoryIndex"); links.add(Link.view("Tags in this directory",request("tags",id,0))); links.add(Link.view("Diagnostics in this directory",request("errors",id,0))); links.add(Link.view("XMP nodes in this directory",request("xmp",id,0)));
            if (fields.has("tagType")) {
                int tag = fields.getInt("tagType"); Object value = document.rawValue(id,tag); String selected = request("value",id,tag);
                links.add(Link.view("Complete value / binary chunks / array elements",selected));
                if (MetadataDocument.bytes(value) != null) links.add(Link.blob("Export complete binary value bytes",selected,"metadata-"+id+"-"+Integer.toHexString(tag)+".bin"));
                if (MetadataDocument.text(value) != null) links.add(Link.text("View / export complete decoded text",selected,"metadata-"+id+"-"+Integer.toHexString(tag)+".txt"));
            }
            if (fields.has("parentDirectoryIndex") && !fields.isNull("parentDirectoryIndex")) links.add(Link.view("Parent directory tags",request("tags",fields.getInt("parentDirectoryIndex"),0)));
        }
        return links;
    }
    @Override JSONObject provenance() throws Exception { return document.provenance(); }
    @Override File byteSource() { return document == null ? input : document.snapshot.file; }
    @Override String sourceName() { return name; }
    @Override String help(String request) {
        return "Collected metadata • EXIF/IPTC/ICC/XMP and supported image/media structures. Tag IDs belong to directory instances; raw values remain separate from engine descriptions. Root views expose directories, diagnostics and XMP nodes. Tag search includes previews; complete value views and JSON exports retain full data. Tap here for coverage and limits.";
    }
    @Override byte[] binaryValue(String encoded, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop); JSONObject query = new JSONObject(encoded); byte[] value = MetadataDocument.bytes(document.rawValue(query.getInt("directory"),query.getInt("tag")));
        if (value == null) throw new IOException("Selected metadata value is not binary"); return value;
    }
    @Override void writeText(String encoded, Writer output, BooleanSupplier stop) throws Exception {
        JSONObject query = new JSONObject(encoded); String value = query.getString("kind").equals("xmp-value") ? document.xmpText(query.getInt("directory")) : MetadataDocument.text(document.rawValue(query.getInt("directory"),query.getInt("tag")));
        if (value == null) throw new IOException("Selected metadata value is not textual");
        for (int i = 0; i < value.length(); i += 4096) { EvidenceRows.check(stop); output.write(value,i,Math.min(4096,value.length()-i)); }
    }
    @Override public void close() { if (document != null) document.close(); }
}

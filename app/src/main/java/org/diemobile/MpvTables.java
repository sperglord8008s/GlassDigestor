// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Bounded interactive rows with complete native node data in streamed exports. */
final class MpvTables {
    static final Set<String> CAPABILITIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "decoder-list","demuxer-lavf-list","protocol-list","mpv-version","mpv-configuration","ffmpeg-version","libass-version")));
    private final MpvDocument document;
    MpvTables(MpvDocument document) { this.document = document; }

    EvidenceRows properties(String scope) throws Exception {
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < document.properties.length(); i++) {
            JSONObject property = document.properties.getJSONObject(i);
            boolean available = property.getString("status").equals("available");
            boolean capability = CAPABILITIES.contains(property.getString("name"));
            if (scope.equals("all") || scope.equals("media") && !capability || scope.equals("capabilities") && capability
                || scope.equals("available") && available || scope.equals("unavailable") && !available) selected.add(i);
        }
        if (!Arrays.asList("all","media","capabilities","available","unavailable").contains(scope)) throw new IOException("Unknown MPV property scope");
        return new EvidenceRows("mpv-properties","MPV • "+scope+" • "+document.name) {
            int size() { return selected.size(); }
            Row row(int index, BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);
                int original = selected.get(index);
                JSONObject property = document.properties.getJSONObject(original);
                String name = property.getString("name"), status = property.getString("status");
                JSONObject value = new JSONObject().put("propertyIndex",original).put("nodePath",new JSONArray())
                    .put("status",status).put("errorCode",property.getInt("errorCode"))
                    .put("reference",MpvReference.context(name)).put("fullNativePropertyInJsonExport",true);
                String description;
                if (status.equals("available")) {
                    JSONObject node = property.getJSONObject("node");
                    value.put("nodePreview",preview(node)); description = summary(node);
                } else { value.put("error",property.getString("error")); description = property.getString("error"); }
                String brief = MpvReference.brief(name);
                return new Row(original,name,status+" • "+description+(brief.isEmpty()?"":" • "+brief),null,null,value);
            }
            JSONObject metadata() throws Exception { return origin().put("selectedScope",scope).put("selectedRows",size()); }
            @Override boolean matches(Row row, String query, BooleanSupplier stop) throws Exception { return valueMatches(row,query,stop); }
            @Override void writeSelectedRow(Writer output, Row row, BooleanSupplier stop) throws Exception {
                output.write(row.encoded,0,row.encoded.length()-1);
                output.write(",\"fullNativeProperty\":");
                EvidenceJson.write(output,document.properties.getJSONObject(row.index),stop);
                output.write('}');
            }
        };
    }
    EvidenceRows nodes(int property, JSONArray path) throws Exception {
        JSONObject parent = document.node(property,path);
        String type = parent.getString("type");
        boolean container = type.equals("map") || type.equals("array");
        JSONArray children = container ? parent.getJSONArray("children") : new JSONArray();
        String propertyName = document.properties.getJSONObject(property).getString("name");
        return new EvidenceRows("mpv-nodes","MPV • "+propertyName+" • node "+path) {
            int size() { return container ? children.length() : 1; }
            Row row(int index, BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);
                JSONArray selectedPath = new JSONArray(path.toString());
                JSONObject node = parent, entry = null;
                String label = propertyName;
                if (container) {
                    selectedPath.put(index);
                    entry = children.getJSONObject(index);
                    node = type.equals("map") ? entry.getJSONObject("node") : entry;
                    label = type.equals("map") ? entry.getString("key") : "["+index+"]";
                }
                JSONObject fields = new JSONObject().put("propertyIndex",property).put("propertyName",propertyName)
                    .put("nodePath",selectedPath).put("nodePreview",preview(node))
                    .put("fullNativeNodeInJsonExport",true).put("reference",MpvReference.context(propertyName));
                if (container && type.equals("map")) fields.put("mapKeyPreview",shortText(label,4096))
                    .put("mapKeyUtf16Units",label.length()).put("mapKeyUtf8HexPreview",shortText(entry.getString("keyUtf8Hex"),256));
                return new Row(index,shortText(label,4096),summary(node),null,null,fields);
            }
            JSONObject metadata() throws Exception { return origin().put("property",propertyName).put("parentNodePath",path).put("parentType",type); }
            @Override boolean matches(Row row, String query, BooleanSupplier stop) throws Exception { return valueMatches(row,query,stop); }
            @Override void writeSelectedRow(Writer output, Row row, BooleanSupplier stop) throws Exception {
                JSONObject fields = row.json();
                JSONArray selectedPath = fields.getJSONArray("nodePath");
                output.write(row.encoded,0,row.encoded.length()-1);
                output.write(",\"fullNativeNode\":");
                EvidenceJson.write(output,document.node(property,selectedPath),stop);
                if (container && type.equals("map")) {
                    JSONObject entry = children.getJSONObject(row.index);
                    output.write(",\"fullMapKey\":"); EvidenceJson.quoted(output,entry.getString("key"),stop);
                    output.write(",\"fullMapKeyUtf8Hex\":"); EvidenceJson.quoted(output,entry.getString("keyUtf8Hex"),stop);
                }
                output.write('}');
            }
        };
    }
    EvidenceRows catalog() throws Exception {
        JSONArray names = document.catalog.getJSONArray("children");
        return new EvidenceRows("mpv-catalog","Complete MPV property-name catalog") {
            int size() { return names.length(); }
            Row row(int i, BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);
                String name = names.getJSONObject(i).getString("value");
                int selected = -1;
                for (int p = 0; p < document.properties.length(); p++) if (document.properties.getJSONObject(p).getString("name").equals(name)) selected = p;
                return new Row(i,name,selected >= 0 ? "Included in the audited capture set" : "Name cataloged; getter not invoked by this selected-file probe",
                    null,null,new JSONObject().put("propertyIndex",selected).put("getterInvoked",selected>=0).put("reference",MpvReference.context(name)));
            }
            JSONObject metadata() throws Exception { return origin().put("scope","All property names reported by this exact runtime; cataloging names does not invoke unrelated system, device or clipboard getters"); }
        };
    }
    EvidenceRows records(String scope) throws Exception {
        JSONArray values = scope.equals("diagnostics") ? document.diagnostics : document.options;
        return new EvidenceRows("mpv-"+scope,"MPV • "+scope) {
            int size() { return values.length(); }
            Row row(int i, BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);
                JSONObject fields = new JSONObject(values.getJSONObject(i).toString());
                String name = scope.equals("diagnostics") ? fields.getString("level")+" • "+fields.getString("prefix") : fields.getString("name");
                String description = scope.equals("diagnostics") ? fields.getString("text") : fields.getString("value");
                return new Row(i,name,description,null,null,fields);
            }
            JSONObject metadata() throws Exception { return origin(); }
        };
    }
    EvidenceRows summary() {
        return new EvidenceRows("mpv-source","MPV source / scope / collected information") {
            int size() { return 1; }
            Row row(int i, BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);
                return new Row(0,document.name,document.header.getBoolean("fileProbeComplete") ? "Media stream initialized; read engine and scope limitations" : "UNSUPPORTED / INCOMPLETE media probe: "+document.header.optString("loadError"),
                    0L,document.snapshot.size,document.provenance());
            }
            JSONObject metadata() throws Exception { return origin(); }
        };
    }
    private JSONObject origin() throws Exception { return document.provenance(); }
    private static boolean valueMatches(EvidenceRows.Row row, String query, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (row.name.toLowerCase(Locale.ROOT).contains(query) || row.description.toLowerCase(Locale.ROOT).contains(query)) return true;
        JSONObject fields = row.json();
        return fields.has("nodePreview") && EvidenceRows.containsDecoded(fields.getJSONObject("nodePreview"),query,0,new int[]{0},stop);
    }
    static String shortText(String value, int maximum) { return value.length() <= maximum ? value : value.substring(0,maximum)+"… [complete value available]"; }
    static JSONObject preview(JSONObject node) throws Exception {
        JSONObject result = new JSONObject().put("format",node.getInt("format")).put("type",node.getString("type"));
        if (node.has("children")) result.put("childCount",node.getJSONArray("children").length());
        if (node.has("value")) {
            Object value = node.get("value");
            result.put("value",value instanceof String ? shortText((String)value,4096) : value);
            if (value instanceof String) result.put("valueUtf16Units",((String)value).length()).put("valuePreviewComplete",((String)value).length()<=4096);
        }
        for (String key : Arrays.asList("decimal","ieee754Bits","byteLength")) if (node.has(key)) result.put(key,node.get(key));
        for (String key : Arrays.asList("utf8Hex","dataHex")) if (node.has(key)) result.put(key+"Preview",shortText(node.getString(key),256));
        return result;
    }
    static String summary(JSONObject node) throws Exception {
        String type = node.getString("type");
        if (node.has("children")) return type+" • "+node.getJSONArray("children").length()+" children; open typed value";
        if (node.has("value")) return type+" • "+shortText(String.valueOf(node.get("value")),300);
        return type+" • "+node.optLong("byteLength")+" bytes";
    }
}

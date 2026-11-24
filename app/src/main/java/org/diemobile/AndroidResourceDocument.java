// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Detached bounded worker report. Tables retain wire ordinals and complete values. */
final class AndroidResourceDocument {
    static final List<String> TABLES=Collections.unmodifiableList(Arrays.asList("chunks","pools","strings","spans","packages","types","offsets","entries","maps","xml-events","attributes","resource-map","type-spec-flags","libraries"));
    final JSONObject header,tables;
    private final File source;
    private final String name,sha256;
    AndroidResourceDocument(File source,String name,String sha256,String report,BooleanSupplier stop) throws Exception {
        this.source=source;this.name=name;this.sha256=sha256;
        if(report==null||report.length()>32*1024*1024) throw new IOException("Resource worker returned no report or exceeded its text budget");
        JSONObject value=new JSONObject(report);
        if(!value.optBoolean("ok")) throw new IOException(value.optString("error","Resource worker failed without a complete report"));
        if(!value.getString("schema").equals("org.diemobile.android-resource-evidence")||value.getInt("schemaVersion")!=1
            ||value.getInt("adapterVersion")!=1||!value.getString("sourceJarSha256").equals(AndroidResourceProbe.SOURCE_SHA)
            ||!value.getString("sourcePatchSha256").equals(AndroidResourceProbe.PATCH_SHA)
            ||!value.getString("engineVersion").equals("32.5.1")||value.getLong("fileSizeBytes")!=source.length()) throw new IOException("Resource report source/schema/input mismatch");
        if(value.getBoolean("networkUsed")||value.getBoolean("inspectedCodeExecuted")||!value.getBoolean("wireCoverageComplete")
            ||value.getBoolean("platformAcceptanceVerified")) throw new IOException("Resource report differs from the requested read-only evidence scope");
        tables=value.getJSONObject("tables");JSONObject counts=value.getJSONObject("tableCounts");
        Set<String> keys=new HashSet<>();Iterator<String> iterator=tables.keys();while(iterator.hasNext()) keys.add(iterator.next());
        if(!keys.equals(new HashSet<>(TABLES))) throw new IOException("Resource report has missing/unexpected tables");
        long total=0,stringUnits=0;
        for(String table:TABLES) {
            JSONArray rows=tables.getJSONArray(table);total+=rows.length();
            if(rows.length()!=counts.getInt(table)||rows.length()>1000000||total>3000000) throw new IOException("Resource report count/budget mismatch");
            for(int i=0;i<rows.length();i++) {
                EvidenceRows.check(stop);JSONObject row=rows.getJSONObject(i);
                long at=row.getLong("physicalOffset"),size=row.getLong("sizeBytes");
                if(row.getInt("index")!=i||at<0||size<0||at>source.length()||size>source.length()-at) throw new IOException("Resource evidence range/ordinal outside its declared source");
                if(table.equals("strings")) {
                    long payload=row.getLong("payloadOffset"),count=row.getLong("payloadSizeBytes");
                    if(payload<at||payload-at>size||count<0||count>size-(payload-at)) throw new IOException("Resource string payload crosses its stored record range");
                    String text=row.getString("text"),encoding=row.getString("encoding");
                    stringUnits+=text.length();
                    if(text.length()!=row.getInt("utf16Units")||text.length()>4*1024*1024||stringUnits>16*1024*1024
                        ||!encoding.equals("UTF-8 / modified UTF-8")&&!encoding.equals("UTF-16LE")) throw new IOException("Resource string encoding/identity exceeds its declared budget");
                }
            }
        }
        if(tables.getJSONArray("strings").length()!=value.getInt("wireStringCount")||tables.getJSONArray("entries").length()!=value.getInt("wireEntryCount")
            ||tables.getJSONArray("offsets").length()!=value.getInt("wireEntrySlots")) throw new IOException("Resource wire model was incompletely transferred");
        value.remove("tables");header=value;
    }
    EvidenceRows rows(String table,JSONObject where,BooleanSupplier stop) throws Exception {
        if(!TABLES.contains(table)) throw new IOException("Unknown Android resource table");
        JSONArray records=tables.getJSONArray(table);int[] selected=new int[records.length()];int count=0;
        for(int i=0;i<records.length();i++) {
            EvidenceRows.check(stop);JSONObject record=records.getJSONObject(i);boolean matches=true;Iterator<String> fields=where.keys();
            while(fields.hasNext()) { String key=fields.next();Object actual=field(record,key),expected=where.get(key);if(actual==null||!String.valueOf(actual).equals(String.valueOf(expected))) { matches=false;break; } }
            if(matches) selected[count++]=i;
        }
        final int[] indices=Arrays.copyOf(selected,count);
        return new EvidenceRows("android-resource-"+table,"Android resources • "+table+" • "+name) {
            int size() { return indices.length; }
            Row row(int index,BooleanSupplier cancelled) throws Exception {
                EvidenceRows.check(cancelled);int original=indices[index];JSONObject raw=records.getJSONObject(original);
                JSONObject fields=(JSONObject)preview(raw,0,new int[]{0},cancelled);
                fields.put("resourceTable",table).put("fullNativeRecordInJsonExport",true).put("context",note(table));
                String label=shortText(raw.optString("name",table+" "+original),4096);
                String description;
                if(raw.has("elementName")) description="Element: "+shortText(raw.getString("elementName"),400)+" • line="+raw.optLong("lineNumber")+" • attributes="+raw.optInt("attributeCount");
                else if(raw.has("text")) description=raw.getString("text");
                else if(raw.has("value")) { JSONObject value=raw.getJSONObject("value");description=value.has("text")?value.getString("text"):value.toString(); }
                else description=raw.optString("engineClass",note(table));
                return new Row(original,label,shortText(description,500),raw.getLong("physicalOffset"),raw.getLong("sizeBytes"),fields);
            }
            JSONObject metadata() throws Exception { return provenance().put("table",table).put("where",where).put("selectedRows",indices.length).put("interpretation",note(table)); }
            @Override boolean matches(Row row,String query,BooleanSupplier cancelled) throws Exception {
                // Shared explanations must not make every row match the same term.
                // Search the complete decoded record, including text beyond its preview.
                return containsDecoded(records.getJSONObject(row.index),query,0,new int[]{0},cancelled);
            }
            @Override void writeSelectedRow(Writer output,Row row,BooleanSupplier cancelled) throws Exception {
                output.write(row.encoded,0,row.encoded.length()-1);output.write(",\"fullNativeRecord\":");
                EvidenceJson.write(output,records.getJSONObject(row.index),cancelled);output.write('}');
            }
        };
    }
    static Object field(JSONObject object,String path) throws Exception {
        Object value=object;
        for(String part:path.split("\\.")) {
            if(!(value instanceof JSONObject)||!((JSONObject)value).has(part)) return null;
            value=((JSONObject)value).get(part);
        }
        return value;
    }
    JSONObject raw(String table,int row) throws Exception {
        if(!TABLES.contains(table)) throw new IOException("Unknown resource record table");
        return tables.getJSONArray(table).getJSONObject(row);
    }
    JSONObject provenance() throws Exception {
        return new JSONObject(header.toString()).put("input",new JSONObject().put("name",name).put("sizeBytes",source.length()).put("sha256",sha256))
            .put("offsetDomain","Physical bytes in this exact selected uncompressed resource snapshot. Resource IDs/string indices are logical selectors; parent compressed-APK offsets are not inferred.")
            .put("scopeLimit","Source-bounded structure/model observations. Opaque chunks and raw header/configuration bytes remain visible. Parsing does not establish Android platform acceptance or component behavior.");
    }
    private static Object preview(Object value,int depth,int[] nodes,BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if(++nodes[0]>1024||depth>12) return "[interactive preview limit; complete record in JSON export]";
        if(value instanceof String) return shortText((String)value,4096);
        if(value instanceof JSONObject) {
            JSONObject original=(JSONObject)value,result=new JSONObject();Iterator<String> keys=original.keys();
            while(keys.hasNext()) { String key=keys.next();result.put(key,preview(original.get(key),depth+1,nodes,stop)); }
            return result;
        }
        if(value instanceof JSONArray) {
            JSONArray original=(JSONArray)value,result=new JSONArray();
            for(int i=0;i<Math.min(original.length(),16);i++) result.put(preview(original.get(i),depth+1,nodes,stop));
            if(original.length()>16) result.put("[complete array in JSON export]");
            return result;
        }
        return value;
    }
    static String shortText(String value,int maximum) { return value.length()<=maximum?value:value.substring(0,maximum)+"… [preview; complete data available]"; }
    static String note(String table) {
        switch(table) {
            case "strings": return "Decoded UTF-16 identity is separate from exact UTF-8/modified UTF-8/UTF-16LE storage. Physical range includes length prefixes/terminator; payload offsets isolate encoded value bytes. Style indices refer to this pool.";
            case "spans": return "Markup name is a string-pool reference. First/last positions address UTF-16 code units, with an inclusive last position. Absent styles and original offsets remain explicit.";
            case "attributes": return "Binary XML preserves optional lexical/raw strings separately from typed Res_value data. Namespace/name indices refer to this XML pool; a declared flag does not establish runtime behavior.";
            case "xml-events": return "Stored namespace/element/text events, line/comment references and observed element relationships. A balanced or accepted platform XML document is not implied by bounds-valid chunks.";
            case "entries": return "Resource IDs pack package/type/entry selectors and are not file addresses. Configuration variants, duplicate logical indices and shared physical entries remain separate. Compact entries store 8 bytes despite a logical Res_value model.";
            case "maps": return "Complex resource bags/styles retain every stored mapping ordinal and duplicate key; upstream Map normalization is not the inventory. Name IDs can identify framework/internal attributes; values retain their exact typed bits.";
            case "types": return "A resource type/configuration describes candidates for Android selection. Qualifier text is the pinned engine's interpretation; complete raw bytes retain unknown fields/flags. Actual device selection is unverified.";
            case "offsets": return "Dense/offset16/sparse slot storage is retained, including absent NO_ENTRY sentinels. Offsets are relative to entryDataBase; some encode units of four bytes. Logical entry indices are separate from slot ordinals.";
            case "packages": return "Package/type/key pools and identifiers are stored declarations. Type-ID adjustments are identifier arithmetic, not byte offsets. Dynamic package IDs can differ at runtime.";
            case "resource-map": return "Maps XML name-string indices to attribute resource identifiers. These IDs describe the attribute definition, separately from the attribute's typed value and file byte position.";
            case "type-spec-flags": return "Per-entry configuration/public/staged flags describe resource table metadata. They are not runtime component export or security policy decisions.";
            case "libraries": return "Build-time shared-library package IDs/names require runtime remapping for dynamic resource references; no library is loaded by this investigation.";
            default: return "Complete bounded chunk/pool information with exact source coordinates. Opaque layouts are labeled; original source bytes remain authoritative for unused/padding and unknown data.";
        }
    }
}

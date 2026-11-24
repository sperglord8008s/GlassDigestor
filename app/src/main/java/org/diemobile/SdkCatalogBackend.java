// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Inspectable complete offline rules, with their own owned catalog byte source. */
final class SdkCatalogBackend extends InvestigationBackend {
    interface Source { InputStream open() throws Exception; }
    private final Source source;
    private final File directory;
    private File owned;
    private KnowledgeCatalog catalog;
    SdkCatalogBackend(Source source,File directory) { this.source=source;this.directory=directory; }
    private void acquire(BooleanSupplier stop) throws Exception {
        if(catalog!=null) return;
        if(!directory.isDirectory()&&!directory.mkdirs()) throw new IOException("Cannot create private knowledge snapshot directory");
        File candidate=File.createTempFile("sdk-catalog-",".json",directory);boolean success=false;
        try {
            try(InputStream input=source.open();OutputStream output=new FileOutputStream(candidate)) {
                int total=0,count;byte[] buffer=new byte[32768];
                while((count=input.read(buffer))!=-1) {
                    EvidenceRows.check(stop);
                    if(count>8*1024*1024-total) throw new IOException("SDK catalog exceeds 8 MiB");
                    output.write(buffer,0,count);total+=count;
                }
            }
            try(InputStream input=new FileInputStream(candidate)) { catalog=KnowledgeCatalog.read(input,stop); }
            EvidenceRows.check(stop);owned=candidate;success=true;
        } finally { if(!success) { catalog=null;candidate.delete(); } }
    }
    static String query(int type,boolean errors,String uuid) throws Exception {
        return new JSONObject().put("type",type).put("errors",errors).put("uuid",uuid).toString();
    }
    @Override String initialRequest(BooleanSupplier stop) throws Exception { return query(-1,false,""); }
    @Override EvidenceRows open(String request,BooleanSupplier stop) throws Exception {
        acquire(stop);JSONObject value=new JSONObject(request);int type=value.getInt("type");
        if(type<-1||type>=NameMatcher.TYPES.length) throw new IOException("Unknown SDK evidence type");
        return catalog.rows(type,value.getBoolean("errors"),value.getString("uuid"));
    }
    @Override List<Link> roots(String request,BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);List<Link> links=new ArrayList<>();
        links.add(Link.view("Every canonical SDK / library matcher",query(-1,false,"")));
        for(int type=0;type<NameMatcher.TYPES.length;type++) links.add(Link.view(NameMatcher.displayTypeLabel(type),query(type,false,"")));
        links.add(Link.view("Unsupported regex diagnostics",query(-1,true,"")));return links;
    }
    @Override List<Link> related(String request,EvidenceRows rows,EvidenceRows.Row row,BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        return Collections.singletonList(Link.view("Every matcher for this library UUID",query(-1,false,row.json().getString("uuid"))));
    }
    @Override JSONObject provenance() throws Exception { return catalog.provenance().put("inputKind","offline rule catalog"); }
    @Override File byteSource() { return owned; }
    @Override String sourceName() { return "LibChecker canonical rules / dataVersion "+(catalog==null?"?":catalog.metadata.optInt("dataVersion")); }
    @Override String help(String request) {
        return "Complete offline SDK rule catalog • exact names and regex, priority, stable IDs/library UUIDs, original labels and all effective locale metadata. These are definitions; observed file-name matches appear in APK / SDKs. Regex uses a source-built linear-time engine and the upstream portable contract. Tap for source/compiler/content identity and support counts.";
    }
    @Override public void close() { if(owned!=null) owned.delete(); }
}

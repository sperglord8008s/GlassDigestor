// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Raw resource files and CRC-verified APK members share one evidence navigator. */
final class AndroidResourceBackend extends InvestigationBackend {
    private final File input,directory;
    private final String name;
    private final BinaryRunner runner;
    private InputSnapshot parent;
    private ArchiveStore.Inventory archive;
    private ArchiveStore.Extracted extraction;
    private AndroidResourceDocument document;
    private boolean zip,listing;
    private int selected=-2;
    private File active;
    private String selectedName;
    AndroidResourceBackend(File input,File directory,String name,BinaryRunner runner) {
        this.input=input;this.directory=directory;this.name=name;this.runner=runner;
    }
    static String request(int member,String table,JSONObject where) throws Exception {
        return new JSONObject().put("member",member).put("table",table).put("where",where).toString();
    }
    private static String inputs() { return "{\"inputs\":true}"; }
    private void acquire(BooleanSupplier stop) throws Exception {
        if(parent!=null) return;
        InputSnapshot snapshot=InputSnapshot.acquire(input,directory,256L*1024*1024,stop);boolean success=false;
        try(RandomAccessFile reader=new RandomAccessFile(snapshot.file,"r")) {
            zip=reader.length()>=4&&reader.read()=='P'&&reader.read()=='K';
            if(!zip&&snapshot.size>64L*1024*1024) throw new IOException("Raw Android resource input exceeds 64 MiB");
            if(zip) archive=ArchiveStore.read(snapshot.file,stop);
            parent=snapshot;success=true;
        } finally { if(!success) snapshot.close(); }
    }
    @Override String initialRequest(BooleanSupplier stop) throws Exception {
        acquire(stop);
        if(zip) {
            for(ArchiveStore.Member member:archive.members) if(member.name.equals("AndroidManifest.xml")&&!member.directory) return request(member.index,"xml-events",new JSONObject());
            return inputs();
        }
        try(RandomAccessFile reader=new RandomAccessFile(parent.file,"r")) {
            int type=reader.length()>=2?reader.read()|(reader.read()<<8):-1;
            return request(-1,type==3?"xml-events":"entries",new JSONObject());
        }
    }
    private void select(int member,BooleanSupplier stop) throws Exception {
        acquire(stop);if(document!=null&&member==selected) return;
        ArchiveStore.Extracted candidate=null;File source=parent.file;String display=name,sha=parent.sha256;
        try {
            if(member>=0) {
                if(!zip||member>=archive.members.size()) throw new IOException("Resource archive member selector expired");
                ArchiveStore.Member value=archive.members.get(member);
                if(value.size<0||value.size>64L*1024*1024) throw new IOException("Selected Android resource member exceeds 64 MiB or has unknown size");
                candidate=ArchiveStore.extract(archive,value,directory,stop);source=candidate.file;sha=candidate.sha256;display+="!/"+value.name;
            } else if(member!=-1||zip) throw new IOException("Choose a compiled resource member from this archive");
            String report=runner.scan(source,stop);EvidenceRows.check(stop);
            AndroidResourceDocument parsed=new AndroidResourceDocument(source,display,sha,report,stop);
            if(extraction!=null) extraction.file.delete();
            extraction=candidate;active=source;selectedName=display;selected=member;document=parsed;
        } catch(Exception|StackOverflowError error) { if(candidate!=null) candidate.file.delete();throw error; }
    }
    private boolean candidate(ArchiveStore.Member member) {
        String lower=member.name.toLowerCase(Locale.ROOT);
        return !member.directory&&(lower.endsWith(".xml")||lower.endsWith(".arsc"));
    }
    @Override EvidenceRows open(String encoded,BooleanSupplier stop) throws Exception {
        acquire(stop);JSONObject query=new JSONObject(encoded);
        if(query.optBoolean("inputs")) {
            if(!zip) throw new IOException("This resource input is not an archive");
            listing=true;List<ArchiveStore.Member> members=new ArrayList<>();
            for(ArchiveStore.Member member:archive.members) if(candidate(member)) members.add(member);
            return new EvidenceRows("android-resource-inputs","Compiled XML / resource table candidates • "+name) {
                int size() { return members.size(); }
                Row row(int i,BooleanSupplier cancelled) throws Exception {
                    EvidenceRows.check(cancelled);ArchiveStore.Member value=members.get(i);
                    return new Row(i,value.name,value.size+" expanded bytes • method="+value.method+" • occurrences="+value.occurrences,null,null,
                        new JSONObject().put("memberIndex",value.index).put("declaredSizeBytes",value.size).put("compressedSizeBytes",value.compressedSize)
                            .put("method",value.method).put("crc32",String.format(Locale.ROOT,"%08x",value.crc32)).put("nameOccurrences",value.occurrences)
                            .put("canSelect",value.occurrences==1&&value.size>=0&&value.size<=64L*1024*1024)
                            .put("interpretation","Name-selected candidate. A copied parent APK and full directory/size/CRC checks precede parsing; the name does not prove this member uses compiled Android resource syntax."));
                }
                JSONObject metadata() throws Exception { return archiveOrigin().put("candidatePolicy","All .xml/.arsc names, including members outside res/. Plain XML is not silently treated as compiled XML."); }
            };
        }
        select(query.getInt("member"),stop);listing=false;
        String table=query.getString("table");JSONObject where=query.getJSONObject("where");
        return document.rows(table,where,stop);
    }
    private Link link(String title,String table,JSONObject where) throws Exception { return Link.view(title,request(selected,table,where)); }
    private JSONObject condition(String field,Object value) throws Exception { return new JSONObject().put(field,value); }
    @Override List<Link> roots(String encoded,BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);List<Link> result=new ArrayList<>();
        if(zip) result.add(Link.view("Choose a compiled XML / resource table member",inputs()));
        if(!listing&&document!=null) for(String table:AndroidResourceDocument.TABLES) result.add(link(table+" • "+document.tables.getJSONArray(table).length()+" records",table,new JSONObject()));
        return result;
    }
    @Override List<Link> related(String encoded,EvidenceRows source,EvidenceRows.Row row,BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);List<Link> links=new ArrayList<>();JSONObject query=new JSONObject(encoded);
        if(query.optBoolean("inputs")) {
            JSONObject fields=row.json();
            if(fields.getBoolean("canSelect")) {
                int member=fields.getInt("memberIndex");String lower=archive.members.get(member).name.toLowerCase(Locale.ROOT);
                links.add(Link.view("Inspect this compiled resource snapshot",request(member,lower.endsWith(".xml")?"xml-events":"entries",new JSONObject())));
            }
            return links;
        }
        String table=query.getString("table");JSONObject raw=document.raw(table,row.index);
        if(raw.has("poolChunkIndex")) links.add(link("Strings in the associated pool","strings",condition("poolChunkIndex",raw.get("poolChunkIndex"))));
        if(table.equals("strings")) {
            JSONObject selectedValue=new JSONObject().put("poolChunkIndex",raw.get("poolChunkIndex")).put("styleIndex",raw.get("stringIndex"));
            links.add(link("Stored style spans for this string","spans",selectedValue));
            String value=request(selected,table,condition("index",row.index));
            links.add(Link.text("View / export complete decoded pool string",value,"resource-string.txt"));
            links.add(Link.blob("Export exact encoded string payload bytes",value,"resource-string.bin"));
            links.add(Link.blob("Export decoded UTF-16LE code units (no BOM)",new JSONObject(value).put("utf16le",true).toString(),"resource-string.utf16le"));
        }
        if(raw.has("typeChunkIndex")) {
            JSONObject type=condition("typeChunkIndex",raw.get("typeChunkIndex"));
            links.add(link("Type / configuration facts","types",type));links.add(link("Every definition in this configuration","entries",type));links.add(link("All original entry offset slots","offsets",type));
        }
        if(table.equals("entries")) links.add(link("Every stored complex mapping for this entry","maps",new JSONObject().put("typeChunkIndex",raw.get("typeChunkIndex")).put("entryOrdinal",raw.get("ordinal"))));
        if(raw.has("elementChunkIndex")) links.add(link("Owning XML element / event","xml-events",condition("chunkIndex",raw.get("elementChunkIndex"))));
        if(table.equals("xml-events")) links.add(link("Every stored attribute on this element","attributes",condition("elementChunkIndex",raw.get("chunkIndex"))));
        if(table.equals("chunks")) {
            int chunk=raw.getInt("chunkIndex");links.add(link("Contained chunks","chunks",condition("parentChunkIndex",chunk)));
            if(raw.getInt("chunkType")==1) links.add(link("Every string in this pool","strings",condition("poolChunkIndex",chunk)));
            if(raw.getInt("chunkType")==0x102) links.add(link("Attributes on this element","attributes",condition("elementChunkIndex",chunk)));
        }
        Object id=AndroidResourceDocument.field(raw,"value.resourceId");
        if(id!=null) links.add(link("Local definitions / all configurations for referenced ID","entries",condition("resourceId",id)));
        if(raw.has("resourceId")&&!raw.isNull("resourceId")) links.add(link("Definitions sharing this resource ID","entries",condition("resourceId",raw.get("resourceId"))));
        if(raw.has("value")&&raw.getJSONObject("value").has("stringIndex")) {
            JSONObject value=raw.getJSONObject("value");
            links.add(link("Referenced complete pool string / original bytes","strings",new JSONObject().put("poolChunkIndex",value.get("poolChunkIndex")).put("stringIndex",value.get("stringIndex"))));
        }
        return links;
    }
    private JSONObject archiveOrigin() throws Exception {
        return new JSONObject().put("input",new JSONObject().put("name",name).put("sizeBytes",parent.size).put("sha256",parent.sha256))
            .put("archiveEntries",archive==null?0:archive.members.size()).put("scope","Owned full parent snapshot; candidates are names, not proof of complete resource discovery");
    }
    @Override JSONObject provenance() throws Exception {
        if(listing||document==null) return archiveOrigin();
        JSONObject result=document.provenance();
        if(zip) {
            ArchiveStore.Member member=archive.members.get(selected);
            result.put("parentArchive",archiveOrigin()).put("member",new JSONObject().put("index",selected).put("name",member.name).put("sizeBytes",member.size)
                .put("compressedSizeBytes",member.compressedSize).put("method",member.method).put("crc32",String.format(Locale.ROOT,"%08x",member.crc32)).put("acquiredSha256",extraction.sha256));
        }
        return result;
    }
    private JSONObject selectedString(String encoded) throws Exception {
        JSONObject query=new JSONObject(encoded);
        if(query.getInt("member")!=selected||document==null||!query.getString("table").equals("strings")) throw new IOException("This resource string scope is not active; reopen its original member");
        return document.raw("strings",query.getJSONObject("where").getInt("index"));
    }
    @Override void writeText(String encoded,Writer output,BooleanSupplier stop) throws Exception {
        String text=selectedString(encoded).getString("text");
        for(int i=0;i<text.length();i++) {
            EvidenceRows.check(stop);
            char unit=text.charAt(i);
            if(Character.isHighSurrogate(unit)&&i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1))) i++;
            else if(Character.isSurrogate(unit)) throw new IOException("This string contains an isolated UTF-16 surrogate, which UTF-8 cannot represent. Export the complete JSON record, decoded UTF-16LE code units or original encoded payload to retain it exactly.");
        }
        for(int i=0;i<text.length();i+=4096) { EvidenceRows.check(stop);output.write(text,i,Math.min(4096,text.length()-i)); }
    }
    @Override byte[] binaryValue(String encoded,BooleanSupplier stop) throws Exception {
        JSONObject value=selectedString(encoded);
        if(new JSONObject(encoded).optBoolean("utf16le")) {
            String text=value.getString("text");byte[] bytes=new byte[text.length()*2];
            for(int i=0;i<text.length();i++) { EvidenceRows.check(stop);char unit=text.charAt(i);bytes[2*i]=(byte)unit;bytes[2*i+1]=(byte)(unit>>>8); }
            return bytes;
        }
        int count=value.getInt("payloadSizeBytes");byte[] bytes=new byte[count];
        try(RandomAccessFile source=new RandomAccessFile(active,"r")) {
            source.seek(value.getLong("payloadOffset"));int position=0;
            while(position<count) { EvidenceRows.check(stop);int take=Math.min(65536,count-position);source.readFully(bytes,position,take);position+=take; }
        }
        return bytes;
    }
    @Override File byteSource() { return listing||active==null?parent==null?input:parent.file:active; }
    @Override String sourceName() { return listing||selectedName==null?name:selectedName; }
    @Override String statusSummary() {
        return document!=null&&!document.header.optBoolean("semanticCoverageComplete")?"PARTIAL SEMANTIC COVERAGE • opaque chunks retain original ranges.":"";
    }
    @Override String help(String request) {
        return "Compiled Android XML/resources • actual source-built Google parser plus bounded wire inventory. Every table is available in Views. Resource IDs, pool indices and UTF-16 positions are distinct from physical offsets. Search covers complete decoded values; previews are bounded and complete records export to JSON. Declarations do not prove platform acceptance or live component behavior. Tap for source/coverage details.";
    }
    @Override public void close() throws Exception {
        try { runner.close(); } finally { if(extraction!=null) extraction.file.delete();if(parent!=null) parent.close(); }
    }
}

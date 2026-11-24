// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Owned multidex inputs and class declarations; method bodies are never retained by this index. */
final class DexDefinitionIndex implements AutoCloseable {
    static final long MAX_PARENT_BYTES=256L*1024*1024,MAX_DEX_BYTES=96L*1024*1024;
    static final int MAX_SOURCES=128,MAX_CLASSES=120000,MAX_CLASS_UNITS=16*1024*1024;
    static final class Root {
        final File file,directory;
        final String name,expectedSha;
        final long expectedSize;
        final boolean archive;
        Root(File file,File directory,String name,boolean archive,String expectedSha,long expectedSize) {
            this.file=file;this.directory=directory;this.name=name;this.archive=archive;this.expectedSha=expectedSha;this.expectedSize=expectedSize;
        }
    }
    static final class Origin {
        final int member;
        final File file;
        final String sha256;
        Origin(int member,File file,String sha256) { this.member=member;this.file=file;this.sha256=sha256; }
    }
    static final class Deadline {
        final BooleanSupplier user;
        final long began=System.nanoTime();
        Deadline(BooleanSupplier user) { this.user=user; }
        boolean expired() { return System.nanoTime()-began>30000000000L; }
        boolean stopped() { return user.getAsBoolean()||expired(); }
        void check() throws IOException {
            EvidenceRows.check(user);if(expired()) throw new IOException("DEX definition operation exceeds its 30-second cooperative deadline; scope is incomplete");
        }
    }
    private static final class Candidate {
        final int index,member;
        final String name;
        final long declaredSize,crc32;
        final ArchiveStore.Member zip;
        Candidate(int index,int member,String name,long size,long crc,ArchiveStore.Member zip) {
            this.index=index;this.member=member;this.name=name;declaredSize=size;crc32=crc;this.zip=zip;
        }
    }
    static final class Source {
        final int index,member,indexedClasses,declaredClasses;
        final String name,sha256,error;
        final long declaredSize,size,crc32;
        final File file;
        private final JSONArray entries;
        Source(Candidate candidate,File file,long size,String sha,JSONArray entries,int declared,int indexed,String error) {
            index=candidate.index;member=candidate.member;name=candidate.name;declaredSize=candidate.declaredSize;crc32=candidate.crc32;
            this.file=file;this.size=size;sha256=sha;this.entries=entries;declaredClasses=declared;indexedClasses=indexed;this.error=error;
        }
        boolean parsed() { return entries.length()>0; }
        JSONObject json() throws Exception {
            return new JSONObject().put("sourceIndex",index).put("archiveMemberIndex",member<0?JSONObject.NULL:member).put("name",name)
                .put("declaredSizeBytes",declaredSize<0?JSONObject.NULL:declaredSize).put("snapshotSizeBytes",size<0?JSONObject.NULL:size)
                .put("declaredCrc32",crc32<0?JSONObject.NULL:String.format(Locale.ROOT,"%08x",crc32)).put("sha256",sha256.isEmpty()?JSONObject.NULL:sha256)
                .put("status",error.isEmpty()?"indexed":file==null?"unavailable":parsed()?"parsed-not-indexed":"DEX-parse-failed")
                .put("diagnostic",error).put("declaredClasses",declaredClasses<0?JSONObject.NULL:declaredClasses).put("indexedClasses",indexedClasses)
                .put("entryMetadata",new JSONArray(entries.toString())).put("snapshotAvailable",file!=null&&file.isFile()&&file.length()==size)
                .put("snapshotAvailabilityCheck","Path/length only; exact SHA-256 is checked before a snapshot is loaded or exported");
        }
    }
    static final class Location {
        final int source,entry,classIndex,typeIndex;
        final String descriptor;
        final long physicalOffset;
        Location(int source,int entry,int classIndex,int typeIndex,String descriptor,long physicalOffset) {
            this.source=source;this.entry=entry;this.classIndex=classIndex;this.typeIndex=typeIndex;this.descriptor=descriptor;this.physicalOffset=physicalOffset;
        }
    }
    final Root root;
    private final List<Source> sourceRecords=new ArrayList<>();
    private final List<Location> declarations=new ArrayList<>();
    private final List<File> owned=new ArrayList<>();
    private final Map<String,List<Location>> byDescriptor=new HashMap<>();
    final List<Source> sources=Collections.unmodifiableList(sourceRecords);
    final List<Location> classes=Collections.unmodifiableList(declarations);
    ArchiveStore.Inventory archive;
    File manifest;
    String manifestSha256="",parentSha256="";
    long parentSize,reservedBytes,manifestSize;
    private long classUnits;
    private DexDefinitionIndex(Root root) { this.root=root; }
    static DexDefinitionIndex acquire(Root root,Origin origin,BooleanSupplier cancelled) throws Exception {
        DexDefinitionIndex index=new DexDefinitionIndex(root);boolean success=false;Deadline budget=new Deadline(cancelled);
        try { index.acquire(origin,budget);success=true;return index; }
        catch(InterruptedIOException failure) { budget.check();throw failure; }
        finally { if(!success) index.close(); }
    }
    private void expected(InputSnapshot snapshot,Origin origin) throws IOException {
        if(origin!=null&&!root.archive) {
            if(!snapshot.sha256.equals(origin.sha256)) throw new IOException("Origin DEX bytes changed before definition acquisition");
            return;
        }
        if(root.expectedSize>=0&&snapshot.size!=root.expectedSize) throw new IOException("Parent size differs from the captured input identity");
        if(root.expectedSha!=null&&root.expectedSha.matches("[0-9a-fA-F]{64}")&&!snapshot.sha256.equalsIgnoreCase(root.expectedSha)) throw new IOException("Parent SHA-256 differs from the captured input identity");
    }
    private void acquire(Origin origin,Deadline budget) throws Exception {
        budget.check();
        if(root.archive) {
            try(InputSnapshot parent=InputSnapshot.acquire(root.file,root.directory,MAX_PARENT_BYTES,budget::stopped)) {
                expected(parent,origin);parentSha256=parent.sha256;parentSize=parent.size;archive=ArchiveStore.read(parent.file,budget::stopped);
                List<Candidate> candidates=new ArrayList<>();
                for(ArchiveStore.Member member:archive.members) {
                    budget.check();if(member.directory||!member.name.toLowerCase(Locale.ROOT).endsWith(".dex")&&(origin==null||member.index!=origin.member)) continue;
                    if(candidates.size()==MAX_SOURCES) throw new IOException("Definition acquisition exceeds 128 named/explicit DEX candidates; inspect individual members");
                    candidates.add(new Candidate(candidates.size(),member.index,member.name,member.size,member.crc32,member));sourceRecords.add(null);
                }
                // Keep source ordinals in directory order, while reserving the
                // explicitly selected origin before other members consume bytes.
                List<Candidate> order=new ArrayList<>(candidates);
                if(origin!=null) order.sort(Comparator.comparingInt(value->value.member==origin.member?0:1));
                for(Candidate candidate:order) sourceRecords.set(candidate.index,acquire(candidate,null,budget));
            }
        } else {
            File source=origin==null?root.file:origin.file;
            InputSnapshot snapshot=InputSnapshot.acquire(source,root.directory,DexDocument.MAX_BYTES,budget::stopped);owned.add(snapshot.file);
            expected(snapshot,origin);parentSha256=snapshot.sha256;parentSize=snapshot.size;
            sourceRecords.add(acquire(new Candidate(0,-1,root.name,snapshot.size,-1,null),snapshot,budget));
        }
        budget.check();
        declarations.sort(Comparator.comparingInt((Location value)->value.source).thenComparingInt(value->value.entry).thenComparingInt(value->value.classIndex));
        for(Location location:declarations) { budget.check();byDescriptor.computeIfAbsent(location.descriptor,key->new ArrayList<>()).add(location); }
        if(origin!=null) {
            Source source=source(origin.member);
            if(source==null||!source.sha256.equals(origin.sha256)) throw new IOException("Captured origin cannot be correlated to this complete parent acquisition; previous DEX snapshot retained");
        }
        writeManifest(budget);budget.check();
    }
    private Source acquire(Candidate candidate,InputSnapshot raw,Deadline budget) throws Exception {
        File file=null;String sha="",error="";long size=-1;JSONArray entries=new JSONArray();int declared=-1,indexed=0;
        try {
            budget.check();
            if(candidate.declaredSize<0||candidate.declaredSize>DexDocument.MAX_BYTES||candidate.declaredSize>MAX_DEX_BYTES-reservedBytes) throw new IOException("Unknown size or 32 MiB/source / 96 MiB aggregate reservation budget");
            reservedBytes+=candidate.declaredSize;
            if(raw!=null) { file=raw.file;sha=raw.sha256;size=raw.size; }
            else {
                ArchiveStore.Extracted extracted=ArchiveStore.extract(archive,candidate.zip,root.directory,budget::stopped);
                file=extracted.file;owned.add(file);sha=extracted.sha256;size=extracted.size;
            }
            DexDocument document=DexDocument.read(file,budget::stopped);budget.check();
            if(!sha.equals(document.sha256)) throw new IOException("Acquired DEX changed before indexing");
            declared=0;for(DexDocument.Entry entry:document.entries) { entries.put(entry.metadata());declared+=entry.dex.getClassSection().size(); }
            if(declared>MAX_CLASSES-declarations.size()) throw new IOException("Aggregate class-definition index exceeds 120,000 records; this source was not indexed");
            List<Location> staged=new ArrayList<>();long units=0;
            for(DexDocument.Entry entry:document.entries) for(int i=0;i<entry.dex.getClassSection().size();i++) {
                budget.check();String descriptor=entry.dex.getClassSection().get(i).getType();
                if(!descriptor.startsWith("L")||!descriptor.endsWith(";")||descriptor.length()<3) throw new IOException("Invalid object-class descriptor; this source was not indexed");
                units+=descriptor.length();if(units>MAX_CLASS_UNITS-classUnits) throw new IOException("Aggregate class-name index exceeds 16 Mi UTF-16 units; this source was not indexed");
                long offset=entry.dex.getClassSection().getOffset(i);entry.requireRange(offset,32);
                staged.add(new Location(candidate.index,entry.index,i,entry.dex.getBuffer().readSmallUint((int)offset),descriptor,offset));
            }
            budget.check();classUnits+=units;indexed=staged.size();declarations.addAll(staged);
        } catch(Exception failure) {
            budget.check();error=failure.toString();
        } catch(StackOverflowError failure) { budget.check();error="Parser recursion exhausted; this source was not indexed"; }
        return new Source(candidate,file,size,sha,entries,declared,indexed,error);
    }
    Source source(int member) {
        for(Source source:sources) if(source!=null&&source.member==member) return source;return null;
    }
    List<Location> find(String descriptor) {
        List<Location> found=byDescriptor.get(descriptor);return found==null?Collections.emptyList():Collections.unmodifiableList(found);
    }
    DexDocument load(Source source,BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if(source.file==null||!source.file.isFile()||source.file.length()!=source.size) throw new IOException("Captured DEX source unavailable: "+source.name);
        DexDocument document=DexDocument.read(source.file,stop);
        if(!document.sha256.equals(source.sha256)) throw new IOException("Captured DEX source SHA-256 changed: "+source.name);
        return document;
    }
    boolean complete() { for(Source source:sources) if(!source.error.isEmpty()) return false;return true; }
    private JSONObject facts() throws Exception {
        JSONArray inputs=new JSONArray();for(Source source:sources) inputs.put(source.json());
        return new JSONObject().put("schema","org.diemobile.dex-definition-index").put("schemaVersion",1)
            .put("engine",DexDocument.ENGINE).put("engineVersion",DexDocument.VERSION)
            .put("parentInput",new JSONObject().put("name",root.name).put("sizeBytes",parentSize).put("sha256",parentSha256))
            .put("selection",root.archive?"Every non-directory name ending .dex, case-insensitively, plus the explicit origin; no nested APK/other-split/hidden-code discovery":"Selected raw DEX/container only")
            .put("completeInSelectedScope",complete()).put("sourceCount",sources.size()).put("classDefinitionCount",classes.size()).put("classDescriptorUtf16Units",classUnits)
            .put("reservedDexBytes",reservedBytes).put("sources",inputs)
            .put("limits",new JSONObject().put("parentBytes",MAX_PARENT_BYTES).put("sourceBytes",DexDocument.MAX_BYTES).put("aggregateReservedBytes",MAX_DEX_BYTES)
                .put("sources",MAX_SOURCES).put("classDefinitions",MAX_CLASSES).put("classDescriptorUtf16Units",MAX_CLASS_UNITS).put("cooperativeDeadlineSeconds",30))
            .put("interpretation","Exact declared-owner/signature information only. Duplicates remain separate; no class loading, inheritance/dispatch/reflection/framework resolution, ART acceptance, SDK invocation or runtime reachability is established. Failed/skipped sources make absence/uniqueness unknown.")
            .put("networkUsed",false).put("executionObserved",false);
    }
    private void writeManifest(Deadline budget) throws Exception {
        manifest=File.createTempFile("dex-definition-index-",".json",root.directory);owned.add(manifest);MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(Writer output=new OutputStreamWriter(new DigestOutputStream(new FileOutputStream(manifest),digest),StandardCharsets.UTF_8.newEncoder())) {
            EvidenceJson.write(output,facts(),budget::stopped);
        }
        manifestSha256=Inspector.toHex(digest.digest());manifestSize=manifest.length();
    }
    JSONObject provenance() throws Exception {
        return facts().put("inputKind","multi-source DEX definition index; rows do not use manifest byte offsets")
            .put("input",new JSONObject().put("name","DEX definition index manifest").put("sizeBytes",manifestSize).put("sha256",manifestSha256))
            .put("indexManifestAvailable",manifest.isFile()&&manifest.length()==manifestSize);
    }
    EvidenceRows sourceRows() {
        return new EvidenceRows("dex-definition-sources",(complete()?"":"PARTIAL scope • ")+"Captured DEX definition sources") {
            int size() { return sources.size(); }
            Row row(int index,BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);Source source=sources.get(index);JSONObject fields=source.json();
                return new Row(index,source.name,fields.getString("status")+" • indexed classes="+source.indexedClasses+" • "+source.error,null,null,fields);
            }
            JSONObject metadata() throws Exception { return provenance(); }
            @Override boolean matches(Row row,String query,BooleanSupplier stop) throws Exception {
                JSONObject fields=row.json();fields.remove("snapshotAvailabilityCheck");return containsDecoded(fields,query,0,new int[]{0},stop);
            }
        };
    }
    EvidenceRows classRows() {
        return new EvidenceRows("dex-global-class-definitions",(complete()?"":"PARTIAL scope • ")+"Class definitions across captured DEX inputs") {
            int size() { return classes.size(); }
            Row row(int index,BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);Location location=classes.get(index);return locationRow(index,location,"classes",-1,location.physicalOffset,32,location.descriptor);
            }
            JSONObject metadata() throws Exception { return provenance(); }
            @Override boolean matches(Row row,String query,BooleanSupplier stop) throws Exception {
                return sources.get(classes.get(row.index).source).name.toLowerCase(Locale.ROOT).contains(query)||locationMatches(row,query,stop);
            }
        };
    }
    EvidenceRows.Row locationRow(int index,Location location,String kind,int declaration,long offset,int bytes,String descriptor) throws Exception {
        Source source=sources.get(location.source);
        JSONObject fields=new JSONObject().put("definitionSourceIndex",location.source).put("targetArchiveMemberIndex",source.member)
            .put("targetEntryIndex",location.entry).put("targetClassIndex",location.classIndex).put("targetTypeIndex",location.typeIndex)
            .put("targetDeclarationIndex",declaration).put("targetKind",kind).put("descriptor",descriptor).put("definitionClassDescriptor",location.descriptor)
            .put("targetSourceNamePreview",preview(source.name,512)).put("targetSourceNamePreviewTruncated",source.name.length()>512)
            .put("targetPhysicalOffset",offset).put("targetSizeBytes",bytes).put("targetDexSha256",source.sha256)
            .put("locationMeaning",kind.equals("classes")?"class_def item in targetDexSha256":"Target identifier item; class/declaration ordinals retain the encoded declaration, including duplicates. This is not a method-body offset.")
            .put("interpretation","Observed exact declaration, not unique resolution, inheritance/dispatch, initialization or execution. Open the target table before using its physical byte actions.");
        return new EvidenceRows.Row(index,descriptor,"source "+location.source+" • "+preview(source.name,160)+" • entry "+location.entry+" • class "+location.classIndex+" • "+kind+(declaration<0?"":" #"+declaration),null,null,fields);
    }
    private static String preview(String text,int maximum) { return text.length()<=maximum?text:text.substring(0,maximum)+"… [preview; full name in source metadata]"; }
    static boolean locationMatches(EvidenceRows.Row row,String query,BooleanSupplier stop) throws Exception {
        JSONObject fields=row.json();fields.remove("interpretation");fields.remove("locationMeaning");
        return EvidenceRows.containsDecoded(fields,query,0,new int[]{0},stop);
    }
    @Override public void close() { for(File file:owned) file.delete();owned.clear(); }
}

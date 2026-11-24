// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import com.android.tools.smali.dexlib2.ReferenceType;
import com.android.tools.smali.dexlib2.MethodHandleType;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** One active DEX snapshot, bounded ZIP acquisition, and small navigation requests. */
final class DexBackend extends InvestigationBackend {
    interface DefinitionAcquirer {
        DexDefinitionIndex acquire(DexDefinitionIndex.Root root,DexDefinitionIndex.Origin origin,BooleanSupplier stop) throws Exception;
    }
    private final File root, directory;
    private final String name, inheritedSha;
    private final long inheritedSize;
    private final DefinitionAcquirer definitionAcquirer;
    private ArchiveStore.Inventory archive;
    private boolean zip, listing,definitionView,selectedBorrowed;
    private DexDefinitionIndex definitions;
    private DexDocument document;
    private File selected;
    private int selectedMember = -2;
    private String selectedName;
    private final Map<Integer,DexStringIndex> classifiedStrings = new HashMap<>();

    DexBackend(File root, File directory, String name, String inheritedSha, long inheritedSize) {
        this(root,directory,name,inheritedSha,inheritedSize,DexDefinitionIndex::acquire);
    }
    DexBackend(File root,File directory,String name,String inheritedSha,long inheritedSize,DefinitionAcquirer definitionAcquirer) {
        this.root = root; this.directory = directory; this.name = name; this.inheritedSha = inheritedSha; this.inheritedSize = inheritedSize;
        this.definitionAcquirer=definitionAcquirer;
    }
    @Override String initialRequest(BooleanSupplier cancelled) throws Exception {
        EvidenceRows.check(cancelled);
        try (RandomAccessFile input = new RandomAccessFile(root, "r")) { zip = input.length() >= 4 && input.read() == 'P' && input.read() == 'K'; }
        return zip ? inputsRequest() : request(-1, DexQuery.root(0, "classes"));
    }
    private static String inputsRequest() throws JSONException { return new JSONObject().put("inputs", true).toString(); }
    private static String request(int member, DexQuery query) throws JSONException { return new JSONObject().put("member", member).put("dex", query.json()).toString(); }
    private String definitionRoot(String view) throws JSONException {
        return new JSONObject().put("definitionView",view).put("archive",zip).put("originMember",document==null?-2:selectedMember).toString();
    }
    private DexDefinitionIndex definitions(BooleanSupplier stop) throws Exception {
        if(definitions==null) {
            DexDefinitionIndex.Origin origin=document==null?null:new DexDefinitionIndex.Origin(selectedMember,selected,document.sha256);
            DexDefinitionIndex.Root source=new DexDefinitionIndex.Root(root,directory,name,zip,inheritedSha,inheritedSize);
            DexDefinitionIndex candidate=definitionAcquirer.acquire(source,origin,stop);boolean adopted=false;
            try { EvidenceRows.check(stop);definitions=candidate;if(zip) archive=candidate.archive;adopted=true; }
            finally { if(!adopted) candidate.close(); }
        }
        return definitions;
    }
    private void inventory(BooleanSupplier cancelled) throws Exception { if (archive == null) archive = ArchiveStore.read(root, cancelled); }
    private void select(int member, BooleanSupplier cancelled) throws Exception {
        if (document != null && member == selectedMember) return;
        File input = root; ArchiveStore.Extracted extraction = null; InputSnapshot snapshot = null;
        try {
            String display = name;boolean borrowed=false;DexDefinitionIndex.Source indexed=null;
            if(definitions!=null) {
                indexed=definitions.source(member);
                if(indexed==null||indexed.file==null) throw new IOException("DEX member unavailable in the captured definition scope; reopen the input or inspect source diagnostics");
                input=indexed.file;borrowed=true;display=member<0?name:name+"!/"+indexed.name;
            } else if (member >= 0) {
                inventory(cancelled);
                if (member >= archive.members.size()) throw new IOException("Archive member index expired");
                ArchiveStore.Member value = archive.members.get(member);
                if (value.size > DexDocument.MAX_BYTES) throw new IOException("Selected DEX exceeds 32 MiB");
                extraction = ArchiveStore.extract(archive, value, directory, cancelled); input = extraction.file; display += "!/" + value.name;
            } else { snapshot = InputSnapshot.acquire(root,directory,DexDocument.MAX_BYTES,cancelled); input = snapshot.file; }
            DexDocument parsed = DexDocument.read(input, cancelled); EvidenceRows.check(cancelled);
            if (extraction != null && !extraction.sha256.equals(parsed.sha256)) throw new IOException("Extracted DEX content changed before inspection");
            if (snapshot != null && !snapshot.sha256.equals(parsed.sha256)) throw new IOException("Private DEX content changed before inspection");
            if (indexed != null && !indexed.sha256.equals(parsed.sha256)) throw new IOException("Captured definition source SHA-256 changed before selection");
            if (selected != null&&!selectedBorrowed) selected.delete();
            document = parsed; selected = input; selectedMember = member; selectedName = display; selectedBorrowed=borrowed;classifiedStrings.clear();
        } catch (Exception | StackOverflowError error) { if (extraction != null) extraction.file.delete(); if (snapshot != null) snapshot.close(); throw error; }
    }
    @Override EvidenceRows open(String encoded, BooleanSupplier cancelled) throws Exception {
        JSONObject value = new JSONObject(encoded);
        definitionView=false;
        if(value.has("definitionView")) {
            zip=value.getBoolean("archive");int origin=value.optInt("originMember",-2);
            if(document==null&&origin>=-1) select(origin,cancelled);
            DexDefinitionIndex index=definitions(cancelled);definitionView=true;listing=false;String view=value.getString("definitionView");
            if(view.equals("sources")) return index.sourceRows();if(view.equals("classes")) return index.classRows();
            throw new IOException("Unknown definition-index root view");
        }
        if (value.optBoolean("inputs")) {
            zip = true; listing = true;
            inventory(cancelled); List<ArchiveStore.Member> members = new ArrayList<>();
            for (ArchiveStore.Member member : archive.members) if (!member.directory && member.name.toLowerCase(Locale.ROOT).endsWith(".dex")) members.add(member);
            return new EvidenceRows("dex-inputs", "Declared DEX members • " + name) {
                int size() { return members.size(); }
                Row row(int i, BooleanSupplier stop) throws Exception {
                    EvidenceRows.check(stop); ArchiveStore.Member member = members.get(i);
                    return new Row(i, member.name, member.size + " expanded bytes • method=" + member.method + " • occurrences=" + member.occurrences, null, null,
                        new JSONObject().put("memberIndex", member.index).put("memberName", member.name).put("declaredSizeBytes", member.size)
                            .put("compressedSizeBytes", member.compressedSize).put("compressionMethod", member.method).put("crc32", String.format(Locale.ROOT, "%08x", member.crc32))
                            .put("nameOccurrences", member.occurrences).put("canSelect", member.occurrences == 1 && member.size >= 0 && member.size <= DexDocument.MAX_BYTES)
                            .put("interpretation", "Filename-selected candidate. Full directory, expanded size and CRC are checked before a private DEX snapshot is parsed; compressed-member offsets are not parent-file byte offsets."));
                }
                JSONObject metadata() throws Exception { return new JSONObject().put("archiveEntries", archive.members.size()).put("candidateSelection", "Names ending .dex; candidate names alone do not validate format or prove exhaustive code discovery").put("maxDexBytes", DexDocument.MAX_BYTES); }
            };
        }
        int member = value.getInt("member"); if (member < -1) throw new IOException("Invalid DEX input selector");
        if (member >= 0) zip = true;
        select(member, cancelled); listing = false; DexQuery query = DexQuery.parse(value.getJSONObject("dex").toString());
        if (query.entry >= document.entries.size()) throw new IOException("DEX container entry unavailable");
        if(query.kind.equals("definitions")) {
            DexDefinitionIndex index=definitions(cancelled);EvidenceRows rows=DexDefinitionLookup.resolve(index,member,document,query,cancelled);definitionView=true;return rows;
        }
        if (query.kind.equals("classifier-rules")) return StringClassifier.catalog();
        if (query.kind.equals("classified-strings")) {
            DexStringIndex index = classifiedStrings.get(query.entry);
            if (index == null) { index = new DexStringIndex(document.entries.get(query.entry),cancelled); classifiedStrings.put(query.entry,index); }
            return index.rows(query.referenceType,cancelled);
        }
        return document.entries.get(query.entry).rows(query, cancelled);
    }
    @Override List<Link> roots(String encoded, BooleanSupplier cancelled) throws Exception {
        List<Link> result = new ArrayList<>();
        if (zip) result.add(Link.view("Choose a DEX member", inputsRequest()));
        result.add(Link.view("Captured multidex sources / acquisition errors",definitionRoot("sources")));
        result.add(Link.view("Class definitions across all captured DEX inputs",definitionRoot("classes")));
        JSONObject value = new JSONObject(encoded);
        if (!value.optBoolean("inputs")) {
            if(value.has("member")) select(value.getInt("member"), cancelled);
            if(document==null) return result;
            for (int entry = 0; entry < document.entries.size(); entry++) {
                for (int i = 0; i < DexQuery.ROOT_KINDS.length; i++) result.add(Link.view("Entry " + entry + " • " + DexQuery.ROOT_TITLES[i], request(selectedMember, DexQuery.root(entry, DexQuery.ROOT_KINDS[i]))));
                result.add(Link.view("Entry " + entry + " • All classified strings",request(selectedMember,DexQuery.root(entry,"classified-strings"))));
                for (StringClassifier.Group group : StringClassifier.Group.values()) result.add(Link.view("Entry " + entry + " • Strings: " + group.label,request(selectedMember,new DexQuery(entry,"classified-strings",-1,-1,group.ordinal(),-1))));
            }
            result.add(Link.view("String classifier rule catalog",request(selectedMember,DexQuery.root(0,"classifier-rules"))));
        }
        return result;
    }
    @Override List<Link> related(String encoded, EvidenceRows source, EvidenceRows.Row row, BooleanSupplier cancelled) throws Exception {
        List<Link> links = new ArrayList<>(); JSONObject fields = row.json(), value = new JSONObject(encoded);
        if(value.has("definitionView")||value.optJSONObject("dex")!=null&&value.getJSONObject("dex").optString("kind").equals("definitions")) {
            if(fields.has("definitionSourceIndex")) {
                int member=fields.getInt("targetArchiveMemberIndex"),entry=fields.getInt("targetEntryIndex"),owner=fields.getInt("targetClassIndex");
                String kind=fields.getString("targetKind");int target=kind.equals("classes")?owner:fields.getInt("targetDeclarationIndex");
                links.add(Link.jump("Open this exact captured declaration",request(member,new DexQuery(entry,kind,kind.equals("classes")?-1:owner,-1,-1,-1)),target));
                links.add(Link.jump("Owner class definition",request(member,DexQuery.root(entry,"classes")),owner));
                if(kind.equals("classes")) links.add(Link.view("Every captured definition of this exact class",request(member,new DexQuery(entry,"definitions",owner,-1,-1,-1))));
            } else if(fields.has("sourceIndex")) {
                DexDefinitionIndex.Source input=definitions.sources.get(fields.getInt("sourceIndex"));
                if(input.parsed()) links.add(Link.view("Inspect this captured DEX",request(input.member,DexQuery.root(0,"classes"))));
                if(input.file!=null) links.add(Link.blob("Export exact acquired DEX snapshot",new JSONObject().put("definitionBytes",input.index).toString(),"captured-dex-"+input.index+".dex"));
            }
            return links;
        }
        if (value.optBoolean("inputs")) {
            if (fields.getBoolean("canSelect")) links.add(Link.view("Inspect this DEX snapshot", request(fields.getInt("memberIndex"), DexQuery.root(0, "classes"))));
            return links;
        }
        DexQuery query = DexQuery.parse(value.getJSONObject("dex").toString()); int member = value.getInt("member");
        if (query.kind.equals("classes")) {
            int owner = fields.getInt("classIndex");
            links.add(Link.view("Declared methods", request(member, query.child("methods", owner, -1))));
            links.add(Link.view("Declared fields", request(member, query.child("fields", owner, -1))));
            links.add(Link.view("Every exact class declaration across captured DEX inputs",request(member,new DexQuery(query.entry,"definitions",owner,-1,-1,-1))));
        }
        if (fields.has("classIndex") && fields.has("declarationIndex") && !query.kind.equals("fields") || query.owner >= 0 && query.member >= 0) {
            int owner = fields.optInt("classIndex", query.owner), declaration = fields.optInt("declarationIndex", query.member);
            DexQuery code = query.child("code", owner, declaration);
            links.add(Link.jump("Method bytecode", request(member, code), fields.optInt("instructionIndex", 0)));
            links.add(Link.view("Method debug events", request(member, query.child("debug", owner, declaration))));
            links.add(Link.view("Method exception regions", request(member, query.child("tries", owner, declaration))));
            links.add(Link.text("View / export method smali", request(member, code), "dex-" + query.entry + "-" + owner + "-" + declaration + ".smali"));
        }
        if (query.kind.equals("code")) {
            if (fields.has("targetInstructionIndex") && !fields.isNull("targetInstructionIndex")) links.add(Link.jump("Jump to encoded target", encoded, fields.getInt("targetInstructionIndex")));
            if (fields.optString("payloadKind").equals("array")) links.add(Link.view("All array payload elements", request(member, new DexQuery(query.entry, "array", query.owner, query.member, -1, row.index))));
            if (fields.optString("payloadKind").equals("switch")) links.add(Link.view("All switch cases/origins", request(member, new DexQuery(query.entry, "switch", query.owner, query.member, -1, row.index))));
            for (String key : Arrays.asList("reference", "reference2")) if (fields.has(key)) {
                JSONObject reference = fields.getJSONObject(key); referenceLinks(links, member, query, reference.getInt("type"), reference.getInt("index"), key);
            }
        }
        if (query.kind.equals("switch") && !fields.isNull("targetInstructionIndex")) links.add(Link.jump("Target bytecode", request(member, query.child("code", query.owner, query.member)), fields.getInt("targetInstructionIndex")));
        if (query.kind.equals("debug")) codeLocation(links, member, query, fields.getInt("codeUnitOffset"), "Associated bytecode", cancelled);
        if (query.kind.equals("tries")) {
            codeLocation(links, member, query, fields.getInt("startCodeUnits"), "Protected range start", cancelled);
            JSONArray handlers = fields.getJSONArray("handlers");
            for (int i = 0; i < handlers.length(); i++) {
                JSONObject handler = handlers.getJSONObject(i);
                if (!handler.isNull("handlerInstructionIndex")) links.add(Link.jump("Exception handler " + i + " • " + handler.opt("exceptionType"), request(member, query.child("code", query.owner, query.member)), handler.getInt("handlerInstructionIndex")));
            }
        }
        if (fields.has("referenceType") && fields.has("referenceIndex") && !query.kind.equals("xrefs")) referenceLinks(links, member, query, fields.getInt("referenceType"), fields.getInt("referenceIndex"), "Selected reference");
        if (query.kind.equals("methods")) referenceLinks(links, member, query, ReferenceType.METHOD, fields.getInt("methodIndex"), "Method identifier");
        if (query.kind.equals("fields")) referenceLinks(links, member, query, ReferenceType.FIELD, fields.getInt("fieldIndex"), "Field identifier");
        if (query.kind.equals("strings")) referenceLinks(links, member, query, ReferenceType.STRING, fields.getInt("stringIndex"), "String identifier");
        if (query.kind.equals("classified-strings")) {
            referenceLinks(links,member,query,ReferenceType.STRING,fields.getInt("stringIndex"),"String identifier");
            JSONArray groups = fields.getJSONArray("groups");
            for (int i = 0; i < groups.length(); i++) { StringClassifier.Group group = StringClassifier.Group.valueOf(groups.getString(i)); links.add(Link.view("String group: " + group.label,request(member,new DexQuery(query.entry,"classified-strings",-1,-1,group.ordinal(),-1)))); }
        }
        if (query.kind.equals("types")) referenceLinks(links, member, query, ReferenceType.TYPE, fields.getInt("typeIndex"), "Type identifier");
        if(query.kind.equals("method-handles")) {
            int handle=fields.getInt("handleType"),type;
            if(handle>=MethodHandleType.STATIC_PUT&&handle<=MethodHandleType.INSTANCE_GET) type=ReferenceType.FIELD;
            else if(handle>=MethodHandleType.INVOKE_STATIC&&handle<=MethodHandleType.INVOKE_INTERFACE) type=ReferenceType.METHOD;
            else throw new IOException("Unsupported method-handle kind: "+handle);
            referenceLinks(links,member,query,type,fields.getInt("memberIndex"),"Method-handle member");
        }
        return links;
    }
    private void codeLocation(List<Link> links, int member, DexQuery query, int units, String label, BooleanSupplier cancelled) throws Exception {
        DexCode body = new DexCode(document, document.entries.get(query.entry), query.owner, query.member, cancelled);
        int index = body.instructionAt(units);
        if (index >= 0) links.add(Link.jump(label, request(member, query.child("code", query.owner, query.member)), index));
    }
    private static void referenceLinks(List<Link> links, int member, DexQuery current, int type, int index, String label) throws Exception {
        String kind;
        switch (type) {
            case ReferenceType.STRING: kind = "strings"; break;
            case ReferenceType.TYPE: kind = "types"; break;
            case ReferenceType.FIELD: kind = "field-refs"; break;
            case ReferenceType.METHOD: kind = "method-refs"; break;
            case ReferenceType.METHOD_PROTO: kind = "protos"; break;
            case ReferenceType.METHOD_HANDLE: kind = "method-handles"; break;
            case ReferenceType.CALL_SITE: kind = "call-sites"; break;
            default: return;
        }
        links.add(Link.jump(label + " • pool record", request(member, DexQuery.root(current.entry, kind)), index));
        links.add(Link.view(label + " • encoded uses in this entry", request(member, current.references(type, index))));
        if(type==ReferenceType.TYPE||type==ReferenceType.METHOD||type==ReferenceType.FIELD)
            links.add(Link.view(label+" • exact declarations across captured DEX inputs",request(member,current.definitions(type,index))));
    }
    @Override JSONObject provenance() throws Exception {
        if(definitions!=null&&(definitionView||listing)) return definitions.provenance();
        JSONObject parent = new JSONObject().put("name", name).put("sizeBytes", root.length())
            .put("sha256", root.length() == inheritedSize && inheritedSha != null && inheritedSha.matches("[0-9a-fA-F]{64}") ? inheritedSha : JSONObject.NULL)
            .put("identitySource", "Parent inspection if supplied; not rehashed by the archive inventory");
        if(definitions!=null) parent=new JSONObject().put("name",name).put("sizeBytes",definitions.parentSize).put("sha256",definitions.parentSha256)
            .put("identitySource","Fresh complete private definition-parent acquisition; extracted DEX snapshots outlive parent expiry");
        if (document == null || listing) return new JSONObject().put("input", parent).put("engine", "Platform ZIP directory reader").put("networkUsed", false);
        return document.provenance(selectedName).put("parentInput", parent).put("archiveMemberIndex", selectedMember < 0 ? JSONObject.NULL : selectedMember);
    }
    @Override File byteSource() { return definitions!=null&&(definitionView||listing)?definitions.manifest:selected == null || listing ? root : selected; }
    @Override String sourceName() { return definitionView?"Captured multidex definition index":selectedName == null || listing ? name : selectedName; }
    @Override byte[] binaryValue(String encoded,BooleanSupplier stop) throws Exception {
        int index=new JSONObject(encoded).getInt("definitionBytes");DexDefinitionIndex.Source source=definitions(stop).sources.get(index);
        if(source.file==null||source.size<0||source.size>DexDocument.MAX_BYTES) throw new IOException("Acquired DEX snapshot unavailable");
        ByteArrayOutputStream output=new ByteArrayOutputStream();java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(source.file)) {
            byte[] buffer=new byte[65536];int count;
            while((count=input.read(buffer))!=-1) { EvidenceRows.check(stop);if(count>source.size-output.size()) throw new IOException("Captured source length changed");output.write(buffer,0,count);digest.update(buffer,0,count); }
        }
        if(output.size()!=source.size||!Inspector.toHex(digest.digest()).equals(source.sha256)) throw new IOException("Captured DEX bytes differ from acquisition identity");
        return output.toByteArray();
    }
    @Override void writeText(String encoded, Writer output, BooleanSupplier cancelled) throws Exception {
        JSONObject value = new JSONObject(encoded); select(value.getInt("member"), cancelled);
        DexSmali.write(document, DexQuery.parse(value.getJSONObject("dex").toString()), output, cancelled);
    }
    @Override String help(String request) {
        if(definitionView||definitions!=null&&listing) return "Captured multidex declaration information • exact declared owner and full signature; every duplicate is retained. Partial sources/lookup errors leave absence and uniqueness unknown. Target offsets belong to the stated DEX SHA-256, so open its declaration before byte actions. No inheritance, framework/split linkage, dispatch, loading or execution is inferred. Tap for acquisition identity, limits and diagnostics.";
        return "Static DEX information • code-unit addresses count 16-bit units relative to a method; physical offsets locate the selected DEX snapshot/container. Identifier tables can reference external definitions. Tap a record for raw fields, relationships and byte actions. Views select every pool/container entry.";
    }
    @Override public void close() { if (selected != null&&!selectedBorrowed) selected.delete();if(definitions!=null) definitions.close(); }
}

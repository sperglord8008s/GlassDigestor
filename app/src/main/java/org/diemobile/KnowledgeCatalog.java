// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Full active canonical knowledge; matching, metadata and source identity stay separate. */
final class KnowledgeCatalog {
    static final String SOURCE="4189fc0de0a0d67f0e729a5b9699a52e9ff3ffcf";
    final NameMatcher matcher;
    final JSONObject metadata,details;
    final String sha256;
    final boolean sourceTreeDirty;
    final String portableArtifactSha256;
    private KnowledgeCatalog(byte[] bytes,BooleanSupplier stop) throws Exception {
        sha256=Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        JSONObject catalog=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!catalog.getString("schema").equals("org.diemobile.libchecker-catalog")||catalog.getInt("schemaVersion")!=1
            ||!catalog.getString("packaging").equals("local-source-build")) throw new IOException("Canonical library catalog schema/build mode differs");
        metadata=catalog.getJSONObject("metadata");details=catalog.getJSONObject("details");
        sourceTreeDirty=catalog.getBoolean("sourceTreeDirty");portableArtifactSha256=catalog.getString("portableArtifactSha256");
        if(!portableArtifactSha256.matches("[0-9a-f]{64}")) throw new IOException("Canonical upstream artifact identity is missing");
        if(metadata.getInt("schemaVersion")!=5||!metadata.getString("sourceRevision").equals(SOURCE)
            ||!metadata.getString("compilerRevision").matches("[0-9a-f]{64}")||!metadata.getString("contentSha256").matches("[0-9a-f]{64}")) throw new IOException("Canonical library source/compiler/content identity differs");
        matcher=new NameMatcher(catalog.getJSONArray("rules"),stop);
        if(matcher.rules.size()!=metadata.getInt("ruleCount")) throw new IOException("Canonical matcher transfer is incomplete");
        int expectedDetails=0;
        for(NameMatcher.Rule rule:matcher.rules) {
            EvidenceRows.check(stop);JSONObject original=rule.original;
            if(original.isNull("detailPath")) {
                if(details.has(Integer.toString(rule.id))) throw new IOException("Description attached to a no-detail matcher");
            } else {
                expectedDetails++;JSONObject detail=details.getJSONObject(Integer.toString(rule.id));
                if(!original.getString("uuid").equals(detail.getString("uuid"))) throw new IOException("Canonical matcher/detail UUID differs");
                detail.getJSONArray("data");
            }
        }
        if(details.length()!=expectedDetails) throw new IOException("Canonical detail payload inventory differs");
    }
    static KnowledgeCatalog read(InputStream input,BooleanSupplier stop) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[32768];int count;
        while((count=input.read(buffer))!=-1) {
            EvidenceRows.check(stop);
            if(count>8*1024*1024-output.size()) throw new IOException("Canonical library catalog exceeds 8 MiB");
            output.write(buffer,0,count);
        }
        EvidenceRows.check(stop);return new KnowledgeCatalog(output.toByteArray(),stop);
    }
    JSONObject detail(NameMatcher.Rule rule) throws Exception {
        JSONObject value=details.optJSONObject(Integer.toString(rule.id));
        return value==null?new JSONObject():new JSONObject(value.toString());
    }
    JSONObject english(NameMatcher.Rule rule) throws Exception {
        JSONObject value=details.optJSONObject(Integer.toString(rule.id));
        if(value!=null) {
            JSONArray locales=value.getJSONArray("data");
            for(int i=0;i<locales.length();i++) {
                JSONObject locale=locales.getJSONObject(i);
                if(locale.getString("locale").equals("en")) return new JSONObject(locale.getJSONObject("data").toString());
            }
        }
        return new JSONObject();
    }
    String label(NameMatcher.Rule rule) throws Exception {
        String english=english(rule).optString("label","").trim();
        return english.isEmpty()?rule.original.optString("label",rule.name):english;
    }
    JSONObject provenance() throws Exception {
        return new JSONObject(metadata.toString()).put("catalogSha256",sha256).put("sourceTreeDirty",sourceTreeDirty).put("portableArtifactSha256",portableArtifactSha256).put("packaging","local-source-build of pinned canonical v5 source; not an activated online release")
            .put("matchingEngine","complete source-built RE2/J 1.8").put("regexSource","57278921a609461c14d9cdb057d7aa9511c8f7ac")
            .put("unsupportedRegexRules",matcher.unsupported.size()).put("ruleCount",matcher.rules.size())
            .put("contract","Exact equality across all rows first; otherwise first whole-string regex ordered by priority then ID. No case folding or Unicode normalization; regex excludes five line terminators and uses ASCII digits.");
    }
    EvidenceRows rows(int type,boolean errorsOnly) {
        return rows(type,errorsOnly,"");
    }
    EvidenceRows rows(int type,boolean errorsOnly,String uuid) {
        List<NameMatcher.Rule> selected=new ArrayList<>();
        for(NameMatcher.Rule rule:matcher.rules) if((type<0||rule.type==type)&&(!errorsOnly||!rule.error.isEmpty())&&(uuid.isEmpty()||uuid.equals(rule.original.optString("uuid")))) selected.add(rule);
        return new EvidenceRows("sdk-rule-catalog","Canonical LibChecker matchers • "+(errorsOnly?"unsupported regex":type<0?"every type":NameMatcher.displayTypeLabel(type))) {
            int size() { return selected.size(); }
            Row row(int index,BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);NameMatcher.Rule rule=selected.get(index);
                JSONObject fields=new JSONObject(rule.original.toString()).put("originalRuleIndex",rule.index).put("matcherId",rule.id)
                    .put("matcherName",rule.name).put("matchMode",rule.regex?"regex":"exact").put("typeName",NameMatcher.TYPES[rule.type])
                    .put("effectiveDetails",detail(rule)).put("regexSupported",!rule.regex||rule.compiled!=null).put("regexError",rule.error)
                    .put("interpretation","A knowledge rule is a name association, not proof of code identity, SDK invocation, malicious behavior or a vulnerability.");
                return new Row(index,label(rule),rule.name,null,null,fields);
            }
            JSONObject metadata() throws Exception { return provenance().put("selectedType",type).put("errorsOnly",errorsOnly).put("selectedUuid",uuid).put("selectedRuleCount",selected.size()); }
            @Override void writeSelectedRow(Writer output,Row row,BooleanSupplier stop) throws Exception {
                NameMatcher.Rule rule=selected.get(row.index);
                output.write(row.encoded,0,row.encoded.length()-1);output.write(",\"fullOriginalRule\":");
                EvidenceJson.write(output,rule.original,stop);output.write('}');
            }
            @Override boolean matches(Row row,String query,BooleanSupplier stop) throws Exception {
                JSONObject fields=row.json();fields.remove("interpretation");
                return containsDecoded(fields,query,0,new int[]{0},stop);
            }
        };
    }
}

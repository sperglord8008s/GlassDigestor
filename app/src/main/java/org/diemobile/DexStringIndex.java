// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Match exact decoded pool values, preserving MUTF-8 rather than byte heuristics. */
final class DexStringIndex {
    private final DexDocument.Entry entry;
    private final EvidenceRows source;
    private final int[] indices;
    private final long[] masks, counts = new long[StringClassifier.Group.values().length];
    DexStringIndex(DexDocument.Entry entry, BooleanSupplier stop) throws Exception {
        this.entry = entry; source = entry.rows(DexQuery.root(entry.index,"strings"),stop);
        int[] selected = new int[Math.min(source.size(),1024)]; long[] groups = new long[selected.length]; int count = 0;
        for (int i = 0; i < source.size(); i++) {
            EvidenceRows.check(stop); String value = entry.dex.getStringSection().get(i); StringClassifier.Result result = StringClassifier.classify(value,stop);
            if (result.groups == 0) continue;
            if (count == StringDocument.MAX_RECORDS) throw new IOException("DEX classification exceeds 200,000 interesting strings; no successful partial index returned");
            if (count == selected.length) { int capacity = Math.min(StringDocument.MAX_RECORDS,Math.max(16,selected.length*2)); selected = Arrays.copyOf(selected,capacity); groups = Arrays.copyOf(groups,capacity); }
            selected[count] = i; groups[count++] = result.groups;
            for (StringClassifier.Group group : StringClassifier.Group.values()) if ((result.groups & group.bit()) != 0) counts[group.ordinal()]++;
        }
        indices = Arrays.copyOf(selected,count); masks = Arrays.copyOf(groups,count);
    }
    EvidenceRows rows(int groupIndex, BooleanSupplier stop) throws Exception {
        if (groupIndex < -1 || groupIndex >= StringClassifier.Group.values().length) throw new IOException("Unknown DEX string classification group");
        int[] selected = new int[indices.length]; int count = 0;
        for (int i = 0; i < indices.length; i++) { EvidenceRows.check(stop); if (groupIndex < 0 || (masks[i] & StringClassifier.Group.values()[groupIndex].bit()) != 0) selected[count++] = indices[i]; }
        final int[] included = Arrays.copyOf(selected,count); String name = groupIndex < 0 ? "All classified strings" : StringClassifier.Group.values()[groupIndex].label;
        return new EvidenceRows("dex-classified-strings",name + " • DEX entry " + entry.index) {
            int size() { return included.length; }
            Row row(int i, BooleanSupplier cancelled) throws Exception {
                Row original = source.row(included[i],cancelled); JSONObject fields = original.json(); String value = fields.getString("value");
                StringClassifier.Result result = StringClassifier.classify(value,cancelled); long start = fields.getLong("physicalTextOffset");
                fields.put("groups",result.groupNames()).put("categoryRationales",result.categoryRationales())
                    .put("classificationMatches",result.json(value, units -> start + mutf8Bytes(value,units)))
                    .put("classificationMeaning","Matches on exact decoded DEX string values. A pool value does not prove use; explicit encoded uses are a separate bounded scan.");
                return new Row(original.index,original.name,"MUTF-8 • " + result.groupNames()
                        + "\n" + result.summary(groupIndex < 0 ? "interesting" : StringClassifier.Group.values()[groupIndex].name()),original.physicalOffset,original.sizeBytes,fields);
            }
            JSONObject metadata() throws Exception {
                JSONObject groupCounts = new JSONObject(); for (StringClassifier.Group group : StringClassifier.Group.values()) groupCounts.put(group.name(),counts[group.ordinal()]);
                return entry.metadata().put("classifier",StringClassifier.provenance()).put("stringPoolRecords",source.size()).put("interestingPoolRecords",indices.length)
                    .put("groupCounts",groupCounts).put("selectedGroup",groupIndex < 0 ? "interesting" : StringClassifier.Group.values()[groupIndex].name())
                    .put("selectedGroupRecords",included.length).put("scope","Every decoded string-pool value in this entry, including embedded NULs and isolated UTF-16 surrogates; not external strings, reflected targets or other entries/splits");
            }
        };
    }
    private static long mutf8Bytes(String value, int units) {
        long bytes = 0;
        for (int i = 0; i < units; i++) { char c = value.charAt(i); bytes += c != 0 && c <= 0x7f ? 1 : c <= 0x7ff ? 2 : 3; }
        return bytes;
    }
}

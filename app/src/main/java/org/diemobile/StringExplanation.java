// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Reads captured classifier evidence. It never reclassifies text or alters the source row. */
final class StringExplanation {
    final boolean catalog;
    final String value, selectedGroup;
    private final JSONArray matches;
    private final JSONArray categoryRationales;
    private final List<Integer> order = new ArrayList<>();

    private StringExplanation(JSONObject fields, String selectedGroup, boolean catalog)
            throws JSONException {
        this.catalog = catalog;
        this.value = catalog ? "" : fields.getString("value");
        this.selectedGroup = selectedGroup;
        categoryRationales = fields.optJSONArray("categoryRationales");
        matches =
                catalog
                        ? new JSONArray().put(fields)
                        : fields.getJSONArray("classificationMatches");
        if (matches.length() > StringClassifier.MAX_MATCHES)
            throw new JSONException("Captured classification exceeds the match limit");
        // Prioritize the category the user opened, retaining every other occurrence and raw
        // ordinal.
        for (int i = 0; i < matches.length(); i++)
            if (contains(matches.getJSONObject(i).optJSONArray("groups"), selectedGroup))
                order.add(i);
        for (int i = 0; i < matches.length(); i++)
            if (!contains(matches.getJSONObject(i).optJSONArray("groups"), selectedGroup))
                order.add(i);
    }

    static StringExplanation read(EvidenceRows.Row row, String kind, String selectedGroup)
            throws JSONException {
        JSONObject fields = row.json();
        if (fields.optJSONArray("classificationMatches") != null
                && fields.opt("value") instanceof String)
            return new StringExplanation(fields, selectedGroup, false);
        if (kind.equals("string-rules") && fields.has("ruleId") && fields.has("expression"))
            return new StringExplanation(fields, selectedGroup, true);
        return null;
    }

    int size() {
        return order.size();
    }

    int originalIndex(int position) {
        return order.get(position);
    }

    JSONObject match(int position) throws JSONException {
        return new JSONObject(matches.getJSONObject(order.get(position)).toString());
    }

    String exactMatch(JSONObject match) {
        if (catalog) return null;
        int start = match.optInt("startUtf16", -1), end = match.optInt("endUtf16", -1);
        return start >= 0 && end > start && end <= value.length()
                ? value.substring(start, end)
                : null;
    }

    JSONArray rationales(JSONObject match) throws JSONException {
        JSONArray retained = match.optJSONArray("categoryRationales");
        if (retained != null) return new JSONArray(retained.toString());
        JSONArray result = new JSONArray();
        if (categoryRationales != null)
            for (int i = 0; i < categoryRationales.length(); i++) {
                JSONObject context = categoryRationales.getJSONObject(i);
                if (contains(match.optJSONArray("groups"), context.getString("group")))
                    result.put(new JSONObject(context.toString()));
            }
        return result;
    }

    static boolean contains(JSONArray array, String value) {
        if (array == null || value == null) return false;
        for (int i = 0; i < array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }

    static String label(String group) {
        try {
            return StringClassifier.Group.valueOf(group).label;
        } catch (IllegalArgumentException unknown) {
            return group;
        }
    }

    static String labels(JSONArray groups) {
        List<String> labels = new ArrayList<>();
        if (groups != null)
            for (int i = 0; i < groups.length(); i++) labels.add(label(groups.optString(i)));
        return labels.isEmpty() ? "Unclassified candidate" : String.join(" · ", labels);
    }

    String summary() throws JSONException {
        if (size() == 0)
            return "No built-in rule matched this candidate; this is not a safety finding.";
        JSONObject first = match(0);
        return first.getString("title") + " — " + first.getString("observation");
    }
}

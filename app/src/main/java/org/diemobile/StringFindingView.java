// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Human-readable classifier witnesses, with bounded match pages and original evidence retained. */
final class StringFindingView {
    static final int PAGE_SIZE = 8;

    private StringFindingView() {}

    static void add(
            Activity activity,
            LinearLayout parent,
            EvidenceRows.Row row,
            StringExplanation explanation,
            ExpandableHelp help)
            throws Exception {
        JSONObject fields = row.json();
        parent.addView(
                WorkbenchUi.section(
                        activity, explanation.catalog ? "Classification rule" : "Captured string"));
        if (!explanation.catalog) {
            TextView value =
                    WorkbenchUi.prose(activity, WorkbenchUi.preview(explanation.value, 320));
            value.setTypeface(Typeface.MONOSPACE);
            value.setTag("string-captured-value");
            parent.addView(value);
            actions(
                    activity,
                    parent,
                    explanation.value,
                    "Complete captured string",
                    "string-value");
        }
        parent.addView(
                WorkbenchUi.fact(
                        activity,
                        "Basis",
                        explanation.catalog
                                ? "Rule definition; no file match is being claimed."
                                : "Offline text match; execution, evasion and vulnerabilities are"
                                      + " not verified.",
                        false));
        if (explanation.size() == 0) {
            parent.addView(WorkbenchUi.prose(activity, explanation.summary()));
            sourceFacts(activity, parent, row, fields, explanation.catalog);
            return;
        }
        LinearLayout navigation = new LinearLayout(activity);
        Button previous = button(activity, "Previous matches", () -> {}),
                next = button(activity, "Next matches", () -> {});
        previous.setTag("string-matches-previous");
        next.setTag("string-matches-next");
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(navigation);
        TextView count = WorkbenchUi.text(activity, "", 12);
        count.setTag("string-match-count");
        parent.addView(count);
        LinearLayout matches = new LinearLayout(activity);
        matches.setOrientation(LinearLayout.VERTICAL);
        parent.addView(matches);
        int[] page = {0};
        Runnable render =
                () -> {
                    matches.removeAllViews();
                    int from = page[0] * PAGE_SIZE,
                            end = Math.min(explanation.size(), from + PAGE_SIZE);
                    count.setText(
                            "Matches "
                                    + (from + 1)
                                    + "–"
                                    + end
                                    + " of "
                                    + explanation.size()
                                    + (explanation.catalog
                                            ? " · rule definition"
                                            : " · all occurrences retained")
                                    + (StringExplanation.contains(
                                                    fields.optJSONArray("groups"),
                                                    explanation.selectedGroup)
                                            ? "\nOpened category first: "
                                                    + StringExplanation.label(
                                                            explanation.selectedGroup)
                                            : ""));
                    previous.setEnabled(page[0] > 0);
                    next.setEnabled(end < explanation.size());
                    previous.setVisibility(
                            explanation.size() > PAGE_SIZE
                                    ? android.view.View.VISIBLE
                                    : android.view.View.GONE);
                    next.setVisibility(
                            explanation.size() > PAGE_SIZE
                                    ? android.view.View.VISIBLE
                                    : android.view.View.GONE);
                    for (int i = from; i < end; i++) {
                        try {
                            match(activity, matches, explanation, i, help);
                        } catch (Exception error) {
                            matches.addView(
                                    WorkbenchUi.prose(
                                            activity,
                                            "This captured match cannot be formatted: "
                                                    + error.getMessage()
                                                    + ". Its unchanged original fields remain below"
                                                    + " and in the export."));
                        }
                    }
                };
        previous.setOnClickListener(
                view -> {
                    if (page[0] > 0) {
                        page[0]--;
                        render.run();
                    }
                });
        next.setOnClickListener(
                view -> {
                    if ((page[0] + 1) * PAGE_SIZE < explanation.size()) {
                        page[0]++;
                        render.run();
                    }
                });
        render.run();
        sourceFacts(activity, parent, row, fields, explanation.catalog);
    }

    private static void sourceFacts(
            Activity activity,
            LinearLayout parent,
            EvidenceRows.Row row,
            JSONObject fields,
            boolean catalog)
            throws Exception {
        if (catalog) return;
        parent.addView(WorkbenchUi.section(activity, "Captured source / decoding"));
        parent.addView(
                WorkbenchUi.fact(
                        activity,
                        "All categories",
                        StringExplanation.labels(fields.optJSONArray("groups")),
                        false));
        if (fields.has("encoding"))
            parent.addView(
                    WorkbenchUi.fact(activity, "Encoding", fields.getString("encoding"), true));
        parent.addView(
                WorkbenchUi.fact(
                        activity,
                        "Physical text offset",
                        NativeTable.hexOffset(row.physicalOffset),
                        true));
        parent.addView(
                WorkbenchUi.fact(
                        activity,
                        "Stored bytes",
                        row.sizeBytes == null ? "unknown" : row.sizeBytes.toString(),
                        true));
    }

    private static void match(
            Activity activity,
            LinearLayout parent,
            StringExplanation explanation,
            int position,
            ExpandableHelp help)
            throws Exception {
        JSONObject match = explanation.match(position);
        int ordinal = explanation.originalIndex(position);
        WorkbenchUi.divider(activity, parent);
        parent.addView(
                WorkbenchUi.section(
                        activity,
                        (explanation.catalog ? "" : "#" + ordinal + " · ")
                                + match.getString("title")));
        String exact = explanation.exactMatch(match);
        if (!explanation.catalog) {
            String witness =
                    exact != null
                            ? exact
                            : match.optString("matchedTextPreview", "No retained text preview");
            TextView displayed =
                    WorkbenchUi.text(
                            activity,
                            new WorkbenchUi.Lines(activity)
                                    .witness("Matched text", WorkbenchUi.preview(witness, 512))
                                    .build(),
                            14);
            displayed.setTextIsSelectable(true);
            displayed.setTag("string-match-value-" + ordinal);
            parent.addView(displayed);
        }
        parent.addView(WorkbenchUi.section(activity, "Why this category"));
        TextView why = WorkbenchUi.prose(activity, match.getString("observation"));
        why.setTag("string-why-" + ordinal);
        parent.addView(why);
        if (exact != null)
            actions(
                    activity,
                    parent,
                    exact,
                    "Exact matched text · " + match.getString("ruleId"),
                    "string-match-" + ordinal);
        parent.addView(WorkbenchUi.section(activity, "Interpretation / next checks"));
        parent.addView(WorkbenchUi.prose(activity, match.getString("implicationsAndNextStep")));
        parent.addView(WorkbenchUi.fact(activity, "Rule", match.getString("ruleId"), true));
        parent.addView(
                WorkbenchUi.fact(
                        activity,
                        "Categories",
                        StringExplanation.labels(match.optJSONArray("groups")),
                        false));
        if (!explanation.catalog) {
            parent.addView(
                    WorkbenchUi.fact(
                            activity,
                            "UTF-16 span",
                            "["
                                    + match.opt("startUtf16")
                                    + ", "
                                    + match.opt("endUtf16")
                                    + ") in the captured string",
                            true));
            parent.addView(
                    WorkbenchUi.fact(
                            activity,
                            "Matched source bytes",
                            match.isNull("physicalMatchOffset")
                                    ? "unknown"
                                    : "offset "
                                            + NativeTable.hexOffset(
                                                    match.getLong("physicalMatchOffset"))
                                            + " · length "
                                            + match.opt("physicalMatchLengthBytes"),
                            true));
        }
        JSONArray rationales = explanation.rationales(match);
        for (int i = 0; i < rationales.length(); i++) {
            JSONObject reason = rationales.getJSONObject(i);
            help.paragraph(
                    activity,
                    parent,
                    "string.match."
                            + match.getString("ruleId")
                            + "."
                            + ordinal
                            + ".group."
                            + reason.getString("group"),
                    "Category meaning · " + reason.getString("label"),
                    reason.getString("reason"),
                    true,
                    false);
        }
        help.paragraph(
                activity,
                parent,
                "string.rule." + match.getString("ruleId"),
                "Matching expression / source",
                match.getString("expression")
                        + "\n\n"
                        + match.optString("matchMethod")
                        + "\n"
                        + match.optString("source")
                        + " · "
                        + match.optString("license"),
                true,
                false);
    }

    private static Button button(Activity activity, String label, Runnable action) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private static void actions(
            Activity activity, LinearLayout parent, String value, String scope, String tag) {
        TextActions actions = new TextActions(activity);
        LinearLayout buttons = new LinearLayout(activity);
        Button copy = button(activity, "Copy", () -> actions.copy(value));
        Button search = button(activity, "Search…", () -> actions.choose(value, scope, false));
        Button translate = button(activity, "Translate…", () -> actions.choose(value, scope, true));
        copy.setContentDescription("Copy " + scope);
        search.setContentDescription("Search " + scope + " with a chosen engine");
        translate.setContentDescription("Translate " + scope + " to English with a chosen engine");
        copy.setTag(tag + "-copy");
        search.setTag(tag + "-search");
        translate.setTag(tag + "-translate");
        for (Button button : new Button[] {copy, search, translate})
            buttons.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(buttons);
    }
}

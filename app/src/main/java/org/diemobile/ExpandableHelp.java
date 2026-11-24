// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;

import java.lang.ref.WeakReference;
import java.util.*;

/** Raw evidence stays visible; optional definitions expand individually or across the workbench. */
final class ExpandableHelp {
    private static final String DEFAULT = "definitions-expanded";
    private final SharedPreferences preferences;
    private final List<Binding> bindings = new ArrayList<>();

    private static final class Binding {
        final String key, title;
        final boolean definition, initial;
        final WeakReference<Button> toggle;
        final WeakReference<View> body;

        Binding(
                String key,
                String title,
                boolean definition,
                boolean initial,
                Button toggle,
                View body) {
            this.key = key;
            this.title = title;
            this.definition = definition;
            this.initial = initial;
            this.toggle = new WeakReference<>(toggle);
            this.body = new WeakReference<>(body);
        }
    }

    ExpandableHelp(Context context) {
        preferences =
                context.getApplicationContext()
                        .getSharedPreferences("workbench-presentation", Context.MODE_PRIVATE);
    }

    void clearBindings() {
        bindings.clear();
    }

    void bind(
            String key,
            String title,
            Button toggle,
            View body,
            boolean definition,
            boolean initial) {
        Binding binding =
                new Binding(
                        "section." + (definition ? "definition." : "") + key,
                        title,
                        definition,
                        initial,
                        toggle,
                        body);
        bindings.removeIf(
                old ->
                        old.toggle.get() == null
                                || old.body.get() == null
                                || old.body.get() == body);
        bindings.add(binding);
        toggle.setAllCaps(false);
        toggle.setOnClickListener(
                view -> {
                    boolean expanded = body.getVisibility() == View.VISIBLE;
                    preferences.edit().putBoolean(binding.key, !expanded).apply();
                    apply(binding);
                });
        apply(binding);
    }

    private void apply(Binding binding) {
        Button button = binding.toggle.get();
        View body = binding.body.get();
        if (button == null || body == null) return;
        boolean fallback =
                binding.definition ? preferences.getBoolean(DEFAULT, false) : binding.initial;
        boolean expanded = preferences.getBoolean(binding.key, fallback);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        button.setText((expanded ? "▾ " : "▸ ") + binding.title);
        button.setContentDescription((expanded ? "Collapse " : "Expand ") + binding.title);
    }

    void refresh() {
        for (Binding binding : bindings) apply(binding);
    }

    void setAll(boolean expanded) {
        SharedPreferences.Editor editor = preferences.edit().putBoolean(DEFAULT, expanded);
        // Only definitions follow this operation. Tool groups and observed facts
        // retain their own state and never disappear behind a help-only switch.
        for (String key : preferences.getAll().keySet())
            if (key.startsWith("section.definition.")) editor.remove(key);
        for (Binding binding : bindings) if (binding.definition) editor.remove(binding.key);
        editor.apply();
        refresh();
    }

    LinearLayout controls(Context context) {
        LinearLayout row = new LinearLayout(context);
        for (boolean expanded : new boolean[] {false, true}) {
            Button button = new Button(context);
            button.setText(expanded ? "Expand explanations" : "Collapse explanations");
            button.setTextSize(12);
            button.setAllCaps(false);
            button.setTag(expanded ? "expand-explanations" : "collapse-explanations");
            button.setOnClickListener(view -> setAll(expanded));
            row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return row;
    }

    void paragraph(
            Context context,
            LinearLayout parent,
            String key,
            String title,
            String text,
            boolean definition,
            boolean initial) {
        Button toggle = new Button(context);
        toggle.setTextSize(12);
        TextView body = WorkbenchUi.prose(context, text);
        int pad = Math.round(8 * context.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        body.setTag("explanation:" + key);
        parent.addView(toggle);
        parent.addView(body);
        bind(key, title, toggle, body, definition, initial);
    }
}

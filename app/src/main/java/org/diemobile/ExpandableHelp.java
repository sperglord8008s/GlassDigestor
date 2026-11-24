// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.lang.ref.WeakReference;
import java.util.*;

/** Observed facts stay visible; optional definitions expand individually or across the workbench. */
final class ExpandableHelp {
    static final int EXPAND_ALL = 1, COLLAPSE_ALL = 2;
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
        compact(toggle.getContext(), toggle);
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
        boolean inline =
                button.getParent() instanceof LinearLayout
                        && ((LinearLayout) button.getParent()).getOrientation()
                                == LinearLayout.HORIZONTAL;
        button.setText((expanded ? "▾" : "▸") + (inline ? "" : " " + binding.title));
        button.setContentDescription((expanded ? "Collapse " : "Expand ") + binding.title);
        if (Build.VERSION.SDK_INT >= 30)
            button.setStateDescription(expanded ? "Expanded" : "Collapsed");
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
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button button = new Button(context);
        compact(context, button);
        button.setText("⋮");
        button.setTextSize(22);
        button.setGravity(Gravity.CENTER);
        button.setTag("explanation-options");
        button.setContentDescription("Explanation options");
        button.setTooltipText("Expand or collapse explanations");
        button.setOnClickListener(
                view -> {
                    PopupMenu menu = new PopupMenu(context, button);
                    menu.getMenu().add(0, EXPAND_ALL, 0, "Expand explanations");
                    menu.getMenu().add(0, COLLAPSE_ALL, 1, "Collapse explanations");
                    menu.setOnMenuItemClickListener(
                            item -> {
                                setAll(item.getItemId() == EXPAND_ALL);
                                return true;
                            });
                    menu.show();
                });
        int size = WorkbenchUi.dp(context, 40);
        row.addView(button, new LinearLayout.LayoutParams(size, size));
        row.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private static void compact(Context context, Button button) {
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(context.getColor(R.color.workbench_secondary));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(WorkbenchUi.dp(context, 40));
        button.setMinimumHeight(WorkbenchUi.dp(context, 40));
        button.setPadding(WorkbenchUi.dp(context, 6), 0, WorkbenchUi.dp(context, 6), 0);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setElevation(0);
        TypedValue background = new TypedValue();
        if (context.getTheme()
                .resolveAttribute(android.R.attr.selectableItemBackground, background, true))
            button.setBackgroundResource(background.resourceId);
        ViewGroup.LayoutParams layout = button.getLayoutParams();
        if (layout != null && layout.width == ViewGroup.LayoutParams.MATCH_PARENT) {
            layout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            button.setLayoutParams(layout);
        }
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
        parent.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(body);
        bind(key, title, toggle, body, definition, initial);
    }
}

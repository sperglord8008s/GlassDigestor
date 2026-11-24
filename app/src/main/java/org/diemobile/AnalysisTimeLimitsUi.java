// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

/** Next-request settings stay separate from already captured evidence. */
final class AnalysisTimeLimitsUi {
    private static final int[] PRESETS = {30, 60, 120, 300, 600};

    private AnalysisTimeLimitsUi() {}

    static void show(Activity activity, String engine, Runnable onChanged) {
        int current = AnalysisTimeLimits.seconds(activity, engine);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = WorkbenchUi.dp(activity, 20);
        body.setPadding(padding, WorkbenchUi.dp(activity, 8), padding, 0);
        body.addView(
                WorkbenchUi.prose(
                        activity,
                        "New Binwalk requests use this analysis limit. The isolated worker allows"
                            + " another 30 seconds for startup and report transfer. A native parser"
                            + " callback can run until the worker deadline. Cancellation remains"
                            + " available."));
        Spinner choices = new Spinner(activity);
        choices.setTag("analysis-time-preset");
        choices.setContentDescription("Binwalk analysis time preset");
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_spinner_item,
                        new String[] {
                            "30 seconds",
                            "1 minute",
                            "2 minutes (default)",
                            "5 minutes",
                            "10 minutes",
                            "Custom seconds"
                        });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        choices.setAdapter(adapter);
        body.addView(choices);
        body.addView(WorkbenchUi.text(activity, "Analysis seconds (1–1800)", 13));
        EditText seconds = new EditText(activity);
        seconds.setTag("analysis-time-seconds");
        seconds.setContentDescription("Analysis seconds, from 1 to 1800");
        seconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        seconds.setSingleLine(true);
        seconds.setText(Integer.toString(current));
        body.addView(seconds);
        int selected = PRESETS.length;
        for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i] == current) selected = i;
        choices.setSelection(selected);
        choices.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        if (position < PRESETS.length)
                            seconds.setText(Integer.toString(PRESETS[position]));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
        ScrollView scroll = WorkbenchUi.scroll(activity);
        scroll.addView(body);
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle("Binwalk time limit")
                        .setView(scroll)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save for next request", null)
                        .create();
        dialog.setOnShowListener(
                ignored ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        view -> {
                                            try {
                                                String text = seconds.getText().toString().trim();
                                                if (!text.matches("[0-9]{1,4}"))
                                                    throw new IllegalArgumentException(
                                                            "Enter whole seconds from 1 to 1800");
                                                AnalysisTimeLimits.save(
                                                        activity, engine, Integer.parseInt(text));
                                                dialog.dismiss();
                                                if (onChanged != null) onChanged.run();
                                            } catch (IllegalArgumentException invalid) {
                                                seconds.setError(
                                                        "Enter whole seconds from 1 to 1800");
                                            }
                                        }));
        dialog.show();
    }
}

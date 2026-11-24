// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Reads a staged full UTF-8 report on the existing worker, retaining only one displayed page. */
final class PagedTextDialog {
    private final Activity owner;
    private final Executor worker;
    private final File file;
    private final long size;
    private final BooleanSupplier externalStop;
    private final Runnable firstPageReady;
    private boolean readyAnnounced;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Utf8Pages.Cursor cursor = new Utf8Pages.Cursor();
    private volatile int generation;
    private Utf8Pages.Page shown;
    private TextView position, body;
    private Button first, back, next, tail, jump;
    private ScrollView scroll;

    static void show(
            Activity owner,
            Executor worker,
            String title,
            File file,
            long size,
            BooleanSupplier stop,
            Runnable ready,
            Runnable export,
            Runnable dismissed) {
        new PagedTextDialog(owner, worker, file, size, stop, ready).open(title, export, dismissed);
    }

    private PagedTextDialog(
            Activity owner,
            Executor worker,
            File file,
            long size,
            BooleanSupplier stop,
            Runnable ready) {
        this.owner = owner;
        this.worker = worker;
        this.file = file;
        this.size = size;
        externalStop = stop;
        firstPageReady = ready;
    }

    private int dp(int value) {
        return Math.round(value * owner.getResources().getDisplayMetrics().density);
    }

    private Button button(LinearLayout row, String label, String tag, Runnable action) {
        Button view = new Button(owner);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(12);
        view.setTag(tag);
        view.setOnClickListener(v -> action.run());
        row.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
        return view;
    }

    private void open(String title, Runnable export, Runnable dismissed) {
        LinearLayout root = new LinearLayout(owner);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(4), dp(10), dp(4));
        position = new TextView(owner);
        position.setTag("text-page-position");
        position.setTextIsSelectable(true);
        position.setTextSize(12);
        root.addView(position);
        LinearLayout controls = new LinearLayout(owner);
        root.addView(controls);
        first = button(controls, "First", "text-page-first", () -> load(0, false, false));
        back =
                button(
                        controls,
                        "Back",
                        "text-page-back",
                        () -> load(cursor.backTarget(), false, true));
        next =
                button(
                        controls,
                        "Next",
                        "text-page-next",
                        () -> {
                            if (shown != null) load(shown.end, false, false);
                        });
        tail = button(controls, "Tail", "text-page-tail", () -> load(0, true, false));
        jump = button(controls, "Byte…", "text-page-jump", this::jump);
        body = new TextView(owner);
        body.setTag("text-page-body");
        body.setTextSize(12);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        HorizontalScrollView horizontal = new HorizontalScrollView(owner);
        horizontal.addView(body);
        scroll =
                new DraggableScrollView(owner) {
                    @Override
                    protected void onMeasure(int width, int height) {
                        int screen = owner.getResources().getConfiguration().screenHeightDp;
                        int maximum =
                                dp(screen > 0 ? Math.max(48, Math.min(360, screen - 210)) : 360);
                        if (View.MeasureSpec.getMode(height) != View.MeasureSpec.UNSPECIFIED)
                            maximum = Math.min(maximum, View.MeasureSpec.getSize(height));
                        super.onMeasure(
                                width,
                                View.MeasureSpec.makeMeasureSpec(
                                        maximum, View.MeasureSpec.AT_MOST));
                    }
                };
        scroll.addView(horizontal);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog =
                new AlertDialog.Builder(owner)
                        .setTitle(title)
                        .setView(root)
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Export complete text", (d, w) -> export.run())
                        .create();
        dialog.setOnDismissListener(
                d -> {
                    closed.set(true);
                    generation++;
                    dismissed.run();
                });
        dialog.show();
        load(0, false, false);
    }

    private void enabled(boolean value) {
        first.setEnabled(value && cursor.offset != 0);
        back.setEnabled(value && cursor.canBack());
        next.setEnabled(value && shown != null && shown.more());
        tail.setEnabled(value && size > 0);
        jump.setEnabled(value);
    }

    private void load(long offset, boolean last, boolean backwards) {
        int ticket = ++generation;
        enabled(false);
        position.setText("Reading a bounded UTF-8 page…");
        worker.execute(
                () -> {
                    BooleanSupplier stop =
                            () ->
                                    closed.get()
                                            || owner.isDestroyed()
                                            || externalStop.getAsBoolean()
                                            || ticket != generation;
                    try {
                        Utf8Pages.Page page =
                                last
                                        ? Utf8Pages.tail(file, size, Utf8Pages.CHARACTERS, stop)
                                        : Utf8Pages.read(
                                                file, size, offset, Utf8Pages.CHARACTERS, stop);
                        owner.runOnUiThread(
                                () -> {
                                    if (stop.getAsBoolean()) return;
                                    if (backwards) cursor.back();
                                    else cursor.move(page.offset);
                                    shown = page;
                                    body.setText(page.text);
                                    scroll.scrollTo(0, 0);
                                    position.setText(
                                            "UTF-8 bytes ["
                                                    + page.offset
                                                    + ", "
                                                    + page.end
                                                    + ") / "
                                                    + size
                                                    + " · "
                                                    + page.text.length()
                                                    + " UTF-16 units · full export retained"
                                                    + (cursor.expired
                                                            ? " · older visited positions available"
                                                                    + " by byte offset"
                                                            : ""));
                                    enabled(true);
                                    announceReady();
                                });
                    } catch (Exception failure) {
                        owner.runOnUiThread(
                                () -> {
                                    if (stop.getAsBoolean()) return;
                                    position.setText(
                                            "Page unavailable: "
                                                    + failure.getMessage()
                                                    + (shown == null
                                                            ? ""
                                                            : " · position unchanged at byte "
                                                                    + shown.offset));
                                    enabled(true);
                                    announceReady();
                                });
                    }
                });
    }

    private void announceReady() {
        if (!readyAnnounced) {
            readyAnnounced = true;
            firstPageReady.run();
        }
    }

    private void jump() {
        EditText value = new EditText(owner);
        value.setSingleLine();
        value.setTag("text-page-byte-offset");
        value.setText("" + cursor.offset);
        value.setHint("Decimal or 0x byte offset; UTF-8 boundary required");
        new AlertDialog.Builder(owner)
                .setTitle("UTF-8 byte offset")
                .setView(value)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Open",
                        (d, w) -> {
                            try {
                                String text = value.getText().toString().trim();
                                long offset =
                                        text.startsWith("0x") || text.startsWith("0X")
                                                ? Long.parseLong(text.substring(2), 16)
                                                : Long.parseLong(text, 10);
                                if (offset < 0 || offset > size)
                                    throw new IllegalArgumentException(
                                            "Offset is outside the staged text");
                                load(offset, false, false);
                            } catch (RuntimeException failure) {
                                position.setText("Invalid text position: " + failure.getMessage());
                            }
                        })
                .show();
    }
}

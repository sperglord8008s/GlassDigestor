// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One archive gallery page. Decoded bytes are checked, private, short-lived and never paths. */
final class NativeArchiveGalleryAdapter extends BaseAdapter implements AutoCloseable {
    static final long MAX_MEMBER_PREVIEW = 32L * 1024 * 1024;
    static final long MAX_PAGE_PAYLOAD = 96L * 1024 * 1024;
    static final long MAX_PAGE_SOURCE_SIZES = 512L * 1024 * 1024;
    private final Context context;
    private final List<ArchiveListing.Entry> entries;
    private final NativeArchiveStore.Inventory inventory;
    private final ArchiveRunner runner;
    private final File directory;
    private final Executor worker;
    private final Consumer<NativeArchiveStore.Member> save, browse;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, Observation> observations = new HashMap<>();
    private final Set<Integer> pending = new HashSet<>();
    private long payloadBudget, sourceSizes;
    private volatile boolean closed;

    private static final class Observation {
        Bitmap bitmap;
        String text = "", status;
        boolean archive;

        Observation(String status) {
            this.status = status;
        }
    }

    NativeArchiveGalleryAdapter(
            Context context,
            List<ArchiveListing.Entry> entries,
            NativeArchiveStore.Inventory inventory,
            ArchiveRunner runner,
            File directory,
            Executor worker,
            Consumer<NativeArchiveStore.Member> save,
            Consumer<NativeArchiveStore.Member> browse) {
        if (entries.size() > 24)
            throw new IllegalArgumentException("Archive gallery exceeds 24 cards");
        this.context = context;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.inventory = inventory;
        this.runner = runner;
        this.directory = directory;
        this.worker = worker;
        this.save = save;
        this.browse = browse;
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public ArchiveListing.Entry getItem(int index) {
        return entries.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        return text;
    }

    @Override
    public View getView(int index, View recycled, ViewGroup parent) {
        ArchiveListing.Entry entry = entries.get(index);
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView title = text(entry.label, 13);
        title.setMaxLines(5);
        tile.addView(title);
        if (entry.member == null) {
            tile.addView(text("Tap to open folder", 12));
            return tile;
        }
        NativeArchiveStore.Member member = entry.member;
        boolean allowed = inventory.complete && member.canAcquire();
        Observation observation = observations.get(index);
        if (observation == null && !closed && pending.add(index)) request(index, member);
        if (observation != null && observation.bitmap != null) {
            ImageView image = new ImageView(context);
            image.setTag("archive-thumbnail:" + member.index);
            image.setContentDescription(member.name);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageBitmap(observation.bitmap);
            tile.addView(image, new LinearLayout.LayoutParams(-1, dp(112)));
        }
        if (observation != null && !observation.text.isEmpty()) {
            TextView text = text(observation.text, 12);
            text.setMaxLines(5);
            tile.addView(text);
        }
        TextView status =
                text(observation == null ? "Acquiring checked preview…" : observation.status, 11);
        status.setTag("archive-preview-status:" + member.index);
        tile.addView(status);
        if (allowed) {
            Button export = button("Save file", () -> save.accept(member));
            export.setTag("save-archive-file:" + member.index);
            tile.addView(export);
            if (observation != null && observation.archive)
                tile.addView(button("Browse archive", () -> browse.accept(member)));
            tile.addView(text("Tap to preview · hold for full evidence and tools", 11));
        }
        return tile;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setOnClickListener(
                view -> {
                    if (!closed) action.run();
                });
        return button;
    }

    private void request(int index, NativeArchiveStore.Member member) {
        worker.execute(
                () -> {
                    if (closed) return;
                    Observation result = null;
                    try {
                        if (!inventory.complete || !member.canAcquire())
                            throw new IOException(
                                    "No automatic extraction: incomplete inventory, link,"
                                            + " special type, encryption or acquisition limit");
                        if (member.size == null || member.size > MAX_MEMBER_PREVIEW)
                            throw new IOException(
                                    "Automatic previews need a declared size ≤32 MiB;"
                                            + " tap to acquire the complete file explicitly");
                        if (member.size > MAX_PAGE_PAYLOAD - payloadBudget
                                || inventory.source.size > MAX_PAGE_SOURCE_SIZES - sourceSizes)
                            throw new IOException(
                                    "Automatic page extraction budget reached; open or save"
                                            + " this file explicitly");
                        payloadBudget += member.size;
                        sourceSizes += inventory.source.size;
                        try (NativeArchiveStore.Acquired acquired =
                                NativeArchiveStore.acquire(
                                        inventory, member, directory, runner, () -> closed)) {
                            InputSnapshot input = acquired.input;
                            byte[] header = ArchiveFiles.header(input);
                            String type = Inspector.identify(header, header.length);
                            String lower = type.toLowerCase(Locale.ROOT);
                            result =
                                    new Observation(
                                            type
                                                    + " · checked "
                                                    + input.size
                                                    + " logical bytes"
                                                    + "\nSHA-256 "
                                                    + input.sha256);
                            FileRange range = new FileRange(input.size, 0, input.size);
                            result.archive =
                                    lower.contains("archive")
                                            || lower.contains("compressed")
                                            || lower.contains("zip container");
                            if (lower.contains("image") || type.equals("PNG")) {
                                result.bitmap = ContentPreview.image(input.file, range, 256);
                                result.status += "\nPlatform decoded first frame";
                            } else if (lower.contains("audio") || lower.contains("media")) {
                                MediaFrame.Result media =
                                        MediaFrame.read(input, range, 256, () -> closed);
                                result.bitmap = media.bitmap;
                                result.status += "\n" + media.description();
                            } else if (type.equals("PDF")) {
                                PdfPagePreview.Result document =
                                        PdfPagePreview.read(input, 0, 256, () -> closed);
                                result.bitmap = document.bitmap;
                                result.status += "\n" + document.description();
                            } else if (ContentPreview.looksLikeText(
                                    header, header.length == input.size)) {
                                result.text =
                                        Utf8Pages.read(input.file, input.size, 0, 512, () -> closed)
                                                .text;
                                result.status +=
                                        "\nUTF-8 prefix preview · tap for complete paged text";
                            } else if (result.archive)
                                result.status += "\nBrowse to decode member files";
                            else
                                result.status +=
                                        "\nTap for preview / Hex; complete bytes remain exportable";
                            input.checkIdentity(() -> closed);
                        }
                    } catch (Exception error) {
                        if (result != null && result.bitmap != null) result.bitmap.recycle();
                        result = new Observation("Preview unavailable: " + error.getMessage());
                    }
                    Observation observation = result;
                    main.post(
                            () -> {
                                if (closed) {
                                    if (observation.bitmap != null) observation.bitmap.recycle();
                                    return;
                                }
                                pending.remove(index);
                                observations.put(index, observation);
                                notifyDataSetChanged();
                            });
                });
    }

    @Override
    public void close() {
        closed = true;
        observations.clear();
        pending.clear();
        // Displayed ImageViews may still own bitmaps until the old page is detached/collected.
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** In-app viewing of a separately owned exact range. No file name becomes an executable or URL. */
public final class ContentPreviewActivity extends Activity {
    private static final String[] MODES = {
        "Automatic", "Image", "Audio / video", "UTF-8 text", "PDF", "Hex"
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancellation = new AtomicBoolean();
    private InputSnapshot input;
    private String name, mode = "Automatic", retainedPath;
    private TextView status, identity;
    private TextView exportNotice;
    private Button exportBytes, originButton;
    private Button gallerize;
    private boolean gallerizeBusy;
    private ContentOrigin origin;
    private boolean originBusy;
    private String originNotice = "No full discovery capture accompanied this preview";
    private PreparedExport contentExport;
    private boolean exportBusy, exportPickerOpen;
    private int exportGeneration;
    private String restoredExportNotice = "";
    private LinearLayout content, controls;
    private MediaPlayer player;
    private SurfaceView surface;
    private boolean prepared, paused = true;
    private volatile int generation;
    private int pdfPage, seekPosition, pdfScrollX, pdfScrollY;
    private boolean pdfActualPixels;
    private PdfPagePreview.Result pdfPreview;
    private HorizontalScrollView pdfPan;
    private ScrollView pdfScroll;
    private Utf8Pages.Cursor textCursor = new Utf8Pages.Cursor();
    private Utf8Pages.Page textPage;
    private Long requestedTextOffset;
    private boolean requestedTextTail, requestedTextBack;
    private long hexOffset;
    private String positionNotice = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size) {
        return WorkbenchUi.text(this, value, size);
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(12);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void add(LinearLayout row, View view) {
        row.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
    }

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        retainedPath = getIntent().getStringExtra("path");
        if (retainedPath == null) {
            finish();
            return;
        }
        name = getIntent().getStringExtra("name");
        if (name == null) name = "Selected bytes";
        if (saved != null) {
            mode = saved.getString("mode", "Automatic");
            pdfPage = Math.max(0, saved.getInt("pdfPage"));
            pdfActualPixels = saved.getBoolean("pdfActualPixels", false);
            pdfScrollX =
                    Math.max(0, Math.min(PdfPagePreview.MAX_TARGET, saved.getInt("pdfScrollX")));
            pdfScrollY =
                    Math.max(0, Math.min(PdfPagePreview.MAX_TARGET, saved.getInt("pdfScrollY")));
            seekPosition = Math.max(0, saved.getInt("seek"));
            long size = getIntent().getLongExtra("sourceSize", -1);
            try {
                textCursor =
                        Utf8Pages.Cursor.restore(
                                size,
                                saved.getLong("textOffset", 0),
                                saved.getLongArray("textHistory"),
                                saved.getBoolean("textHistoryExpired"));
                hexOffset = saved.getLong("hexOffset", 0);
                if (hexOffset < 0 || hexOffset > size)
                    throw new IOException("Saved Hex position is outside the input");
            } catch (IOException expired) {
                textCursor = new Utf8Pages.Cursor();
                hexOffset = 0;
                positionNotice = " · saved text/Hex position reset: " + expired.getMessage();
            }
            if (saved.getString("contentExport") != null)
                try {
                    contentExport =
                            PreparedExport.restore(
                                    new File(getCacheDir(), "tool-exports"),
                                    saved.getString("contentExport"),
                                    saved.getString("contentExportName"),
                                    saved.getString("contentExportMime"),
                                    saved.getLong("contentExportSize"));
                    exportPickerOpen = true;
                    restoredExportNotice =
                            "Captured export is ready; finish choosing its destination";
                } catch (IOException error) {
                    restoredExportNotice = "Prepared export expired; prepare it again";
                }
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff15191e);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        android.graphics.Insets bars =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        view.setPadding(
                                bars.left + dp(8),
                                bars.top + dp(8),
                                bars.right + dp(8),
                                bars.bottom + dp(8));
                        return insets;
                    });
        }
        TextView title = text(name, 18);
        title.setMaxLines(2);
        root.addView(title);
        identity = text("Checking owned preview bytes…", 12);
        identity.setTextIsSelectable(true);
        identity.setMaxLines(2);
        identity.setEllipsize(TextUtils.TruncateAt.END);
        identity.setTag("content-source-identity");
        identity.setTooltipText("Tap for complete source identity and origin summary");
        identity.setOnClickListener(
                view ->
                        new AlertDialog.Builder(this)
                                .setTitle("Source identity / origin summary")
                                .setMessage(identity.getText())
                                .setPositiveButton("Close", null)
                                .show());
        root.addView(identity);
        originButton = button("Source / captured information", this::showOrigin);
        originButton.setTag("content-origin");
        originButton.setEnabled(false);
        originButton.setVisibility(
                getIntent().getStringExtra("contentOrigin") == null ? View.GONE : View.VISIBLE);
        root.addView(originButton);
        LinearLayout tools = new LinearLayout(this);
        add(tools, button("View as…", this::chooseMode));
        add(tools, button("Inspect / metadata", this::tools));
        exportBytes = button("Export bytes", this::exportBytes);
        exportBytes.setEnabled(false);
        add(tools, exportBytes);
        gallerize = button("Gallerize", this::gallerize);
        gallerize.setTag("content-gallerize");
        gallerize.setEnabled(false);
        gallerize.setTooltipText("Find embedded files and media inside these decoded bytes");
        add(tools, gallerize);
        root.addView(tools);
        status = text("Loading…", 12);
        root.addView(status);
        exportNotice = text(restoredExportNotice, 12);
        exportNotice.setTextIsSelectable(true);
        exportNotice.setVisibility(restoredExportNotice.isEmpty() ? View.GONE : View.VISIBLE);
        root.addView(exportNotice);
        controls = new LinearLayout(this);
        root.addView(controls);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        int ticket = ++generation;
        worker.execute(
                () -> {
                    try {
                        input =
                                InputSnapshot.restore(
                                        new File(retainedPath),
                                        new File(getCacheDir(), "content-previews"),
                                        getIntent().getLongExtra("sourceSize", -1),
                                        getIntent().getStringExtra("sha256"),
                                        cancellation::get);
                        String descriptor = getIntent().getStringExtra("contentOrigin");
                        if (descriptor != null) {
                            try {
                                origin =
                                        ContentOrigin.restore(
                                                new File(getCacheDir(), "content-origins"),
                                                descriptor,
                                                input.sha256,
                                                input.size,
                                                cancellation::get);
                                originNotice =
                                        "Captured origin · "
                                                + origin.relationship
                                                + " · "
                                                + origin.size
                                                + " information bytes · SHA-256 "
                                                + origin.sha256;
                            } catch (Exception unavailable) {
                                originNotice =
                                        "Origin information unavailable: " + unavailable.getMessage();
                            }
                        }
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || ticket != generation) return;
                                    originButton.setEnabled(origin != null);
                                    if (origin == null && descriptor != null)
                                        originButton.setText(originNotice);
                                    identity.setText(
                                            input.size
                                                    + " exact bytes · SHA-256 "
                                                    + input.sha256
                                                    + "\nOriginal input "
                                                    + getIntent().getStringExtra("originalSha256")
                                                    + (getIntent().hasExtra("originalOffset")
                                                            ? " · offset 0x"
                                                                    + Long.toHexString(
                                                                            getIntent()
                                                                                    .getLongExtra(
                                                                                            "originalOffset",
                                                                                            0))
                                                            : " · derived archive member; original"
                                                                    + " physical offset"
                                                                    + " unavailable")
                                                    + "\n"
                                                    + originNotice);
                                    exportBytes.setEnabled(!exportBusy && !exportPickerOpen);
                                    gallerize.setEnabled(!gallerizeBusy);
                                    render();
                                });
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void chooseMode() {
        new AlertDialog.Builder(this)
                .setTitle("View exact selected bytes as")
                .setItems(
                        MODES,
                        (dialog, which) -> {
                            mode = MODES[which];
                            render();
                        })
                .show();
    }

    private void exportMessage(String value) {
        exportNotice.setText(value);
        exportNotice.setVisibility(View.VISIBLE);
    }

    private void exportBytes() {
        if (input == null || exportBusy || exportPickerOpen) return;
        InputSnapshot captured = input;
        int operation = ++exportGeneration;
        exportBusy = true;
        exportBytes.setEnabled(false);
        exportMessage("Checking and staging exact selected bytes…");
        String suggested = getIntent().getStringExtra("exportName");
        String filename =
                suggested != null && suggested.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                        ? suggested
                        : "content-" + captured.sha256.substring(0, 12) + ".bin";
        worker.execute(
                () -> {
                    PreparedExport staged = null;
                    try {
                        staged =
                                RecoveredFiles.namedRange(
                                        captured,
                                        new FileRange(captured.size, 0, captured.size),
                                        new File(getCacheDir(), "tool-exports"),
                                        filename,
                                        cancellation::get);
                        EvidenceRows.check(cancellation::get);
                        PreparedExport ready = staged;
                        staged = null;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || operation != exportGeneration) {
                                        ready.discard();
                                        return;
                                    }
                                    exportBusy = false;
                                    contentExport = ready;
                                    exportPickerOpen = true;
                                    exportMessage(
                                            "Captured exact byte export · "
                                                    + ready.size
                                                    + " bytes · SHA-256 "
                                                    + captured.sha256);
                                    try {
                                        startActivityForResult(
                                                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                                        .setType(ready.mime)
                                                        .putExtra(Intent.EXTRA_TITLE, ready.name),
                                                1);
                                    } catch (RuntimeException error) {
                                        contentExport = null;
                                        exportPickerOpen = false;
                                        ready.discard();
                                        exportBytes.setEnabled(input != null);
                                        exportMessage("Byte export could not open: " + error);
                                    }
                                });
                    } catch (Exception error) {
                        if (staged != null) staged.discard();
                        runOnUiThread(
                                () -> {
                                    if (!isDestroyed() && operation == exportGeneration) {
                                        exportBusy = false;
                                        exportBytes.setEnabled(input != null && !exportPickerOpen);
                                        exportMessage(
                                                "Byte export preparation failed: "
                                                        + error.getMessage());
                                    }
                                });
                    }
                });
    }

    private void finishByteExport(int result, Intent data) {
        PreparedExport staged = contentExport;
        contentExport = null;
        exportPickerOpen = false;
        if (staged == null) {
            exportBytes.setEnabled(input != null);
            exportMessage("Prepared export expired; prepare it again");
            return;
        }
        String exportLabel = "Export";
        if (result != RESULT_OK || data == null || data.getData() == null) {
            staged.discard();
            exportBytes.setEnabled(input != null);
            exportMessage(exportLabel + " cancelled");
            return;
        }
        Uri destination = data.getData();
        int operation = ++exportGeneration;
        exportBusy = true;
        exportBytes.setEnabled(false);
        exportMessage("Writing captured bytes…");
        worker.execute(
                () -> {
                    String message;
                    try (OutputStream output =
                            getContentResolver().openOutputStream(destination, "wt")) {
                        if (output == null) throw new IOException("Destination unavailable");
                        staged.copyTo(output, cancellation::get);
                        message = exportLabel + " saved · " + staged.size + " exact bytes";
                    } catch (Exception error) {
                        message =
                                exportLabel
                                        + " failed; destination may contain partial data: "
                                        + error.getMessage();
                    } finally {
                        staged.discard();
                    }
                    String response = message;
                    runOnUiThread(
                            () -> {
                                if (!isDestroyed() && operation == exportGeneration) {
                                    exportBusy = false;
                                    exportBytes.setEnabled(input != null);
                                    exportMessage(response);
                                }
                            });
                });
    }

    private void showOrigin() {
        if (origin == null || originBusy || exportBusy || exportPickerOpen) return;
        originBusy = true;
        originButton.setEnabled(false);
        ContentOrigin captured = origin;
        worker.execute(
                () -> {
                    try {
                        captured.checkIdentity(cancellation::get);
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed()) return;
                                    originBusy = false;
                                    originButton.setEnabled(true);
                                    PagedTextDialog.show(
                                            this,
                                            worker,
                                            "Captured origin · " + captured.size + " bytes",
                                            captured.file,
                                            captured.size,
                                            cancellation::get,
                                            () -> {},
                                            this::exportOrigin,
                                            () -> {});
                                });
                    } catch (Exception error) {
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed()) return;
                                    originBusy = false;
                                    originButton.setEnabled(true);
                                    exportMessage(
                                            "Origin information unavailable: " + error.getMessage());
                                });
                    }
                });
    }

    private void exportOrigin() {
        if (origin == null || exportBusy || exportPickerOpen) return;
        ContentOrigin captured = origin;
        int operation = ++exportGeneration;
        exportBusy = true;
        exportBytes.setEnabled(false);
        exportMessage("Checking and staging complete origin information…");
        worker.execute(
                () -> {
                    PreparedExport staged = null;
                    try {
                        staged =
                                captured.export(
                                        new File(getCacheDir(), "tool-exports"), cancellation::get);
                        EvidenceRows.check(cancellation::get);
                        PreparedExport ready = staged;
                        staged = null;
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || operation != exportGeneration) {
                                        ready.discard();
                                        return;
                                    }
                                    exportBusy = false;
                                    contentExport = ready;
                                    exportPickerOpen = true;
                                    exportMessage(
                                            "Captured complete origin · "
                                                    + ready.size
                                                    + " bytes · SHA-256 "
                                                    + captured.sha256);
                                    try {
                                        startActivityForResult(
                                                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                                        .setType(ready.mime)
                                                        .putExtra(Intent.EXTRA_TITLE, ready.name),
                                                1);
                                    } catch (RuntimeException error) {
                                        contentExport = null;
                                        exportPickerOpen = false;
                                        ready.discard();
                                        exportBytes.setEnabled(input != null);
                                        exportMessage(
                                                "Origin export could not open: "
                                                        + error.getMessage());
                                    }
                                });
                    } catch (Exception error) {
                        if (staged != null) staged.discard();
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed() || operation != exportGeneration) return;
                                    exportBusy = false;
                                    exportBytes.setEnabled(input != null && !exportPickerOpen);
                                    exportMessage(
                                            "Origin export preparation failed: "
                                                    + error.getMessage());
                                });
                    }
                });
    }

    private void releasePlayer() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.reset();
            } catch (RuntimeException ignored) {
            }
            player.release();
            player = null;
        }
        prepared = false;
        paused = true;
    }

    private void render() {
        if (input == null) return;
        if (pdfPreview != null && pdfPreview.pageIndex != pdfPage) pdfScrollX = pdfScrollY = 0;
        else capturePdfScroll();
        pdfPreview = null;
        pdfPan = null;
        pdfScroll = null;
        releasePlayer();
        int ticket = ++generation;
        long wantedText = requestedTextOffset == null ? textCursor.offset : requestedTextOffset;
        boolean lastText = requestedTextTail, backText = requestedTextBack;
        requestedTextOffset = null;
        requestedTextTail = requestedTextBack = false;
        long wantedHex = hexOffset;
        int wantedPdfPage = pdfPage;
        if (mode.equals("UTF-8 text") && textPage != null) {
            showTextBody(textPage.text, false);
            textControls(textPage);
            for (int i = 0; i < controls.getChildCount(); i++)
                controls.getChildAt(i).setEnabled(false);
        } else {
            content.removeAllViews();
            controls.removeAllViews();
        }
        status.setText(
                "Rendering "
                        + mode
                        + " · decoder success is separate from complete-file integrity");
        worker.execute(
                () -> {
                    BooleanSupplier stop = () -> cancellation.get() || ticket != generation;
                    try {
                        input.checkIdentity(stop);
                        byte[] head = new byte[(int) Math.min(4096, input.size)];
                        try (InputStream stream = new FileInputStream(input.file)) {
                            int n = 0;
                            while (n < head.length) {
                                int read = stream.read(head, n, head.length - n);
                                if (read < 0) throw new EOFException();
                                n += read;
                            }
                        }
                        String type = Inspector.identify(head, head.length), chosen = mode;
                        if (chosen.equals("Automatic")) {
                            String lower = type.toLowerCase(Locale.ROOT);
                            String preferred = getIntent().getStringExtra("preferred");
                            if (lower.contains("image")
                                    || lower.equals("png")
                                    || "IMAGE".equals(preferred)) chosen = "Image";
                            else if (lower.contains("audio")
                                    || lower.contains("media")
                                    || "AUDIO".equals(preferred)
                                    || "VIDEO".equals(preferred)) chosen = "Audio / video";
                            else if (type.equals("PDF")) chosen = "PDF";
                            else if ("TEXT".equals(preferred)
                                    || ContentPreview.looksLikeText(
                                            head, head.length == input.size)) chosen = "UTF-8 text";
                            else chosen = "Hex";
                        }
                        String renderer = chosen;
                        if (chosen.equals("Image")) {
                            Bitmap bitmap =
                                    ContentPreview.image(
                                            input.file,
                                            new FileRange(input.size, 0, input.size),
                                            2048);
                            runOnUiThread(
                                    () -> {
                                        if (isDestroyed() || ticket != generation) {
                                            bitmap.recycle();
                                            return;
                                        }
                                        ImageView image = new ImageView(this);
                                        image.setTag("content-image");
                                        image.setContentDescription(name);
                                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                        image.setImageBitmap(bitmap);
                                        content.addView(
                                                image, new LinearLayout.LayoutParams(-1, -1));
                                        status.setText(
                                                type
                                                        + " · platform decoded first frame "
                                                        + bitmap.getWidth()
                                                        + " × "
                                                        + bitmap.getHeight()
                                                        + " (bounded to 2048 × 2048)");
                                    });
                        } else if (chosen.equals("UTF-8 text")) {
                            Utf8Pages.Page page =
                                    lastText
                                            ? Utf8Pages.tail(
                                                    input.file,
                                                    input.size,
                                                    Utf8Pages.CHARACTERS,
                                                    stop)
                                            : Utf8Pages.read(
                                                    input.file,
                                                    input.size,
                                                    wantedText,
                                                    Utf8Pages.CHARACTERS,
                                                    stop);
                            input.checkIdentity(stop);
                            runOnUiThread(
                                    () -> {
                                        if (isDestroyed() || ticket != generation) return;
                                        if (backText
                                                && textCursor.canBack()
                                                && textCursor.backTarget() == page.offset)
                                            textCursor.back();
                                        else textCursor.move(page.offset);
                                        textPage = page;
                                        showTextBody(page.text, false);
                                        textControls(page);
                                        status.setText(
                                                "UTF-8 · preview bytes ["
                                                        + page.offset
                                                        + ", "
                                                        + page.end
                                                        + ") / "
                                                        + input.size
                                                        + " · "
                                                        + page.text.length()
                                                        + " UTF-16 units"
                                                        + (textCursor.expired
                                                                ? " · older visited positions"
                                                                      + " available by byte offset"
                                                                : "")
                                                        + positionNotice);
                                    });
                        } else if (chosen.equals("Hex")) {
                            if (wantedHex < 0 || wantedHex > input.size)
                                throw new IOException("Hex position is outside the input");
                            byte[] bytes = new byte[(int) Math.min(input.size - wantedHex, 4096)];
                            try (RandomAccessFile file = new RandomAccessFile(input.file, "r")) {
                                file.seek(wantedHex);
                                file.readFully(bytes);
                            }
                            input.checkIdentity(stop);
                            StringBuilder output = new StringBuilder();
                            for (int i = 0; i < bytes.length; i += 16) {
                                output.append(String.format(Locale.ROOT, "%08X  ", wantedHex + i));
                                for (int j = i; j < Math.min(i + 16, bytes.length); j++)
                                    output.append(
                                            String.format(Locale.ROOT, "%02X ", bytes[j] & 255));
                                output.append('\n');
                            }
                            String value = output.toString();
                            runOnUiThread(
                                    () -> {
                                        if (isDestroyed() || ticket != generation) return;
                                        hexOffset = wantedHex;
                                        showTextBody(value, true);
                                        hexControls(wantedHex + bytes.length);
                                        status.setText(
                                                "Hex · preview bytes ["
                                                        + wantedHex
                                                        + ", "
                                                        + (wantedHex + bytes.length)
                                                        + ") / "
                                                        + input.size
                                                        + positionNotice);
                                    });
                        } else if (chosen.equals("PDF")) pdf(ticket, wantedPdfPage);
                        else
                            runOnUiThread(
                                    () -> {
                                        if (!isDestroyed() && ticket == generation) media(ticket);
                                    });
                    } catch (Exception error) {
                        failed(ticket, error);
                    }
                });
    }

    private void showTextBody(String value, boolean mono) {
        content.removeAllViews();
        TextView view = text(value, 13);
        view.setTag("content-text");
        view.setTextIsSelectable(true);
        if (mono) view.setTypeface(Typeface.MONOSPACE);
        ScrollView scroll = WorkbenchUi.scroll(this);
        scroll.addView(view);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private Button pageButton(String label, String tag, Runnable action, boolean enabled) {
        Button view = button(label, action);
        view.setTag(tag);
        view.setEnabled(enabled);
        add(controls, view);
        return view;
    }

    private void requestText(long offset, boolean tail, boolean back) {
        mode = "UTF-8 text";
        requestedTextOffset = offset;
        requestedTextTail = tail;
        requestedTextBack = back;
        render();
    }

    private void textControls(Utf8Pages.Page page) {
        controls.removeAllViews();
        pageButton(
                "First",
                "content-text-first",
                () -> requestText(0, false, false),
                page.offset != 0);
        pageButton(
                "Back",
                "content-text-back",
                () -> requestText(textCursor.backTarget(), false, true),
                textCursor.canBack());
        pageButton(
                "Next",
                "content-text-next",
                () -> requestText(page.end, false, false),
                page.more());
        pageButton("Tail", "content-text-tail", () -> requestText(0, true, false), input.size > 0);
        pageButton("Byte…", "content-text-jump", () -> jumpPosition(false), true);
    }

    private void hexControls(long end) {
        controls.removeAllViews();
        pageButton(
                "First",
                "content-hex-first",
                () -> {
                    hexOffset = 0;
                    render();
                },
                hexOffset != 0);
        pageButton(
                "Previous",
                "content-hex-back",
                () -> {
                    hexOffset = Math.max(0, hexOffset - 4096);
                    render();
                },
                hexOffset > 0);
        pageButton(
                "Next",
                "content-hex-next",
                () -> {
                    hexOffset = end;
                    render();
                },
                end < input.size);
        pageButton(
                "Tail",
                "content-hex-tail",
                () -> {
                    hexOffset = Math.max(0, input.size - 4096);
                    render();
                },
                input.size > 0);
        pageButton("Byte…", "content-hex-jump", () -> jumpPosition(true), true);
    }

    private void jumpPosition(boolean hex) {
        EditText value = new EditText(this);
        value.setSingleLine();
        value.setTag("content-byte-offset");
        value.setText("" + (hex ? hexOffset : textCursor.offset));
        value.setHint("Decimal or 0x byte offset within these preview bytes");
        new AlertDialog.Builder(this)
                .setTitle(hex ? "Hex byte offset" : "UTF-8 byte offset")
                .setMessage(
                        "Positions refer to this owned preview. UTF-8 starts require a"
                                + " complete-character boundary; Hex accepts any byte.")
                .setView(value)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Open",
                        (dialog, which) -> {
                            try {
                                String text = value.getText().toString().trim();
                                long offset =
                                        text.startsWith("0x") || text.startsWith("0X")
                                                ? Long.parseLong(text.substring(2), 16)
                                                : Long.parseLong(text, 10);
                                if (offset < 0 || offset > input.size)
                                    throw new IllegalArgumentException(
                                            "Offset is outside the preview");
                                if (hex) {
                                    hexOffset = offset;
                                    render();
                                } else requestText(offset, false, false);
                            } catch (RuntimeException failure) {
                                status.setText("Invalid preview position: " + failure.getMessage());
                            }
                        })
                .show();
    }

    private void pdf(int ticket, int wantedPage) throws Exception {
        PdfPagePreview.Result observation =
                PdfPagePreview.read(
                        input,
                        wantedPage,
                        PdfPagePreview.MAX_TARGET,
                        () -> cancellation.get() || ticket != generation);
        Bitmap bitmap = observation.bitmap;
        int count = observation.pageCount, selected = observation.pageIndex;
        runOnUiThread(
                () -> {
                    if (isDestroyed() || ticket != generation) {
                        bitmap.recycle();
                        return;
                    }
                    pdfPage = selected;
                    pdfPreview = observation;
                    showPdfPage(observation);
                });
    }

    private void capturePdfScroll() {
        if (pdfPan != null) pdfScrollX = pdfPan.getScrollX();
        if (pdfScroll != null) pdfScrollY = pdfScroll.getScrollY();
    }

    /** Layout changes reuse the captured raster instead of parsing the PDF again. */
    private void showPdfPage(PdfPagePreview.Result observation) {
        if (isDestroyed() || pdfPreview != observation) return;
        content.removeAllViews();
        controls.removeAllViews();
        pdfPan = null;
        pdfScroll = null;
        ImageView image = new ImageView(this);
        image.setTag("content-pdf");
        image.setContentDescription(name + " · PDF page " + (pdfPage + 1));
        image.setImageBitmap(observation.bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (pdfActualPixels) {
            HorizontalScrollView horizontal = new HorizontalScrollView(this);
            horizontal.setTag("content-pdf-pan");
            horizontal.addView(
                    image,
                    new android.widget.FrameLayout.LayoutParams(
                            observation.bitmap.getWidth(), observation.bitmap.getHeight()));
            ScrollView vertical = WorkbenchUi.scroll(this);
            vertical.setTag("content-pdf-scroll");
            vertical.addView(
                    horizontal,
                    new android.widget.FrameLayout.LayoutParams(
                            -1, observation.bitmap.getHeight()));
            content.addView(vertical, new LinearLayout.LayoutParams(-1, -1));
            pdfPan = horizontal;
            pdfScroll = vertical;
            vertical.addOnLayoutChangeListener(
                    new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(
                                View view,
                                int left,
                                int top,
                                int right,
                                int bottom,
                                int oldLeft,
                                int oldTop,
                                int oldRight,
                                int oldBottom) {
                            if (right <= left || bottom <= top) return;
                            vertical.removeOnLayoutChangeListener(this);
                            if (!isDestroyed()
                                    && pdfPreview == observation
                                    && pdfActualPixels
                                    && pdfPan == horizontal
                                    && pdfScroll == vertical) {
                                horizontal.scrollTo(pdfScrollX, 0);
                                vertical.scrollTo(0, pdfScrollY);
                            }
                        }
                    });
        } else content.addView(image, new LinearLayout.LayoutParams(-1, -1));
        Button previous =
                button(
                        "Previous page",
                        () -> {
                            if (pdfPreview != observation || pdfPage <= 0) return;
                            pdfPage--;
                            render();
                        });
        previous.setEnabled(pdfPage > 0);
        add(controls, previous);
        Button next =
                button(
                        "Next page",
                        () -> {
                            if (pdfPreview != observation || pdfPage + 1 >= observation.pageCount)
                                return;
                            pdfPage++;
                            render();
                        });
        next.setEnabled(pdfPage + 1 < observation.pageCount);
        add(controls, next);
        add(
                controls,
                button(
                        pdfActualPixels ? "Fit page" : "Actual pixels",
                        () -> {
                            if (pdfPreview != observation) return;
                            capturePdfScroll();
                            pdfActualPixels = !pdfActualPixels;
                            showPdfPage(observation);
                        }));
        status.setText(
                observation.description()
                        + (pdfActualPixels
                                ? "\nActual raster pixels · scroll horizontally/vertically"
                                : "\nFit page · choose Actual pixels for small text"));
    }

    private void media(int ticket) {
        try {
            surface = new SurfaceView(this);
            surface.setTag("content-media");
            content.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
            player = new MediaPlayer();
            MediaPlayer captured = player;
            player.setOnErrorListener(
                    (value, what, extra) -> {
                        if (ticket == generation && value == player) {
                            status.setText(
                                    "Platform media decoder failed · what="
                                            + what
                                            + " extra="
                                            + extra
                                            + ". MPV probing and original bytes remain available.");
                            releasePlayer();
                        }
                        return true;
                    });
            player.setOnPreparedListener(
                    value -> {
                        if (isDestroyed() || ticket != generation || value != player) return;
                        prepared = true;
                        if (seekPosition > 0) value.seekTo(seekPosition);
                        paused = true;
                        status.setText(
                                "Local media ready · duration="
                                        + value.getDuration()
                                        + " ms · "
                                        + value.getVideoWidth()
                                        + " × "
                                        + value.getVideoHeight()
                                        + " · press Play");
                        SeekBar seek = new SeekBar(this);
                        seek.setMax(Math.max(1, value.getDuration()));
                        seek.setTag("content-seek");
                        content.addView(seek);
                        seek.setOnSeekBarChangeListener(
                                new SeekBar.OnSeekBarChangeListener() {
                                    public void onProgressChanged(
                                            SeekBar bar, int progress, boolean user) {
                                        if (user && player == value && prepared) {
                                            seekPosition = progress;
                                            value.seekTo(progress);
                                        }
                                    }

                                    public void onStartTrackingTouch(SeekBar bar) {}

                                    public void onStopTrackingTouch(SeekBar bar) {}
                                });
                        Runnable tick =
                                new Runnable() {
                                    public void run() {
                                        if (player == value && prepared && ticket == generation) {
                                            seek.setProgress(value.getCurrentPosition());
                                            handler.postDelayed(this, 500);
                                        }
                                    }
                                };
                        handler.post(tick);
                    });
            player.setOnCompletionListener(
                    value -> {
                        if (value == player) {
                            paused = true;
                            status.setText(
                                    "Playback reached the end · complete-file integrity is a"
                                            + " separate check");
                        }
                    });
            try (FileInputStream source = new FileInputStream(input.file)) {
                player.setDataSource(source.getFD(), 0, input.size);
            }
            surface.getHolder()
                    .addCallback(
                            new SurfaceHolder.Callback() {
                                public void surfaceCreated(SurfaceHolder holder) {
                                    if (player == captured) captured.setDisplay(holder);
                                }

                                public void surfaceChanged(
                                        SurfaceHolder holder, int format, int width, int height) {}

                                public void surfaceDestroyed(SurfaceHolder holder) {
                                    if (player == captured) captured.setDisplay(null);
                                }
                            });
            Button play =
                    button(
                            "Play / pause",
                            () -> {
                                if (player != captured || !prepared) return;
                                if (paused) {
                                    captured.start();
                                    paused = false;
                                } else {
                                    captured.pause();
                                    paused = true;
                                }
                            });
            play.setTag("content-play");
            add(controls, play);
            add(
                    controls,
                    button(
                            "Restart",
                            () -> {
                                if (player == captured && prepared) {
                                    seekPosition = 0;
                                    captured.seekTo(0);
                                }
                            }));
            player.prepareAsync();
        } catch (Exception error) {
            releasePlayer();
            status.setText(
                    "Local media unavailable: "
                            + error
                            + ". Use MPV / metadata tools for codec information.");
        }
    }

    private void gallerize() {
        if (input == null || gallerizeBusy) return;
        final InputSnapshot captured = input;
        final int ticket = generation;
        gallerizeBusy = true;
        gallerize.setEnabled(false);
        status.setText("Preparing independently owned bytes for file discovery…");
        worker.execute(
                () -> {
                    InputSnapshot staged = null;
                    String error = null;
                    try {
                        staged =
                                OwnedContent.stageRange(
                                        captured,
                                        new FileRange(captured.size, 0, captured.size),
                                        new File(getCacheDir(), "gallery-handoffs"),
                                        () -> cancellation.get() || isDestroyed());
                    } catch (Exception failure) {
                        error = failure.toString();
                    }
                    InputSnapshot handoff = staged;
                    String failure = error;
                    runOnUiThread(
                            () -> {
                                gallerizeBusy = false;
                                gallerize.setEnabled(input != null);
                                if (isDestroyed()
                                        || cancellation.get()
                                        || ticket != generation
                                        || input != captured) {
                                    if (handoff != null) handoff.close();
                                    return;
                                }
                                if (handoff == null) {
                                    status.setText("File discovery handoff failed: " + failure);
                                    return;
                                }
                                try {
                                    startActivity(
                                            new Intent(this, GallerizeActivity.class)
                                                    .putExtra(
                                                            "path", handoff.file.getAbsolutePath())
                                                    .putExtra("name", name)
                                                    .putExtra("sha256", handoff.sha256)
                                                    .putExtra("sourceSize", handoff.size)
                                                    .putExtra("ownedInputHandoff", true));
                                    status.setText(
                                            "Opened independently owned decoded bytes in"
                                                + " Gallerize");
                                } catch (RuntimeException unavailable) {
                                    handoff.close();
                                    status.setText("Cannot open Gallerize: " + unavailable);
                                }
                            });
                });
    }

    private void tools() {
        if (input == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Inspect these exact derived bytes")
                .setItems(
                        new String[] {
                            "Collected metadata / EXIF",
                            "MPV codecs / streams / tags",
                            "Classified strings",
                            "Archive hierarchy",
                            "Gallerize this range",
                            "All native tools"
                        },
                        (dialog, which) -> {
                            if (which == 4) {
                                gallerize();
                                return;
                            }
                            Class<?> target =
                                    which == 3
                                            ? NativeArchiveExplorerActivity.class
                                            : which == 4
                                                    ? GallerizeActivity.class
                                                    : which == 5
                                                            ? NativeToolsActivity.class
                                                            : InvestigationActivity.class;
                            Intent intent =
                                    new Intent(this, target)
                                            .putExtra("originPreviewResult", true)
                                            .putExtra("path", input.file.getAbsolutePath())
                                            .putExtra("name", name)
                                            .putExtra("sha256", input.sha256)
                                            .putExtra("sourceSize", input.size);
                            if (which < 3)
                                intent.putExtra(
                                        "provider",
                                        new String[] {"metadata", "mpv", "strings"}[which]);
                            if (which == 3) startActivityForResult(intent, 2);
                            else startActivity(intent);
                        })
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1) {
            finishByteExport(result, data);
            return;
        }
        if (request != 2) return;
        if (result != RESULT_OK || data == null) {
            status.setText("Archive selection cancelled");
            return;
        }
        if (input == null) {
            status.setText("Archive parent preview expired");
            return;
        }
        releasePlayer();
        int ticket = ++generation;
        status.setText("Acquiring returned archive member…");
        ArchivePreviewLauncher.launch(
                this,
                worker,
                data,
                input.sha256,
                input.size,
                cancellation::get,
                () -> !isDestroyed() && ticket == generation,
                () -> {},
                message -> status.setText(message));
    }

    private void failed(int ticket, Exception error) {
        runOnUiThread(
                () -> {
                    if (!isDestroyed() && ticket == generation) {
                        if (mode.equals("UTF-8 text") && textPage != null) {
                            showTextBody(textPage.text, false);
                            textControls(textPage);
                            status.setText(
                                    "Requested page unavailable: "
                                            + error.getMessage()
                                            + " · previous captured page ["
                                            + textPage.offset
                                            + ", "
                                            + textPage.end
                                            + ") remains displayed");
                        } else
                            status.setText(
                                    "Preview unavailable: "
                                            + error
                                            + ". Use View as… or Inspect / metadata to continue.");
                    }
                });
    }

    @Override
    protected void onPause() {
        if (player != null && prepared) {
            seekPosition = player.getCurrentPosition();
            if (!paused) player.pause();
            paused = true;
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle saved) {
        super.onSaveInstanceState(saved);
        saved.putString("mode", mode);
        saved.putInt("pdfPage", pdfPage);
        capturePdfScroll();
        saved.putBoolean("pdfActualPixels", pdfActualPixels);
        saved.putInt("pdfScrollX", pdfScrollX);
        saved.putInt("pdfScrollY", pdfScrollY);
        saved.putLong("textOffset", textCursor.offset);
        saved.putLongArray("textHistory", textCursor.history());
        saved.putBoolean("textHistoryExpired", textCursor.expired);
        saved.putLong("hexOffset", hexOffset);
        if (contentExport != null && exportPickerOpen) {
            saved.putString("contentExport", contentExport.file.getAbsolutePath());
            saved.putString("contentExportName", contentExport.name);
            saved.putString("contentExportMime", contentExport.mime);
            saved.putLong("contentExportSize", contentExport.size);
        }
        saved.putInt(
                "seek", player != null && prepared ? player.getCurrentPosition() : seekPosition);
    }

    @Override
    protected void onDestroy() {
        cancellation.set(true);
        generation++;
        exportGeneration++;
        if (contentExport != null && (!exportPickerOpen || isFinishing())) contentExport.discard();
        releasePlayer();
        boolean retain = isChangingConfigurations();
        worker.execute(
                () -> {
                    if (!retain) {
                        if (origin != null) origin.close();
                        else
                            ContentOrigin.discard(
                                    new File(getCacheDir(), "content-origins"),
                                    getIntent().getStringExtra("contentOrigin"));
                        if (input != null) input.close();
                        else if (retainedPath != null)
                            try {
                                // Ownership transfers before onCreate; clean an unacquired private
                                // input after cancellation.
                                File file = new File(retainedPath).getCanonicalFile();
                                if (file.getParentFile()
                                                .equals(
                                                        new File(getCacheDir(), "content-previews")
                                                                .getCanonicalFile())
                                        && file.getName()
                                                .matches(
                                                        "analysis-input-[A-Za-z0-9._-]+\\.snapshot"))
                                    file.delete();
                            } catch (IOException ignored) {
                            }
                    }
                });
        worker.shutdown();
        super.onDestroy();
    }
}

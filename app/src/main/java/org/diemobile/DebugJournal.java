// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Best-effort, app-private operational diagnostics, excluded from Android backup.
 *
 * <p>Callers must supply a constant event code and structural key=value fields only. Never pass
 * file names/paths, inspected bytes/strings, search/translation text, exception messages or
 * credentials. Only approved metadata fields survive the additional defensive filter. No logcat or
 * network IO.
 */
final class DebugJournal {
    static final int MAX_ENTRIES = 256;
    static final int MAX_BYTES = 96 * 1024;
    static final int MAX_DETAIL_UNITS = 320;
    private static final Object LOCK = new Object();
    private static final Set<String> CODE_FIELDS =
            new HashSet<>(
                    Arrays.asList(
                            "engine",
                            "status",
                            "stage",
                            "operation",
                            "exception",
                            "error",
                            "reason",
                            "token",
                            "request",
                            "id",
                            "mode",
                            "class",
                            "method"));
    private static final Set<String> NUMBER_FIELDS =
            new HashSet<>(
                    Arrays.asList(
                            "bytes", "seconds", "rows", "count", "timeout", "limit", "uid",
                            "line"));
    private static volatile String lastFailure;

    private DebugJournal() {}

    /**
     * Logging must never change the outcome of analysis, including when private storage is full.
     */
    static void record(Context context, String event, String detail) {
        try {
            if (context == null) return;
            synchronized (LOCK) {
                AtomicFile journal = journal(context);
                List<Entry> entries;
                String now = timestamp();
                try {
                    entries = readEntries(journal);
                } catch (IOException damaged) {
                    entries = new ArrayList<>();
                    entries.add(new Entry(now, "journal_reset", "reason=unreadable_state"));
                }
                entries.add(new Entry(now, safeEvent(event), safeDetail(detail)));
                trim(entries);
                byte[] bytes = encode(entries);
                FileOutputStream output = null;
                try {
                    output = journal.startWrite();
                    output.write(bytes);
                    journal.finishWrite(output);
                } catch (Throwable failure) {
                    if (output != null) journal.failWrite(output);
                    throw failure;
                }
                lastFailure = null;
            }
        } catch (Throwable failure) {
            // Do not recurse through diagnostics or expose exception messages containing input
            // paths.
            lastFailure = failure.getClass().getSimpleName();
        }
    }

    /** Exception type and first app stack frame only; never a message, filename or stack dump. */
    static void recordFailure(Context context, String event, String detail, Throwable failure) {
        try {
            if (failure == null) {
                record(context, event, detail);
                return;
            }
            StringBuilder fields = new StringBuilder(detail == null ? "" : detail);
            append(fields, "exception=" + failure.getClass().getSimpleName());
            StackTraceElement[] frames = failure.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 48); i++) {
                StackTraceElement frame = frames[i];
                if (!frame.getClassName().startsWith("org.diemobile.")) continue;
                append(fields, "class=" + frame.getClassName());
                append(
                        fields,
                        "method=" + frame.getMethodName().replace('<', '_').replace('>', '_'));
                if (frame.getLineNumber() >= 0) append(fields, "line=" + frame.getLineNumber());
                break;
            }
            record(context, event, fields.toString());
        } catch (Throwable ignored) {
            // Even a failure while formatting a diagnostic must not affect its caller.
        }
    }

    static Snapshot read(Context context) {
        try {
            synchronized (LOCK) {
                List<Entry> entries = readEntries(journal(context));
                Collections.reverse(entries);
                String status = entries.size() + " retained entries · newest first · UTC";
                if (lastFailure != null)
                    status += "\nA recent log write failed (" + lastFailure + ")";
                return new Snapshot(entries, status, true);
            }
        } catch (Throwable failure) {
            return new Snapshot(
                    Collections.emptyList(),
                    "Debug log unavailable ("
                            + failure.getClass().getSimpleName()
                            + "). Analysis can continue.",
                    false);
        }
    }

    static boolean clear(Context context) {
        try {
            synchronized (LOCK) {
                AtomicFile journal = journal(context);
                journal.delete();
                boolean cleared = !journal.getBaseFile().exists();
                if (cleared) lastFailure = null;
                return cleared;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static AtomicFile journal(Context context) throws IOException {
        File privateRoot = context.getNoBackupFilesDir();
        if (privateRoot == null) throw new IOException("Private diagnostics storage unavailable");
        File directory = new File(privateRoot, "program-debug");
        if (!privateRoot.getCanonicalFile().equals(directory.getCanonicalFile().getParentFile()))
            throw new IOException("Unexpected diagnostics directory location");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Private diagnostics directory unavailable");
        File canonicalDirectory = directory.getCanonicalFile();
        File file = new File(canonicalDirectory, "events.log");
        if (!file.getAbsoluteFile().equals(file.getCanonicalFile()))
            throw new IOException("Unexpected diagnostics file location");
        return new AtomicFile(file);
    }

    private static List<Entry> readEntries(AtomicFile journal) throws IOException {
        byte[] bytes;
        try (FileInputStream input = journal.openRead();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > MAX_BYTES - output.size())
                    throw new IOException("Debug log exceeds its storage bound");
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        } catch (FileNotFoundException absent) {
            if (journal.getBaseFile().exists()) throw absent;
            return new ArrayList<>();
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<Entry> entries = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length != 3
                    || !fields[0].matches(
                            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z")
                    || !fields[1].equals(safeEvent(fields[1]))
                    || !fields[2].equals(safeDetail(fields[2]))
                    || entries.size() >= MAX_ENTRIES) throw new IOException("Malformed debug log");
            entries.add(new Entry(fields[0], fields[1], fields[2]));
        }
        return entries;
    }

    private static void trim(List<Entry> entries) {
        int bytes = 0;
        for (Entry entry : entries) bytes += entry.line().length(); // Stored alphabet is ASCII.
        while (entries.size() > MAX_ENTRIES || bytes > MAX_BYTES)
            bytes -= entries.remove(0).line().length();
    }

    private static byte[] encode(List<Entry> entries) {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries) text.append(entry.line());
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String safeEvent(String event) {
        return event != null && event.matches("[A-Za-z0-9_.-]{1,64}") ? event : "diagnostic";
    }

    /**
     * Unknown/free-text fields are intentionally omitted instead of storing potential file text.
     */
    private static String safeDetail(String detail) {
        if (detail == null || detail.isEmpty()) return "";
        String bounded = detail.substring(0, Math.min(detail.length(), 2048));
        StringBuilder output = new StringBuilder();
        for (String field : bounded.split("\\s+")) {
            if (field.isEmpty()) continue;
            int equal = field.indexOf('=');
            String key = equal > 0 ? field.substring(0, equal) : "";
            String value = equal > 0 ? field.substring(equal + 1) : "";
            boolean code = CODE_FIELDS.contains(key) && value.matches("[A-Za-z0-9_.$:+-]{1,96}");
            boolean numericKey =
                    NUMBER_FIELDS.contains(key)
                            || key.matches(
                                    "[A-Za-z][A-Za-z0-9_]{0,30}(Ms|Bytes|Seconds|Count|Rows|Uid)");
            boolean numeric = numericKey && value.matches("[0-9]{1,20}");
            boolean flag = key.equals("ok") && (value.equals("true") || value.equals("false"));
            String safe =
                    code
                                    || numeric
                                    || flag
                                    || field.equals("[omitted]")
                                    || field.equals("[bounded]")
                            ? field
                            : "[omitted]";
            if (output.length() + safe.length() + 1 > MAX_DETAIL_UNITS - 10) {
                append(output, "[bounded]");
                break;
            }
            append(output, safe);
        }
        if (detail.length() > bounded.length()
                && output.length() + 10 <= MAX_DETAIL_UNITS
                && !output.toString().endsWith("[bounded]")) append(output, "[bounded]");
        return output.toString();
    }

    private static void append(StringBuilder output, String field) {
        if (output.length() != 0) output.append(' ');
        output.append(field);
    }

    static final class Entry {
        final String timestamp, event, detail;

        private Entry(String timestamp, String event, String detail) {
            this.timestamp = timestamp;
            this.event = event;
            this.detail = detail;
        }

        String line() {
            return timestamp + '\t' + event + '\t' + detail + '\n';
        }
    }

    static final class Snapshot {
        final List<Entry> entries;
        final String status;
        final boolean available;

        private Snapshot(List<Entry> entries, String status, boolean available) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.status = status;
            this.available = available;
        }

        String text() {
            StringBuilder text =
                    new StringBuilder(
                            "GlassDigestor private program debug log\n"
                                    + "UTC timestamps; newest entries first.\n"
                                    + "Retention: at most "
                                    + MAX_ENTRIES
                                    + " entries / "
                                    + MAX_BYTES
                                    + " bytes; older entries are removed automatically.\n"
                                    + "Only operational metadata is recorded.\n"
                                    + status
                                    + "\n\n");
            for (Entry entry : entries) text.append(entry.line());
            return text.toString();
        }
    }
}

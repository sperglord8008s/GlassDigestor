// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Actual file payload exports. Native ranges stay authoritative; names never become paths. */
final class RecoveredFiles {
    static final int MAX_FILES = 4096;
    static final long MAX_TOTAL_BYTES = 240L * 1024 * 1024;

    static String extension(String format) {
        String value = format == null ? "" : format.toLowerCase(Locale.ROOT).trim();
        switch (value) {
            case "jpeg":
                return "jpg";
            case "tiff":
                return "tif";
            case "wave":
                return "wav";
            case "gzip":
                return "gz";
            case "bzip2":
                return "bz2";
            case "zstd":
            case "zstandard":
                return "zst";
            case "matroska":
                return "mkv";
            case "utf-8":
            case "utf8":
            case "text":
                return "txt";
            default:
                return value.matches("[a-z0-9]{1,16}") ? value : "bin";
        }
    }

    static String name(ContentMap.Card card) {
        String prefix = card.kind == ContentMap.Kind.TEXT ? "text" : "recovered";
        return prefix
                + "-"
                + String.format(Locale.ROOT, "%08x", card.offset)
                + "-"
                + (card.length == null ? "unknown" : card.length)
                + "."
                + extension(card.format);
    }

    static String mime(ContentMap.Card card) {
        return mime(card.format);
    }

    static String mime(String format) {
        switch (extension(format)) {
            case "jpg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "tif":
                return "image/tiff";
            case "ico":
                return "image/vnd.microsoft.icon";
            case "svg":
                return "image/svg+xml";
            case "avif":
                return "image/avif";
            case "heic":
                return "image/heic";
            case "pdf":
                return "application/pdf";
            case "txt":
            case "log":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "xml":
                return "application/xml";
            case "json":
                return "application/json";
            case "wav":
                return "audio/wav";
            case "mp3":
                return "audio/mpeg";
            case "flac":
                return "audio/flac";
            case "ogg":
            case "opus":
                return "audio/ogg";
            case "aac":
                return "audio/aac";
            case "m4a":
                return "audio/mp4";
            case "mp4":
            case "m4v":
                return "video/mp4";
            case "mov":
                return "video/quicktime";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return "video/webm";
            case "avi":
                return "video/x-msvideo";
            case "mpg":
            case "mpeg":
                return "video/mpeg";
            case "m2ts":
            case "mts":
                return "video/mp2t";
            case "asf":
                return "video/x-ms-asf";
            case "wmv":
                return "video/x-ms-wmv";
            case "flv":
                return "video/x-flv";
            case "3gp":
                return "video/3gpp";
            case "3g2":
                return "video/3gpp2";
            case "zip":
                return "application/zip";
            case "7z":
                return "application/x-7z-compressed";
            case "rar":
                return "application/vnd.rar";
            case "gz":
                return "application/gzip";
            case "tar":
                return "application/x-tar";
            default:
                return "application/octet-stream";
        }
    }

    static boolean fileCard(ContentMap.Card card) {
        return card.length != null
                && card.length > 0
                && (card.nativeRow >= 0 || card.kind == ContentMap.Kind.TEXT);
    }

    static PreparedExport file(
            InputSnapshot input, ContentMap.Card card, File directory, BooleanSupplier stop)
            throws Exception {
        return namedRange(input, card.range(input.size), directory, name(card), stop);
    }

    static PreparedExport namedRange(
            InputSnapshot input,
            FileRange range,
            File directory,
            String filename,
            BooleanSupplier stop)
            throws Exception {
        PreparedExport staged = OwnedContent.exportRange(input, range, directory, filename, stop);
        try {
            return PreparedExport.restore(
                    directory,
                    staged.file.getAbsolutePath(),
                    staged.name,
                    mime(filename.substring(filename.lastIndexOf('.') + 1)),
                    staged.size);
        } catch (Exception failure) {
            staged.discard();
            throw failure;
        }
    }

    /** Flat ZIP entries contain the files themselves; the manifest supplements those payloads. */
    static PreparedExport zip(
            InputSnapshot input, List<ContentMap.Card> cards, File directory, BooleanSupplier stop)
            throws Exception {
        Map<String, List<ContentMap.Card>> files = new LinkedHashMap<>();
        long total = 0;
        for (ContentMap.Card card : cards) {
            EvidenceRows.check(stop);
            if (!fileCard(card)) continue;
            FileRange range = card.range(input.size);
            range.requireOperationBudget();
            String name = name(card);
            if (!files.containsKey(name)) {
                if (files.size() >= MAX_FILES || range.length > MAX_TOTAL_BYTES - total)
                    throw new IOException(
                            "Recovered-file ZIP exceeds 4096 files / 240 MiB; export a smaller"
                                    + " group or individual files");
                total += range.length;
                files.put(name, new ArrayList<>());
            }
            files.get(name).add(card);
        }
        if (files.isEmpty())
            throw new IOException("No bounded recovered files or text runs in this group");
        input.checkIdentity(stop);
        PreparedExport stage = null;
        boolean success = false;
        try {
            stage =
                    PreparedExport.streamBytes(
                            directory,
                            "recovered-files.zip",
                            "application/zip",
                            output -> {
                                JSONArray entries = new JSONArray();
                                byte[] buffer = new byte[65536];
                                try (ZipOutputStream zip =
                                                new ZipOutputStream(
                                                        output, StandardCharsets.UTF_8);
                                        RandomAccessFile source =
                                                new RandomAccessFile(input.file, "r")) {
                                    for (Map.Entry<String, List<ContentMap.Card>> entry :
                                            files.entrySet()) {
                                        EvidenceRows.check(stop);
                                        ContentMap.Card card = entry.getValue().get(0);
                                        FileRange range = card.range(input.size);
                                        range.requireCurrentLength(source.length());
                                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                        ZipEntry item = new ZipEntry(entry.getKey());
                                        item.setTime(0);
                                        zip.putNextEntry(item);
                                        source.seek(range.offset);
                                        for (long left = range.length; left > 0; ) {
                                            EvidenceRows.check(stop);
                                            int count =
                                                    source.read(
                                                            buffer,
                                                            0,
                                                            (int) Math.min(buffer.length, left));
                                            if (count < 0)
                                                throw new EOFException(
                                                        "Recovered file shortened during export");
                                            zip.write(buffer, 0, count);
                                            digest.update(buffer, 0, count);
                                            left -= count;
                                        }
                                        zip.closeEntry();
                                        JSONArray discoveries = new JSONArray();
                                        for (ContentMap.Card finding : entry.getValue())
                                            discoveries.put(finding.json());
                                        entries.put(
                                                new JSONObject()
                                                        .put("file", entry.getKey())
                                                        .put("mime", mime(card))
                                                        .put("sizeBytes", range.length)
                                                        .put(
                                                                "sha256",
                                                                Inspector.toHex(digest.digest()))
                                                        .put("physicalOffset", range.offset)
                                                        .put("discoveries", discoveries));
                                    }
                                    ZipEntry index = new ZipEntry("_recovery.json");
                                    index.setTime(0);
                                    zip.putNextEntry(index);
                                    Writer writer =
                                            new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                                    EvidenceJson.write(
                                            writer,
                                            new JSONObject()
                                                    .put("schema", "org.diemobile.recovered-files")
                                                    .put("schemaVersion", 1)
                                                    .put("sourceSha256", input.sha256)
                                                    .put("sourceSizeBytes", input.size)
                                                    .put("files", entries)
                                                    .put(
                                                            "meaning",
                                                            "Entries contain exact stored file"
                                                                + " payloads. Text entries are"
                                                                + " bounded UTF-8 runs. Native"
                                                                + " observations do not establish"
                                                                + " complete validity or safety."),
                                            stop);
                                    writer.flush();
                                    zip.closeEntry();
                                }
                            },
                            stop);
            input.checkIdentity(stop);
            success = true;
            return stage;
        } finally {
            if (!success && stage != null) stage.discard();
        }
    }

    private RecoveredFiles() {}
}

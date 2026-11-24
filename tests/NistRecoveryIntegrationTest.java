// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPOutputStream;

/** Optional corpus test of actual production acquisition, discovery and file export. */
public final class NistRecoveryIntegrationTest {
    private static final BooleanSupplier STOP = () -> false;
    private static final String COMPRESSED_SHA =
            "91f4bc9c1a7fafdc82088a1c08a457bbfb7a9df6aa520d066054ae20b2502b50";
    private static final String DECODED_SHA =
            "20d8a231ded5223bbecab97d4155d028ef9cd0760a5dd3ecef22123c46b48870";
    private static final long BINWALK_MS = 120_000;

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Expected image and output directory");
        File source = new File(args[0]);
        Path output = Path.of(args[1]);
        require(Files.isDirectory(output), "Output directory is unavailable");
        require(
                source.length() == 19_741_341 && sha(source.toPath()).equals(COMPRESSED_SHA),
                "Input is not the pinned external L0_Video archive");
        require(PhotoRecEngine.AVAILABLE, PhotoRecEngine.ERROR);
        require(BinwalkEngine.AVAILABLE, BinwalkEngine.ERROR);
        Path temporary = Files.createTempDirectory("nist-recovery-");
        JSONObject receipt =
                new JSONObject()
                        .put("schema", "org.glassdigestor.nist-native-recovery")
                        .put("schemaVersion", 1)
                        .put("case", "L0_Video")
                        .put(
                                "environment",
                                "host JNI and unchanged production models; no Android UI or SAF")
                        .put("compressedSizeBytes", source.length())
                        .put("compressedSha256", COMPRESSED_SHA)
                        .put("binwalkAnalysisDeadlineMs", BINWALK_MS)
                        .put("nativeReportsReplayed", false)
                        .put("expectedLayoutUsedForDiscovery", false)
                        .put("androidUiExecuted", false)
                        .put("safExecuted", false);
        long started = System.nanoTime();
        try {
            Path physical = Files.createDirectory(temporary.resolve("physical"));
            Path alias = temporary.resolve("context-alias");
            Files.createSymbolicLink(alias, physical);
            try (NativeArchiveSession archive =
                    NativeArchiveSession.open(
                            source,
                            "misleading.jpeg",
                            alias.resolve("archives").toFile(),
                            0,
                            source.length(),
                            archiveRunner(),
                            STOP)) {
                NativeArchiveStore.Inventory inventory = archive.current().inventory;
                require(
                        inventory.complete && inventory.members.size() == 1,
                        "Archive did not yield one complete decoded stream");
                receipt.put("archiveInspection", inventory.json());
                try (NativeArchiveStore.Acquired decoded =
                        archive.acquire(
                                inventory.members.get(0),
                                temporary.resolve("decoded").toFile(),
                                STOP)) {
                    require(
                            decoded.input.size == 62_578_688L
                                    && decoded.input.sha256.equals(DECODED_SHA)
                                    && sha(decoded.input.file.toPath()).equals(DECODED_SHA),
                            "Decoded bytes differ from the independent archive oracle");
                    receipt.put("decodedSizeBytes", decoded.input.size)
                            .put("decodedSha256", decoded.input.sha256)
                            .put("archiveAcquisition", new JSONObject(decoded.extractionReport))
                            .put("trustedAncestorAliasAccepted", true);
                    try (GallerizeSession gallery =
                            GallerizeSession.open(
                                    decoded.input.file,
                                    temporary.resolve("gallery").toFile(),
                                    "L0_Video.dd",
                                    DECODED_SHA,
                                    decoded.input.size,
                                    null,
                                    runner("photorec", output),
                                    runner("binwalk", output),
                                    STOP)) {
                        ContentMap map = gallery.digest(STOP);
                        JSONObject provenance = gallery.provenance();
                        JSONArray discoveries = provenance.getJSONArray("discoveries");
                        require(discoveries.length() == 2, "Discovery scope changed");
                        for (int i = 0; i < discoveries.length(); i++) {
                            JSONObject discovery = discoveries.getJSONObject(i);
                            require(
                                    discovery.getBoolean("completed"),
                                    discovery.getString("engine")
                                            + ": "
                                            + discovery.getString("status"));
                        }
                        List<ContentMap.Card> files = new ArrayList<>();
                        Map<ContentMap.Kind, Integer> counts = new EnumMap<>(ContentMap.Kind.class);
                        for (ContentMap.Card card : map.cards) {
                            counts.merge(card.kind, 1, Integer::sum);
                            if (card.kind != ContentMap.Kind.TEXT && RecoveredFiles.fileCard(card))
                                files.add(card);
                        }
                        JSONObject kindCounts = new JSONObject();
                        for (Map.Entry<ContentMap.Kind, Integer> entry : counts.entrySet())
                            kindCounts.put(entry.getKey().name(), entry.getValue());
                        receipt.put("gallery", provenance)
                                .put("cardCount", map.cards.size())
                                .put("cardKinds", kindCounts)
                                .put("rawNativeRowCount", map.rawCount)
                                .put("boundedArtifactCount", map.boundedCount)
                                .put(
                                        "exportSelection",
                                        "all bounded non-text file cards; no oracle coordinates")
                                .put("selectedFileCards", files.size());
                        PreparedExport staged =
                                RecoveredFiles.zip(
                                        gallery.input,
                                        files,
                                        temporary.resolve("exports").toFile(),
                                        STOP);
                        try {
                            Path zip = output.resolve("recovered-files.zip");
                            Files.move(staged.file.toPath(), zip);
                            receipt.put("zipSizeBytes", Files.size(zip)).put("zipSha256", sha(zip));
                        } finally {
                            staged.discard();
                        }
                        try (var stream =
                                        new GZIPOutputStream(
                                                Files.newOutputStream(
                                                        output.resolve("gallery-report.json.gz"),
                                                        StandardOpenOption.CREATE_NEW));
                                var writer =
                                        new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                            gallery.writeReport(writer, STOP);
                        }
                    }
                }
            }
            require(sha(source.toPath()).equals(COMPRESSED_SHA), "Original corpus input changed");
            receipt.put("elapsedMs", (System.nanoTime() - started) / 1_000_000)
                    .put("exportPassed", true)
                    .put("exactReferenceScore", "independent score.json required");
            Files.writeString(
                    output.resolve("execution.json"),
                    receipt.toString(2) + "\n",
                    StandardOpenOption.CREATE_NEW);
            System.out.println(
                    "Actual archive → both native FD engines → gallery → recovered-file ZIP"
                        + " completed.");
            System.out.println("Exported payload scoring is a separate independent step.");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private static BinaryRunner runner(String engine, Path output) {
        return (input, stop) -> {
            EvidenceRows.check(stop);
            String encoded;
            try (RandomAccessFile file = new RandomAccessFile(input, "r")) {
                encoded =
                        engine.equals("photorec")
                                ? PhotoRecEngine.scanFd(fd(file.getFD()))
                                : BinwalkEngine.scanFdWithDeadline(fd(file.getFD()), BINWALK_MS);
            }
            EvidenceRows.check(stop);
            try (var gzip =
                    new GZIPOutputStream(
                            Files.newOutputStream(
                                    output.resolve("native-" + engine + ".json.gz"),
                                    StandardOpenOption.CREATE_NEW))) {
                gzip.write(encoded.getBytes(StandardCharsets.UTF_8));
            }
            return encoded;
        };
    }

    private static ArchiveRunner archiveRunner() {
        return new ArchiveRunner() {
            public String inspect(File input, long offset, long length, BooleanSupplier stop)
                    throws Exception {
                EvidenceRows.check(stop);
                try (RandomAccessFile source = new RandomAccessFile(input, "r")) {
                    return ArchiveEngine.inspectFd(fd(source.getFD()), offset, length);
                }
            }

            public String extract(
                    File input,
                    File target,
                    long offset,
                    long length,
                    int member,
                    BooleanSupplier stop)
                    throws Exception {
                EvidenceRows.check(stop);
                try (RandomAccessFile source = new RandomAccessFile(input, "r");
                        RandomAccessFile destination = new RandomAccessFile(target, "rw")) {
                    return ArchiveEngine.extractFd(
                            fd(source.getFD()), fd(destination.getFD()), offset, length, member);
                }
            }
        };
    }

    private static int fd(FileDescriptor descriptor) throws Exception {
        Field field = FileDescriptor.class.getDeclaredField("fd");
        field.setAccessible(true);
        return field.getInt(descriptor);
    }

    private static String sha(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) != -1; ) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

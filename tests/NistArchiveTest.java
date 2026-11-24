// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;

/** Optional external corpus regression, never packaged with the application. */
public final class NistArchiveTest {
    private static final String COMPRESSED_SHA =
            "91f4bc9c1a7fafdc82088a1c08a457bbfb7a9df6aa520d066054ae20b2502b50";
    private static final String DECODED_SHA =
            "20d8a231ded5223bbecab97d4155d028ef9cd0760a5dd3ecef22123c46b48870";

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Expected image and receipt paths");
        File source = new File(args[0]);
        require(
                source.length() == 19_741_341L && sha256(source).equals(COMPRESSED_SHA),
                "Input is not the pinned NIST L0_Video.dd.bz2");
        Path temporary = Files.createTempDirectory("nist-archive-");
        JSONObject receipt =
                new JSONObject()
                        .put("schema", "org.glassdigestor.nist-archive-regression")
                        .put("schemaVersion", 1)
                        .put("case", "L0_Video")
                        .put("environment", "host JNI; not Android UI or SAF")
                        .put("compressedSizeBytes", source.length())
                        .put("compressedSha256", COMPRESSED_SHA);
        try {
            Path physical = Files.createDirectory(temporary.resolve("physical"));
            Path alias = temporary.resolve("context-alias");
            Files.createSymbolicLink(alias, physical);
            try (NativeArchiveSession session =
                    NativeArchiveSession.open(
                            source,
                            "misleading.jpeg",
                            alias.resolve("sessions").toFile(),
                            0,
                            source.length(),
                            NativeArchiveStoreTest.NATIVE,
                            () -> false)) {
                NativeArchiveStore.Inventory inventory = session.current().inventory;
                require(
                        inventory.complete && inventory.members.size() == 1,
                        "Expected one completely inventoried decoded stream");
                require(
                        session.directory.equals(session.directory.getCanonicalFile()),
                        "Trusted Context alias was not anchored");
                receipt.put("inspection", inventory.json());
                try (NativeArchiveStore.Acquired decoded =
                        session.acquire(
                                inventory.members.get(0),
                                temporary.resolve("members").toFile(),
                                () -> false)) {
                    require(
                            decoded.input.size == 62_578_688L
                                    && decoded.input.sha256.equals(DECODED_SHA)
                                    && sha256(decoded.input.file).equals(DECODED_SHA),
                            "Actual decoded bytes do not match the independent corpus oracle");
                    decoded.input.checkIdentity(() -> false);
                    receipt.put("decodedSizeBytes", decoded.input.size)
                            .put("decodedSha256", DECODED_SHA)
                            .put("trustedAncestorAliasAccepted", true)
                            .put("archiveDecodingPassed", true)
                            .put("recoveredFileExportScored", false)
                            .put("acquisition", new JSONObject(decoded.extractionReport));
                }
            }
            require(sha256(source).equals(COMPRESSED_SHA), "Original image changed");
            Files.writeString(
                    Path.of(args[1]),
                    receipt.toString(2) + "\n",
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            System.out.println(
                    "NIST L0_Video: exact 62,578,688-byte archive decode passed; carving/export"
                        + " unscored.");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) != -1; ) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Actual source/raster ownership and Bitmap conversion; native/worker transport is substituted. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
public final class MpvFrameWorkbenchTest {
    private Path directory;
    private InputSnapshot input;
    private byte[] source;
    private AtomicReference<File> job;

    @Before
    public void setup() throws Exception {
        directory = Files.createTempDirectory("mpv-frame-helper");
        source = new byte[103];
        new Random(78124).nextBytes(source);
        Path original = directory.resolve("source.bin");
        Files.write(original, source);
        input =
                InputSnapshot.acquire(
                        original.toFile(), directory.toFile(), source.length, () -> false);
        job = new AtomicReference<>();
    }

    @After
    public void cleanup() throws Exception {
        input.close();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
        if (job.get() != null)
            assertFalse("A private MPV capture directory leaked", job.get().exists());
    }

    private String produce(File staged, File raster, int maximum) throws Exception {
        assertNotEquals(input.file.getCanonicalFile(), staged.getCanonicalFile());
        assertNotEquals(staged.getCanonicalFile(), raster.getCanonicalFile());
        assertTrue(raster.isFile());
        assertEquals(0, raster.length());
        job.set(raster.getParentFile());
        Files.write(raster.toPath(), MpvFrameFixture.RGBA);
        return MpvFrameFixture.report(staged.length(), maximum).toString();
    }

    @Test
    public void exactSelectedBytesBecomeExplicitArgbPixelsAndDetachedSourceReport()
            throws Exception {
        FileRange range = new FileRange(input.size, 17, 73);
        MpvFrame.Result result =
                MpvFrame.capture(
                        RuntimeEnvironment.getApplication(),
                        input,
                        range,
                        24,
                        true,
                        () -> false,
                        (staged, raster, maximum, stop) -> {
                            assertArrayEquals(
                                    Arrays.copyOfRange(source, 17, 90),
                                    Files.readAllBytes(staged.toPath()));
                            return produce(staged, raster, maximum);
                        });
        try {
            assertEquals(2, result.bitmap.getWidth());
            assertEquals(2, result.bitmap.getHeight());
            assertFalse(result.bitmap.hasAlpha());
            int[] actual = new int[4];
            result.bitmap.getPixels(actual, 0, 2, 0, 0, 2, 2);
            assertArrayEquals(MpvFrameFixture.ARGB, actual);
            JSONObject report = result.json();
            assertEquals(input.sha256, report.getString("sourceSha256"));
            assertEquals(103, report.getLong("sourceSizeBytes"));
            assertEquals(17, report.getLong("physicalOffset"));
            assertEquals(73, report.getLong("storedSizeBytes"));
            assertEquals(
                    hex(Arrays.copyOfRange(source, 17, 90)),
                    report.getString("decodedInputSha256"));
            assertEquals(hex(MpvFrameFixture.RGBA), report.getString("rasterSha256"));
            assertEquals(
                    "external-host-runtime",
                    report.getJSONObject("nativeReport").getString("engineBinaryOrigin"));
            assertTrue(
                    report.getJSONObject("nativeReport").getJSONArray("unknownRaw").length() == 2);
            assertTrue(result.description().contains("MPV still image"));
            report.put("rasterSha256", "changed");
            assertEquals(hex(MpvFrameFixture.RGBA), result.json().getString("rasterSha256"));
            input.checkIdentity(() -> false);
            assertFalse(job.get().exists());
        } finally {
            result.bitmap.recycle();
        }
    }

    @Test
    public void failedMalformedAndChangedSourcesRemoveEveryTemporaryWithoutPublishing()
            throws Exception {
        FileRange range = new FileRange(input.size, 17, 73);
        for (String failure :
                new String[] {
                    "throw",
                    "native-failure",
                    "truncated",
                    "alpha",
                    "stage-changed",
                    "parent-changed"
                }) {
            byte[] saved = Files.readAllBytes(input.file.toPath());
            try {
                assertThrows(
                        Exception.class,
                        () ->
                                MpvFrame.capture(
                                        RuntimeEnvironment.getApplication(),
                                        input,
                                        range,
                                        24,
                                        true,
                                        () -> false,
                                        (staged, raster, maximum, stop) -> {
                                            String report = produce(staged, raster, maximum);
                                            switch (failure) {
                                                case "throw":
                                                    throw new IOException(
                                                            "Transport fixture failure after"
                                                                + " partial output");
                                                case "native-failure":
                                                    return "{\"ok\":false,\"error\":\"codec"
                                                               + " unavailable\"}";
                                                case "truncated":
                                                    Files.write(raster.toPath(), new byte[3]);
                                                    break;
                                                case "alpha":
                                                    {
                                                        byte[] rgba = MpvFrameFixture.RGBA.clone();
                                                        rgba[3] = 0;
                                                        Files.write(raster.toPath(), rgba);
                                                        break;
                                                    }
                                                case "stage-changed":
                                                    {
                                                        byte[] bytes =
                                                                Files.readAllBytes(staged.toPath());
                                                        bytes[0] ^= 1;
                                                        Files.write(staged.toPath(), bytes);
                                                        break;
                                                    }
                                                case "parent-changed":
                                                    {
                                                        byte[] bytes = saved.clone();
                                                        bytes[0] ^= 1;
                                                        Files.write(input.file.toPath(), bytes);
                                                        break;
                                                    }
                                                default:
                                                    fail("Unknown failure fixture");
                                            }
                                            return report;
                                        }));
                assertFalse(failure + " leaked a candidate directory", job.get().exists());
            } finally {
                Files.write(input.file.toPath(), saved);
            }
        }
        input.checkIdentity(() -> false);
    }

    @Test
    public void cancellationAfterWorkerAndAfterCleanupCannotPublishABitmap() throws Exception {
        FileRange range = new FileRange(input.size, 0, input.size);
        AtomicBoolean cancelled = new AtomicBoolean();
        assertThrows(
                InterruptedIOException.class,
                () ->
                        MpvFrame.capture(
                                RuntimeEnvironment.getApplication(),
                                input,
                                range,
                                24,
                                true,
                                cancelled::get,
                                (staged, raster, maximum, stop) -> {
                                    String report = produce(staged, raster, maximum);
                                    cancelled.set(true);
                                    return report;
                                }));
        assertFalse(job.get().exists());
        job.set(null);
        // This flips only after serialization, all source/pixel checks and candidate cleanup.
        assertThrows(
                InterruptedIOException.class,
                () ->
                        MpvFrame.capture(
                                RuntimeEnvironment.getApplication(),
                                input,
                                range,
                                24,
                                true,
                                () -> job.get() != null && !job.get().exists(),
                                (staged, raster, maximum, stop) ->
                                        produce(staged, raster, maximum)));
        assertFalse(job.get().exists());
        input.checkIdentity(() -> false);
    }

    @Test
    public void invalidEmptyAndForeignRangesNeverReachTheTransport() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MpvFrameRunner forbidden =
                (staged, raster, maximum, stop) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("Invalid source reached transport");
                };
        assertThrows(
                IOException.class,
                () ->
                        MpvFrame.capture(
                                RuntimeEnvironment.getApplication(),
                                input,
                                new FileRange(input.size, 0, 0),
                                24,
                                true,
                                () -> false,
                                forbidden));
        assertThrows(
                IOException.class,
                () ->
                        MpvFrame.capture(
                                RuntimeEnvironment.getApplication(),
                                input,
                                new FileRange(input.size + 1, 0, 10),
                                24,
                                true,
                                () -> false,
                                forbidden));
        assertThrows(
                IOException.class,
                () ->
                        MpvFrame.capture(
                                RuntimeEnvironment.getApplication(),
                                input,
                                new FileRange(input.size, 0, 10),
                                1537,
                                true,
                                () -> false,
                                forbidden));
        assertThrows(
                InterruptedIOException.class,
                () ->
                        MpvFrame.capture(
                                RuntimeEnvironment.getApplication(),
                                input,
                                new FileRange(input.size, 0, 10),
                                24,
                                true,
                                () -> true,
                                forbidden));
        assertEquals(0, calls.get());
    }

    @Test
    public void automaticAcquisitionCapIsSeparateFromTheExplicitInputAndPixelBounds()
            throws Exception {
        long size = 33L * 1024 * 1024;
        File largeFile = directory.resolve("analysis-input-large.snapshot").toFile();
        try (RandomAccessFile sparse = new RandomAccessFile(largeFile, "rw")) {
            sparse.setLength(size);
        }
        String sha = fileHash(largeFile);
        try (InputSnapshot large =
                InputSnapshot.restore(largeFile, directory.toFile(), size, sha, () -> false)) {
            AtomicInteger calls = new AtomicInteger();
            FileRange range = new FileRange(size, 0, size);
            IOException refusal =
                    assertThrows(
                            IOException.class,
                            () ->
                                    MpvFrame.capture(
                                            RuntimeEnvironment.getApplication(),
                                            large,
                                            range,
                                            24,
                                            true,
                                            () -> false,
                                            (staged, raster, maximum, stop) -> {
                                                calls.incrementAndGet();
                                                throw new AssertionError(
                                                        "Automatic large input reached transport");
                                            }));
            assertTrue(refusal.getMessage().contains("32 MiB"));
            assertEquals(0, calls.get());
            MpvFrame.Result explicit =
                    MpvFrame.capture(
                            RuntimeEnvironment.getApplication(),
                            large,
                            range,
                            24,
                            false,
                            () -> false,
                            (staged, raster, maximum, stop) -> {
                                assertEquals(size, staged.length());
                                return produce(staged, raster, maximum);
                            });
            try {
                assertFalse(explicit.json().getBoolean("automatic"));
                assertEquals(size, explicit.json().getLong("decodedInputSizeBytes"));
                assertEquals(
                        33554432,
                        explicit.json().getJSONObject("nativeReport").getLong("maxReadBytes"));
            } finally {
                explicit.bitmap.recycle();
            }
        }
    }

    private static String hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String fileHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[65536];
            int count;
            while ((count = input.read(bytes)) >= 0) digest.update(bytes, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}

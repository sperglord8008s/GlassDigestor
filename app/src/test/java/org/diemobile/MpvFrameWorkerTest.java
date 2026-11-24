// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.system.OsConstants;
import android.system.StructStat;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Message/descriptor/refusal contracts only; this does not execute an isolated OS UID or MPV. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class MpvFrameWorkerTest {
    private Path directory;
    private File source, raster, report;

    @Before
    public void setup() throws Exception {
        directory = Files.createTempDirectory("mpv-frame-protocol");
        source = directory.resolve("source.bin").toFile();
        raster = directory.resolve("pixels.rgba").toFile();
        report = directory.resolve("report.json").toFile();
        Files.write(source.toPath(), new byte[] {1, 2, 3});
        assertTrue(raster.createNewFile());
    }

    @After
    public void cleanup() throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    @Test
    public void frameMaximumIsRequiredAnIntegerAndAnEdgeWithin1536Pixels() throws Exception {
        for (Object value : new Object[] {null, 24L, 24.0, "24", -1, 0, 1537, Integer.MAX_VALUE}) {
            Bundle request = new Bundle();
            if (value instanceof Long) request.putLong("maximumDimensionPixels", (Long) value);
            else if (value instanceof Double)
                request.putDouble("maximumDimensionPixels", (Double) value);
            else if (value instanceof String)
                request.putString("maximumDimensionPixels", (String) value);
            else if (value != null) request.putInt("maximumDimensionPixels", (Integer) value);
            assertThrows(IOException.class, () -> BinaryWorkerService.frameMaximum(request));
        }
        for (int edge : new int[] {1, 24, 1536}) {
            Bundle request = new Bundle();
            request.putInt("maximumDimensionPixels", edge);
            assertEquals(edge, BinaryWorkerService.frameMaximum(request));
        }
        AnalysisTimeLimits.Request limits =
                AnalysisTimeLimits.capture(RuntimeEnvironment.getApplication(), "mpv-frame");
        assertEquals(0, limits.analysisDeadlineMs);
        assertEquals(45000, limits.requestDeadlineMs);
        Bundle message = new Bundle();
        limits.writeTo(message);
        assertEquals(45000, AnalysisTimeLimits.fromMessage(message, "mpv-frame").requestDeadlineMs);
    }

    @Test
    public void frameResponsesMustMatchTheCapturedTokenEngineAndMaximum() throws Exception {
        JSONObject envelope =
                new JSONObject()
                        .put("engine", "mpv-frame")
                        .put("requestToken", "exact-token")
                        .put("maximumDimensionPixels", 24);
        BinaryWorkerClient.validateFrameEnvelope(envelope, "exact-token", 24);
        assertThrows(
                IOException.class,
                () -> BinaryWorkerClient.validateFrameEnvelope(envelope, "another-token", 24));
        assertThrows(
                IOException.class,
                () -> BinaryWorkerClient.validateFrameEnvelope(envelope, "exact-token", 25));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerClient.validateFrameEnvelope(
                                new JSONObject(envelope.toString())
                                        .put("maximumDimensionPixels", 24.5),
                                "exact-token",
                                24));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerClient.validateFrameEnvelope(
                                new JSONObject(envelope.toString()).put("engine", "mpv"),
                                "exact-token",
                                24));
    }

    private ParcelFileDescriptor open(File file, int mode) throws Exception {
        return ParcelFileDescriptor.open(file, mode);
    }

    private Message request(ParcelFileDescriptor pixels) throws Exception {
        Message request = Message.obtain(null, BinaryWorkerService.RUN);
        Bundle data = request.getData();
        data.putString("token", "frame-protocol");
        data.putString("engine", "mpv-frame");
        data.putInt("maximumDimensionPixels", 24);
        data.putInt("member", -1);
        data.putLong("offset", 0);
        data.putLong("length", source.length());
        data.putParcelable("input", open(source, ParcelFileDescriptor.MODE_READ_ONLY));
        data.putParcelable("raster", pixels);
        data.putParcelable(
                "output",
                open(
                        report,
                        ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_WRITE_ONLY
                                | ParcelFileDescriptor.MODE_TRUNCATE));
        return request;
    }

    private JSONObject send(ServiceController<BinaryWorkerService> controller, Message message)
            throws Exception {
        new Messenger(controller.get().onBind(new Intent())).send(message);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return new JSONObject(Files.readString(report.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void ordinaryUidCannotProducePixelsAndBothOutputDescriptorsClose() throws Exception {
        ApplicationInfo application = RuntimeEnvironment.getApplication().getApplicationInfo();
        int original = application.uid;
        application.uid = android.os.Process.myUid();
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        ParcelFileDescriptor pixels = open(raster, ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            Message request = request(pixels);
            ParcelFileDescriptor envelope = request.getData().getParcelable("output");
            JSONObject response = send(controller, request);
            assertFalse(response.getBoolean("isolatedUid"));
            assertFalse(response.getJSONObject("report").getBoolean("ok"));
            assertTrue(
                    response.getJSONObject("report").getString("error").contains("isolated UID"));
            assertEquals("mpv-frame", response.getString("engine"));
            assertEquals(0, raster.length());
            assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(source.toPath()));
            assertThrows(IllegalStateException.class, pixels::getFd);
            assertThrows(IllegalStateException.class, envelope::getFd);
        } finally {
            pixels.close();
            controller.destroy();
            application.uid = original;
        }
    }

    @Test
    public void missingInputClosesTheNewRasterDescriptorWithoutNativeWork() throws Exception {
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        ParcelFileDescriptor pixels = open(raster, ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            Message request = request(pixels);
            ParcelFileDescriptor sourceFd = request.getData().getParcelable("input");
            sourceFd.close();
            request.getData().remove("input");
            ParcelFileDescriptor envelope = request.getData().getParcelable("output");
            new Messenger(controller.get().onBind(new Intent())).send(request);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertThrows(IllegalStateException.class, pixels::getFd);
            assertThrows(IllegalStateException.class, envelope::getFd);
            assertEquals(0, raster.length());
        } finally {
            pixels.close();
            controller.destroy();
        }
    }

    @Test
    public void malformedFrameParametersCannotReachNativeAndReleaseTheirPixels() throws Exception {
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        // Simulated policy flag permits testing the pre-native refusal path, not an actual UID.
        ReflectionHelpers.setField(controller.get(), "isolated", true);
        try {
            for (String failure :
                    new String[] {
                        "missing-maximum",
                        "typed-maximum",
                        "oversized-maximum",
                        "missing-raster",
                        "range",
                        "member"
                    }) {
                ParcelFileDescriptor pixels = open(raster, ParcelFileDescriptor.MODE_WRITE_ONLY);
                Message request = request(pixels);
                Bundle data = request.getData();
                switch (failure) {
                    case "missing-maximum":
                        data.remove("maximumDimensionPixels");
                        break;
                    case "typed-maximum":
                        data.putLong("maximumDimensionPixels", 24);
                        break;
                    case "oversized-maximum":
                        data.putInt("maximumDimensionPixels", 1537);
                        break;
                    case "missing-raster":
                        data.remove("raster");
                        pixels.close();
                        break;
                    case "range":
                        data.putLong("offset", 1);
                        break;
                    case "member":
                        data.putInt("member", 0);
                        break;
                    default:
                        fail("Unknown fixture");
                }
                JSONObject response = send(controller, request);
                assertFalse(response.getJSONObject("report").getBoolean("ok"));
                assertFalse(
                        response.getJSONObject("report")
                                .getString("error")
                                .contains("native library"));
                assertEquals(0, raster.length());
                assertThrows(IllegalStateException.class, pixels::getFd);
            }
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void clientRequiresTheExplicitEngineFreshOutputAndValidEdgeBeforeBinding()
            throws Exception {
        try (BinaryWorkerClient frames =
                        new BinaryWorkerClient(RuntimeEnvironment.getApplication(), "mpv-frame");
                BinaryWorkerClient metadata =
                        new BinaryWorkerClient(RuntimeEnvironment.getApplication(), "mpv")) {
            assertThrows(IOException.class, () -> frames.scan(source, () -> false));
            assertThrows(
                    IOException.class, () -> metadata.capture(source, raster, 24, () -> false));
            assertThrows(
                    IOException.class, () -> frames.capture(source, raster, 1537, () -> false));
            Files.write(raster.toPath(), new byte[] {77});
            assertThrows(IOException.class, () -> frames.capture(source, raster, 24, () -> false));
            assertArrayEquals(new byte[] {77}, Files.readAllBytes(raster.toPath()));
            assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(source.toPath()));
        }
    }

    @Test
    public void descriptorsRequireReadOnlyInputAndFreshWritableDistinctRaster() throws Exception {
        // Stock Robolectric fstat/fcntl observations are placeholders. Supply explicit facts;
        // this checks the Java rule, while real descriptor behavior remains an Android gate.
        StructStat source = facts(11, 3), pixels = facts(12, 0), envelope = facts(13, 0);
        BinaryWorkerService.validateFrameDescriptorFacts(
                source, OsConstants.O_RDONLY, pixels, OsConstants.O_WRONLY, envelope);
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateFrameDescriptorFacts(
                                source,
                                OsConstants.O_RDWR,
                                pixels,
                                OsConstants.O_WRONLY,
                                envelope));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateFrameDescriptorFacts(
                                source,
                                OsConstants.O_RDONLY,
                                pixels,
                                OsConstants.O_RDONLY,
                                envelope));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateFrameDescriptorFacts(
                                source,
                                OsConstants.O_RDONLY,
                                pixels,
                                OsConstants.O_WRONLY | OsConstants.O_APPEND,
                                envelope));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateFrameDescriptorFacts(
                                source,
                                OsConstants.O_RDONLY,
                                envelope,
                                OsConstants.O_WRONLY,
                                envelope));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateFrameDescriptorFacts(
                                source,
                                OsConstants.O_RDONLY,
                                facts(11, 0),
                                OsConstants.O_WRONLY,
                                envelope));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateFrameDescriptorFacts(
                                source,
                                OsConstants.O_RDONLY,
                                facts(12, 1),
                                OsConstants.O_WRONLY,
                                envelope));
    }

    private static StructStat facts(long inode, long size) {
        return new StructStat(
                1, inode, OsConstants.S_IFREG | 0600, 1, 1, 1, 0, size, 0, 0, 0, 4096, 1);
    }
}

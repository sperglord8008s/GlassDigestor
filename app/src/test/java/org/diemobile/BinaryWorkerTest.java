// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.system.OsConstants;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Manifest/refusal and real descriptor closure with explicit facts; no isolated OS UID is
 * simulated.
 */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class BinaryWorkerTest {
    @Test
    public void workerIsPrivateAndRequestsASeparateIsolatedProcess() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ServiceInfo info =
                context.getPackageManager()
                        .getServiceInfo(new ComponentName(context, BinaryWorkerService.class), 0);
        assertFalse(info.exported);
        assertTrue((info.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0);
        assertTrue(info.processName.endsWith(":binary_worker"));
    }

    @Test
    public void ordinaryUidReceivesAnErrorWithoutNativeFallback() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ApplicationInfo info = context.getApplicationInfo();
        int previous = info.uid;
        info.uid = android.os.Process.myUid();
        Path directory = Files.createTempDirectory("die-worker-guard"),
                input = directory.resolve("input.bin"),
                output = directory.resolve("report.json");
        Files.write(input, new byte[] {1, 2, 3});
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        try {
            for (String engine :
                    new String[] {"binwalk", "photorec", "mpv", "resources", "hashes", "archive"}) {
                Message request = Message.obtain(null, BinaryWorkerService.RUN);
                request.getData().putString("token", "guard-fixture");
                request.getData()
                        .putParcelable(
                                "input",
                                ParcelFileDescriptor.open(
                                        input.toFile(), ParcelFileDescriptor.MODE_READ_ONLY));
                request.getData().putString("engine", engine);
                request.getData()
                        .putParcelable(
                                "output",
                                ParcelFileDescriptor.open(
                                        output.toFile(),
                                        ParcelFileDescriptor.MODE_CREATE
                                                | ParcelFileDescriptor.MODE_WRITE_ONLY
                                                | ParcelFileDescriptor.MODE_TRUNCATE));
                WorkerDescriptorFactsFixture.safe(request.getData()).attach(controller.get());
                new Messenger(controller.get().onBind(new Intent())).send(request);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                JSONObject report =
                        new JSONObject(
                                new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
                assertFalse(report.getBoolean("isolatedUid"));
                assertFalse(report.getJSONObject("report").getBoolean("ok"));
                assertTrue(
                        report.getJSONObject("report").getString("error").contains("isolated UID"));
                assertEquals(engine, report.getString("engine"));
            }
        } finally {
            controller.destroy();
            info.uid = previous;
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void malformedArchiveRequestClosesBothOutputDescriptors() throws Exception {
        Path directory = Files.createTempDirectory("archive-malformed-worker");
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        ParcelFileDescriptor report =
                ParcelFileDescriptor.open(
                        directory.resolve("report").toFile(),
                        ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
        ParcelFileDescriptor payload =
                ParcelFileDescriptor.open(
                        directory.resolve("payload").toFile(),
                        ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            Message request = Message.obtain(null, BinaryWorkerService.RUN);
            request.getData().putString("engine", "archive");
            request.getData().putString("token", "malformed-archive");
            request.getData().putParcelable("output", report);
            request.getData().putParcelable("payload", payload);
            new Messenger(controller.get().onBind(new Intent())).send(request);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertThrows(IllegalStateException.class, report::getFd);
            assertThrows(IllegalStateException.class, payload::getFd);
        } finally {
            report.close();
            payload.close();
            controller.destroy();
            Files.deleteIfExists(directory.resolve("report"));
            Files.deleteIfExists(directory.resolve("payload"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void ordinaryUidCannotWriteArchivePayloadAndReleasesTheDescriptor() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ApplicationInfo info = context.getApplicationInfo();
        int previous = info.uid;
        info.uid = android.os.Process.myUid();
        Path directory = Files.createTempDirectory("archive-worker-payload-guard");
        Path source = directory.resolve("input"),
                target = directory.resolve("payload"),
                report = directory.resolve("report");
        Files.write(source, new byte[] {1, 2, 3});
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        ParcelFileDescriptor payload =
                ParcelFileDescriptor.open(
                        target.toFile(),
                        ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            Message request = Message.obtain(null, BinaryWorkerService.RUN);
            Bundle data = request.getData();
            data.putString("engine", "archive");
            data.putString("token", "ordinary-archive");
            data.putInt("member", 0);
            data.putLong("offset", 0);
            data.putLong("length", 3);
            data.putParcelable(
                    "input",
                    ParcelFileDescriptor.open(
                            source.toFile(), ParcelFileDescriptor.MODE_READ_ONLY));
            data.putParcelable("payload", payload);
            data.putParcelable(
                    "output",
                    ParcelFileDescriptor.open(
                            report.toFile(),
                            ParcelFileDescriptor.MODE_CREATE
                                    | ParcelFileDescriptor.MODE_WRITE_ONLY));
            WorkerDescriptorFactsFixture.safe(data).attach(controller.get());
            new Messenger(controller.get().onBind(new Intent())).send(request);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            JSONObject response =
                    new JSONObject(new String(Files.readAllBytes(report), StandardCharsets.UTF_8));
            assertFalse(response.getBoolean("isolatedUid"));
            assertEquals("archive", response.getString("engine"));
            assertTrue(
                    response.getJSONObject("report").getString("error").contains("isolated UID"));
            assertEquals(0, Files.size(target));
            assertThrows(IllegalStateException.class, payload::getFd);
        } finally {
            payload.close();
            controller.destroy();
            info.uid = previous;
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(report);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void replyFactsAcceptDistinctRegularFilesAndPrefilledWritablePipes() throws Exception {
        BinaryWorkerService.DescriptorFacts empty =
                WorkerDescriptorFactsFixture.regular(1, 10, 0, OsConstants.O_RDONLY);
        BinaryWorkerService.DescriptorFacts regular =
                WorkerDescriptorFactsFixture.regular(1, 11, 100, OsConstants.O_RDWR);
        BinaryWorkerService.validateReportDescriptorFacts(empty, regular, null, null);
        BinaryWorkerService.DescriptorFacts pipe =
                WorkerDescriptorFactsFixture.facts(
                        2,
                        12,
                        OsConstants.S_IFIFO | 0600,
                        65536,
                        OsConstants.O_WRONLY | OsConstants.O_NONBLOCK);
        BinaryWorkerService.validateReportDescriptorFacts(
                empty,
                pipe,
                WorkerDescriptorFactsFixture.regular(1, 13, 5, OsConstants.O_WRONLY),
                WorkerDescriptorFactsFixture.regular(1, 14, 9, OsConstants.O_WRONLY));
        // Equal inode numbers on different devices are not the same kernel object.
        BinaryWorkerService.validateReportDescriptorFacts(
                empty,
                WorkerDescriptorFactsFixture.regular(2, 10, 0, OsConstants.O_WRONLY),
                null,
                null);
    }

    @Test
    public void replyFactsRejectAllDataAliasesAndUnavailableOrUnwritableObservations()
            throws Exception {
        BinaryWorkerService.DescriptorFacts source =
                WorkerDescriptorFactsFixture.regular(1, 10, 0, OsConstants.O_RDONLY);
        BinaryWorkerService.DescriptorFacts output =
                WorkerDescriptorFactsFixture.regular(1, 11, 0, OsConstants.O_WRONLY);
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateReportDescriptorFacts(
                                source,
                                WorkerDescriptorFactsFixture.regular(
                                        1, 10, 0, OsConstants.O_WRONLY),
                                null,
                                null));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateReportDescriptorFacts(
                                source, output, output, null));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateReportDescriptorFacts(
                                source, output, null, output));
        for (int flags : new int[] {OsConstants.O_RDONLY, OsConstants.O_ACCMODE, -1})
            assertThrows(
                    IOException.class,
                    () ->
                            BinaryWorkerService.validateReportDescriptorFacts(
                                    source,
                                    WorkerDescriptorFactsFixture.regular(1, 11, 0, flags),
                                    null,
                                    null));
        assertThrows(
                IOException.class,
                () -> BinaryWorkerService.validateReportDescriptorFacts(null, output, null, null));
        assertThrows(
                IOException.class,
                () -> BinaryWorkerService.validateReportDescriptorFacts(source, null, null, null));
        assertThrows(
                IOException.class,
                () ->
                        BinaryWorkerService.validateReportDescriptorFacts(
                                source,
                                new BinaryWorkerService.DescriptorFacts(null, OsConstants.O_WRONLY),
                                null,
                                null));
    }

    @Test
    public void sourceReplyAliasesNeverWriteEvenForOrdinaryMalformedUnsupportedAndBusyRequests()
            throws Exception {
        Path directory = Files.createTempDirectory("worker-report-source-alias");
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        try {
            for (boolean empty : new boolean[] {false, true}) {
                for (String state : new String[] {"ordinary", "malformed", "unsupported", "busy"}) {
                    byte[] expected =
                            empty
                                    ? new byte[0]
                                    : "source bytes must stay exact"
                                            .getBytes(StandardCharsets.UTF_8);
                    File source = directory.resolve("source").toFile();
                    Files.write(source.toPath(), expected);
                    Message request = guardedRequest(directory, source, source);
                    Bundle data = request.getData();
                    data.putString(
                            "engine",
                            state.equals("unsupported")
                                    ? "unknown-engine"
                                    : state.equals("malformed") ? "mpv-frame" : "hashes");
                    if (state.equals("malformed")) data.putLong("maximumDimensionPixels", 24);
                    ReflectionHelpers.setField(
                            controller.get(), "isolated", !state.equals("ordinary"));
                    String active = state.equals("busy") ? "prior-request" : null;
                    ReflectionHelpers.setField(controller.get(), "activeToken", active);
                    List<ParcelFileDescriptor> descriptors = requestDescriptors(data);
                    ParcelFileDescriptor output = data.getParcelable("output");
                    WorkerDescriptorFactsFixture fixture =
                            WorkerDescriptorFactsFixture.safe(data)
                                    .put(
                                            output,
                                            WorkerDescriptorFactsFixture.regular(
                                                    1, 100, expected.length, OsConstants.O_WRONLY));
                    dispatch(controller, request, fixture);
                    assertArrayEquals(
                            state + " serialized into its source",
                            expected,
                            Files.readAllBytes(source.toPath()));
                    assertEquals(
                            active,
                            ReflectionHelpers.<String>getField(controller.get(), "activeToken"));
                    assertClosed(descriptors);
                    assertEquals(0, Files.size(directory.resolve("payload")));
                    assertEquals(0, Files.size(directory.resolve("raster")));
                }
            }
        } finally {
            controller.destroy();
            remove(directory);
        }
    }

    @Test
    public void replyAliasesWithPayloadOrRasterDoNotReceiveEvenARejectionEnvelope()
            throws Exception {
        Path directory = Files.createTempDirectory("worker-report-payload-alias");
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        try {
            ReflectionHelpers.setField(controller.get(), "isolated", false);
            for (String role : new String[] {"payload", "raster"}) {
                File source = directory.resolve("source").toFile();
                Files.write(source.toPath(), new byte[] {1, 2, 3});
                File aliased = directory.resolve(role).toFile();
                Files.write(aliased.toPath(), new byte[0]);
                Message request = guardedRequest(directory, source, aliased);
                Bundle data = request.getData();
                List<ParcelFileDescriptor> descriptors = requestDescriptors(data);
                WorkerDescriptorFactsFixture fixture = WorkerDescriptorFactsFixture.safe(data);
                long inode = role.equals("payload") ? 102 : 103;
                fixture.put(
                        data.getParcelable("output"),
                        WorkerDescriptorFactsFixture.regular(1, inode, 0, OsConstants.O_WRONLY));
                dispatch(controller, request, fixture);
                assertEquals(0, aliased.length());
                assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(source.toPath()));
                assertClosed(descriptors);
            }
        } finally {
            controller.destroy();
            remove(directory);
        }
    }

    @Test
    public void descriptorFlagAndInspectionFailuresCloseAllChannelsWithoutJson() throws Exception {
        Path directory = Files.createTempDirectory("worker-report-inspection-failure");
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        try {
            ReflectionHelpers.setField(controller.get(), "isolated", false);
            for (String failure :
                    new String[] {
                        "read-only",
                        "bad-access",
                        "negative-flags",
                        "input",
                        "output",
                        "payload",
                        "raster",
                        "unknown-payload",
                        "unknown-raster"
                    }) {
                File source = directory.resolve("source").toFile(),
                        report = directory.resolve("report").toFile();
                Files.write(source.toPath(), new byte[] {9, 8, 7});
                Files.write(report.toPath(), new byte[0]);
                Message request = guardedRequest(directory, source, report);
                Bundle data = request.getData();
                List<ParcelFileDescriptor> descriptors = requestDescriptors(data);
                WorkerDescriptorFactsFixture fixture = WorkerDescriptorFactsFixture.safe(data);
                if (failure.equals("read-only")
                        || failure.equals("bad-access")
                        || failure.equals("negative-flags")) {
                    int flags =
                            failure.equals("read-only")
                                    ? OsConstants.O_RDONLY
                                    : failure.equals("bad-access") ? OsConstants.O_ACCMODE : -1;
                    fixture.put(
                            data.getParcelable("output"),
                            WorkerDescriptorFactsFixture.regular(1, 101, 0, flags));
                } else if (failure.startsWith("unknown-")) {
                    fixture.put(data.getParcelable(failure.substring(8)), null);
                } else fixture.failOnInspection(data.getParcelable(failure), 1);
                dispatch(controller, request, fixture);
                assertEquals(failure + " wrote an error envelope", 0, report.length());
                assertArrayEquals(new byte[] {9, 8, 7}, Files.readAllBytes(source.toPath()));
                assertClosed(descriptors);
            }
        } finally {
            controller.destroy();
            remove(directory);
        }
    }

    @Test
    public void lateInspectionFailureCannotSerializeThePreparedErrorOrClearAnotherToken()
            throws Exception {
        Path directory = Files.createTempDirectory("worker-report-late-inspection");
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        try {
            ReflectionHelpers.setField(controller.get(), "isolated", false);
            ReflectionHelpers.setField(controller.get(), "activeToken", "ongoing-request");
            File source = directory.resolve("source").toFile(),
                    report = directory.resolve("report").toFile();
            Files.write(source.toPath(), new byte[] {5, 4, 3});
            Message request = guardedRequest(directory, source, report);
            Bundle data = request.getData();
            List<ParcelFileDescriptor> descriptors = requestDescriptors(data);
            WorkerDescriptorFactsFixture fixture =
                    WorkerDescriptorFactsFixture.safe(data)
                            .failOnInspection(data.getParcelable("output"), 3);
            dispatch(controller, request, fixture);
            assertEquals(0, report.length());
            assertArrayEquals(new byte[] {5, 4, 3}, Files.readAllBytes(source.toPath()));
            assertEquals(
                    "ongoing-request",
                    ReflectionHelpers.<String>getField(controller.get(), "activeToken"));
            assertClosed(descriptors);
        } finally {
            controller.destroy();
            remove(directory);
        }
    }

    @Test
    public void malformedDescriptorAndScalarTypesStillReleaseEveryKnownDescriptor()
            throws Exception {
        Path directory = Files.createTempDirectory("worker-report-malformed-types");
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        try {
            ReflectionHelpers.setField(controller.get(), "isolated", false);
            for (String key :
                    new String[] {"input", "output", "payload", "raster", "token", "engine"}) {
                File source = directory.resolve("source").toFile(),
                        report = directory.resolve("report").toFile();
                Files.write(source.toPath(), new byte[] {2, 4, 6});
                Files.write(report.toPath(), new byte[0]);
                Message request = guardedRequest(directory, source, report);
                Bundle data = request.getData();
                List<ParcelFileDescriptor> descriptors = requestDescriptors(data);
                WorkerDescriptorFactsFixture fixture = WorkerDescriptorFactsFixture.safe(data);
                if (!key.equals("token") && !key.equals("engine")) {
                    // A replaced descriptor is no longer sent. The other three must still close.
                    ((ParcelFileDescriptor) data.get(key)).close();
                }
                data.putParcelable(key, new Intent("not-a-descriptor-or-string"));
                dispatch(controller, request, fixture);
                assertEquals(0, report.length());
                assertArrayEquals(new byte[] {2, 4, 6}, Files.readAllBytes(source.toPath()));
                assertClosed(descriptors);
            }
        } finally {
            controller.destroy();
            remove(directory);
        }
    }

    @Test
    public void distinctSafeReplyStillExplainsOrdinaryUidRefusalForAnEmptyInput() throws Exception {
        Path directory = Files.createTempDirectory("worker-empty-safe-reply");
        ServiceController<BinaryWorkerService> controller =
                Robolectric.buildService(BinaryWorkerService.class).create();
        try {
            ReflectionHelpers.setField(controller.get(), "isolated", false);
            File source = directory.resolve("source").toFile(),
                    report = directory.resolve("report").toFile();
            Files.write(source.toPath(), new byte[0]);
            Message request = guardedRequest(directory, source, report);
            List<ParcelFileDescriptor> descriptors = requestDescriptors(request.getData());
            dispatch(controller, request, WorkerDescriptorFactsFixture.safe(request.getData()));
            JSONObject reply =
                    new JSONObject(new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8));
            assertFalse(reply.getBoolean("isolatedUid"));
            assertTrue(reply.getJSONObject("report").getString("error").contains("isolated UID"));
            assertEquals(0, source.length());
            assertClosed(descriptors);
        } finally {
            controller.destroy();
            remove(directory);
        }
    }

    private static Message guardedRequest(Path directory, File source, File report)
            throws Exception {
        Message request = Message.obtain(null, BinaryWorkerService.RUN);
        Bundle data = request.getData();
        data.putString("token", "unsafe-reply-test");
        data.putString("engine", "hashes");
        data.putParcelable(
                "input", ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY));
        // Deliberately do not truncate: source/report alias tests must detect the actual write.
        data.putParcelable(
                "output",
                ParcelFileDescriptor.open(
                        report,
                        ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_WRITE_ONLY));
        for (String key : new String[] {"payload", "raster"}) {
            File file = directory.resolve(key).toFile();
            data.putParcelable(
                    key,
                    ParcelFileDescriptor.open(
                            file,
                            ParcelFileDescriptor.MODE_CREATE
                                    | ParcelFileDescriptor.MODE_WRITE_ONLY));
        }
        return request;
    }

    private static List<ParcelFileDescriptor> requestDescriptors(Bundle request) {
        List<ParcelFileDescriptor> descriptors = new ArrayList<>();
        for (String key : new String[] {"input", "output", "payload", "raster"}) {
            ParcelFileDescriptor descriptor = request.getParcelable(key);
            if (descriptor != null) descriptors.add(descriptor);
        }
        return descriptors;
    }

    private static void assertClosed(List<ParcelFileDescriptor> descriptors) {
        for (ParcelFileDescriptor descriptor : descriptors)
            assertThrows(IllegalStateException.class, descriptor::getFd);
    }

    private static void dispatch(
            ServiceController<BinaryWorkerService> controller,
            Message request,
            WorkerDescriptorFactsFixture facts)
            throws Exception {
        facts.attach(controller.get());
        new Messenger(controller.get().onBind(new Intent())).send(request);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static void remove(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(file);
        }
    }
}

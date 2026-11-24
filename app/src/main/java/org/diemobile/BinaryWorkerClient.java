// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.*;
import android.os.*;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Pipe reports avoid Binder's transaction-size limit; cancellation targets one job token. */
final class BinaryWorkerClient implements BinaryRunner, ArchiveRunner, MpvFrameRunner {
    private final Context context;
    private final String engine;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean();

    BinaryWorkerClient(Context context) {
        this(context, "binwalk");
    }

    BinaryWorkerClient(Context context, String engine) {
        if (!engine.equals("binwalk")
                && !engine.equals("photorec")
                && !engine.equals("mpv")
                && !engine.equals("mpv-frame")
                && !engine.equals("resources")
                && !engine.equals("hashes")
                && !engine.equals("archive"))
            throw new IllegalArgumentException("Unknown binary worker engine");
        this.context = context.getApplicationContext();
        this.engine = engine;
    }

    @Override
    public String scan(File input, BooleanSupplier stop) throws Exception {
        if (engine.equals("mpv-frame"))
            throw new IOException("MPV frame requests require a separate raster output");
        return request(input, null, 0, input.length(), -1, 0, stop);
    }

    @Override
    public String inspect(File input, long offset, long length, BooleanSupplier stop)
            throws Exception {
        requireArchive();
        return request(input, null, offset, length, -1, 0, stop);
    }

    @Override
    public String extract(
            File input, File output, long offset, long length, int member, BooleanSupplier stop)
            throws Exception {
        requireArchive();
        if (member < 0
                || !output.isFile()
                || output.length() != 0
                || input.getCanonicalFile().equals(output.getCanonicalFile()))
            throw new IOException("Archive output must be a separate empty private file");
        return request(input, output, offset, length, member, 0, stop);
    }

    @Override
    public String capture(File input, File output, int maximumDimensionPixels, BooleanSupplier stop)
            throws Exception {
        if (!engine.equals("mpv-frame"))
            throw new IOException("MPV capture requires the mpv-frame worker engine");
        MpvFrameRunner.requireMaximum(maximumDimensionPixels);
        if (!input.isFile()
                || input.length() <= 0
                || input.length() > MpvFrameRunner.MAXIMUM_INPUT_BYTES
                || !output.isFile()
                || output.length() != 0
                || input.getCanonicalFile().equals(output.getCanonicalFile())
                || !output.getCanonicalFile()
                        .equals(
                                new File(
                                        output.getAbsoluteFile().getParentFile().getCanonicalFile(),
                                        output.getName())))
            throw new IOException("MPV frame needs a bounded input and separate fresh raster file");
        return request(input, output, 0, input.length(), -1, maximumDimensionPixels, stop);
    }

    private void requireArchive() throws IOException {
        if (!engine.equals("archive"))
            throw new IOException("Archive request requires the archive worker engine");
    }

    private String request(
            File input,
            File destination,
            long offset,
            long length,
            int member,
            int maximumDimensionPixels,
            BooleanSupplier stop)
            throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IOException("Binary worker client must run off the UI thread");
        EvidenceRows.check(stop);
        if (closed.get()) throw new IOException("Binary worker client closed");
        AnalysisTimeLimits.Request limits = AnalysisTimeLimits.capture(context, engine);
        String token = UUID.randomUUID().toString();
        String requestDetail = limits.detail(engine, token);
        DebugJournal.record(context, "worker_request_started", requestDetail);
        AtomicReference<Messenger> remote = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicBoolean bound = new AtomicBoolean(), finished = new AtomicBoolean();
        long started = SystemClock.elapsedRealtime();
        ServiceConnection connection =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(new Messenger(binder));
                        connected.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {
                        failure.compareAndSet(
                                null,
                                new IOException(
                                        "Isolated binary worker disconnected before completing its"
                                                + " report"));
                        connected.countDown();
                    }

                    @Override
                    public void onNullBinding(ComponentName name) {
                        failure.set(new IOException("Binary worker returned no binding"));
                        connected.countDown();
                    }

                    @Override
                    public void onBindingDied(ComponentName name) {
                        failure.set(new IOException("Binary worker binding died"));
                        connected.countDown();
                    }
                };
        Runnable unbind =
                () -> {
                    if (bound.compareAndSet(true, false))
                        try {
                            context.unbindService(connection);
                        } catch (IllegalArgumentException ignored) {
                        }
                };
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.scheduleAtFixedRate(
                () -> {
                    if (finished.get()) return;
                    IOException stopped =
                            limits.termination(
                                    SystemClock.elapsedRealtime() - started,
                                    stop.getAsBoolean(),
                                    closed.get());
                    if (stopped != null && failure.compareAndSet(null, stopped)) {
                        DebugJournal.record(
                                context,
                                stopped instanceof InterruptedIOException
                                        ? "worker_request_cancelled"
                                        : "worker_request_deadline",
                                requestDetail);
                        Messenger service = remote.get();
                        if (service != null)
                            try {
                                Message cancel = Message.obtain(null, BinaryWorkerService.CANCEL);
                                cancel.getData().putString("token", token);
                                service.send(cancel);
                            } catch (RemoteException ignored) {
                            }
                        main.post(unbind);
                        connected.countDown();
                    }
                },
                0,
                100,
                TimeUnit.MILLISECONDS);
        main.post(
                () -> {
                    if (finished.get() || failure.get() != null) return;
                    try {
                        boolean result =
                                context.bindService(
                                        new Intent(context, BinaryWorkerService.class),
                                        connection,
                                        Context.BIND_AUTO_CREATE);
                        bound.set(result);
                        if (!result) {
                            failure.set(new IOException("Unable to bind isolated binary worker"));
                            connected.countDown();
                        }
                    } catch (RuntimeException error) {
                        failure.set(error);
                        connected.countDown();
                    }
                });
        ParcelFileDescriptor[] pipe = null;
        try {
            while (!connected.await(100, TimeUnit.MILLISECONDS)) {
                EvidenceRows.check(stop);
                if (failure.get() != null) throw failure.get();
            }
            if (failure.get() != null) throw failure.get();
            if (remote.get() == null) throw new IOException("Isolated worker unavailable");
            pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor source =
                            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor payload =
                            destination == null || !engine.equals("archive")
                                    ? null
                                    : ParcelFileDescriptor.open(
                                            destination, ParcelFileDescriptor.MODE_WRITE_ONLY);
                    ParcelFileDescriptor raster =
                            destination == null || !engine.equals("mpv-frame")
                                    ? null
                                    : ParcelFileDescriptor.open(
                                            destination, ParcelFileDescriptor.MODE_WRITE_ONLY)) {
                Message request = Message.obtain(null, BinaryWorkerService.RUN);
                Bundle arguments = request.getData();
                arguments.putString("token", token);
                arguments.putString("engine", engine);
                limits.writeTo(arguments);
                arguments.putParcelable("input", source);
                arguments.putParcelable("output", pipe[1]);
                arguments.putParcelable("payload", payload);
                if (engine.equals("mpv-frame")) {
                    arguments.putParcelable("raster", raster);
                    arguments.putInt("maximumDimensionPixels", maximumDimensionPixels);
                }
                arguments.putLong("offset", offset);
                arguments.putLong("length", length);
                arguments.putInt("member", member);
                remote.get().send(request);
                DebugJournal.record(context, "worker_request_dispatched", requestDetail);
            }
            pipe[1].close();
            pipe[1] = null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream reader = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                pipe[0] = null;
                byte[] block = new byte[65536];
                int count;
                while ((count = reader.read(block)) >= 0) {
                    if (failure.get() != null) throw failure.get();
                    EvidenceRows.check(stop);
                    if (count > 32 * 1024 * 1024 - bytes.size())
                        throw new IOException("Binary worker report exceeds 32 MiB");
                    bytes.write(block, 0, count);
                }
            }
            if (failure.get() != null) throw failure.get();
            EvidenceRows.check(stop);
            JSONObject envelope;
            try {
                envelope = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            } catch (JSONException error) {
                throw new IOException(
                        "Isolated worker exited or returned an incomplete report", error);
            }
            if (!envelope.getBoolean("isolatedUid"))
                throw new IOException("Worker did not confirm UID isolation");
            if (!engine.equals(envelope.getString("engine")))
                throw new IOException("Worker response belongs to a different engine");
            if (engine.equals("mpv-frame"))
                validateFrameEnvelope(envelope, token, maximumDimensionPixels);
            JSONObject report = envelope.getJSONObject("report");
            limits.validateEnvelope(envelope, report);
            envelope.remove("report");
            report.put("worker", envelope);
            return completeReport(
                    report,
                    () ->
                            DebugJournal.record(
                                    context,
                                    "worker_request_completed",
                                    requestDetail + " ok=" + report.optBoolean("ok")),
                    () -> {
                        Exception recorded = failure.get();
                        return recorded != null
                                ? recorded
                                : limits.termination(
                                        SystemClock.elapsedRealtime() - started,
                                        stop.getAsBoolean(),
                                        closed.get());
                    });
        } catch (Exception | LinkageError error) {
            // Worker termination can close the report pipe before read() can observe failure.
            // Keep the captured cancellation/deadline instead of replacing it with a pipe error.
            Exception recorded = failure.get();
            DebugJournal.recordFailure(
                    context,
                    "worker_request_failed",
                    requestDetail,
                    recorded == null ? error : recorded);
            if (recorded != null) throw recorded;
            throw error;
        } finally {
            finished.set(true);
            watchdog.shutdownNow();
            if (pipe != null)
                for (ParcelFileDescriptor descriptor : pipe)
                    if (descriptor != null)
                        try {
                            descriptor.close();
                        } catch (IOException ignored) {
                        }
            main.post(unbind);
        }
    }

    /**
     * Serialization and best-effort journaling must settle before the terminal publication gate.
     */
    static String completeReport(
            JSONObject report, Runnable journal, Supplier<Exception> terminalFailure)
            throws Exception {
        String encoded = report.toString();
        journal.run();
        Exception stopped = terminalFailure.get();
        if (stopped != null) throw stopped;
        return encoded;
    }

    static void validateFrameEnvelope(JSONObject envelope, String token, int maximumDimensionPixels)
            throws Exception {
        Object maximum = envelope.get("maximumDimensionPixels");
        if (!"mpv-frame".equals(envelope.getString("engine"))
                || !token.equals(envelope.getString("requestToken"))
                || (!(maximum instanceof Integer) && !(maximum instanceof Long))
                || ((Number) maximum).longValue() != maximumDimensionPixels)
            throw new IOException("MPV worker response differs from its captured frame request");
    }

    @Override
    public void close() {
        closed.set(true);
    }
}

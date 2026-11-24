// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Descriptor-only isolated UID. It never receives an input/output filesystem path. */
public final class BinaryWorkerService extends Service {
    static final int RUN = 1, CANCEL = 2;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String activeToken;
    private boolean isolated;
    private Messenger messenger;

    @Override
    public void onCreate() {
        super.onCreate();
        isolated = android.os.Process.myUid() != getApplicationInfo().uid;
        messenger =
                new Messenger(
                        new Handler(
                                Looper.getMainLooper(),
                                message -> {
                                    Bundle data = message.getData();
                                    String token = data.getString("token");
                                    if (message.what == CANCEL) {
                                        if (isolated && token != null && token.equals(activeToken))
                                            android.os.Process.killProcess(
                                                    android.os.Process.myPid());
                                        return true;
                                    }
                                    if (message.what != RUN) return false;
                                    ParcelFileDescriptor input = data.getParcelable("input"),
                                            output = data.getParcelable("output"),
                                            payload = data.getParcelable("payload"),
                                            raster = data.getParcelable("raster");
                                    if (input == null || output == null || token == null) {
                                        close(input);
                                        close(output);
                                        close(payload);
                                        close(raster);
                                        return true;
                                    }
                                    String engine = data.getString("engine", "binwalk");
                                    String rejection =
                                            !isolated
                                                    ? "The binary worker does not have an isolated"
                                                            + " UID; in-process fallback is"
                                                            + " prohibited"
                                                    : !engine.equals("binwalk")
                                                                    && !engine.equals("photorec")
                                                                    && !engine.equals("mpv")
                                                                    && !engine.equals("mpv-frame")
                                                                    && !engine.equals("resources")
                                                                    && !engine.equals("hashes")
                                                                    && !engine.equals("archive")
                                                            ? "Unsupported worker engine"
                                                            : activeToken != null
                                                                    ? "Binary worker is already"
                                                                            + " serving another"
                                                                            + " request"
                                                                    : null;
                                    if (rejection == null
                                            && ((!engine.equals("archive") && payload != null)
                                                    || (engine.equals("archive")
                                                            && (data.getInt("member", -1) < -1
                                                                    || ((data.getInt("member", -1)
                                                                                    >= 0)
                                                                            != (payload
                                                                                    != null))))))
                                        rejection =
                                                "Unexpected or missing archive payload descriptor";
                                    int maximumDimensionPixels =
                                            engine.equals("mpv-frame")
                                                            && data.get("maximumDimensionPixels")
                                                                    instanceof Integer
                                                    ? (Integer) data.get("maximumDimensionPixels")
                                                    : 0;
                                    if (rejection == null) {
                                        try {
                                            if (engine.equals("mpv-frame")) {
                                                maximumDimensionPixels = frameMaximum(data);
                                                if (raster == null
                                                        || payload != null
                                                        || data.getInt("member", -1) != -1
                                                        || data.getLong("offset", 0) != 0
                                                        || data.getLong("length", -1)
                                                                != input.getStatSize())
                                                    throw new IOException(
                                                            "MPV frame requires one whole input and"
                                                                + " a separate raster descriptor");
                                                validateFrameDescriptors(input, raster, output);
                                            } else if (raster != null
                                                    || data.containsKey("maximumDimensionPixels")) {
                                                throw new IOException(
                                                        "Unexpected MPV frame descriptor or"
                                                                + " dimension parameter");
                                            }
                                        } catch (IOException invalidFrame) {
                                            rejection = invalidFrame.getMessage();
                                        }
                                    }
                                    AnalysisTimeLimits.Request limits = null;
                                    try {
                                        limits = AnalysisTimeLimits.fromMessage(data, engine);
                                    } catch (IOException invalidLimits) {
                                        if (rejection == null)
                                            rejection = invalidLimits.getMessage();
                                    }
                                    AnalysisTimeLimits.Request requestLimits = limits;
                                    if (rejection == null) activeToken = token;
                                    // A rejected second client must not queue behind a slow first
                                    // job.
                                    // Its small error envelope fits a fresh pipe and needs no
                                    // native call.
                                    long offset = data.getLong("offset", 0),
                                            length = data.getLong("length", -1);
                                    int member = data.getInt("member", -1);
                                    int frameMaximum = maximumDimensionPixels;
                                    if (rejection != null)
                                        execute(
                                                token,
                                                input,
                                                output,
                                                payload,
                                                raster,
                                                engine,
                                                offset,
                                                length,
                                                member,
                                                frameMaximum,
                                                requestLimits,
                                                rejection);
                                    else
                                        worker.execute(
                                                () ->
                                                        execute(
                                                                token,
                                                                input,
                                                                output,
                                                                payload,
                                                                raster,
                                                                engine,
                                                                offset,
                                                                length,
                                                                member,
                                                                frameMaximum,
                                                                requestLimits,
                                                                null));
                                    return true;
                                }));
    }

    private void execute(
            String token,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            ParcelFileDescriptor payload,
            ParcelFileDescriptor raster,
            String engine,
            long offset,
            long length,
            int member,
            int maximumDimensionPixels,
            AnalysisTimeLimits.Request limits,
            String rejection) {
        try (ParcelFileDescriptor source = input;
                ParcelFileDescriptor destination = payload;
                ParcelFileDescriptor pixels = raster;
                OutputStream target = new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
            String result;
            try {
                if (rejection != null) throw new IOException(rejection);
                if (engine.equals("archive")) {
                    if (!ArchiveEngine.AVAILABLE)
                        throw new IOException(
                                "Archive native library unavailable: " + ArchiveEngine.ERROR);
                    result =
                            member < 0
                                    ? ArchiveEngine.inspectFd(source.getFd(), offset, length)
                                    : ArchiveEngine.extractFd(
                                            source.getFd(),
                                            destination.getFd(),
                                            offset,
                                            length,
                                            member);
                } else if (engine.equals("photorec")) {
                    if (!PhotoRecEngine.AVAILABLE)
                        throw new IOException(
                                "PhotoRec native library unavailable: " + PhotoRecEngine.ERROR);
                    result = PhotoRecEngine.scanFd(source.getFd());
                } else if (engine.equals("hashes")) {
                    if (!HashEngine.AVAILABLE)
                        throw new IOException(
                                "Native whole-file hash engine unavailable: " + HashEngine.ERROR);
                    result = HashEngine.scanFd(source.getFd());
                } else if (engine.equals("resources")) {
                    long deadline = SystemClock.elapsedRealtime() + 25000;
                    try (InputStream reader =
                            new ParcelFileDescriptor.AutoCloseInputStream(
                                    ParcelFileDescriptor.dup(source.getFileDescriptor()))) {
                        result =
                                AndroidResourceProbe.scan(
                                        reader,
                                        source.getStatSize(),
                                        () ->
                                                Thread.currentThread().isInterrupted()
                                                        || SystemClock.elapsedRealtime()
                                                                > deadline);
                    }
                } else if (engine.equals("mpv") || engine.equals("mpv-frame")) {
                    if (!MpvEngine.AVAILABLE)
                        throw new IOException("MPV native library unavailable: " + MpvEngine.ERROR);
                    result =
                            engine.equals("mpv-frame")
                                    ? MpvEngine.captureFd(
                                            source.getFd(), pixels.getFd(), maximumDimensionPixels)
                                    : MpvEngine.scanFd(source.getFd());
                } else {
                    if (!BinwalkEngine.AVAILABLE)
                        throw new IOException(
                                "Binwalk native library unavailable: " + BinwalkEngine.ERROR);
                    result =
                            BinwalkEngine.scanFdWithDeadline(
                                    source.getFd(), limits.analysisDeadlineMs);
                }
                if (result == null)
                    throw new IOException("Native parser returned no complete report");
            } catch (Exception | LinkageError failure) {
                result =
                        new JSONObject()
                                .put("ok", false)
                                .put("error", failure.toString())
                                .toString();
            }
            target.write(
                    ("{\"workerUid\":"
                                    + android.os.Process.myUid()
                                    + ",\"applicationUid\":"
                                    + getApplicationInfo().uid
                                    + ",\"isolatedUid\":"
                                    + isolated
                                    + ",\"engine\":"
                                    + JSONObject.quote(engine)
                                    + (engine.equals("mpv-frame")
                                            ? ",\"requestToken\":"
                                                    + JSONObject.quote(token)
                                                    + ",\"maximumDimensionPixels\":"
                                                    + maximumDimensionPixels
                                            : "")
                                    + (limits == null
                                            ? ""
                                            : ",\"analysisDeadlineMs\":"
                                                    + limits.analysisDeadlineMs
                                                    + ",\"requestDeadlineMs\":"
                                                    + limits.requestDeadlineMs)
                                    + ",\"report\":")
                            .getBytes(StandardCharsets.UTF_8));
            target.write(result.getBytes(StandardCharsets.UTF_8));
            target.write('}');
        } catch (Exception ignored) {
            /* Pipe closure/crash is an explicit incomplete-report failure in the caller. */
        } finally {
            new Handler(Looper.getMainLooper())
                    .post(
                            () -> {
                                if (token.equals(activeToken)) activeToken = null;
                            });
        }
    }

    private static void close(ParcelFileDescriptor value) {
        if (value != null)
            try {
                value.close();
            } catch (IOException ignored) {
            }
    }

    static int frameMaximum(Bundle message) throws IOException {
        Object value = message.get("maximumDimensionPixels");
        if (!(value instanceof Integer))
            throw new IOException("MPV frame requires an integer maximumDimensionPixels parameter");
        int maximum = (Integer) value;
        MpvFrameRunner.requireMaximum(maximum);
        return maximum;
    }

    /** Refuse aliases and stale output before loading or invoking the frame engine. */
    static void validateFrameDescriptors(
            ParcelFileDescriptor input, ParcelFileDescriptor raster, ParcelFileDescriptor report)
            throws IOException {
        try {
            StructStat source = Os.fstat(input.getFileDescriptor());
            StructStat pixels = Os.fstat(raster.getFileDescriptor());
            StructStat envelope = Os.fstat(report.getFileDescriptor());
            int sourceFlags = Os.fcntlInt(input.getFileDescriptor(), OsConstants.F_GETFL, 0);
            int pixelFlags = Os.fcntlInt(raster.getFileDescriptor(), OsConstants.F_GETFL, 0);
            validateFrameDescriptorFacts(source, sourceFlags, pixels, pixelFlags, envelope);
        } catch (ErrnoException | IllegalStateException invalid) {
            throw new IOException("MPV frame descriptor validation failed", invalid);
        }
    }

    /** Android supplies these facts; this rule does not simulate OS fstat or fcntl. */
    static void validateFrameDescriptorFacts(
            StructStat source,
            int sourceFlags,
            StructStat pixels,
            int pixelFlags,
            StructStat envelope)
            throws IOException {
        if (!OsConstants.S_ISREG(source.st_mode)
                || source.st_size <= 0
                || source.st_size > MpvFrameRunner.MAXIMUM_INPUT_BYTES
                || (sourceFlags & OsConstants.O_ACCMODE) != OsConstants.O_RDONLY
                || !OsConstants.S_ISREG(pixels.st_mode)
                || pixels.st_size != 0
                || (pixelFlags & OsConstants.O_ACCMODE) == OsConstants.O_RDONLY
                || (pixelFlags & OsConstants.O_APPEND) != 0
                || source.st_dev == pixels.st_dev && source.st_ino == pixels.st_ino
                || envelope.st_dev == pixels.st_dev && envelope.st_ino == pixels.st_ino)
            throw new IOException(
                    "MPV frame needs read-only bounded input and a fresh separate writable"
                            + " regular raster");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (isolated) android.os.Process.killProcess(android.os.Process.myPid());
        return false;
    }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}

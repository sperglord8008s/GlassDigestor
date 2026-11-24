// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.system.*;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;

/**
 * Real Android processes, Binder/PFDs and unchanged production JNI; no host transport substitute.
 */
public final class WorkerRuntimeInstrumentation extends Instrumentation {
    private final JSONArray checks = new JSONArray();
    private Context target;
    private File directory;
    private long started;
    private JSONObject pendingContractDiagnostic;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        started = SystemClock.elapsedRealtime();
        target = getTargetContext();
        Bundle result = new Bundle();
        boolean success = false;
        JSONObject report = new JSONObject();
        try {
            report.put("schema", "org.diemobile.android-worker-runtime")
                    .put("schemaVersion", 1)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("abis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)))
                    .put("applicationId", target.getPackageName())
                    .put("applicationUid", target.getApplicationInfo().uid)
                    .put("instrumentationUid", android.os.Process.myUid())
                    .put("sourceRevision", BuildConfig.SOURCE_REVISION)
                    .put("sourceDirty", BuildConfig.SOURCE_DIRTY)
                    .put(
                            "scope",
                            "Actual x86_64 Android production worker, hash/archive/PhotoRec JNI,"
                                + " Binder, descriptors, PDF/MPEG file recovery, compiled manifest"
                                + " declarations, token cancellation and the production platform"
                                + " media/PDF preview, text clipboard/URI helpers, captured"
                                + " analysis limits and private diagnostics. This does not verify"
                                + " ARM64, full UI, MPV playback or 16 KiB-page runtime.");
            require(
                    android.os.Process.myUid() == target.getApplicationInfo().uid,
                    "Instrumentation must run as the normal target app UID");
            directory = new File(target.getCacheDir(), "worker-runtime-fixtures");
            require(
                    directory.isDirectory() || directory.mkdirs(),
                    "Cannot create private fixture directory");
            File abc = new File(directory, "abc.bin");
            write(abc, "abc".getBytes(StandardCharsets.US_ASCII));
            JSONObject small = hash(abc);
            require(
                    digests(small).get("MD4").equals("a448017aaf21d8525fc10ae87aa6729d"),
                    "Actual Android native MD4 abc vector differs");
            require(
                    digests(small).get("CRC32").equals("352441c2"),
                    "Actual Android native CRC32 abc vector differs");
            record("isolated-native-hash-vectors", small);
            reportSourceAliasRefusal();
            largeHash();
            archive();
            archiveSession();
            compiledManifest();
            photoRecPdf();
            photoRecMpeg();
            pdfPages();
            mediaPreview();
            cancelBlockedReporter(abc);
            record("normal-client-recovers-after-worker-death", hash(abc));
            textActions();
            capturedLimitsAndDiagnostics();
            success = true;
        } catch (Throwable failure) {
            try {
                StringWriter text = new StringWriter();
                failure.printStackTrace(new PrintWriter(text));
                report.put("failure", text.toString());
                if (pendingContractDiagnostic != null)
                    report.put("failedContractDiagnostic", pendingContractDiagnostic);
            } catch (Exception ignored) {
            }
        } finally {
            try {
                report.put("passed", success)
                        .put("checks", checks)
                        .put("elapsedMs", SystemClock.elapsedRealtime() - started);
                File output = new File(target.getFilesDir(), "worker-runtime-report.json");
                try (Writer out =
                        new OutputStreamWriter(
                                new FileOutputStream(output), StandardCharsets.UTF_8)) {
                    EvidenceJson.write(out, report, () -> false);
                }
                result.putString("runtimePassed", Boolean.toString(success));
                result.putString("runtimeReport", output.getAbsolutePath());
                // Instrumentation.finish uses Binder. Full native catalogs stay in the owned
                // report file; returning them again here exceeds Binder's transaction budget.
                result.putString("runtimeCheckCount", Integer.toString(checks.length()));
                result.putString("runtimeReportBytes", Long.toString(output.length()));
                result.putString("runtimeReportSha256", reportDigest(output));
                result.putString(
                        "stream",
                        "Completed "
                                + checks.length()
                                + " checks; complete evidence: "
                                + output.getAbsolutePath());
            } catch (Throwable failure) {
                success = false;
                result.putString("runtimePassed", "false");
                result.putString(
                        "stream", "Runtime report capture failed: " + failure.getClass().getName());
            }
            if (directory != null) {
                File[] files = directory.listFiles();
                if (files != null) for (File file : files) if (file.isFile()) file.delete();
            }
            finish(
                    success ? android.app.Activity.RESULT_OK : android.app.Activity.RESULT_CANCELED,
                    result);
        }
    }

    private void capturedLimitsAndDiagnostics() throws Exception {
        long began = SystemClock.elapsedRealtime();
        JSONObject limits = capturedLimits();
        JSONObject diagnostics = privateDiagnostics();
        record(
                "captured-analysis-limits-and-private-diagnostics",
                new JSONObject()
                        .put("limits", limits)
                        .put("diagnostics", diagnostics)
                        .put("elapsedMs", SystemClock.elapsedRealtime() - began)
                        .put("binwalkNativeExecuted", false)
                        .put("workerDeadlineWaitExecuted", false)
                        .put("fullUiExecuted", false)
                        .put("safExecuted", false));
    }

    private JSONObject capturedLimits() throws Exception {
        SharedPreferences preferences =
                target.getSharedPreferences(
                        "runtime-analysis-limits-fixture", Context.MODE_PRIVATE);
        require(preferences.edit().clear().commit(), "Cannot reset private limit fixture settings");
        Context fixture =
                new ContextWrapper(target) {
                    @Override
                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return AnalysisTimeLimits.PREFERENCES.equals(name)
                                ? preferences
                                : super.getSharedPreferences(name, mode);
                    }
                };
        try {
            AnalysisTimeLimits.Request defaults = AnalysisTimeLimits.capture(fixture, "binwalk");
            require(
                    defaults.analysisDeadlineMs == 120000 && defaults.requestDeadlineMs == 150000,
                    "Actual Android default limit capture differs from 120/150 seconds");
            Bundle defaultMessage = new Bundle();
            defaults.writeTo(defaultMessage);
            int[] defaultParcelBytes = {0};
            AnalysisTimeLimits.Request defaultTransported =
                    AnalysisTimeLimits.fromMessage(
                            limitParcelRoundTrip(defaultMessage, defaultParcelBytes), "binwalk");
            require(
                    defaultTransported.analysisDeadlineMs == 120000
                            && defaultTransported.requestDeadlineMs == 150000,
                    "Actual Android Parcel changed the default limit capture");
            AnalysisTimeLimits.save(fixture, "binwalk", 240);
            AnalysisTimeLimits.Request custom = AnalysisTimeLimits.capture(fixture, "binwalk");
            require(
                    custom.analysisDeadlineMs == 240000 && custom.requestDeadlineMs == 270000,
                    "Actual Android custom limit capture differs from 240/270 seconds");
            require(
                    custom.requestDeadlineMs - custom.analysisDeadlineMs == 30000,
                    "Captured worker margin differs from 30 seconds");
            Bundle captured = new Bundle();
            custom.writeTo(captured);
            int[] parcelBytes = {0};
            Bundle roundTripped = limitParcelRoundTrip(captured, parcelBytes);
            AnalysisTimeLimits.save(fixture, "binwalk", 30);
            AnalysisTimeLimits.Request changed = AnalysisTimeLimits.capture(fixture, "binwalk");
            AnalysisTimeLimits.Request transported =
                    AnalysisTimeLimits.fromMessage(roundTripped, "binwalk");
            require(
                    changed.analysisDeadlineMs == 30000 && changed.requestDeadlineMs == 60000,
                    "Mutated Android preference was not read by a new capture");
            require(
                    transported.analysisDeadlineMs == 240000
                            && transported.requestDeadlineMs == 270000
                            && custom.analysisDeadlineMs == 240000
                            && defaults.analysisDeadlineMs == 120000,
                    "Preference mutation changed an already captured/parcelled limit");

            int saveRefusals = 0;
            for (int invalid : new int[] {0, AnalysisTimeLimits.MAX_SECONDS + 1}) {
                try {
                    AnalysisTimeLimits.save(fixture, "binwalk", invalid);
                    throw new AssertionError("Unsupported saved seconds were accepted");
                } catch (IllegalArgumentException expected) {
                    saveRefusals++;
                }
            }
            try {
                AnalysisTimeLimits.save(fixture, "hashes", 240);
                throw new AssertionError("A non-configurable engine accepted saved seconds");
            } catch (IllegalArgumentException expected) {
                saveRefusals++;
            }
            require(
                    AnalysisTimeLimits.seconds(fixture, "binwalk") == 30,
                    "A refused setting changed the previous preference");

            Bundle partial = new Bundle(captured);
            partial.remove("requestDeadlineMs");
            requireLimitMessageRefusal(partial, "binwalk");
            Bundle wrongType = new Bundle(captured);
            wrongType.putInt("analysisDeadlineMs", 240000);
            requireLimitMessageRefusal(wrongType, "binwalk");
            Bundle mismatched = new Bundle(captured);
            mismatched.putLong("requestDeadlineMs", 270001);
            requireLimitMessageRefusal(mismatched, "binwalk");
            Bundle fractionalSecond = new Bundle(captured);
            fractionalSecond.putLong("analysisDeadlineMs", 240001);
            fractionalSecond.putLong("requestDeadlineMs", 270001);
            requireLimitMessageRefusal(fractionalSecond, "binwalk");
            Bundle oversized = new Bundle(captured);
            long excessive = 1000L * (AnalysisTimeLimits.MAX_SECONDS + 1);
            oversized.putLong("analysisDeadlineMs", excessive);
            oversized.putLong("requestDeadlineMs", excessive + 30000);
            requireLimitMessageRefusal(oversized, "binwalk");
            requireLimitMessageRefusal(captured, "hashes");
            AnalysisTimeLimits.Request legacy =
                    AnalysisTimeLimits.fromMessage(
                            limitParcelRoundTrip(new Bundle(), null), "binwalk");
            require(
                    legacy.analysisDeadlineMs == 120000 && legacy.requestDeadlineMs == 150000,
                    "Legacy empty Bundle defaults differ");
            require(
                    custom.termination(269999, false, false) == null
                            && custom.termination(270000, false, false) != null
                            && custom.termination(0, true, false) instanceof InterruptedIOException
                            && custom.termination(0, false, true) instanceof InterruptedIOException,
                    "Captured deadline/cancellation boundaries differ");

            require(
                    preferences
                            .edit()
                            .putString("binwalk_seconds", "invalid-fixture-type")
                            .commit(),
                    "Cannot write the corrupt-type preference fixture");
            require(
                    AnalysisTimeLimits.seconds(fixture, "binwalk") == 120,
                    "Corrupt preference type did not fall back to default");
            require(
                    preferences
                            .edit()
                            .putInt("binwalk_seconds", AnalysisTimeLimits.MAX_SECONDS + 1)
                            .commit(),
                    "Cannot write the out-of-range preference fixture");
            require(
                    AnalysisTimeLimits.seconds(fixture, "binwalk") == 120,
                    "Out-of-range preference did not fall back to default");
            return new JSONObject()
                    .put("defaultAnalysisMs", defaults.analysisDeadlineMs)
                    .put("defaultWorkerMs", defaults.requestDeadlineMs)
                    .put("customAnalysisMs", custom.analysisDeadlineMs)
                    .put("customWorkerMs", custom.requestDeadlineMs)
                    .put("workerMarginMs", custom.requestDeadlineMs - custom.analysisDeadlineMs)
                    .put("defaultParcelBytes", defaultParcelBytes[0])
                    .put("customParcelBytes", parcelBytes[0])
                    .put("bundleLongTypesRetained", true)
                    .put(
                            "capturedAfterPreferenceMutationAnalysisMs",
                            transported.analysisDeadlineMs)
                    .put("mutatedPreferenceAnalysisMs", changed.analysisDeadlineMs)
                    .put("invalidSaveRefusals", saveRefusals)
                    .put("invalidParcelledMessageRefusals", 6)
                    .put("corruptPreferenceDefaulted", true)
                    .put("legacyEmptyBundleDefaulted", true)
                    .put("deadlineAndCancellationBoundariesChecked", true);
        } finally {
            require(
                    preferences.edit().clear().commit(),
                    "Cannot clear private limit fixture settings");
        }
    }

    private static Bundle limitParcelRoundTrip(Bundle original, int[] encodedBytes) {
        Parcel writer = Parcel.obtain();
        Parcel reader = Parcel.obtain();
        try {
            writer.writeBundle(original);
            byte[] bytes = writer.marshall();
            if (encodedBytes != null) encodedBytes[0] = bytes.length;
            reader.unmarshall(bytes, 0, bytes.length);
            reader.setDataPosition(0);
            Bundle restored = reader.readBundle(AnalysisTimeLimits.class.getClassLoader());
            require(restored != null, "Actual Android Parcel lost the Bundle");
            return restored;
        } finally {
            writer.recycle();
            reader.recycle();
        }
    }

    private static void requireLimitMessageRefusal(Bundle message, String engine) throws Exception {
        try {
            AnalysisTimeLimits.fromMessage(limitParcelRoundTrip(message, null), engine);
            throw new AssertionError("Unsupported parcelled limit message was accepted");
        } catch (IOException expected) {
            // Complete typed messages are required after actual platform Parcel decoding.
        }
    }

    private JSONObject privateDiagnostics() throws Exception {
        File root = target.getNoBackupFilesDir().getCanonicalFile();
        require(
                root.isDirectory()
                        && root.getParentFile()
                                .equals(
                                        new File(target.getApplicationInfo().dataDir)
                                                .getCanonicalFile()),
                "Diagnostics fixture is not inside the actual application no-backup root");
        File journal = new File(root, "program-debug/events.log");
        File alias = File.createTempFile("runtime-debug-context-", ".alias", root);
        File sentinel = File.createTempFile("runtime-debug-outside-", ".sentinel", directory);
        byte[] sentinelBytes =
                "outside fixture bytes must remain unchanged".getBytes(StandardCharsets.UTF_8);
        boolean linkedLeaf = false;
        try {
            require(alias.delete(), "Cannot prepare the trusted diagnostics ancestor alias");
            Os.symlink(root.getAbsolutePath(), alias.getAbsolutePath());
            Context aliased =
                    new ContextWrapper(target) {
                        @Override
                        public File getNoBackupFilesDir() {
                            return alias;
                        }
                    };
            require(DebugJournal.clear(target), "Cannot clear the target's private diagnostics");
            DebugJournal.record(
                    target,
                    "runtime_diagnostic",
                    "engine=binwalk status=complete requestMs=270000"
                        + " path=/runtime/private-selected.bin"
                        + " filename=runtime_private_filename.bin query=runtime_query_needle"
                        + " runtime_raw_text");
            IOException failure = new IOException("runtime_exception_private_message");
            failure.setStackTrace(
                    new StackTraceElement[] {
                        new StackTraceElement(
                                "thirdparty.Parser", "read", "runtime_private_parser.bin", 11),
                        new StackTraceElement(
                                "org.diemobile.WorkerRuntimeInstrumentation",
                                "capturedLimitsAndDiagnostics",
                                "runtime_private_source.bin",
                                731)
                    });
            DebugJournal.recordFailure(target, "runtime_failure", "engine=binwalk", failure);
            DebugJournal.Snapshot privacy = DebugJournal.read(target);
            require(
                    privacy.available && privacy.entries.size() == 2,
                    "Actual Android private diagnostics did not retain the expected entries");
            String text = privacy.text();
            for (String secret :
                    new String[] {
                        "private-selected.bin",
                        "runtime_private_filename.bin",
                        "runtime_query_needle",
                        "runtime_raw_text",
                        "runtime_exception_private_message",
                        "runtime_private_parser.bin",
                        "runtime_private_source.bin"
                    })
                require(
                        !text.contains(secret),
                        "Private source text entered the diagnostics fixture");
            require(
                    text.contains("exception=IOException")
                            && text.contains("class=org.diemobile.WorkerRuntimeInstrumentation")
                            && text.contains("method=capturedLimitsAndDiagnostics")
                            && text.contains("line=731"),
                    "Safe exception class/method/line metadata was lost");
            require(
                    journal.isFile() && journal.equals(journal.getCanonicalFile()),
                    "Actual no-backup journal leaf is unavailable or noncanonical");
            long privacyBytes = journal.length();
            DebugJournal.record(aliased, "runtime_alias_event", "engine=binwalk status=complete");
            DebugJournal.Snapshot aliasRead = DebugJournal.read(target);
            require(
                    aliasRead.available
                            && aliasRead.entries.size() == 3
                            && aliasRead.entries.get(0).event.equals("runtime_alias_event"),
                    "Trusted diagnostics ancestor alias did not share the actual journal");
            require(
                    DebugJournal.clear(aliased)
                            && !journal.exists()
                            && DebugJournal.read(target).entries.isEmpty(),
                    "Clearing through the trusted alias did not clear the physical journal");

            write(sentinel, sentinelBytes);
            Os.symlink(sentinel.getCanonicalPath(), journal.getAbsolutePath());
            linkedLeaf = true;
            DebugJournal.record(aliased, "runtime_refused_link", "engine=binwalk");
            require(
                    !DebugJournal.read(aliased).available && !DebugJournal.clear(aliased),
                    "Generated diagnostics leaf link was accepted");
            require(
                    Arrays.equals(sentinelBytes, read(sentinel)),
                    "Diagnostics linked-leaf refusal changed the outside sentinel");
            java.nio.file.Files.delete(journal.toPath());
            linkedLeaf = false;

            JSONObject retention = diagnosticsRetention(target, journal);
            require(DebugJournal.clear(target), "Cannot clear retained diagnostics fixture");
            DebugJournal.Snapshot empty = DebugJournal.read(target);
            require(
                    empty.available && empty.entries.isEmpty() && !journal.exists(),
                    "Cleared diagnostics were not readable and empty");
            return new JSONObject()
                    .put("actualNoBackupRootUsed", true)
                    .put("journalLeafCanonical", true)
                    .put("contextAncestorAliasAccepted", true)
                    .put("privatePathsContentsAndQueriesOmitted", true)
                    .put("exceptionMessagesAndSourceFilenamesOmitted", true)
                    .put("appClassMethodLineRetained", true)
                    .put("privacyProbeEntries", privacy.entries.size())
                    .put("privacyProbeStoredBytes", privacyBytes)
                    .put("linkedJournalLeafRefused", true)
                    .put("outsideSentinelUnchanged", true)
                    .put("clearLeavesReadableEmptyJournal", true)
                    .put("retention", retention);
        } finally {
            if (linkedLeaf) java.nio.file.Files.deleteIfExists(journal.toPath());
            DebugJournal.clear(target);
            java.nio.file.Files.deleteIfExists(alias.toPath());
            sentinel.delete();
        }
    }

    private static JSONObject diagnosticsRetention(Context context, File journal) throws Exception {
        require(DebugJournal.clear(context), "Cannot reset row retention fixture");
        DebugJournal.record(context, "runtime_retention_oldest", "engine=hashes count=0");
        byte[] oldest = read(journal);
        DebugJournal.record(context, "runtime_retention_seed", "engine=hashes count=1");
        String[] lines = new String(read(journal), StandardCharsets.UTF_8).split("\n", -1);
        require(lines.length == 3, "Cannot acquire actual journal line fixtures");
        byte[] seed = (lines[1] + "\n").getBytes(StandardCharsets.UTF_8);
        prefillDiagnostics(journal, oldest, seed, DebugJournal.MAX_ENTRIES);
        require(
                DebugJournal.read(context).entries.size() == DebugJournal.MAX_ENTRIES,
                "Owned row retention prefill was not accepted");
        DebugJournal.record(context, "runtime_retention_latest", "engine=hashes count=256");
        DebugJournal.Snapshot rowBounded = DebugJournal.read(context);
        require(
                rowBounded.available
                        && rowBounded.entries.size() == DebugJournal.MAX_ENTRIES
                        && rowBounded.entries.get(0).event.equals("runtime_retention_latest"),
                "Actual Android journal row retention bound failed");
        for (DebugJournal.Entry entry : rowBounded.entries)
            require(!entry.event.equals("runtime_retention_oldest"), "Oldest row was not removed");

        require(DebugJournal.clear(context), "Cannot reset byte retention fixture");
        char[] code96 = new char[96], code64 = new char[64];
        Arrays.fill(code96, 'x');
        Arrays.fill(code64, 'x');
        String detail =
                "token="
                        + new String(code96)
                        + " request="
                        + new String(code96)
                        + " status="
                        + new String(code64)
                        + " count=1234567890123456";
        String oldestEvent = diagnosticsEvent("runtime_byte_oldest");
        DebugJournal.record(context, oldestEvent, detail);
        oldest = read(journal);
        DebugJournal.record(context, diagnosticsEvent("runtime_byte_seed"), detail);
        lines = new String(read(journal), StandardCharsets.UTF_8).split("\n", -1);
        require(lines.length == 3, "Cannot acquire actual bounded long-line fixtures");
        seed = (lines[1] + "\n").getBytes(StandardCharsets.UTF_8);
        require(seed.length == oldest.length, "Long-line fixture sizes differ");
        int byteEntries = DebugJournal.MAX_BYTES / seed.length;
        require(
                byteEntries > 1 && byteEntries < DebugJournal.MAX_ENTRIES,
                "Byte retention fixture does not isolate the byte bound");
        prefillDiagnostics(journal, oldest, seed, byteEntries);
        require(
                DebugJournal.read(context).entries.size() == byteEntries
                        && journal.length() > DebugJournal.MAX_BYTES - seed.length,
                "Owned byte retention prefill was not accepted near the bound");
        String latestEvent = diagnosticsEvent("runtime_byte_latest");
        DebugJournal.record(context, latestEvent, detail);
        DebugJournal.Snapshot byteBounded = DebugJournal.read(context);
        require(
                byteBounded.available
                        && byteBounded.entries.size() == byteEntries
                        && byteBounded.entries.get(0).event.equals(latestEvent)
                        && journal.length() <= DebugJournal.MAX_BYTES,
                "Actual Android journal byte retention bound failed");
        for (DebugJournal.Entry entry : byteBounded.entries)
            require(
                    !entry.event.equals(oldestEvent)
                            && entry.detail.length() <= DebugJournal.MAX_DETAIL_UNITS,
                    "Byte retention preserved the oldest row or an over-budget detail");
        return new JSONObject()
                .put("maxEntries", DebugJournal.MAX_ENTRIES)
                .put("maxStoredBytes", DebugJournal.MAX_BYTES)
                .put("rowBoundRetainedEntries", rowBounded.entries.size())
                .put("byteBoundPrefilledEntries", byteEntries)
                .put("byteBoundRetainedEntries", byteBounded.entries.size())
                .put("byteBoundStoredBytes", journal.length())
                .put("validOwnedLinePrefillUsed", true)
                .put("oldestRowsRemoved", true)
                .put("newestEventsRetained", true);
    }

    private static void prefillDiagnostics(File file, byte[] oldest, byte[] seed, int entries)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(oldest);
        for (int index = 1; index < entries; index++) bytes.write(seed);
        require(bytes.size() <= DebugJournal.MAX_BYTES, "Owned journal prefill exceeds its bound");
        write(file, bytes.toByteArray());
    }

    private static String diagnosticsEvent(String prefix) {
        char[] value = new char[64];
        Arrays.fill(value, 'x');
        prefix.getChars(0, prefix.length(), value, 0);
        return new String(value);
    }

    private void archiveSession() throws Exception {
        File compressed = new File(directory, "archive-session-misleading.jpeg");
        try (InputStream asset = target.getAssets().open("test-fixtures/archive-session-20m.bz2");
                OutputStream output = new FileOutputStream(compressed)) {
            byte[] buffer = new byte[65536];
            long copied = 0;
            for (int count; (count = asset.read(buffer)) != -1; ) {
                copied += count;
                require(copied < 22L * 1024 * 1024, "Archive fixture exceeds its bounded size");
                output.write(buffer, 0, count);
            }
        }
        require(
                compressed.length() > 19L * 1024 * 1024,
                "Archive fixture is not a large full input");
        long compressedSize = compressed.length();
        String compressedSha = reportDigest(compressed);
        File physical = File.createTempFile("archive-session-physical-", ".owned", directory);
        require(physical.delete() && physical.mkdir(), "Cannot create physical cache fixture");
        File alias = new File(directory, physical.getName() + ".alias");
        Os.symlink(physical.getAbsolutePath(), alias.getAbsolutePath());
        File base = new File(alias, "sessions");
        File sentinel = new File(directory, "archive-session-outside-sentinel.json");
        byte[] originalSentinel = "preserve outside bytes".getBytes(StandardCharsets.UTF_8);
        write(sentinel, originalSentinel);
        File linked = null;
        NativeArchiveSession session = null;
        BinaryWorkerClient actual = new BinaryWorkerClient(target, "archive");
        int[] requests = {0};
        ArchiveRunner counted =
                new ArchiveRunner() {
                    @Override
                    public String inspect(
                            File input,
                            long offset,
                            long length,
                            java.util.function.BooleanSupplier stop)
                            throws Exception {
                        requests[0]++;
                        return actual.inspect(input, offset, length, stop);
                    }

                    @Override
                    public String extract(
                            File input,
                            File output,
                            long offset,
                            long length,
                            int member,
                            java.util.function.BooleanSupplier stop)
                            throws Exception {
                        requests[0]++;
                        return actual.extract(input, output, offset, length, member, stop);
                    }
                };
        try {
            session =
                    NativeArchiveSession.open(
                            compressed,
                            "misleading.jpeg",
                            base,
                            0,
                            compressed.length(),
                            counted,
                            ArchiveLaunch.forInput(
                                    compressedSha, compressed.length(), 0, compressed.length()),
                            () -> false);
            require(
                    session.directory.equals(session.directory.getCanonicalFile()),
                    "Session failed to anchor the Context cache alias");
            require(
                    session.current().inventory.complete
                            && session.current().inventory.members.size() == 1,
                    "BZip2 stream did not produce one complete returned member");
            JSONObject inspection = session.current().inventory.json();
            int inspectedRequests = requests[0];
            linked = new File(session.directory, "archive-capture-linked.json");
            Os.symlink(sentinel.getAbsolutePath(), linked.getAbsolutePath());
            boolean refused = false;
            try {
                session.changeScope(0, session.current().input.size, () -> false);
            } catch (IOException expected) {
                refused = true;
            }
            require(
                    refused && requests[0] == inspectedRequests,
                    "Linked cache leaf was not refused before native work");
            require(
                    Arrays.equals(originalSentinel, read(sentinel)),
                    "Cache refusal changed bytes outside the session");
            java.nio.file.Files.deleteIfExists(linked.toPath());
            linked = null;
            String state = session.retentionState();
            session.close(true);
            session = null;
            require(compressed.delete(), "Cannot expire original compressed fixture");
            session = NativeArchiveSession.restore(base, state, counted, () -> false);
            require(requests[0] == inspectedRequests, "Alias restoration repeated native analysis");
            final NativeArchiveSession captured = session;
            NativeArchiveStore.Member member = session.current().inventory.members.get(0);
            try (NativeArchiveStore.Acquired acquired =
                    session.acquire(
                            member, new File(directory, "archive-session-members"), () -> false)) {
                String expectedSha =
                        "73aea6dfaf4ef1966595e77bfa2c69597b0830f7f6c82f9ca6490260ae16ccf3";
                require(
                        acquired.input.size == 20L * 1024 * 1024
                                && acquired.input.sha256.equals(expectedSha)
                                && reportDigest(acquired.input.file).equals(expectedSha),
                        "Complete native BZip2 payload differs from its independent oracle");
                File origins = new File(alias, "origins");
                try (ContentOrigin origin =
                                ContentOrigin.create(
                                        origins,
                                        acquired.input,
                                        ContentOrigin.member(session.current().input, member.index),
                                        records ->
                                                records.json(
                                                        "archiveTrail",
                                                        out ->
                                                                captured.writeCapturedEvidence(
                                                                        out, () -> false)),
                                        () -> false);
                        ContentOrigin restored =
                                ContentOrigin.restore(
                                        origins,
                                        origin.descriptor(),
                                        acquired.input.sha256,
                                        acquired.input.size,
                                        () -> false)) {
                    require(
                            origin.size == restored.size && origin.size > 0,
                            "Archive origin handoff failed through the Context alias");
                }
                record(
                        "aliased-cache-bzip2-session-and-exact-large-file",
                        new JSONObject()
                                .put("compressedSizeBytes", compressedSize)
                                .put("compressedSha256", compressedSha)
                                .put("decodedSizeBytes", acquired.input.size)
                                .put("decodedSha256", acquired.input.sha256)
                                .put("contextAncestorAliasAccepted", true)
                                .put("sessionRootCanonical", true)
                                .put("linkedCacheLeafRefusedBeforeNativeWork", true)
                                .put("outsideSentinelUnchanged", true)
                                .put("restoreFromAliasWithoutNativeReanalysis", true)
                                .put("originalSourceExpiredBeforeAcquisition", true)
                                .put("originRestoredThroughAlias", true)
                                .put("sourceExtensionIgnored", true)
                                .put("fullArchiveUiExecuted", false)
                                .put("inspection", inspection)
                                .put("acquisition", new JSONObject(acquired.extractionReport)));
            }
        } finally {
            if (linked != null)
                try {
                    java.nio.file.Files.deleteIfExists(linked.toPath());
                } catch (Exception ignored) {
                }
            if (session != null) session.close();
            actual.close();
            try {
                java.nio.file.Files.deleteIfExists(alias.toPath());
            } catch (Exception ignored) {
            }
        }
    }

    private void textActions() throws Exception {
        pendingContractDiagnostic =
                new JSONObject()
                        .put("contract", "exact-text-actions-platform-clipboard-and-uris")
                        .put("stage", "launching-focus-fixture");
        TextActionRuntimeActivity activity =
                (TextActionRuntimeActivity)
                        startActivitySync(
                                new Intent(target, TextActionRuntimeActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> sensitive = new AtomicReference<>(false);
        AtomicReference<JSONObject> currentFocus = new AtomicReference<>();
        Throwable primaryFailure = null;
        String raw = "研究 😀 &?#/\n" + (char) 0 + (char) 0x202e + (char) 0xd800;
        try {
            pendingContractDiagnostic.put("stage", "awaiting-current-focus");
            long focusWait = SystemClock.elapsedRealtime();
            boolean focusReady = activity.awaitFocus();
            pendingContractDiagnostic.put("focusWaitMs", SystemClock.elapsedRealtime() - focusWait)
                    .put("readinessReturned", focusReady);
            require(
                    focusReady,
                    "Clipboard fixture never obtained window focus: " + activity.focusState());
            pendingContractDiagnostic
                    .put("stage", "clipboard-main-thread-check");
            waitForIdleSync();
            runOnMainSync(
                    () -> {
                        ClipboardManager clipboard =
                                (ClipboardManager)
                                        activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        try {
                            require(
                                    activity.currentFocusReady(),
                                    "Clipboard fixture is not currently resumed/visible/focused: "
                                            + activity.focusState());
                            require(activity.hasWindowFocus(), "Clipboard window lost focus");
                            currentFocus.set(activity.focusSnapshot());
                            TextActions actions = new TextActions(activity);
                            actions.copy(raw);
                            ClipData copied = clipboard.getPrimaryClip();
                            require(copied != null, "Platform clipboard did not return the clip");
                            require(
                                    raw.equals(copied.getItemAt(0).getText().toString()),
                                    "Clipboard changed raw UTF-16, NUL or isolated surrogate");
                            if (Build.VERSION.SDK_INT >= 33) {
                                sensitive.set(
                                        copied.getDescription()
                                                .getExtras()
                                                .getBoolean(ClipDescription.EXTRA_IS_SENSITIVE));
                                require(sensitive.get(), "Sensitive clipboard flag was lost");
                            }
                            char[] oversized = new char[TextActions.MAX_CLIPBOARD_UNITS + 1];
                            Arrays.fill(oversized, 'x');
                            actions.copy(new String(oversized));
                            require(
                                    raw.equals(
                                            clipboard
                                                    .getPrimaryClip()
                                                    .getItemAt(0)
                                                    .getText()
                                                    .toString()),
                                    "Oversized refusal replaced the previous clip");
                        } catch (Throwable error) {
                            failure.set(error);
                        } finally {
                            if (clipboard != null)
                                clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Fixture cleanup", ""));
                        }
                    });
            require(failure.get() == null, "Platform copy failed: " + failure.get());
            pendingContractDiagnostic.put("stage", "validating-uri-encodings");
            String query = raw.substring(0, raw.length() - 1);
            JSONArray engines = new JSONArray();
            for (TextActions.Engine engine : TextActions.Engine.values()) {
                android.net.Uri uri = engine.uri(query);
                require("https".equals(uri.getScheme()), "Text action URI is not HTTPS");
                String decoded;
                if (engine == TextActions.Engine.DEEPL) {
                    require("www.deepl.com".equals(uri.getHost()), "Unexpected DeepL host");
                    decoded = uri.getFragment().substring("auto/en/".length());
                    require(uri.getFragment().startsWith("auto/en/"), "DeepL target differs");
                } else {
                    decoded = uri.getQueryParameter(engine.translation ? "text" : "q");
                    if (engine.translation)
                        require(
                                "en"
                                        .equals(
                                                uri.getQueryParameter(
                                                        engine
                                                                        == TextActions.Engine
                                                                                .GOOGLE_TRANSLATE
                                                                ? "tl"
                                                                : "to")),
                                "Translation target is not English");
                }
                require(query.equals(decoded), "URI query changed the exact selected text");
                try {
                    engine.uri(raw);
                    throw new AssertionError("Unpaired surrogate accepted for browser handoff");
                } catch (IllegalArgumentException expected) {
                    // Exact copy is allowed; lossy query encoding is not.
                }
                engines.put(
                        new JSONObject()
                                .put("engine", engine.name())
                                .put("host", uri.getHost())
                                .put("translation", engine.translation)
                                .put("queryRoundTrip", true)
                                .put("isolatedSurrogateRefused", true));
            }
            record(
                    "exact-text-actions-platform-clipboard-and-uris",
                    new JSONObject()
                            .put("applicationUid", android.os.Process.myUid())
                            .put("focusedNormalUidWindow", true)
                            .put("currentFocusSnapshot", currentFocus.get())
                            .put("exactUtf16Clipboard", true)
                            .put("clipboardUtf16Units", raw.length())
                            .put("nulBidiSupplementaryAndIsolatedSurrogate", true)
                            .put("oversizedClipboardPreservedPrevious", true)
                            .put("sensitiveClipFlagApplicable", Build.VERSION.SDK_INT >= 33)
                            .put("sensitiveClipFlag", sensitive.get())
                            .put("engines", engines)
                            .put("externalBrowserLaunched", false)
                            .put("externalServiceContacted", false));
            pendingContractDiagnostic = null;
        } catch (Exception | Error error) {
            primaryFailure = error;
            captureFocusFailure(activity, error);
            throw error;
        } finally {
            FutureTask<Void> finish =
                    new FutureTask<>(
                            () -> {
                                activity.finish();
                                return null;
                            });
            try {
                activity.runOnUiThread(finish);
                finish.get(5, TimeUnit.SECONDS);
            } catch (Exception cleanup) {
                finish.cancel(false);
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private void captureFocusFailure(TextActionRuntimeActivity activity, Throwable primary) {
        ExecutorService diagnostics = null;
        Future<JSONObject> capture = null;
        AtomicReference<ParcelFileDescriptor> open = new AtomicReference<>();
        AtomicReference<String> completedSnapshots = new AtomicReference<>("{}");
        try {
            if (pendingContractDiagnostic == null)
                pendingContractDiagnostic =
                        new JSONObject()
                                .put("contract", "exact-text-actions-platform-clipboard-and-uris");
            pendingContractDiagnostic
                    .put("failureClass", primary.getClass().getName())
                    .put("focusAtFailure", activity.focusSnapshot())
                    .put("snapshotsCapturedBeforeFixtureFinish", true)
                    .put("snapshotBudgetMs", 8000)
                    .put("maximumSnapshotBytesPerCommand", 256 * 1024);
            diagnostics =
                    Executors.newSingleThreadExecutor(
                            task -> {
                                Thread thread = new Thread(task, "runtime-focus-diagnostics");
                                thread.setDaemon(true);
                                return thread;
                            });
            capture =
                    diagnostics.submit(
                            () -> {
                                UiAutomation automation =
                                        getUiAutomation(
                                                UiAutomation
                                                        .FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
                                JSONObject snapshots =
                                        new JSONObject()
                                                .put("powerAndTask", activity.systemFocusState());
                                completedSnapshots.set(snapshots.toString());
                                for (String service :
                                        new String[] {"window windows", "activity activities"}) {
                                    if (Thread.currentThread().isInterrupted())
                                        throw new InterruptedIOException(
                                                "Snapshot capture cancelled");
                                    String command = "dumpsys -t 2 " + service;
                                    try (ParcelFileDescriptor descriptor =
                                            automation.executeShellCommand(command)) {
                                        if (descriptor == null)
                                            throw new IOException("No snapshot descriptor");
                                        open.set(descriptor);
                                        snapshots.put(
                                                service, boundedFocusDump(descriptor, command));
                                    } catch (Exception failure) {
                                        snapshots.put(
                                                service,
                                                new JSONObject()
                                                        .put("command", command)
                                                        .put(
                                                                "errorClass",
                                                                failure.getClass().getName()));
                                    } finally {
                                        open.set(null);
                                        completedSnapshots.set(snapshots.toString());
                                    }
                                }
                                return snapshots;
                            });
            pendingContractDiagnostic
                    .put("systemFocusSnapshots", capture.get(8, TimeUnit.SECONDS))
                    .put("systemFocusSnapshotsComplete", true);
        } catch (Throwable diagnosticFailure) {
            try {
                if (pendingContractDiagnostic != null)
                    pendingContractDiagnostic
                            .put("focusSnapshotErrorClass", diagnosticFailure.getClass().getName())
                            .put("systemFocusSnapshots", new JSONObject(completedSnapshots.get()))
                            .put("systemFocusSnapshotsComplete", false);
            } catch (Exception ignored) {
            }
            if (diagnosticFailure instanceof InterruptedException)
                Thread.currentThread().interrupt();
        } finally {
            if (capture != null) capture.cancel(true);
            ParcelFileDescriptor descriptor = open.getAndSet(null);
            if (descriptor != null)
                try {
                    descriptor.close();
                } catch (Exception ignored) {
                }
            if (diagnostics != null) diagnostics.shutdownNow();
        }
    }

    private static JSONObject boundedFocusDump(ParcelFileDescriptor descriptor, String command)
            throws Exception {
        final int maximum = 256 * 1024;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean complete = false, truncated = false;
        String error = null;
        long began = SystemClock.elapsedRealtime(), deadline = began + 2000;
        try {
            FileDescriptor input = descriptor.getFileDescriptor();
            int flags = Os.fcntlInt(input, OsConstants.F_GETFL, 0);
            Os.fcntlInt(input, OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
            StructPollfd poll = new StructPollfd();
            poll.fd = input;
            poll.events = (short) OsConstants.POLLIN;
            StructPollfd[] polled = {poll};
            byte[] block = new byte[4096];
            while (true) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedIOException("Snapshot capture cancelled");
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) {
                    error = "read-deadline";
                    break;
                }
                try {
                    if (Os.poll(polled, (int) remaining) == 0) continue;
                    if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLNVAL)) != 0)
                        throw new IOException("Snapshot descriptor unavailable");
                    int count = Os.read(input, block, 0, block.length);
                    if (count == 0) {
                        complete = true;
                        break;
                    }
                    int retained = Math.min(count, maximum - bytes.size());
                    bytes.write(block, 0, retained);
                    if (retained != count) {
                        truncated = true;
                        break;
                    }
                } catch (ErrnoException failure) {
                    if (failure.errno != OsConstants.EINTR && failure.errno != OsConstants.EAGAIN)
                        throw failure;
                }
            }
        } catch (Exception failure) {
            error = failure.getClass().getName();
        }
        byte[] captured = bytes.toByteArray();
        return new JSONObject()
                .put("command", command)
                .put("capturedBytes", captured.length)
                .put("elapsedMs", SystemClock.elapsedRealtime() - began)
                .put("readComplete", complete)
                .put("truncated", truncated)
                .put("error", error == null ? JSONObject.NULL : error)
                .put(
                        "capturedSha256",
                        Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(captured)))
                .put(
                        "rawOutputBase64",
                        android.util.Base64.encodeToString(captured, android.util.Base64.NO_WRAP))
                .put("text", new String(captured, StandardCharsets.UTF_8));
    }

    private static String reportDigest(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) != -1; ) digest.update(buffer, 0, count);
        }
        return Inspector.toHex(digest.digest());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static byte[] read(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[65536];
            int count;
            while ((count = in.read(bytes)) >= 0) output.write(bytes, 0, count);
        }
        return output.toByteArray();
    }

    private void record(String name, JSONObject evidence) throws Exception {
        checks.put(
                new JSONObject().put("name", name).put("passed", true).put("evidence", evidence));
    }

    private void worker(JSONObject report, String engine) throws Exception {
        JSONObject identity = report.getJSONObject("worker");
        require(
                identity.getBoolean("isolatedUid"),
                "Production worker did not confirm UID isolation");
        require(
                identity.getInt("workerUid") != android.os.Process.myUid(),
                "Parser ran under ordinary app UID");
        require(
                identity.getInt("applicationUid") == target.getApplicationInfo().uid,
                "Worker belongs to a different app UID");
        require(identity.getString("engine").equals(engine), "Worker engine identity differs");
    }

    private JSONObject hash(File file) throws Exception {
        try (BinaryWorkerClient client = new BinaryWorkerClient(target, "hashes")) {
            JSONObject report = new JSONObject(client.scan(file, () -> false));
            worker(report, "hashes");
            require(report.getBoolean("ok"), report.optString("error"));
            require(
                    report.getBoolean("wholeInputComplete")
                            && report.getLong("bytesRead") == file.length(),
                    "Hash report is not complete");
            require(report.getLong("fileSizeBytes") == file.length(), "Hash source size differs");
            return report;
        }
    }

    private static Map<String, String> digests(JSONObject report) throws Exception {
        Map<String, String> values = new HashMap<>();
        JSONArray rows = report.getJSONArray("digests");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject value = rows.getJSONObject(i);
            require(
                    values.put(value.getString("algorithm"), value.getString("hex")) == null,
                    "Duplicate digest algorithm");
        }
        require(values.size() == 8, "Incomplete native digest set");
        return values;
    }

    private void reportSourceAliasRefusal() throws Exception {
        AtomicReference<Messenger> remote = new AtomicReference<>();
        CountDownLatch connected = new CountDownLatch(1);
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(new Messenger(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        remote.set(null);
                        connected.countDown();
                    }
                };
        boolean bound =
                target.bindService(
                        new Intent(target, BinaryWorkerService.class),
                        connection,
                        Context.BIND_AUTO_CREATE);
        require(bound, "Cannot bind the actual report-alias refusal worker");
        File source = new File(directory, "report-alias-owned-source.bin");
        try {
            require(
                    connected.await(15, TimeUnit.SECONDS) && remote.get() != null,
                    "Actual report-alias refusal worker did not connect");
            Messenger service = remote.get();
            IBinder binder = service.getBinder();
            int binderIdentity = System.identityHashCode(binder), workerUid = -1;
            String[] labels = {"supported-hashes", "unsupported-engine", "empty-hashes"};
            String[] engines = {"hashes", "runtime-unsupported", "hashes"};
            JSONArray cases = new JSONArray();
            for (int index = 0; index < labels.length; index++) {
                byte[] expected =
                        index == 2 ? new byte[0] : "abc".getBytes(StandardCharsets.US_ASCII);
                write(source, expected);
                String expectedSha =
                        Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(expected));
                JSONObject diagnostic =
                        new JSONObject()
                                .put(
                                        "contract",
                                        "isolated-report-source-alias-refusal-and-recovery")
                                .put("caseIndex", index)
                                .put("case", labels[index])
                                .put("probeEngine", engines[index])
                                .put("stage", "opening-alias-descriptors")
                                .put("expectedSourceSizeBytes", expected.length)
                                .put("expectedSourceSha256", expectedSha)
                                .put("workerBinderIdentityHash", binderIdentity)
                                .put("completedCases", new JSONArray(cases.toString()));
                pendingContractDiagnostic = diagnostic;
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        source, ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor report =
                                ParcelFileDescriptor.open(
                                        source, ParcelFileDescriptor.MODE_WRITE_ONLY)) {
                    StructStat before = Os.fstat(input.getFileDescriptor());
                    StructStat reportIdentity = Os.fstat(report.getFileDescriptor());
                    require(
                            input.getFd() != report.getFd()
                                    && before.st_dev == reportIdentity.st_dev
                                    && before.st_ino == reportIdentity.st_ino
                                    && before.st_size == expected.length
                                    && (Os.fcntlInt(
                                                            input.getFileDescriptor(),
                                                            OsConstants.F_GETFL,
                                                            0)
                                                    & OsConstants.O_ACCMODE)
                                            == OsConstants.O_RDONLY
                                    && (Os.fcntlInt(
                                                            report.getFileDescriptor(),
                                                            OsConstants.F_GETFL,
                                                            0)
                                                    & OsConstants.O_ACCMODE)
                                            == OsConstants.O_WRONLY,
                            "Alias fixture did not open independent RO/WO descriptors of one"
                                    + " source");
                    require(
                            Os.lseek(input.getFileDescriptor(), 1, OsConstants.SEEK_SET) == 1
                                    && Os.lseek(report.getFileDescriptor(), 0, OsConstants.SEEK_CUR)
                                            == 0,
                            "Independent alias fixture descriptions unexpectedly share an offset");
                    Message alias = Message.obtain(null, BinaryWorkerService.RUN);
                    Bundle arguments = alias.getData();
                    arguments.putString("engine", engines[index]);
                    String aliasToken = UUID.randomUUID().toString();
                    arguments.putString("token", aliasToken);
                    diagnostic.put("aliasRequestToken", aliasToken).put("stage", "sending-alias");
                    AnalysisTimeLimits.capture(target, engines[index]).writeTo(arguments);
                    arguments.putParcelable("input", input);
                    arguments.putParcelable("output", report);
                    service.send(alias);

                    // Same-Messenger ordering makes the valid reply a processing barrier for
                    // the preceding alias. No sleep or reply on the unsafe descriptor is used.
                    JSONObject recovered = hashOnConnection(service, source, diagnostic);
                    diagnostic.put("stage", "validating-source-and-connection");
                    JSONObject worker = recovered.getJSONObject("worker");
                    int currentUid = worker.getInt("workerUid");
                    if (workerUid == -1) workerUid = currentUid;
                    require(
                            currentUid == workerUid
                                    && binder.isBinderAlive()
                                    && remote.get() != null
                                    && remote.get().getBinder() == binder,
                            "Report-alias refusal killed or replaced the connected worker");
                    require(
                            digests(recovered).get("SHA256").equals(expectedSha),
                            "Same-connection native recovery hash differs from the original"
                                    + " source");
                    StructStat after = Os.fstat(input.getFileDescriptor());
                    byte[] actual = new byte[expected.length];
                    if (actual.length != 0)
                        require(
                                Os.pread(input.getFileDescriptor(), actual, 0, actual.length, 0)
                                        == actual.length,
                                "Cannot independently read the complete alias fixture source");
                    long inputPosition =
                            Os.lseek(input.getFileDescriptor(), 0, OsConstants.SEEK_CUR);
                    long reportPosition =
                            Os.lseek(report.getFileDescriptor(), 0, OsConstants.SEEK_CUR);
                    require(
                            Arrays.equals(expected, actual)
                                    && after.st_dev == before.st_dev
                                    && after.st_ino == before.st_ino
                                    && after.st_size == expected.length
                                    && input.getFileDescriptor().valid()
                                    && report.getFileDescriptor().valid()
                                    && inputPosition == 1
                                    && reportPosition == 0,
                            "Refused report alias changed source bytes, size or descriptor"
                                    + " positions");
                    cases.put(
                            new JSONObject()
                                    .put("case", labels[index])
                                    .put("probeEngine", engines[index])
                                    .put("sourceSizeBytes", expected.length)
                                    .put("sourceSha256", expectedSha)
                                    .put("sameDeviceInode", true)
                                    .put("independentReadWriteOpenDescriptions", true)
                                    .put("inputOffsetBefore", 1)
                                    .put("inputOffsetAfter", inputPosition)
                                    .put("reportOffsetBefore", 0)
                                    .put("reportOffsetAfter", reportPosition)
                                    .put("sourceBytesAndIdentityUnchanged", true)
                                    .put("parentDescriptorsRemainValid", true)
                                    .put("serializedEnvelopeObserved", false)
                                    .put("sameConnectionHashSucceeded", true)
                                    .put("recoverySha256", digests(recovered).get("SHA256"))
                                    .put("workerUid", currentUid)
                                    .put("workerBinderIdentityHash", binderIdentity)
                                    .put("recoveryWorker", worker));
                }
            }
            record(
                    "isolated-report-source-alias-refusal-and-recovery",
                    new JSONObject()
                            .put("cases", cases)
                            .put("sameConnectionAcrossAllCases", true)
                            .put("recoveryReportReadDeadlineMs", 30000)
                            .put("maximumRecoveryReportBytes", 1024 * 1024)
                            .put("workerUid", workerUid)
                            .put("workerBinderIdentityHash", binderIdentity)
                            .put("ordinaryUidRejectionPathExercised", false)
                            .put("mpvNativeExecuted", false)
                            .put("uiExecuted", false)
                            .put("safExecuted", false)
                            .put("arm64Executed", false));
            pendingContractDiagnostic = null;
        } finally {
            source.delete();
            try {
                target.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // Cleanup must not replace an alias/refusal/transport failure.
            }
        }
    }

    private JSONObject hashOnConnection(Messenger service, File source, JSONObject diagnostic)
            throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        String token = UUID.randomUUID().toString();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean complete = false;
        try {
            diagnostic
                    .put("recoveryRequestToken", token)
                    .put("stage", "sending-recovery")
                    .put("sourceSizeBeforeRecoveryBytes", source.length())
                    .put("recoveryReportComplete", false)
                    .put("recoveryReportReadDeadlineMs", 30000)
                    .put("maximumRecoveryReportBytes", 1024 * 1024);
            AnalysisTimeLimits.Request limits = AnalysisTimeLimits.capture(target, "hashes");
            try (ParcelFileDescriptor input =
                    ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)) {
                Message request = Message.obtain(null, BinaryWorkerService.RUN);
                Bundle arguments = request.getData();
                arguments.putString("engine", "hashes");
                arguments.putString("token", token);
                limits.writeTo(arguments);
                arguments.putParcelable("input", input);
                arguments.putParcelable("output", pipe[1]);
                service.send(request);
            }
            pipe[1].close();
            pipe[1] = null;
            diagnostic.put("stage", "reading-recovery-report");
            FileDescriptor reader = pipe[0].getFileDescriptor();
            int flags = Os.fcntlInt(reader, OsConstants.F_GETFL, 0);
            Os.fcntlInt(reader, OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
            StructPollfd poll = new StructPollfd();
            poll.fd = reader;
            poll.events = (short) OsConstants.POLLIN;
            StructPollfd[] descriptors = {poll};
            byte[] block = new byte[4096];
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (true) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0)
                    throw new IOException(
                            "Hash recovery report exceeded its 30-second read deadline");
                try {
                    if (Os.poll(descriptors, (int) remaining) == 0) continue;
                    if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLNVAL)) != 0)
                        throw new IOException("Hash recovery report pipe is unavailable");
                    int count = Os.read(reader, block, 0, block.length);
                    if (count == 0) break;
                    require(
                            count <= 1024 * 1024 - bytes.size(),
                            "Hash recovery report exceeds 1 MiB");
                    bytes.write(block, 0, count);
                } catch (ErrnoException failure) {
                    if (failure.errno != OsConstants.EINTR && failure.errno != OsConstants.EAGAIN)
                        throw new IOException("Hash recovery report read failed", failure);
                }
            }
            diagnostic.put("recoveryReportComplete", true);
            retainRecoveryBytes(diagnostic, bytes.toByteArray());
            JSONObject envelope = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            JSONObject report = envelope.getJSONObject("report");
            diagnostic.put("recoveryEnvelope", new JSONObject(envelope.toString()));
            JSONObject fields = new JSONObject();
            for (String field :
                    new String[] {
                        "ok", "error", "wholeInputComplete", "bytesRead", "fileSizeBytes"
                    }) {
                Object value = report.opt(field);
                fields.put(field, value == null ? JSONObject.NULL : value);
            }
            diagnostic.put("recoveryFields", fields).put("stage", "validating-recovery-envelope");
            limits.validateEnvelope(envelope, report);
            envelope.remove("report");
            report.put("worker", envelope);
            worker(report, "hashes");
            diagnostic.put("stage", "validating-recovery-report");
            require(
                    report.getBoolean("ok")
                            && report.getBoolean("wholeInputComplete")
                            && report.getLong("bytesRead") == source.length()
                            && report.getLong("fileSizeBytes") == source.length(),
                    "Same-connection hash request did not recover after the refused alias");
            complete = true;
            return report;
        } catch (Exception | Error error) {
            retainRecoveryBytes(diagnostic, bytes.toByteArray());
            try {
                diagnostic
                        .put("failureClass", error.getClass().getName())
                        .put("binderAliveBeforeCleanup", service.getBinder().isBinderAlive());
            } catch (Exception ignored) {
                // Diagnostic collection cannot replace the original failed assertion/read.
            }
            throw error;
        } finally {
            if (!complete) {
                try {
                    Message cancel = Message.obtain(null, BinaryWorkerService.CANCEL);
                    cancel.getData().putString("token", token);
                    service.send(cancel);
                } catch (Exception ignored) {
                    // Preserve the principal timeout/transport/refusal failure.
                }
            }
            for (ParcelFileDescriptor fd : pipe)
                if (fd != null)
                    try {
                        fd.close();
                    } catch (IOException ignored) {
                        // Closing a failed channel must not replace its original failure.
                    }
        }
    }

    private static void retainRecoveryBytes(JSONObject diagnostic, byte[] bytes) {
        try {
            diagnostic
                    .put("recoveryReportBytes", bytes.length)
                    .put(
                            "recoveryReportSha256",
                            Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                    .put(
                            "rawRecoveryReportBase64",
                            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
        } catch (Throwable ignored) {
            // The read already enforces a 1 MiB bound. This preserves exact failure bytes.
        }
    }

    private void largeHash() throws Exception {
        File file = new File(directory, "fully-written-16MiB-plus.bin");
        long size = 16L * 1024 * 1024 + 17;
        Map<String, MessageDigest> expected = new LinkedHashMap<>();
        for (String name : new String[] {"MD5", "SHA1", "SHA224", "SHA256", "SHA384", "SHA512"})
            expected.put(
                    name,
                    MessageDigest.getInstance(
                            name.startsWith("SHA") ? name.replace("SHA", "SHA-") : name));
        CRC32 crc = new CRC32();
        byte[] block = new byte[65536];
        for (int i = 0; i < block.length; i++) block[i] = (byte) (i * 37 + 11);
        try (OutputStream out = new FileOutputStream(file)) {
            for (long left = size; left > 0; ) {
                int count = (int) Math.min(left, block.length);
                out.write(block, 0, count);
                crc.update(block, 0, count);
                for (MessageDigest digest : expected.values()) digest.update(block, 0, count);
                left -= count;
            }
        }
        JSONObject report = hash(file);
        Map<String, String> got = digests(report);
        require(
                got.get("CRC32").equals(String.format(Locale.ROOT, "%08x", crc.getValue())),
                "Large CRC differs");
        for (Map.Entry<String, MessageDigest> entry : expected.entrySet())
            require(
                    got.get(entry.getKey()).equals(Inspector.toHex(entry.getValue().digest())),
                    "Large native " + entry.getKey() + " differs from independent JCA");
        record("fully-written-large-file-through-real-isolated-fd", report);
        require(file.delete(), "Large fixture cleanup failed");
    }

    private void archive() throws Exception {
        byte[] payload =
                "Exact Android isolated archive payload 世界 😀\u0000\n"
                        .getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(zipped)) {
            gzip.write(payload);
        }
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        source.write(new byte[37]);
        source.write(zipped.toByteArray());
        File original = new File(directory, "archive-disguised-as-image.jpeg");
        write(original, source.toByteArray());
        try (InputSnapshot input =
                        InputSnapshot.acquire(
                                original, directory, NativeArchiveStore.MAX_INPUT, () -> false);
                BinaryWorkerClient client = new BinaryWorkerClient(target, "archive")) {
            NativeArchiveStore.Inventory inventory =
                    NativeArchiveStore.inspect(input, 37, input.size - 37, client, () -> false);
            worker(inventory.json(), "archive");
            require(
                    inventory.complete && inventory.members.size() == 1,
                    "Actual native archive inventory incomplete");
            JSONObject evidence = new JSONObject().put("inspection", inventory.json());
            try (NativeArchiveStore.Acquired member =
                    NativeArchiveStore.acquire(
                            inventory, inventory.members.get(0), directory, client, () -> false)) {
                require(
                        Arrays.equals(payload, read(member.input.file)),
                        "Actual Android writable-PFD decoded bytes differ");
                evidence.put("acquisition", new JSONObject(member.extractionReport))
                        .put(
                                "independentPayloadSha256",
                                Inspector.toHex(
                                        MessageDigest.getInstance("SHA-256").digest(payload)));
                worker(new JSONObject(member.extractionReport), "archive");
            }
            String aliasRefusal = null;
            try {
                client.extract(input.file, input.file, 37, input.size - 37, 0, () -> false);
            } catch (IOException refused) {
                aliasRefusal = refused.getMessage();
            }
            require(
                    "Archive output must be a separate empty private file".equals(aliasRefusal),
                    "Client did not refuse input/output alias before worker dispatch");
            input.checkIdentity(() -> false);
            evidence.put("clientAliasRefusal", aliasRefusal);
            record("real-isolated-archive-read-write-descriptors", evidence);
        }
    }

    private void compiledManifest() throws Exception {
        byte[] bytes;
        CRC32 crc = new CRC32();
        try (ZipFile apk = new ZipFile(target.getApplicationInfo().sourceDir)) {
            ZipEntry entry = apk.getEntry("AndroidManifest.xml");
            require(
                    entry != null && entry.getSize() > 0 && entry.getSize() <= 65536,
                    "Trusted runtime target has no bounded compiled manifest");
            try (InputStream source = apk.getInputStream(entry);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] block = new byte[8192];
                int count;
                while ((count = source.read(block)) >= 0) {
                    require(output.size() + count <= 65536, "Compiled manifest fixture grew");
                    output.write(block, 0, count);
                    crc.update(block, 0, count);
                }
                bytes = output.toByteArray();
            }
            require(
                    bytes.length == entry.getSize() && crc.getValue() == entry.getCrc(),
                    "Trusted runtime manifest ZIP size/CRC differs");
        }
        File source = new File(directory, "own-compiled-manifest.blob");
        write(source, bytes);
        try (InputSnapshot input = InputSnapshot.acquire(source, directory, 65536, () -> false);
                BinaryWorkerClient client = new BinaryWorkerClient(target, "resources")) {
            String encoded = client.scan(input.file, () -> false);
            JSONObject report = new JSONObject(encoded);
            worker(report, "resources");
            AndroidResourceDocument document =
                    new AndroidResourceDocument(
                            input.file,
                            "Installed trusted runtime target AndroidManifest.xml",
                            input.sha256,
                            encoded,
                            () -> false);
            PackageManifest declarations = new PackageManifest(document, () -> false);
            require(
                    target.getPackageName().equals(declarations.packageName()),
                    "Actual Android worker package declaration differs from the installed target"
                            + " ID");
            require(
                    Long.valueOf(26).equals(declarations.fields.get("minSdkVersion").unsigned())
                            && Long.valueOf(35)
                                    .equals(declarations.fields.get("targetSdkVersion").unsigned()),
                    "Actual Android worker SDK declarations differ from the compiled target");
            require(
                    document.tables.getJSONArray("attributes").length() >= 8,
                    "Actual Android resource attribute table incomplete");
            input.checkIdentity(() -> false);
            record(
                    "real-isolated-compiled-manifest-declaration",
                    new JSONObject()
                            .put("manifestInputSha256", input.sha256)
                            .put("manifestInputBytes", input.size)
                            .put(
                                    "independentZipCrc32",
                                    String.format(Locale.ROOT, "%08x", crc.getValue()))
                            .put("expectedInstalledPackageId", target.getPackageName())
                            .put(
                                    "literalPackageDeclaration",
                                    declarations.fields.get("package").json())
                            .put(
                                    "minSdkDeclaration",
                                    declarations.fields.get("minSdkVersion").json())
                            .put(
                                    "targetSdkDeclaration",
                                    declarations.fields.get("targetSdkVersion").json())
                            .put("completeResourceReport", report));
        }
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream in = target.getAssets().open("test-fixtures/" + name);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] block = new byte[8192];
            int count;
            while ((count = in.read(block)) >= 0) {
                require(bytes.size() + count <= 65536, "Owned media fixture exceeds its budget");
                bytes.write(block, 0, count);
            }
            return bytes.toByteArray();
        }
    }

    private static JSONObject pdfRow(JSONObject report, long offset) throws Exception {
        JSONArray rows = report.getJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            Object value = rows.get(i);
            if (!(value instanceof JSONObject)) continue;
            JSONObject row = (JSONObject) value;
            if (row.getString("name").equals("pdf") && row.getLong("physicalOffset") == offset)
                return row;
        }
        throw new AssertionError("Actual PhotoRec did not return the expected PDF finding");
    }

    private void photoRecPdf() throws Exception {
        byte[] pdf = MetadataFixtureFactory.pdf();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[37]);
        bytes.write(pdf);
        bytes.write(new byte[97]);
        File blob = new File(directory, "pdf-disguised-as-image.jpg");
        write(blob, bytes.toByteArray());
        JSONObject valid, truncated, largeReport;
        byte[] largePayload = MetadataFixtureFactory.pdf(9 * 1024 * 1024);
        try (BinaryWorkerClient client = new BinaryWorkerClient(target, "photorec")) {
            valid = new JSONObject(client.scan(blob, () -> false));
            worker(valid, "photorec");
            require(
                    valid.getBoolean("ok")
                            && valid.getString("adapter").equals("diemobile-photorec-6"),
                    "Actual Android PhotoRec adapter differs from the reviewed file-check scope");
            require(
                    valid.getString("fileCheckCallbackScope")
                                    .equals("pdf-only/read-only-relative-stdio")
                            && valid.getLong("maximumInputReadBufferBytes") == 1376256
                            && valid.getLong("maximumCandidateFileReadBytes") == 8L * 1024 * 1024,
                    "Actual PhotoRec stdio/input budget differs");
            JSONObject row = pdfRow(valid, 37);
            require(
                    row.getBoolean("fileCheckAttempted")
                            && row.getBoolean("fileCheckExecuted")
                            && row.isNull("fileCheckFailureReason")
                            && row.getLong("reportedSizeBytes") == pdf.length,
                    "Actual Bionic PDF file-check did not supply the exact embedded payload");
            ByteArrayOutputStream recovered = new ByteArrayOutputStream();
            RangeAnalysis.copy(
                    blob,
                    new FileRange(blob.length(), 37, row.getLong("reportedSizeBytes")),
                    recovered,
                    () -> false);
            require(
                    Arrays.equals(pdf, recovered.toByteArray()),
                    "Android native PDF extent changed the actual bytes");
            byte[] damaged = Arrays.copyOf(pdf, pdf.length - 7);
            File broken = new File(directory, "truncated.pdf");
            write(broken, damaged);
            truncated = new JSONObject(client.scan(broken, () -> false));
            worker(truncated, "photorec");
            require(truncated.getBoolean("ok"), "Truncated PDF crashed the actual Android worker");
            JSONObject failed = pdfRow(truncated, 0);
            require(
                    failed.getBoolean("fileCheckExecuted") && failed.isNull("reportedSizeBytes"),
                    "Actual Android missing-footer PDF acquired a recovered boundary");
            File largeFile = new File(directory, "large-document.blob");
            try (OutputStream out = new FileOutputStream(largeFile)) {
                out.write(new byte[37]);
                out.write(largePayload);
                out.write(new byte[97]);
            }
            largeReport = new JSONObject(client.scan(largeFile, () -> false));
            worker(largeReport, "photorec");
            require(largeReport.getBoolean("ok"), "Actual Android large PDF scan failed");
            JSONObject largeRow = pdfRow(largeReport, 37);
            require(
                    largeRow.getLong("reportedSizeBytes") == largePayload.length
                            && largeRow.getBoolean("fileCheckMetadataReadLimited")
                            && largeRow.getBoolean("fileCheckBoundaryEstablished")
                            && largeRow.getLong("fileCheckSourceBytesProcessed")
                                    <= 8L * 1024 * 1024,
                    "Actual Bionic optional date-read work discarded the established PDF file");
            try (RandomAccessFile input = new RandomAccessFile(largeFile, "r")) {
                input.seek(37);
                byte[] block = new byte[65536];
                for (int position = 0; position < largePayload.length; ) {
                    int count = Math.min(block.length, largePayload.length - position);
                    input.readFully(block, 0, count);
                    for (int i = 0; i < count; i++)
                        require(
                                block[i] == largePayload[position + i],
                                "Actual large PDF native range differs from the expected file"
                                        + " bytes");
                    position += count;
                }
            }
        }
        record(
                "isolated-photorec-pdf-recovery",
                new JSONObject()
                        .put("valid", valid)
                        .put("truncated", truncated)
                        .put("large", largeReport)
                        .put("expectedLargePayloadSizeBytes", largePayload.length)
                        .put(
                                "expectedLargePayloadSha256",
                                Inspector.toHex(
                                        MessageDigest.getInstance("SHA-256").digest(largePayload)))
                        .put("expectedPayloadSizeBytes", pdf.length)
                        .put(
                                "expectedPayloadSha256",
                                Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(pdf)))
                        .put("physicalOffset", 37)
                        .put("pdfRendererExecuted", false));
    }

    private static final String MPEG_SUPPRESSED_MODE =
            "upstream-active-mpeg-header-ignored/data-callback-suppressed";

    private static byte[] mpegSector(boolean restart) {
        byte[] pack = {
            0, 0, 1, (byte) 0xba, 0x44, 0, 4, 0, 4, 1, 0x43, 0x38, (byte) 0xdb, (byte) 0xf8
        };
        if (!restart) pack[5] = 1;
        byte[] sector = new byte[2048];
        Arrays.fill(sector, (byte) 0xa7);
        System.arraycopy(pack, 0, sector, 0, pack.length);
        int at = pack.length, payload = sector.length - at - 6;
        sector[at] = 0;
        sector[at + 1] = 0;
        sector[at + 2] = 1;
        sector[at + 3] = (byte) 0xbe;
        sector[at + 4] = (byte) (payload >>> 8);
        sector[at + 5] = (byte) payload;
        return sector;
    }

    private static String writeMpeg(File source, int sectors, boolean firstRestart, int restartAt)
            throws Exception {
        MessageDigest payload = MessageDigest.getInstance("SHA-256");
        byte[] prefix = new byte[37], suffix = new byte[64];
        Arrays.fill(prefix, (byte) 0x5a);
        Arrays.fill(suffix, (byte) 0x5a);
        try (OutputStream output = new FileOutputStream(source)) {
            output.write(prefix);
            for (int index = 0; index < sectors; index++) {
                byte[] bytes = mpegSector(index == 0 ? firstRestart : index == restartAt);
                output.write(bytes);
                payload.update(bytes);
            }
            output.write(suffix);
        }
        return Inspector.toHex(payload.digest());
    }

    private static List<Integer> mpegRows(JSONObject report) throws Exception {
        JSONArray rows = report.getJSONArray("rows");
        List<Integer> ordinals = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            Object value = rows.get(index);
            if (value instanceof JSONObject && ((JSONObject) value).getString("name").equals("mpg"))
                ordinals.add(index);
        }
        return ordinals;
    }

    /**
     * Actual Binder duplicate shares the original open-file position, independently checked below.
     */
    private JSONObject photoRecSharedDescriptor(File source, JSONObject descriptor)
            throws Exception {
        AtomicReference<Messenger> remote = new AtomicReference<>();
        CountDownLatch connected = new CountDownLatch(1);
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(new Messenger(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        connected.countDown();
                    }
                };
        boolean bound =
                target.bindService(
                        new Intent(target, BinaryWorkerService.class),
                        connection,
                        Context.BIND_AUTO_CREATE);
        require(bound, "Cannot bind the actual MPEG descriptor worker");
        ParcelFileDescriptor[] pipe = null;
        try (RandomAccessFile original = new RandomAccessFile(source, "r")) {
            require(
                    connected.await(15, TimeUnit.SECONDS) && remote.get() != null,
                    "Actual MPEG descriptor worker did not connect");
            original.seek(17);
            StructStat before = Os.fstat(original.getFD());
            pipe = ParcelFileDescriptor.createPipe();
            AnalysisTimeLimits.Request limits = AnalysisTimeLimits.capture(target, "photorec");
            try (ParcelFileDescriptor shared = ParcelFileDescriptor.dup(original.getFD())) {
                Message run = Message.obtain(null, BinaryWorkerService.RUN);
                Bundle data = run.getData();
                data.putString("engine", "photorec");
                data.putString("token", UUID.randomUUID().toString());
                limits.writeTo(data);
                data.putParcelable("input", shared);
                data.putParcelable("output", pipe[1]);
                remote.get().send(run);
            }
            pipe[1].close();
            pipe[1] = null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream reader = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])) {
                pipe[0] = null;
                byte[] block = new byte[65536];
                for (int count; (count = reader.read(block)) != -1; ) {
                    require(
                            count <= 32 * 1024 * 1024 - bytes.size(),
                            "MPEG worker report exceeds 32 MiB");
                    bytes.write(block, 0, count);
                }
            }
            StructStat after = Os.fstat(original.getFD());
            require(
                    original.getFD().valid() && original.getFilePointer() == 17,
                    "Actual isolated MPEG parser changed caller descriptor ownership/position");
            require(
                    before.st_dev == after.st_dev
                            && before.st_ino == after.st_ino
                            && before.st_size == after.st_size
                            && before.st_mtime == after.st_mtime
                            && before.st_ctime == after.st_ctime,
                    "Actual isolated MPEG parser changed its source descriptor identity");
            JSONObject envelope = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            JSONObject nativeReport = envelope.getJSONObject("report");
            limits.validateEnvelope(envelope, nativeReport);
            envelope.remove("report");
            nativeReport.put("worker", envelope);
            worker(nativeReport, "photorec");
            descriptor
                    .put("callerPositionBefore", 17)
                    .put("callerPositionAfter", original.getFilePointer())
                    .put("callerDescriptorStillValid", true)
                    .put("deviceInodeSizeAndTimesUnchanged", true)
                    .put("descriptorSizeBytes", before.st_size)
                    .put("reportTransportBytes", bytes.size());
            return nativeReport;
        } finally {
            if (pipe != null) for (ParcelFileDescriptor fd : pipe) if (fd != null) fd.close();
            target.unbindService(connection);
        }
    }

    private void photoRecMpeg() throws Exception {
        final int sectors = 4608;
        final long payloadBytes = 9L * 1024 * 1024;
        final String expectedSha =
                "a78ec64f310dbedba7ef3c37c0e20b213559a1a55fd562095833c2f7a40b82ba";
        final String expectedSourceSha =
                "86593e36855315d927182606268da1303dc15cd5236cc97c1c4274aada59f000";
        File source = new File(directory, "mpeg-packets-disguised.jpeg");
        File recovered = new File(directory, "mpeg-actual-recovered.private");
        File restartFile = new File(directory, "mpeg-independent-restart.blob");
        File unownedFile = new File(directory, "mpeg-nonzero-first.blob");
        File truncatedFile = new File(directory, "mpeg-incomplete-packet.blob");
        try {
            require(
                    writeMpeg(source, sectors, true, -1).equals(expectedSha),
                    "Generated MPEG payload differs from the independent fixed packet oracle");
            require(source.length() == 37 + payloadBytes + 64, "MPEG source was not fully written");
            String sourceSha = reportDigest(source);
            require(
                    sourceSha.equals(expectedSourceSha),
                    "Generated MPEG wrapper differs from the independent fixed source oracle");
            JSONObject descriptor = new JSONObject();
            JSONObject large = photoRecSharedDescriptor(source, descriptor);
            require(
                    large.getBoolean("ok")
                            && large.getString("adapter").equals("diemobile-photorec-6")
                            && large.getLong("fileSizeBytes") == source.length()
                            && large.getLong("maximumCandidateDataBufferBytes") == 64L * 1024 * 1024
                            && large.getLong("maximumCandidateFileReadBytes") == 8L * 1024 * 1024
                            && large.getLong("maximumCallbackBufferWorkBytes") == 256L * 1024 * 1024
                            && large.getString("mpegContinuationPolicy")
                                    .equals(
                                            "standalone-header-retained/active-mpeg-header-ignored/bounded-owner-only"),
                    "Actual isolated MPEG adapter budgets/policy differ");
            require(
                    large.getBoolean("ownedDescriptorDuplicate")
                            && large.getBoolean("callerDescriptorPositionPreserved")
                            && large.getString("inputReadMode").equals("bounded-pread-windows")
                            && large.getLong("maximumInputReadBufferBytes") == 1376256
                            && large.getLong("sourceReadBytes") >= source.length()
                            && large.getBoolean("fullBlobSearch")
                            && !large.getBoolean("nestedResultsPruned")
                            && !large.getBoolean("unknownExtentsInferred"),
                    "Actual isolated MPEG input/search contracts differ");
            List<Integer> rows = mpegRows(large);
            require(
                    rows.size() == sectors
                            && large.getInt("dataCallbacksSuppressedCount") == sectors - 1,
                    "Original MPEG pack occurrences were dropped or optional-work count differs");
            int ownerIndex = rows.get(0);
            JSONObject owner = large.getJSONArray("rows").getJSONObject(ownerIndex);
            require(
                    owner.getLong("physicalOffset") == 37
                            && owner.getBoolean("headerAccepted")
                            && owner.getBoolean("dataCheckExecuted")
                            && owner.getInt("dataCheckStatus") == 2
                            && !owner.getBoolean("dataCheckLimited")
                            && owner.isNull("dataCheckSuppressedByCandidate")
                            && owner.getLong("reportedSizeBytes") == payloadBytes
                            && owner.getLong("dataCheckSourceBytesProcessed") == payloadBytes + 64,
                    "The >8 MiB MPEG owner did not obtain its exact native boundary");
            for (int index = 1; index < rows.size(); index++) {
                JSONObject row = large.getJSONArray("rows").getJSONObject(rows.get(index));
                require(
                        row.getLong("physicalOffset") == 37L + index * 2048
                                && row.getBoolean("headerAccepted")
                                && row.isNull("reportedSizeBytes")
                                && !row.getBoolean("dataCheckExecuted")
                                && row.isNull("dataCheckStatus")
                                && !row.getBoolean("dataCheckLimited")
                                && row.isNull("dataCheckLimitReason")
                                && row.getLong("dataCheckSourceBytesProcessed") == 0
                                && row.getString("dataCheckMode").equals(MPEG_SUPPRESSED_MODE)
                                && row.getInt("dataCheckSuppressedByCandidate") == ownerIndex,
                        "MPEG continuation lost a header, owner ordinal or unknown independent"
                                + " extent");
            }
            try (OutputStream output = new FileOutputStream(recovered)) {
                RangeAnalysis.copy(
                        source,
                        new FileRange(source.length(), 37, owner.getLong("reportedSizeBytes")),
                        output,
                        () -> false);
            }
            require(
                    recovered.length() == payloadBytes
                            && reportDigest(recovered).equals(expectedSha)
                            && reportDigest(source).equals(sourceSha),
                    "Native MPEG range copy changed payload bytes or source identity");

            JSONObject restart, unowned, truncated;
            String restartSha, unownedSha, truncatedSha;
            try (BinaryWorkerClient client = new BinaryWorkerClient(target, "photorec")) {
                writeMpeg(restartFile, 8, true, 3);
                restartSha = reportDigest(restartFile);
                restart = new JSONObject(client.scan(restartFile, () -> false));
                worker(restart, "photorec");
                List<Integer> restartRows = mpegRows(restart);
                require(
                        restart.getBoolean("ok")
                                && restartRows.size() == 8
                                && restart.getInt("dataCallbacksSuppressedCount") == 6,
                        "Independent MPEG restart candidate/count differs");
                JSONObject outer = restart.getJSONArray("rows").getJSONObject(restartRows.get(0));
                JSONObject inner = restart.getJSONArray("rows").getJSONObject(restartRows.get(3));
                require(
                        outer.getLong("reportedSizeBytes") == 8 * 2048
                                && inner.getLong("physicalOffset") == 37 + 3 * 2048
                                && inner.getBoolean("dataCheckExecuted")
                                && inner.getLong("reportedSizeBytes") == 5 * 2048
                                && inner.isNull("dataCheckSuppressedByCandidate"),
                        "SCR=0 restart was suppressed or the original owner was manually split");
                writeMpeg(unownedFile, 8, false, -1);
                unownedSha = reportDigest(unownedFile);
                unowned = new JSONObject(client.scan(unownedFile, () -> false));
                worker(unowned, "photorec");
                JSONObject standalone =
                        unowned.getJSONArray("rows").getJSONObject(mpegRows(unowned).get(0));
                require(
                        unowned.getBoolean("ok")
                                && standalone.getBoolean("dataCheckExecuted")
                                && standalone.isNull("dataCheckSuppressedByCandidate")
                                && standalone.getLong("reportedSizeBytes") == 8 * 2048,
                        "A standalone nonzero-SCR pack was suppressed without a bounded owner");
                writeMpeg(truncatedFile, 5, true, -1);
                try (RandomAccessFile file = new RandomAccessFile(truncatedFile, "rw")) {
                    file.setLength(37 + 5 * 2048 - 17);
                }
                truncatedSha = reportDigest(truncatedFile);
                truncated = new JSONObject(client.scan(truncatedFile, () -> false));
                worker(truncated, "photorec");
                List<Integer> unresolved = mpegRows(truncated);
                require(
                        truncated.getBoolean("ok")
                                && unresolved.size() == 5
                                && truncated.getInt("dataCallbacksSuppressedCount") == 0,
                        "An unbounded MPEG stream acquired a suppression owner");
                for (int ordinal : unresolved) {
                    JSONObject row = truncated.getJSONArray("rows").getJSONObject(ordinal);
                    require(
                            row.isNull("reportedSizeBytes")
                                    && row.getBoolean("dataCheckExecuted")
                                    && row.isNull("dataCheckSuppressedByCandidate"),
                            "Truncated MPEG header acquired an invented extent or skipped"
                                    + " callback");
                }
            }
            require(
                    reportDigest(restartFile).equals(restartSha)
                            && reportDigest(unownedFile).equals(unownedSha)
                            && reportDigest(truncatedFile).equals(truncatedSha),
                    "Small MPEG source identities changed during isolated parsing");
            record(
                    "isolated-photorec-large-mpeg-pack-retention",
                    new JSONObject()
                            .put("sourceSizeBytes", source.length())
                            .put("sourceSha256", sourceSha)
                            .put("expectedSourceSha256", expectedSourceSha)
                            .put("payloadOffset", 37)
                            .put("payloadSizeBytes", payloadBytes)
                            .put("expectedPayloadSha256", expectedSha)
                            .put("recoveredPayloadSha256", reportDigest(recovered))
                            .put("sourceIdentityUnchanged", true)
                            .put("descriptor", descriptor)
                            .put("mpegPackRows", rows.size())
                            .put("ownerOriginalRowOrdinal", ownerIndex)
                            .put("suppressedOptionalCallbacks", sectors - 1)
                            .put("allContinuationIndependentExtentsUnknown", true)
                            .put("scrZeroRestartCheckedIndependently", true)
                            .put("standaloneNonzeroScrCheckedIndependently", true)
                            .put("unboundedOwnerSuppressionRefused", true)
                            .put("restartSourceSizeBytes", restartFile.length())
                            .put("restartSourceSha256", restartSha)
                            .put("standaloneSourceSizeBytes", unownedFile.length())
                            .put("standaloneSourceSha256", unownedSha)
                            .put("truncatedSourceSizeBytes", truncatedFile.length())
                            .put("truncatedSourceSha256", truncatedSha)
                            .put("smallSourceIdentitiesUnchanged", true)
                            .put("largeNativeReport", large)
                            .put("restartNativeReport", restart)
                            .put("standaloneNativeReport", unowned)
                            .put("truncatedNativeReport", truncated)
                            .put("uiExecuted", false)
                            .put("safExecuted", false)
                            .put("arm64Executed", false)
                            .put("mpegPlaybackExecuted", false)
                            .put("fragmentedRecoveryExecuted", false)
                            .put("nistMediaOrCoordinatesUsed", false));
        } finally {
            for (File file :
                    new File[] {source, recovered, restartFile, unownedFile, truncatedFile})
                file.delete();
        }
    }

    private void pdfPages() throws Exception {
        byte[] payload = MetadataFixtureFactory.coloredPdf();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[37]);
        bytes.write(payload);
        bytes.write(new byte[97]);
        File disguised = new File(directory, "two-pages-disguised-as-audio.wav");
        write(disguised, bytes.toByteArray());
        JSONObject nativeReport, firstReport, secondReport, fullReport;
        String expectedSha = Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(payload));
        try (InputSnapshot blob = InputSnapshot.acquire(disguised, directory, 65536, () -> false);
                BinaryWorkerClient client = new BinaryWorkerClient(target, "photorec")) {
            nativeReport = new JSONObject(client.scan(blob.file, () -> false));
            worker(nativeReport, "photorec");
            JSONObject row = pdfRow(nativeReport, 37);
            require(
                    row.getLong("reportedSizeBytes") == payload.length,
                    "Native recovery did not supply the two-page PDF's complete file extent");
            FileRange range = new FileRange(blob.size, 37, row.getLong("reportedSizeBytes"));
            PdfPagePreview.Result first =
                    PdfPagePreview.thumbnail(
                            blob,
                            range,
                            new File(directory, "pdf-thumbnail-inputs"),
                            24,
                            () -> false);
            try {
                firstReport = first.report();
                require(
                        first.pageCount == 2
                                && first.pageIndex == 0
                                && first.widthPoints == 144
                                && first.heightPoints == 96
                                && first.bitmap.getWidth() == 24
                                && first.bitmap.getHeight() == 16,
                        "Actual PDF thumbnail count, logical geometry or bounded raster differs");
                require(
                        first.bitmap.getPixel(12, 8) == Color.RED,
                        "Actual first PDF page did not render its independent red vector field");
                require(
                        firstReport.getString("sourceSha256").equals(expectedSha)
                                && firstReport.getString("parentSourceSha256").equals(blob.sha256)
                                && firstReport.getLong("physicalOffset") == 37
                                && firstReport.getInt("applicationUid")
                                        == target.getApplicationInfo().uid,
                        "Actual PDF preview source/offset/normal-UID evidence differs");
                firstReport.put(
                        "centerPixelArgb", Integer.toHexString(first.bitmap.getPixel(12, 8)));
            } finally {
                first.bitmap.recycle();
            }
            try (InputSnapshot recovered =
                    OwnedContent.stageRange(blob, range, directory, () -> false)) {
                require(
                        Arrays.equals(
                                payload, java.nio.file.Files.readAllBytes(recovered.file.toPath())),
                        "Actual renderer input differs from the complete recovered PDF bytes");
                PdfPagePreview.Result second = PdfPagePreview.read(recovered, 1, 24, () -> false);
                try {
                    secondReport = second.report();
                    require(
                            second.pageIndex == 1
                                    && second.pageCount == 2
                                    && second.bitmap.getWidth() == 24
                                    && second.bitmap.getHeight() == 16
                                    && second.bitmap.getPixel(12, 8) == Color.GREEN,
                            "Actual second PDF page did not render its independent green field");
                    secondReport.put(
                            "centerPixelArgb", Integer.toHexString(second.bitmap.getPixel(12, 8)));
                } finally {
                    second.bitmap.recycle();
                }
                PdfPagePreview.Result full =
                        PdfPagePreview.read(recovered, 0, PdfPagePreview.MAX_TARGET, () -> false);
                try {
                    fullReport = full.report();
                    require(
                            full.bitmap.getWidth() == 1536
                                    && full.bitmap.getHeight() == 1024
                                    && full.bitmap.getPixel(768, 512) == Color.RED,
                            "Actual full viewer helper did not rasterize logical points to bounded"
                                    + " useful pixels");
                    fullReport.put(
                            "centerPixelArgb", Integer.toHexString(full.bitmap.getPixel(768, 512)));
                } finally {
                    full.bitmap.recycle();
                }
                boolean refused = false;
                try {
                    PdfPagePreview.read(recovered, 2, 24, () -> false);
                } catch (IOException expected) {
                    refused = true;
                }
                require(refused, "Actual renderer accepted a page outside its returned count");
                recovered.checkIdentity(() -> false);
            }
            blob.checkIdentity(() -> false);
            require(
                    Objects.requireNonNull(new File(directory, "pdf-thumbnail-inputs").list())
                                    .length
                            == 0,
                    "Temporary embedded PDF thumbnail bytes were retained after decoding");
        }
        record(
                "exact-recovered-platform-pdf-pages",
                new JSONObject()
                        .put("nativeRecovery", nativeReport)
                        .put("firstPage", firstReport)
                        .put("secondPage", secondReport)
                        .put("fullViewerPage", fullReport)
                        .put("expectedPayloadSizeBytes", payload.length)
                        .put("expectedPayloadSha256", expectedSha)
                        .put("expectedPageCount", 2)
                        .put("outOfRangePageRefused", true)
                        .put("pdfRendererExecuted", true)
                        .put("fullGalleryUiExecuted", false));
    }

    private void mediaPreview() throws Exception {
        JSONObject evidence = new JSONObject();
        for (String fixture : new String[] {"green-64x48.mp4", "pcm-500ms.wav"}) {
            byte[] payload = asset(fixture);
            ByteArrayOutputStream blob = new ByteArrayOutputStream();
            blob.write(new byte[37]);
            blob.write(payload);
            blob.write(new byte[43]);
            File source = new File(directory, "media-" + fixture + ".blob");
            write(source, blob.toByteArray());
            try (InputSnapshot input =
                    InputSnapshot.acquire(source, directory, 65536, () -> false)) {
                MediaFrame.Result preview =
                        MediaFrame.read(
                                input,
                                new FileRange(input.size, 37, payload.length),
                                24,
                                () -> false);
                try {
                    JSONObject report = preview.report();
                    require(
                            report.getLong("physicalOffset") == 37
                                    && report.getLong("storedSizeBytes") == payload.length,
                            "Platform preview lost its exact embedded interval");
                    require(
                            report.getString("sourceSha256").equals(input.sha256),
                            "Platform preview source identity differs");
                    require(
                            report.isNull("actualTimestampUs"),
                            "Preview invented a frame timestamp");
                    if (fixture.endsWith(".mp4")) {
                        require(
                                "yes".equals(preview.metadata.get("hasVideo")),
                                "No actual video track");
                        require(
                                "64".equals(preview.metadata.get("videoWidth"))
                                        && "48".equals(preview.metadata.get("videoHeight")),
                                "Actual platform video dimensions differ from generated fixture");
                        long duration = Long.parseLong(preview.metadata.get("durationMs"));
                        require(
                                duration >= 900 && duration <= 1100,
                                "Actual video duration differs");
                        if (Build.VERSION.SDK_INT >= 27) {
                            require(
                                    preview.bitmap != null,
                                    "Actual scaled platform video frame unavailable: " + report);
                            require(
                                    preview.bitmap.getWidth() <= 24
                                            && preview.bitmap.getHeight() <= 24,
                                    "Actual video frame exceeded requested dimensions");
                            int pixel =
                                    preview.bitmap.getPixel(
                                            preview.bitmap.getWidth() / 2,
                                            preview.bitmap.getHeight() / 2);
                            int red = android.graphics.Color.red(pixel),
                                    green = android.graphics.Color.green(pixel),
                                    blue = android.graphics.Color.blue(pixel);
                            require(
                                    green > 80 && red < 60 && blue < 60,
                                    "Actual decoded fixture pixel is not independently expected"
                                            + " green");
                            report.put(
                                    "independentCenterArgb",
                                    String.format(Locale.ROOT, "%08x", pixel));
                        } else {
                            require(
                                    preview.bitmap == null
                                            && preview.frameStatus.contains("API 27+"),
                                    "API 26 must keep metadata without unbounded video decoding");
                        }
                    } else {
                        require(
                                "yes".equals(preview.metadata.get("hasAudio")),
                                "No actual audio track");
                        require(
                                preview.bitmap == null,
                                "Audio-only fixture produced an invented frame");
                        long duration = Long.parseLong(preview.metadata.get("durationMs"));
                        require(duration >= 490 && duration <= 510, "Actual PCM duration differs");
                    }
                    input.checkIdentity(() -> false);
                    evidence.put(fixture, report);
                } finally {
                    if (preview.bitmap != null) preview.bitmap.recycle();
                }
            }
        }
        evidence.put(
                "fixtureManifest",
                new JSONObject(new String(asset("fixture-manifest.json"), StandardCharsets.UTF_8)));
        record("exact-range-platform-media-preview", evidence);
    }

    private void cancelBlockedReporter(File input) throws Exception {
        AtomicReference<Messenger> remote = new AtomicReference<>();
        CountDownLatch connected = new CountDownLatch(1), died = new CountDownLatch(1);
        ServiceConnection connection =
                new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(new Messenger(binder));
                        connected.countDown();
                    }

                    public void onServiceDisconnected(ComponentName name) {
                        died.countDown();
                    }
                };
        boolean bound =
                target.bindService(
                        new Intent(target, BinaryWorkerService.class),
                        connection,
                        Context.BIND_AUTO_CREATE);
        require(bound, "Could not bind actual worker for backpressure cancellation");
        ParcelFileDescriptor[] pipe = null;
        try {
            require(connected.await(15, TimeUnit.SECONDS), "Worker binding timed out");
            pipe = ParcelFileDescriptor.createPipe();
            FileDescriptor sink = pipe[1].getFileDescriptor();
            int flags = Os.fcntlInt(sink, OsConstants.F_GETFL, 0), filled = 0;
            byte[] bytes = new byte[4096];
            try {
                Os.fcntlInt(sink, OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
                while (filled < 2 * 1024 * 1024) {
                    try {
                        int count = Os.write(sink, bytes, 0, bytes.length);
                        require(count > 0, "Pipe made no progress");
                        filled += count;
                    } catch (ErrnoException full) {
                        if (full.errno != OsConstants.EAGAIN) throw full;
                        break;
                    }
                }
            } finally {
                Os.fcntlInt(sink, OsConstants.F_SETFL, flags);
            }
            require(
                    filled > 0 && filled < 2 * 1024 * 1024,
                    "Could not establish a bounded full report pipe");
            String token = UUID.randomUUID().toString();
            try (ParcelFileDescriptor source =
                    ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)) {
                Message run = Message.obtain(null, BinaryWorkerService.RUN);
                Bundle data = run.getData();
                data.putString("token", token);
                data.putString("engine", "hashes");
                data.putParcelable("input", source);
                data.putParcelable("output", pipe[1]);
                remote.get().send(run);
            }
            Message wrong = Message.obtain(null, BinaryWorkerService.CANCEL);
            wrong.getData().putString("token", token + "-wrong");
            remote.get().send(wrong);
            require(
                    !died.await(250, TimeUnit.MILLISECONDS)
                            && remote.get().getBinder().isBinderAlive(),
                    "Wrong job token killed the worker");
            Message cancel = Message.obtain(null, BinaryWorkerService.CANCEL);
            cancel.getData().putString("token", token);
            remote.get().send(cancel);
            require(
                    died.await(10, TimeUnit.SECONDS),
                    "Correct job token did not stop the blocked isolated worker");
            require(
                    android.os.Process.myUid() == target.getApplicationInfo().uid,
                    "Parent app UID/lifetime changed");
            record(
                    "token-scoped-kill-with-blocked-report-pipe",
                    new JSONObject()
                            .put("prefilledPipeBytes", filled)
                            .put("wrongTokenSurvived", true)
                            .put("correctTokenDisconnected", true));
        } finally {
            if (pipe != null) for (ParcelFileDescriptor fd : pipe) if (fd != null) fd.close();
            try {
                target.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}

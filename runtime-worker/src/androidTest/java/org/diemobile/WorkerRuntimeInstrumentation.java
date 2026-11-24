// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.Instrumentation;
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
                                + " Binder, descriptors, PDF file recovery, compiled manifest"
                                + " declarations, token cancellation and the production platform"
                                + " media/PDF preview and text clipboard/URI helpers. This does not"
                                + " verify ARM64, full UI, MPV playback or 16 KiB-page runtime.");
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
            largeHash();
            archive();
            archiveSession();
            compiledManifest();
            photoRecPdf();
            pdfPages();
            mediaPreview();
            cancelBlockedReporter(abc);
            record("normal-client-recovers-after-worker-death", hash(abc));
            textActions();
            success = true;
        } catch (Throwable failure) {
            try {
                StringWriter text = new StringWriter();
                failure.printStackTrace(new PrintWriter(text));
                report.put("failure", text.toString());
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
        TextActionRuntimeActivity activity =
                (TextActionRuntimeActivity)
                        startActivitySync(
                                new Intent(target, TextActionRuntimeActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> sensitive = new AtomicReference<>(false);
        String raw = "研究 😀 &?#/\n" + (char) 0 + (char) 0x202e + (char) 0xd800;
        try {
            require(
                    activity.awaitFocus(),
                    "Clipboard fixture never obtained window focus: " + activity.focusState());
            waitForIdleSync();
            runOnMainSync(
                    () -> {
                        ClipboardManager clipboard =
                                (ClipboardManager)
                                        activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        try {
                            require(activity.hasWindowFocus(), "Clipboard window lost focus");
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
                            .put("exactUtf16Clipboard", true)
                            .put("clipboardUtf16Units", raw.length())
                            .put("nulBidiSupplementaryAndIsolatedSurrogate", true)
                            .put("oversizedClipboardPreservedPrevious", true)
                            .put("sensitiveClipFlagApplicable", Build.VERSION.SDK_INT >= 33)
                            .put("sensitiveClipFlag", sensitive.get())
                            .put("engines", engines)
                            .put("externalBrowserLaunched", false)
                            .put("externalServiceContacted", false));
        } finally {
            runOnMainSync(activity::finish);
            waitForIdleSync();
        }
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
                            && valid.getString("adapter").equals("diemobile-photorec-5"),
                    "Actual Android PhotoRec adapter differs from the reviewed file-check scope");
            require(
                    valid.getString("fileCheckCallbackScope")
                                    .equals("pdf-only/read-only-relative-stdio")
                            && valid.getLong("maximumInputReadBufferBytes") == 1376256,
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

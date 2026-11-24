// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Owns archive inputs/captures and a bounded nested trail, independent of Activity lifetime. */
final class NativeArchiveSession implements AutoCloseable {
    static final int MAX_LEVELS = 16, MAX_STATE = 192 * 1024;
    static final long MAX_NESTED_BYTES = 512L * 1024 * 1024, MAX_REPORT_BYTES = 64L * 1024 * 1024;

    static final class Location {
        final InputSnapshot input;
        final String name, acquisition;
        final int parentIndex;
        NativeArchiveStore.Inventory inventory;
        File capture;
        String captureSha, acquisitionSha;
        File acquisitionFile;
        String prefix = "", filter = "";
        int page;
        boolean allEntries;

        Location(InputSnapshot input, String name, int parentIndex, String acquisition) {
            this.input = input;
            this.name = name;
            this.parentIndex = parentIndex;
            this.acquisition = acquisition;
        }
    }

    ArchiveLaunch launch;
    final File directory;
    private final ArchiveRunner runner;
    private final ArrayList<Location> trail = new ArrayList<>();
    private long retainedDerivedBytes;
    private final ArrayList<JSONObject> failures = new ArrayList<>();

    private NativeArchiveSession(File directory, ArchiveRunner runner) {
        this.directory = directory;
        this.runner = runner;
    }

    static NativeArchiveSession open(
            File source,
            String name,
            File base,
            long offset,
            long length,
            ArchiveRunner runner,
            BooleanSupplier stop)
            throws Exception {
        return open(source, name, base, offset, length, runner, null, stop);
    }

    static NativeArchiveSession open(
            File source,
            String name,
            File base,
            long offset,
            long length,
            ArchiveRunner runner,
            ArchiveLaunch launch,
            BooleanSupplier stop)
            throws Exception {
        // Context cache roots can have Android-managed ancestor aliases. Anchor the
        // trusted root once; per-entry canonical checks must still reject leaf links.
        base = base.getCanonicalFile();
        if (!base.isDirectory() && !base.mkdirs())
            throw new IOException("Cannot create archive sessions");
        File directory = File.createTempFile("archive-session-", ".owned", base);
        if (!directory.delete() || !directory.mkdir())
            throw new IOException("Cannot create private archive session");
        NativeArchiveSession session = new NativeArchiveSession(directory, runner);
        InputSnapshot input = null;
        try {
            input = InputSnapshot.acquire(source, directory, NativeArchiveStore.MAX_INPUT, stop);
            if (launch != null) {
                launch.requireSource(input);
                if (launch.offset != offset || launch.length != length)
                    throw new IOException(
                            "Archive launch interval differs from its requested native scope");
            }
            session.launch = launch;
            Location root = new Location(input, name, -1, null);
            session.trail.add(root);
            input = null;
            session.capture(root, offset, length, stop);
            return session;
        } catch (Exception failure) {
            if (input != null) input.close();
            session.close();
            throw failure;
        }
    }

    Location current() {
        return trail.get(trail.size() - 1);
    }

    List<Location> locations() {
        return Collections.unmodifiableList(trail);
    }

    int depth() {
        return trail.size();
    }

    long retainedBytes() {
        return retainedDerivedBytes;
    }

    void browse(NativeArchiveStore.Member member, BooleanSupplier stop) throws Exception {
        if (trail.size() >= MAX_LEVELS
                || member.size != null && member.size > MAX_NESTED_BYTES - retainedBytes())
            throw new IOException(
                    "Nested archive budget: 16 levels / 512 MiB retained derived bytes");
        NativeArchiveStore.Acquired child = acquire(member, directory, stop);
        Location location =
                new Location(child.input, member.name, member.index, child.extractionReport);
        try {
            if (child.input.size > MAX_NESTED_BYTES - retainedBytes())
                throw new IOException("Nested bytes exceed 512 MiB");
            capture(location, 0, child.input.size, stop);
            if (!location.inventory.complete)
                throw new IOException(
                        "Member is not a complete supported archive: "
                                + location.inventory.failure);
            EvidenceRows.check(stop);
            trail.add(location);
        } catch (Exception failure) {
            try {
                recordFailure(
                        "nested-inspection",
                        member,
                        failure,
                        location.inventory == null ? null : location.inventory.raw);
            } finally {
                child.close();
                if (location.capture != null) location.capture.delete();
                if (location.acquisitionFile != null) location.acquisitionFile.delete();
                retainedDerivedBytes = privateUsage()[0];
            }
            throw failure;
        }
    }

    NativeArchiveStore.Acquired acquire(
            NativeArchiveStore.Member member, File target, BooleanSupplier stop) throws Exception {
        try {
            return NativeArchiveStore.acquire(current().inventory, member, target, runner, stop);
        } catch (Exception failure) {
            recordFailure(
                    "member-acquisition",
                    member,
                    failure,
                    failure instanceof NativeArchiveStore.AcquisitionException
                            ? ((NativeArchiveStore.AcquisitionException) failure).report
                            : null);
            throw failure;
        }
    }

    void back() {
        if (trail.size() < 2) return;
        trail.remove(trail.size() - 1);
        // Previous saved descriptors may still refer to this node. Retain bounded private bytes
        // until the session finishes, so recreation during a Back/scope operation remains valid.
    }

    void changeScope(long offset, long length, BooleanSupplier stop) throws Exception {
        capture(current(), offset, length, stop);
        current().prefix = "";
        current().page = 0;
    }

    private void capture(Location location, long offset, long length, BooleanSupplier stop)
            throws Exception {
        long[] usage = privateUsage();
        NativeArchiveStore.Inventory result =
                NativeArchiveStore.inspect(location.input, offset, length, runner, stop);
        long reportBytes = usage[1] + result.raw.getBytes(StandardCharsets.UTF_8).length;
        if (location.acquisition != null && location.acquisitionFile == null)
            reportBytes += location.acquisition.getBytes(StandardCharsets.UTF_8).length;
        if (reportBytes > MAX_REPORT_BYTES || usage[0] > MAX_NESTED_BYTES)
            throw new IOException(
                    "Retained archive budget: 512 MiB derived bytes / 64 MiB reports");
        File file = File.createTempFile("archive-capture-", ".json", directory);
        File acquisitionFile = location.acquisitionFile;
        String acquisitionSha = location.acquisitionSha;
        boolean createdAcquisition = false;
        try {
            byte[] bytes = result.raw.getBytes(StandardCharsets.UTF_8);
            Files.write(file.toPath(), bytes);
            String identity = sha(bytes);
            if (location.acquisition != null && acquisitionFile == null) {
                acquisitionFile =
                        new File(
                                directory,
                                "archive-acquisition-" + location.input.file.getName() + ".json");
                createdAcquisition = true;
                byte[] acquisition = location.acquisition.getBytes(StandardCharsets.UTF_8);
                Files.write(acquisitionFile.toPath(), acquisition);
                acquisitionSha = sha(acquisition);
            }
            EvidenceRows.check(stop);
            // Keep prior captures until close: an in-flight recreation may reference them.
            location.inventory = result;
            location.capture = file;
            location.captureSha = identity;
            location.acquisitionFile = acquisitionFile;
            location.acquisitionSha = acquisitionSha;
            retainedDerivedBytes = usage[0];
        } catch (Exception failure) {
            file.delete();
            if (createdAcquisition) acquisitionFile.delete();
            throw failure;
        }
    }

    JSONObject evidence() throws Exception {
        JSONArray chain = new JSONArray();
        for (Location location : trail) {
            chain.put(
                    new JSONObject()
                            .put("sourceSha256", location.input.sha256)
                            .put("sourceSizeBytes", location.input.size)
                            .put("displayName", location.name)
                            .put("parentEntryIndex", location.parentIndex)
                            .put(
                                    "acquisitionFromParentRaw",
                                    location.acquisition == null
                                            ? JSONObject.NULL
                                            : location.acquisition)
                            .put("inspection", location.inventory.json())
                            .put("inspectionRaw", location.inventory.raw)
                            .put("inspectionSha256", location.captureSha)
                            .put(
                                    "acquisitionFromParent",
                                    location.acquisition == null
                                            ? JSONObject.NULL
                                            : new JSONObject(location.acquisition)));
        }
        return new JSONObject()
                .put("schema", "org.diemobile.archive-session")
                .put("schemaVersion", 1)
                .put("originalLaunch", launch == null ? JSONObject.NULL : launch.json())
                .put("originalLaunchRaw", launch == null ? JSONObject.NULL : launch.raw)
                .put("locations", chain)
                .put("failedAttempts", failureEvidence())
                .put("physicalMemberOffsets", JSONObject.NULL)
                .put(
                        "meaning",
                        "Complete returned reports and exact source identities. Parsing intervals"
                                + " are not recovered extents; members are derived logical bytes."
                                + " Metadata iteration does not verify every payload.");
    }

    /** Stream current-chain raw witnesses without building another complete parsed report tree. */
    void writeCapturedEvidence(java.io.Writer out, BooleanSupplier stop) throws Exception {
        out.write(
                "{\"schema\":\"org.diemobile.archive-origin-trail\",\"schemaVersion\":1,\"originalLaunchRaw\":");
        EvidenceJson.write(out, launch == null ? JSONObject.NULL : launch.raw, stop);
        out.write(",\"locations\":[");
        boolean first = true;
        for (Location location : trail) {
            EvidenceRows.check(stop);
            location.input.checkIdentity(stop);
            if (!sha(location.inventory.raw.getBytes(StandardCharsets.UTF_8))
                    .equals(location.captureSha))
                throw new IOException("Archive inspection witness identity changed");
            if (!first) out.write(',');
            first = false;
            EvidenceJson.write(
                    out,
                    new JSONObject()
                            .put("sourceSha256", location.input.sha256)
                            .put("sourceSizeBytes", location.input.size)
                            .put("displayName", location.name)
                            .put("parentEntryIndex", location.parentIndex)
                            .put("inspectionRaw", location.inventory.raw)
                            .put("inspectionSha256", location.captureSha)
                            .put(
                                    "acquisitionFromParentRaw",
                                    location.acquisition == null
                                            ? JSONObject.NULL
                                            : location.acquisition),
                    stop);
        }
        out.write("],\"failedAttempts\":");
        EvidenceJson.write(out, failureEvidence(), stop);
        out.write(",\"physicalMemberOffsets\":null,\"meaning\":");
        EvidenceJson.quoted(
                out,
                "Current captured archive chain and retained failures. Members are logical derived"
                    + " bytes; parsing intervals are not recovered extents. Earlier visited"
                    + " branches and cross-activity ancestry are outside this capture.",
                stop);
        out.write('}');
    }

    /** Compact private descriptors only. Large original native reports remain in checked files. */
    String retentionState() throws Exception {
        JSONArray locations = new JSONArray();
        for (Location location : trail) {
            locations.put(
                    new JSONObject()
                            .put("file", location.input.file.getName())
                            .put("size", location.input.size)
                            .put("sha256", location.input.sha256)
                            .put("name", location.parentIndex < 0 ? location.name : JSONObject.NULL)
                            .put("parentIndex", location.parentIndex)
                            .put("capture", location.capture.getName())
                            .put("captureSha256", location.captureSha)
                            .put("offset", location.inventory.offset)
                            .put("length", location.inventory.length)
                            .put(
                                    "acquisition",
                                    location.acquisitionFile == null
                                            ? JSONObject.NULL
                                            : location.acquisitionFile.getName())
                            .put(
                                    "acquisitionSha256",
                                    location.acquisitionSha == null
                                            ? JSONObject.NULL
                                            : location.acquisitionSha)
                            .put("prefix", location.prefix)
                            .put("filter", location.filter)
                            .put("page", location.page)
                            .put("allEntries", location.allEntries));
        }
        String state =
                new JSONObject()
                        .put("version", 1)
                        .put("launch", launch == null ? JSONObject.NULL : launch.raw)
                        .put("directory", directory.getName())
                        .put("locations", locations)
                        .put("failures", failureDescriptors())
                        .toString();
        if (state.length() > MAX_STATE)
            throw new IOException("Archive navigation state exceeds 192 Ki-characters");
        return state;
    }

    static NativeArchiveSession restore(
            File base, String state, ArchiveRunner runner, BooleanSupplier stop) throws Exception {
        base = base.getCanonicalFile();
        if (state == null || state.length() > MAX_STATE)
            throw new IOException("Archive state absent or exceeds budget");
        JSONObject value = new JSONObject(state);
        if (value.getInt("version") != 1)
            throw new IOException("Unsupported archive state version");
        String name = value.getString("directory");
        if (!name.matches("archive-session-[A-Za-z0-9._-]+\\.owned"))
            throw new IOException("Invalid private archive session");
        File directory = leaf(base, name);
        if (!directory.isDirectory()) throw new IOException("Archive session expired");
        NativeArchiveSession session = new NativeArchiveSession(directory, runner);
        JSONArray locations = value.getJSONArray("locations");
        if (locations.length() < 1 || locations.length() > MAX_LEVELS)
            throw new IOException("Invalid archive depth");
        long reportBytes = 0;
        // Restoration failure must not delete any caller-supplied or partially validated
        // descriptor.
        for (int i = 0; i < locations.length(); i++) {
            EvidenceRows.check(stop);
            JSONObject item = locations.getJSONObject(i);
            InputSnapshot input =
                    InputSnapshot.restore(
                            leaf(directory, item.getString("file")),
                            directory,
                            item.getLong("size"),
                            item.getString("sha256"),
                            stop);
            NativeArchiveStore.requireScope(input, item.getLong("offset"), item.getLong("length"));
            File capture = leaf(directory, item.getString("capture"));
            if (!capture.getName().matches("archive-capture-[A-Za-z0-9._-]+\\.json"))
                throw new IOException("Invalid capture path");
            String raw = readCapture(capture, item.getString("captureSha256"));
            String acquisition = null;
            if (!item.isNull("acquisition")) {
                File origin = leaf(directory, item.getString("acquisition"));
                if (!origin.getName()
                        .equals("archive-acquisition-" + input.file.getName() + ".json"))
                    throw new IOException("Invalid acquisition path");
                acquisition = readCapture(origin, item.getString("acquisitionSha256"));
            }
            int parentIndex = item.getInt("parentIndex");
            if (i == 0 && parentIndex != -1
                    || i > 0
                            && (parentIndex < 0
                                    || parentIndex >= session.current().inventory.members.size()))
                throw new IOException("Invalid parent entry identity");
            String displayName =
                    i == 0
                            ? item.getString("name")
                            : session.current().inventory.members.get(parentIndex).name;
            Location location = new Location(input, displayName, parentIndex, acquisition);
            location.inventory =
                    NativeArchiveStore.restore(
                            input, item.getLong("offset"), item.getLong("length"), raw);
            location.capture = capture;
            if (acquisition != null) {
                location.acquisitionFile = leaf(directory, item.getString("acquisition"));
                location.acquisitionSha = item.getString("acquisitionSha256");
            }
            location.captureSha = item.getString("captureSha256");
            location.prefix = item.getString("prefix");
            location.filter = item.getString("filter");
            location.page = Math.max(0, item.getInt("page"));
            location.allEntries = item.getBoolean("allEntries");
            if (location.prefix.length() > 8192 || location.filter.length() > 1024)
                throw new IOException("Archive navigation exceeds limits");
            if (i == 0 && location.parentIndex != -1
                    || i > 0
                            && (location.parentIndex < 0
                                    || location.parentIndex
                                            >= session.current().inventory.members.size()))
                throw new IOException("Invalid parent entry identity");
            if (i > 0) {
                NativeArchiveStore.Member member =
                        session.current().inventory.members.get(location.parentIndex);
                if (acquisition == null || !member.canAcquire())
                    throw new IOException("Missing nested acquisition witness");
                // Recheck the retained extraction packet's source, scope, selected row and length.
                NativeArchiveStore.validateAcquisition(
                        session.current().inventory, member, input.size, acquisition);
            }
            reportBytes +=
                    raw.getBytes(StandardCharsets.UTF_8).length
                            + (acquisition == null
                                    ? 0
                                    : acquisition.getBytes(StandardCharsets.UTF_8).length);
            session.trail.add(location);
            if (session.retainedBytes() > MAX_NESTED_BYTES || reportBytes > MAX_REPORT_BYTES)
                throw new IOException("Restored archive exceeds retained budgets");
        }
        JSONArray failed = value.optJSONArray("failures");
        if (failed != null)
            for (int i = 0; i < failed.length(); i++) {
                JSONObject descriptor = failed.getJSONObject(i);
                File file = leaf(directory, descriptor.getString("file"));
                if (!file.getName().matches("archive-capture-[A-Za-z0-9._-]+\\.json"))
                    throw new IOException("Invalid failed-attempt capture path");
                String raw = readCapture(file, descriptor.getString("sha256"));
                session.failures.add(
                        new JSONObject()
                                .put("file", file.getName())
                                .put("sha256", descriptor.getString("sha256"))
                                .put("evidence", new JSONObject(raw)));
            }
        if (!value.isNull("launch")) {
            session.launch = ArchiveLaunch.parse(value.getString("launch"));
            session.launch.requireSource(session.trail.get(0).input);
        }
        long[] usage = session.privateUsage();
        if (usage[0] > MAX_NESTED_BYTES || usage[1] > MAX_REPORT_BYTES)
            throw new IOException("Restored private archive cache exceeds budgets");
        session.retainedDerivedBytes = usage[0];
        return session;
    }

    private void recordFailure(
            String operation, NativeArchiveStore.Member member, Exception failure, String raw)
            throws Exception {
        JSONObject evidence =
                new JSONObject()
                        .put("operation", operation)
                        .put("sourceSha256", current().input.sha256)
                        .put("sourceSizeBytes", current().input.size)
                        .put("scopeOffset", current().inventory.offset)
                        .put("scopeSizeBytes", current().inventory.length)
                        .put("entryIndex", member.index)
                        .put("error", failure.toString())
                        .put("nativeReport", raw == null ? JSONObject.NULL : raw);
        byte[] bytes = evidence.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > NativeArchiveStore.MAX_REPORT
                || privateUsage()[1] + bytes.length > MAX_REPORT_BYTES)
            throw new IOException(
                    failure
                            + "; failed-attempt report could not be retained within the report"
                            + " budget",
                    failure);
        File file = File.createTempFile("archive-capture-", ".json", directory);
        try {
            Files.write(file.toPath(), bytes);
            failures.add(
                    new JSONObject()
                            .put("file", file.getName())
                            .put("sha256", sha(bytes))
                            .put("evidence", evidence));
        } catch (Exception error) {
            file.delete();
            throw error;
        }
    }

    private JSONArray failureEvidence() throws Exception {
        JSONArray result = new JSONArray();
        for (JSONObject failure : failures) result.put(failure.getJSONObject("evidence"));
        return result;
    }

    private JSONArray failureDescriptors() throws Exception {
        JSONArray result = new JSONArray();
        for (JSONObject failure : failures)
            result.put(
                    new JSONObject()
                            .put("file", failure.getString("file"))
                            .put("sha256", failure.getString("sha256")));
        return result;
    }

    private long[] privateUsage() throws IOException {
        File[] files = directory.listFiles();
        if (files == null || files.length > 256)
            throw new IOException("Archive private cache exceeds 256 files or expired");
        long inputs = 0, reports = 0;
        File root = trail.get(0).input.file;
        for (File file : files) {
            if (!file.isFile() || !file.getAbsoluteFile().equals(file.getCanonicalFile()))
                throw new IOException("Unexpected non-regular archive cache entry");
            if (file.getName().matches("analysis-input-[A-Za-z0-9._-]+\\.snapshot")) {
                if (!file.equals(root)) inputs += file.length();
            } else if (file.getName()
                    .matches("archive-(capture|acquisition)-[A-Za-z0-9._-]+\\.json"))
                reports += file.length();
            else throw new IOException("Unknown private archive cache entry");
        }
        return new long[] {inputs, reports};
    }

    private static String readCapture(File file, String expected) throws Exception {
        if (!file.isFile()
                || file.length() > NativeArchiveStore.MAX_REPORT
                || !expected.matches("[0-9a-f]{64}"))
            throw new IOException("Archive capture absent/oversized");
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (!sha(bytes).equals(expected)) throw new IOException("Archive capture SHA-256 changed");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static File leaf(File directory, String name) throws IOException {
        File file = new File(directory.getCanonicalFile(), name);
        if (!file.getAbsoluteFile().equals(file.getCanonicalFile())
                || !file.getParentFile().getCanonicalFile().equals(directory.getCanonicalFile())
                || !file.getName().equals(name))
            throw new IOException("Archive descriptor escapes its private directory");
        return file;
    }

    static String abbreviated(String value, int limit) {
        return value.length() <= limit
                ? value
                : value.substring(0, limit) + "… [full value in evidence]";
    }

    private static String sha(byte[] bytes) throws Exception {
        return Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    void close(boolean retain) {
        runner.close();
        if (retain) return;
        // The directory is generated and all deletion is limited to direct regular children.
        File[] files = directory.listFiles();
        if (files != null)
            for (File file : files) {
                try {
                    if (file.isFile()
                            && file.getAbsoluteFile().equals(file.getCanonicalFile())
                            && file.getName()
                                    .matches(
                                            "(?:analysis-input-[A-Za-z0-9._-]+\\.snapshot|archive-(?:capture|acquisition)-[A-Za-z0-9._-]+\\.json)"))
                        file.delete();
                } catch (IOException ignored) {
                }
            }
        directory.delete();
    }

    @Override
    public void close() {
        close(false);
    }
}

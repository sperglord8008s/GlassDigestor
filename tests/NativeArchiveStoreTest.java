// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Real JNI plus independent original fixture expectations; no inspected program is executed. */
public final class NativeArchiveStoreTest {
    static int calls;
    static final ArchiveRunner NATIVE =
            new ArchiveRunner() {
                private int fd(FileDescriptor value) throws Exception {
                    Field field = FileDescriptor.class.getDeclaredField("fd");
                    field.setAccessible(true);
                    return field.getInt(value);
                }

                public String inspect(File file, long offset, long length, BooleanSupplier stop)
                        throws Exception {
                    EvidenceRows.check(stop);
                    calls++;
                    try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
                        return ArchiveEngine.inspectFd(fd(input.getFD()), offset, length);
                    }
                }

                public String extract(
                        File file,
                        File target,
                        long offset,
                        long length,
                        int member,
                        BooleanSupplier stop)
                        throws Exception {
                    EvidenceRows.check(stop);
                    calls++;
                    try (RandomAccessFile input = new RandomAccessFile(file, "r");
                            RandomAccessFile output = new RandomAccessFile(target, "rw")) {
                        return ArchiveEngine.extractFd(
                                fd(input.getFD()), fd(output.getFD()), offset, length, member);
                    }
                }
            };

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    interface Task {
        void run() throws Exception;
    }

    static void refuse(Task task, String reason) throws Exception {
        try {
            task.run();
            throw new AssertionError("Accepted " + reason);
        } catch (IOException expected) {
            require(expected.getMessage() != null, "Missing refusal diagnostic");
        }
    }

    static String sha(byte[] bytes) throws Exception {
        return Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static void main(String[] args) throws Exception {
        require(ArchiveEngine.AVAILABLE, ArchiveEngine.ERROR);
        Path fixtures = Path.of(args[0]),
                temporary = Files.createTempDirectory("glassdigestor-archive-store");
        File inputs = temporary.resolve("inputs").toFile(),
                members = temporary.resolve("members").toFile();
        JSONObject expected = new JSONObject(Files.readString(fixtures.resolve("expected.json")));
        int inventories = 0, acquisitions = 0;
        try {
            for (String filename : expected.keySet()) {
                JSONObject want = expected.getJSONObject(filename);
                try (InputSnapshot input =
                        InputSnapshot.acquire(
                                fixtures.resolve(filename).toFile(),
                                inputs,
                                NativeArchiveStore.MAX_INPUT,
                                () -> false)) {
                    long start = want.optLong("offset", 0);
                    NativeArchiveStore.Inventory inventory =
                            NativeArchiveStore.inspect(
                                    input, start, input.size - start, NATIVE, () -> false);
                    inventories++;
                    require(inventory.raw.contains("\"ok\""), "Raw report lost");
                    if (filename.startsWith("budget-") || filename.equals("unknown.dat")) {
                        require(
                                !inventory.complete && !inventory.failure.isEmpty(),
                                "Native refusal was hidden");
                        continue;
                    }
                    if (filename.equals("bad-crc.zip")) {
                        refuse(
                                () ->
                                        NativeArchiveStore.acquire(
                                                inventory,
                                                inventory.members.get(0),
                                                members,
                                                NATIVE,
                                                () -> false),
                                "CRC-failed bytes");
                        require(members.list().length == 0, "CRC failure retained partial payload");
                        continue;
                    }
                    require(inventory.complete, filename + ": " + inventory.failure);
                    JSONArray entries = want.getJSONArray("entries");
                    require(inventory.members.size() == entries.length(), "Inventory row loss");
                    for (int i = 0; i < inventory.members.size(); i++) {
                        NativeArchiveStore.Member member = inventory.members.get(i);
                        JSONObject entry = entries.getJSONObject(i);
                        require(member.name.equals(entry.getString("name")), "Name changed");
                        if (entry.optBoolean("link") || entry.optBoolean("directory")) {
                            refuse(
                                    () ->
                                            NativeArchiveStore.acquire(
                                                    inventory,
                                                    member,
                                                    members,
                                                    NATIVE,
                                                    () -> false),
                                    "non-regular bytes");
                            continue;
                        }
                        try (NativeArchiveStore.Acquired acquired =
                                NativeArchiveStore.acquire(
                                        inventory, member, members, NATIVE, () -> false)) {
                            byte[] bytes = Files.readAllBytes(acquired.input.file.toPath());
                            require(
                                    bytes.length == entry.getLong("size"),
                                    "Independent member size differs");
                            if (entry.has("sha256"))
                                require(
                                        sha(bytes).equals(entry.getString("sha256")),
                                        "Independent payload identity differs");
                            require(
                                    sha(bytes).equals(acquired.input.sha256),
                                    "Derived hash differs");
                            require(
                                    new JSONObject(acquired.extractionReport)
                                                    .getInt("selectedIndex")
                                            == member.index,
                                    "Index not retained");
                            acquisitions++;
                        }
                    }
                }
            }
            File original = temporary.resolve("original.bin").toFile();
            Files.copy(fixtures.resolve("mislabeled.apk"), original.toPath());
            String sourceLabel = "misleading-" + "世界😀".repeat(4000) + ".jpg";
            // Android may expose private cache through an ancestor alias (/data/user/0).
            // The alias is trusted Context storage; individual cache leaf links remain invalid.
            Path physicalCache = Files.createDirectories(temporary.resolve("physical-cache"));
            Path cacheAlias = temporary.resolve("context-cache-alias");
            Files.createSymbolicLink(cacheAlias, physicalCache);
            File aliasedSessions = cacheAlias.resolve("sessions").toFile();
            NativeArchiveSession session =
                    NativeArchiveSession.open(
                            original,
                            sourceLabel,
                            aliasedSessions,
                            0,
                            original.length(),
                            NATIVE,
                            () -> false);
            require(
                    session.current().name.equals(sourceLabel),
                    "Source display name was silently abbreviated");
            require(
                    session.evidence()
                            .getJSONArray("locations")
                            .getJSONObject(0)
                            .getString("displayName")
                            .equals(sourceLabel),
                    "Full source name missing from export");
            File directory = session.directory;
            require(
                    directory.equals(directory.getCanonicalFile()),
                    "Session retained the Context ancestor alias instead of its anchored root");
            Path sentinel = temporary.resolve("outside-session-sentinel.json");
            byte[] sentinelBytes =
                    "preserve outside bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Files.write(sentinel, sentinelBytes);
            Path linkedCapture = directory.toPath().resolve("archive-capture-link.json");
            Files.createSymbolicLink(linkedCapture, sentinel);
            NativeArchiveSession aliasSession = session;
            refuse(
                    () ->
                            aliasSession.changeScope(
                                    0, aliasSession.current().input.size, () -> false),
                    "a linked cache leaf under an aliased Context root");
            require(
                    Arrays.equals(Files.readAllBytes(sentinel), sentinelBytes),
                    "Linked-entry refusal modified bytes outside the session");
            Files.delete(linkedCapture);
            NativeArchiveStore.Inventory inventory = session.current().inventory;
            require(
                    !inventory.members.get(0).treeName(),
                    "Traversal-looking name entered virtual tree");
            require(
                    inventory.members.get(1).index != inventory.members.get(2).index,
                    "Duplicate names merged");
            original.delete();
            int before = calls;
            String state = session.retentionState();
            session.close(true);
            session = NativeArchiveSession.restore(aliasedSessions, state, NATIVE, () -> false);
            require(calls == before, "Restore reran native analysis");
            require(
                    session.current().name.equals(sourceLabel),
                    "Restoration lost full source display name");
            try (NativeArchiveStore.Acquired first =
                            session.acquire(
                                    session.current().inventory.members.get(1),
                                    members,
                                    () -> false);
                    NativeArchiveStore.Acquired second =
                            session.acquire(
                                    session.current().inventory.members.get(2),
                                    members,
                                    () -> false)) {
                require(
                        Files.readString(first.input.file.toPath()).equals("first"),
                        "Wrong first duplicate bytes");
                require(
                        Files.readString(second.input.file.toPath()).equals("second"),
                        "Wrong second duplicate bytes");
            }
            NativeArchiveStore.Inventory bound = session.current().inventory;
            JSONObject forged = new JSONObject(bound.raw).put("scopeOffset", "0");
            refuse(
                    () ->
                            NativeArchiveStore.restore(
                                    bound.source, 0, bound.length, forged.toString()),
                    "coerced scope integer");
            forged.put("scopeOffset", 0).put("sourceRevision", "different");
            refuse(
                    () ->
                            NativeArchiveStore.restore(
                                    bound.source, 0, bound.length, forged.toString()),
                    "wrong native revision");
            int stopped = calls;
            NativeArchiveSession active = session;
            refuse(
                    () -> active.acquire(bound.members.get(0), members, () -> true),
                    "cancelled acquisition");
            require(calls == stopped, "Cancelled acquisition entered native parser");
            require(
                    active.evidence().getJSONArray("failedAttempts").length() == 1,
                    "Failed acquisition evidence lost");
            JSONObject saved = new JSONObject(active.retentionState());
            String capture = saved.getJSONArray("locations").getJSONObject(0).getString("capture");
            Files.writeString(directory.toPath().resolve(capture), "{}");
            refuse(
                    () ->
                            NativeArchiveSession.restore(
                                    directory.getParentFile(),
                                    saved.toString(),
                                    NATIVE,
                                    () -> false),
                    "corrupt capture");
            require(bound.source.file.isFile(), "Failed restore deleted source");
            active.close();
            require(!directory.exists(), "Finished session leaked files");
            require(
                    inventories == 25 && acquisitions == 37,
                    "Unexpected independent fixture coverage " + inventories + "/" + acquisitions);
            System.out.println(
                    "Native archive model passed: "
                            + inventories
                            + " source-bound captures / "
                            + acquisitions
                            + " independently checked acquisitions; duplicate ordinals, offline"
                            + " restore, failures, cancellation and tamper refusal.");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }
}

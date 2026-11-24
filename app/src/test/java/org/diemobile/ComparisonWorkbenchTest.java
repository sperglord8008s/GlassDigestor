// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Actual file comparator, widgets, lifetime and native hex handoff; external streams substituted.
 */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class ComparisonWorkbenchTest {
    private ActivityController<InvestigationActivity> controller;
    private InvestigationActivity activity;
    private Path temporary;
    private File parent, cache;
    private byte[] left, right;
    private static final String LEFT_NAME = "left-\ud800\u0000照片.bin",
            RIGHT_NAME = "right-\udfff\u0000照片.bin";

    public static final class Provider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(
                Uri uri, String[] projection, String selection, String[] args, String sort) {
            if (uri.getPath().contains("no-metadata"))
                throw new UnsupportedOperationException("Fixture provider has no metadata query");
            MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
            cursor.addRow(new Object[] {RIGHT_NAME});
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            return "application/octet-stream";
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int delete(Uri uri, String where, String[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(Uri uri, ContentValues values, String where, String[] args) {
            throw new UnsupportedOperationException();
        }
    }

    @Before
    public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        cache = new File(context.getCacheDir(), "comparison-snapshots");
        remove(cache.toPath());
        temporary = Files.createTempDirectory("die-comparison-ui");
        parent = temporary.resolve("original.bin").toFile();
        left = new byte[8192];
        for (int i = 0; i < left.length; i++) left[i] = (byte) i;
        right = Arrays.copyOf(left, left.length + 100);
        for (int i = 0; i < 80; i++) right[512 + i * 4] ^= 0x7f;
        Arrays.fill(right, left.length, right.length, (byte) 0x5a);
        Files.write(parent.toPath(), left);
        Provider provider = new Provider();
        ProviderInfo info = new ProviderInfo();
        info.authority = "org.diemobile.comparison-test";
        provider.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(info.authority, provider);
        Intent intent =
                new Intent(context, InvestigationActivity.class)
                        .putExtra("provider", "comparison")
                        .putExtra("path", parent.getAbsolutePath())
                        .putExtra("name", LEFT_NAME)
                        .putExtra("sha256", Inspector.scan(parent, 4).sha256)
                        .putExtra("sourceSize", parent.length());
        controller =
                Robolectric.buildActivity(InvestigationActivity.class, intent).setup().visible();
        activity = controller.get();
        settle();
    }

    @After
    public void cleanup() throws Exception {
        if (controller != null) {
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            controller.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        }
        remove(temporary);
        remove(cache.toPath());
    }

    private static void remove(Path path) throws Exception {
        if (path != null && Files.exists(path))
            try (var paths = Files.walk(path)) {
                for (Path item : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(item);
            }
    }

    private void settle() throws Exception {
        for (int i = 0; i < 10; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(30, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!ReflectionHelpers.<Boolean>getField(activity, "busy")) return;
        }
        fail("Comparison work did not settle: " + status());
    }

    private String status() {
        return ReflectionHelpers.<TextView>getField(activity, "status").getText().toString();
    }

    private EvidenceRows rows() {
        return ReflectionHelpers.getField(activity, "source");
    }

    private ComparisonBackend backend() {
        return ReflectionHelpers.getField(activity, "backend");
    }

    private Button button(String field) {
        return ReflectionHelpers.getField(activity, field);
    }

    private void choose(String label) {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ListView list = dialog.getListView();
        for (int i = 0; i < list.getAdapter().getCount(); i++)
            if (list.getAdapter().getItem(i).toString().equals(label)) {
                list.performItemClick(null, i, i);
                return;
            }
        fail("Missing comparison action: " + label);
    }

    private void root(String label) throws Exception {
        button("views").performClick();
        choose(label);
        settle();
    }

    private void select(InputStream input, String path) throws Exception {
        root("Select / replace right-hand comparison input");
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        Uri uri = Uri.parse("content://org.diemobile.comparison-test/" + path);
        Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri, input);
        activity.onActivityResult(2, Activity.RESULT_OK, new Intent().setData(uri));
    }

    private void select() throws Exception {
        select(new ByteArrayInputStream(right), "right.bin");
        settle();
    }

    private void rowAction(int position, String label) throws Exception {
        ReflectionHelpers.<ListView>getField(activity, "records")
                .performItemClick(null, position, position);
        AlertDialog detail = ShadowAlertDialog.getLatestAlertDialog();
        detail.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        settle();
        choose(label);
        settle();
    }

    private void finish(OutputStream output) throws Exception {
        Uri uri = Uri.parse("content://org.diemobile.comparison-test/export");
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(uri, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(uri));
        settle();
    }

    private PreparedExport prepareComplete() throws Exception {
        root("Preview / export complete comparison JSON");
        AlertDialog preview = ShadowAlertDialog.getLatestAlertDialog();
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        var picker = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(picker);
        assertEquals("application/json", picker.intent.getType());
        PreparedExport prepared = ReflectionHelpers.getField(activity, "prepared");
        assertNotNull(prepared);
        return prepared;
    }

    @Test
    public void realDifferencesPageLinkToCorrectSidesAndExportExactBytes() throws Exception {
        assertFalse(rows().row(0, () -> false).json().getBoolean("complete"));
        select();
        JSONObject summary = rows().row(0, () -> false).json();
        assertEquals(80, summary.getLong("differentCommonBytes"));
        assertEquals(100, summary.getLong("rightOnlyTailBytes"));
        assertEquals(LEFT_NAME, summary.getJSONObject("left").getString("name"));
        assertEquals(RIGHT_NAME, summary.getJSONObject("right").getString("name"));
        assertTrue(parent.delete());
        root("Every differing byte run / one-sided tail");
        assertEquals(81, rows().size());
        assertNull(rows().row(0, () -> false).physicalOffset);
        button("next").performClick();
        settle();
        EvidenceRows.Page page = ReflectionHelpers.getField(activity, "shown");
        assertEquals(50, page.rows.get(0).index);
        rowAction(0, "Right exact corresponding range / hex / export");
        assertEquals(712, rows().row(0, () -> false).physicalOffset.longValue());
        assertEquals("right", backend().provenance().getString("byteSourceSide"));
        rowAction(0, "Hex at associated physical offset");
        Intent hex = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(712, hex.getLongExtra("offset", -1));
        assertEquals(right.length, hex.getLongExtra("sourceSize", -1));
        assertArrayEquals(right, Files.readAllBytes(new File(hex.getStringExtra("path")).toPath()));
        ActivityController<NativeToolsActivity> nativeViewer =
                Robolectric.buildActivity(NativeToolsActivity.class, hex).setup().visible();
        try {
            NativeToolsActivity nativeActivity = nativeViewer.get();
            for (int i = 0; i < 5; i++) {
                ReflectionHelpers.<ExecutorService>getField(nativeActivity, "worker")
                        .submit(() -> {})
                        .get(30, TimeUnit.SECONDS);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                if (!ReflectionHelpers.<Boolean>getField(nativeActivity, "busy")) break;
            }
            assertTrue(
                    ((TextView) nativeActivity.findViewById(R.id.native_output))
                            .getText()
                            .toString()
                            .startsWith("00000000000002C8"));
        } finally {
            nativeViewer.pause().stop().destroy();
        }
        rowAction(0, "Export exact stored range");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        finish(output);
        assertArrayEquals(new byte[] {right[712]}, output.toByteArray());
        button("back").performClick();
        settle();
        page = ReflectionHelpers.getField(activity, "shown");
        assertEquals(50, page.rows.get(0).index);
        controller.recreate();
        activity = controller.get();
        settle();
        assertEquals(81, rows().size());
        page = ReflectionHelpers.getField(activity, "shown");
        assertEquals(50, page.rows.get(0).index);
    }

    @Test
    public void fullFilteredExportIsCapturedBeforeExpiryAndRecreation() throws Exception {
        select();
        root("Every differing byte run / one-sided tail");
        ReflectionHelpers.<EditText>getField(activity, "filter").setText("right-only-tail");
        button("search").performClick();
        settle();
        assertEquals(1, ReflectionHelpers.<EvidenceRows.Page>getField(activity, "shown").matches);
        PreparedExport prepared = prepareComplete();
        byte[] captured = Files.readAllBytes(prepared.file.toPath());
        File session =
                new File(cache, new JSONObject(backend().retentionState()).getString("session"));
        for (File file : session.listFiles())
            if (file.getName().endsWith(".snapshot")) assertTrue(file.delete());
        assertTrue(parent.delete());
        controller.recreate();
        activity = controller.get();
        settle();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        finish(output);
        assertArrayEquals(captured, output.toByteArray());
        JSONObject report = new JSONObject(output.toString(StandardCharsets.UTF_8));
        assertEquals(81, report.getJSONArray("ranges").length());
        assertEquals(LEFT_NAME, report.getJSONObject("left").getString("name"));
        assertEquals(RIGHT_NAME, report.getJSONObject("right").getString("name"));
        assertFalse(prepared.file.exists());
        assertTrue(status(), status().contains("Export saved"));
    }

    @Test
    public void failedAndCancelledReplacementKeepsPriorPairThenRecovers() throws Exception {
        select();
        String prior = backend().provenance().getString("pairId");
        select(
                new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("Fixture provider failure");
                    }
                },
                "failed.bin");
        settle();
        assertTrue(status(), status().contains("prior captured information retained"));
        assertEquals(prior, backend().provenance().getString("pairId"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        select(
                new InputStream() {
                    @Override
                    public int read() {
                        return -1;
                    }

                    @Override
                    public int read(byte[] bytes, int offset, int count) throws IOException {
                        entered.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS))
                                throw new IOException("Cancellation fixture timed out");
                        } catch (InterruptedException error) {
                            throw new IOException(error);
                        }
                        bytes[offset] = 1;
                        return 1;
                    }
                },
                "cancelled.bin");
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            button("cancel").performClick();
        } finally {
            release.countDown();
        }
        settle();
        assertEquals(prior, backend().provenance().getString("pairId"));
        select(new ByteArrayInputStream(left), "identical.bin");
        settle();
        assertTrue(rows().row(0, () -> false).json().getBoolean("identicalBytes"));
        assertNotEquals(prior, backend().provenance().getString("pairId"));
    }

    @Test
    public void providerCloseAfterAdoptionShowsNewPairAndExplicitDiagnostic() throws Exception {
        select();
        String prior = backend().provenance().getString("pairId");
        select(
                new ByteArrayInputStream(left) {
                    @Override
                    public void close() throws IOException {
                        throw new IOException("Fixture provider close failure");
                    }
                },
                "no-metadata.bin");
        settle();
        assertNotEquals(prior, backend().provenance().getString("pairId"));
        assertTrue(rows().row(0, () -> false).json().getBoolean("identicalBytes"));
        assertTrue(status(), status().contains("was adopted"));
        assertTrue(status(), status().contains("close failure"));
        assertFalse(status(), status().contains("prior captured information retained"));
        controller.recreate();
        activity = controller.get();
        settle();
        assertTrue(rows().row(0, () -> false).json().getBoolean("identicalBytes"));
        assertTrue(status(), status().contains("was adopted"));
    }

    @Test
    public void cancelledAndFailedOutputKeepAcceptedPair() throws Exception {
        select();
        String prior = backend().provenance().getString("pairId");
        PreparedExport cancelled = prepareComplete();
        activity.onActivityResult(1, Activity.RESULT_CANCELED, null);
        settle();
        assertFalse(cancelled.file.exists());
        PreparedExport failed = prepareComplete();
        finish(
                new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw new IOException("Fixture output failure");
                    }
                });
        assertFalse(failed.file.exists());
        assertTrue(status(), status().contains("partial"));
        assertEquals(prior, backend().provenance().getString("pairId"));
    }
}

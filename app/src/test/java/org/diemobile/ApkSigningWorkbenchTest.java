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
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual generated signatures/apksig/widgets; content-provider streams alone are substituted. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public final class ApkSigningWorkbenchTest {
    private static Path keyDirectory;
    private static KeyStore keys;
    private Path temporary;
    private File input;
    private byte[] idsig;
    private ActivityController<InvestigationActivity> controller;
    private InvestigationActivity activity;

    @BeforeClass
    public static void keys() throws Exception {
        keyDirectory = Files.createTempDirectory("die-signing-ui-keys");
        ApkSigningFixture.generateKeys(keyDirectory.resolve("fixture.p12"));
        keys = ApkSigningFixture.keys(keyDirectory.resolve("fixture.p12"));
    }

    @AfterClass
    public static void removeKeys() throws Exception {
        remove(keyDirectory);
    }

    private static void remove(Path directory) throws Exception {
        if (directory != null)
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    @Before
    public void fixture() throws Exception {
        temporary = Files.createTempDirectory("die-signing-ui");
        input = ApkSigningFixture.rotated(temporary, keys, 33);
        idsig = Files.readAllBytes(temporary.resolve("rotation-33.idsig"));
        Intent intent =
                new Intent(RuntimeEnvironment.getApplication(), InvestigationActivity.class)
                        .putExtra("provider", "apk-signing")
                        .putExtra("path", input.getAbsolutePath())
                        .putExtra("name", "signed-fixture.apk")
                        .putExtra("sourceSize", input.length())
                        .putExtra(
                                "sha256",
                                Inspector.toHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(Files.readAllBytes(input.toPath()))));
        controller =
                Robolectric.buildActivity(InvestigationActivity.class, intent).setup().visible();
        activity = controller.get();
        settle();
        TestProvider provider = new TestProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = "org.diemobile.signing-test";
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal(info.authority, provider);
    }

    @After
    public void cleanup() throws Exception {
        if (controller != null) {
            ExecutorService worker = ReflectionHelpers.getField(activity, "worker");
            controller.pause().stop().destroy();
            assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
        }
        remove(temporary);
    }

    public static final class TestProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(
                Uri uri, String[] projection, String selection, String[] args, String sort) {
            MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
            cursor.addRow(new Object[] {uri.getLastPathSegment()});
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
        public int update(Uri uri, ContentValues values, String where, String[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int delete(Uri uri, String where, String[] args) {
            throw new UnsupportedOperationException();
        }
    }

    private ListView list() {
        return activity.findViewById(R.id.investigation_records);
    }

    private EvidenceRows.Row row(int index) {
        return (EvidenceRows.Row) list().getAdapter().getItem(index);
    }

    private Button button(String name) {
        return ReflectionHelpers.getField(activity, name);
    }

    private String status() {
        return ((TextView) activity.findViewById(R.id.investigation_status)).getText().toString();
    }

    private void settle() throws Exception {
        for (int i = 0; i < 10; i++) {
            ReflectionHelpers.<ExecutorService>getField(activity, "worker")
                    .submit(() -> {})
                    .get(45, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (!ReflectionHelpers.<Boolean>getField(activity, "busy")) return;
        }
        fail("Signing operation did not settle: " + status());
    }

    private void choose(String text) {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        ListView choices = dialog.getListView();
        for (int i = 0; i < choices.getAdapter().getCount(); i++)
            if (choices.getAdapter().getItem(i).toString().contains(text)) {
                choices.performItemClick(null, i, i);
                dialog.dismiss();
                return;
            }
        fail("Missing signing action " + text);
    }

    private void root(String text) throws Exception {
        button("views").performClick();
        choose(text);
        settle();
    }

    private void action(int index, String text) throws Exception {
        list().performItemClick(null, index, index);
        ShadowAlertDialog.getLatestAlertDialog()
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .performClick();
        settle();
        choose(text);
        settle();
    }

    private void search(String text) throws Exception {
        ((EditText) activity.findViewById(R.id.investigation_filter)).setText(text);
        button("search").performClick();
        settle();
    }

    private void select(byte[] bytes) throws Exception {
        Uri uri = Uri.parse("content://org.diemobile.signing-test/selected.idsig");
        Shadows.shadowOf(activity.getContentResolver())
                .registerInputStream(uri, new ByteArrayInputStream(bytes));
        activity.onActivityResult(2, Activity.RESULT_OK, new Intent().setData(uri));
        settle();
    }

    private byte[] export() throws Exception {
        Uri uri = Uri.parse("content://org.diemobile.signing-test/output");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(uri, output);
        activity.onActivityResult(1, Activity.RESULT_OK, new Intent().setData(uri));
        settle();
        assertTrue(status(), status().contains("Export saved"));
        return output.toByteArray();
    }

    private void recreate() throws Exception {
        controller.recreate();
        activity = controller.get();
        settle();
    }

    @Test
    public void targetedSignerLinksPreserveScopedCapabilitiesAndExactCertificateExports()
            throws Exception {
        assertTrue(row(0).json().getBoolean("verified"));
        assertTrue(row(0).json().getBoolean("v31"));
        assertFalse(row(0).json().getBoolean("v4"));
        root("all scheme signer records");
        search("v3.1/");
        assertEquals(1, list().getAdapter().getCount());
        JSONObject signer = row(0).json();
        assertEquals(33, signer.getInt("minimumApi"));
        assertEquals(1, signer.getJSONArray("certificateIndices").length());
        action(0, "original scoped rotation lineage");
        JSONObject lineage = row(signer.getInt("lineageIndex")).json();
        assertEquals(2, lineage.getJSONArray("nodes").length());
        JSONObject predecessor = lineage.getJSONArray("nodes").getJSONObject(0);
        assertTrue(predecessor.getBoolean("installedData"));
        assertTrue(predecessor.getBoolean("permission"));
        assertFalse(predecessor.getBoolean("rollback"));
        assertFalse(predecessor.getBoolean("auth"));
        action(
                signer.getInt("lineageIndex"),
                "Inspect certificate " + predecessor.getInt("certificateIndex"));
        int certificate = predecessor.getInt("certificateIndex");
        JSONObject details = row(certificate).json();
        assertEquals(2048, details.getJSONObject("publicKey").getInt("modulusBits"));
        assertTrue(details.getJSONArray("extensions").length() >= 5);
        action(certificate, "Export derBase64");
        assertTrue(input.delete());
        recreate();
        assertArrayEquals(ApkSigningFixture.certificate(keys, "fixture").getEncoded(), export());
    }

    @Test
    public void pendingAndSelectedDetachedInputsRestoreAfterParentExpiryAndExportExactBytes()
            throws Exception {
        root("Select / replace detached");
        assertTrue(ReflectionHelpers.getField(activity, "pickerOpen"));
        assertTrue(input.delete());
        recreate();
        select(idsig);
        assertTrue(row(0).json().getBoolean("verified"));
        assertTrue(row(0).json().getBoolean("v4"));
        recreate();
        assertTrue(row(0).json().getBoolean("v4"));
        root("Selected detached input");
        assertEquals(Long.valueOf(0), row(0).physicalOffset);
        assertEquals(
                Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(idsig)),
                row(0).json().getString("sha256"));
        assertEquals(idsig.length, ReflectionHelpers.<File>getField(activity, "bytes").length());
        root("Export exact selected .idsig");
        recreate();
        assertArrayEquals(idsig, export());
    }

    @Test
    public void corruptDetachedEvidenceRetainsEmbeddedVerificationAndFullFilteredExport()
            throws Exception {
        byte[] corrupt = idsig.clone();
        corrupt[corrupt.length - 1] ^= 1;
        root("Select / replace detached");
        select(corrupt);
        assertFalse(row(0).json().getBoolean("verified"));
        assertTrue(row(0).json().getBoolean("apksigResultVerified"));
        assertTrue(row(0).json().getBoolean("embeddedApkVerifiedWithoutDetached"));
        root("all scoped errors");
        search("V4_SIG_APK_TREE_DID_NOT_VERIFY");
        assertEquals(1, list().getAdapter().getCount());
        assertEquals("v4/0", row(0).json().getString("scope"));
        root("All collected signing information");
        EvidenceRows complete = ReflectionHelpers.getField(activity, "source");
        int count = complete.size();
        assertTrue(count > 10);
        search("V4_SIG_APK_TREE_DID_NOT_VERIFY");
        assertEquals(1, list().getAdapter().getCount());
        button("export").performClick();
        settle();
        assertTrue(input.delete());
        recreate();
        JSONObject report = new JSONObject(new String(export(), StandardCharsets.UTF_8));
        assertEquals(count, report.getJSONArray("rows").length());
        assertEquals(
                Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(corrupt)),
                report.getJSONObject("provenance")
                        .getJSONObject("detachedInput")
                        .getString("sha256"));
        assertFalse(report.toString().contains("sessionDirectory"));
        root("Clear detached signature");
        assertTrue(row(0).json().getBoolean("verified"));
        assertFalse(row(0).json().getBoolean("idsigSupplied"));
        recreate();
        assertTrue(row(0).json().getBoolean("verified"));
    }

    @Test
    public void cancelledPickerAndOversizeReplacementKeepThePreviousCapturedPair()
            throws Exception {
        root("Select / replace detached");
        select(idsig);
        String original = ReflectionHelpers.<String>getField(activity, "provenance");
        root("Select / replace detached");
        recreate();
        activity.onActivityResult(2, Activity.RESULT_CANCELED, null);
        settle();
        assertTrue(row(0).json().getBoolean("v4"));
        root("Select / replace detached");
        select(new byte[(int) ApkSigningInput.MAX_IDSIG_BYTES + 1]);
        assertTrue(status(), status().contains("exceeds"));
        assertTrue(row(0).json().getBoolean("v4"));
        JSONObject retained =
                new JSONObject(ReflectionHelpers.<String>getField(activity, "provenance"));
        assertEquals(
                new JSONObject(original).getJSONObject("detachedInput").getString("sha256"),
                retained.getJSONObject("detachedInput").getString("sha256"));
        recreate();
        assertTrue(row(0).json().getBoolean("v4"));
    }
}

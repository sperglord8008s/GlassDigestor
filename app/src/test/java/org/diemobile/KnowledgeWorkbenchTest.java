// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import static org.junit.Assert.*;
import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;

/** Real canonical rules, source-built RE2/J and resource parser; OS transport only is replaced. */
@RunWith(WorkbenchTestRunner.class)
@Config(sdk=28,shadows=KnowledgeWorkbenchTest.HostWorkerTransport.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class KnowledgeWorkbenchTest {
    @Implements(value=BinaryWorkerClient.class,isInAndroidSdk=false)
    public static final class HostWorkerTransport {
        @Implementation public String scan(File file,BooleanSupplier stop) throws Exception {
            try(InputStream source=new FileInputStream(file)) { return AndroidResourceProbe.scan(source,file.length(),stop); }
        }
    }
    private ActivityController<InvestigationActivity> controller;
    private InvestigationActivity activity;
    private Path temporary;
    private File input;
    @Before public void fixture() throws Exception {
        temporary=Files.createTempDirectory("die-knowledge-ui");input=temporary.resolve("selected.bin").toFile();Files.write(input.toPath(),new byte[]{1});
        Intent intent=new Intent(RuntimeEnvironment.getApplication(),InvestigationActivity.class).putExtra("provider","sdk-rules").putExtra("path",input.getAbsolutePath()).putExtra("name","selected.bin");
        controller=Robolectric.buildActivity(InvestigationActivity.class,intent).setup().visible();activity=controller.get();settle();
    }
    @After public void cleanup() throws Exception {
        if(controller!=null) {
            ExecutorService worker=ReflectionHelpers.getField(activity,"worker");controller.pause().stop().destroy();assertTrue(worker.awaitTermination(30,TimeUnit.SECONDS));
        }
        if(temporary!=null) try(var paths=Files.walk(temporary)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
    private ListView list() { return activity.findViewById(R.id.investigation_records); }
    private EvidenceRows.Row row(int index) { return (EvidenceRows.Row)list().getAdapter().getItem(index); }
    private Button button(String field) { return ReflectionHelpers.getField(activity,field); }
    private void settle() throws Exception {
        for(int i=0;i<8;i++) {
            ReflectionHelpers.<ExecutorService>getField(activity,"worker").submit(()->{}).get(30,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();
            if(!ReflectionHelpers.<Boolean>getField(activity,"busy")) return;
        }
        fail("Knowledge work did not settle: "+((TextView)activity.findViewById(R.id.investigation_status)).getText());
    }
    private void search(String value) throws Exception {
        ((EditText)activity.findViewById(R.id.investigation_filter)).setText(value);button("search").performClick();settle();
    }
    private void choose(String label) {
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();ListView values=dialog.getListView();
        for(int i=0;i<values.getAdapter().getCount();i++) if(values.getAdapter().getItem(i).toString().contains(label)) {
            values.performItemClick(null,i,i);dialog.dismiss();return;
        }
        fail("Missing knowledge action: "+label);
    }
    private void root(String label) throws Exception { button("views").performClick();choose(label);settle(); }
    private byte[] finishExport() throws Exception {
        Uri uri=Uri.parse("content://org.diemobile.test/knowledge-export");ByteArrayOutputStream output=new ByteArrayOutputStream();
        Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(uri,output);activity.onActivityResult(1,Activity.RESULT_OK,new Intent().setData(uri));settle();
        assertTrue(((TextView)activity.findViewById(R.id.investigation_status)).getText().toString().contains("Export saved"));return output.toByteArray();
    }
    @Test public void fullCatalogPagesGroupsLibrariesAndPreservesOriginalLocaleMetadata() throws Exception {
        assertEquals(50,list().getAdapter().getCount());assertEquals(3053,ReflectionHelpers.<EvidenceRows>getField(activity,"source").size());
        button("next").performClick();settle();assertEquals(50,row(0).index);
        search("libflutter.so");assertEquals(1,list().getAdapter().getCount());assertEquals(71,row(0).json().getInt("matcherId"));
        assertTrue(row(0).json().getJSONObject("effectiveDetails").getJSONArray("data").length()>=2);assertNull(row(0).physicalOffset);
        list().performItemClick(null,0,0);ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();settle();
        choose("Every matcher for this library UUID");settle();assertTrue(list().getAdapter().getCount()>0);
        controller.recreate();activity=controller.get();settle();button("back").performClick();settle();assertEquals(1,list().getAdapter().getCount());assertEquals(71,row(0).json().getInt("matcherId"));
        root("Unsupported regex diagnostics");assertEquals(0,list().getAdapter().getCount());
        root("DEX package names");assertTrue(list().getAdapter().getCount()>0);
        assertTrue(ReflectionHelpers.<TextView>getField(activity,"title").getText().toString().endsWith("DEX package names"));
        assertEquals(5,row(0).json().getInt("type"));
        assertEquals("DEX package evidence",row(0).json().getString("typeName"));
    }
    @Test public void completeCatalogExportAndReloadAreIndependentOfSelectedFileExpiry() throws Exception {
        search("libflutter.so");button("export").performClick();settle();
        assertTrue(input.delete());controller.recreate();activity=controller.get();settle();
        JSONObject exported=new JSONObject(new String(finishExport(),StandardCharsets.UTF_8));assertEquals(3053,exported.getJSONArray("rows").length());
        assertEquals(KnowledgeCatalog.SOURCE,exported.getJSONObject("provenance").getString("sourceRevision"));
        assertTrue(exported.getJSONArray("rows").getJSONObject(0).getJSONObject("fullOriginalRule").has("name"));
        button("reload").performClick();settle();assertEquals(1,list().getAdapter().getCount());
    }
    @Test public void actualArchiveReportUsesRegexAndDerivedDexPackagesWithoutIdentityClaims() throws Exception {
        File archive=temporary.resolve("libraries.zip").toFile();
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("lib/arm64-v8a/libjlibtorrent-1.2.3.so"));zip.write(new byte[]{1,2,3});zip.closeEntry();
        }
        KnowledgeReport.Output report=KnowledgeReport.analyze(RuntimeEnvironment.getApplication(),archive,Inspector.scan(archive,4),"libraries.zip");
        assertTrue(report.apk.toString(),report.apk.toString().contains("whole-string regex"));assertTrue(report.apk.toString().contains("jlibtorrent"));
        KnowledgeCatalog catalog=KnowledgeReport.catalog(RuntimeEnvironment.getApplication(),()->false);
        KnowledgeReport.dexLibraries(catalog,new HashSet<>(Arrays.asList("androidx.preference.PreferenceManager","androidx.preference.internal.More")),report,()->false);
        assertTrue(report.apk.toString(),report.apk.toString().contains("derived package ancestor of defined class"));
        assertTrue(report.apk.toString().contains("code identity, loading and use are not established"));
    }
    @Test public void actualAaptManifestFeedsComponentActionAndStaticLibraryMatches() throws Exception {
        File apk=new File(System.getProperty("die.fixture.dir"),"android-resources/reference.apk");assertTrue("Generate the required AAPT2 fixture",apk.isFile());
        KnowledgeCatalog catalog=KnowledgeReport.catalog(RuntimeEnvironment.getApplication(),()->false);KnowledgeReport.Output report=new KnowledgeReport.Output();
        KnowledgeReport.manifestLibraries(RuntimeEnvironment.getApplication(),apk,report,catalog,()->false);
        String text=report.apk.toString();assertTrue(text,text.contains("8 literal name records"));
        assertTrue(text,text.contains("PluginPitServiceP1")&&text.contains("com.baidu.action.SOFIRE.VIEW")&&text.contains("com.google.android.trichromelibrary"));
        assertTrue(text,text.contains("compiled android:name resource ID 0x01010003"));assertFalse(text,text.contains("inspection incomplete"));
        File snapshots=new File(RuntimeEnvironment.getApplication().getCacheDir(),"sdk-manifest-inputs");assertEquals(0,Objects.requireNonNull(snapshots.list()).length);
    }
}

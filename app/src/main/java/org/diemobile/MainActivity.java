// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;
import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private LinearLayout root,pane,tabs;
 private ExpandableHelp explanations;
 private TextView status,fileLabel;
 private Button open,rescan,export,cancel;
 private final ArrayList<Button> reportFormatButtons=new ArrayList<>();
 private Engine.Cancellation scanCancellation;
 private ProgressBar progress;
 private Uri selectedUri;
 private File retainedFile,ownedMember;
 private String selectedDisplayName;
 private Button nativeTools,archiveTools;
 private long elapsedMs;
 private boolean settingsDirty,loadingSettings;
 private AlertDialog scanOptionsDialog;
 private ScanOptions scannedOptions;
 private ScanTarget.Request scanTarget=ScanTarget.AUTOMATIC;
 private ScanTarget.Capture scannedTarget;
 private AlertDialog interpretationDialog;
 private ScanEvidence evidence;
 private JSONObject nativeFacts;
 private EngineDatabase database;
 private int scannedFlags,scannedMin;
 private LinearLayout optionPanel;
 private CheckBox deep,recursive,heuristic,aggressive,all;
 private EditText min;
 private Inspector.Result result;
 private String name="",report="",error="",guide="",apkReport="";
 private String stringFilter="";
 private boolean busy;
 private PreparedExport preparedReport;
 private boolean preparingReport,reportPickerOpen,writingReport;
 private Uri pending;
 private int page;
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private TextView txt(String s,int size){return WorkbenchUi.text(this,s,size);}
 private TextView mono(String s){TextView t=txt(s,12);t.setTypeface(Typeface.MONOSPACE);t.setTextIsSelectable(true);return t;}
 private Button btn(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setMinHeight(dp(38));b.setMinimumHeight(dp(38));b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(8),0,dp(8),0);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
 private CheckBox opt(LinearLayout l,String s,boolean b){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(12);c.setChecked(b);l.addView(c,new LinearLayout.LayoutParams(0,dp(42),1));return c;}
 @Override public void onCreate(Bundle s){
  super.onCreate(s);explanations=new ExpandableHelp(this);ui();
  if(s!=null){
   selectedUri=s.getParcelable("selected");page=s.getInt("page",0);stringFilter=s.getString("stringFilter","");if(page==2)page=1;
   if(s.getString("scanTarget")!=null)try{scanTarget=ScanTarget.Request.restore(s.getString("scanTarget"));}catch(JSONException e){status.setText("Saved interpretation expired; Automatic selected");}
   if(s.getString("preparedReport")!=null){
    try{
     preparedReport=PreparedExport.restore(scanExportDirectory(),s.getString("preparedReport"),s.getString("preparedReportName"),s.getString("preparedReportMime"),s.getLong("preparedReportSize"));
     reportPickerOpen=true;status.setText("Captured report is ready; finish choosing its destination");reportControls();return;
    }catch(IOException error){status.setText("Prepared report expired; prepare the report again");}
   }
   if(selectedUri!=null){scan(selectedUri);return;}
   if(s.getBoolean("preparingReport"))status.setText("Report preparation was interrupted; reopen the input to prepare it again");
   show();
  }
  receive(getIntent());
 }
 @Override protected void onSaveInstanceState(Bundle state){
  super.onSaveInstanceState(state);state.putParcelable("selected",selectedUri);state.putInt("page",page);state.putString("stringFilter",stringFilter);state.putBoolean("preparingReport",preparingReport);
  try{state.putString("scanTarget",scanTarget.toJson().toString());}catch(JSONException e){throw new IllegalStateException(e);}
  if(preparedReport!=null&&reportPickerOpen){state.putString("preparedReport",preparedReport.file.getAbsolutePath());state.putString("preparedReportName",preparedReport.name);state.putString("preparedReportMime",preparedReport.mime);state.putLong("preparedReportSize",preparedReport.size);}
 }
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);receive(i);}
 private void ui(){
  root=vertical();root.setBackgroundColor(0xff15191e);root.setPadding(dp(8),dp(6),dp(8),dp(4));
  if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets a=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(a.left+dp(8),a.top+dp(6),a.right+dp(8),a.bottom+dp(4));return i;});}
  setContentView(root);
  LinearLayout titlebar=new LinearLayout(this);titlebar.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=txt("GlassDigestor",18);title.setTypeface(null,Typeface.BOLD);
  title.setContentDescription(BuildConfig.PRODUCT_NAME+" "+BuildConfig.VERSION_NAME+"; tap for build details");title.setOnClickListener(v->showBuildDetails());
  titlebar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  titlebar.addView(btn("Batch",()->startActivity(new Intent(this,BatchScanActivity.class))));titlebar.addView(btn("Explore",()->startActivityForResult(new Intent(this,SystemExplorerActivity.class),4)));titlebar.addView(btn("Options",()->optionsDialog()));titlebar.addView(explanations.controls(this));root.addView(titlebar);
  fileLabel=mono("No file selected");fileLabel.setTextSize(13);fileLabel.setPadding(dp(6),dp(8),dp(6),dp(8));fileLabel.setBackgroundColor(0xff222a31);fileLabel.setMaxLines(2);
  LinearLayout fileRow=new LinearLayout(this);fileRow.setGravity(Gravity.CENTER_VERTICAL);fileRow.addView(fileLabel,new LinearLayout.LayoutParams(0,-2,1));
  open=btn("…",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,1);});open.setContentDescription("Browse for a file");fileRow.addView(open);root.addView(fileRow);
  LinearLayout actions=new LinearLayout(this);rescan=btn("Scan / Rescan",()->{if(selectedUri!=null)scan(selectedUri);});rescan.setEnabled(false);
  export=btn("Save report",this::prepareReport);
  export.setEnabled(false);actions.addView(rescan,new LinearLayout.LayoutParams(0,-2,1));cancel=btn("Cancel",()->{if(busy&&scanCancellation!=null){scanCancellation.cancel();status.setText("Cancellation requested • waiting for analysis to stop");}});cancel.setEnabled(false);actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));actions.addView(export,new LinearLayout.LayoutParams(0,-2,1));nativeTools=btn("Tools",this::showToolMenu);nativeTools.setEnabled(false);actions.addView(nativeTools,new LinearLayout.LayoutParams(0,-2,1));archiveTools=btn("Archive",()->openArchive());archiveTools.setEnabled(false);actions.addView(archiveTools,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
  ScanOptions prefs=ScanPreferences.load(this);
  optionPanel=vertical();
  LinearLayout r1=new LinearLayout(this),r2=new LinearLayout(this);
  deep=opt(r1,"Deep",(prefs.flags&1)!=0);heuristic=opt(r1,"Heuristic",(prefs.flags&2)!=0);recursive=opt(r1,"Recursive",(prefs.flags&8)!=0);
  aggressive=opt(r2,"Aggressive",(prefs.flags&32)!=0);all=opt(r2,"All types",(prefs.flags&4)!=0);
  optionPanel.addView(r1);optionPanel.addView(r2);root.addView(optionPanel);
  min=new EditText(this);min.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);min.setText(""+prefs.minimumStringLength);
  for(CheckBox c:new CheckBox[]{deep,heuristic,recursive,aggressive,all})c.setOnCheckedChangeListener((b,v)->settingsChanged());
  tabs=new LinearLayout(this);HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.addView(tabs);root.addView(hs);
  String[] labels={"Scan","File info",null,"APK / SDKs","Entropy","Strings","Hex","Reports"};for(int i=0;i<labels.length;i++){if(labels[i]==null)continue;final int p=i;Button tab=btn(labels[i],()->{page=p;show();});tab.setTag(p);tabs.addView(tab);if(i==1){Button gallery=btn("Gallerize",this::openGallerize);gallery.setTag(8);tabs.addView(gallery);}}
  View line=new View(this);line.setBackgroundColor(0xff44525e);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  ScrollView sc=WorkbenchUi.scroll(this);pane=vertical();sc.addView(pane);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(0);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));
  status=txt("Ready • "+(Engine.AVAILABLE?"CDiE loaded":"Local inspector only"),12);status.setPadding(dp(4),dp(4),dp(4),dp(2));root.addView(status);show();
 }
 private ScanOptions currentOptions(){return ScanPreferences.load(this).withBasicFlags((deep.isChecked()?1:0)|(heuristic.isChecked()?2:0)|(all.isChecked()?4:0)|(recursive.isChecked()?8:0)|(aggressive.isChecked()?32:0),minLength());}
 private int currentFlags(){return currentOptions().flags;}
 @Override protected void onResume(){super.onResume();if(explanations!=null)explanations.refresh();if(deep==null)return;loadingSettings=true;ScanOptions o=ScanPreferences.load(this);deep.setChecked((o.flags&1)!=0);heuristic.setChecked((o.flags&2)!=0);all.setChecked((o.flags&4)!=0);recursive.setChecked((o.flags&8)!=0);aggressive.setChecked((o.flags&32)!=0);min.setText(""+o.minimumStringLength);loadingSettings=false;settingsChanged();if(page==5)show();}
 private int minLength(){try{return Math.max(3,Math.min(64,Integer.parseInt(min.getText().toString())));}catch(Exception e){return 4;}}
 private void settingsChanged(){if(loadingSettings)return;ScanPreferences.save(this,currentOptions());settingsDirty=scannedOptions!=null&&(!currentOptions().sameAs(scannedOptions)||scannedTarget!=null&&!scanTarget.sameAs(scannedTarget.request));if(!busy&&selectedUri!=null&&settingsDirty){status.setText("Settings changed • press Scan / Rescan");}else if(!busy&&result!=null)finishedStatus();}
 private void optionsDialog() {
  LinearLayout form=vertical();
  form.setPadding(dp(18),dp(8),dp(18),dp(8));
  form.addView(txt("Deep: additional signature rules.\nHeuristic: infer probable matches.\nRecursive: inspect embedded files.\nAggressive: additional speculative checks.\nAll types: also scan compatible parent formats.\n\nMinimum string length (3–64; also in Strings)",14));
  EditText entry=new EditText(this);
  entry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  entry.setText(min.getText());
  form.addView(entry);
  form.addView(btn("Saved scan option profiles",()->{
   // The old minimum editor must not overwrite a subsequently applied profile.
   if(scanOptionsDialog!=null)scanOptionsDialog.dismiss();
   startActivity(new Intent(this,ScanProfilesActivity.class));
  }));
  form.addView(btn("Import Exodus database (JSON)",()->{
   Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
   startActivityForResult(intent,3);
  }));
  form.addView(btn("Inspect imported Exodus rules / metadata",this::openTrackerCatalog));
  form.addView(btn("What does a package do? / offline community knowledge",this::openPackageKnowledge));
  form.addView(btn("About GlassDigestor",()->{if(scanOptionsDialog!=null)scanOptionsDialog.dismiss();startActivity(new Intent(this,AboutActivity.class));}));
  form.addView(btn("Debug log",()->{if(scanOptionsDialog!=null)scanOptionsDialog.dismiss();startActivity(new Intent(this,DebugLogActivity.class));}));
  form.addView(btn("Build details / "+BuildConfig.VERSION_NAME,this::showBuildDetails));
  ScrollView scroll=WorkbenchUi.scroll(this);scroll.addView(form);
  scanOptionsDialog=new AlertDialog.Builder(this).setTitle("Options").setView(scroll)
   .setNegativeButton("Cancel",null).setPositiveButton("Apply",(dialog,which)->{
    min.setText(entry.getText());min.setText(""+minLength());settingsChanged();
   }).show();
 }
 private void showBuildDetails(){explain(BuildConfig.PRODUCT_NAME+" build",BuildConfig.VERSION_NAME+"\nVersion code: "+BuildConfig.VERSION_CODE+"\nVariant: "+BuildConfig.BUILD_TYPE+"\nApplication: "+BuildConfig.APPLICATION_ID+"\nSource: "+BuildConfig.SOURCE_REVISION+(BuildConfig.SOURCE_DIRTY?"\nSource has local changes or its identity is unavailable":"\nSource was clean at configuration"),"The numbered pre-release identifies a published checkpoint. Debug builds also identify the Git revision; .dirty means local edits were present. Android uses the numeric version code when comparing updates. An update also requires the same signing certificate. GlassDigestor continues DiE Android, with Detect It Easy/CDiE as a credited core engine. A version number does not establish handset verification or complete desktop parity.");}
 private void chooseInterpretation(){
  if(busy||result==null)return;
  final ScanTarget.Capture captured=scannedTarget;final String identity=result.sha256;
  final ArrayList<ScanTarget.Choice> choices=new ArrayList<>();
  if(captured!=null&&captured.catalog!=null&&captured.catalog.ok)choices.addAll(captured.catalog.choices);
  else choices.add(new ScanTarget.Choice(0,"Automatic"));
  String[] labels=new String[choices.size()];for(int i=0;i<labels.length;i++)labels[i]=choices.get(i).toString();
  interpretationDialog=new AlertDialog.Builder(this).setTitle("Next scan interpretation")
   .setItems(labels,(dialog,which)->{
    if(busy||scannedTarget!=captured||result==null||!result.sha256.equals(identity)){status.setText("Format choices expired for this input");return;}
    scanTarget=choices.get(which).request();settingsChanged();show();
   }).setNegativeButton("Cancel",null).show();
 }
 private void finishedStatus(){status.setText(String.format(Locale.ROOT,"%s • %d ms%s",error.isEmpty()?"Scan complete":"Local scan / engine error",elapsedMs,settingsDirty?" • settings changed":""));}
 private void receive(Intent i){if(i==null)return;Uri u=null;if(Intent.ACTION_VIEW.equals(i.getAction()))u=i.getData();else if(Intent.ACTION_SEND.equals(i.getAction()))u=i.getParcelableExtra(Intent.EXTRA_STREAM);if(u!=null&&"content".equals(u.getScheme()))scan(u);}
 @Override protected void onActivityResult(int req,int code,Intent data){super.onActivityResult(req,code,data);if(req==2){finishReport(code,data);return;}if((req==4||req==5)&&code==RESULT_OK&&data!=null){String p=data.getStringExtra("path");if(p!=null){if(req==5){if(ownedMember!=null)ownedMember.delete();ownedMember=new File(p);selectedDisplayName=data.getStringExtra("display");}scan(Uri.fromFile(new File(p)));}return;}if(code==RESULT_OK&&data!=null&&data.getData()!=null){if(req==1){try{if((data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}scan(data.getData());}if(req==3)importExodus(data.getData());}}
 private void importExodus(Uri u){
  worker.execute(()->{String msg;try(InputStream in=getContentResolver().openInputStream(u)){
   if(in==null)throw new IOException("No input");
   TrackerCatalog catalog=TrackerCatalog.install(in,new File(getFilesDir(),"exodus-trackers.json"),this::isDestroyed);
   msg="Exodus snapshot imported: "+catalog.rules.size()+" records; SHA-256 "+catalog.sha256+". Inspect rules for unsupported syntax; rescan to apply.";
  }catch(Exception e){msg="Import failed; previous snapshot retained: "+e.getMessage();}final String text=msg;runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_LONG).show();});});
 }
 private void openSelectedPackageKnowledge() {
  if (busy || retainedFile == null || result == null) return;
  Intent intent = new Intent(this, InvestigationActivity.class)
    .putExtra("provider", "package-set")
    .putExtra("path", retainedFile.getAbsolutePath())
    .putExtra("name", name)
    .putExtra("sha256", result.sha256)
    .putExtra("sourceSize", result.size)
    .putExtra("openCapturedPackageKnowledge", true);
  startActivity(intent);
 }
 private void openPackageKnowledge(){startActivity(new Intent(this,InvestigationActivity.class).putExtra("provider","package-knowledge").putExtra("path",new File(getCacheDir(),"package-knowledge-catalog").getAbsolutePath()).putExtra("name","Offline community package knowledge"));}
 private void openTrackerCatalog(){startActivity(new Intent(this,InvestigationActivity.class).putExtra("provider","tracker-rules").putExtra("path",new File(getFilesDir(),"exodus-trackers.json").getAbsolutePath()).putExtra("name","Imported Exodus tracker definitions"));}
 private String fileName(Uri u){if("file".equals(u.getScheme())){File f=new File(u.getPath());return ownedMember!=null&&f.equals(ownedMember)&&selectedDisplayName!=null?selectedDisplayName:f.getName();}try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getString(0);}catch(Exception ignored){}return "input.bin";}
 private void scan(Uri u){
  if(busy){pending=u;return;}if(!java.util.Objects.equals(selectedUri,u))scanTarget=ScanTarget.AUTOMATIC;if(interpretationDialog!=null)interpretationDialog.dismiss();if(ownedMember!=null&&!("file".equals(u.getScheme())&&ownedMember.equals(new File(u.getPath())))){ownedMember.delete();ownedMember=null;selectedDisplayName=null;}if(retainedFile!=null){retainedFile.delete();retainedFile=null;}selectedUri=u;busy=true;scanCancellation=new Engine.Cancellation();final Engine.Cancellation cancellation=scanCancellation;cancel.setEnabled(true);result=null;evidence=null;nativeFacts=null;database=null;report="";error="";guide="";apkReport="";status.setText("Scanning locally…");fileLabel.setText(u.getLastPathSegment()==null?"Selected file":u.getLastPathSegment());open.setEnabled(false);rescan.setEnabled(false);export.setEnabled(false);nativeTools.setEnabled(false);archiveTools.setEnabled(false);progress.setIndeterminate(true);show();
  final ScanTarget.Request requestedTarget=scanTarget;final ScanOptions options=currentOptions();int flags=options.flags;final int minLen=options.minimumStringLength;final long started=SystemClock.elapsedRealtime();
  DebugJournal.record(this,"scan_started","engine=cdie status=running");
  worker.execute(()->{
   File f=null;Inspector.Result r=null;String n="",rep="",err="",fail="",g="",a="";ScanEvidence ev=null;JSONObject facts=null;EngineDatabase databases=null;ScanTarget.Capture targetCapture=null;
   try{
    n=fileName(u);String ext=".bin";int dot=n.lastIndexOf('.');if(dot>=0&&n.substring(dot).matches("\\.[a-zA-Z0-9]{1,12}"))ext=n.substring(dot);
    f=File.createTempFile("scan-",ext,getCacheDir());
    try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(f)){if(in==null)throw new IOException("No input data");byte[] b=new byte[65536];int count;long total=0;while((count=in.read(b))!=-1){if(cancellation.isRequested())throw new InterruptedIOException("Scan cancelled during input acquisition");total+=count;if(total>1024L*1024*1024)throw new IOException("Alpha limit: 1 GiB");out.write(b,0,count);}}
    r=Inspector.scan(f,minLen,cancellation::isRequested);
    targetCapture=ScanTarget.Capture.resolve(requestedTarget,null,r.sha256,r.size,"");
    try{if(cancellation.isRequested())throw new InterruptedIOException("Scan cancelled");KnowledgeReport.Output k=KnowledgeReport.analyze(this,f,r,n,cancellation::isRequested);g=k.guide.toString();a=k.apk.toString();}catch(Exception e){g="Explanatory report unavailable: "+e.getMessage();}
    if(Engine.AVAILABLE){try{
     databases=EngineDatabase.prepare(this);
     ScanTarget.Catalog catalog;
     if(cancellation.isRequested())throw new InterruptedIOException("Scan cancelled before format resolution");
     try{catalog=ScanTarget.Catalog.parse(Engine.desktop(f.getAbsolutePath(),databases.main.getAbsolutePath(),databases.custom.getAbsolutePath(),0,flags,0,options.includeTags,options.excludeTags,"",""));}
     catch(Exception e){catalog=ScanTarget.Catalog.unavailable("Compatible-format catalog failed: "+e);}
     targetCapture=ScanTarget.Capture.resolve(requestedTarget,catalog,r.sha256,r.size,databases.engineRevision);
     if(targetCapture.nativeTypeId==null)throw new IOException(targetCapture.error);
     final int selectedType=targetCapture.nativeTypeId;
     if(cancellation.isRequested())throw new InterruptedIOException("Scan cancelled during format resolution");
     facts=new JSONObject(Engine.desktop(f.getAbsolutePath(),databases.main.getAbsolutePath(),databases.custom.getAbsolutePath(),5,flags,selectedType,options.includeTags,options.excludeTags,"",""));
     rep=Engine.desktop(f.getAbsolutePath(),databases.main.getAbsolutePath(),databases.custom.getAbsolutePath(),1,flags,selectedType,options.includeTags,options.excludeTags,"","",cancellation);
     ev=ScanEvidence.parse(rep);if(!ev.ok)err=ev.error;
    }catch(Exception|LinkageError e){DebugJournal.recordFailure(this,"scan_engine_failed","engine=cdie",e);err=e.toString();}}
    else err="CDiE not bundled; compiler/packer detection unavailable.";
   }catch(Exception e){DebugJournal.recordFailure(this,"scan_failed","engine=cdie",e);fail=e.toString();}finally{if(r==null&&f!=null){f.delete();f=null;}}
   DebugJournal.record(this,"scan_finished","engine=cdie status="+(fail.isEmpty()?(err.isEmpty()?"complete":"partial"):"failed")+" elapsedMs="+(SystemClock.elapsedRealtime()-started)+" inputBytes="+(r==null?0:r.size));
   final ScanEvidence finalEvidence=ev;final JSONObject finalFacts=facts;final EngineDatabase finalDatabases=databases;final ScanTarget.Capture finalTarget=targetCapture;
   final File inputCopy=f;final Inspector.Result fr=r;final String fn=n,fp=rep,fe=err,ff=fail,fg=g,fa=a;
   runOnUiThread(()->{if(isDestroyed()){if(inputCopy!=null)inputCopy.delete();return;}retainedFile=inputCopy;nativeTools.setEnabled(retainedFile!=null);archiveTools.setEnabled(retainedFile!=null);result=fr;evidence=finalEvidence;nativeFacts=finalFacts;database=finalDatabases;scannedOptions=options;scannedTarget=finalTarget;name=fn;report=fp;error=fe;guide=fg;apkReport=fa;busy=false;cancel.setEnabled(false);elapsedMs=SystemClock.elapsedRealtime()-started;scannedFlags=flags;scannedMin=minLen;settingsDirty=!currentOptions().sameAs(options)||!scanTarget.sameAs(requestedTarget);fileLabel.setText(fn.isEmpty()?"Selected file":fn);open.setEnabled(true);rescan.setEnabled(selectedUri!=null);reportControls();progress.setIndeterminate(false);progress.setProgress(result!=null?100:0);if(ff.isEmpty())finishedStatus();else status.setText(ff);show();if(pending!=null){Uri next=pending;pending=null;scan(next);}});
  });
 }
 private void openDesktopScan(){if(retainedFile==null||!Engine.AVAILABLE)return;Intent i=new Intent(this,DesktopScanActivity.class);i.putExtra("path",retainedFile.getAbsolutePath());i.putExtra("name",name);startActivity(i);}
 private void openGallerize(){if(busy||retainedFile==null||result==null)return;startActivity(new Intent(this,GallerizeActivity.class).putExtra("path",retainedFile.getAbsolutePath()).putExtra("name",name).putExtra("sha256",result.sha256).putExtra("sourceSize",result.size));}
    private Intent archiveRequest() {
        if (retainedFile == null) return null;
        Intent i = new Intent(this, NativeArchiveExplorerActivity.class);
        i.putExtra("path", retainedFile.getAbsolutePath());
        i.putExtra("name", name);
        if (result != null) {
            i.putExtra("sha256", result.sha256);
            i.putExtra("sourceSize", result.size);
        }
        return i;
    }
    private void openArchive() {
        Intent request = archiveRequest();
        if (request != null) startActivityForResult(request, 5);
    }
    private String archiveIdentification() {
        if (result == null) return null;
        String lower = result.type.toLowerCase(Locale.ROOT);
        if (lower.contains("zip")
                || lower.contains("archive")
                || lower.contains("compressed stream")) return result.type;
        if (evidence != null && evidence.ok)
            for (ScanEvidence.Row row : evidence.rows)
                if ("Archive".equalsIgnoreCase(row.category))
                    return row.name + " • CDiE indication";
        return null;
    }
    private void archiveSummary() {
        String identified = archiveIdentification();
        Intent request = archiveRequest();
        if (identified == null || request == null) return;
        heading("Archive / compressed contents");
        TextView format = txt(WorkbenchUi.visible(WorkbenchUi.preview(identified, 180)), 18);
        format.setTextIsSelectable(true);
        pane.addView(format);
        Button browse =
                btn(
                        "Browse contents / decompress / save files",
                        () -> startActivityForResult(request, 5));
        browse.setTag("main-open-archive-contents");
        pane.addView(browse);
        pane.addView(
                WorkbenchUi.prose(
                        this,
                        "Open the captured file with the content-based archive reader. A compressed"
                            + " stream can contain one decoded file rather than a folder. Select a"
                            + " file to preview or save it; original information remains available."
                            + " Embedded findings need their own observed scope in Gallerize."));
    }
 private void openNativeTools(){if(retainedFile==null)return;Intent i=new Intent(this,NativeToolsActivity.class);i.putExtra("path",retainedFile.getAbsolutePath());i.putExtra("name",name);if(result!=null){i.putExtra("sha256",result.sha256);i.putExtra("sourceSize",result.size);}startActivity(i);}
 private void openInvestigation(String provider){if(retainedFile==null)return;Intent i=new Intent(this,InvestigationActivity.class).putExtra("provider",provider).putExtra("path",retainedFile.getAbsolutePath()).putExtra("name",name);if(result!=null){i.putExtra("sha256",result.sha256);i.putExtra("sourceSize",result.size);}startActivity(i);}
    private void openStrings() {
        if (busy || retainedFile == null || result == null) return;
        startActivity(
                new Intent(this, InvestigationActivity.class)
                        .putExtra("provider", "strings")
                        .putExtra("path", retainedFile.getAbsolutePath())
                        .putExtra("name", name)
                        .putExtra("sha256", result.sha256)
                        .putExtra("sourceSize", result.size)
                        .putExtra("stringMinimum", minLength())
                        .putExtra("stringInitialView", "all")
                        .putExtra("stringFilter", stringFilter));
    }
    private void showStrings() {
        heading("Strings");
        pane.addView(txt("Next analysis minimum (3–64 Unicode code points)", 13));
        LinearLayout settings = new LinearLayout(this);
        EditText entry = new EditText(this);
        entry.setTag("strings-minimum");
        entry.setContentDescription("Minimum string length for next analysis");
        entry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        entry.setSingleLine(true);
        entry.setText(min.getText());
        settings.addView(entry, new LinearLayout.LayoutParams(dp(72), -2));
        Button apply = btn("Apply minimum", () -> {
            int value;
            try { value = Integer.parseInt(entry.getText().toString()); }
            catch (NumberFormatException error) { value = -1; }
            if (value < 3 || value > 64) {
                entry.setError("Enter a whole number from 3 to 64");
                return;
            }
            min.setText(Integer.toString(value));
            settingsChanged();
            show();
        });
        apply.setTag("strings-apply-minimum");
        settings.addView(apply, new LinearLayout.LayoutParams(0, -2, 1));
        pane.addView(settings);
        if (result == null) {
            pane.addView(txt(busy ? "Scan running; string settings apply to the next analysis."
                    : "Open or share a file to browse its strings.", 14));
            return;
        }
        int capturedMinimum = scannedOptions == null ? scannedMin : scannedOptions.minimumStringLength;
        pane.addView(txt("ASCII preview minimum: " + capturedMinimum
                + ". Scan / Rescan refreshes the preview.", 12));
        EditText query = new EditText(this);
        query.setTag("strings-filter");
        query.setSingleLine(true);
        query.setHint("Filter string text (case-insensitive)");
        query.setContentDescription("Filter strings; also used when opening the complete list");
        query.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1024)});
        query.setText(stringFilter);
        pane.addView(query);
        Button browse = btn("Browse all strings / categories", this::openStrings);
        browse.setTag("strings-browse");
        browse.setEnabled(!busy && retainedFile != null);
        pane.addView(browse);
        pane.addView(txt("Complete paged UTF-8 / anchored UTF-16 candidates, categories, offsets,"
                + " classification details and exports. The filter above carries into that list.", 13));
        TextView count = txt("", 13);
        count.setTag("strings-preview-count");
        pane.addView(count);
        pane.addView(txt("Quick ASCII preview only: up to about 200k characters and 4096 characters"
                + " per run. The complete list has separate decoding limits.", 12));
        TextView lines = mono("");
        lines.setTag("strings-preview");
        pane.addView(lines);
        final String[] captured = result.strings.split("\n");
        Runnable render = () -> {
            String term = stringFilter.toLowerCase(Locale.ROOT);
            StringBuilder filtered = new StringBuilder();
            int total = 0, matches = 0;
            for (String line : captured) {
                if (line.isEmpty()) continue;
                total++;
                if (line.toLowerCase(Locale.ROOT).contains(term)) {
                    filtered.append(line).append('\n');
                    matches++;
                }
            }
            count.setText("Quick ASCII preview · " + matches + " of " + total + " lines");
            lines.setText(matches == 0 ? "No matching preview strings; browse the complete list"
                    + " to search other encodings." : filtered.toString());
        };
        query.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                stringFilter = value.toString();
                render.run();
            }
            public void afterTextChanged(android.text.Editable value) {}
        });
        render.run();
    }
 private void openNativeToolAt(int tool,long offset){if(retainedFile==null)return;Intent i=new Intent(this,NativeToolsActivity.class);i.putExtra("path",retainedFile.getAbsolutePath());i.putExtra("name",name);i.putExtra("tool",tool);i.putExtra("offset",offset);if(result!=null){i.putExtra("sha256",result.sha256);i.putExtra("sourceSize",result.size);}startActivity(i);}
 private void showNativeFacts(){
  if(evidence==null||!evidence.ok)return;
  String machine=evidence.architecture.isEmpty()?"Unknown":evidence.architecture;
  if(!evidence.mode.isEmpty())machine+=" / "+evidence.mode;
  if(!evidence.endianness.isEmpty())machine+=" / "+evidence.endianness;
  field("Architecture",machine,"Parser-reported architecture, execution mode and byte order. These describe the file, not this Android device or a runtime process. An empty parser field does not establish a machine architecture.");
  if(nativeFacts!=null&&nativeFacts.optBoolean("ok")){
   String formatPage=nativeFacts.optString("page");
   if(formatPage.equals("PE")||formatPage.equals("ELF")||formatPage.equals("Mach-O")){
    field("Image base",nativeFacts.optString("base"),"Preferred loader address before relocation or ASLR. A virtual address is not a physical file offset.");
    field("Entry VA",nativeFacts.optString("entry"),"Format-declared entry point in the virtual address domain. Loader initialization and other entry paths may run first.");
   }
   long entry=nativeFacts.optLong("entryOffset",-1);
   if((nativeFacts.optInt("flags")&(1<<14))!=0&&entry>=0){field("Entry offset",String.format(Locale.ROOT,"0x%X",entry),"Physical byte offset mapped by the native parser (GUI_FACTS_ENTRY_OFFSET). Inspect bytes and instruction decoding to corroborate the entry point.");pane.addView(btn("Hex at entry point",()->openNativeToolAt(9,entry)));pane.addView(btn("Disassemble entry point",()->openNativeToolAt(6,entry)));}
  }
  if(database!=null)field("Database",database.signatureRevision,"Exact bundled Detect-It-Easy signature database Git revision. The engine revision and options are preserved in report exports.");
 }
 private String pretty(String s){try{return new JSONObject(s).toString(2);}catch(JSONException e){try{return new JSONArray(s).toString(2);}catch(JSONException ignored){return s;}}}
 private void show(){
  if(page==2)page=1;explanations.clearBindings();pane.removeAllViews();reportFormatButtons.clear();for(int i=0;i<tabs.getChildCount();i++){Button b=(Button)tabs.getChildAt(i);boolean selected=((Integer)b.getTag())==page;b.setEnabled(!selected);b.setTextColor(selected?0xff80cbc4:0xffe5eaf0);}
  if(page==5){showStrings();return;}
  if(result==null){pane.addView(txt(busy?"Scan running on worker thread.":"Open or share a file to inspect it. No root or internet access required.",16));return;}
  switch(page){
   case 0:archiveSummary();pane.addView(btn("Next scan interpretation: "+scanTarget.label()+" • choose…",this::chooseInterpretation));if(scannedTarget!=null)field("Requested interpretation",scannedTarget.request.label()+" • backend selector "+(scannedTarget.nativeTypeId==null?"unresolved":scannedTarget.nativeTypeId),"Captured selection for the displayed scan. Explicit choices are compatible native scan passes, not validation of every format structure. Draft selection changes apply only after Rescan.");field("Desktop parser view",evidence!=null&&evidence.ok?evidence.type:result.type,"The desktop parsing context can be Binary even when signatures identify an archive or compressed stream. Byte-based header identification and archive actions are shown separately. Recognition does not validate every structure.");showNativeFacts();field("Size",String.format(Locale.ROOT,"%,d bytes (0x%X)",result.size,result.size),"Exact input length in decimal and hexadecimal. Offsets refer to byte positions within that length.");field("Entropy",String.format(Locale.ROOT,"%.4f / 8",result.entropy),"Shannon entropy of the observed byte histogram, from 0 to 8 bits per byte. Compression, encryption and ordinary media can all produce high entropy; this is not a packing or malware verdict.");heading("Detection results • desktop CDiE");pane.addView(btn("Desktop scan laboratory / signature editor",()->openDesktopScan()));showDetections();heading("Content identity");field("SHA-256",result.sha256,"A 256-bit digest of the exact bytes analyzed. Compare against a trusted digest to identify matching content; it is not a publisher signature.");field("Flags",String.format(Locale.ROOT,"0x%04X",scannedFlags),"Actual desktop-backend flags captured when the scan began. Laboratory options and tag filters apply here too. The export preserves their names and values.");field("Time",elapsedMs+" ms","Elapsed wall-clock time including local copying, file inspection, knowledge lookup and signature scanning.");heading("Technical interpretation");pane.addView(btn("File structure and interpretation",()->{page=1;show();}));if(result.type.contains("ZIP")||result.type.contains("DEX"))pane.addView(btn("APK / DEX information and SDK details",()->{page=3;show();}));break;
   case 1:showFileInfo();break;
   case 3: {
    heading("APK / SDKs • static information");
    Button community = btn("What does this APK do? / captured package knowledge", this::openSelectedPackageKnowledge);
    community.setTag("apk-community-lookup");
    pane.addView(community);
    pane.addView(btn("What does a package do? / sourced community catalog", this::openPackageKnowledge));
    pane.addView(btn("APK signing / rotation / certificates / detached v4", () -> openInvestigation("apk-signing")));
    pane.addView(btn("Package / split declarations / captured relationships", () -> openInvestigation("package-set")));
    pane.addView(btn("Imported Exodus rules / metadata / diagnostics", this::openTrackerCatalog));
    pane.addView(btn("Canonical SDK / library rules / regex / source metadata", () -> openInvestigation("sdk-rules")));
    if (result.type.contains("ZIP") || result.type.contains("DEX"))
     pane.addView(btn("DEX tables / bytecode / references / smali", () -> openInvestigation("dex")));
    readableReport(apkReport);
    break;
   }
   case 4:pane.addView(txt(String.format(Locale.ROOT,"Whole-file entropy: %.3f / 8",result.entropy),17));TextView detail=txt("Touch graph to inspect a block.",14);pane.addView(new Graph(result,detail),new LinearLayout.LayoutParams(-1,dp(230)));pane.addView(detail);pane.addView(txt("High entropy can indicate compression, encryption or randomness; not proof of packing.",14));break;
   case 6:pane.addView(txt("First 4096 bytes • hex / ASCII",14));HorizontalScrollView hs=new HorizontalScrollView(this);hs.addView(mono(result.hex));pane.addView(hs);break;
   case 7:heading("Captured report exports / engine log");addReportFormat("Export complete XML report",ScanReportFormats.Format.XML);addReportFormat("Export complete CSV report",ScanReportFormats.Format.CSV);addReportFormat("Export complete TSV report",ScanReportFormats.Format.TSV);pane.addView(txt("Complete captures, independent of page/filter. XML and tabular string values use explicit JSON encoding to preserve every UTF-16 unit; tabular metadata retains diagnostics and unknown fields.",13));pane.addView(mono(error.isEmpty()?pretty(report):error+"\n"+pretty(report)));break;
  }
 }
 private void heading(String title){pane.addView(WorkbenchUi.section(this,title),new LinearLayout.LayoutParams(-1,-2));}
 private void field(String key,String value){field(key,value,null);}
 private void field(String key,String value,String explanation){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);row.setPadding(dp(6),dp(5),dp(6),dp(5));
  LinearLayout facts=vertical();TextView k=txt(key,12);k.setTypeface(null,Typeface.BOLD);k.setTextColor(getColor(R.color.workbench_secondary));facts.addView(k);
  TextView v=txt(WorkbenchUi.visible(value),14);v.setTextIsSelectable(true);if(key.matches("(?i).*(?:sha|md5|crc|offset|path|identity).*"))v.setTypeface(Typeface.MONOSPACE);facts.addView(v);row.addView(facts,new LinearLayout.LayoutParams(0,-2,1));
  TextView why=null;Button toggle=null;
  if(explanation!=null){toggle=btn("Explain",()->{});row.addView(toggle,new LinearLayout.LayoutParams(dp(40),-2));why=txt(explanation,13);why.setTextIsSelectable(true);why.setPadding(dp(8),dp(4),dp(8),dp(8));why.setTag("explanation:main."+page+"."+key);}
  pane.addView(row);if(why!=null){pane.addView(why);explanations.bind("main."+page+"."+key,"Explain "+key,toggle,why,true,false);}rule(pane);
 }
    private void explain(String title, String value, String explanation) {
        LinearLayout body = vertical();
        body.setPadding(dp(18), dp(12), dp(18), dp(12));
        body.addView(mono(value));
        body.addView(explanations.controls(this));
        explanations.paragraph(
                this,
                body,
                "dialog." + title,
                "Definition / interpretation",
                explanation,
                true,
                false);
        ScrollView sc = WorkbenchUi.scroll(this);
        sc.addView(body);
        AlertDialog.Builder dialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setView(sc)
                        .setPositiveButton("Close", null);
        if ("Archive".equalsIgnoreCase(title)) {
            Intent request = archiveRequest();
            if (request != null) {
                body.addView(
                        WorkbenchUi.prose(
                                this,
                                "Browse the complete captured input. This detection does not supply"
                                    + " a verified physical extent for an embedded archive."));
                dialog.setNeutralButton(
                        "Browse / extract", (d, which) -> startActivityForResult(request, 5));
            }
        }
        dialog.show();
    }
 private void showFileInfo(){
  heading("File type");TextView type=txt(result.type,21);type.setTypeface(null,Typeface.BOLD);type.setPadding(dp(6),dp(8),dp(6),dp(8));pane.addView(type);
  String lower=result.type.toLowerCase(Locale.ROOT);
  boolean container=lower.contains("zip")||lower.contains("archive")||lower.contains("container")||lower.contains("compressed stream");
  field("Layout",container?"Container / compressed content • inspect its contents":lower.startsWith("unknown")?"Unclassified bytes • whole-blob layout not yet determined":"Primary header format identified • embedded layout not yet determined", "The displayed type comes from bytes, not the filename. Header recognition does not establish homogeneous contents or complete validity. Whole-blob carving and archive inspection retain their separate source information and limits.");
  field("Name",name);field("Size",String.format(Locale.ROOT,"%,d bytes (0x%X)",result.size,result.size));
  field("Entropy",String.format(Locale.ROOT,"%.4f bits/byte",result.entropy),"Whole-input Shannon entropy over byte counts. Regions, raw byte views and alternative interpretations remain available; compression, encryption and ordinary media can all have high entropy.");
  field("SHA-256",result.sha256,"Digest of the exact acquired bytes. Captured reports and exported resources retain their own complete source identities.");
  pane.addView(btn("Gallerize / content gallery / byte and entropy grids",this::openGallerize));
  heading("Explore content and details");
  for(ToolCatalog.Group group:ToolCatalog.GROUPS){
   Button toggle=btn(group.label,()->{});toggle.setTag("tool-group-toggle:"+group.id);LinearLayout body=vertical();body.setTag("tool-group:"+group.id);
   for(ToolCatalog.Tool tool:group.tools)body.addView(btn(tool.label,()->launchTool(tool)));
   pane.addView(toggle);pane.addView(body);
   boolean preferred=group.id.equals("general")||group.id.equals("contents")&&container||group.id.equals("media")&&(lower.contains("image")||lower.contains("audio")||lower.contains("media")||lower.equals("png"));
   explanations.bind("tools."+group.id,group.label,toggle,body,false,preferred);
  }
  heading("Format mechanisms and observations");
  int index=0;for(ReferenceSections.Section section:ReferenceSections.parse(guide))explanations.paragraph(this,pane,"reference."+index++,section.title,section.body,!section.observed,section.observed);
 }
 private void showToolMenu(){
  if(busy||retainedFile==null||result==null)return;
  final File captured=retainedFile;final String sha=result.sha256;
  String[] labels=new String[ToolCatalog.GROUPS.size()];for(int i=0;i<labels.length;i++)labels[i]=ToolCatalog.GROUPS.get(i).label;
  new AlertDialog.Builder(this).setTitle("Investigation tools").setItems(labels,(dialog,which)->{
   if(busy||retainedFile!=captured||result==null||!result.sha256.equals(sha)){status.setText("Tool choices expired for this input");return;}
   ToolCatalog.Group group=ToolCatalog.GROUPS.get(which);String[] tools=new String[group.tools.size()];for(int i=0;i<tools.length;i++)tools[i]=group.tools.get(i).label;
   new AlertDialog.Builder(this).setTitle(group.label).setItems(tools,(d,index)->{
    if(busy||retainedFile!=captured||result==null||!result.sha256.equals(sha)){status.setText("Tool choices expired for this input");return;}launchTool(group.tools.get(index));
   }).setNegativeButton("Back",(d,w)->showToolMenu()).show();
  }).setNegativeButton("Close",null).show();
 }
 private void launchTool(ToolCatalog.Tool tool){
  if(busy||retainedFile==null)return;
  if(tool.nativeTool>=0){openNativeToolAt(tool.nativeTool,-1);return;}
  if(tool.provider!=null){openInvestigation(tool.provider);return;}
  switch(tool.id){case "gallerize":openGallerize();break;case "archive":openArchive();break;case "laboratory":openDesktopScan();break;
   case "profiles":startActivity(new Intent(this,ScanProfilesActivity.class));break;
   case "batch":startActivity(new Intent(this,BatchScanActivity.class));break;case "native-all":openNativeTools();break;
   default:throw new IllegalArgumentException("Unknown tool action: "+tool.id);}
 }
 private void readableReport(String content){
  // Preserve every line of the report; paragraphs use proportional text rather than one giant monospace block.
  String[] blocks=content.split("\\n\\s*\\n");for(String block:blocks){String trimmed=block.trim();if(trimmed.isEmpty())continue;TextView t=txt(trimmed,13);t.setTextIsSelectable(true);t.setPadding(dp(6),dp(5),dp(6),dp(5));pane.addView(t);}
 }
 private void rule(LinearLayout l){View rule=new View(this);rule.setBackgroundColor(0xff35414b);l.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));}
 private void showDetections(){
  if(!error.isEmpty()){pane.addView(txt(error,13));return;}
  if(evidence==null){pane.addView(txt("No desktop scan information available.",13));return;}
  if(!evidence.log.isEmpty()){heading("Engine diagnostics");pane.addView(mono(evidence.log));}
  pane.addView(txt(evidence.rows.size()+" tree rows • "+evidence.signatureCount+" loaded rules. Tap a record for details and interpretation.",12));
  EditText search=new EditText(this);search.setSingleLine();search.setHint("Search detections, categories or rule paths");pane.addView(search);
  LinearLayout rows=vertical();pane.addView(rows);renderEvidence(rows,"",0);
  search.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int a,int c,int d){}
   public void onTextChanged(CharSequence s,int a,int b,int c){renderEvidence(rows,s.toString(),0);}
   public void afterTextChanged(android.text.Editable e){}
  });
 }
 private void renderEvidence(LinearLayout target,String query,int page){
  target.removeAllViews();int total=0;
  String q=query.toLowerCase(Locale.ROOT);
  for(ScanEvidence.Row row:evidence.rows)if((row.text+"\n"+row.category+"\n"+row.signature).toLowerCase(Locale.ROOT).contains(q))total++;
  int pages=Math.max(1,(total+49)/50);target.addView(txt(total+" matching rows • page "+(page+1)+" / "+pages,12));
  for(ScanEvidence.Row row:evidence.page(query,page,50)){
   String indent="";for(int d=0;d<Math.min(row.depth,4);d++)indent+="  ";
   String label=indent+row.text+(row.signature.isEmpty()?"":"\n"+row.signature);
   Button record=btn(label,()->explain(row.category.isEmpty()?"Detection details":row.category,
    row.text+"\nCategory: "+row.category+"\nName: "+row.name+"\nRule source: "+(row.signature.isEmpty()?"No script source attached to this group":row.signature)+"\nTree row: "+row.index+"; parent: "+row.parent,
    DetectionReference.describe(row.category)+"\n\nThe path is the exact upstream rule provenance, not a byte-match location. Use the laboratory to read or test the source. Group offsets may describe decompressed archive members rather than bytes in this input; do not treat them as physical offsets without checking their address basis."));
   record.setTag("detection-evidence-"+row.index);record.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);record.setPadding(dp(8),dp(8),dp(8),dp(8));target.addView(record,new LinearLayout.LayoutParams(-1,-2));
  }
  LinearLayout navigation=new LinearLayout(this);
  Button previous=btn("Previous 50",()->renderEvidence(target,query,page-1));previous.setEnabled(page>0);navigation.addView(previous);
  Button next=btn("Next 50",()->renderEvidence(target,query,page+1));next.setEnabled(page+1<pages);navigation.addView(next);target.addView(navigation);
 }
 private File scanExportDirectory(){return new File(getCacheDir(),"scan-exports");}
 private void reportControls(){boolean available=!busy&&result!=null&&scannedOptions!=null&&!preparingReport&&!reportPickerOpen&&!writingReport;export.setEnabled(available);for(Button button:reportFormatButtons)button.setEnabled(available);}
 private void addReportFormat(String label,ScanReportFormats.Format format){Button button=btn(label,()->prepareReport(format));reportFormatButtons.add(button);button.setEnabled(export.isEnabled());pane.addView(button);}
 private void prepareReport(){prepareReport(ScanReportFormats.Format.JSON);}
 private void prepareReport(ScanReportFormats.Format format){
  if(result==null||scannedOptions==null||busy||preparingReport||reportPickerOpen||writingReport)return;
  final Inspector.Result source=result;final ScanOptions options=scannedOptions;final ScanTarget.Capture capturedTarget=scannedTarget;final ScanEvidence scan=evidence;
  final JSONObject facts=nativeFacts;final EngineDatabase db=database;final String inputName=name,explanation=guide,apk=apkReport,engineError=error;
  final long duration=elapsedMs;
  preparingReport=true;reportControls();status.setText("Preparing the captured scan report…");
  worker.execute(()->{
   PreparedExport staged=null;
   try{
    JSONObject output=ScanReport.create(inputName,source,options,scan,facts,db==null?"":db.engineRevision,db==null?"":db.signatureRevision,db==null?"":db.customDigest,duration,explanation,apk,engineError);
    if(capturedTarget!=null)capturedTarget.attach(output);
    staged=PreparedExport.stream(scanExportDirectory(),"die-android-report-"+source.sha256.substring(0,12)+"."+format.extension,format.mime,writer->ScanReportFormats.write(writer,output,format,this::isDestroyed),this::isDestroyed);
    final PreparedExport ready=staged;
    runOnUiThread(()->{
     if(isDestroyed()){ready.discard();return;}
     preparingReport=false;preparedReport=ready;reportPickerOpen=true;reportControls();
     Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(ready.mime).putExtra(Intent.EXTRA_TITLE,ready.name);
     try{startActivityForResult(intent,2);if(!busy)status.setText("Choose a destination for the captured report • "+source.sha256.substring(0,12));}
     catch(RuntimeException error){ready.discard();preparedReport=null;reportPickerOpen=false;reportControls();status.setText("Cannot open the report picker: "+error.getMessage());}
    });
   }catch(Exception error){
    if(staged!=null)staged.discard();
    runOnUiThread(()->{if(!isDestroyed()){preparingReport=false;reportControls();if(!busy)status.setText("Report preparation failed: "+error.getMessage());}});
   }
  });
 }
 private void finishReport(int code,Intent data){
  reportPickerOpen=false;final PreparedExport captured=preparedReport;preparedReport=null;
  if(captured==null){reportControls();status.setText("Prepared report expired; prepare it again");return;}
  if(code!=RESULT_OK||data==null||data.getData()==null){captured.discard();reportControls();if(!busy)status.setText("Report export cancelled");return;}
  final Uri uri=data.getData();writingReport=true;reportControls();if(!busy)status.setText("Writing the captured report…");
  worker.execute(()->{
   String message;
   try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){
    if(output==null)throw new IOException("Cannot open the report destination");
    captured.copyTo(output,()->false);message="Report saved • "+captured.size+" bytes";
   }catch(Exception error){message="Export failed; destination may contain partial data: "+error.getMessage();}
   finally{captured.discard();}
   final String text=message;runOnUiThread(()->{if(!isDestroyed()){writingReport=false;reportControls();rescan.setEnabled(!busy&&selectedUri!=null);if(!busy)status.setText(text);Toast.makeText(this,text,Toast.LENGTH_LONG).show();}});
  });
 }
 @Override protected void onDestroy(){if(busy&&scanCancellation!=null)scanCancellation.cancel();worker.shutdown();if((!reportPickerOpen||isFinishing())&&preparedReport!=null)preparedReport.discard();if(retainedFile!=null)retainedFile.delete();if(ownedMember!=null)ownedMember.delete();super.onDestroy();}
 private class Graph extends View{
  final Inspector.Result r;final TextView detail;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int selected=-1;
  Graph(Inspector.Result data,TextView t){super(MainActivity.this);r=data;detail=t;setContentDescription("Entropy graph, 0 to 8 bits per byte");}
  @Override protected void onDraw(Canvas c){float l=dp(30),right=getWidth()-dp(8),top=dp(12),bottom=getHeight()-dp(20);p.setTextSize(dp(12));for(int i=0;i<=8;i+=2){float y=bottom-(bottom-top)*i/8f;p.setColor(0xff58646f);c.drawLine(l,y,right,y,p);p.setColor(0xffd3dce5);c.drawText(""+i,dp(7),y,p);}if(r.blocks.length==0)return;float w=(right-l)/r.blocks.length;p.setColor(0xff80cbc4);for(int i=0;i<r.blocks.length;i++)c.drawRect(l+w*i,bottom-(bottom-top)*(float)r.blocks[i]/8,l+w*(i+1),bottom,p);if(selected>=0){p.setColor(0xffffce75);float x=l+w*(selected+.5f);c.drawLine(x,top,x,bottom,p);}}
  @Override public boolean onTouchEvent(MotionEvent e){if(r.blocks.length==0)return false;if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){selected=Math.max(0,Math.min(r.blocks.length-1,(int)((e.getX()-dp(30))*r.blocks.length/Math.max(1,getWidth()-dp(38)))));long block=Math.max(4096,(r.size+255)/256);detail.setText(String.format(Locale.ROOT,"Offset 0x%X • %.4f bits/byte",block*selected,r.blocks[selected]));invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP){performClick();return true;}return true;}
  @Override public boolean performClick(){super.performClick();return true;}
 }
}

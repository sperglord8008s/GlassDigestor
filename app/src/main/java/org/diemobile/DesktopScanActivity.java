// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Expert scan laboratory. Uses the upstream desktop backend rather than
 * trying to infer which signature ran from a detection's display name.
 */
public final class DesktopScanActivity extends Activity {
    private static final String[] OPTIONS = ScanOptions.LABELS;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Choice> types = new ArrayList<>();
    private final ArrayList<Choice> signatures = new ArrayList<>();
    private String path, db, custom, currentReport = "", exportSnapshot = "";
    private Spinner format;
    private CheckBox[] options;
    private EditText include, exclude, filter;
    private TextView status;
    private ListView list;
    private Button scan, cancel;
    private boolean busy, scanning;
    private Engine.Cancellation scanCancellation;

    private static final class Choice {
        final int id;
        final String title, signature;
        Choice(int id, String title, String signature) {
            this.id=id; this.title=title; this.signature=signature;
        }
        @Override public String toString(){return title;}
    }

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private Button button(String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setOnClickListener(v->action.run());return b;
    }
    private TextView text(String s,int size){
        return WorkbenchUi.text(this,s,size);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        path=getIntent().getStringExtra("path");
        if(path==null||!new File(path).isFile()){finish();return;}
        // Database snapshots are prepared on the worker before any native request.

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8),dp(8),dp(8),dp(8));
        root.addView(text("Desktop scan laboratory",19));
        root.addView(text(getIntent().getStringExtra("name"),13));
        format=new Spinner(this);root.addView(format);
        LinearLayout toggles=new LinearLayout(this);toggles.setOrientation(LinearLayout.VERTICAL);
        options=new CheckBox[OPTIONS.length];
        ScanOptions storedOptions=ScanPreferences.load(this);
        int stored=storedOptions.flags;
        for(int i=0;i<OPTIONS.length;i+=3) {
            LinearLayout row=new LinearLayout(this);
            for(int j=i;j<Math.min(i+3,OPTIONS.length);j++) {
                CheckBox c=new CheckBox(this);c.setText(OPTIONS[j]);c.setTextSize(12);
                c.setChecked((stored&(1<<j))!=0);options[j]=c;
                row.addView(c,new LinearLayout.LayoutParams(0,dp(40),1));
            }
            toggles.addView(row);
        }
        root.addView(button("Scan options (expand / collapse)",()->toggles.setVisibility(
            toggles.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));
        toggles.setVisibility(View.GONE);root.addView(toggles);

        include=new EditText(this);exclude=new EditText(this);
        include.setSingleLine();exclude.setSingleLine();
        include.setHint("Include tags (; separated)");exclude.setHint("Exclude tags");
        include.setText(storedOptions.includeTags);
        exclude.setText(storedOptions.excludeTags);
        root.addView(include);root.addView(exclude);

        LinearLayout controls=new LinearLayout(this);
        scan=button("Scan",()->runScan());cancel=button("Cancel",()->{
            if(scanning&&scanCancellation!=null){scanCancellation.cancel();status.setText("Cancellation requested");}
        });
        cancel.setEnabled(false);
        controls.addView(scan);controls.addView(cancel);
        controls.addView(button("Facts",()->request(5,"","",this::showFacts)));
        controls.addView(button("Tags",()->request(6,"","",j->dialog("Available tags",j.optString("tags")))));
        root.addView(controls);

        LinearLayout more=new LinearLayout(this);
        more.addView(button("Signatures",()->loadSignatures()));
        more.addView(button("Raw report",()->dialog("Raw report",currentReport)));
        more.addView(button("Export",()->exportReport()));
        root.addView(more);
        root.addView(button("Offline custom databases",()->startActivity(new Intent(this,RuleDatabaseActivity.class))));
        root.addView(button("Desktop batch scanning",()->startActivity(new Intent(this,BatchScanActivity.class))));
        root.addView(button("Saved scan option profiles",()->startActivity(new Intent(this,ScanProfilesActivity.class))));
        status=text("Initializing bundled desktop database…",12);root.addView(status);
        filter=new EditText(this);filter.setSingleLine();filter.setHint("Filter results or signatures");
        root.addView(filter);
        list=new ListView(this);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){render();}
            public void afterTextChanged(android.text.Editable e){}
        });
        request(0,"","",this::loadTypes);
    }

    private int flags(){
        int value=0;
        for(int i=0;i<options.length;i++)if(options[i].isChecked())value|=1<<i;
        return value;
    }
    private int typeId(){
        Object selected=format.getSelectedItem();
        return selected instanceof Choice?((Choice)selected).id:0;
    }
    interface Completion {void accept(JSONObject result);}
    private void request(int verb,String arg,String source,Completion done){
        if(busy)return;
        final int f=flags(),t=typeId();
        final String inc=include.getText().toString(),exc=exclude.getText().toString();
        try {ScanPreferences.save(this,new ScanOptions(f,ScanPreferences.load(this).minimumStringLength,inc,exc));}
        catch(IllegalArgumentException e){status.setText(e.getMessage());return;}
        busy=true;scanning=verb==1;scan.setEnabled(false);cancel.setEnabled(scanning);
        final Engine.Cancellation cancellation=scanning?new Engine.Cancellation():null;
        if(scanning)scanCancellation=cancellation;
        status.setText(verb==1?"Scanning with desktop backend…":"Reading native backend…");
        worker.execute(()->{
            JSONObject result;
            try {
                EngineDatabase database=EngineDatabase.prepare(this);
                db=database.main.getAbsolutePath();custom=database.custom.getAbsolutePath();
                result=new JSONObject(Engine.desktop(path,db,custom,verb,f,t,inc,exc,arg,source,cancellation));
                result.put("provenance",new JSONObject().put("engineRevision",database.engineRevision)
                    .put("signatureRevision",database.signatureRevision).put("customArchiveSha256",database.customDigest));
            }
            catch(Exception|LinkageError e){result=new JSONObject();try{result.put("ok",false);result.put("error",e.toString());}catch(JSONException ignored){}}
            final JSONObject response=result;
            runOnUiThread(()->{
                if(isDestroyed())return;
                busy=false;scanning=false;scan.setEnabled(true);cancel.setEnabled(false);
                if(!response.optBoolean("ok")&&verb!=4){status.setText(response.optString("error","Native operation failed"));return;}
                status.setText("Complete");done.accept(response);
            });
        });
    }
    private void loadTypes(JSONObject result){
        JSONArray a=result.optJSONArray("types");types.clear();
        if(a!=null)for(int i=0;i<a.length();i++){
            JSONObject j=a.optJSONObject(i);
            if(j!=null)types.add(new Choice(j.optInt("id"),j.optString("name"),""));
        }
        if(types.isEmpty())types.add(new Choice(0,"Automatic",""));
        format.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));
        status.setText(result.optInt("signatureCount")+" loaded signatures • choose format and scan");
    }
    private void runScan(){
        request(1,"","",result->{
            currentReport=result.toString();signatures.clear();JSONArray rows=result.optJSONArray("rows");
            ArrayList<Integer> depths=new ArrayList<>();
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject r=rows.optJSONObject(i);
                if(r==null){depths.add(0);continue;}
                int parent=r.optInt("parent",-1),depth=parent>=0&&parent<depths.size()?depths.get(parent)+1:0;
                depths.add(depth);
                StringBuilder indentBuilder=new StringBuilder();for(int d=0;d<Math.min(depth,6);d++)indentBuilder.append("  ");String indent=indentBuilder.toString();
                String title=indent+r.optString("text");
                String sig=r.optString("signature");
                if(!sig.isEmpty())title+="\n"+indent+sig;
                signatures.add(new Choice(0,title,sig));
            }
            status.setText(result.optString("type")+" • "+result.optString("architecture")+" • "+
                    signatures.size()+" rows • "+result.optInt("signatureCount")+" loaded rules");
            filter.setText("");render();
            if(!result.optString("log").isEmpty())status.append(" • log available in raw report");
        });
    }
    private void showFacts(JSONObject j){
        currentReport=j.toString();dialog("Native file facts",pretty(j.toString()));
    }
    private void loadSignatures(){
        request(2,"","",j->{
            signatures.clear();JSONArray a=j.optJSONArray("signatures");
            if(a!=null)for(int i=0;i<a.length();i++){
                JSONObject x=a.optJSONObject(i);
                if(x!=null)signatures.add(new Choice(0,x.optString("display"),x.optString("path")));
            }
            status.setText(j.optInt("displayed")+" of "+j.optInt("total")+" loaded signatures • tap to view");
            filter.setText("");render();
        });
    }
    private void render(){
        String q=filter.getText().toString().toLowerCase(Locale.ROOT);
        ArrayList<Choice> visible=new ArrayList<>();
        for(Choice c:signatures)if(c.title.toLowerCase(Locale.ROOT).contains(q)||
            c.signature.toLowerCase(Locale.ROOT).contains(q))visible.add(c);
        list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,visible));
        list.setOnItemClickListener((p,v,i,id)->{
            Choice choice=visible.get(i);
            if(choice.signature.isEmpty())dialog("Detection group",choice.title);
            else request(3,choice.signature,"",j->editSignature(choice.signature,j.optString("source")));
        });
    }
    private void editSignature(String signature,String original){
        EditText editor=new EditText(this);editor.setTypeface(Typeface.MONOSPACE);editor.setTextSize(12);
        editor.setText(original);editor.setMinLines(12);editor.setMaxLines(22);editor.setHorizontallyScrolling(true);
        new AlertDialog.Builder(this).setTitle("Signature source / test")
            .setMessage("Edits are temporary. Run only a trusted rule against this file; no installed app is executed. Script testing has no cancellation yet.")
            .setView(editor).setNegativeButton("Close",null)
            .setNeutralButton("Copy original",(d,w)->{
                android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("DiE signature",original));
            })
            .setPositiveButton("Test edited rule",(d,w)->{
                String source=editor.getText().toString();
                if(source.length()>1024*1024){status.setText("Rule exceeds 1 MiB test limit");return;}
                request(4,signature,source,j->dialog(j.optBoolean("ok")?"Rule test":"Rule test failed",j.optString("report")));
            }).show();
    }
    private String pretty(String s){try{return new JSONObject(s).toString(2);}catch(Exception e){return s;}}
    private void dialog(String title,String value){
        ScrollView sc=new ScrollView(this);TextView t=text(value,12);t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);sc.addView(t);new AlertDialog.Builder(this).setTitle(title)
            .setView(sc).setPositiveButton("Close",null).show();
    }
    private void exportReport(){
        if(currentReport.isEmpty()){status.setText("No report yet");return;}
        exportSnapshot=currentReport;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"die-desktop-report.json");
        startActivityForResult(i,10);
    }
    @Override protected void onActivityResult(int req,int code,Intent data){
        super.onActivityResult(req,code,data);
        if(req!=10||code!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();final String snapshot=exportSnapshot;
        worker.execute(()->{
            String message;
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("Cannot open export");
                out.write(snapshot.getBytes(StandardCharsets.UTF_8));message="Report exported";
            }catch(Exception e){message="Export failed: "+e.getMessage();}
            final String m=message;runOnUiThread(()->{if(!isDestroyed())status.setText(m);});
        });
    }
    @Override protected void onResume(){
        super.onResume();
        if(options==null)return;
        ScanOptions stored=ScanPreferences.load(this);
        boolean changed=flags()!=stored.flags||!include.getText().toString().equals(stored.includeTags)
            ||!exclude.getText().toString().equals(stored.excludeTags);
        for(int i=0;i<options.length;i++)options[i].setChecked((stored.flags&(1<<i))!=0);
        include.setText(stored.includeTags);exclude.setText(stored.excludeTags);
        if(changed&&!busy)status.setText("Shared options updated • scan to refresh results and database provenance");
    }
    @Override protected void onPause(){
        if(options!=null)try{
            ScanPreferences.save(this,new ScanOptions(flags(),ScanPreferences.load(this).minimumStringLength,
                include.getText().toString(),exclude.getText().toString()));
        }catch(IllegalArgumentException e){Toast.makeText(this,"Options not saved: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        super.onPause();
    }
    @Override protected void onDestroy(){if(scanning&&scanCancellation!=null)scanCancellation.cancel();worker.shutdown();super.onDestroy();}
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import java.util.function.BooleanSupplier;

final class KnowledgeReport {
 static final int MAX_ENTRIES=10000, MAX_CLASSES=DexDocument.MAX_CLASSES, MAX_DEX=DexDocument.MAX_BYTES;
 static final long MAX_TOTAL_DEX=96L*1024*1024;
 static final class Output {
  StringBuilder guide=new StringBuilder(), apk=new StringBuilder();
  Set<Integer> describedRules=new HashSet<>();
  BooleanSupplier cancelled=()->false;
  boolean clipped;
  void append(String text){
   if(clipped)return;
   if(text.length()>16*1024*1024-apk.length()-256){apk.append("\nPARTIAL: APK summary exceeds its 16 Mi-character budget. The binary manifest, DEX, signing and full SDK rule tools have separate scopes and limits.\n");clipped=true;return;}
   apk.append(text);
  }
  void section(String s){append("\n"+s+"\n");}
  void item(String s){append("• "+s+"\n");}
 }
 static String read(InputStream in,int max)throws IOException{
  return new String(bytes(in,max),StandardCharsets.UTF_8);
 }
 static byte[] bytes(InputStream in,int max)throws IOException{
  return bytes(in,max,()->false);
 }
 static byte[] bytes(InputStream in,int max,BooleanSupplier cancelled)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[32768];int n;
  while((n=in.read(buf))!=-1){EvidenceRows.check(cancelled);if(n>max-out.size())throw new IOException("Size limit exceeded");out.write(buf,0,n);}
  return out.toByteArray();
 }
 static JSONObject asset(Context c,String path)throws Exception{
  try(InputStream in=c.getAssets().open(path)){return new JSONObject(read(in,16*1024*1024));}
 }
 static Output analyze(Context c,File file,Inspector.Result r,String displayName){
  return analyze(c,file,r,displayName,()->false);
 }
 static Output analyze(Context c,File file,Inspector.Result r,String displayName,BooleanSupplier cancelled){
  Output o=new Output();
  o.cancelled=cancelled;
  o.guide.append("FILE 101\n\n").append(displayName).append("\n\n");
  o.guide.append("Observed format: ").append(r.type).append("\n");
  String t=r.type;
  if(t.startsWith("ELF"))o.guide.append("An Executable and Linkable Format file holds native machine code or related data. Android uses ELF for executables and .so shared libraries. Architecture tells you which processor instruction set the code targets, not what it does. A library can expose functions to other programs; file type alone cannot reveal behavior.\n");
  else if(t.contains("DEX"))o.guide.append("Dalvik Executable (DEX) contains Android bytecode, normally executed by ART. One APK may contain several DEX files. Class names can reveal bundled SDKs, but names can be renamed by obfuscation and do not prove that a class is called.\n");
  else if(t.contains("ZIP"))o.guide.append("A ZIP container groups files, often compressing them. An APK is a specialized ZIP containing an Android manifest, code, resources and possibly native libraries. An ordinary ZIP is not necessarily an app.\n");
  else if(t.contains("MZ"))o.guide.append("MZ is a historical executable header also used at the beginning of Windows PE files. It is a candidate marker—not sufficient by itself to establish a valid executable or processor type.\n");
  else if(t.equals("PDF"))o.guide.append("PDF is a document format which can include text, images, forms, embedded files and scripts. This scanner does not render or execute its content.\n");
  else if(t.equals("PNG"))o.guide.append("PNG is a losslessly compressed raster image format. A magic-byte match does not validate every chunk or establish what the picture contains.\n");
  else o.guide.append("No recognized header in the local inspector. It may be an unsupported format, raw data, a fragment, compressed data or encrypted data. Check the CDiE results for additional format information.\n");
  o.guide.append("\nSIZE AND IDENTITY\n").append(r.size).append(" bytes. Size is the amount of stored data, not a measure of complexity.\nSHA-256: ").append(r.sha256).append("\nA content fingerprint: matching hashes strongly indicate identical bytes, but a hash alone does not tell you who made the file or if it is safe.\n\nENTROPY\n");
  o.guide.append(String.format(Locale.ROOT,"%.4f of 8 bits per byte. ",r.entropy));
  o.guide.append("Entropy estimates how evenly byte values occur. Compression, encryption, images and random data can all produce high entropy. Low entropy may reflect repeated data or padding. Neither establishes malware or encryption. Use the graph to locate transitions, then inspect the corresponding content.\n\nSIGNATURES\nCDiE compares file structures and patterns against its database. A matching compiler, packer or SDK identifies a possible technology, not a verified author or runtime behavior. A missed match does not establish absence.\n\nSTRINGS AND HEX\nStrings are printable byte sequences and may expose names or configuration; they may also be unused or coincidental. Hex displays original bytes; an offset is a position in the file, not always a virtual memory address. Previews are bounded.\n\nLIMITS\nStatic analysis only: no app is installed, executed, or submitted online. Results are not a safety verdict. Malformed, encrypted, split or obfuscated content may be missed. Individual detection details in Scan show underlying rule output.\n");
  if(t.startsWith("ELF"))try{elf(file,o);}catch(Exception e){o.guide.append("\nELF header detail unavailable: ").append(e.getMessage()).append("\n");}
  if(t.contains("ZIP"))try{archive(c,file,o,cancelled);}catch(Exception e){o.item("Archive inspection incomplete: "+e.getMessage());}
  else if(t.contains("DEX"))try{
   DexDocument document=DexDocument.read(file,cancelled);Set<String> classes=document.definedClassNames(cancelled);
   dexSummary(o,displayName,document);o.item("Distinct MUTF-8/Unicode defined class names inspected: "+classes.size());
   try{dexLibraries(catalog(c,cancelled),classes,o,cancelled);}catch(InterruptedIOException stopped){throw stopped;}catch(Exception error){o.item("LibChecker DEX matching incomplete: "+error.getMessage());}
   trackers(c,document.definedClassDescriptors(cancelled),o);
  }catch(Exception e){o.item("DEX inspection incomplete: "+e.getMessage());}
  else o.apk.append("Not a recognized ZIP/APK or DEX input. Use File info / format mechanisms for general interpretation.\n");
  return o;
 }
 static void elf(File file,Output o)throws IOException{
  try(RandomAccessFile in=new RandomAccessFile(file,"r")){
   byte[] b=new byte[(int)Math.min(64,in.length())];in.readFully(b);
   if(b.length<16||(b[4]!=1&&b[4]!=2)||(b[5]!=1&&b[5]!=2))throw new IOException("Unsupported ELF class or byte order");
   boolean bits=b[4]==2,le=b[5]==1;
   if(b.length<(bits?64:52))throw new IOException("Truncated header");
   int type=(int)num(b,16,2,le);long entry=num(b,24,bits?8:4,le);
   int ph=(int)num(b,bits?56:44,2,le),sh=(int)num(b,bits?60:48,2,le);
   o.guide.append("\nELF HEADER (direct observation)\n").append(bits?"64-bit":"32-bit").append(", ").append(le?"little-endian":"big-endian").append("\nObject type: ").append(type==1?"relocatable object":type==2?"executable":type==3?"shared object / position-independent executable":type==4?"core dump":"numeric "+type).append("\nEntry point: 0x").append(Long.toUnsignedString(entry,16)).append("\nProgram headers: ").append(ph).append(" (loader mapping descriptions)\nSection headers: ").append(sh).append(" (linking/analysis descriptions; zero can indicate absence or extended numbering)\nAn entry point is a virtual address, not a file offset.\n");
  }
 }
 static long num(byte[] b,int off,int n,boolean le)throws IOException{
  if(off<0||n<1||off>b.length-n)throw new IOException("Header offset out of range");long v=0;
  for(int i=0;i<n;i++)v=(v<<8)|(b[off+(le?n-1-i:i)]&255L);return v;
 }
 static DexDocument dex(byte[] b,Set<String> out,BooleanSupplier cancelled)throws Exception{
  DexDocument document=DexDocument.takeOwnership(b,cancelled);Set<String> names=document.definedClassNames(cancelled);int added=0;
  for(String name:names){EvidenceRows.check(cancelled);if(!out.contains(name))added++;}
  if(added>MAX_CLASSES-out.size())throw new IOException("Class inspection limit exceeded (120,000 distinct names)");
  out.addAll(names);return document;
 }
 static void dexSummary(Output o,String name,DexDocument document){
  o.section("DEX metadata — "+name+" — "+DexDocument.ENGINE+" "+DexDocument.VERSION);o.item("DEX bytes SHA-256: "+document.sha256);
  for(DexDocument.Entry entry:document.entries){
   o.item("Entry "+entry.index+": DEX "+entry.version+"; header @0x"+Integer.toHexString(entry.headerOffset)+"; declared entry bytes="+entry.fileSize
    +"; classes="+entry.dex.getClassSection().size()+"; types="+entry.dex.getTypeSection().size()+"; strings="+entry.dex.getStringSection().size()
    +"; method IDs="+entry.dex.getMethodSection().size()+"; field IDs="+entry.dex.getFieldSection().size()+"; prototypes="+entry.dex.getProtoSection().size());
   o.item("Entry Adler32 stored="+entry.storedChecksum+", measured="+entry.measuredChecksum+", matches="+entry.checksumValid
    +"; SHA-1 consistency stored="+entry.storedSignature+", measured="+entry.measuredSignature+", matches="+entry.signatureValid);
  }
  o.item("Header integrity is not publisher authentication or ART verification. Identifier counts include referenced types and members; they do not establish local definitions, loading or execution. Class-name matching is a static name association.");
 }
 static void archive(Context c,File file,Output o,BooleanSupplier cancelled)throws Exception{
  Set<String> nativeNames=new TreeSet<>(),abis=new TreeSet<>(),classes=new TreeSet<>(),descriptors=new TreeSet<>();List<String> entries=new ArrayList<>();
  boolean manifest=false;int count=0,dexCount=0;long total=0,dexBytes=0;boolean partial=false;
  try(ZipFile zip=new ZipFile(file)){
   Map<String,Integer> dexNames=new HashMap<>();Enumeration<? extends ZipEntry> directory=zip.entries();int directoryCount=0;
   while(directory.hasMoreElements()){
    EvidenceRows.check(cancelled);ZipEntry candidate=directory.nextElement();if(++directoryCount>100000)throw new IOException("Metadata directory budget: 100,000 entries; DEX-name uniqueness could not be established");
    if(candidate.getName().matches("classes([0-9]+)?\\.dex")){
     if(dexNames.containsKey(candidate.getName()))dexNames.put(candidate.getName(),2);
     else if(dexNames.size()<128)dexNames.put(candidate.getName(),1);else partial=true;
    }
   }
   Enumeration<? extends ZipEntry> es=zip.entries();
   while(es.hasMoreElements()){
    EvidenceRows.check(cancelled);
    ZipEntry e=es.nextElement();if(++count>MAX_ENTRIES){partial=true;break;}
    String n=e.getName();if(e.getSize()>0&&Long.MAX_VALUE-total>=e.getSize())total+=e.getSize();
    if(entries.size()<1000)entries.add(n+"  ["+e.getSize()+" bytes]");
    if(n.equals("AndroidManifest.xml"))manifest=true;
    if(n.startsWith("lib/")&&n.endsWith(".so")){String[] parts=n.split("/");if(parts.length==3){abis.add(parts[1]);nativeNames.add(parts[2]);}}
    if(n.matches("classes([0-9]+)?\\.dex")){
     dexCount++;
     if(!Integer.valueOf(1).equals(dexNames.get(n))){partial=true;o.item("DEX "+n+" not inspected: duplicate name or 128-name budget exceeded");continue;}
     if(e.getSize()<0||e.getSize()>MAX_DEX||e.getSize()>MAX_TOTAL_DEX-dexBytes){partial=true;o.item("DEX "+n+" not inspected: unknown size or 32 MiB/file / 96 MiB total budget");continue;}
     dexBytes+=e.getSize(); // Reserve declared bytes even if parsing/CRC fails.
     try(InputStream in=zip.getInputStream(e)){
      byte[] b=bytes(in,(int)e.getSize(),cancelled);CRC32 crc=new CRC32();crc.update(b);
      if(b.length!=e.getSize()||crc.getValue()!=e.getCrc())throw new IOException("Expanded size or CRC32 mismatch");
      DexDocument document=dex(b,classes,cancelled);Set<String> raw=document.definedClassDescriptors(cancelled);
      for(String descriptor:raw){EvidenceRows.check(cancelled);descriptors.add(descriptor);if(descriptors.size()>MAX_CLASSES)throw new IOException("Defined descriptor inspection exceeds 120,000 names");}
      dexSummary(o,n,document);
     }catch(Exception ex){if(cancelled.getAsBoolean())throw ex;partial=true;o.item("DEX "+n+" inspection incomplete: "+ex.getMessage());}
    }
   }
  }
  o.section(manifest?"APK structure":"ZIP structure");o.item("Entries inspected: "+Math.min(count,MAX_ENTRIES));o.item("Declared expanded bytes: "+total+" (header values, not verified decompressed size)");
  o.item("DEX files encountered in the inspected inventory: "+dexCount+"; distinct MUTF-8/Unicode defined class names inspected: "+classes.size());
  o.item("Native ABI folders: "+(abis.isEmpty()?"none seen":abis));if(partial)o.item("PARTIAL: archive/DEX limits or parser errors; results are not exhaustive.");
  KnowledgeCatalog rules=null;try{rules=catalog(c,cancelled);o.section("LibChecker canonical matcher details");o.item("Catalog source "+KnowledgeCatalog.SOURCE+"; SHA-256 "+rules.sha256+"; active matchers="+rules.matcher.rules.size()+"; unsupported regex="+rules.matcher.unsupported.size()+". Exact equality precedes priority-ordered whole-string regex; names alone do not verify code identity.");}catch(Exception e){o.item("LibChecker canonical database unavailable: "+e.getMessage());}
  o.section("Native libraries — machine-code modules loaded by applications");
  for(String n:nativeNames){EvidenceRows.check(cancelled);if(o.clipped)break;o.item(n);match(o,rules,"native-libs",n);}
  if(nativeNames.isEmpty())o.item("No conventional lib/<ABI>/*.so entries seen; code could be stored elsewhere.");
  if(manifest){o.guide.append("\nAPK FOUND\nThis archive contains AndroidManifest.xml. See APK / SDKs for package identity, permissions, exposed components, library details and community package guidance. The report describes requested capabilities, not actions observed in use.\n");o.append(ApkSigningReport.analyze(file));pkg(c,file,o,rules);manifestLibraries(c,file,o,rules,cancelled);}
  if(dexCount>0&&rules!=null)try{dexLibraries(rules,classes,o,cancelled);}catch(InterruptedIOException stopped){throw stopped;}catch(Exception error){o.item("LibChecker DEX matching incomplete: "+error.getMessage());}
  if(dexCount>0)trackers(c,descriptors,o);
  o.section("Archive inventory (first 1,000 entries; no extraction)");for(String n:entries)o.item(n);
  if(count>entries.size())o.item("Inventory truncated.");
 }
 static KnowledgeCatalog catalog(Context c,BooleanSupplier stop)throws Exception{
  try(InputStream input=c.getAssets().open("knowledge/libchecker-v5.json")){return KnowledgeCatalog.read(input,stop);}
 }
 static void match(Output o,KnowledgeCatalog rules,String kind,String evidence){
  if(rules==null)return;
  int type=kind.equals("native-libs")?0:kind.equals("services-libs")?1:kind.equals("activities-libs")?2:kind.equals("receivers-libs")?3:kind.equals("providers-libs")?4:-1;
  if(type<0){o.item("Unsupported library name type: "+kind);return;}
  match(o,rules,type,evidence,kind,()->Thread.currentThread().isInterrupted()||o.cancelled.getAsBoolean());
 }
 static void match(Output o,KnowledgeCatalog rules,int type,String evidence,String origin,BooleanSupplier cancelled){
  if(rules==null)return;
  try{
   NameMatcher.Match hit=rules.matcher.match(type,evidence,cancelled);if(hit==null)return;
   NameMatcher.Rule rule=hit.rule;JSONObject description=rules.english(rule);
   o.item("LibChecker "+hit.method+": "+rules.label(rule)+" [matcher "+rule.id+", UUID "+rule.original.getString("uuid")+", priority "+rule.priority+"]");
   o.item("Matched source: "+origin+" / "+evidence+"; original pattern/name: "+rule.name);
   if(o.describedRules.add(rule.id)){
    String team=description.optString("dev_team");if(!team.isEmpty())o.item("Development team: "+team);
    String text=description.optString("description");if(!text.isEmpty())o.item(text);
    String link=description.optString("source_link");if(!link.isEmpty())o.item("Source: "+link);
    o.item("Name association only; code identity, loading and use are not established. The complete effective locale payload is available in the SDK rule catalog.");
   }
  }catch(Exception error){o.item("Library matching incomplete: "+error.getMessage());}
 }
 static void dexLibraries(KnowledgeCatalog rules,Set<String> classes,Output output,BooleanSupplier stop)throws Exception{
  output.section("LibChecker DEX package-name associations");Set<String> seen=new HashSet<>();long units=0;int matches=0;
  for(String name:classes){
   EvidenceRows.check(stop);int dot=name.lastIndexOf('.');
   for(int depth=0;dot>0;depth++){
    if(depth>=64)throw new IOException("DEX package hierarchy exceeds 64 levels; library-name matching incomplete");
    String prefix=name.substring(0,dot);
    if(seen.add(prefix)){
     units+=prefix.length();if(seen.size()>250000||units>16L*1024*1024)throw new IOException("DEX package-name input exceeds count/text budgets; no exhaustive SDK result");
     NameMatcher.Match hit=rules.matcher.match(5,prefix,stop);
     if(hit!=null){matches++;match(output,rules,5,prefix,"derived package ancestor of defined class "+name,stop);}
    }
    dot=name.lastIndexOf('.',dot-1);
   }
  }
  output.item(seen.size()+" distinct package ancestors inspected; "+matches+" name associations. Class names are defined-class metadata; they do not establish calls or SDK invocation.");
 }
 static void manifestLibraries(Context context,File input,Output output,KnowledgeCatalog rules,BooleanSupplier stop){
  if(rules==null)return;ArchiveStore.Extracted extracted=null;
  try{
   ArchiveStore.Inventory archive=ArchiveStore.read(input,stop);ArchiveStore.Member selected=null;
   for(ArchiveStore.Member member:archive.members)if(member.name.equals("AndroidManifest.xml")){if(selected!=null)throw new IOException("Duplicate binary manifest names");selected=member;}
   if(selected==null)return;if(selected.size<0||selected.size>64L*1024*1024)throw new IOException("Compiled manifest exceeds 64 MiB or has unknown length");
   extracted=ArchiveStore.extract(archive,selected,new File(context.getCacheDir(),"sdk-manifest-inputs"),stop);
   try(BinaryWorkerClient runner=new BinaryWorkerClient(context,"resources")){
    AndroidResourceDocument document=new AndroidResourceDocument(extracted.file,"AndroidManifest.xml",extracted.sha256,runner.scan(extracted.file,stop),stop);
    SdkManifestNames names=new SdkManifestNames(document,stop);
    output.section("SDK names from the original compiled manifest");output.item("CRC/size-verified expanded manifest SHA-256 "+extracted.sha256+"; declared package "+names.packageName+"; "+names.facts.size()+" literal name records. Offsets below refer to the uncompressed manifest, not its parent APK.");
    for(SdkManifestNames.Fact fact:names.facts){
     EvidenceRows.check(stop);match(output,rules,fact.type,fact.value,"compiled "+fact.element+" attribute "+fact.attributeIndex+" @0x"+Long.toHexString(fact.physicalOffset)+"; original="+fact.original+"; "+fact.selection,stop);
    }
    for(String diagnostic:names.diagnostics)output.item("INCOMPLETE name resolution: "+diagnostic);
    output.item("Shared/static libraries, permissions, metadata and intent actions are declarations. Referenced strings, split/framework resolution and platform acceptance can remain incomplete; no component is executed.");
   }
  }catch(Exception error){output.item("Compiled manifest SDK-name inspection incomplete: "+error.getMessage());}
  finally{if(extracted!=null)extracted.file.delete();}
 }
 static void pkg(Context c,File f,Output o,KnowledgeCatalog rules){
  try{
   int flags=PackageManager.GET_ACTIVITIES|PackageManager.GET_SERVICES|PackageManager.GET_RECEIVERS|PackageManager.GET_PROVIDERS|PackageManager.GET_PERMISSIONS;
   flags|=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
   PackageManager pm=c.getPackageManager();PackageInfo p=pm.getPackageArchiveInfo(f.getAbsolutePath(),flags);
   if(p==null){o.item("Android PackageManager could not parse this APK. It may be a split, malformed, unsupported or incomplete package.");return;}
   o.section("Package identity");o.item("Package ID: "+p.packageName);o.item("Version: "+p.versionName+" ("+(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode)+")");
   if(p.applicationInfo!=null){ApplicationInfo a=p.applicationInfo;o.item("Minimum Android API: "+a.minSdkVersion+"; target API: "+a.targetSdkVersion+" (compatibility behavior, not a security rating)");o.item("Debuggable flag: "+((a.flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0)+" — allows debugging when platform conditions permit.");o.item("Allow backup flag: "+((a.flags&ApplicationInfo.FLAG_ALLOW_BACKUP)!=0)+" — actual backup depends on OS, policies and app rules.");}
   o.section("Signing identity");Signature[] sig=Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;
   if(sig!=null){for(Signature x:sig)o.item("Platform-reported certificate SHA-256: "+Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(x.toByteArray())));}
   else o.item("No certificate exposed by PackageManager.");
   o.item("A certificate fingerprint can be compared to a trusted release. This is not an independent APK signing-scheme verification or a trusted-publisher verdict.");
   o.section("Requested permissions (not necessarily granted or exercised)");if(p.requestedPermissions!=null)for(String perm:p.requestedPermissions){
    String desc="";
    try{PermissionInfo pi=pm.getPermissionInfo(perm,0);CharSequence d=pi.loadDescription(pm);if(d!=null)desc=d.toString();int base=pi.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE;desc+=(desc.isEmpty()?"":" ")+"Protection: "+(base==PermissionInfo.PROTECTION_DANGEROUS?"runtime/dangerous":base==PermissionInfo.PROTECTION_NORMAL?"normal":base==PermissionInfo.PROTECTION_SIGNATURE?"signature-restricted":"other")+".";}catch(Exception ignored){}
    o.item(perm+" — "+permission(perm)+(desc.isEmpty()?"":" "+desc));
   }else o.item("No requested permissions returned.");
   components(o,rules,"activities-libs","Activities — screens or entry points",p.activities);
   components(o,rules,"services-libs","Services — background or bound work",p.services);
   components(o,rules,"receivers-libs","Receivers — handle broadcast events",p.receivers);
   components(o,rules,"providers-libs","Providers — structured data access interfaces",p.providers);
   o.section("What does it do? / sourced community knowledge (UAD-NG)");
   try(InputStream input=c.getAssets().open("knowledge/uad.json")){
    PackageKnowledge knowledge=PackageKnowledge.read(input,o.cancelled);
    if(!knowledge.sha256.equals(PackageKnowledge.PINNED_SHA256))throw new IOException("Bundled knowledge differs from its pinned content hash");
    List<PackageKnowledge.Entry> matches=knowledge.byId.get(p.packageName);
    if(matches==null)o.item("No exact package ID match in this pinned catalog. Absence supplies no behavior or removal conclusion.");
    else{
     o.item(matches.size()+" original community record(s); every duplicate ID occurrence is retained without selecting a preferred description or category.");
     for(PackageKnowledge.Entry entry:matches){
      o.item("Original source record #"+entry.index+" • "+entry.id);
      if(entry.value!=null)for(String key:new String[]{"description","list","removal","dependencies","neededBy","labels"})if(entry.value.has(key)){
       StringWriter encoded=new StringWriter();EvidenceJson.write(encoded,entry.value.get(key),o.cancelled);o.item("Community "+key+": "+encoded);
      }
      for(String diagnostic:entry.diagnostics)o.item("Source coverage: "+diagnostic);
     }
    }
    o.item("UAD-NG revision "+PackageKnowledge.REVISION+" • exact catalog SHA-256 "+knowledge.sha256+" • GPL-3.0. Full original fields and relationships are available in the package knowledge viewer.");
    o.item(PackageKnowledge.MEANING);
   }catch(InterruptedIOException cancelled){throw cancelled;}catch(Exception e){o.item("UAD knowledge unavailable: "+e.getMessage());}
  }catch(Exception e){o.item("Package metadata incomplete: "+e.getMessage());}
 }
 static void components(Output o,KnowledgeCatalog rules,String kind,String label,ComponentInfo[] a){
  o.section(label);if(a==null||a.length==0){o.item("None returned.");return;}
  o.item(a.length+" declared entries. Exported means other apps may be able to address the component, subject to permission and platform restrictions.");
  for(int i=0;i<Math.min(a.length,500);i++){ComponentInfo v=a[i];String protection="";
   if(o.cancelled.getAsBoolean()||o.clipped){o.item("Component matching cancelled or summary budget reached; remaining names were not summarized.");return;}
   if(v instanceof ActivityInfo)protection=((ActivityInfo)v).permission;
   if(v instanceof ServiceInfo)protection=((ServiceInfo)v).permission;
   if(v instanceof ProviderInfo){ProviderInfo p=(ProviderInfo)v;protection="read="+p.readPermission+", write="+p.writePermission+", authority="+p.authority;}
   o.item(v.name+" [exported="+v.exported+", enabled="+v.enabled+"]"+(protection==null||protection.isEmpty()?"":" "+protection));match(o,rules,kind,v.name);
  }if(a.length>500)o.item("Component list truncated to 500.");
 }
 static String permission(String p){
  if(p.equals("android.permission.INTERNET"))return "Can open network sockets; destination and transmitted data are not established.";
  if(p.contains("LOCATION"))return "Location-related capability; accuracy and background access depend on the specific grant and operating system.";
  if(p.equals("android.permission.RECORD_AUDIO"))return "Can request microphone access; actual use requires runtime conditions.";
  if(p.equals("android.permission.CAMERA"))return "Can request camera access; does not show that images were recorded.";
  if(p.contains("CONTACTS"))return "Contact data access or modification, depending on the permission.";
  if(p.contains("SMS")||p.contains("CALL_LOG"))return "Messaging or call-history capability; access can be tightly restricted by Android.";
  if(p.equals("android.permission.QUERY_ALL_PACKAGES"))return "Can request broad visibility of installed package identities.";
  if(p.contains("READ_MEDIA")||p.contains("EXTERNAL_STORAGE"))return "Media or storage capability; access depends on Android version, scope and grants.";
  if(p.contains("BLUETOOTH")||p.contains("NEARBY"))return "Nearby device connectivity or discovery capability.";
  if(p.equals("android.permission.REQUEST_INSTALL_PACKAGES"))return "Can request user-mediated package installation; not permission for silent installation.";
  return "Declared permission; consult platform or defining application's documentation for its exact scope.";
 }
 static void trackers(Context c,Set<String> descriptors,Output o)throws Exception{
  o.section("Exodus tracker associations — offline imported database");
  File f=new File(c.getFilesDir(),"exodus-trackers.json");
  if(!f.isFile()){o.item("No Exodus snapshot imported. Use Options > Import Exodus database (JSON). No tracker result is available.");return;}
  try(InputStream in=new FileInputStream(f)){
   TrackerCatalog catalog=TrackerCatalog.read(in,o.cancelled);TrackerCatalog.Result result=catalog.match(descriptors,o.cancelled);
   o.item("Exact imported bytes SHA-256 "+catalog.sha256+"; "+catalog.bytes.length+" bytes; "+catalog.rules.size()+" records. Independent RE2/J search adapter, frozen Unicode "+TrackerUnicode.VERSION+"; audited Exodus contract "+TrackerCatalog.REFERENCE+". No online authenticity check.");
   for(TrackerCatalog.Evaluation evaluation:result.evaluations){
    EvidenceRows.check(o.cancelled);if(o.clipped)break;TrackerCatalog.Rule rule=evaluation.rule;
    if(evaluation.status.equals("matched")){
     o.item(TrackerCatalog.preview(rule.name,512)+" [Exodus ID "+rule.id+"] — regex-search association; original signature: "+rule.signature);
     o.item("Matched original descriptor "+evaluation.descriptor+"; slash-name match UTF-16 units ["+evaluation.start+", "+evaluation.end+") = "+evaluation.input.substring(evaluation.start,evaluation.end)+". First matching class per rule; names do not verify tracker use.");
     o.item("Original tracker metadata JSON: "+TrackerCatalog.preview(rule.slice.raw,8192));
    }else if(evaluation.status.equals("unsupported")||evaluation.status.equals("not-fully-searched"))o.item("INCOMPLETE tracker "+rule.id+" / "+TrackerCatalog.preview(rule.name,512)+": "+evaluation.status+"; "+rule.diagnostic);
   }
   o.item(result.hits+" matching rules; "+result.unsupportedRules+" unsupported rules; "+result.unsearched+" not fully searched; "+result.unsupportedInputs.size()+" unsupported class names; "+result.inputCount+" original descriptors; "+result.comparisons+" actual regex/class comparisons. Required-literal prefilter: "+catalog.prefilter.ruleCount+" indexed rules / "+catalog.prefilter.stateCount()+" states, "+result.prefilterUnits+" scanned UTF-16 units / "+result.prefilterSteps+" work steps. Only RE2/J establishes a match/span. Ignored short signatures follow the upstream >3-code-point gate.");
   if(!result.complete())o.item("PARTIAL tracker search: unsupported rules/names or budget limits leave unknown results. "+result.limit);
   for(int i=0;i<Math.min(16,result.unsupportedInputs.size());i++)o.item("Unsupported descriptor (LF, isolated surrogate or >16,384 UTF-16 units): "+result.unsupportedInputs.get(i));
   o.item("Imported Exodus database is ODbL 1.0 / individual contents DbCL 1.0. Complete unknown fields, source/version metadata, exact original records and snapshot bytes are available in Imported Exodus rules. This independent adapter does not bundle the AGPL Exodus core. It inspects defined classes in the bounded selected DEX/APK scope, unlike upstream dexdump text extraction; nested APKs, omitted splits, arbitrary type references and live traffic are not inspected here. No class association proves initialization, collection, transmission, a vulnerability or malicious behavior.");
  }catch(InterruptedIOException cancelled){throw cancelled;}catch(Exception e){o.item("Exodus snapshot could not be analyzed: "+e.getMessage());}
 }
}

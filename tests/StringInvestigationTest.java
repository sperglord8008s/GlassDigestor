// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Classifier provenance and exact text/physical evidence, with real byte streams. */
public final class StringInvestigationTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static boolean matches(String text, String rule) throws Exception { return StringClassifier.classify(text,() -> false).hits.stream().anyMatch(hit -> hit.rule.id.equals(rule)); }
    private static EvidenceRows.Row find(EvidenceRows rows, String text, String encoding) throws Exception {
        for (int i = 0; i < rows.size(); i++) { EvidenceRows.Row row = rows.row(i,() -> false); JSONObject value = row.json(); if (value.getString("value").equals(text) && value.getString("encoding").equals(encoding)) return row; }
        throw new AssertionError("Missing " + encoding + " text: " + text);
    }
    public static void main(String[] args) throws Exception {
        Path temporary = Path.of(args[0]);
        Map<String,String> positives = new LinkedHashMap<>();
        positives.put("net.uri","https://fixture.invalid/api/v1?q=1"); positives.put("net.cleartext","http://fixture.invalid/"); positives.put("net.uri-credentials","https://user:password@fixture.invalid/");
        positives.put("android.uri","content://org.fixture.provider/path"); positives.put("net.api-path","/api/v2/users"); positives.put("net.ipv4","192.0.2.4:443"); positives.put("net.ipv6","[2001:db8::1]:8443");
        positives.put("net.domain","sub.fixture.invalid"); positives.put("net.email","investigation@example.invalid"); positives.put("path.windows","C:\\Users\\fixture\\test.exe"); positives.put("path.unix","/data/user/0/org.fixture/files/test");
        positives.put("path.registry","HKEY_CURRENT_USER\\Software\\Fixture"); positives.put("cmd.interpreter","/bin/sh -c echo fixture"); positives.put("cmd.transfer","curl https://fixture.invalid");
        positives.put("cmd.destructive","rm -rf /fixture"); positives.put("cmd.encoded","-EncodedCommand SQBFAFgAIAAnAHQAZQBzAHQAJwA="); positives.put("api.windows-exec","CreateRemoteThread");
        positives.put("api.loader","DexClassLoader"); positives.put("api.process","java/lang/Runtime"); positives.put("api.unsafe-c","strcpy"); positives.put("api.android-capability","AccessibilityService");
        positives.put("secret.aws-id","AKIA1234567890ABCDEF"); positives.put("secret.google-api","AIza"+"A".repeat(35)); positives.put("secret.github","ghp_"+"A".repeat(36)); positives.put("secret.slack","xoxb-1234567890-abcdef");
        positives.put("secret.assignment","client_secret=EXAMPLE_VALUE_ONLY"); positives.put("secret.jwt","eyJhbGciOiJub25lIn0.eyJzdWIiOiJmaXh0dXJlIn0.");
        positives.put("crypto.private-key","-----BEGIN PRIVATE KEY-----"); positives.put("crypto.public","-----BEGIN CERTIFICATE-----"); positives.put("crypto.algorithms","ChaCha20 AES-GCM SHA256"); positives.put("crypto.legacy","MD5 AES-ECB");
        positives.put("persist.windows","Software\\Microsoft\\Windows\\CurrentVersion\\Run\\Fixture"); positives.put("persist.unix","android.intent.action.BOOT_COMPLETED"); positives.put("evasion.debug","TracerPid");
        positives.put("evasion.environment","ro.kernel.qemu frida"); positives.put("text.direction","prefix\u202Eexe.fixture"); positives.put("vuln.cve","CVE-2024-12345"); positives.put("vuln.cwe","CWE-787");
        positives.put("vuln.terminology","use-after-free"); positives.put("vuln.input-pattern","${jndi:ldap://fixture.invalid/x}");
        positives.put("encoded.base64",Base64.getEncoder().encodeToString("FOSS binary investigation fixture: not a secret, not a command".getBytes(StandardCharsets.UTF_8)));
        positives.put("id.hex-digest","abcdef0123456789".repeat(4)); positives.put("id.uuid","12345678-1234-5678-abcd-123456789abc");
        require(positives.size() == StringClassifier.RULES.size(),"Every built-in rule needs a semantic positive fixture");
        for (var positive : positives.entrySet()) require(matches(positive.getValue(),positive.getKey()),"Rule failed: " + positive);
        Set<String> ids = new HashSet<>();
        for (var rule : StringClassifier.RULES) {
            require(ids.add(rule.id),"Duplicate rule identifier");
            JSONObject captured = rule.json();
            JSONArray rationales = captured.getJSONArray("categoryRationales");
            require(rationales.length() == rule.groups.size(), "A category lost its captured rationale");
            for (int i = 0; i < rationales.length(); i++) {
                JSONObject reason = rationales.getJSONObject(i);
                require(reason.getString("group").equals(rule.groups.get(i).name()), "Category rationale refers to another group");
                require(!reason.getString("label").isEmpty() && !reason.getString("reason").isEmpty(), "Category explanation is blank");
            }
        }
        StringClassifier.Result mixed = StringClassifier.classify("ptrace CVE-2024-12345 strcpy", () -> false);
        require(mixed.summary("EVASION").contains("tracing") && !mixed.summary("EVASION").contains("CVE identifier"), "An evasion group shows an unrelated first reason");
        require(mixed.summary("VULNERABILITIES").contains("Common Vulnerabilities and Exposures"), "Vulnerability grouping does not explain its actual CVE witness");
        require(StringClassifier.RULES_SHA256.matches("[a-f0-9]{64}"),"Missing rule identity");
        for (String bad : Arrays.asList("999.0.0.1","192.0.2.1:99999","1.2.3.456","v192.0.2.1")) require(!matches(bad,"net.ipv4"),"Invalid IPv4 accepted: " + bad);
        for (String good : Arrays.asList("::1","2001:db8:0:0:0:0:0:1","::ffff:192.0.2.1","[fe80::1%wlan0]:80")) require(matches(good,"net.ipv6"),"Valid IPv6 absent: " + good);
        for (String bad : Arrays.asList("1:2:3","1:2:3:4:5:6:7:8:9","2001:::1","[::1]:99999","::ffff:999.0.0.1")) require(!matches(bad,"net.ipv6"),"Invalid IPv6 accepted: " + bad);
        require(!matches("notpassword=x", "secret.assignment") && !matches("strcpy_wrapper", "api.unsafe-c"),"Token boundaries lost");
        require(!matches("A".repeat(64),"encoded.base64"),"Alphanumeric identifier mislabeled Base64");
        require(!StringClassifier.provenance().getBoolean("vulnerabilityVerified") && !StringClassifier.provenance().getBoolean("networkUsed"),"Classifier implies an unperformed check");
        try { StringClassifier.classify("ptrace ".repeat(2049),() -> false); throw new AssertionError("Match budget silently dropped hits"); } catch (IOException expected) { require(expected.getMessage().contains("2,048"),"Wrong match-budget failure"); }
        try { StringClassifier.classify("https://fixture.invalid",() -> true); throw new AssertionError("Classification ignored cancellation"); } catch (InterruptedIOException expected) {}

        ByteArrayOutputStream blob = new ByteArrayOutputStream(); blob.write(new byte[65533]);
        String unicode = "é😀 https://fixture.invalid/api/v1"; long utf8At = blob.size(); blob.write(unicode.getBytes(StandardCharsets.UTF_8)); blob.write(new byte[5]);
        String le = "https://utf16-le.invalid/研究😀"; if ((blob.size() & 1) == 0) blob.write(0); long leAt = blob.size(); blob.write(le.getBytes(StandardCharsets.UTF_16LE)); blob.write(new byte[6]);
        String be = "https://utf16-be.invalid/api"; long beAt = blob.size(); blob.write(be.getBytes(StandardCharsets.UTF_16BE)); blob.write(new byte[6]);
        String bomOnly = "研究文章"; long bomAt = blob.size(); blob.write(new byte[]{(byte)0xff,(byte)0xfe}); blob.write(bomOnly.getBytes(StandardCharsets.UTF_16LE)); blob.write(new byte[6]);
        blob.write(new byte[]{(byte)0xc0,(byte)0x80,(byte)0xed,(byte)0xa0,(byte)0x80}); blob.write("RECOVER_AFTER_INVALID".getBytes(StandardCharsets.UTF_8)); blob.write(new byte[6]);
        byte[] original = blob.toByteArray(); File input = temporary.resolve("strings.bin").toFile(); Files.write(input.toPath(),original); File spools = temporary.resolve("strings").toFile();
        try (StringDocument document = StringDocument.scan(input,spools,4,() -> false)) {
            EvidenceRows all = document.rows("all"); EvidenceRows.Row u8 = find(all,unicode,"UTF8"), u16le = find(all,le,"UTF16LE"), u16be = find(all,be,"UTF16BE");
            require(u8.physicalOffset == utf8At && u16le.physicalOffset == leAt && u16be.physicalOffset == beAt,"Physical string offsets shifted");
            require(find(all,bomOnly,"UTF16LE").physicalOffset == bomAt+2,"BOM counted as string content"); find(all,"RECOVER_AFTER_INVALID","UTF8");
            for (EvidenceRows.Row value : Arrays.asList(u8,u16le,u16be)) {
                JSONObject fields = value.json();
                byte[] expected = fields.getString("value").getBytes(fields.getString("encoding").equals("UTF8") ? StandardCharsets.UTF_8 : fields.getString("encoding").equals("UTF16LE") ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE);
                require(Arrays.equals(expected,Arrays.copyOfRange(original,value.physicalOffset.intValue(),(int)(value.physicalOffset+value.sizeBytes))),"Raw byte range differs from decoded encoding");
                boolean uriFound = false;
                for (int i = 0; i < fields.getJSONArray("classificationMatches").length(); i++) {
                    JSONObject match = fields.getJSONArray("classificationMatches").getJSONObject(i); if (!match.getString("ruleId").equals("net.uri")) continue;
                    uriFound = true; long at = match.getLong("physicalMatchOffset"), bytes = match.getLong("physicalMatchLengthBytes");
                    require(at >= value.physicalOffset && at+bytes <= value.physicalOffset+value.sizeBytes,"Match range outside text");
                    if (value == u8) require(at == utf8At + "é😀 ".getBytes(StandardCharsets.UTF_8).length,"UTF-16 match index mistaken for UTF-8 byte position");
                }
                require(uriFound,"URI evidence missing");
            }
            require(document.sha256.equals(Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(original))),"Whole input identity differs");
            require(document.metadata().getInt("completeEncodingPasses") == 5 && document.metadata().getJSONObject("groupCounts").getInt("URLS") >= 3,"Scan coverage or groups missing");
            EvidenceRows urls = document.rows("URLS"); StringWriter report = new StringWriter(); urls.writeReport(report,StringClassifier.provenance(),"no matching filter",0,10,() -> false);
            JSONObject exported = new JSONObject(report.toString()); require(exported.getJSONArray("rows").length() == urls.size() && urls.size() > 0,"Filter changed group export scope");
            Files.write(input.toPath(),new byte[]{1}); require(find(all,unicode,"UTF8").name.equals(unicode),"Live input mutation changed acquired string evidence");
            require(Arrays.equals(original,Files.readAllBytes(document.input.toPath())),"Original mutation changed byte-view/extraction source");
        }
        require(Objects.requireNonNull(spools.list()).length == 0,"Closed string spool leaked");
        File fragments = temporary.resolve("long.txt").toFile(); Files.writeString(fragments.toPath(),"a".repeat(StringDocument.FRAGMENT_UNITS)+"尾😀",StandardCharsets.UTF_8);
        try (StringDocument document = StringDocument.scan(fragments,spools,4,() -> false)) {
            EvidenceRows all = document.rows("all"); EvidenceRows.Row tail = find(all,"尾😀","UTF8"); require(tail.json().getInt("fragmentIndex") == 1 && !tail.json().getBoolean("runContinues"),"Short admitted run tail was discarded or split inside a surrogate pair");
        }
        Files.writeString(input.toPath(),("https://fixture.invalid\0").repeat(100)); AtomicInteger cancellation = new AtomicInteger();
        try { StringDocument.scan(input,spools,4,() -> cancellation.incrementAndGet() > 50); throw new AssertionError("Cancelled scan succeeded"); } catch (InterruptedIOException expected) { require(Objects.requireNonNull(spools.list()).length == 0,"Cancelled spool leaked"); }
        File large = temporary.resolve("over-budget.bin").toFile(); try (RandomAccessFile out = new RandomAccessFile(large,"rw")) { out.setLength(StringDocument.MAX_INPUT_BYTES+1); }
        try { StringDocument.scan(large,spools,4,() -> false); throw new AssertionError("Whole-input budget ignored"); } catch (IOException expected) { require(expected.getMessage().contains("256 MiB"),"Wrong whole-input failure"); } large.delete();
        StringBackend backend = new StringBackend(input,spools,"fixture.bin",4); String root = backend.initialRequest(() -> false); EvidenceRows interesting = backend.open(root,() -> false);
        require(interesting.size() > 0 && backend.roots(root,() -> false).size() == StringClassifier.Group.values().length+4,"Provider omitted group navigation"); require(backend.provenance().getJSONObject("input").getString("sha256").length() == 64,"Provider lacks measured input identity"); backend.close();
        System.out.println("String fixtures passed: every rule, validated IP syntax, exact Unicode/byte spans, five-pass identity, overlapping encodings, fragments, groups, complete exports, immutable text/bytes, budgets and cancelled cleanup");
        System.out.println("String catalog: " + StringClassifier.RULES.size() + " rules; SHA-256=" + StringClassifier.RULES_SHA256);
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Validated complete digest set bound to an owned full input snapshot. */
final class HashDocument implements AutoCloseable {
    static final String RHASH_SOURCE="6ea6791024499524cf41f9e0b6b08d456bf4a5e2";
    static final String XXFCLIB_SOURCE="0fc8b4fa12c2c9c97d79fa3a3806667fc1da9b9d";
    static final List<String> ALGORITHMS=Collections.unmodifiableList(Arrays.asList("CRC32","MD4","MD5","SHA1","SHA224","SHA256","SHA384","SHA512"));
    private static final int[] LENGTHS={8,32,32,40,56,64,96,128};
    final InputSnapshot snapshot;
    final JSONObject header;
    final JSONArray digests;
    private final String name;

    private HashDocument(InputSnapshot snapshot,String name,String encoded) throws Exception {
        this.snapshot=snapshot;this.name=name;
        if(encoded==null||encoded.length()>16384) throw new IOException("Hash worker returned no bounded complete report");
        JSONObject report=new JSONObject(encoded);
        if(!report.optBoolean("ok")) throw new IOException(report.optString("error","Hash worker did not complete"));
        if(!report.getString("schema").equals("org.diemobile.whole-file-hashes")||report.getInt("schemaVersion")!=1||report.getInt("adapterVersion")!=1
            ||!report.getString("rhashSource").equals(RHASH_SOURCE)||!report.getString("xxfclibSource").equals(XXFCLIB_SOURCE)) throw new IOException("Hash report engine/schema mismatch");
        if(report.getLong("fileSizeBytes")!=snapshot.size||report.getLong("bytesRead")!=snapshot.size
            ||!report.getBoolean("wholeInputComplete")||report.getInt("passes")!=1) throw new IOException("Hash worker returned incomplete/mismatched input coverage");
        digests=report.getJSONArray("digests");
        if(digests.length()!=ALGORITHMS.size()) throw new IOException("Hash worker omitted or added an algorithm");
        for(int i=0;i<digests.length();i++) {
            JSONObject value=digests.getJSONObject(i);
            if(!value.getString("algorithm").equals(ALGORITHMS.get(i))||!value.getString("hex").matches("[0-9a-f]{"+LENGTHS[i]+"}")) throw new IOException("Hash algorithm order/length/hex mismatch");
        }
        if(!digests.getJSONObject(5).getString("hex").equals(snapshot.sha256)) throw new IOException("Hash worker SHA-256 differs from the complete acquired input");
        report.remove("digests");header=report;
    }

    static HashDocument read(File source,File directory,String name,BinaryRunner runner,BooleanSupplier stop) throws Exception {
        InputSnapshot snapshot=InputSnapshot.acquire(source,directory,1024L*1024*1024,stop);boolean success=false;
        try {
            String report=runner.scan(snapshot.file,stop);EvidenceRows.check(stop);
            HashDocument document=new HashDocument(snapshot,name,report);success=true;return document;
        } finally { if(!success) snapshot.close(); }
    }

    EvidenceRows rows() {
        return new EvidenceRows("whole-file-hashes","Whole-file hashes • "+name) {
            int size() { return ALGORITHMS.size(); }
            Row row(int index,BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);JSONObject value=digests.getJSONObject(index);String algorithm=value.getString("algorithm"),hex=value.getString("hex");
                return new Row(index,algorithm,hex,null,null,new JSONObject().put("algorithm",algorithm).put("hex",hex).put("digestBits",LENGTHS[index]*4)
                    .put("inputBytes",snapshot.size).put("wholeInputComplete",true).put("mechanism",mechanism(index)).put("securityMeaning",security(index))
                    .put("implementation",index==1?"Unmodified RHash 1.4.5 MD4":"Pinned xxfclib streaming context")
                    .put("sourceRevision",index==1?RHASH_SOURCE:XXFCLIB_SOURCE)
                    .put("physicalOffsetMeaning","This computed digest is not stored at a known byte position in the selected file."));
            }
            JSONObject metadata() throws Exception { return provenance(); }
            @Override boolean matches(Row row,String query,BooleanSupplier stop) throws Exception {
                EvidenceRows.check(stop);JSONObject value=digests.getJSONObject(row.index);
                return value.getString("algorithm").toLowerCase(Locale.ROOT).contains(query.replace("-",""))||value.getString("hex").contains(query);
            }
        };
    }

    JSONObject provenance() throws Exception {
        return new JSONObject(header.toString()).put("input",new JSONObject().put("name",name).put("sizeBytes",snapshot.size).put("sha256",snapshot.sha256))
            .put("offsetDomain","Computed digest over all bytes of the exact owned input; no stored digest location is inferred.")
            .put("identityCheck","The native SHA-256 must equal the independently measured Java acquisition SHA-256.")
            .put("scope","Whole-file raw bytes in one native streaming pass. No normalization, PE exclusions, certificate trust, fuzzy hashing, reputation or network lookup.");
    }

    /** The existing Native Tools entry retains its text/JSON switch and selector. */
    String legacyReport(String selector,boolean json) throws Exception {
        String selected=selector.trim().toUpperCase(Locale.ROOT).replace("-","");
        if(!selected.isEmpty()&&!ALGORITHMS.contains(selected)) throw new IOException("Unsupported digest selector; choose CRC32, MD4, MD5, SHA1, SHA224, SHA256, SHA384 or SHA512");
        JSONObject values=new JSONObject();StringBuilder text=new StringBuilder("Whole-file hashes • "+snapshot.size+" bytes\n");
        for(int i=0;i<digests.length();i++) {
            JSONObject value=digests.getJSONObject(i);String algorithm=value.getString("algorithm");
            if(!selected.isEmpty()&&!selected.equals(algorithm)) continue;
            values.put(algorithm,value.getString("hex"));text.append(algorithm).append(": ").append(value.getString("hex")).append('\n');
        }
        if(json) return new JSONObject().put("data",new JSONObject().put("Hash",values)).put("provenance",provenance()).toString();
        return text.append("\nInput SHA-256: ").append(snapshot.sha256).append("\nRHash MD4 source: ").append(RHASH_SOURCE)
            .append("\nxxfclib source: ").append(XXFCLIB_SOURCE).append("\nCRC32 is an error-detection checksum; MD4/MD5/SHA1 have collision weaknesses. No digest authenticates a publisher by itself.\n").toString();
    }

    private static String mechanism(int index) {
        switch(index) {
            case 0: return "CRC-32/ISO-HDLC (ZIP/Ethernet): polynomial 0x04c11db7, init/xorout 0xffffffff, reflected input/output. Eight hex digits encode the final 32-bit value, not byte-swapped storage.";
            case 1: return "MD4, RFC 1320: 128-bit digest, 512-bit blocks and little-endian length/digest encoding. The standard empty-input digest is retained.";
            case 2: return "MD5, RFC 1321: 128-bit digest over padded 512-bit message blocks; hexadecimal preserves the digest byte order.";
            case 3: return "SHA-1, FIPS 180-4: 160-bit digest with 512-bit blocks. This is the raw file digest, distinct from an APK signing-scheme digest.";
            case 4: return "SHA-224, FIPS 180-4: SHA-256-family compression with its own initial state and 224-bit output; it is not a truncated SHA-256 digest.";
            case 5: return "SHA-256, FIPS 180-4: 256-bit digest with 512-bit blocks. The result is checked against the independently measured acquisition digest.";
            case 6: return "SHA-384, FIPS 180-4: SHA-512-family compression with its own initial state and 384-bit output; it is not a truncated SHA-512 digest.";
            default: return "SHA-512, FIPS 180-4: 512-bit digest with 1024-bit message blocks and 64-bit operations; digest bytes are encoded as 128 lowercase hex digits.";
        }
    }
    private static String security(int index) {
        if(index==0) return "Accidental-corruption detection; CRC is not cryptographic and can be deliberately preserved by modified content.";
        if(index<=3) return "Legacy identification/interoperability only. Practical collision weaknesses make this unsuitable as an adversarial authenticity or signature check; prefer SHA-256 or stronger corroboration.";
        return "Content identity comparison against a trusted expected value. A plain digest supplies no publisher authentication, trust or malware verdict.";
    }
    @Override public void close() { snapshot.close(); }
}

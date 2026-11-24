// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Entire-input string candidate scans with an immutable private text spool. */
final class StringDocument implements AutoCloseable {
    static final long MAX_INPUT_BYTES = 256L * 1024 * 1024, MAX_TEXT_UNITS = 16L * 1024 * 1024;
    static final int MAX_RECORDS = 200000, FRAGMENT_UNITS = 8192;
    enum Encoding { UTF8, UTF16LE, UTF16BE }
    private static final class Record {
        final long spool, physical, length, runStart, groups;
        final int alignment, fragment, codePoints;
        final boolean continued;
        final Encoding encoding;
        Record(long spool, long physical, long length, long runStart, long groups, int alignment, int fragment, int codePoints, boolean continued, Encoding encoding) {
            this.spool = spool; this.physical = physical; this.length = length; this.runStart = runStart; this.groups = groups;
            this.alignment = alignment; this.fragment = fragment; this.codePoints = codePoints; this.continued = continued; this.encoding = encoding;
        }
    }
    final File input;
    final long inputSize;
    final int minimumLength;
    final String sha256;
    private final File spoolFile;
    private final RandomAccessFile spool;
    private final InputSnapshot snapshot;
    private final List<Record> records;
    private final long[] groupCounts = new long[StringClassifier.Group.values().length];
    private long textUnits;
    private int interesting;
    private boolean closed;

    static StringDocument scan(File input, File directory, int minimum, BooleanSupplier stop) throws Exception {
        if (!input.isFile() || input.length() > MAX_INPUT_BYTES) throw new IOException("String input unavailable or exceeds the 256 MiB whole-input budget");
        if (minimum < 1 || minimum > 1024) throw new IOException("Minimum string length must be 1–1,024 Unicode code points");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create private string spool directory");
        File path = File.createTempFile("strings-", ".snapshot", directory); boolean success = false; InputSnapshot snapshot = null;
        try {
            snapshot = InputSnapshot.acquire(input,directory,MAX_INPUT_BYTES,stop);
            StringDocument result = new StringDocument(snapshot,path,minimum,stop); success = true; return result;
        } finally { if (!success) { path.delete(); if (snapshot != null) snapshot.close(); } }
    }
    private StringDocument(InputSnapshot snapshot, File path, int minimum, BooleanSupplier stop) throws Exception {
        this.snapshot = snapshot; input = snapshot.file; inputSize = snapshot.size; minimumLength = minimum; spoolFile = path;
        List<Record> values = new ArrayList<>(); String identity = null;
        try (RandomAccessFile output = new RandomAccessFile(path,"rw")) {
            for (int pass = 0; pass < 5; pass++) {
                EvidenceRows.check(stop); Encoding encoding = pass == 0 ? Encoding.UTF8 : pass < 3 ? Encoding.UTF16LE : Encoding.UTF16BE;
                int alignment = pass == 0 ? 0 : (pass-1) % 2;
                try (Cursor cursor = new Cursor(input)) {
                    Run run = new Run(encoding,alignment,output,values,stop);
                    if (encoding == Encoding.UTF8) scanUtf8(cursor,run,stop); else scanUtf16(cursor,run,alignment,encoding == Encoding.UTF16LE,stop);
                    String measured = Inspector.toHex(cursor.digest.digest());
                    if (!snapshot.sha256.equals(measured) || identity != null && !identity.equals(measured)) throw new IOException("Input content changed between acquisition/encoding passes; no combined string result returned");
                    if (identity == null) identity = measured;
                    if (cursor.position != inputSize || input.length() != inputSize) throw new IOException("Input length changed during string acquisition");
                }
            }
            EvidenceRows.check(stop);
        }
        values.sort(Comparator.comparingLong((Record value) -> value.physical).thenComparing(value -> value.encoding).thenComparingInt(value -> value.alignment));
        records = Collections.unmodifiableList(values); sha256 = identity; spool = new RandomAccessFile(path,"r");
        for (Record value : records) {
            if (value.groups != 0) interesting++;
            for (StringClassifier.Group group : StringClassifier.Group.values()) if ((value.groups & group.bit()) != 0) groupCounts[group.ordinal()]++;
        }
    }
    private static final class Cursor implements Closeable {
        final MessageDigest digest;
        final PushbackInputStream input;
        long position;
        Cursor(File source) throws Exception {
            digest = MessageDigest.getInstance("SHA-256"); input = new PushbackInputStream(new BufferedInputStream(new DigestInputStream(new FileInputStream(source),digest),65536),1);
        }
        int read() throws IOException { int value = input.read(); if (value >= 0) position++; return value; }
        void unread(int value) throws IOException { if (value >= 0) { input.unread(value); position--; } }
        public void close() throws IOException { input.close(); }
    }
    private static boolean printable(int codePoint) {
        return (codePoint == '\t' || codePoint >= 0x20) && !(codePoint >= 0x7f && codePoint <= 0x9f)
            && !(codePoint >= 0xd800 && codePoint <= 0xdfff) && codePoint <= 0x10ffff && !(codePoint >= 0xfdd0 && codePoint <= 0xfdef)
            && (codePoint & 0xffff) < 0xfffe && codePoint != 0x2028 && codePoint != 0x2029 && codePoint != 0xfeff;
    }
    private static void scanUtf8(Cursor cursor, Run run, BooleanSupplier stop) throws Exception {
        int first;
        while ((first = cursor.read()) >= 0) {
            if ((cursor.position & 4095) == 1) EvidenceRows.check(stop);
            long at = cursor.position-1; int value = first, following = 0, minimum = 0;
            if (first >= 0xc2 && first <= 0xdf) { value = first & 31; following = 1; minimum = 0x80; }
            else if (first >= 0xe0 && first <= 0xef) { value = first & 15; following = 2; minimum = 0x800; }
            else if (first >= 0xf0 && first <= 0xf4) { value = first & 7; following = 3; minimum = 0x10000; }
            else if (first >= 0x80) value = -1;
            for (int i = 0; i < following; i++) {
                int next = cursor.read(); if (next < 0 || (next & 0xc0) != 0x80) { cursor.unread(next); value = -1; break; } value = value << 6 | next & 63;
            }
            if (value < minimum || value >= 0xd800 && value <= 0xdfff || value > 0x10ffff) value = -1;
            if (printable(value)) run.accept(value,at,(int)(cursor.position-at)); else run.boundary(value == 0xfeff);
        }
        run.boundary(false);
    }
    private static void scanUtf16(Cursor cursor, Run run, int alignment, boolean little, BooleanSupplier stop) throws Exception {
        if (alignment != 0) cursor.read(); int pending = -1; long pendingAt = -1;
        while (true) {
            if ((cursor.position & 4095) < 2) EvidenceRows.check(stop);
            long at = pending < 0 ? cursor.position : pendingAt; int unit;
            if (pending >= 0) { unit = pending; pending = -1; }
            else { int a = cursor.read(), b = cursor.read(); if (a < 0 || b < 0) break; unit = little ? a | b << 8 : a << 8 | b; }
            int value = unit, width = 2;
            if (unit >= 0xd800 && unit <= 0xdbff) {
                long lowAt = cursor.position; int a = cursor.read(), b = cursor.read();
                if (a < 0 || b < 0) { value = -1; }
                else {
                    int low = little ? a | b << 8 : a << 8 | b;
                    if (low >= 0xdc00 && low <= 0xdfff) { value = Character.toCodePoint((char)unit,(char)low); width = 4; }
                    else { pending = low; pendingAt = lowAt; value = -1; }
                }
            }
            if (printable(value)) run.accept(value,at,width); else run.boundary(value == 0xfeff);
        }
        run.boundary(false);
    }
    private final class Run {
        final Encoding encoding;
        final int alignment;
        final RandomAccessFile output;
        final List<Record> values;
        final BooleanSupplier stop;
        final StringBuilder text = new StringBuilder();
        long start, end, runStart;
        int points, fragment;
        boolean anchored, bom, previouslyEmitted;
        Run(Encoding encoding, int alignment, RandomAccessFile output, List<Record> values, BooleanSupplier stop) { this.encoding = encoding; this.alignment = alignment; this.output = output; this.values = values; this.stop = stop; }
        void accept(int point, long at, int width) throws Exception {
            if (text.length() + Character.charCount(point) > FRAGMENT_UNITS) { flush(true); fragment++; }
            if (text.length() == 0) { start = at; if (fragment == 0) runStart = at; }
            text.appendCodePoint(point); points++; end = at + width;
            if (point >= 0x21 && point <= 0x7e) anchored = true;
        }
        void boundary(boolean nextBom) throws Exception { flush(false); points = 0; fragment = 0; anchored = false; previouslyEmitted = false; bom = nextBom; }
        void flush(boolean continues) throws Exception {
            if (text.length() != 0 && (points >= minimumLength || previouslyEmitted) && (encoding == Encoding.UTF8 || anchored || bom || previouslyEmitted)) {
                EvidenceRows.check(stop);
                if (values.size() == MAX_RECORDS || text.length() > MAX_TEXT_UNITS - textUnits) throw new IOException("String result exceeds 200,000 fragments or 16 Mi decoded UTF-16 units; no successful partial result returned");
                String value = text.toString(); StringClassifier.Result classified = StringClassifier.classify(value,stop);
                byte[] encoded = value.getBytes(StandardCharsets.UTF_8); long position = output.getFilePointer();
                output.writeInt(encoded.length); output.write(encoded);
                values.add(new Record(position,start,end-start,runStart,classified.groups,alignment,fragment,points,continues,encoding)); textUnits += text.length(); previouslyEmitted = true;
            }
            text.setLength(0); points = 0; anchored = false;
        }
    }
    int size() { return records.size(); }
    JSONObject metadata() throws JSONException {
        JSONObject counts = new JSONObject(); for (StringClassifier.Group group : StringClassifier.Group.values()) counts.put(group.name(),groupCounts[group.ordinal()]);
        return StringClassifier.provenance().put("sourceSha256",sha256).put("inputBytesScannedPerPass",inputSize).put("completeEncodingPasses",5)
            .put("recordCount",size()).put("interestingRecords",interesting).put("unclassifiedRecords",size()-interesting).put("groupCounts",counts)
            .put("minimumUnicodeCodePoints",minimumLength).put("decodedUtf16Units",textUnits).put("maxFragmentUtf16Units",FRAGMENT_UNITS)
            .put("candidateAdmission", "UTF-8 includes ASCII; strict UTF-8 excludes overlong/surrogate encodings. UTF-16 LE/BE are scanned at both byte alignments; each run/fragment needs an ASCII graphic anchor or preceding BOM, or a previously admitted fragment. Unicode-only UTF-16 without those anchors may be absent.")
            .put("printablePolicy", "Tab and Unicode code points >= U+0020, excluding C0/C1/DEL, surrogates, Unicode noncharacters, BOM and U+2028/U+2029. No assigned-character/category guarantee is inferred. Direction/format controls remain exact in evidence and are shown as escapes in the UI.")
            .put("fragmentation", "Runs longer than 8,192 UTF-16 units are split without splitting surrogate pairs; admitted run tails are retained. Rules are evaluated per fragment, so a pattern crossing a fragment boundary may be missed.")
            .put("interpretation", "Decoded string candidates, not proven format fields or executed behavior. Encodings can overlap and produce several readings of the same bytes. Group counts overlap and are not additive. No unpacking, decryption, emulation or network lookup.")
            .put("ordering", "Physical offset, encoding, alignment").put("maxInputBytes",MAX_INPUT_BYTES).put("maxRecords",MAX_RECORDS).put("maxDecodedUtf16Units",MAX_TEXT_UNITS);
    }
    EvidenceRows rows(String group) throws Exception {
        final long wanted;
        if (group.equals("all") || group.equals("interesting") || group.equals("unclassified")) wanted = 0;
        else try { wanted = StringClassifier.Group.valueOf(group).bit(); } catch (IllegalArgumentException e) { throw new IOException("Unknown string group",e); }
        int[] included = new int[records.size()]; int count = 0;
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            if (group.equals("all") || group.equals("interesting") && record.groups != 0 || group.equals("unclassified") && record.groups == 0 || (record.groups & wanted) != 0) included[count++] = i;
        }
        final int[] indices = Arrays.copyOf(included,count);
        return new EvidenceRows("strings-"+group,"Strings • " + group) {
            int size() { return indices.length; }
            Row row(int i, BooleanSupplier stop) throws Exception { return StringDocument.this.row(indices[i],stop,group); }
            JSONObject metadata() throws Exception { return StringDocument.this.metadata().put("selectedGroup",group).put("selectedGroupRecords",indices.length); }
        };
    }
    private EvidenceRows.Row row(int index, BooleanSupplier stop, String selectedGroup) throws Exception {
        EvidenceRows.check(stop); if (closed) throw new IOException("String snapshot closed"); Record record = records.get(index);
        spool.seek(record.spool); int length = spool.readInt(); if (length < 0 || length > FRAGMENT_UNITS*4) throw new IOException("Invalid private string snapshot");
        byte[] encoded = new byte[length]; spool.readFully(encoded); String value = new String(encoded,StandardCharsets.UTF_8); StringClassifier.Result classified = StringClassifier.classify(value,stop);
        JSONObject fields = new JSONObject().put("value",value).put("encoding",record.encoding.name()).put("alignment",record.alignment).put("unicodeCodePoints",record.codePoints).put("utf16Units",value.length())
            .put("physicalTextOffset",record.physical).put("textByteLength",record.length).put("runStartPhysicalOffset",record.runStart).put("fragmentIndex",record.fragment).put("runContinues",record.continued)
            .put("groups",classified.groupNames()).put("categoryRationales",classified.categoryRationales())
            .put("classificationMatches",classified.json(value, units -> record.physical + (record.encoding == Encoding.UTF8 ? value.substring(0,units).getBytes(StandardCharsets.UTF_8).length : units*2L)))
            .put("locationMeaning", "Exact bytes encoding this decoded text fragment; excludes a separator/terminator or BOM. Byte order/alignment are decoding candidates, not format validation.");
        return new EvidenceRows.Row(index,value,record.encoding + " • " + record.codePoints + " code points • " + classified.groupNames()
                + "\n" + classified.summary(selectedGroup),record.physical,record.length,fields);
    }
    @Override public void close() throws IOException { if (!closed) { closed = true; try { spool.close(); } finally { spoolFile.delete(); snapshot.close(); } } }
}

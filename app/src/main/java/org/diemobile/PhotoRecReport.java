// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.function.BooleanSupplier;

/** Lossless dictionary transport; rejected occurrences expand only when requested. */
final class PhotoRecReport {
    static final int MAX_CANDIDATES = 250000;
    static final long MAX_INPUT_BYTES = 256L * 1024 * 1024;
    static final long MAX_DATA_BYTES = 64L * 1024 * 1024;
    static final String MPEG_POLICY =
            "standalone-header-retained/active-mpeg-header-ignored/bounded-owner-only";
    static final String MPEG_DATA_MODE =
            "upstream-active-mpeg-header-ignored/data-callback-suppressed";

    static boolean supportedAdapter(String value) {
        return value.equals("diemobile-photorec-2")
                || value.equals("diemobile-photorec-3")
                || value.equals("diemobile-photorec-4")
                || value.equals("diemobile-photorec-5")
                || value.equals("diemobile-photorec-6");
    }

    static final String[] REJECTED_FIELDS = {
        "headerCalculatedSizeBytes",
        "minimumFileSizeBytes",
        "dataCheckAvailable",
        "fileCheckAvailable",
        "upstreamTime",
        "upstreamFlags",
        "upstreamOffsetOk",
        "upstreamOffsetError",
        "upstreamExtra",
        "upstreamCheckpointOffset",
        "upstreamCheckpointStatus",
        "upstreamDataCheckTemporary",
        "dataCheckFileSizeCounterBytes",
        "reportedExtension"
    };
    private static final BigInteger UINT64_MAX = new BigInteger("18446744073709551615");
    private final JSONArray rows, patterns, states;
    private final JSONObject metadata;
    private final long sourceSize;
    private final boolean suppressionSupported;

    PhotoRecReport(JSONObject report) throws Exception {
        this(report, () -> false);
    }

    PhotoRecReport(JSONObject report, BooleanSupplier cancelled) throws Exception {
        if (!report.getString("schema").equals("org.diemobile.photorec-evidence")
                || report.getInt("schemaVersion") != 2
                || !supportedAdapter(report.getString("adapter")))
            throw new IOException("Unsupported PhotoRec compact transport version");
        rows = report.getJSONArray("rows");
        suppressionSupported = report.getString("adapter").equals("diemobile-photorec-6");
        if (suppressionSupported) {
            if (integer(report.get("maximumCandidateDataBufferBytes")) != MAX_DATA_BYTES
                    || integer(report.get("maximumCallbackBufferWorkBytes")) != 256L * 1024 * 1024
                    || !report.getString("mpegContinuationPolicy").equals(MPEG_POLICY))
                throw new IOException(
                        "PhotoRec streaming work or MPEG continuation policy differs");
            integer(report.get("dataCallbacksSuppressedCount"));
        } else if (report.has("maximumCandidateDataBufferBytes")
                && report.getLong("maximumCandidateDataBufferBytes") != 8L * 1024 * 1024)
            throw new IOException("Legacy PhotoRec capture changed its data callback work limit");
        metadata = new JSONObject();
        Iterator<String> keys = report.keys();
        while (keys.hasNext()) {
            EvidenceRows.check(cancelled);
            String key = keys.next();
            if (!key.equals("rows")
                    && !key.equals("patterns")
                    && !key.equals("rejectedRecoveryStates")) metadata.put(key, report.get(key));
        }
        patterns = report.getJSONArray("patterns");
        states = report.getJSONArray("rejectedRecoveryStates");
        sourceSize = report.getLong("fileSizeBytes");
        boolean streaming = !report.getString("adapter").equals("diemobile-photorec-2");
        long maximum = streaming ? MAX_INPUT_BYTES : 64L * 1024 * 1024;
        if (sourceSize < 0
                || sourceSize > maximum
                || report.getLong("maximumInputBytes") != maximum)
            throw new IOException("PhotoRec source exceeds its adapter input contract");
        if (streaming
                && (!report.getString("inputReadMode").equals("bounded-pread-windows")
                        || report.getBoolean("inputSizedBufferAllocation")
                        || !report.getBoolean("ownedDescriptorDuplicate")
                        || !report.getBoolean("callerDescriptorPositionPreserved")
                        || report.getLong("maximumInputReadBufferBytes")
                                != ((report.getString("adapter").equals("diemobile-photorec-4")
                                                || report.getString("adapter")
                                                        .equals("diemobile-photorec-5")
                                                || suppressionSupported)
                                        ? 1376256
                                        : 1310720)
                        || report.getLong("scanReadBufferBytes") != 1048576
                        || report.getInt("headerCacheWindows") != 2
                        || report.getLong("headerCacheWindowBytes") != 131072
                        || report.getLong("sourceReadBytes") < sourceSize
                        || report.getLong("sourceReadOperations")
                                < (sourceSize + 1048575) / 1048576))
            throw new IOException(
                    "PhotoRec bounded input-window/descriptor accounting differs from its"
                            + " contract");

        if (rows.length() > MAX_CANDIDATES
                || states.length() > rows.length()
                || report.getInt("rejectedRecoveryStateCount") != states.length())
            throw new IOException("PhotoRec compact transport exceeds its record/state budget");
        for (int i = 0; i < states.length(); i++) {
            EvidenceRows.check(cancelled);
            validateState(states.getJSONArray(i));
        }
    }

    JSONObject row(int ordinal) throws Exception {
        Object value = rows.get(ordinal);
        if (value instanceof JSONObject) return (JSONObject) value;
        if (!(value instanceof JSONArray))
            throw new IOException(
                    "PhotoRec row is neither an original object nor a compact occurrence");
        JSONArray tuple = (JSONArray) value;
        if (tuple.length() != 3)
            throw new IOException("PhotoRec compact occurrence must contain three exact integers");
        int patternIndex = integer(tuple.get(0)),
                magic = integer(tuple.get(1)),
                stateIndex = integer(tuple.get(2));
        if (patternIndex >= patterns.length() || stateIndex >= states.length())
            throw new IOException(
                    "PhotoRec compact pattern/state selector is outside its dictionary");
        JSONObject pattern = patterns.getJSONObject(patternIndex);
        long start = (long) magic - pattern.getLong("magicRelativeOffset");
        if (start < 0 || magic >= sourceSize || !pattern.getBoolean("searched"))
            throw new IOException("PhotoRec compact occurrence is outside the searched source");
        long available = Math.min(65536, sourceSize - start);
        return new JSONObject()
                .put("name", pattern.getString("name"))
                .put("description", pattern.getString("description"))
                .put("patternIndex", patternIndex)
                .put("magicOffset", magic)
                .put("physicalOffset", start)
                .put("candidateStartPhysicalOffset", start)
                .put("magicRelativeOffset", pattern.getLong("magicRelativeOffset"))
                .put("headerAccepted", false)
                .put("parserAccepted", false)
                .put("parserStatus", "header-rejected")
                .put("reportedSizeBytes", JSONObject.NULL)
                .put("fileCheckExecuted", false)
                .put("renameCheckExecuted", false)
                .put("headerWindowSourceBytes", available)
                .put("headerWindowZeroPaddingBytes", 65536 - available)
                .put("rejectedRecoveryStateIndex", stateIndex)
                .put("rejectedRecoveryState", states.getJSONArray(stateIndex));
    }

    boolean compact(int ordinal) throws Exception {
        return rows.get(ordinal) instanceof JSONArray;
    }

    /** Optional data work may be omitted only with an earlier, independently bounded MPEG owner. */
    boolean validateDataRow(int ordinal, JSONObject row) throws Exception {
        boolean suppressed = !row.isNull("dataCheckSuppressedByCandidate");
        if (!suppressionSupported) {
            if (suppressed || row.optString("dataCheckMode").equals(MPEG_DATA_MODE))
                throw new IOException(
                        "Legacy PhotoRec capture cannot suppress MPEG data callbacks");
            return false;
        }
        if (!row.getBoolean("headerAccepted")) {
            if (suppressed || row.optString("dataCheckMode").equals(MPEG_DATA_MODE))
                throw new IOException("Rejected PhotoRec header cannot claim a MPEG owner");
            return false;
        }
        if (integer(row.get("dataCheckSourceBytesProcessed")) > MAX_DATA_BYTES)
            throw new IOException("PhotoRec data callback work differs from its budget");
        if (!suppressed) {
            if (row.getString("dataCheckMode").equals(MPEG_DATA_MODE))
                throw new IOException("Un-suppressed PhotoRec row cannot claim a MPEG owner");
            if (!row.getBoolean("dataCheckExecuted")) {
                if (!row.isNull("dataCheckStatus")
                        || row.getLong("dataCheckSourceBytesProcessed") != 0
                        || row.getBoolean("dataCheckAvailable")
                                && !row.getBoolean("dataCheckLimited")
                                && !outsideDataContract(row))
                    throw new IOException(
                            "PhotoRec available data work was omitted without a bounded owner or"
                                + " explicit limit");
            }
            return false;
        }
        int ownerIndex = integer(row.get("dataCheckSuppressedByCandidate"));
        if (ownerIndex >= ordinal)
            throw new IOException("PhotoRec MPEG owner must be an earlier captured candidate");
        JSONObject owner = row(ownerIndex);
        JSONObject pattern = patterns.getJSONObject(integer(row.get("patternIndex")));
        long start = row.getLong("physicalOffset"), ownerStart = owner.getLong("physicalOffset");
        long ownerSize = owner.isNull("reportedSizeBytes") ? 0 : owner.getLong("reportedSizeBytes");
        if (!row.getString("name").equals("mpg")
                || !row.getString("dataCheckMode").equals(MPEG_DATA_MODE)
                || !pattern.getString("magicHex").equals("000001ba")
                || pattern.getLong("magicRelativeOffset") != 0
                || row.getLong("magicOffset") != start
                || !row.getBoolean("dataCheckAvailable")
                || row.getBoolean("dataCheckExecuted")
                || row.getBoolean("dataCheckLimited")
                || !row.isNull("dataCheckLimitReason")
                || !row.isNull("dataCheckStatus")
                || !row.isNull("reportedSizeBytes")
                || row.getLong("dataCheckSourceBytesProcessed") != 0
                || row.getLong("dataCheckFileSizeCounterBytes") != 0
                || row.getLong("finalCalculatedSizeBytes")
                        != row.getLong("headerCalculatedSizeBytes")
                || !owner.getString("name").equals("mpg")
                || !owner.getBoolean("headerAccepted")
                || !owner.getBoolean("dataCheckExecuted")
                || owner.getBoolean("dataCheckLimited")
                || owner.getInt("dataCheckStatus") != 2
                || owner.getLong("upstreamOffsetError") != 0
                || owner.getLong("finalCalculatedSizeBytes") != ownerSize
                || ownerStart < 0
                || ownerSize < 4
                || ownerSize > sourceSize - ownerStart
                || start <= ownerStart
                || start - ownerStart > ownerSize - 4)
            throw new IOException("PhotoRec MPEG suppression lacks an independent bounded owner");
        return true;
    }

    private static boolean outsideDataContract(JSONObject row) throws Exception {
        Object value = row.get("headerCalculatedSizeBytes");
        if (!(value instanceof String)) return false;
        String text = (String) value;
        if (text.length() > 20 || !text.matches("0|[1-9][0-9]*")) return false;
        BigInteger size = new BigInteger(text);
        // Pinned filegen.h, POSIX/non-DJGPP PHOTOREC_MAX_FILE_SIZE; probe.cpp
        // preserves oversized header state without invoking an invalid callback.
        return size.compareTo(BigInteger.ONE.shiftLeft(41).subtract(BigInteger.ONE)) >= 0
                && size.compareTo(UINT64_MAX) <= 0
                && row.getString("dataCheckMode").equals("not-run")
                && row.getString("parserStatus")
                        .equals("header-accepted/calculated-size-outside-upstream-data-contract");
    }

    /** Complete original indexed evidence plus the parent's owned-input identity. */
    void writeCapture(Writer output, JSONObject provenance, BooleanSupplier cancelled)
            throws Exception {
        output.write(
                "{\"schema\":\"org.diemobile.photorec-capture\",\"schemaVersion\":1,\"createdAt\":");
        EvidenceJson.quoted(output, java.time.Instant.now().toString(), cancelled);
        output.write(",\"provenance\":");
        EvidenceJson.write(output, provenance, cancelled);
        output.write(",\"engineReportMetadata\":");
        EvidenceJson.write(output, metadata, cancelled);
        output.write(",\"patterns\":");
        writeArray(output, patterns, cancelled);
        output.write(",\"rejectedRecoveryStates\":");
        writeArray(output, states, cancelled);
        output.write(",\"rows\":");
        writeArray(output, rows, cancelled);
        EvidenceRows.check(cancelled);
        output.write('}');
    }

    private static void writeArray(Writer output, JSONArray values, BooleanSupplier cancelled)
            throws Exception {
        output.write('[');
        for (int i = 0; i < values.length(); i++) {
            EvidenceRows.check(cancelled);
            if (i > 0) output.write(',');
            EvidenceJson.write(output, values.get(i), cancelled);
        }
        output.write(']');
    }

    private static int integer(Object value) throws IOException {
        if (!(value instanceof Integer) && !(value instanceof Long))
            throw new IOException("PhotoRec compact selector is not an exact integer");
        long number = ((Number) value).longValue();
        if (number < 0 || number > Integer.MAX_VALUE)
            throw new IOException("PhotoRec compact selector is out of range");
        return (int) number;
    }

    private static void validateState(JSONArray state) throws Exception {
        if (state.length() != REJECTED_FIELDS.length)
            throw new IOException("PhotoRec recovery-state tuple is incomplete");
        for (int i : new int[] {0, 1, 6, 7, 8, 9, 12}) {
            Object value = state.get(i);
            if (!(value instanceof String))
                throw new IOException("PhotoRec uint64 state lost its decimal-string type");
            String text = (String) value;
            if (text.length() > 20
                    || !text.matches("0|[1-9][0-9]*")
                    || new BigInteger(text).compareTo(UINT64_MAX) > 0)
                throw new IOException("PhotoRec raw unsigned state is not an exact uint64");
        }
        if (!(state.get(2) instanceof Boolean) || !(state.get(3) instanceof Boolean))
            throw new IOException("PhotoRec callback availability state is not boolean");
        for (int i : new int[] {4, 5, 10, 11}) {
            Object value = state.get(i);
            if (!(value instanceof Integer) && !(value instanceof Long))
                throw new IOException("PhotoRec raw integral state was rounded or changed type");
            long number = ((Number) value).longValue();
            if (i != 4 && (number < 0 || number > 0xffffffffL))
                throw new IOException("PhotoRec uint32 state is out of range");
        }
        if (!state.isNull(13) && !(state.get(13) instanceof String))
            throw new IOException("PhotoRec extension state is not text/null");
    }
}

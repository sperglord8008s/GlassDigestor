// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.IOException;

/** Scope validation for the single reviewed upstream PDF file-check module. */
final class PhotoRecFileChecks {
    static final long MAX_READ = 8L * 1024 * 1024;

    static boolean enabled(JSONObject report) {
        return report.optString("adapter").equals("diemobile-photorec-4")
                || partialMetadataSupported(report);
    }

    static boolean partialMetadataSupported(JSONObject report) {
        return report.optString("adapter").equals("diemobile-photorec-5")
                || report.optString("adapter").equals("diemobile-photorec-6");
    }

    private static boolean partialMetadataResult(JSONObject row, JSONObject report)
            throws Exception {
        if (!partialMetadataSupported(report) || !row.optBoolean("fileCheckMetadataReadLimited"))
            return false;
        String reason = row.optString("fileCheckFailureReason");
        return row.getBoolean("fileCheckExecuted")
                && row.getBoolean("fileCheckReadBudgetLimited")
                && row.getBoolean("fileCheckBoundaryEstablished")
                && (reason.equals("per-candidate-file-read-budget")
                        || reason.equals("aggregate-file-read-budget"))
                && !row.isNull("reportedSizeBytes")
                && row.getLong("reportedSizeBytes") > 0
                && row.getLong("fileCheckSizeBytes") == row.getLong("reportedSizeBytes");
    }

    static void validateReport(
            JSONObject report, int attempted, int executed, int limited, int metadataLimited)
            throws Exception {
        if (!enabled(report)) {
            if (report.getBoolean("fileCheckCallbacksExecuted")
                    || attempted != 0
                    || executed != 0
                    || limited != 0
                    || metadataLimited != 0)
                throw new IOException("Legacy PhotoRec capture cannot execute file checks");
            return;
        }
        if (partialMetadataSupported(report)) {
            if (!report.getString("fileCheckBoundaryPolicy")
                            .equals(
                                    "native-positive-footer-survives-optional-post-footer-read-limit")
                    || report.getInt("fileCheckMetadataReadLimitedCount") != metadataLimited)
                throw new IOException("PhotoRec footer/optional-metadata policy or counts differ");
        } else if (metadataLimited != 0)
            throw new IOException("Legacy PDF file checks cannot retain partial metadata extents");
        if (!report.getString("fileCheckCallbackScope").equals("pdf-only/read-only-relative-stdio")
                || report.getLong("maximumCandidateFileReadBytes") != MAX_READ
                || report.getLong("fileCheckReadBufferBytes") != 65536
                || report.getInt("fileCheckCandidatesAttempted") != attempted
                || report.getInt("fileCheckCandidatesExecuted") != executed
                || report.getInt("fileCheckCandidatesLimited") != limited
                || report.getBoolean("fileCheckCallbacksExecuted") != (executed > 0))
            throw new IOException(
                    "PhotoRec PDF file-check scope or execution counts differ from its rows");
    }

    static void validateRow(JSONObject row, JSONObject report) throws Exception {
        boolean enabled = enabled(report);
        boolean attempted = row.optBoolean("fileCheckAttempted");
        boolean executed = row.getBoolean("fileCheckExecuted");
        boolean failed = row.has("fileCheckFailureReason") && !row.isNull("fileCheckFailureReason");
        boolean expected =
                enabled
                        && row.getString("name").equals("pdf")
                        && row.getBoolean("headerAccepted")
                        && row.getBoolean("fileCheckAvailable");
        if (enabled && expected != attempted)
            throw new IOException(
                    "Reviewed PDF file-check attempt was omitted or added outside its scope");
        if (!attempted && !executed && !failed) {
            if (row.optBoolean("fileCheckMetadataReadLimited")
                    || row.optBoolean("fileCheckBoundaryEstablished"))
                throw new IOException(
                        "Unexecuted PDF check cannot claim a footer or partial metadata");
            return;
        }
        if (!enabled
                || !attempted
                || !row.getString("name").equals("pdf")
                || !row.getBoolean("headerAccepted")
                || !row.getBoolean("fileCheckAvailable")
                || executed
                        && !row.getString("fileCheckMode")
                                .equals("upstream-pdf/read-only-relative-stdio")
                || !executed && !row.getString("fileCheckMode").equals("not-run")
                || row.getLong("fileCheckSourceBytesProcessed") < 0
                || row.getLong("fileCheckSourceBytesProcessed") > MAX_READ)
            throw new IOException("PhotoRec file check escaped its reviewed PDF/read-only scope");
        boolean partial = partialMetadataResult(row, report);
        if (row.optBoolean("fileCheckMetadataReadLimited") && !partial)
            throw new IOException("PDF partial metadata claim has no established native footer");
        if (partialMetadataSupported(report)
                && attempted
                && row.getBoolean("fileCheckBoundaryEstablished")
                        != !row.isNull("reportedSizeBytes"))
            throw new IOException("PDF native footer flag differs from its recovered extent");
        if ((!executed || failed && !partial) && !row.isNull("reportedSizeBytes"))
            throw new IOException(
                    "Failed/unexecuted PDF file check cannot establish a recovered extent");
    }

    static boolean supportsExtent(JSONObject row, long count, JSONObject report) throws Exception {
        return row.getBoolean("fileCheckExecuted")
                && (row.isNull("fileCheckFailureReason") || partialMetadataResult(row, report))
                && row.getLong("fileCheckSizeBytes") == count;
    }
}

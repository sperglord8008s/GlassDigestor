// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Entire-blob signatures retain overlaps, parser failures and unknown extents. */
class CarvingBackend extends InvestigationBackend {
  static final long MAX_BYTES = 64L * 1024 * 1024;
  private final File root, directory;
  private final String name, engineName, schema, sourceRevision;
  private final boolean photoRec;
  private final BinaryRunner runner;
  private final InputSnapshot borrowedInput;
  private InputSnapshot snapshot;
  private JSONObject header;
  private JSONArray candidates, patterns;
  private PhotoRecReport photoRecReport;
  private static final String[] PHOTO_REC_REJECTED_FIELDS = PhotoRecReport.REJECTED_FIELDS;

  CarvingBackend(
      File root,
      File directory,
      String name,
      BinaryRunner runner,
      String engineName,
      String schema,
      String sourceRevision) {
    this(root, directory, name, runner, engineName, schema, sourceRevision, null);
  }

  CarvingBackend(
      File root,
      File directory,
      String name,
      BinaryRunner runner,
      String engineName,
      String schema,
      String sourceRevision,
      InputSnapshot borrowedInput) {
    this.borrowedInput = borrowedInput;
    this.root = root;
    this.directory = directory;
    this.name = name;
    this.runner = runner;
    this.engineName = engineName;
    this.schema = schema;
    this.sourceRevision = sourceRevision;
    this.photoRec = engineName.equals("PhotoRec");
  }

  long maximumInputBytes() {
    return MAX_BYTES;
  }

  private void acquire(BooleanSupplier stop) throws Exception {
    if (header != null) return;
    InputSnapshot input;
    if (borrowedInput == null)
      input = InputSnapshot.acquire(root, directory, maximumInputBytes(), stop);
    else {
      if (borrowedInput.size > maximumInputBytes())
        throw new IOException("Borrowed input exceeds carving budget");
      borrowedInput.checkIdentity(stop);
      input = borrowedInput;
    }
    boolean success = false;
    try {
      String encoded = runner.scan(input.file, stop);
      EvidenceRows.check(stop);
      if (encoded == null || encoded.length() > 32 * 1024 * 1024)
        throw new IOException("Binary worker returned no report or exceeded 32 Mi-characters");
      JSONObject response = new JSONObject(encoded);
      if (!response.optBoolean("ok"))
        throw new IOException(
            response.optString("error", "Binary worker failed without a diagnostic"));
      if (!response.optString("schema").equals(schema)
          || response.optInt("schemaVersion") != (photoRec ? 2 : 1)
          || response.getLong("fileSizeBytes") != input.size
          || !response.getString("sourceRevision").equals(sourceRevision))
        throw new IOException("Binary worker report schema/input/source mismatch");
      JSONArray rows = response.getJSONArray("rows"), catalog = response.getJSONArray("patterns");
      if (rows.length() > (photoRec ? PhotoRecReport.MAX_CANDIDATES : 20000)
          || catalog.length() > 10000
          || rows.length() != response.getInt("candidateCount")
          || catalog.length() != response.getInt("patternCount"))
        throw new IOException(
            "Binary worker exceeded budgets or returned incomplete candidate/catalog" + " counts");
      if (!response.getBoolean("fullBlobSearch")
          || response.getBoolean("nestedResultsPruned")
          || response.getBoolean("unknownExtentsInferred")
          || response.getBoolean("externalExtractorsExecuted"))
        throw new IOException(
            "Binary worker analysis mode differs from the requested read-only"
                + " complete-blob scope");
      if (photoRec
          && ((!PhotoRecFileChecks.enabled(response)
                  && response.getBoolean("fileCheckCallbacksExecuted"))
              || response.getBoolean("renameCallbacksExecuted")
              || response.getBoolean("filesystemRecoveryExecuted")
              || !response.getBoolean("safeHeaderOnly")
              || response.getBoolean("auxiliarySignatureFiles")))
        throw new IOException(
            "PhotoRec worker scope differs from the requested buffer-only analysis");
      if (photoRec) validatePhotoRecCatalog(response, catalog);
      else validateBinwalkInput(response);
      PhotoRecReport decoded = photoRec ? new PhotoRecReport(response, stop) : null;
      int limitedDataCandidates = 0, compactRejectedCandidates = 0, suppressedDataCandidates = 0;
      int fileAttempted = 0, fileExecuted = 0, fileLimited = 0, fileMetadataLimited = 0;
      for (int i = 0; i < rows.length(); i++) {
        EvidenceRows.check(stop);
        JSONObject row = photoRec ? decoded.row(i) : rows.getJSONObject(i);
        if (photoRec && decoded.compact(i)) compactRejectedCandidates++;
        if (photoRec && row.optBoolean("dataCheckLimited")) limitedDataCandidates++;
        if (photoRec) {
          PhotoRecFileChecks.validateRow(row, response);
          if (decoded.validateDataRow(i, row)) suppressedDataCandidates++;
          if (row.optBoolean("fileCheckAttempted")) fileAttempted++;
          if (row.getBoolean("fileCheckExecuted")) fileExecuted++;
          if (row.optBoolean("fileCheckMetadataReadLimited")) fileMetadataLimited++;
          if (row.has("fileCheckFailureReason") && !row.isNull("fileCheckFailureReason"))
            fileLimited++;
        }
        long offset = row.getLong("physicalOffset");
        if (offset < 0 || offset >= input.size)
          throw new IOException(engineName + " candidate location outside input");
        if (photoRec
            && (row.getBoolean("renameCheckExecuted")
                || row.getBoolean("headerAccepted") != row.getBoolean("parserAccepted")))
          throw new IOException("PhotoRec candidate callback scope mismatch");
        long magic = row.getLong("magicOffset");
        int pattern = row.getInt("patternIndex");
        if (magic < 0 || magic >= input.size || pattern < 0 || pattern >= catalog.length())
          throw new IOException(engineName + " magic/pattern selector outside its declared scope");
        if (photoRec) {
          if (row.has("rejectedRecoveryState")
              && (row.getBoolean("headerAccepted")
                  || row.getJSONArray("rejectedRecoveryState").length()
                      != PHOTO_REC_REJECTED_FIELDS.length))
            throw new IOException(
                "PhotoRec compact rejected state differs from its declared field" + " dictionary");
          JSONObject signature = catalog.getJSONObject(pattern);
          long relative = signature.getLong("magicRelativeOffset");
          long derived = magic - relative;
          if (!signature.getBoolean("searched")
              || !signature.getString("name").equals(row.getString("name"))
              || row.getLong("magicRelativeOffset") != relative
              || signature.getLong("magicSizeBytes") > input.size - magic)
            throw new IOException("PhotoRec candidate differs from its searched signature");
          if (derived < 0) {
            if (!row.isNull("candidateStartPhysicalOffset")
                || row.getLong("derivedCandidateStart") != derived
                || offset != magic
                || row.getBoolean("headerAccepted")
                || !row.isNull("reportedSizeBytes"))
              throw new IOException("PhotoRec negative-start witness was reinterpreted as a file");
          } else if (row.getLong("candidateStartPhysicalOffset") != derived || offset != derived)
            throw new IOException(
                "PhotoRec relative magic does not identify the reported candidate" + " start");
        }
        if (!row.isNull("reportedSizeBytes")) {
          long count = row.getLong("reportedSizeBytes");
          if (photoRec) {
            boolean established =
                row.optBoolean("fileCheckAttempted")
                    ? PhotoRecFileChecks.supportsExtent(row, count, response)
                    : row.getBoolean("dataCheckExecuted")
                        && !row.getBoolean("dataCheckLimited")
                        && row.getInt("dataCheckStatus") == 2
                        && row.getLong("finalCalculatedSizeBytes") == count;
            if (!established
                || row.getLong("upstreamOffsetError") != 0
                || row.getLong("minimumFileSizeBytes") > count
                || count > catalog.getJSONObject(pattern).getLong("upstreamMaximumFileSizeBytes"))
              throw new IOException(
                  "PhotoRec extraction extent lacks a successful bounded"
                      + " data-stop or reviewed PDF file-check witness");
          }
          if (count <= 0 || count > input.size - offset || !row.getBoolean("parserAccepted"))
            throw new IOException(engineName + " extent is not a bounded accepted parser result");
        }
      }
      if (photoRec)
        PhotoRecFileChecks.validateReport(
            response, fileAttempted, fileExecuted, fileLimited, fileMetadataLimited);
      if (photoRec
          && (response.getInt("dataCallbacksLimitedCount") != limitedDataCandidates
              || response.getLong("callbackBufferWorkBytes") < 0
              || response.getLong("callbackBufferWorkBytes") > 256L * 1024 * 1024))
        throw new IOException(
            "PhotoRec data-work counts or partial coverage differ from returned" + " candidates");
      if (photoRec && response.getInt("compactRejectedCandidateCount") != compactRejectedCandidates)
        throw new IOException(
            "PhotoRec compact rejected occurrence count differs from the original" + " rows");
      if (photoRec
          && response.getString("adapter").equals("diemobile-photorec-6")
          && response.getInt("dataCallbacksSuppressedCount") != suppressedDataCandidates)
        throw new IOException("PhotoRec MPEG suppression count differs from its captured rows");
      photoRecReport = decoded;
      response.remove("rows");
      response.remove("patterns");
      if (photoRec) response.remove("rejectedRecoveryStates");
      header = response;
      patterns = catalog;
      candidates = rows;
      snapshot = input;
      success = true;
    } finally {
      if (!success && borrowedInput == null) input.close();
    }
  }

  private JSONObject candidate(int ordinal) throws Exception {
    return photoRec ? photoRecReport.row(ordinal) : candidates.getJSONObject(ordinal);
  }

  private static void validateBinwalkInput(JSONObject response) throws Exception {
    // Adapter 1 captures predate input-IO metadata. Keep their evidence
    // readable without attributing later adapters' guarantees to them.
    if (!response.has("adapter")) return;
    long size = response.getLong("fileSizeBytes");
    long operations = response.getLong("sourceReadOperations");
    String adapter = response.getString("adapter");
    long deadline = binwalkDeadlineInteger(response, "cooperativeDeadlineMs");
    boolean configurable = adapter.equals("diemobile-binwalk-3");
    if ((!configurable && !adapter.equals("diemobile-binwalk-2"))
        || (!configurable && deadline != 30000)
        || (configurable
            && (deadline < 1000
                || deadline > 1800000
                || deadline % 1000 != 0
                || binwalkDeadlineInteger(response, "requestedDeadlineMs") != deadline
                || binwalkDeadlineInteger(response, "minimumCooperativeDeadlineMs") != 1000
                || binwalkDeadlineInteger(response, "maximumCooperativeDeadlineMs") != 1800000))
        || size < 0
        || size > MAX_BYTES
        || response.getLong("maxInputBytes") != MAX_BYTES
        || !response.getString("inputReadMode").equals("owned-bounded-pread-copy")
        || !response.getBoolean("inputSizedBufferAllocation")
        || response.getLong("maximumInputReadBufferBytes") != MAX_BYTES
        || response.getLong("inputReadBufferBytes") != size
        || response.getLong("maximumSourceReadRequestBytes") != 65536
        || response.getLong("sourceReadBytes") != size
        || operations < (size + 65535) / 65536
        || operations > size
        || !response.getBoolean("ownedDescriptorDuplicate")
        || !response.getBoolean("callerDescriptorPositionPreserved")
        || !response.getBoolean("sourceMetadataUnchanged")
        || response.getBoolean("parserCallbacksPreemptible"))
      throw new IOException("Binwalk input/descriptor/budget accounting differs from its contract");
    if (configurable && response.has("worker")) {
      JSONObject worker = response.getJSONObject("worker");
      if (!worker.getString("engine").equals("binwalk")
          || binwalkDeadlineInteger(worker, "analysisDeadlineMs") != deadline
          || binwalkDeadlineInteger(worker, "requestDeadlineMs") != deadline + 30000)
        throw new IOException(
            "Binwalk input/descriptor/budget accounting differs from its captured worker request");
    }
  }

  private static long binwalkDeadlineInteger(JSONObject object, String field) throws Exception {
    Object value = object.get(field);
    if (!(value instanceof Integer) && !(value instanceof Long))
      throw new IOException(
          "Binwalk input/descriptor/budget accounting differs: non-integer deadline");
    return ((Number) value).longValue();
  }

  private static void validatePhotoRecCatalog(JSONObject response, JSONArray catalog)
      throws Exception {
    JSONArray fields = response.getJSONArray("rejectedRecoveryStateFields");
    if (fields.length() != PHOTO_REC_REJECTED_FIELDS.length)
      throw new IOException("PhotoRec rejected-state dictionary is incomplete");
    for (int i = 0; i < PHOTO_REC_REJECTED_FIELDS.length; i++)
      if (!fields.getString(i).equals(PHOTO_REC_REJECTED_FIELDS[i]))
        throw new IOException("PhotoRec rejected-state field dictionary differs from its contract");
    if (!PhotoRecReport.supportedAdapter(response.getString("adapter"))
        || !response
            .getString("sourceArchiveSha256")
            .equals("0cfd82ca6890188a3a4fe01c9b970f654eb3af396580f408ed024789e2873616")
        || response.getJSONArray("optionalLibraries").length() != 0)
      throw new IOException(
          "PhotoRec source/configuration identity differs from the reviewed adapter");
    int searched = 0;
    for (int i = 0; i < catalog.length(); i++) {
      JSONObject pattern = catalog.getJSONObject(i);
      String hex = pattern.getString("magicHex");
      long size = pattern.getLong("magicSizeBytes"),
          offset = pattern.getLong("magicRelativeOffset");
      if (pattern.getInt("index") != i
          || size <= 0
          || size > 4095
          || offset < 0
          || offset > 65535
          || hex.length() != 2 * size
          || !hex.matches("[0-9a-f]+"))
        throw new IOException("PhotoRec catalog pattern identity/bytes/bounds differ");
      boolean expectedSearch = size >= 2 && !hex.matches("0+");
      if (pattern.getBoolean("searched") != expectedSearch
          || expectedSearch != pattern.isNull("searchExclusion"))
        throw new IOException("PhotoRec catalog search policy differs from declared coverage");
      if (expectedSearch) searched++;
    }
    if (response.getInt("searchedPatternCount") != searched
        || response.getInt("excludedPatternCount") != catalog.length() - searched)
      throw new IOException("PhotoRec searched/excluded pattern counts are incomplete");
  }

  @Override
  String initialRequest(BooleanSupplier stop) {
    return photoRec ? "bounded" : "accepted";
  }

  @Override
  EvidenceRows open(String request, BooleanSupplier stop) throws Exception {
    acquire(stop);
    if (request.equals("catalog"))
      return new EvidenceRows(
          engineName.toLowerCase(java.util.Locale.ROOT) + "-patterns",
          engineName + " signature pattern catalog") {
        int size() {
          return patterns.length();
        }

        Row row(int i, BooleanSupplier cancelled) throws Exception {
          EvidenceRows.check(cancelled);
          JSONObject value = new JSONObject(patterns.getJSONObject(i).toString());
          return new Row(
              i,
              value.getString("name"),
              (photoRec
                      ? (value.getBoolean("searched")
                          ? "SEARCHED • "
                          : "EXCLUDED: " + value.getString("searchExclusion") + " • ")
                      : "")
                  + value.getString("description")
                  + " • "
                  + value.getString("magicHex"),
              null,
              null,
              value);
        }

        JSONObject metadata() throws Exception {
          return CarvingBackend.this
              .metadata()
              .put(
                  "scope",
                  "Every compiled " + engineName + " magic pattern, including short patterns");
        }
      };
    if (!(request.equals("all")
        || request.equals("bounded")
        || request.equals("accepted")
        || request.equals("unresolved")
        || request.startsWith("format:")))
      throw new IOException("Unknown " + engineName + " scope");
    int[] selected = new int[candidates.length()];
    int count = 0;
    for (int i = 0; i < candidates.length(); i++) {
      EvidenceRows.check(stop);
      JSONObject value = candidate(i);
      boolean accepted = value.getBoolean("parserAccepted");
      if (request.equals("all")
          || request.equals("bounded") && !value.isNull("reportedSizeBytes")
          || request.equals("accepted") && accepted
          || request.equals("unresolved")
              && (photoRec ? value.isNull("reportedSizeBytes") : !accepted)
          || request.startsWith("format:") && request.substring(7).equals(value.getString("name")))
        selected[count++] = i;
    }
    final int[] indices = Arrays.copyOf(selected, count);
    return new EvidenceRows(
        engineName.toLowerCase(java.util.Locale.ROOT) + "-candidates",
        engineName + " • " + request + " • " + name) {
      int size() {
        return indices.length;
      }

      Row row(int i, BooleanSupplier cancelled) throws Exception {
        EvidenceRows.check(cancelled);
        int original = indices[i];
        JSONObject value = new JSONObject(candidate(original).toString());
        if (photoRec) {
          if (!value.has("description"))
            value.put(
                "description",
                patterns.getJSONObject(value.getInt("patternIndex")).getString("description"));
          if (value.has("rejectedRecoveryState")) {
            JSONArray state = value.getJSONArray("rejectedRecoveryState");
            for (int j = 0; j < PHOTO_REC_REJECTED_FIELDS.length; j++)
              value.put(PHOTO_REC_REJECTED_FIELDS[j], state.get(j));
            value.put(
                "rejectedStateMeaning",
                "Raw callback state after a rejected header; upstream does not"
                    + " establish a valid recovery object. No data/full-file/rename"
                    + " check was run.");
            value
                .put("dataCheckExecuted", false)
                .put("dataCheckStatus", JSONObject.NULL)
                .put("dataCheckLimited", false)
                .put("dataCheckSourceBytesProcessed", 0)
                .put("finalCalculatedSizeBytes", state.get(0));
          }
        }
        Long length = value.isNull("reportedSizeBytes") ? null : value.getLong("reportedSizeBytes");
        boolean accepted = value.getBoolean("parserAccepted");
        value.put(
            "interpretation",
            photoRec
                ? rowPhotoRecInterpretation(value)
                : accepted
                    ? "Upstream parser accepted this candidate. Extent, when"
                        + " supplied, is a parser report bounded to this"
                        + " source; it is not proof of provenance, complete"
                        + " decoding, safety or behavior."
                    : "Magic bytes matched but the upstream parser rejected or"
                        + " could not resolve this candidate. Only its"
                        + " observed magic location is established; no"
                        + " extraction extent is inferred.");
        return new Row(
            original,
            value.getString("name"),
            value.getString("parserStatus") + " • " + value.getString("description"),
            value.getLong("physicalOffset"),
            length,
            value);
      }

      JSONObject metadata() throws Exception {
        return CarvingBackend.this
            .metadata()
            .put("selectedScope", request)
            .put("selectedRows", indices.length);
      }
    };
  }

  private String rowPhotoRecInterpretation(JSONObject value) {
    if (!value.isNull("dataCheckSuppressedByCandidate"))
      return "This standalone MPEG pack header was accepted inside the positively bounded stream"
          + " from original candidate ordinal "
          + value.optInt("dataCheckSuppressedByCandidate")
          + " (zero-based). The upstream active-stream header check treats it as a continuation,"
          + " so its optional repeated data scan was not run. This row retains its original header"
          + " result and unknown independent size; the owner is a separate captured candidate."
          + " Independent SCR=0 starts are still checked, including overlaps.";
    if (value.optBoolean("fileCheckMetadataReadLimited"))
      return "PhotoRec established this stored PDF footer boundary before optional"
          + " newline/date reading reached a work limit. The original extent, raw time"
          + " and failure remain recorded separately. Optional metadata is incomplete;"
          + " full PDF structure and rendering remain separate checks.";

    if (value.optBoolean("fileCheckAttempted"))
      return "PhotoRec's original PDF file-check callback ran through a bounded read-only"
          + " source-relative FILE view where execution is recorded. Its reported footer"
          + " boundary enables exact file export only after source/budget checks. This"
          + " does not establish PDF structural integrity, successful rendering, safety"
          + " or the absence of embedded/concatenated documents. No rename is run.";
    return "PhotoRec header callback result; a bounded length is supplied only after its"
        + " data callback reports DC_STOP within the actual source and format limits."
        + " This row has no full file check. Zero-padded lookahead and raw callback"
        + " state remain visible; a stored range does not prove complete decoding.";
  }

  private JSONObject metadata() throws Exception {
    return new JSONObject(header.toString())
        .put(
            "scope",
            photoRec
                ? "Every byte for catalog patterns marked searched; one-byte and"
                    + " all-zero patterns remain visible with explicit search"
                    + " exclusions. Nested/overlapping results retained; no"
                    + " inferred next-hit/EOF sizes."
                : "Every raw magic pattern across the entire supported private"
                    + " blob; short patterns included; nested/overlapping results"
                    + " retained. No inferred next-hit/EOF size and no automatic"
                    + " external extraction.")
        .put(
            "confidenceMeaning",
            photoRec
                ? "Header acceptance and streaming data-stop are separate"
                    + " observations; no security score or complete-file"
                    + " validation is implied."
                : "Raw Binwalk parser confidence (0/128/250 conventions), not a"
                    + " security score or proof that a recovered file fully"
                    + " decodes.")
        .put(
            "validationLimits",
            photoRec
                ? "Upstream standalone safe-header/data callbacks and, in adapters 4/5/6,"
                    + " the reviewed PDF file_check through bounded"
                    + " read-only relative stdio. No other file checks, rename,"
                    + " fragmented/disk recovery or optional codec libraries. 64"
                    + " KiB windows use explicit zero padding near EOF; a"
                    + " reported range must remain within actual source bytes."
                    + " Unknown extents remain null. Adapter 6 can omit repeated optional MPEG"
                    + " data scans only inside independently bounded owners; every original header"
                    + " remains visible with its own unknown independent size."
                : "Format-specific upstream validation varies. Some parsers"
                    + " decompress in memory. A known reported extent enables"
                    + " exact stored-byte export; an unknown extent remains null."
                    + " Rejected/panicked candidates are visible separately.");
  }

  @Override
  List<Link> roots(String request, BooleanSupplier stop) throws Exception {
    List<Link> result = new ArrayList<>();
    if (photoRec)
      result.add(
          Link.text(
              "Preview / export complete compact capture JSON",
              "compact-capture",
              "photorec-capture.json"));
    if (photoRec) result.add(Link.view("Bounded stored-byte ranges (data-stop)", "bounded"));
    result.add(
        Link.view(
            photoRec
                ? "Header-accepted candidates (check data status)"
                : "Parser-accepted candidates",
            "accepted"));
    result.add(Link.view("All signature candidates / overlaps", "all"));
    result.add(Link.view("Rejected / unresolved magic candidates", "unresolved"));
    result.add(Link.view("Complete signature / magic catalog", "catalog"));
    TreeSet<String> names = new TreeSet<>();
    for (int i = 0; i < candidates.length(); i++) names.add(candidate(i).getString("name"));
    for (String format : names) result.add(Link.view("Format: " + format, "format:" + format));
    return result;
  }

  @Override
  List<Link> related(
      String request, EvidenceRows source, EvidenceRows.Row row, BooleanSupplier stop)
      throws Exception {
    EvidenceRows.check(stop);
    return Arrays.asList(
        Link.view("All candidates for this format", "format:" + row.json().getString("name")),
        Link.view("Inspect the actual magic catalog", "catalog"));
  }

  @Override
  JSONObject provenance() throws Exception {
    return metadata()
        .put(
            "input",
            new JSONObject()
                .put("name", name)
                .put("sizeBytes", snapshot.size)
                .put("sha256", snapshot.sha256));
  }

  @Override
  void writeText(String request, Writer output, BooleanSupplier stop) throws Exception {
    if (!photoRec || !request.equals("compact-capture")) {
      super.writeText(request, output, stop);
      return;
    }
    acquire(stop);
    photoRecReport.writeCapture(output, provenance(), stop);
  }

  @Override
  File byteSource() {
    return snapshot == null ? root : snapshot.file;
  }

  @Override
  InputSnapshot ownedByteSource() {
    return snapshot;
  }

  @Override
  String sourceName() {
    return name;
  }

  @Override
  String statusSummary() {
    String fileChecks =
        photoRec && header != null && header.optInt("fileCheckCandidatesLimited") > 0
            ? "PARTIAL PDF FILE CHECKS: "
                + header.optInt("fileCheckCandidatesLimited")
                + " candidates reached read/work/source limits; established native"
                + " footer boundaries remain available when only optional metadata"
                + " was limited. "
            : "";
    return (fileChecks
            + (photoRec && header != null && header.optInt("dataCallbacksSuppressedCount") > 0
                ? "OPTIONAL MPEG DATA CHECKS: "
                    + header.optInt("dataCallbacksSuppressedCount")
                    + " pack headers remain visible inside bounded streams; their repeated data"
                    + " checks were omitted and independent lengths remain unknown. "
                : "")
            + (photoRec && header != null && header.optInt("dataCallbacksLimitedCount") > 0
                ? "PARTIAL DATA CHECKS: "
                    + header.optInt("dataCallbacksLimitedCount")
                    + " candidates reached a data-work budget; their extents"
                    + " remain unknown. "
                : "")
            + (photoRec && header != null
                ? "PARTIAL CATALOG COVERAGE: "
                    + header.optInt("searchedPatternCount")
                    + " / "
                    + header.optInt("patternCount")
                    + " patterns searched at every byte. One-byte/all-zero"
                    + " patterns remain catalogued with reasons. Other file"
                    + " checks/fragment recovery remain pending; PDF file-check"
                    + " scope is recorded separately. "
                : "")
            + (header != null && header.optInt("parserPanics") > 0
                ? "PARTIAL: "
                    + header.optInt("parserPanics")
                    + " upstream parser failures; inspect unresolved"
                    + " candidates. "
                : ""))
        .trim();
  }

  @Override
  String help(String request) {
    return engineName
        + " whole-blob investigation • accepted candidates, unresolved headers, overlaps"
        + " and the full pattern catalog remain available. Parser confidence is not a"
        + " verdict. Only reported bounded extents enable stored-range extraction; unknown"
        + " lengths stay unknown. Tap here for engine/process/coverage details.";
  }

  @Override
  public void close() throws Exception {
    try {
      runner.close();
    } finally {
      if (snapshot != null && borrowedInput == null) snapshot.close();
    }
  }
}

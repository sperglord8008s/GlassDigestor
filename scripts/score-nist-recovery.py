#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Independently score actual recovered ZIP bytes against a pinned test oracle.

No extraction, downloads or production recovery logic. Additional exports remain
visible; Results cover the named reference file set and its exact exported payloads.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import zipfile

MAX_FILES = 4096
MAX_BYTES = 240 * 1024 * 1024
MAX_MANIFEST = 16 * 1024 * 1024
SHA256 = re.compile(r"[0-9a-f]{64}\Z")


def unique_object(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError(f"Duplicate JSON key: {key!r}")
        value[key] = item
    return value


def digest_stream(stream):
    digest = hashlib.sha256()
    count = 0
    while block := stream.read(1024 * 1024):
        count += len(block)
        if count > MAX_BYTES:
            raise ValueError("ZIP payload exceeds the scoring work bound")
        digest.update(block)
    return count, digest.hexdigest()


def score(recovered_zip, oracle):
    if not SHA256.fullmatch(oracle["sourceSha256"]):
        raise ValueError("Oracle source SHA-256 is invalid")
    expected = oracle["files"]
    if not expected or len(expected) > MAX_FILES:
        raise ValueError("Oracle file count is invalid")
    actual = []
    with zipfile.ZipFile(recovered_zip) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        if len(entries) > MAX_FILES + 1 or len(names) != len(set(names)):
            raise ValueError("Duplicate or excessive ZIP entries")
        if "_recovery.json" not in names:
            raise ValueError("Recovered-file manifest is missing")
        manifest_entry = archive.getinfo("_recovery.json")
        if manifest_entry.file_size > MAX_MANIFEST:
            raise ValueError("Recovered-file manifest exceeds 16 MiB")
        manifest_bytes = archive.read(manifest_entry)
        manifest = json.loads(manifest_bytes, object_pairs_hook=unique_object)
        if (manifest["schema"] != "org.diemobile.recovered-files"
                or manifest["schemaVersion"] != 1):
            raise ValueError("Unsupported recovered-file manifest")
        if (manifest["sourceSha256"] != oracle["sourceSha256"]
                or manifest["sourceSizeBytes"] != oracle["sourceSizeBytes"]):
            raise ValueError("Recovered ZIP belongs to a different source image")
        files = manifest["files"]
        if not isinstance(files, list) or len(files) > MAX_FILES:
            raise ValueError("Invalid manifest file count")
        indexed = set()
        total = 0
        for item in files:
            name = item["file"]
            if (not isinstance(name, str) or not name or name in indexed
                    or name == "_recovery.json" or "/" in name or "\\" in name):
                raise ValueError("Unsafe or duplicate indexed file name")
            indexed.add(name)
            entry = archive.getinfo(name)
            if entry.is_dir() or stat.S_IFMT(entry.external_attr >> 16) not in (0, stat.S_IFREG):
                raise ValueError("Indexed ZIP entry is not a regular payload")
            size = item["sizeBytes"]
            offset = item["physicalOffset"]
            if (type(size) is not int or type(offset) is not int or size <= 0 or offset < 0
                    or offset + size > oracle["sourceSizeBytes"] or size != entry.file_size):
                raise ValueError("Invalid indexed payload size or source interval")
            total += size
            if total > MAX_BYTES:
                raise ValueError("Aggregate ZIP payload exceeds 240 MiB")
            with archive.open(entry) as payload:
                read, digest = digest_stream(payload)
            if read != size or digest != item["sha256"]:
                raise ValueError("Actual exported bytes differ from the manifest digest/size")
            actual.append({"file": name, "sizeBytes": size, "sha256": digest,
                           "physicalOffset": offset, "mime": item.get("mime")})
        if set(names) != indexed | {"_recovery.json"}:
            raise ValueError("ZIP contains unindexed payloads")
    reference_results = []
    matched_names = set()
    for reference in expected:
        if not SHA256.fullmatch(reference["sha256"]):
            raise ValueError("Oracle payload SHA-256 is invalid")
        matches = [item["file"] for item in actual
                   if item["sizeBytes"] == reference["sizeBytes"]
                   and item["sha256"] == reference["sha256"]
                   and item["physicalOffset"] == reference["offset"]]
        matched_names.update(matches)
        reference_results.append({**reference, "exactExportMatches": matches,
                                  "status": "exact" if matches else "missing"})
    recovered = sum(bool(item["exactExportMatches"]) for item in reference_results)
    return {
        "schema": "org.glassdigestor.nist-recovery-score", "schemaVersion": 1,
        "meaning": "Actual ZIP bytes scored; additional exports are retained and unscored. "
                   "Android UI acceptance is recorded separately.",
        "sourceSha256": oracle["sourceSha256"], "sourceSizeBytes": oracle["sourceSizeBytes"],
        "expectedReferenceFiles": len(expected), "exactReferenceFiles": recovered,
        "allReferenceFilesRecovered": recovered == len(expected),
        "exportFileCount": len(actual), "referenceFiles": reference_results,
        "additionalExports": [item for item in actual if item["file"] not in matched_names],
        "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recovered_zip", type=Path)
    parser.add_argument("--oracle", type=Path, default=Path(__file__).resolve().parents[1] / "tests/fixtures/nist/L0_Video-expected.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    oracle = json.loads(args.oracle.read_bytes(), object_pairs_hook=unique_object)
    result = score(args.recovered_zip, oracle)
    with args.recovered_zip.open("rb") as stream:
        result["zipSha256"] = hashlib.file_digest(stream, "sha256").hexdigest()
    result["oracleSha256"] = hashlib.sha256(args.oracle.read_bytes()).hexdigest()
    with args.output.open("x", encoding="utf-8") as output:
        output.write(json.dumps(result, indent=2) + "\n")
    print(f"Exact reference-file exports: {result['exactReferenceFiles']}/"
          f"{result['expectedReferenceFiles']}; additional exports: {len(result['additionalExports'])}")
    return 0 if result["allReferenceFilesRecovered"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

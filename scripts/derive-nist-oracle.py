#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Derive test-only contiguous-file hashes from a pinned external NIST image.

Uses the published layout and Python's independent BZip2 decoder. No files are
carved or extracted, and the production app never imports these coordinates.
"""
import argparse
import bz2
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def derive(image, catalog, case_id):
    case = next(case for case in catalog["cases"] if case["id"] == case_id)
    if case["level"] != 0 or any(case.get(key) is None for key in
            ("compressedSha256", "compressedSizeBytes", "decodedSha256", "decodedSizeBytes")):
        raise ValueError("Only acquired, pinned contiguous cases have an exact-file oracle")
    if image.stat().st_size != case["compressedSizeBytes"]:
        raise ValueError("Compressed image size differs from the pinned identity")
    with image.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != case["compressedSha256"]:
            raise ValueError("Compressed image SHA-256 differs from the pinned identity")
    files = []
    for index, row in enumerate(case["layout"]):
        if row["name"] == "Fill":
            continue
        if any(issue.get("recordIndex") == index for issue in case["referenceIssues"]):
            raise ValueError("A reference file has unresolved layout coordinates")
        files.append({"name": row["name"], "offset": row["physicalOffset"],
                      "sizeBytes": row["sizeBytes"]})
    if any(item["offset"] < 0 or item["offset"] + item["sizeBytes"] > case["decodedSizeBytes"]
           for item in files):
        raise ValueError("A reference interval exceeds the pinned decoded image")
    digests = [hashlib.sha256() for item in files]
    counts = [0] * len(files)
    whole = hashlib.sha256()
    position = 0
    with bz2.open(image, "rb") as source:
        while block := source.read(1024 * 1024):
            if len(block) > case["decodedSizeBytes"] - position:
                raise ValueError("Decoded image exceeds its pinned size")
            whole.update(block)
            for index, item in enumerate(files):
                start = max(position, item["offset"])
                end = min(position + len(block), item["offset"] + item["sizeBytes"])
                if end > start:
                    digests[index].update(block[start - position:end - position])
                    counts[index] += end - start
            position += len(block)
    if position != case["decodedSizeBytes"] or whole.hexdigest() != case["decodedSha256"]:
        raise ValueError("Complete independent decode differs from its pinned identity")
    for index, item in enumerate(files):
        if counts[index] != item["sizeBytes"]:
            raise ValueError("Reference interval was not fully hashed")
        item["sha256"] = digests[index].hexdigest()
    return {
        "schema": "org.glassdigestor.nist-contiguous-oracle", "schemaVersion": 1,
        "case": case_id, "layoutSourceSha256": catalog["layoutSourceSha256"],
        "meaning": "Independent test oracle from full pinned stdlib BZip2 decode and published "
                   "NIST layout coordinates; never a production recovery algorithm.",
        "sourceSha256": case["decodedSha256"], "sourceSizeBytes": position, "files": files,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--case", default="L0_Video")
    parser.add_argument("--catalog", type=Path,
                        default=ROOT / "tests/fixtures/nist/file-carving-catalog.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = derive(args.image, json.loads(args.catalog.read_bytes()), args.case)
    with args.output.open("x", encoding="utf-8") as output:
        output.write(json.dumps(result, indent=2) + "\n")
    print(f"Pinned complete decode and {len(result['files'])} reference file hashes verified offline.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Guard the external acceptance reference; this does not score recovery."""
import json
from pathlib import Path

catalog = json.loads((Path(__file__).parent / "fixtures/nist/file-carving-catalog.json").read_text())
assert catalog["schema"] == "org.glassdigestor.nist-file-carving-corpus"
assert catalog["schemaVersion"] == 1 and catalog["sectorSizeBytes"] == 512
assert catalog["layoutSourceSha256"] == "665fb749210b7f86433ac0680351647ee7a01305cfaa41ecd1fd666127fc60c3"
expected_ids = {f"L{level}_{family}" for level in range(6)
                for family in ("Graphic", "Archive", "Video", "Document", "Audio")}
cases = {case["id"]: case for case in catalog["cases"]}
assert len(catalog["cases"]) == 30 and cases.keys() == expected_ids
assert sum(len(case["layout"]) for case in cases.values()) == 534
assert sum(len(case["referenceIssues"]) for case in cases.values()) == 7
for name, case in cases.items():
    assert name == f"L{case['level']}_{case['family']}"
    assert case["archiveUrl"] == f"https://cfreds-archive.nist.gov/FileCarving/Images/{name}.dd.bz2"
    mismatches = []
    for index, row in enumerate(case["layout"]):
        assert row["physicalOffset"] == row["startSector"] * 512
        assert row["sizeBytes"] > 0 and 0 <= row["startSector"] <= row["endSector"]
        assert row["rawCells"][0] == row["name"]
        numbers = [int("".join(value.split()).replace(",", "")) for value in row["rawCells"][1:]]
        assert numbers == [row["sizeBytes"], row["startSector"], row["endSector"]]
        if (row["sizeBytes"] + 511) // 512 != row["endSector"] - row["startSector"] + 1:
            mismatches.append({"kind": "declared_size_sector_span_mismatch", "recordIndex": index})
    assert mismatches == case["referenceIssues"], name
    if name != "L0_Video":
        assert all(case[key] is None for key in
                   ("compressedSha256", "compressedSizeBytes", "decodedSha256"))

video = cases["L0_Video"]
assert video["compressedSizeBytes"] == 19_741_341
assert video["compressedSha256"] == "91f4bc9c1a7fafdc82088a1c08a457bbfb7a9df6aa520d066054ae20b2502b50"
assert video["decodedSizeBytes"] == 62_578_688
assert video["decodedSha256"] == "20d8a231ded5223bbecab97d4155d028ef9cd0760a5dd3ecef22123c46b48870"
assert [(row["name"], row["sizeBytes"], row["startSector"])
        for row in video["layout"] if row["name"] != "Fill"] == [
    ("vid1.mp4", 1_136_007, 10_000), ("vid2.avi", 1_470_360, 22_219),
    ("vid3.mov", 497_414, 35_091), ("vid4.flv", 3_713_213, 46_063),
    ("vid5.mpg", 22_579_200, 63_316), ("vid6.wmv", 2_461_696, 117_416),
]
print("NIST acceptance reference: 30 images, 534 original layout rows, 7 disclosed inconsistencies; recovery unscored.")

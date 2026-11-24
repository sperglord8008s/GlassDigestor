# NIST CFReDS file-carving acceptance corpus

The target is all **30 images** in NIST's [file-carving set](https://cfreds-archive.nist.gov/FileCarving/index.html):
levels 0–5 for Graphic, Archive, Video, Document and Audio. This is one named
CFReDS dataset, not every forensic test ever published by NIST.

`file-carving-catalog.json` records the 534 rows from the original
[image layouts](https://cfreds-archive.nist.gov/FileCarving/ImageLayouts.htm),
including original cell text, sector coordinates and seven apparent reference
size/span inconsistencies. Its source-page SHA-256 identifies the consulted
reference. Inconsistent rows are flagged, not silently corrected. Layouts and
expected hashes are **test oracles only**; the app does not consume this catalog.
No third-party media or disk image is bundled in the app or this repository.

Only **L0_Video** is currently acquired and identity-pinned. Its compressed
identity exactly matches the reported `L0_Video.dd.bz2` screenshot. Complete
archive decoding has passed through the actual host libarchive JNI and a
symlinked trusted cache ancestor, producing 62,578,688 bytes with the pinned
independently calculated SHA-256. Binwalk and PhotoRec also produce real
discovery reports. Discovery counts are not recovered-file acceptance.

| Gate | L0_Video | Other 29 images |
| --- | --- | --- |
| Reference layout recorded | Yes, with one flagged Fill-row inconsistency | Yes, with six flagged inconsistencies |
| Compressed and decoded identity pinned | Yes | Pending acquisition |
| Entire archive decoded byte-for-byte | Passed on host JNI | Unverified |
| Every expected file recovered and exported byte-for-byte | Unverified | Unverified |
| Android gallery, preview and real destination export | Unverified for this corpus | Unverified |

For a complete contiguous recovery, compare the **actual exported bytes** with
the independently identified original file: size and SHA-256 must match.
Offsets/names/header hits alone do not pass. Retain duplicates, false positives,
unresolved extents and native errors in the result. Fragmented and missing-data
cases need explicit reconstruction/partial-recovery scoring; missing bytes must
never be invented. Reference contradictions must remain visible in evaluation.

Run `python3 tests/test-nist-catalog.py` to check the reference inventory.
`bash scripts/test-nist-archive.sh /path/to/L0_Video.dd.bz2 /path/to/receipt.json`
repeats the optional exact public-image archive regression entirely offline.
The caller acquires the external image separately. Broader carving/export
evaluation and the remaining 29 image identities are continuation work.

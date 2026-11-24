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
discovery reports. Discovery counts are separate from recovered-file acceptance.

The current real-native host run acquires the archive, executes both FD-based
engines through production Gallerize and exports **all six** reference files
byte-for-byte. Independent ZIP/CRC/size/offset/SHA-256 scoring passes. The 86
actual files include a duplicate exact AVI and 79 additional unscored exports.
All 28,890 original PhotoRec occurrences, ordering and header acceptance counts
are preserved; 11,024 in-stream MPEG pack rows retain unknown independent
extents while their redundant optional callbacks are explicitly omitted. Native
callback work totals 47,972,352 bytes with no exhausted data-work budgets.
[Current receipt](../../../docs/verification/nist-L0-video-native-export.json).

The historical pre-alpha.19 baseline was 5/6 because MPEG exceeded the old
8 MiB data-work cap. A fresh before/after real-native run confirms that gap and
its closure. This is host file recovery, not Android gallery/SAF acceptance or
NIST certification; other images and fragment recovery remain unscored.

| Gate | L0_Video | Other 29 images |
| --- | --- | --- |
| Reference layout recorded | Yes, with one flagged Fill-row inconsistency | Yes, with six flagged inconsistencies |
| Compressed and decoded identity pinned | Yes | Pending acquisition |
| Entire archive decoded byte-for-byte | Passed on host JNI | Unverified |
| Every expected file recovered and exported byte-for-byte | **6/6** exact on host; duplicate/additional exports retained and unscored | Unverified |
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


## Score actual recovered files

`L0_Video-expected.json` pins all six original file intervals and SHA-256 values.
Regenerate it from the separately acquired image using Python's independent
BZip2 decoder; no media files are written:

```sh
python3 scripts/derive-nist-oracle.py /path/to/L0_Video.dd.bz2 --output /tmp/oracle.json
cmp tests/fixtures/nist/L0_Video-expected.json /tmp/oracle.json
python3 scripts/score-nist-recovery.py /path/to/recovered-files.zip --output /tmp/score.json
```

The score checks source binding, every actual payload's CRC/size/SHA-256,
reference coordinates and all indexed entries. A missing file returns status
1; complete reference recovery returns 0. Duplicate exact files and additional
exports remain visible. Malformed/unindexed/duplicate ZIP entries and false
manifest digests are refused. Nine small independent negative/positive fixtures
run without corpus downloads in `scripts/test.sh`. This scoring/oracle code is
test tooling only; production discovery never receives reference coordinates.
Fragmented, missing-data and unacquired cases remain unscored.


## Run acquisition, discovery and real file exports

The optional end-to-end harness rebuilds the three host native engines, acquires
the pinned compressed input through the production archive session (including a
trusted ancestor alias), and decodes it byte-for-byte. A production Gallerize
session runs both descriptor-based native engines against its owned decoded
input. It exports **all bounded non-text file cards**, without consulting layout
coordinates, then invokes the independent ZIP scorer:

```sh
bash scripts/test-nist-recovery.sh /path/to/L0_Video.dd.bz2 /new/output-directory
```

An existing output directory is refused. Outputs retain the actual payload ZIP,
lossless raw native captures and complete gallery report, execution/identity
receipts and independent score. A missing reference file makes the scoring step
return 1; additional/duplicate exports remain visible. The external image and
its media are never bundled. Peak work includes several owned image copies,
bounded native/Java reports and the resulting ZIP. Run native builds/tests
serially: never replace a library while a test JVM has it loaded. This checks
host engines/models/file bytes; Android UI and SAF acceptance stay separate.

The pre-alpha.20 actual host native/export run passes 6/6; full build/CI and
actual Android regression gates are still pending. [Receipt](../../../docs/verification/nist-L0-video-native-export.json).

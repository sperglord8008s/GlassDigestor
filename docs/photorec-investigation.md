# PhotoRec whole-blob investigation

Open **File info → PhotoRec / whole-blob formats / stored-byte carving**.
The app compiles unmodified PhotoRec 7.2 format sources, commit
`281be432dd79121e08e0898887a9ee1f30fb3e96`. It complements
[Binwalk](binwalk-investigation.md), [MPV](media-investigation.md) and
[metadata/EXIF investigation](metadata-investigation.md), each with separate
validation and coverage. Source pins, GPL-2.0-or-later notices and standalone
build instructions are in [carving-engine](../carving-engine/README.md).

## Catalog and search scope

The original registration API supplies **349 format groups / 774 magic
patterns**. The complete pattern bytes, relative offsets, descriptions, original
enable/recover flags and maximum file sizes remain inspectable/exportable.
The whole-blob index searches **581 patterns at every byte**. **193 one-byte or
all-zero patterns are not searched in this mode**; each remains in the catalog
with `searched: false` and an explicit reason. They need a separately bounded
anchored scope, currently pending. These exclusions include generic text and
encrypted/low-discrimination signatures; absence of a hit cannot prove absence
of those formats. Catalog groups are not a format-validation coverage claim.

Nested and overlapping matches remain separate. Every occurrence identifies its
original `patternIndex` and physical `magicOffset`. The candidate start is
`magicOffset - magicRelativeOffset`. If that is negative, no callback is invoked;
the exact magic remains an unresolved record, with a null candidate start and
its observed magic coordinate as the navigable `physicalOffset`. This exception
is explicitly labelled in `physicalOffsetMeaning` and is never extractable.
An independent naive matcher checks every searched-pattern occurrence in fixtures.

## What each check establishes

The upstream header callback runs in standalone `safe_header_only=1` mode with
an empty previous-file recovery context. This differs from PhotoRec's live disk
recovery state and from fidentify's default `safe_header_only=0`. The source
header window is 64 KiB, borrowed from two bounded pread-cache windows.
Near EOF, owned zero-padded lookahead is explicit and never becomes a stored
source extent. The input is not copied into a file-sized native allocation.
`headerAccepted` means this callback accepted its header, including any padded
context. It is not full-file validation.

Where supplied, the original `data_check` callback processes 64 KiB blocks with
the previous block in the other half of its buffer. The final partial block is
zero-padded, as exposed in the report. The audited generic `data_check_size` callback ignores payload contents: it runs
against the actual available source length without copying every unused block.
`dataCheckMode` and zero processed payload bytes disclose this declared-size path;
it is not content integrity verification. Other data callbacks use streamed blocks.
Returned statuses preserve the upstream
enum: `DC_SCAN=0`, `DC_CONTINUE=1`, `DC_STOP=2`, `DC_ERROR=3`; null means no data
callback was run. The final calculated size, consumed source bytes, min/max,
offset-ok/error state, time, flags and returned extension remain visible.
Raw unsigned 64-bit calculated/minimum/offset values use exact decimal strings;
they cannot silently become rounded JSON floating-point numbers on Android.
Extractable source-bounded lengths and ordinary file coordinates remain numbers.

A positive calculated length after **DC_STOP**, within actual remaining source
bytes and upstream minimum/maximum, with no offset error, is a **reported stored
extent**. Only that interval enables exact byte export, hashes/entropy and hex.
The adapter never takes a length from the next hit or EOF. Missing terminators,
out-of-source sizes, data errors and header-only formats retain null lengths.
Header-calculated sizes outside PhotoRec's own data-check contract are preserved
without invoking a data callback; an actual 7z header fixture exercises a value
beyond signed 64-bit and checks that its decimal digits remain exact.

Adapter 4 now runs the reviewed upstream PDF `file_check` through bounded
read-only relative stdio; see [actual PDF recovery](pdf-file-recovery.md).
Other full file checks, rename callbacks, optional libjpeg/zlib verification,
PhotoRec's disk recovery and reconstruction of fragmented files remain disabled. Some full callbacks open/rename outputs or extract thumbnails;
they do not belong in a read-only descriptor adapter. A bounded extent can still
contain corrupt data: the upstream PNG data walker finds chunk termination but
does not check PNG CRCs or decode pixels. Tests preserve this distinction using
an actual bad-CRC file. JPEG fixtures include an ICC false EOI and nested PNG;
those markers do not shorten the reported outer JPEG range.

## Ownership, worker and UI

The parent owns a whole private input snapshot and its SHA-256. The shared
non-exported isolated worker receives a read-only descriptor, output pipe and
request token; it receives no filesystem path and refuses normal app-UID
fallback. Native `pread` preserves caller descriptor ownership/position and
checks source size/timestamps across acquisition. Upstream path opens and renames
are redirected to denying functions, auxiliary `photorec.sig` is disabled, and
timestamp-changing capabilities are not configured. No inspected app, external
extractor or recovery command is run. Native defects may still crash/hang the
worker; real OS isolation/cancellation has not been verified on a handset.

The default view shows bounded ranges. Other roots expose header-accepted files,
all raw candidates, unresolved extents, format groups and the complete catalog.
Each catalog row labels SEARCHED or EXCLUDED. Help shows the partial catalog
coverage before opening detail. Search/paging never narrows a complete selected
scope export. Exact range bytes and complete JSON are staged before SAF; pending
exports survive source expiry and recreation independently of the viewer input.
Normal close deletes its private input; recreation reacquires from the original
path when available. Persistent carving sessions and recursive extracted-file
handoff remain pending.

## Indexed evidence and complete capture

Native `org.diemobile.photorec-evidence` **version 2** retains the original row
order. Accepted/header-only/negative-start records are objects. Each ordinary
rejected header is `[patternIndex, magicOffset, recoveryStateIndex]`; its exact
raw callback tuple is stored once in `rejectedRecoveryStates`, using
`rejectedRecoveryStateFields` as the field dictionary. Occurrences are never
deduplicated. Candidate starts, fixed rejected outcomes and header-window
lengths are derived from the catalog and owned source size; every raw returned
state value remains available. Rejected state does not establish a valid
recovery object under the upstream callback contract.

The Java receiver checks exact integer selectors, dictionary bounds/widths,
uint64 decimal strings, uint32/int64 state and every original coordinate/count.
Rows expand only when queried/displayed/streamed. The compact and expanded
representations retain the same evidence, including repeated raw occurrences.
A 75,000-header fixture verifies every occurrence, page 1,000/original index
50,000, complete streamed exports and exact engine-report reconstruction.
Fractional offsets, invalid state references, uint64 overflow and cancelled
staging are rejected without retaining an input/output stage.

**Root views → Preview / export complete compact capture JSON** stages a complete
`org.diemobile.photorec-capture` **version 1** before opening the picker. It
contains fresh captured input SHA-256/size/name in `provenance`, original
`engineReportMetadata`, `patterns`, `rejectedRecoveryStates` and every `rows`
entry. Reconstruct the native packet by adding those three arrays to the
metadata object. The capture includes all rows regardless of current table,
filter or page. UTF-16 source-name units and all raw unsigned strings survive;
cancellation discards incomplete staging. A pending capture remains exportable
after source expiry and recreation. Ordinary selected-table/record exports
continue to stream fully expanded, named fields.

## Limits and verification

Limits: **256 MiB input**, **250,000 candidate occurrences**, **10,000 patterns**,
**64 MiB streamed data work per candidate / 8 MiB PDF file-read work per candidate / 256 MiB aggregate callback work**, **24 MiB native report**,
**30-second cooperative native deadline**, and the shared **45-second request
watchdog / 32 MiB pipe envelope**. Input/match/report/deadline caps fail with no successful prefix. Data-work caps preserve complete magic/header discovery, mark each limited candidate with null extent and an explicit reason, and expose aggregate counts in help/exports.
Expensive native callbacks may need the parent watchdog. The Java boundary checks
source/schema/configuration identities, counts, search policy, magic-relative
coordinates, callback scope and every extractable data-stop witness.

Run `bash scripts/test-photorec.sh` and `bash scripts/test-workbench-ui.sh`.
Host checks use the real engine and a separately implemented naive magic oracle:
all 77 fixture occurrences, overlapping JPEG/PNG, GIF subblocks/trailer and WAV
declared size, negative/relative starts, DICOM header-only evidence, missing JPEG
EOI, malformed PNG length, bad-CRC semantics, invalid/oversized descriptors,
caller position, budget exhaustion, complete exports and exact immutable carving.
Four API 28 Robolectric cases use actual host JNI/widgets with only OS transport
and destination streams substituted: unknown/bounded evidence, picker-delay
source-expiry/recreation, catalog paging/full export including exclusions, and complete compact capture through source expiry/picker recreation.
The full regression count is recorded in the continuation/parity documents.

ARM64 source compilation, static 16 KiB alignment and host/fixture verification
are separate from device verification. Real SAF/Binder/isolated UID/crash/cancel,
API 26/35 and 16 KiB Android runtime remain unverified. Complete PhotoRec feature
parity, filesystem/unallocated media, fragmented/JPEG Digger recovery, every weak
signature and exhaustive format/codec/integrity checks remain incomplete.

The former 50,000-record transport limit was reproduced on the local 48 MiB
debug APK. Adapter 2 resolves that case with a lossless dictionary representation:
all **109,023** occurrences complete for **50,223,887 bytes**, source SHA-256
`c799a83bfd0508963dcb3ea58dd6aa38d687979dc0b3edff920cef9abafb8444`. The
receiver and complete exports run with a 256 MiB JVM heap in a separate host
audit: 176,292,786 expanded characters versus about 2,305,975 compact capture
characters; 18 ranges have bounded data-stop evidence. This is one static host
audit, not comprehensive APK recognition, integrity or Android runtime proof.
Debug signing/build environments can produce different APK bytes.


## Adapter 3: bounded input windows and larger blobs

Adapter 3 retains native evidence schema 2 and the complete indexed transport;
original adapter-2 captures remain readable within their original 64 MiB scope.
The PhotoRec provider now acquires up to 256 MiB; Binwalk keeps its separate
64 MiB limit. The upstream 7.2 format sources, pattern coverage and callback
validation policies are unchanged.

The native adapter owns a duplicated readable regular-file descriptor and uses
`pread`, preserving the caller's position and ownership. A 1 MiB matcher buffer
and two 128 KiB header caches bound **input-read payload buffers to 1.25 MiB**,
independent of file size. Upstream data callbacks retain their separate 128 KiB
previous/current block window and existing work budgets. Registry/evidence
storage, JNI/JVM objects, allocator overhead and kernel cache are additional;
1.25 MiB is neither the maximum input size nor a whole-process memory cap.

Aho-Corasick state continues across matcher chunks. Unaligned headers borrow a
contiguous cached 64 KiB view; short final headers receive explicit scratch
padding. Data callbacks read only their source-bounded intervals. Reads reject
EOF/truncation and unreadable/O_PATH descriptors, retry EINTR, and check the
cooperative deadline. Source identity, size and timestamps are checked after
analysis. These checks complement the app's privately acquired input identity;
they are not cryptographic proof against an independently mutable external FD.

New native metadata identifies the read mode, lack of input-sized allocation,
maximum read-buffer payload, matcher/cache dimensions, logical source-read
operations/bytes, owned duplicate and preserved caller position. Repeated
header/data reads can make sourceReadBytes exceed the unique input size. The
Java boundary checks this contract before accepting an adapter-3 report.

The host suite now includes a separate reader executable for unaligned seams,
EOF padding, cache reuse, bounds, caller close/position, write-only/O_PATH
refusal, truncation/timestamp changes and 2,048 lease destructions. Real JNI
checks cover an **80 MiB + 17 byte** sparse blob and an independently fully
written nonzero blob of the same size, exact PNGs across 64 KiB/1 MiB/64 MiB and
EOF boundaries, owned large-provider acquisition, a complete **256 MiB** sparse
scan and rejection at 256 MiB + 1. These run with a 128 MiB Java heap and do not
allocate full-size Java input arrays. Existing naive-pattern, dense-index,
callback-budget and exact-export regressions still pass.

A separate static audit of the frozen 0.5.0-alpha APK checks **51,088,307 bytes**
(SHA-256 `893b9af057a829c43882dedd9ec39085319b5fc5e0909bb259e9bccc58091749`):
all **111,228** returned magic occurrences agree with independent reads of the
original bytes, and all **38** reported ranges stay inside the source. The APK
was not executed. The standalone corresponding-source ZIP includes the new
reader and tests and is separately rebuilt offline. These remain host/static
checks, separate from actual device IPC, memory pressure and crash behavior.


## Adapter 4: actual PDF files

The PDF-only file-check exception now produces source-bounded file extents
where the original footer check completes successfully. Other file callbacks
remain disabled. Fixed relative-stdio input buffering adds 64 KiB to adapter 3's
1.25 MiB input-read payload buffers (now 1.3125 MiB); this still is not total
memory or maximum input size. Original 2/3 captures retain their own limits.
The existing aggregate callback budget and a separate 8 MiB per-PDF read-work
cap remain explicit. Detailed provenance, refusal conditions, optional-date
work limitation and tested actual file exports are in the [PDF contract](pdf-file-recovery.md).


## Adapter 5: file boundaries and optional work

An established native PDF footer survives only a typed optional post-footer
read-work limit. Missing footer, source IO, deadline and other errors remain
unresolved. Original error/work/time values and separate native boundary versus
partial-metadata flags/counts are retained. Java validates the exact policy,
flags and counters; older captures keep their original stricter semantics.
The input and memory/work caps stay unchanged. Actual >9 MiB file/gallery/export
and independent xref/byte tests pass; see [PDF policy](pdf-file-recovery.md).

## Adapter 6: larger streaming checks and repeated MPEG packs

Adapter 6 raises only the per-candidate streaming data-work allowance to
**64 MiB**. PDF file reads retain their separate **8 MiB** cap and adapter 5's
established-footer/optional-metadata policy. All callbacks still share the
**256 MiB** aggregate work cap. Input buffers, source bounds, 30-second native
deadline, candidate count and 24 MiB report cap remain unchanged. These are
read-work allowances, not promised recovered file sizes: a 64 MiB stream can
need another block to observe its stop and therefore remain unresolved.

MPEG program streams contain repeated `000001ba` pack headers. After an
upstream data callback establishes a positive, source-bounded MPEG extent with
`DC_STOP` and no offset error, the adapter can omit repeated optional data
walks within that interval. The original standalone safe-header callback still
runs for every occurrence. For an accepted pack inside the interval, an
additional upstream header check receives a scoped active-MPEG context. Its
ignored-header result authorizes omission of the optional data walk. This
context has no file handle and executes no file check or recovery operation.
PhotoRec deliberately accepts an SCR=0 pack as an independent stream even in
that context; its data callback runs normally. Native overlapping extents are
retained without manually splitting the earlier stream.

An omitted walk has `dataCheckMode` equal to
`upstream-active-mpeg-header-ignored/data-callback-suppressed` and an earlier
`dataCheckSuppressedByCandidate` row ordinal. Its original header/raw state
remains present, `dataCheckExecuted` is false, status and independent extent
are null, and processed data bytes are zero. The owner reference describes a
captured covering interval; it never gives the inner pack an inherited file
size. `dataCallbacksSuppressedCount` and `mpegContinuationPolicy` expose the
policy in complete metadata/Source exports. This intentional optional-work
omission is distinct from `dataCheckLimited` and its budget-exhaustion count.
Every raw header/candidate remains searchable and exportable. Additional
independent content inside a covering stream may remain unvalidated.

Java validates the exact work/policy fields, earlier owner index, MPEG pack
pattern, independent owner data-stop witness, source containment, unexecuted
inner state and exact counts. Adapters 2–5 remain readable under their original
limits and cannot claim adapter 6's optional-work omission.

`bash scripts/test-photorec-mpeg.sh` runs generated packet-stream checks:
exact 9 MiB exported payload hashes, every repeated pack, nonzero-SCR suffixes
with and without owners, independent SCR=0 overlaps, owner endpoints,
truncated packets, 64 MiB stop/lookahead work limits, malformed owner/policy
refusals, legacy adapter-5 reading, descriptor position and cancellation
cleanup. The standard PhotoRec script also runs these cases. Existing PDF
checks separately retain the 8 MiB file-read cap. These host contracts do not
establish actual Android/SAF/handset acceptance or complete media integrity.

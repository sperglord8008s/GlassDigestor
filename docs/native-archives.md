# Native archive investigation

GlassDigestor's broader archive browser identifies content through pinned
libarchive 3.8.11. The original ZIP browser remains available through **Classic
ZIP**. The main **Archive** action accepts any captured input; the gallery and
content-preview archive tools use the broader browser as well. A filename or
extension is not a prerequisite.

## What the browser does

- Captures an independent private input, with complete SHA-256 and exact size.
- Requests metadata through the private isolated worker using a read-only
  descriptor, an explicit offset/length and a report pipe. No input pathname is
  sent to the parser. Selected acquisitions add a separate empty writable
  descriptor; the worker never chooses an output pathname.
- Presents virtual folders, a name/index filter and 50-row pages. **All entries**
  includes every returned native index, explicit directories, duplicate names,
  links, special entries, missing/invalid names and names outside the virtual
  hierarchy. Unusual names are also shown at the tree root. Original accessor
  bytes, validity flags and complete metadata remain in the raw evidence.
- Acquires one regular, unlinked, unencrypted member by its original index.
  Duplicate names therefore select distinct members. The native extraction
  report must match the captured directory, source, scope and selected row.
  The app independently checks output size, SHA-256 and CRC32. Native checksum
  or parser failure prevents publishing the partial output.
- Opens exact member bytes in the existing media/text/hex preview and byte
  exporter, returns them to the parent analysis workbench, or identifies them
  as a nested archive/compressed stream. No member is installed or executed.
- Retains the nested parent trail, inspection packets and successful parent
  acquisition packets. Back uses captured parent evidence. Compact saved state
  references private files with independently checked hashes; restoring a
  finished navigation state does not rerun native analysis.
- Shows paged original native reports and complete entry metadata. Explanations
  are available separately from priority facts. Complete report export includes
  source identities, parsing intervals, original packets and the current nested
  chain. A persistent history of all visited nodes remains future work.
  Failed acquisition/inspection attempts retain diagnostics and available native
  reports within the same explicit report budget.

Use **Parsing interval** for a prefixed/embedded archive: enter the observed
start and the intended parsing length in decimal or `0x` hexadecimal. This is
an explicit analysis scope, **not an inferred recovered archive extent**. The
browser does not claim arbitrary deobfuscation, password recovery or exhaustive
recognition of every archive format.

Binwalk and PhotoRec row actions can open a [source-bound archive parsing
interval](archive-launches.md), including findings with unknown extents. The
original finding/provenance remains in the resulting archive packet, and caller
SHA-256/size expectations are verified before parsing.

## Direct archive actions and Android cache aliases

The main scan puts byte-based archive/compression identification and **Browse
contents / decompress / save files** before the desktop parser facts. A generic
`Binary` parsing context does not hide an Archive-category signature. Archive
detection details also offer **Browse / extract**, bound to the captured input;
a signature alone does not supply a verified embedded physical extent.

A completely inventoried single regular member exposes **Open decoded file**
and **Save decoded file…** directly in list mode. Member details put these
actions before raw evidence. Gallery cards retain their direct file actions;
the extra single-file row is hidden there to preserve preview space.
Decoded previews can hand an independently owned byte-for-byte copy to
**Gallerize**, including a compressed disk image containing several media files.
Closing the preview does not invalidate that handoff. These actions add no
network access or inspected-code execution.

The reported `Unexpected non-regular archive cache entry` failure was
reproduced when a trusted cache ancestor had an operating-system path alias.
Session, origin and staging roots now anchor the canonical trusted directory
before generating direct child leaves. A symlink in a generated leaf is still
refused, including before native reanalysis; outside sentinel bytes remain
unchanged. The exact public NIST `L0_Video.dd.bz2` identity now decodes through
actual host JNI to the independently pinned 62,578,688-byte result. This does
not establish full NIST media recovery or handset UI acceptance; see the
[acceptance corpus](../tests/fixtures/nist/README.md).

## Formats, filters and evidence

The adapter registers reviewed readers for 7z, ar, CAB, cpio, empty input,
ISO9660, LHA, RAR/RAR5, TAR, WARC, XAR, ZIP and transformed raw streams. It
registers none/gzip/bzip2/XZ/LZMA/lzip/LZ4/Zstandard/compress/RPM/uu filters.
Registration diagnostics are exported: registration is not positive fixture
verification of every subtype. XAR's XML reader is unavailable in this build.
Uncompressed raw fallback is explicitly refused as archive identification.

Zlib uses Android's platform library. Bzip2, XZ/liblzma, LZ4 and Zstandard are
built from hash-pinned original source. Version/license/source contracts are in
[archive-engine](../archive-engine/README.md); complete notices and original
source headers are packaged in the APK. Corresponding source archives accompany
published checkpoints. No command-line extractor, external-program filter,
filesystem reader, path extraction, passphrase prompt or mtree reader is enabled.

Metadata is what the pinned API returned, rather than an assertion about device
behavior or a publisher. Names, UID/GID, mode, timestamps/nanoseconds, link
accessors, encryption state, xattrs, ACLs, sparse ranges, Mac metadata and digest
accessor buffers remain available in each complete entry object. Raw C-string
bytes and UTF-8 interpretations are separate. Digest presence is unknown:
libarchive's public getter can return a zero buffer even when no digest was
stored. Such buffers are not presented as declared or verified checksums.

`headerPositionUncompressedStream` is the original libarchive getter observation.
It is **not a physical member offset**. CAB reports can change that getter when
selected data is read instead of skipped; both raw observations remain, and
only this field is excluded from stable directory comparison. Decompressed
member previews retain parent identity without inventing physical offsets.

## Ownership, limits and failure behavior

| Scope | Bound |
| --- | --- |
| Captured input | 1 GiB |
| One acquired logical member | 256 MiB |
| Native returned entries | 20,000 |
| Native aggregate entry metadata / report | 16 MiB / 24 MiB |
| Source-read / transformed-stream work | 4 GiB / 512 MiB |
| Selected LZMA decoder memory / Zstandard window | 64 MiB / 64 MiB |
| Native cooperative / client request deadline | 30 seconds / 45 seconds |
| Nested trail | 16 levels |
| Retained derived inputs / native reports | 512 MiB / 64 MiB |
| Private generated files / compact saved state | 256 / 192 Ki-characters |

Decoder limits do not bound the entire worker's heap/RSS. Native limits are also
recorded in every complete-schema report. Cancellation/death/disconnection must
produce a failed or incomplete request; there is no in-process native fallback.
Actual Android process termination and Binder transport still require devices.

Input and capture SHA-256 checks cover the whole parent, including bytes outside
an explicitly selected parsing interval. Same-length mutation is refused.
Names never become extraction paths, and links/devices/directories cannot be
materialized. Only generated direct private handoff leaves can be cleaned up.

Prior inputs/captures remain within the bounded session cache until the session
finishes, including nodes left through Back. This preserves older saved
navigation descriptors during in-flight changes. Finishing deletes generated
session files. Android may evict cache files; expiry/corruption is an explicit
restoration failure. A newly selected scope is published only after complete
capture; native failed inventories remain labeled failed/partial and cannot
supply acquired member bytes.

Prepared report exports are independent of subsequent navigation and survive
picker recreation. Cancelled/failed stages are removed. A failing document
provider may retain partial destination output, which the UI states explicitly.

Per-preview persistent native-origin sidecars and a cross-scan parent-history
index remain continuation work. Keep/export the parent archive packet for its
full native witness and ancestor relationship. The independent derived preview
retains exact bytes and immediate parent identity; it does not relabel a
logical member as a physical range.

## Verification and reproduction

The existing native core passes 25 captures / 37 exact acquisitions, including
TAR/PAX/xattr/links, duplicate ZIP names, CRC failures, 7z, stored/compressed RAR5,
CAB STORE/MSZIP/LZX, compressed ISO, gzip/bzip2/XZ/LZMA/LZ4/Zstandard and a
16 MiB + 17-byte member. The source-bound model independently repeats all 25/37
fixtures and checks indexed duplicate bytes, original-input expiry, restoration
without rescanning, cancellation, failed-attempt retention and tamper refusal.

Five API 28 host cases use the real native engine with the OS transport
substituted. They cover duplicate-index preview/export after parent closure,
ZIP → mislabeled TAR folders with Back/recreation, CRC failure packets and
cancelled/failed/recreated report export, and an explicit embedded interval with
whole-parent mutation refusal, long-name filtering and independent selected-member
return to a parent. An additional actual-CDiE case checks Main’s mislabeled-TAR
archive action and a known independent member-result handoff. Worker tests check the private manifest,
ordinary-UID refusal and closure of malformed/rejected payload descriptors.
These are host checks, not real Android UID/Binder/SAF/device verification.

```
bash scripts/test-archive-engine.sh
bash scripts/test-native-archive-store.sh
bash scripts/test-workbench-ui.sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
bash scripts/verify-apk.sh
```

The full **147-case Android host suite** passes with zero failures/errors/skips,
as do ARM64 assembly, lint and static APK checks for its fifteen native
libraries, source notices, version, signature, offline permissions and 16 KiB
alignment. All five source CI jobs passed in run `44650839550`; pre-alpha.3
is published from exact CI source `78edded6db49efdfb9bc3e823182c18e7d6854ee`. Actual ARM64/device/API 26/API 35/Binder/UID/SAF/cancel/crash,
16 KiB runtime, storage-pressure and media/PDF decoding acceptance remain
unverified. Full desktop DiE parity remains incomplete.

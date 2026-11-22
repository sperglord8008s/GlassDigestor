# Analysis workbench

Design: every result should include observation, interpretation, evidence, source/revision, limitations, and practical consequences. Never equate a requested permission, signature match, high entropy, or package label with proven malicious behavior.

## Interface principle: expert-first, explanatory by design

There is no beginner/advanced/expert gating. The default Scan view exposes technical facts and signature results, exact byte lengths, hashes, flags, and scan provenance. Preserve original names, units, offsets and raw records. Contextual help explains what a term means, mechanism, interpretation, corroboration and limitations beside the evidence. Reference material is supplemental, not a substitute for technical facts. Use readable proportional text for explanations and monospace for actual bytes/code. No artificial security score. Never imply evidence was collected when an engine has not exposed it.

## Integration map

- CDiE: existing native signature engine.
- Whole-file digests: pinned xxfclib contexts plus unmodified 0BSD RHash 1.4.5 MD4, isolated descriptor adapter and eight typed CRC32/MD4/MD5/SHA1/SHA224/SHA256/SHA384/SHA512 records. Both the File info viewer and Native Tools use complete checked input coverage and independently cross-check native SHA-256 against acquisition. RFC/JCA/OpenSSL/descriptor/budget fixtures and three API 28 actual-JNI cases pass; OS transport/device behavior remains unverified. See [hash contracts](hash-investigation.md).
- LibChecker-Rules: actual pinned canonical v5 compiler retains every active matcher/effective locale payload (3,053 / 2,934). Complete source-built RE2/J 1.8 supplies all 123 regex with exact-first, priority/ID ordering and whole-string portable semantics. Native/component names, DEX package ancestors and original compiled-manifest shared-library/permission/metadata/action names feed the report. The complete typed catalog has UUID relationships, full metadata and exact source/content identities. Host/upstream/AAPT2/four Android cases pass; SDK implementation/use, fingerprint/version providers, split/framework resolution and device flows remain unverified. See [SDK contracts](sdk-investigation.md).
- Universal Android Debloater NG: pinned GPL-3.0 package catalog. Exact package ID lookup, read-only. Recommendations are contextual.
- Exodus: local import of a lawfully obtained tracker API snapshot; ODbL 1.0 / DbCL 1.0. No network or API credentials in the app. Independent source-built RE2/J search adapter now uses original slash DEX descriptors, a compared Python-compatible subset and frozen Unicode 15 properties. A bounded shared required-literal index removes impossible candidates before exact RE2/J searches; complex patterns fall back to ordinary search, with all atoms/work/unknown coverage visible. Every imported rule/unknown field/source metadata is inspectable and exportable, unsupported/short rules stay visible, and exact snapshot bytes retain SHA-256 identity. Host tests compare 924 searches to actual pinned upstream methods and exhaust all Unicode scalar properties; five Android integration cases pass. Full Python syntax, dexdump extraction/nested-APK equivalence and handset behavior remain incomplete. See [tracker contracts](tracker-investigation.md).
- APKEngineer: exact upstream project/repository still to be identified and license audited.
- AOSP apksig 8.9.2 (Apache-2.0): unmodified hash-verified binary plus reviewed source contracts, complete typed scheme signers/certificates/DER/SPKI/extensions/scoped rotation capabilities/digests/source stamps/diagnostics. Explicit detached v4 selection, pair ownership, allocation gate and exact exports preserve identity across recreation/parent expiry. The adapter guards the reproduced upstream v4 aggregate-status omission. Actual RSA/EC/DSA, v3/v3.1/v4/v4.1, negative/ownership host fixtures and four Android cases pass; exhaustive algorithms, API 36, split signing policy, hard isolation, real SAF and handset/platform behavior remain incomplete/unverified. See [signing contracts](apk-signing-investigation.md).
- CDiE native tools now expose ELF/PE/Mach-O structures and supported memory maps, symbols, imports/exports/resources, instruction decoding, Unicode strings, hashes and entropy. ZIP/APK members can be selected for bounded private extraction and analysis; installed package inventory is available. These integrations do not imply full format coverage or device verification.
- Structures, entropy, maps, imports, exports, symbols, resources and instructions have detached typed records, searchable Android pages, raw field details with contextual help, physical/VA navigation and range evidence. Resource exports stage exact stored bytes before the picker; reports preserve complete returned tables and captured parameters. Host JNI and API 28 Robolectric integration checks pass; real document-provider/handset flows remain pending. See [record contracts and UI scope](native-records.md).
- The selected-member ZIP browser supports nested private snapshots, paginated/filter-preserving Back/recreation, complete bounded inventory, directory revalidation and exact size/CRC32/SHA-256 acquisition. Host fixtures and five API 28 Robolectric cases cover real ZIP payloads and navigation; device/cache-pressure checks remain pending. See [archive acquisition](archive-acquisition.md).
- Google smali/dexlib2 + baksmali 3.0.10: Unicode/MUTF-8 class indexing in the main report; lazy typed Android metadata/instruction/reference/debug/exception views and actual smali exports. Class/method/pool/caller/payload/handler links, complete scoped exports, APK member acquisition and Back/recreation have host/API 28 Robolectric fixtures. A captured multidex index now follows exact type/method/field/handle references to every original declaration across APK members and 041 entries, preserving failed scopes/duplicates and source identities; see [multidex contracts](dex-definitions.md). See [DEX contracts](dex-investigation.md) for exact scope, budgets and limits. No classes are loaded or executed.
- Built-in string classifier 1.0.0: 43 GPL-3.0-only inspectable rules, exact match/byte spans, catalog identity/context and 16 overlapping groups. Whole supported inputs get five UTF-8/anchored UTF-16 passes; DEX uses exact MUTF-8 pool values and encoded-use links. Input/text snapshots, group exports, paging/Back/recreation, cancellation and hex ownership have host/API 28 Robolectric fixtures. No reputation, credential-validity or vulnerability lookup is implied. See [string contracts](string-investigation.md).
- Google binary-resources 32.5.1: complete source-built parser with pinned compatibility patch and bounded wire preflight. Fourteen searchable tables preserve compiled XML/string/style/type/configuration evidence, duplicate attributes/maps and dense/offset16/sparse/compact entries/aliases. Whole-parent APK SHA-256, checked child extraction, local ID links and complete JSON/encoded-byte/UTF-16LE exports have host/AAPT2/API 28 Robolectric fixtures. The private isolated worker is wired with ordinary-UID refusal, while production IPC/platform resolution remains unverified. See [compiled-resource contracts](android-resources-investigation.md).
- Captured APK package sets: current PackageManager base/split paths or one selected APK feed owned whole-file/CRC-checked manifest snapshots. Existing Google/apksig engines supply original package/version/split/SDK/type declarations and signing scopes; exact candidate graphs preserve duplicates, missing/unknown inputs and certificate inequalities without claiming installability or trust. Independent AAPT2/malformed/ownership/model and five real-engine viewer cases pass (83 suite checks). Real inventory paths/SAF/worker/handset and full targeting/signing/framework/runtime policy remain unverified/incomplete. See [package-set contracts](package-set-investigation.md).
- Saved scan option profiles now persist all shared desktop flags/tags/minimum with strict lossless source catalogs, atomic revision checks, CRUD/apply and staged complete exports. Host metadata/failure/budget cases and six Android/actual-CDiE-request cases pass (93 full-suite checks); manual format/tool/Qt compatibility, named batch/report profile attribution and diff, import-review process death and handset storage/providers remain incomplete. See [profile contracts](scan-profiles.md).
- Desktop document batches capture one option/database snapshot and a complete ordered plan before sequential whole-file acquisition and real CDiE/facts/inspection. Persistent journals retain every failed/cancelled/unstarted scope, independent input/report identities, searchable native trees, exact byte ownership and complete staged exports; active leases and canonical saved/damaged-batch deletion bound lifetime. Host and seven real-engine/widget cases pass (100 full-suite checks); directory/background/resumable jobs, diff/profile attribution and real device/provider/crash/durability flows remain incomplete. See [batch contracts](batch-scanning.md).
- Exact binary comparison owns both sources and computes complete aligned byte runs/tails with independent whole-input hashes and exact side/range links. Candidate publication is atomic; failed/cancelled replacement retains the prior pair. Complete staged exports, stale-scope rejection and recreation have independent oracle/100,000-run/failure fixtures plus five comparator/native-hex cases (105 full-suite checks). Semantic/report/package diff, insertion/moved-block alignment and handset/provider/durability flows remain incomplete. See [comparison contracts](binary-comparison.md).
- Captured native scan-tree comparison adds exact original row fields/ancestry/duplicate occurrences, independent source/report/option/rule contexts, original-row links, complete staged exports and explicit failed-scan unknown coverage. Independent random/20,000-deep/budget/failure fixtures and three real-CDiE viewer cases pass (108 full-suite checks). Source-byte/behavior/package semantics, external/cross-batch comparisons and handset/provider/durability remain incomplete. See [scan comparison contracts](scan-comparison.md).
- Complete primary-report XML/CSV/TSV exports share captured state with JSON, retain typed unknown values/diagnostics/all native rows and use reversible string encodings for exact UTF-16. Independent Python parser reconstruction and two additional primary-view cases pass (110 full-suite checks); Qt serialization equivalence and real provider/handset/storage behavior remain unverified. See [format contracts](report-formats.md).
- Future: full framework/split resource resolution, overlay/staged aliases and canonical XML reconstruction; JADX; package diff; optional separate privileged actions only by explicit consent.
- Source-built metadata-extractor 2.22.0 (Apache-2.0) and Adobe XMP Core 5.1.0-jc003 from pinned AOSP BSD source now provide directory/tag/error/XMP views, raw values, complete streamed JSON and binary/text value exports. Host JPEG/EXIF/GPS/ICC/XMP/PNG/WAV fixtures and six Android cases pass. General tag offsets remain unknown; normalized XMP and returned values are distinct from packet bytes. See [metadata contracts](metadata-investigation.md).
- Binwalk 3.1.1 source commit 26713972: actual 111-signature/251-pattern catalog and upstream validators, every-byte/overlapping scans, explicit unresolved headers and unknown extents, bounded stored-byte carving and complete evidence exports. Linux JNI fixtures and ARM64 compilation pass. A private isolated service is wired; ordinary-UID refusal/manifest checks pass, while handset Binder/crash/cancellation flows remain unverified. See [Binwalk contracts](binwalk-investigation.md).
- Actual libmpv media-codec/container probing is integrated with a hash-pinned official Android runtime, complete dependency/submodule source locks and source-built descriptor adapter. Typed file/track/chapter/tag values, unavailable-property errors, full catalogs and offline property mechanisms are exposed; exact value exports and host/API 28 Robolectric fixtures pass. Production isolated-worker/ARM64 execution remains device-unverified. See [media contracts](media-investigation.md).
- Actual PhotoRec 7.2: all 774 upstream catalog patterns, 581 searched at every byte and 193 explicit weak-pattern exclusions; unmodified standalone safe-header/data callbacks, complete raw state/overlaps/negative-relative coordinates and bounded immutable carving. Independent host media/naive-oracle/CRC/budget/ownership fixtures and four actual-JNI Android cases pass (87 full-suite checks), including lossless indexed capture/reconstruction and source-expiry export; a 75,000-hit fixture and real 48 MiB/109,023-row host audit exercise large captures, with ARM64 source compilation and corresponding-source packaging. Full file-check/rename/optional codec callbacks and filesystem/fragment recovery are not executed; real isolated-worker/SAF/handset flows remain unverified. See [PhotoRec contracts](photorec-investigation.md).
- Remaining requested scope: full PhotoRec/JPEG Digger/fragment recovery, anchored weak signatures, full file/integrity checks and exhaustive media coverage. Metadata readers, pattern matches and stored-byte recovery have different evidential meanings; do not substitute labels for actual engine capabilities. MPV probe initialization is not full-file/frame integrity verification.

## Remaining Android metadata limits
APK identity/components in the main summary still use platform PackageManager. The separate package-set tool now correlates captured declarations and verified signer sets; full split installation/targeting/framework/runtime policy remains incomplete. The separate compiled-resource viewer supplies original binary XML/table evidence without claiming device selection or platform acceptance. No resource execution, network, installation or debloating. ZIP enumeration and DEX parsing are bounded; this summary module reads selected DEX streams without extracting files. Unicode/MUTF-8 class names and 041 containers are supported by the pinned reader; compact DEX/ODEX/OAT and full ART validation remain pending. Tracker syntax uses an explicitly bounded compared subset; unsupported patterns and unsearched names/rules mark partial coverage. Rules and signing metadata can be incomplete. Full data and source notices are included at build time; Exodus user-provided data is not redistributed.

Typed DataStructs now retain structure/field parent identities, exact raw and
variant values, semantic flags/unknown bits, comments and native text units.
Field storage is separate from its declared target; reviewed PE RVA and fat
Mach-O slice relationships require captured context. Inventory/selectors and
explicit row/text/input/deadline bounds fail without successful partial data.
Actual native byte/Unicode/IEEE/cancellation fixtures and three Android host
cases pass; complete native field semantics and handset/isolation behavior
remain unverified. See [structure contracts](native-structures.md).

Native entropy is now a complete typed snapshot of whole-file/region/block
measurements and all 256 byte-frequency bins, including zeros. Blocks cover the
entire input; regional overlaps and aggregate histogram domains remain explicit.
Independent byte/log2/IEEE checks and actual legacy API/widget/export cases
verify the returned values; native packed labels are explained as a display
heuristic, not a verdict. Native request cancellation/deadline observation is
shared with Structures; OS process isolation remains pending. See
[entropy contracts](native-entropy.md).

Primary scans now expose CDiE's complete compatible-format choices alongside
Automatic. The chosen name resolves against the freshly acquired input; facts
and scanning use the same actual selector. Reports bind the request, full
catalog, source and engine identity, including explicit unresolved failures.
Stale controls cannot relabel captured results. Known native passes/cache and
three real-engine Android host cases pass. Profiles retain their separate flag-setting policy; batches now capture
and independently resolve their own interpretation names; broader desktop/device equivalence
remains unverified. See [interpretation contracts](scan-interpretations.md).

PhotoRec adapter 3 now streams its complete supported input up to 256 MiB.
Input-read payload buffers stay bounded to 1.25 MiB; data-callback/registry/
evidence/JVM memory are separate. Owned pread descriptors, cross-chunk matching,
header padding, source-change gates and explicit IO metadata preserve exact
coordinates. Sparse and fully written 80 MiB data, 256 MiB limits, every returned
real-APK magic witness and independent source rebuilding are checked. Binwalk
retains its separate 64 MiB limit. The tagged 0.5.0-alpha save point remains
unchanged; this work follows that checkpoint. See [PhotoRec](photorec-investigation.md).

Binwalk adapter 2 now uses owned positional descriptor acquisition, fixing the
shared-FD seek-position regression. Readable regular-file gates, source-change
checks, explicit file-sized-copy/read accounting and a cooperative 30-second
budget are preserved in reports. The 64 MiB copy limit remains separate from
PhotoRec's streaming input limit. Six Rust tests and actual JNI/64 MiB seam/EOF
fixtures verify the IO behavior; a parser callback remains non-preemptible and
real worker/handset behavior remains unverified. See [Binwalk](binwalk-investigation.md).

Desktop batch schema 2 captures an immutable Automatic/compatible-name request
and resolves it per acquired input, with one actual selector shared by native
facts/scanning. Unresolved names retain failed reports and bytes; version-1
Automatic journals remain readable. Stored resolution/source/engine checks and
complete comparisons/exports preserve unknown evidence. Host policy fixtures
and three actual-CDiE Android cases exercise these flows, including invalid
saved-state refusal and pending export ownership (121 full-suite checks).
Arbitrary forcing, per-file overrides, named profile attribution and actual
device/provider behavior remain incomplete. See [batches](batch-scanning.md).

OS Binder registry inspection now owns one normal-UID `service list` capture,
separates parsed observations from every diagnostic/unknown line, and exposes
raw output/envelope identities, exact byte ranges, full scoped exports and the
pinned offline AOSP output reference. Empty descriptors, non-atomic queries and
visibility/count limits stay explicit; recreation never reruns the command.
Trusted-process host checks and three provider/widget cases pass (124 combined
checks). Actual OS output, SELinux/visibility, provider/process-death and handset
behavior remain unverified. See [Binder registry](binder-registry.md).

The file presentation foundation now prioritizes observed type/identity, combines
File 101/Reference into structured File info sections, shares individual/global
definition controls, and groups all existing tools by investigative purpose.
Raw facts and unsupported/partial status remain visible independently of optional
help. Byte-based ordinary media/archive header recognition is expanded, without
claiming full validation/decompression. See [presentation contracts](content-presentation.md)
and the [active Gallerize requirements](gallerize-workbench.md).

Gallerize now owns the exact input and original PhotoRec capture, maps accepted/
bounded/header-only/nested/unassigned content plus gap UTF-8 text, and supplies a
labeled gallery, independent owned-range previews and complete configurable byte/
entropy grids. Complete exports retain raw findings/catalog and cell values.
Four concatenated media files/text, independent grid oracles, original-capture
restoration and a fully written 80 MiB fixture are host checked; platform media/
PDF and actual Android behavior remain separately unverified. See
[Gallerize contracts](gallerize-implementation.md).

## Android worker runtime laboratory

A separate test-only x86_64 module now reuses the unchanged production isolated
worker/client and hash/archive JNI. Five instrumented contracts cover actual
UID/Binder/PFD hashing, fully written 16 MiB-plus independent digest oracles,
embedded archive extraction/client alias refusal, token-scoped backpressure
cancellation and recovery. API 26/35 emulator jobs retain complete evidence
and source/APK identities. Compilation/static checks are implemented; actual
Android execution is pending. This does not verify ARM64, other engines, SAF,
UI, media/PDF or 16 KiB runtime. See [laboratory contracts](../runtime-worker/README.md).

The original five contracts passed on actual API 26 and API 35 x86 emulators
at source `26ad7d6`, run `44729667760`; downloaded complete reports, APK/source
identities and artifact digests were independently checked.
[Verification summary](verification/android-worker-runtime-26ad7d6.json).
The next source adds a separately tested production gallery media-preview
helper; its Android codec case is pending, and it runs in the normal app UID.
See [gallery media contracts](gallery-media-previews.md).

Six contracts now passed on actual x86 API 26/35 at source `8f2ab38` in
run `44733208678`, including the normal-UID bounded platform media helper;
[download-verified evidence](verification/android-worker-runtime-8f2ab38.json).
The seventh compiled-manifest worker/declaration case is implemented, execution
pending. Selected APKs now have an exact-literal-ID community lookup with
independently retained original manifest and input evidence;
[association contracts](apk-community-context.md). Three new host/widget flows
and independent Unicode/ownership/export oracles pass; full source gates pending.


## Actual recovered files

Gallerize now combines PhotoRec/Binwalk over one owned input, defaults to Files,
opens previews/archive contents directly, and exports typed individual files
or an actual-payload ZIP across all pages. Source 123193d passed all five source
CI jobs and seven actual x86 Android lab contracts; pre-alpha.12 is published.
New adapter 4 PDF file checks supply native bounded PDF payloads. All 181 host
cases and ARM64/static gates pass. An eighth actual Bionic/PhotoRec contract is
compiled/static-checked, with Android execution and source CI pending. See
[file gallery](recovered-file-gallery.md) and [PDF recovery](pdf-file-recovery.md).


## Published larger PDF recovery and archive file galleries

Pre-alpha.14 at source `8adac7b` is published after all five source CI jobs,
181 host tests and eight actual x86 API 26/35 contracts passed. Independently
downloaded reports verify native Bionic/isolated PhotoRec recovery of the exact
9,437,636-byte PDF with explicit incomplete optional metadata.
[Evidence](verification/android-worker-runtime-8adac7b.json). PDF rendering and
production ARM64/SAF/handset acceptance remain unverified.

Pre-alpha.15 adds a gallery to each selected native archive level, checked
image/media/text observations, direct member previews, typed individual saves
and an actual-file ZIP over all matching pages/displayed-folder descendants.
Original duplicate/unsafe names, indices, hashes, per-file acquisition reports
and the current native/finding chain remain available; links are not
materialized. Six new actual-libarchive host/widget cases are added; current
full-suite/build/CI gates are recorded in continuation. See
[archive file gallery](archive-file-gallery.md). This is not automatic recursive
decompression of every archive into a single gallery.

The README now focuses on files/workflows and precise published verification.
[F-Droid preparation](fdroid.md) identifies the prebuilt MPV runtime as a
source-build integration blocker and records the release/signing/recipe work
needed. Upstream store metadata is present; no store acceptance is claimed.


Pre-alpha.15 source `2dcfce3` is now published after both five-job source CI
runs, all 188 host tests and eight selected actual x86 API 26/35 contracts
passed. The downloaded public APK and runtime report/source/library identities
are independently verified; see [evidence](verification/android-worker-runtime-2dcfce3.json)
and [release](releases/0.6.0-pre-alpha.15.md). Archive gallery/SAF/production
ARM64/PDF-rendering acceptance remains separate and unverified.


Development pre-alpha.16 unifies PDF viewing through the owned-input
`PdfPagePreview` helper. Recovered and archive PDFs have first-page thumbnails;
the full viewer rasterizes logical point geometry to bounded useful pixels,
with Fit/Actual pixels, horizontal/vertical scrolling and retained page/view/
position. Source identity expands without truncating captured text. Native
file boundaries, transient rendering and structural validity remain distinct.
Guard/widget/exact-byte and original recovery fixtures pass; the ninth actual
Android lab case is compiled, execution pending. See
[PDF preview contracts](pdf-page-previews.md).

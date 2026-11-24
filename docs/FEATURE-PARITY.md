# Feature parity / verification checklist

Status labels: wired = integration exists, not validated on hardware; partial = reduced capability; pending = absent. Host JNI fixtures and Robolectric Android UI checks are identified separately from handset verification. Update on each build and fixture verification.

Latest published checkpoint: **[pre-alpha.18](https://github.com/sperglord8008s/GlassDigestor/releases/tag/v0.6.0-pre-alpha.18)**, exact source `47bde0b`, code **24**. All five source jobs, **213 host Android tests** and **11 actual x86 API 26/35 contracts** pass. [Runtime receipt](verification/android-worker-runtime-47bde0b.json) · [public release receipt](verification/pre-alpha18-release.json). Production ARM64/full UI/SAF and full desktop parity remain separate incomplete acceptance targets. [Current work and project navigation](STATUS.md).

The table retains detailed implemented/partial scopes. Older per-slice counts are historical; the current whole-suite baseline above supersedes them. [Original full historical checklist](history/feature-parity-through-pre-alpha18.md). Current usability/time-limit work is in development until its own checks pass. UI terminology is Information / Details / Source; legacy saved schema names remain compatible.

| Feature | Current target branch |
|---|---|
| CDiE signatures, deep/heuristic/recursive/aggressive/parent formats | desktop backend is primary; host known-rule detection, unmodified bundled GLIBC library detection/exact source, custom-DB gating and tag-filter fixtures verified; full Qt/heuristic parity and device flow unverified |
| File selection + rescan | device feedback confirms earlier build; regressions pending |
| Primary scan report capture / exact JSON / picker lifetime | immutable complete report staged before picker, captured options/input identity retained through later state changes, pending export restored after recreation/source expiry, cancellation/destination cleanup; every UTF-16 code unit round-trips in host fixtures and three main-view Robolectric cases pass; real providers, process death during copy and handset flows remain unverified |
| Upstream PE/ELF/Mach-O DataStructs | host fixtures for ELF, PE32, PE64 and thin Mach-O; malformed headers tested; device flow pending |
| Memory map sections/segments/default | typed host ELF default/sections, PE sections and Mach-O segments; unsupported modes fail explicitly; Android mode capture and unsigned-VA navigation checked with Robolectric/real host JNI; handset flow pending |
| Imports, exports, symbols, resources | typed complete snapshots with searchable 50-row Android pages and per-field details; host ELF/rich PE32/64, named/ordinal imports, forwarders, COFF/UTF-8 symbols verified; 10,000-row paging/search/back navigation checked with Robolectric/real host JNI; handset flow pending |
| Native disassembly with offset and architecture | typed instruction pages and continuation/domain labeling host verified; Android row paging, next-block navigation, manual mode and Back checked with Robolectric/real host JNI; handset flow and control-flow analysis pending |
| Whole-file CRC32, MD4, MD5, SHA1, SHA224, SHA256, SHA384, SHA512 | all eight implemented using pinned xxfclib and unmodified RHash MD4; typed File info viewer plus expanded Native Tools text/JSON/selectors, complete source/coverage/exports and independent acquisition SHA-256 check; RFC/JCA/OpenSSL and descriptor/budget/failure/ownership host fixtures, three actual-JNI Robolectric cases and ARM64 compilation verified; production isolated UID/IPC/deadline/device flows unverified; original CDiE C report API remains four algorithms |
| Unicode strings | wired, upstream limits |
| String categories, matches, explanations and context | implemented: 43 inspectable offline rules/16 overlapping groups; whole-input UTF-8 and anchored UTF-16 candidates plus exact DEX MUTF-8 pools, original bytes/match spans, rule SHA-256/context, paging/search/group exports and caller links; classifier 1.1.0 captured category mechanisms, category-specific Why summaries, highlighted exact matches, eight-occurrence detail pages and original raw evidence implemented; all-rule/encoding/identity/budget host fixtures, ten string and one DEX-classification Robolectric cases verified; broader databases/encodings, reconstructed strings, hard isolation and handset flows pending |
| Full paged read-only hex | wired, 4 KiB pages; table/physical-offset/unsigned-VA cross-links checked with Robolectric/real host JNI; handset flow pending |
| Entropy region analysis, native block statistics | complete typed whole-input/region/block measurements and all 256 histogram bins, exact values/domains/limits, native legacy-label context and active cooperative cancellation; actual JNI/legacy comparisons, independent 18-scope/612-range/4,608-bin byte oracle and two Android host export/navigation cases pass; interactive zoom, broader region/fat semantics and handset/isolation behavior pending; see [contracts](native-entropy.md) |
| APK metadata, declared permissions/components, signing fingerprint | partial |
| LibChecker canonical matching and UAD catalog | all 3,053 active LibChecker v5 matchers/2,934 locale payloads retained from its pinned compiler; all 123 regex compile with source-built RE2/J, exact-first/priority/full-match semantics match 19 upstream fixtures; native/component/DEX-package/compiled-manifest shared-library/permission/metadata/action inputs, complete catalog/UUID views/exports, real AAPT2 names and four Robolectric cases verified; actual SDK code identity/use, fingerprint/version providers, split/framework resolution and device flows remain incomplete/unverified; UAD stays exact package guidance |
| Installed user/system package inventory, base/split APK paths | implemented normal-UID inventory; sectioned labelled Explore rows, non-actionable headings, expanded scope/full-field copy and existing APK/split/package/service/filesystem routes; four API 26/28 host cases and a native-graphics compact preview pass; actual PackageManager/handset acceptance pending |
| Explicit text copy / browser search / English translation | implemented whole-string, exact-match and selectable display-text actions; four search engines and three English translation providers, engine-only preferences, fixed HTTPS query encoding, sensitive exact local clipboard, explicit limits/refusals and no added INTERNET permission; five host contracts plus string integration cases pass; all ten actual x86 API 26/35 contracts pass at 7c80322, including focused exact/sensitive clipboard and all seven URI encodings, with [download-verified evidence](verification/android-worker-runtime-7c80322.json); actual external browsers/providers, selection toolbar and production ARM64 handset acceptance pending; [contracts](text-investigation-ui.md) |
| Shared readable information hierarchy | implemented shared section/identity/labelled fact/code/prose/divider roles across core viewers, lists, galleries, help and Explore; native-graphics 360 × 640 preview checks primary match explanation visibility; all 209 host Android regressions pass; real handset/font-scale/screen-reader acceptance pending |
| Captured base/split APK declaration and signer relationships | normal-UID inventory handoff and single-file entry; owned whole-APK/manifest SHA-256 plus CRC/size acquisition, exact package/version/split groups, every dependency/type candidate, duplicate/unknown scopes and verified certificate DER-set comparisons; complete metadata and APK/manifest/certificate exports with recreation; independent AAPT2/malformed/CRC/cycle/budget/cancel/identity/Unicode cases and five actual-engine viewer flows pass (83 suite checks); real installed-path/SAF/worker/handset behavior, installability/signing policy, archive-set import and framework/cross-split runtime resolution remain incomplete/unverified |
| Read-only permitted filesystem and declared service inventory | wired |
| OS Binder service registrations | typed immutable normal-UID command captures, explicit registration/diagnostic/unknown separation, exact source/output identities, original rows/ranges, complete exports and pinned offline AOSP reference; trusted-process IO/parser/ownership fixtures and three Android host cases pass (124 full-suite checks); actual OS command/UID/SELinux/SAF/handset behavior remains unverified; see [contract](binder-registry.md) |
| Live process/service diagnostics and privileged files | pending; explicit bridge required |
| ZIP/APK nested member selection/extraction | complete bounded inventories, searchable 50-row folder pages, nested Back/recreation, private extraction with full-directory revalidation, size/CRC32/SHA-256 checks; host malformed/stale/duplicate/cancel fixtures and five Robolectric navigation/lifecycle/budget cases verified; handset flow and persistent cross-scan parent history pending |
| Broader archive engine / codecs | implemented native worker/typed source-bound inventory, content-based virtual tree/All entries, indexed duplicate-name acquisition, nested Back/retention, explicit embedded scope, checked preview/member return and complete native-chain/failed-attempt exports; pinned libarchive 3.8.11 + four source-built codecs/platform zlib; 25 captures / 37 payloads independently verified at core and model levels, plus five actual-JNI API 28 browser cases, Main/CDiE member-result boundary and worker refusal/descriptor guards pass; full 147-case suite, ARM64 assembly/lint and fifteen-library APK static checks pass; all five source CI jobs passed and pre-alpha.3 published; real UID/Binder/SAF/handset, exhaustive reader subtype coverage, arbitrary deobfuscation/passwords and current-chain per-preview origin captures implemented; all-visited/cross-Activity history remains incomplete; device behavior remains unverified; see [contracts](native-archives.md) |
| Native resource payload extraction | selected resource or manually validated physical range staged for exact SAF byte export; host payload equality and Robolectric picker-delay/recreation/output checks verified; real document-provider flow pending |
| Practical / technical package knowledge | implemented: all 5,402 original pinned UAD-NG record occurrences / 5,399 distinct IDs, all unknown fields, three explicit duplicate-ID pairs, practical descriptions, contextual field meanings, full-value search, source categories and exact directed/reverse relationship occurrences; installed-ID and file-independent entry points, complete paged records/fields/source exports and snapshot recreation; independent actual-corpus/Python/all-UTF-16/numeric/ownership/refusal fixtures, six Android host cases and 167-case full suite pass; real device/SAF/PackageManager behavior, selected-APK/signer correlation, user overlays, more sources, version conditions and transitive/runtime analysis remain incomplete/unverified; see [contracts](package-knowledge.md) |
| Binary manifest/resource tables | source-built Google binary-resources 32.5.1 plus bounded wire inventory; fourteen tables cover chunks/pools/styles, XML events/duplicate attributes, packages/configurations, dense/offset16/sparse/compact entries, absent/aliased slots and every duplicate bag mapping; local resource-ID/value links, whole-parent APK SHA-256/CRC acquisition and complete JSON/encoded-byte/UTF-16LE exports; actual engine/report/ownership/boundary host fixtures, independent ordinary/sparse/deduplicated AAPT2 references and five Robolectric cases verified; opaque overlay/staged aliases, canonical XML reconstruction, framework/split resolution and production worker/handset flows remain incomplete/unverified |
| DEX metadata, Unicode class names and bytecode | pinned Google smali/dexlib2 + baksmali 3.0.10; Unicode/MUTF-8 report indexing and explicit integrity/table evidence; lazy Android tables, classified strings, payloads/debug/handlers, reference/caller links, exact offsets, range actions and actual method smali; classic 035–040/041 shared containers host verified; four report and seven viewer Robolectric cases verified; compact/optimized formats, inherited/framework/cross-split resolution and handset flow pending |
| MPV/media codec and container inspection | actual libmpv/FFmpeg integration, official hash-pinned mpv-android 2026-09-17 ARM64 runtime plus source-built descriptor probe; 53 audited file/engine getters, complete typed descendants/errors, track/chapter/tag views, codec/container/property catalogs and offline source references; WAV/Matroska/PCM/FFV1/SubRip/Unicode/large-tag/reference-HTTP/ownership/export host fixtures, four actual-JNI Robolectric cases and ARM64/static 16 KiB packaging verified; production Binder/isolated UID, ARM64 codec/device runtime, broader media and complete decoding remain unverified |
| Exact multidex declaration index / cross-file navigation | owned whole-parent SHA-256 plus directory/CRC/size-checked DEX snapshots; original member/041 entry/class/declaration indices, overload/field-type matching and identifier-owner checks; every duplicate/failed scope retained, correct-file table/bytecode/smali navigation, source-byte export and complete staged reports; host real-Dex/CRC/foreign-owner/encoded-duplicate/identity/budget/cancellation/041 fixtures and four API 28 cases verified; class loading, inheritance, framework/other-split linkage, hard isolation and handset flows pending |
| Binwalk firmware/embedded signature analysis | actual unmodified Binwalk library source at 26713972 (3.1.1), 111 signatures/251 patterns; whole-blob overlapping/short-pattern scans, accepted/rejected/panicked evidence, complete catalog/groups/exports; adapter 2 adds positional owned-FD acquisition, source/read accounting and cooperative budgets, with six Rust tests and actual JNI/64 MiB seam/EOF regressions; Linux JNI fixtures and ARM64 cross-compilation verified; private isolated service wired with manifest/ordinary-UID refusal checks; actual Binder/UID/device flow and broader format validation pending |
| Whole-blob media/resource carving (JPEG recovery / PhotoRec-style coverage) | PhotoRec adapter 3 streams up to 256 MiB with 1.25 MiB input-read payload buffers, exact source/descriptor/window contracts, sparse/fully written scale and source-rebuild fixtures;  actual Binwalk plus source-built PhotoRec 7.2 header/data callbacks; full 774-pattern PhotoRec catalog with 581 every-byte searches and 193 explicit weak-pattern exclusions, nested/overlap/relative/negative/rejected/unknown evidence and bounded immutable ranges; JPEG/PNG/GIF/WAV/naive-oracle/CRC/truncation/FD/budget/export host fixtures plus 75,000-hit/lazy/full-export/reconstruction checks and four actual-JNI viewer cases pass (87 suite checks); actual 48 MiB/109,023-row host capture completes; full PhotoRec/JPEG Digger, file checks/fragmented/filesystem recovery, exhaustive media and handset flows pending |
| EXIF and comprehensive collected file metadata | source-built metadata-extractor 2.22.0 + AOSP BSD XMP 5.1.0, complete directory/tag/diagnostic/XMP-node views, typed values/relationships, full streamed JSON and exact binary/text exports; JPEG/EXIF/GPS/ICC/XMP, PNG and WAV host fixtures and six Android metadata cases verified; other dispatched readers integrated/unverified; unsupported/identification-only states explicit; broader formats, packet byte locations, isolation and handset flow pending |
| Offline imported Exodus tracker signatures / complete metadata | independent RE2/J regex-search adapter over original slash class descriptors, >3-code-point upstream gate, frozen Unicode 15 properties, explicit unsupported/short/unsearched records and budgets; complete unknown/root metadata, exact original record/snapshot exports, strict duplicate/UTF-8/depth checks and atomic failed-import preservation; 924 actual pinned-upstream-method comparisons, exhaustive Unicode property fixtures and five API 28 integration cases pass; full Python syntax, upstream dexdump/nested-APK equivalence, separate isolation and handset behavior remain incomplete/unverified |
| AOSP apksig signing schemes / rotation / complete collected certificates | owned typed v1/v2/v3/v3.1/v4 signer, certificate/DER/SPKI/extension, scoped lineage/capability, content-digest, source-stamp and diagnostic tables with full exports; original API ranges/identities retained and failed-v4 upstream aggregate flag guarded; actual RSA/EC/DSA, rotation/capability/stamp/malformed/ownership host fixtures and four API 28 cases verified; exhaustive algorithms, API 36, split policy, hard isolation and handset/platform behavior pending |
| v4 detached .idsig handling | explicit offline selection/replacement/clear with full input SHA-256, allocation-length gate, original layout/optional tree/trailing-byte coverage and exact export; actual v4/v4.1, corrupt/mismatched/truncated/tree-omitted/trailing/oversized/cancel fixtures and pending-picker/source-expiry/recreation UI verified; actual SAF/platform incremental installation and handset behavior unverified |
| Signature database browser, source, temporary editor, single-rule testing, profiling, tag filters | source retrieval, known edited-rule test and include-tag filtering host verified; profiling and device flow pending |
| Custom database import and persistence | local ZIP snapshots, SHA-256 identity, native load check, activation/rollback and original-ZIP export wired; host import/CRC/path/size/persistence tests and native activation/tag/reload fixtures verified; Android picker/restart flows pending |
| Full original Qt DiE rule compatibility comparison | pending |
| Searchable typed evidence with source offsets and structured tool exports | desktop detection tree plus eight native record types and dedicated DEX/string/metadata/media/resource/hash models; search/50-row pages, field details/help and 64-location Back history implemented; captured parameters, full-table exports, staged range exports and recreation checked with host/Robolectric fixtures; remaining legacy strings/hex outputs also have dedicated classified/byte views; typed structures include raw/variant values, semantic flags, parent links and reviewed field domains; handset flow pending |
| Gallerize content map / labeled gallery | implemented: owned input/original PhotoRec capture, source/type/composition summaries, accepted/header-only/nested/overlap/unassigned and strict UTF-8 cards, original witnesses, 24-card group pages and complete native/catalog exports; actual four-file/text, corruption/restoration and fully written 80 MiB host fixtures; eight Android host cases cover gallery/preview/grid/lifecycle/export paths and real nested-ZIP handoffs; direct card archive parsing/manual interval actions and explicit grid/inspection origins now implemented with three additional host cases and a real PhotoRec → libarchive chain; handset and broader discovery/rendering remain unverified/incomplete; see [contracts](gallerize-implementation.md) and [range actions](gallery-inspection-ranges.md) |
| Byte / local entropy visual grids | implemented: complete streaming mean/entropy measurements, exact row-major cell partitions, automatic and common/custom layouts including 640 × 640, persistent manual overrides, touch-to-original-hex and full values/IEEE exports; independent byte/log2/partition and 80 MiB host checks plus Android widget cases; actual handset graphics/performance remain unverified |
| In-app media/text/document previews | bounded first-frame images; full-input UTF-8/Hex page navigation, exact Unicode seams, byte jumps/Tail/history/recreation and owned ranges with metadata/MPV/tool links; independent 24 MB text / 161,892-page fixtures and five additional host cases pass; generated text snapshots are paged without rerendering and exports retain all bytes. Image/media/PDF thumbnails are implemented; selected platform media/PDF helpers pass actual x86 API 26/35 contracts separately. Handset playback, animation, broader encodings/documents and MPV playback fallback remain incomplete/unverified; see [text contracts](text-paging.md) and [PDF contracts](pdf-page-previews.md) |
| PDF page thumbnails / bounded in-app reading | implemented shared owned-input/descriptor renderer, first-page Gallerize/archive thumbnails, explicit point/pixel geometry, exact embedded staging, 32 MiB automatic-thumbnail / 1536-pixel full-view bounds, typed saves retained, Fit/Actual pixels with two-axis scrolling/recreation and compact expandable source identity. Six new guarded-renderer/widget cases and a real-libarchive PDF case pass; independent xref/color oracle and native/generic regressions pass. Published pre-alpha.17 source 7c80322 passes all 209 host tests and both five-job source CI runs. All ten actual x86 API 26/35 contracts pass, including native recovery then known red/green rendering, with [download-verified identities/pixels](verification/android-worker-runtime-7c80322.json). Renderer time/internal memory, production ARM64/handset/SAF/full UI and arbitrary-document acceptance remain unverified; see [contracts](pdf-page-previews.md) |
| PDF file recovery | implemented adapters 4/5 with original footer/linearized-size checks via bounded read-only stdio; adapter 5 retains a positive native footer after classified optional metadata-work exhaustion, with strict IO/deadline/legacy/count/scope refusals. Exact >9 MiB gallery/save and Python byte/xref tests pass. Published pre-alpha.17 passes all 209 host/native/80/256 MiB/ARM64/lint/APK/source CI gates at 7c80322. Actual Bionic/isolated PhotoRec complete/truncated/9,437,636-byte PDF contracts pass on x86 API 26/35 with [downloaded identities](verification/android-worker-runtime-7c80322.json); selected platform rendering is verified separately. Production ARM64/SAF/handset, other full file checks and fragments remain incomplete/unverified; see [contract](pdf-file-recovery.md) |
| Archive member gallery / actual file bundles | implemented shared tree/gallery/filter selection, 24-card checked image/media/text/PDF observations, direct preview, byte-hinted typed saves and payload ZIP across every matching page/displayed folder descendant. Complete acquisition/current-chain reports, duplicate/unsafe original names, index/hash identities and explicit link/directory exclusions retained. Actual-libarchive/host cases cover 30 files, source-expiry picker recreation, 33 MiB automatic-preview refusal, cleanup, misleading PDF suffix/exact saves and a ≥200 px gallery pane at 360×640. All 209 host tests, generic/native checks, both five-job source CI ARM64/lint/static APK and source-bundle gates pass at 7c80322; pre-alpha.17 is published. Selected actual x86 worker/helper contracts pass separately. Actual ARM64 gallery/SAF/codecs and automatic recursive member discovery remain incomplete/unverified; see [contracts](archive-file-gallery.md) |
| Recovered-file gallery / payload exports | implemented combined PhotoRec/Binwalk discovery, default Files, direct previews/archive browser, typed individual files and payload ZIP across all pages. Exact JPEG/PNG/WAV/GIF/gzip/MP4/text, duplicate/unknown/overlap/29-image/recreation and fully written 80 MiB beyond-64-MiB file export checks pass; published pre-alpha.17 passes 209 host tests and all five source CI gates. Actual x86 native PDF recovery and selected page rendering are separately verified. Handset SAF/full-format rendering, complete PhotoRec file checks and automatic recursive decompression remain incomplete; see [contracts](recovered-file-gallery.md) |
| Checked recovered-content previews / exports | Binwalk/PhotoRec bounded findings acquire independent exact previews, parent-identity-checked recovery exports, safe suggested names and private whole-input gallery handoff; preview byte export retains immutable picker state through source expiry/recreation; four actual-JNI Android host cases plus pure ownership/mutation/cancel and existing gallery/carving cases pass; 139-case full host suite, ARM64 assembly, lint and static APK checks pass; CI for this slice is pending, real SAF/UID/codec/crash/cache flows and persistent native-origin sidecars remain incomplete/unverified; see [contracts](recovered-content.md) |
| Comprehensive per-field documentation and investigative guidance | partial; shared individual/global expandable definitions in Main, native tools and investigation views; visible raw facts/failure/coverage statuses and complete guide/exports retained; content-first grouping/identity and three real-Main cases implemented; Gallerize/gallery/grids have separate implemented/verified rows; broader archives and hardware flows remain pending/in progress; see [presentation contract](content-presentation.md) |
| Cooperative cancellation and result cache | desktop cache hit and byte-change invalidation host verified; request-owned scan cancellation and Java streaming cancellation host verified; typed table queued-cancel/recovery checked with Robolectric/real JNI, active table calls settle before cancellation is shown; active scan interrupt latency and handset lifecycle tests pending |
| Typed native structures and fields | actual CDiE PE/ELF/thin/fat Mach-O visitor, exact descriptors/values/text units, parent/storage links, reviewed RVA/slice references, selectors/inventory, budgets and active cooperative cancellation; independent native/Python byte and Unicode/IEEE/bounds fixtures and three actual-JNI UI cases pass; broader formats, per-field semantics, Qt parity and device/provider/isolation behavior remain incomplete/unverified; see [contracts](native-structures.md) |
| Primary compatible-format selection | actual native compatible catalog, explicit facts/scan selector, source-bound captured reports, rescan-by-name/state, new-input Automatic and stale-dialog rejection; native known-rule/All-types/cache separation and three actual-engine UI/export cases pass; arbitrary forcing, interpretation profiles/per-file batch overrides, Qt settings and handset/provider flows remain incomplete/unverified; see [contracts](scan-interpretations.md) |
| Saved scan option profiles | complete named snapshots of all 14 shared flags, tag filters and preview minimum; strict lossless catalog imports/exports, immutable IDs, revision-checked atomic saves, apply/rename/update/delete and staged picker lifetime; host/file/malformed/metadata fixtures and six actual-widget/one actual-CDiE-request cases pass (93 suite checks); manual format/tool settings, Qt settings compatibility, named batch/report profile attribution and broader semantic/report diff, power-loss/import-approval process death and handset flows remain incomplete/unverified |
| Desktop document batches | captured 64-input plan, actual serial CDiE/native facts, full input/report identities, fixed settings/revisions, checkpointed outcomes, cooperative request-owned cancellation, complete exports, persistent owned byte views and saved/damaged-batch deletion; schema 2 adds captured interpretation names, per-input native catalogs/selectors, unresolved reports and version-1 compatibility; host failures/budgets/mutation/leases/interpretation policies and thirteen actual-engine/widget/viewer cases pass (121 full-suite checks); directory/background/resume/cross-batch semantic diff/arbitrary forcing/per-file overrides/profile attribution, power-loss and handset/provider flows remain incomplete/unverified |
| Exact binary byte comparison | complete aligned-byte runs/tails with whole-input identities, independent side/range coordinates, staged complete exports and atomic candidate replacement; independent naive/random/seam/100,000-run/budget/publication/retention fixtures and five actual comparator/native-hex cases pass (105 full-suite checks); insertion alignment, moved content, semantic/report/package diff, Qt equivalence and handset/provider/durability flows remain incomplete/unverified |
| Complete JSON/XML/CSV/TSV scan exports | shared complete captured state, typed XML and reversible tabular metadata/native rows, exact UTF-16, unknown values, MIME/extensions, pending picker ownership and explicit bounds; independent Python standard parser reconstruction/all-code-unit/20,000-row cases and two added primary-view cases pass (110 full-suite checks); real providers/process death/storage and Qt serialization equivalence remain unverified |
| Captured native scan-tree comparison | exact original fields, ancestry and duplicate sibling occurrence order; input/options/rule/report identities, matched/one-sided/uncompared tables, original-row links and complete staged export; independent random/deep/unknown-field/failure/budget fixtures and three additional actual-CDiE viewer cases pass (108 full-suite checks); failed scans retain unknown coverage; external/cross-batch/full semantic/package comparison and handset/storage flows remain incomplete/unverified |
| Broader semantic comparison, isolated native worker | program/package/structure semantics and external/cross-batch comparison pending. Private worker integration exists for Binwalk/PhotoRec/MPV/resources/hashes; actual x86 API 26/35 lab verifies selected hash/archive/PhotoRec/resource contracts and separate normal-UID media observations at 8adac7b. Binwalk/MPV transport, production ARM64/handset/wider crash modes and remaining parser migration remain pending |
| Android worker runtime laboratory | separate test-only x86_64 APK reuses production worker/client plus source-built hash/archive/PhotoRec JNI and shared resource/media/PDF helpers. Nine contracts pass on actual API 26/35 at 7c80322 in run 44967525584; downloaded source/APK/library/full-report/completion identities and expected pixels independently verified. Includes normal/isolated UID, descriptors, cancellation/recovery, compiled manifest, bounded media observations, complete/truncated/>9 MiB PDF recovery and known red/green PDF page rendering. Production ARM64/SAF/full UI/16 KiB runtime and other engines remain unverified; see [contracts](../runtime-worker/README.md) |
| Bounded gallery video/media observations | exact stored-interval FD input, raw/null platform MIME/duration/geometry/rotation/bitrate/track metadata, API 27+ scaled keyframes and explicit API 26/codec-unavailable states; transient source-identified details separate from original findings; 175-case host suite plus final frame-budget guard rerun, ARM64/lint/APK checks and test-APK compilation/static verification pass; all five source CI jobs and actual API 26/35 generated video/audio checks passed at 8f2ab38; independent downloads match, API 35 24×18 green pixels and API 26 metadata-only behavior verified; no inferred unknown extents or full playback/validity claim; see [contracts](gallery-media-previews.md) |
| Captured APK → sourced community knowledge | selected-APK and per-source exact literal-package handoffs; independent 1 MiB evidence capture retains APK/manifest identities, original attribute, parser and signer context, all duplicate community claims and full exports across parent expiry/recreation; strict ownership/UTF-16/budget/raw-number host oracles and three new actual AAPT2/signing/widget flows pass (18 focused checks); all 178 host tests, required generic/native checks, ARM64/lint/APK gate, final once-only recreation check and test-APK compilation/static verification pass; five-job source CI and seven-case emulator execution pending; ID association does not verify publisher/version/device behavior or recommend removal; see [contracts](apk-community-context.md) |
| Device testing, regression fixtures, license audit, reproducible signing | pending |
| Packaged ARM64 APK signing/identity, offline assets and static 16 KiB alignment | build-time verification script and CI gate; actual 16 KiB Android runtime/device verification pending |
| Numbered debug builds / project identity | GlassDigestor name/icon, numbered prerelease/version codes, source/dirty debug names, build details and immutable checkpoints implemented. Published pre-alpha.15/code21 passed all five source CI jobs and exact downloaded APK identity/signature/assets/static alignment checks. All previous checkpoints remain unchanged. Stable production signing, installed launcher/update/handset behavior and reproducible release builds remain unverified; see [F-Droid preparation](fdroid.md) |

A functional alpha may have pending items. Do not describe it as a full port until parity is verified.

See [continuation instructions](continuation.md) for the build/check commands, implemented-versus-verified scope and next work.

## Automated verification
Linux host JNI fixture job exercises ELF structures, section and default maps, imports, exports, symbols, disassembly, hash comparison and strings. Deterministic PE32, PE64 and thin Mach-O fixtures check structures, supported maps, entry-point mapping and decoding. Truncated ELF/PE/Mach-O headers must return explicit errors. Minimal DEX and ZIP fixtures check recognition only, not full format validity. This complements Android ARM64 cross-compilation and does not verify handset behavior.

Desktop-backend fixtures validate candidate types, parser facts, loaded signature inventory, known custom-rule detection, the unmodified bundled GLIBC rule's detection and exact source on the dynamically linked fixture, source retrieval, edited-rule execution, custom-DB gating, include-tag filters and cache identity behavior. `scripts/test-models.sh` checks immutable settings, strict tree parsing, paging/search and structured report round trips. It uses a SHA-256-pinned host-only org.json JAR (`JSON_JAR` supports offline testing); the app uses Android's built-in org.json.

Typed native table and range contracts, limits and additional fixtures are documented in [native-records.md](native-records.md). JNI fixtures additionally check 10,000-record preservation/over-limit errors, high unsigned VAs, IAT offsets versus symbol-record offsets, virtual tails, stored payload extraction and instruction-page continuation.

`scripts/test-workbench-ui.sh` builds Linux JNI and runs fourteen native-workbench Robolectric API 28 Android UI/lifecycle cases against it: 10,000-row search/paging/Back; immutable resource export across picker delay and recreation; filtered full-table report export; manual disassembly mode and separate row/block pages; exact resource-range digest export; displayed-versus-edited mode preservation across Back/recreation; unsigned preferred-VA navigation; queued table cancellation/recovery; usable map/disassembly result space at 360×640. The native methods are not mocked. The document provider is a test output stream, so this does not verify a real SAF permission grant, picker implementation, ARM64 runtime, handset layout or API 26/35 lifecycle behavior. Android CI runs these checks in addition to ARM64 compilation, lint and APK verification. A separate JVM verifies explicit errors when JNI is unavailable.

The same UI command also runs five archive cases with real ZIP streams: parent folder/filter/page restoration; nested recreation and leaf ownership; invalid nested ZIP recovery; queued cancellation/reload; and the 16-level limit. Host acquisition fixtures run in `scripts/test.sh`. See [archive-acquisition.md](archive-acquisition.md) for exact budgets, supported methods, ownership and remaining ZIP/provenance gaps. These checks are separate from the native DEX/ZIP identification-only fixtures.

Four DEX report cases, seven DEX viewer cases and six string viewer cases bring the suite to 31 API 28 Robolectric checks. They exercise actual smali output, string rules/streams, relationships, staged complete exports, owned input/hex lifetimes, cancellation and Back/recreation. See [DEX contracts](dex-investigation.md) and [string contracts](string-investigation.md). This does not verify handset behavior or real document-provider grants.

Six metadata cases extend the suite to 37, exercising source-built EXIF/XMP/ICC data, raw rational/parent facts, XMP members/qualifiers/search/Back, complete selected-record and binary exports, input expiry/recreation, XML DTD rejection and cancellation/ownership. Host metadata fixtures additionally check PNG/WAV, 1,005 ancestry items, full binary chunks/typed values and XML boundaries. See [metadata contracts](metadata-investigation.md).

Two binary-worker checks extend the suite to 39: the service is private/isolated in the manifest, and an ordinary UID is refused without native fallback. Real Binwalk JNI fixtures separately check overlap/extent/catalog/fd/budget/export contracts; they do not verify an isolated Android UID or Binder lifecycle. See [Binwalk contracts](binwalk-investigation.md). CDiE/DEX/metadata process isolation remains pending.

Four actual-host-MPV viewer checks extend the suite to 43: typed container/track/reference/key-value search; staged exact metadata bytes across input expiry/recreation; decoder paging/recreation and complete exports; unavailable/unsupported states and snapshot cleanup. Only OS worker transport is substituted. Real host media fixtures separately exercise codecs/chapters/long Unicode metadata/descriptor ownership/reference blocking. Android libraries remain a separately pinned runtime requiring handset verification. See [media contracts](media-investigation.md) and [engine/source build instructions](../media-engine/README.md).

Five actual-source-parser compiled-resource viewer checks extend the suite to 48: duplicate XML attributes/string links/Back; resource configurations/aliases/compact ranges/duplicate maps; 2,051-string paging/full-value search and complete filtered export; exact UTF-16LE staging across source expiry/recreation; and failed candidate recovery/ownership cleanup. Only OS worker transport is substituted. `scripts/test-resources.sh` verifies the source/patch/NOTICE identity, malformed/modern/legacy wire fixtures, real Google reports and APK provenance. Android CI additionally requires ordinary and optimized sparse/collapsed/deduplicated AAPT2 references, comparing element/attribute counts with AAPT2 itself. This does not verify production Binder/isolated UID, platform acceptance or device resource resolution. See [compiled-resource contracts](android-resources-investigation.md).

Three actual-JNI hash cases extend the suite to 51: every digest/source with no invented storage offsets; full filtered export across source expiry/recreation; and the existing Native Tools hash entry with all eight algorithms, SHA-512 selection and freshly measured export identity. OS transport alone is substituted. `scripts/test-hashes.sh` separately checks all RFC MD4 vectors, independent JCA/OpenSSL comparisons at padding/block/read boundaries and native descriptor/size/deadline/no-prefix failures. The hash adapter is a separate small native library, independent of the CDiE parser bridge. See [hash contracts](hash-investigation.md); actual worker/device behavior remains unverified.

Four canonical-knowledge cases extend the suite to 55: complete rule pages/locales/UUID Back/recreation; complete filtered export and catalog reload after selected-file expiry; actual archive regex/derived DEX package associations; and AAPT2 manifest component/action/static-library matching/cleanup through the real resource parser with only OS transport substituted. `scripts/test-knowledge.sh` verifies all active canonical rules/details, all 123 compiled regex, 19 upstream cases, real rules and no-detail semantics, original object exports and non-backtracking/length/cancellation boundaries. See [SDK contracts](sdk-investigation.md). Name matching is not verified SDK implementation/use or handset behavior.

Three primary-export cases extend the suite to 58: actual input/Google-decoded strings preserve report identity, options and exact Unicode through picker delay, state mutation, source expiry and recreation; picker cancellation removes only its private stage and permits retry; destination failure removes staging without regenerating changed data. Host models round-trip all 65,536 UTF-16 units in JSON keys/values. The shared streamed writer also passes resource/metadata/MPV regressions. These use test output providers, not real SAF permissions or handset/process-death validation. See [report contracts](report-schema.md).

## Desktop upstream gap audit

Compared with the pinned Detect-It-Easy README, CDiE README, `docs/DATABASE.md` and `docs/LIMITATIONS.md` (engine `f055daa`, database `7ef816e`):

- PE native/.NET heuristic analyses, anti-analysis, Authenticode correlations and format-integrity checks are database-driven. Loading these rules is implemented; equivalence of their complete output with Qt DiE is **unverified**. CDiE documents an additional VMProtect heuristic result compared with the reference engine.
- The pinned CDiE C hash report still provides four algorithms. The Android whole-file hash adapter/viewers now provide all eight CRC32/MD4/MD5/SHA1/SHA224/SHA256/SHA384/SHA512 results with independent fixtures, including standard empty-file digests where older desktop versions can return blanks. Fuzzy/signing/import/format-specific hashes are separate features and are not implied by this coverage.
- Archive scanning expands ZIP-family containers with upstream limits; 7z/RAR/CAB/ISO member expansion and ZIP64 remain unsupported by this backend. Some upstream resource/temporary-space failures skip members without a diagnostic; exports explicitly disclose this coverage limit.
- Directory/tree scanning and background/resumable batches, broader semantic/structure/cross-batch comparisons and alignment, full Qt profile/settings compatibility, demangling, debug-file/DWARF/PDB decoding and control-flow navigation remain pending in Android.
- Native DataStructs cover PE/ELF/thin and selected fat Mach-O slices; other formats are not thereby fully parsed. Separate smali and Google resource adapters now supply DEX metadata/instructions and binary XML/resources.arsc evidence. Compact/optimized DEX, ART/platform validation, overlays/staged aliases, split/framework resource resolution and broader Android format parity remain incomplete.
- Automatic network database updates are deliberately absent. Bundled data is pinned and installed by revision; custom local import is tracked separately below.

## Offline custom databases

The laboratory opens **Offline custom databases**. Import ZIPs with supported format folders at the root or below `db/` or `db_custom/`. The importer rejects unknown/nested format folders, non-script members (except metadata/notices), unsafe paths, duplicate logical entries, corrupt CRCs and oversized inputs rather than silently discarding them. Limits: 32 MiB compressed archive, 64 MiB expanded bytes, 1 MiB per file and 10,000 entries. Helpers, `_metadata` and license notices are retained; a snapshot must contain at least one non-`_init` format rule.

Each archive gets a private SHA-256-named immutable directory. The complete original ZIP is retained for exact export. Native inventory must load custom scripts before the active pointer changes. Activation enables the shared Custom DB option; toggling that option off retains the selected snapshot. Older snapshots support rollback and are retained; storage reclamation UI is pending. A new directory forces native database/cache invalidation. Import/load does not validate every rule's syntax, licensing or correctness: use laboratory source/test on known inputs. Snapshot archive hashes and enabled state are captured in reports. Native imports do not execute the inspected application. Native JS/parser isolation remains pending.

`scripts/test-apk.sh` generates disposable RSA/EC/DSA keys and actual binary-manifest signatures, rotation/v3.1/v4/v4.1 and source stamps, with complete metadata/ownership/negative fixtures. Published binary/source archives and reviewed contracts are SHA-256 pinned; `APKSIG_JAR`, `APKSIG_SOURCES_JAR` and `JSON_JAR` support offline tests. Pinned apksig omits v4 signer errors from its aggregate error/status decisions; the adapter preserves these scopes and requires successful v4 verification for a supplied detached input. Reports check manifest minimum through API 35, without claiming device acceptance or API 36. See [signing contracts](apk-signing-investigation.md).

Five tracker cases extend the Android suite to 63: full imported catalog paging/search/metadata/diagnostics/Back; complete long original-record exports across replacement/recreation; exact snapshot export after import deletion/recreation; actual DEX/ZIP report search with all matching rules; and the real import entry rejecting duplicate keys while retaining the prior snapshot. The shared viewer now dispatches root binary/text actions correctly. Host fixtures additionally compare 924 searches against actual pinned Exodus methods and exhaust frozen Unicode 15 properties. Real SAF/provider, handset/API 26/35 and process-isolation behavior remain unverified. See [tracker contracts](tracker-investigation.md).

Tracker adapter 2 uses a bounded shared Aho-Corasick required-literal prefilter,
then actual RE2/J verification; complex/short-atom/capacity cases retain ordinary
regex search. Rules expose exact required atoms/index status, with measured
index/regex work and unknown coverage on exhausted traversal. Host differential
fixtures preserve every result/first witness/span against unfiltered searches
across 191 mixed patterns; 400 rules over 30,003 descriptors finish with three
regex searches. All 428 active patterns of the separately audited public
snapshot qualify for 3,139 index states. Device performance remains unverified.

Build dependency checks now stage and verify downloads before publishing cache
files, retain checksum rejection for existing inputs, refuse missing offline
dependencies and share public CI inputs between test steps. Interrupted/invalid
download fixtures and real offline signing/DEX/AAPT2 references pass. This
addresses a reproduced Maven HTTP 429 failure without bypassing a test or pin.

Four multidex cases extend the Android suite to 67: exact cross-member method navigation with all ambiguities after parent expiry; 129-class paging/source search/Back/recreation; complete filtered definition exports across deletion/picker recreation; and exact acquired-source exports. Host fixtures additionally exercise return/parameter/field overloads, method/field handles, encoded declaration duplicates, shared 041 class/method/code addresses, CRC/owner/parent/cache identity failures, candidate/aggregate reservation budgets and late-cancel publication cleanup. The parsers are real; document output streams are test doubles. Handset/real SAF/runtime linking remain unverified. See [multidex contracts](dex-definitions.md).

Four signing cases extend the Android suite to 71: actual targeted signer/lineage/certificate relationships and DER export; pending/selected detached input recreation after parent expiry with exact byte export; corrupt-v4 diagnostics plus complete filtered exports/clear; and picker cancellation/oversized replacement preserving the previous pair. All use real generated signatures/apksig; provider streams are test substitutes. This does not establish real SAF/handset/Android installation or rotation/v4 behavior.

Package-set verification adds three compiled-manifest cases, four document/model cases and five viewer cases, bringing the suite to 83. Independent AAPT2 bytes and actual apksig signatures feed the real parsers; only inventory/worker/provider transport is substituted. Cases retain original/failed/duplicate scopes, full version bits, every captured candidate, wrong-kind/empty targets, CRC rejection, cancellation/identity/reservation failures, exact owned bytes and complete Unicode exports. See [package-set contracts](package-set-investigation.md). These are declaration relationships, not device installation/runtime decisions.

PhotoRec adds three actual-host-JNI viewer cases, bringing the full suite to 86 with zero failures/errors/skips: bounded versus unresolved evidence and relative coordinates; exact carving through source expiry/picker recreation; and all 774 catalog rows through paging/filter-independent exports, including 193 explicit search exclusions. Host fixtures independently compare all 77 raw occurrences, media extents and unknown/CRC/budget/ownership semantics. The source-built ARM64 adapter has a private descriptor-only worker path; normal-UID refusal now includes PhotoRec. Static assembly/alignment and Robolectric are separate from real isolated UID/SAF/crash/cancellation/runtime verification. See [PhotoRec contracts](photorec-investigation.md).

PhotoRec adapter 2 adds one capture lifecycle case (87 full-suite checks): complete
indexed capture, original source SHA-256, nested media and every tuple remain
exportable after source expiry/picker recreation. Host fixtures retain all
75,000 dense rejected headers with lazy paging/full streamed exports and exact
original packet reconstruction; malformed/fractional/overflow selectors and
cancelled stages fail explicitly. The former 48 MiB APK raw-occurrence failure
now completes with 109,023 records under a 256 MiB JVM heap. Native dictionary
transport changes storage, not format-validation coverage; device behavior is
still unverified. See [PhotoRec contracts](photorec-investigation.md).

Six scan-profile cases bring the suite to 93: actual catalog/widget save/apply and CDiE request, stale-edit/reload/CRUD scopes, captured catalog export/recreation, strict captured imports/inspection, failed/cancelled output cleanup and primary entry/settings refresh. The real engine path also verifies parent creation for nested bundled assets. A shared lossless catalog reader preserves existing tracker behavior. See [profile contracts](scan-profiles.md). Handset providers, storage durability and process-death/import-review recovery remain unverified/incomplete.

Seven desktop-batch cases bring the suite to 100 with actual multi-file CDiE/facts and captured options/identity, queued-cancel/next-batch recovery, worker retention, complete staged exports through deletion/recreation/newer journals, destination cleanup, valid/damaged journal deletion and selection limits. Pure-Java fixtures independently exercise journal/source/report/budget/lease failures and complete native-row scopes. These are host/API 28 fixtures; real providers/process death/ARM64 runtime and full Qt batch parity remain unverified. See [batch contracts](batch-scanning.md).

Five comparison cases bring the full suite to 105: exact 81-run paging and side-specific native hex/range bytes, complete filtered exports through parent/snapshot expiry/recreation, failed/cancelled replacement/recovery, accepted-input/provider-close diagnostics and query fallback, and cancelled/failed output cleanup. Independent host tests verify aligned-byte results, 100,000 complete ranges, bounds, mutation, failed publication and retained source identities. The common supplemental-input UI refreshes accepted state after a later provider-close failure, avoiding a false prior-evidence claim. See [comparison contracts](binary-comparison.md); this does not establish semantic or Qt comparison parity.

Three captured native-tree comparison cases bring the full suite to 108 and the batch viewer to ten: actual CDiE matching/original-row/owned-source links and Back/recreation, full filtered exports across session expiry/settings changes/recreation, and stored-report mutation/failure/recovery. Independent host oracles retain duplicate ancestry and unknown fields, distinguish failed-scan unknown coverage and exercise 20,000-level trees plus bounds. Preview-only settings and disabled custom-archive metadata do not become native configuration differences. See [scan comparison contracts](scan-comparison.md). Real providers/handsets/storage and broader semantic/Qt comparison remain unverified.

Two additional primary-export cases bring the full suite to 110 (five primary-export cases): XML structured values and MIME/ownership through source expiry/recreation, plus CSV/TSV complete metadata and delayed picker byte identity. Independent XML/CSV parsers reconstruct every value/type/native row across success/failure/null/missing scopes and every UTF-16 unit; bounded streaming/cancellation/depth/native-row cases pass. See [report format contracts](report-formats.md). Qt byte layout and real document providers/handsets remain unverified.

Typed native structures add three actual-JNI Android host cases (113 full-suite
checks when combined with existing cases). They verify parent/storage versus
PE RVA navigation, complete filtered reports, chosen fat-slice coordinates and
captured index units, context-sensitive references and staged exact field bytes
through source mutation/recreation. Independent host byte/variant fixtures and
limits are detailed in [native-structures.md](native-structures.md). Device
execution and exhaustive native field semantics remain separate acceptance work.

Typed entropy adds two real-JNI Android host cases (115 full-suite checks when
combined): complete histogram pages/search/aggregate domains and full filtered
export across mutation/recreation, plus exact final block remainder bytes.
Independent Python byte/log2/IEEE checks cover 18 scopes, 612 intervals and all
4,608 bins, alongside original native report compatibility and cancellation/
callback recovery. See [native-entropy.md](native-entropy.md). Device execution,
interactive charts and hard OS resource/isolation guarantees remain unverified.

Primary compatible-format selection adds three actual-engine Android host cases
(118 full-suite checks when combined): captured facts/scan IDs and complete
pending exports through draft changes/recreation, name-based restoration/new
input defaults, stale dialogs and exported unresolved diagnostics/recovery.
Actual native known-rule cases also verify explicit-only passes under both
All-types settings and separate cached Binary/Automatic results. See
[scan-interpretations.md](scan-interpretations.md); device/full Qt semantics and
profile interpretation settings and per-file batch overrides remain pending.

PhotoRec adapter 3 preserves schema-2 indexed evidence and legacy capture
reading while replacing whole-input buffering with bounded pread windows.
Existing 118 Android host cases and static ARM64/APK checks pass; added reader
and real JNI cases cover sparse and fully written 80 MiB data, 256 MiB limits,
all buffer/EOF seams, ownership/mutation/refusal, separate Binwalk limits and
standalone source rebuilding. A real alpha-APK audit independently checks every
returned magic witness. Source/window/JVM and actual-device limits remain
explicit in [PhotoRec contracts](photorec-investigation.md).

Three named-batch interpretation cases bring the full API 28 suite to 121.
Actual Binary passes over ELF/raw inputs share each input's facts/scan selector;
unresolved requests retain failed captured reports and owned bytes, and a new
Automatic batch recovers. Exact names restore across retained/fresh saved state;
invalid saved requests require a visible choice before starting. Pending complete
exports remain bound through draft changes/recreation. Host policy fixtures
verify differing per-file IDs, strict stored captures and original schema-1
journals/reports. Real device/SAF/process-death/Qt behavior remains unverified.

Three Binder registry cases bring the complete API 28 suite to 124. Actual
provider/widget/digest paths cover 155-row paging/search/full filtered exports,
source references, diagnostic separation and pending bytes across recreation/
source expiry. The production command's host launch failure remains an explicit
failure with zero invented registrations. Separate trusted-JVM IO fixtures cover
output caps, timeouts, cancellation and child cleanup; raw-byte oracles check
Unicode/invalid UTF-8, duplicate/header/overflow and exact source lifetimes.
Real OS service visibility, providers, process death and handset behavior remain
unverified. See [Binder registry contracts](binder-registry.md).

Content-first presentation adds file type/identity before tools, one structured
File info guide, shared definition toggles and purpose-based tool groups. The
complete catalog retains native IDs 0–10 and all prior providers/workflows.
Three actual Main/CDiE cases cover data/settings identity, recreation, old
Reference locations and stale tool actions; the new combined suite has 127
checks. Partial/unsupported statuses remain outside collapsed definitions.
This presentation milestone did not claim Gallerize, media galleries, visual
grids or broad archive decoding; the subsequent Gallerize rows/contracts now
record the implemented subset and its remaining limits. See
[content presentation](content-presentation.md).

Gallerize adds seven Android host cases (134 full-suite checks when combined).
Actual PhotoRec content evidence and widget/image/text/grid ownership paths are
used; worker/media-runtime/provider transport is substituted where documented.
Independent complete byte/log2/partition checks and fully written 80 MiB content/
grid fixtures pass. PDF and actual media/device decoding remain separately
unverified. See [Gallerize contracts](gallerize-implementation.md).

A further real nested-ZIP handoff case brings the combined suite to 135. Both
Gallerize and a derived preview receive member results, verify private ownership/
SHA-256, transfer exact independent bytes and release the old browser leaf.
Archive member origin preserves parent identity without inventing a stored
physical offset. See [Gallerize](gallerize-implementation.md).

Four recovered-content cases bring the Android host suite to 139, with zero
failures/errors/skips. Actual Binwalk/PhotoRec rows feed independent PNG
previews and checked recovery exports; cancelled/failed destinations, pending
preview exports after source expiry/recreation, canonical private gallery
handoffs, capture restoration, unknown extents and changed-parent refusal are
covered. ARM64 assembly, lint and APK signature/version/offline/static-alignment
checks pass. These remain separate from real SAF/isolated UID/media/handset
acceptance; see [recovered-content contracts](recovered-content.md).

The native archive integration brings the Android host suite to **147 tests**,
with zero failures/errors/skips. Five cases use actual libarchive JNI with host
transport substitution; the Main/CDiE result boundary is checked independently.
The cold batch recreation fixture prepares its signature database before
blocking the active native scan, avoiding a disk-throughput timing assumption
while preserving ownership/recreation assertions. Required generic/native
checks, archive model/core oracles, ARM64 assembly/lint and the fifteen-library
APK static verifier pass. Real Android process/UID/Binder/SAF/codec/crash/storage
and 16 KiB runtime acceptance remain separate and unverified. See
[native archive contracts](native-archives.md).

Embedded archive launch contexts are implemented: actual Binwalk/PhotoRec row
starts can supply explicit native parsing tails without changing null or known
finding extents. Complete row/provenance/source identity is retained; new caller
identity checks run before native parsing. Actual Binwalk → prefixed TAR and
PhotoRec unknown-extent gzip → libarchive payload host chains pass independent
hash expectations and source expiry/restoration/Unicode/refusal checks. Focused
viewer/result cases and the full **151-case** Android host suite pass with zero
failures/errors/skips, plus required generic/native checks, archive regressions,
ARM64 assembly/lint and fifteen-library APK static verification. All five source CI jobs passed; pre-alpha.4 is published. See
[archive launch contracts](archive-launches.md). Device behavior and per-preview
native-origin captures are now implemented for independent previews; cross-Activity history and device behavior remain incomplete/unverified.


Five text-paging cases bring the complete Android host suite to **156 tests**,
with zero failures/errors/skips. Actual private file IO, UTF-8/Hex controls,
source expiry, recreation, unchanged full report exports and first-frame Close
ownership pass. Independent 128 MiB-JVM Unicode/raw-byte oracles reconstruct
161,892 complete pages and a fully written 24,117,266-byte file. Required
generic/native checks, ARM64 assembly/lint and the fifteen-library APK static
verifier pass. All five text-paging source CI jobs passed in run `44661425550`
and PR #7 merged. These do not establish
real SAF, device rendering, process death or API 26/35 acceptance.
See [large text/report contracts](text-paging.md).


Independent preview origins are implemented for bounded Binwalk/PhotoRec
findings, Gallerize cards and native archive member previews. Full selected-row
provenance/metadata and current-chain raw inspection/acquisition/failure/launch
witnesses survive parent expiry, retain exact UTF-16 and logical-versus-physical
relationships, and have checked complete JSON Lines exports. Four new viewer
cases and one actual nested libarchive case bring the full suite to **161**,
with zero failures/errors/skips; actual Binwalk/PhotoRec/Gallerize cases also
assert the produced captures. Independent raw-byte/Python all-UTF-16/unknown
field/mutation/ownership/refusal checks, required generic/native/archive model
checks, ARM64 assembly/lint and the fifteen-library APK verifier pass. Source
CI passed all five jobs in run `44664429934`; pre-alpha.6 is published. Main return-and-rescan, Classic ZIP and
cross-Activity ancestry inheritance remain incomplete; actual device/SAF/UID/
process-death/cache-pressure acceptance is unverified.
See [origin contracts](content-origins.md).


Sourced package knowledge brings the full Android host suite to **167 tests**,
with zero failures/errors/skips. All 5,402 original UAD-NG occurrences (5,399
IDs, three duplicate-ID pairs) and 85 relationship declarations are preserved.
Six API 28 cases cover practical/ambiguous cards, paging/categories/full search,
exact occurrence rendering, directed references/Back, private restoration and
pending source export after expiry, main/inventory entry points and the
duplicate-preserving APK summary. Independent 128 MiB-JVM/Python oracles
reconstruct every original field and UTF-16 unit with exact numeric/raw data,
unknowns, graph and refusal/ownership fixtures. Required generic/native, 924
tracker reference/strict-import and shared prefilter checks, ARM64 assembly/lint
and fifteen-library APK checks pass. All five source CI jobs passed in run `44719613831`; pre-alpha.7 is published. Community
claims are not measured device behavior; handset/SAF/PackageManager acceptance
and broader knowledge/correlation remain incomplete. See
[package knowledge](package-knowledge.md).


Gallery per-card archive inspection and explicit manual byte intervals bring
the full suite to **170 Android host tests**, with zero failures/errors/skips.
Original native finding geometry is checked before launch; known/null extents
remain unchanged. Full gallery/card/associated-row context survives the native
trail and origin export. Manual and grid ranges are labeled as selected bytes,
not native recovered-file boundaries; exact previews retain associated findings
after parent expiry. Actual PhotoRec → unknown-extent gzip gallery card →
libarchive decoded-member/independent-hash/restoration/UTF-16/refusal tests and
the fully written 80 MiB-plus gallery regression pass. Required generic/native
checks, ARM64 assembly/lint and fifteen-library APK checks pass. Source CI is
next; real Android UID/Binder/SAF/media/PDF/process-death/16 KiB runtime remains
unverified. See [gallery interval contracts](gallery-inspection-ranges.md).


The recovered-file gallery slice combines both native captures and exports
actual files. All **180 host tests** pass with zero failures/errors/skips,
including direct PNG preview/typed saves and a 29-image plus nested-payload ZIP
through picker recreation. Actual native JPEG/PNG/WAV/GIF/gzip/MP4/text exports
and independent Python reconstruction pass. The 80 MiB + 17 byte fixture exports
the exact PNG beyond 64 MiB while explicitly skipping over-budget Binwalk.
Required generic/native checks, ARM64 assembly/lint and fifteen-library APK
signature/offline/alignment checks pass. New five-job source CI is pending;
these checks do not establish production ARM64 gallery/SAF/all-codec acceptance.
See [actual file contracts](recovered-file-gallery.md).


PhotoRec adapter 4 adds actual PDF payload recovery through the reviewed
upstream file-check module, with bounded read-only relative stdio and strict
scope/count validation. All **181 host tests** pass, alongside required native/
generic, existing native carving/large-input and new Java/Python/C++ oracles.
ARM64 compilation/lint and APK signature/offline/notices/alignment checks pass.
The eighth instrumented PhotoRec/Bionic test compiles/static-verifies with its
matching native-source and packaged-library identities. Actual execution and
current source CI remain pending; PDF rendering and other full file checks are
not claimed. See [PDF recovery](pdf-file-recovery.md).


Source **431f98d**, merged through PR #15 as d03f90d, passed all five source/PR
checks and eight actual x86 Android API 26/35 contracts. Downloaded complete
reports, completion/file SHA identity, native/Java/fixture sources, APK/library
hashes and isolated PDF worker UIDs were independently verified;
[retained summary](verification/android-worker-runtime-431f98d.json). This is
actual Bionic PDF recovery, not production ARM64/SAF/PDF-renderer acceptance.
Pre-alpha.13 is published.

Adapter 5 now implements separate native PDF footer and optional metadata-work
outcomes. Exact >9 MiB PDF file/gallery/save and independent Python byte/xref
oracles pass, with missing-footer and forged error/flag/count/legacy refusals.
The full existing host gallery case and actual Android lab are extended to the
larger file. Current .14 full-suite/ARM64/CI gates remain pending; see
[PDF recovery policy](pdf-file-recovery.md).


## Published archive gallery checkpoint

Pre-alpha.15 source `2dcfce3` passed source CI **44940694548** and PR CI
**44940700294**, all 188 host tests, and eight actual x86 API 26/35 contracts
in **44940694581**. PR #17 merged as `d7a31d1`; publisher **44941636216**
succeeded. Downloaded checkpoint files match public asset hashes, and the
exact-source APK passes signature/offline/identity/static alignment checks.
The public APK SHA-256 is
`e96547fc2b7349048c5cb02d5fe7477004cdf6eddeac9b8231adb2dd5a16150e`.
[Runtime evidence](verification/android-worker-runtime-2dcfce3.json). Actual
new gallery/SAF/production ARM64/codec/PDF-rendering acceptance remains
unverified; full parity is not claimed.


Pre-alpha.16 source `04e93c0` is published after both five-job source CI runs
44967525630 / 44967529622 and all 195 host cases passed. All nine actual x86
API 26/35 contracts pass in 44967525584 / 44967529605. Independently downloaded
source/APK/native-library/report/completion identities and red/green page
geometry/pixels pass; [evidence](verification/android-worker-runtime-04e93c0.json).
The public six-file checkpoint inventory and all corresponding public asset
digests match, and exact-source APK static verification passes. This verifies
the shared PDF helper on those Android fixtures; production ARM64/SAF/full
gallery UI, arbitrary documents and full desktop parity remain separate.


## Archive cache aliases and actionable compressed files (pre-alpha.18 work)

Implemented: canonical trusted roots for archive sessions/origins/staged ranges,
strict generated-leaf refusal retained, a prominent source-bound archive action
before generic desktop parser facts, direct decoded-file Open/Save, and an
independent preview → Gallerize handoff. Four new actual-JNI host/widget cases
cover captured BZip2 actions and decoded file ownership. Gallery mode retains
its existing direct card actions and usable viewport.

The exact public NIST L0_Video input from the reported screenshot passes complete
host-JNI BZip2 acquisition: 19,741,341 compressed bytes → 62,578,688 decoded
bytes, independently pinned SHA-256. Actual Binwalk/PhotoRec discovery runs also
complete; full recovered/exported-file scoring remains **unverified**. All
30 related CFReDS carving images have a test-only reference catalog, with raw
layout cells and seven disclosed inconsistencies. Only L0_Video is acquired
and hash-pinned. [Acceptance criteria and current matrix](../tests/fixtures/nist/README.md).
The optional offline exact-image regression is reproducible from an externally
supplied corpus file. No expected layouts or third-party media enter the app.

An eleventh actual Android lab contract covers >20 MiB compressed input,
20 MiB exact decoding, trusted ancestor aliases, leaf refusal before native
work, restoration and origin ownership. All **213 host Android cases**, generic/native regressions and the optional
exact-image decoding test pass locally. [Full host receipt](verification/nist-L0-video-archive-host.json).
The eleventh lab case compiles locally; source CI and actual API 26/35
execution remain pending. Full gallery/SAF/production ARM64
acceptance and arbitrary fragmentation/deobfuscation remain unverified.


## Current pre-alpha.19 implementation, pending complete gates

Configurable Binwalk/worker limits, Strings minimum/search/Categories, compact
definitions, functional list/detail thumbs, zoom/contiguous grid ranges and
private diagnostics/About are implemented. The focused host suite passes 62
cases, including late completion cancellation/deadline races; eight Rust and
all configured-limit/ownership/64 MiB JNI regressions pass and ARM64 Binwalk
compiles. Full-suite, assembly/lint/static, clean CI and actual Android
regressions are pending. [Current control contract](workbench-usability.md).
Existing data/source ownership, raw fields, native catalogs, schemas and offline
operation are preserved. The NIST exact-export baseline remains 5/6 on host,
with all additional exports visible; [method](../tests/fixtures/nist/README.md).

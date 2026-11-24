Current verification is recorded in [STATUS](STATUS.md) and the [current parity checklist](FEATURE-PARITY.md).
Selected actual x86 Android contracts are verified; historical unverified statements below do not establish or revoke other scopes.

# GlassDigestor — detailed workbench capabilities

Offline native Android frontend for the open-source Detect It Easy ecosystem. Independent project, not an official Horsicq application.

Development alpha with host JNI, Android UI simulation and CI build checks; handset verification and a complete audit remain pending. Do not treat a signature match or its absence as proof of safety. Use appropriate isolation for hostile samples.

The [0.5.0-alpha checkpoint notes](releases/0.5.0-alpha.md) identify the save point, build checks and remaining runtime limits.

## Features

- Storage Access Framework and Android share menu; no root, analytics, broad storage or INTERNET permission.
- Streaming header identification, SHA-256, Shannon entropy, touch-selectable block entropy, searchable ASCII strings and hex preview.
- **File info → Binary comparison** owns both selected inputs, verifies whole-file identities and compares exact aligned bytes with coalesced unequal runs/one-sided tails. Complete source-identified reports, per-side hex/range exports, transactional replacement and recreation retain precise scope. Host oracle/100,000-run/budget/failure cases and five Android/native-hex cases pass; semantic alignment, report/package diff and handset flows remain incomplete. See [comparison contracts](binary-comparison.md).
- **File info → Whole-file hashes** exposes complete CRC32/MD4/MD5/SHA1/SHA224/SHA256/SHA384/SHA512 records with exact values, mechanisms, source revisions and complete exports. Native Tools uses the same expanded worker path. Pinned xxfclib and unmodified 0BSD RHash MD4 supply the computations; native SHA-256 is independently checked against acquisition. Host/JCA/OpenSSL/RFC fixtures and three Android cases pass. See [hash contracts](hash-investigation.md).
- **File info / Tools** prioritizes observed type and identity, keeps one structured format guide, and groups general/media/content/Android/executable/expert tools. Definitions expand individually or globally while raw evidence and failure/coverage status stay visible. Every existing tool remains reachable. See [presentation scope](content-presentation.md) and the [active Gallerize work](gallerize-workbench.md).
- Primary scanning reuses the CDiE desktop backend: exact signature source paths, format facts, hierarchy, diagnostics, deep/heuristic/recursive/aggressive/parent-format flags, overlays/resources/archives and tag filters. Calls are serialized. Full Qt engine parity is unverified.
- Shared persisted settings between Scan and the signature laboratory; captured options are exported with the result. Searchable detection evidence is paged without discarding records.
- **Options / laboratory → Saved scan option profiles** captures all shared flags, tags and string minimum, with named UUIDs, complete source metadata, strict catalog import/export, revision-checked atomic storage and apply/rename/update/delete. Pending exports survive catalog changes/recreation. Host and six Android/real-engine cases pass; Qt/manual-format/tool settings and handset durability remain incomplete. See [profile contracts](scan-profiles.md).
- **Scan → Batch** captures 1–64 documents for serial actual CDiE/native-facts scans, whole-file identities, fixed settings and persistent per-file outcomes. A captured Automatic or compatible-name policy resolves separately per input; unresolved names keep failed reports and owned bytes. Schema-1 Automatic captures remain readable. Searchable native trees, complete staged exports and saved-batch deletion retain scope. Host and thirteen actual-engine activity/viewer cases pass; directory/background jobs, arbitrary forcing and real device/provider flows remain incomplete. See [batch contracts](batch-scanning.md).
- **Batch results → record Actions → Compare native report with #…** compares captured native trees with exact fields, ancestry and duplicate occurrence order. Both report identities/options/rules remain visible; failed scans stay uncompared. Original-row links and complete staged exports preserve source scope. Independent random/deep/budget fixtures and three additional real-CDiE viewer cases pass; broader semantic/package/cross-batch comparison remains incomplete. See [scan comparison contracts](scan-comparison.md).
- Offline custom-rule ZIP import, native load checks, immutable SHA-256 snapshots, activation/rollback and original archive export; helpers, tag metadata and licensing notices are retained. No automatic network database updates.
- **Save report / Reports → XML / CSV / TSV** exports complete captured report values with source identities, diagnostics and unknown fields. XML keys/string values and tabular JSON cells preserve every UTF-16 unit; independent standard-library reconstruction and two additional primary-view cases verify serialization/lifetime. See [format schemas](report-formats.md).
- Choose a compatible native interpretation directly on the primary Scan page. Names resolve against each acquired input, facts/scans share the actual selector, and complete source-bound reports preserve the choice, catalog and unresolved diagnostics. See [interpretation contracts](scan-interpretations.md).
- Complete typed native entropy: whole input, every eligible region, all contiguous blocks and 256 histogram bins, with exact counts/ranges and clearly explained native display heuristics. Physical measurements and aggregate bins have distinct actions. See [entropy contracts](native-entropy.md).
- Typed CDiE PE/ELF/thin and fat Mach-O structures retain parent/field coordinates, raw/typed values, semantic flags, comments and exact native text. Field storage and resolved RVA/slice targets remain distinct, with complete exports and bounded active cancellation. See [structure contracts](native-structures.md).
- Versioned structured JSON reports capture content identity, original scan options, engine/database revisions, native evidence and limits before the system picker. Immutable staging survives pending-picker recreation/source expiry and preserves exact decoded Unicode, including isolated UTF-16 units. See [schema and lifecycle](report-schema.md).
- Searchable, paginated structures/entropy/maps/imports/exports/symbols/resources/instructions with every native field, contextual explanations and explicit unsupported/errors. Navigate physical offsets or unsigned preferred VAs to hex/disassembly, retain Back context, measure range entropy/digests and export exact stored resource bytes. See [contracts and limits](native-records.md).
- Nested ZIP/APK browsing with folder search/paging, Back/recreation context and bounded private snapshots. Selected bytes require current directory metadata, exact expanded length and CRC32 checks, with a freshly measured SHA-256. See [archive acquisition](archive-acquisition.md).
- Google smali/dexlib2 + baksmali 3.0.10 provide Unicode DEX class indexing and integrity/table evidence. **APK / SDKs → DEX tables / bytecode / references / smali** opens lazy searchable tables, explicit reference/caller links, complete payloads, debug/exception records, byte-range actions and actual method smali. APK DEX members are privately acquired with size/CRC checks. See [DEX scope and contracts](dex-investigation.md).
- Canonical LibChecker v5 supplies all 3,053 active SDK/library name matchers and complete effective locale metadata, including all 123 regex rules through source-built RE2/J. The APK report combines native/component names, derived DEX packages and original compiled-manifest library/permission/metadata/action names. A searchable catalog exposes rules, priorities, source hashes, all languages, UUID relationships and complete exports. Name associations remain separate from code identity/use. See [SDK contracts](sdk-investigation.md).
- **Strings → Grouped string classifier** scans whole supported inputs with UTF-8/anchored UTF-16 and groups URLs/endpoints, commands/APIs, secret candidates, crypto, persistence, anti-analysis and vulnerability/exploit indicators. Exact DEX values have the same classifier and caller links. Every observation preserves rules, physical spans and limitations; no online service or malware verdict is involved. See [string scope and verification](string-investigation.md).
- **File info → Collected metadata** exposes EXIF/GPS/IPTC/ICC/XMP and supported image/media fields through source-built metadata-extractor/AOSP XMP. Directory relationships, raw typed values, reader errors, complete binary/text values and streamed exports remain separate from descriptions and previews. See [metadata scope and verification](metadata-investigation.md).
- **System explorer → Services → OS Binder registry** captures the fixed platform query as the app UID. Parsed names/descriptors stay separate from diagnostics, unknown bytes and manifest declarations; immutable source/output identities, raw byte ranges, complete exports and the pinned offline AOSP source retain context. Host IO/parser checks and three viewer cases pass; actual handset visibility/SELinux/runtime remains unverified. See [Binder scope](binder-registry.md).
- **File info → Binwalk / resource carving** uses actual upstream validators and all 251 compiled magic patterns over the supported whole blob, retaining nested content, rejected candidates and unknown lengths. Exact bounded stored ranges can be recovered/exported. Owned positional acquisition preserves caller offsets and reports source/read/budget limits; its 64 MiB file-sized copy remains explicit. The ARM64 integration uses a private isolated service; host parser/range fixtures pass, while its handset IPC path remains unverified. See [Binwalk scope](binwalk-investigation.md).
- **File info → PhotoRec / whole-blob formats / stored-byte carving** compiles unmodified PhotoRec 7.2 header/data checks and streams up to 256 MiB with bounded input-read windows. The 1.25 MiB window bound is separate from input size and total worker memory. Its full 774-pattern catalog exposes 581 patterns searched at every byte and 193 explicit one-byte/all-zero exclusions. Lossless indexed reports and complete compact captures retain high occurrence counts; bounded stored ranges, nested/overlapping/rejected/header-only evidence and exact exports remain separate from full integrity or fragmented recovery. Real host oracle/media/dense-index fixtures, four Android viewer cases and ARM64 compilation pass; handset isolation remains unverified. See [PhotoRec scope](photorec-investigation.md).
- **File info → MPV / media codecs / stream metadata** invokes actual libmpv with typed file/track/chapter tags, codec/container catalogs, exact unavailable-property errors and offline upstream explanations. Complete values export to JSON/text/bytes. Official ARM64 libraries, dependency sources and license notices are hash-pinned; real host parser/UI fixtures pass, while ARM64/isolated-service handset execution remains unverified. See [media scope](media-investigation.md).
- **File info → Binary Android manifest / resource tables / compiled XML** uses source-built Google binary-resources 32.5.1 with bounded wire validation. Fourteen searchable tables retain XML events/duplicate attributes, exact string/style/code-unit evidence, packages/configurations, every dense/offset16/sparse/compact entry and duplicate bag mapping. IDs link to local definitions; full JSON, encoded payload and UTF-16LE exports preserve complete values. Host/AAPT2 references and five Android viewer cases pass; production worker/device resource resolution remains unverified. See [resource contracts](android-resources-investigation.md).
- Desktop-inspired dark native UI: persistent scan flags, Scan / Rescan, file summary, expandable signature groups with details, raw JSON log, elapsed time and progress indicator. ARM64 native linkage; no Termux. Rescan reopens the selected URI (subject to provider permissions) with current settings.

## Build

JDK 17, Gradle 8.11.1, Android platform 35, CMake 3.22.1, NDK 28.2.13676358. API 26+; Android 16 support intended but not yet validated.

```sh
bash scripts/test.sh
bash scripts/test-apk.sh
bash scripts/test-models.sh
bash scripts/test-profiles.sh
bash scripts/test-batch.sh
bash scripts/test-comparison.sh
bash scripts/test-scan-comparison.sh
bash scripts/test-report-formats.sh
bash scripts/test-dex.sh
bash scripts/test-resources.sh
bash scripts/fetch-engine.sh
bash scripts/test-native.sh
bash scripts/test-hashes.sh
bash scripts/fetch-knowledge.sh
bash scripts/test-knowledge.sh
bash scripts/fetch-metadata.sh
bash scripts/fetch-binwalk.sh
bash scripts/build-binwalk.sh android
bash scripts/build-mpv.sh android
bash scripts/test-photorec.sh
bash scripts/build-photorec.sh android
python3 scripts/package-photorec-sources.py
python3 scripts/fetch-mpv-sources.py --bundle
./gradlew :app:assembleDebug :app:lintDebug
bash scripts/verify-apk.sh
bash scripts/test-workbench-ui.sh
```

CDiE and submodules are pinned. The Gradle wrapper pins 8.11.1 with a distribution SHA-256. Build-time downloads only. GitHub Actions uploads the verified debug APK, checksum and source/data/signer build manifest, plus pinned MPV and PhotoRec source bundles. Debug signing keys and build paths can differ; bit-identical signed APKs are not claimed. Java-only fallback: `./gradlew -PwithCdie=false :app:assembleDebug` (no compiler/packer detection; the native APK verification script intentionally requires the full ARM64 build).

## Limits / roadmap

Primary acquisition is capped at 1 GiB; specialized tools have [separate input budgets and verified scale](input-budgets.md). Binwalk, PhotoRec, MPV, compiled resources and whole-file hashing have private isolated-worker integration; real OS IPC/crash/cancellation behavior remains device-unverified. CDiE, DEX and collected metadata still run in process. ASCII preview is capped at approximately 200k characters, runs at 4096, hex preview at 4096 bytes and entropy at 256 blocks; Native Tools provides paged hex, Unicode extraction, disassembly and native tables. Main Scan and the laboratory have request-owned cancellation, with queued-cancel and recovery fixtures; native interruption remains cooperative and active interrupt latency is unverified. Some provider, metadata and rule operations must return before cancellation settles. Cache hits and byte-change invalidation have host fixtures. Native reports have explicit output limits. Recursive signature-scan archive expansion inherits CDiE's unsupported formats and some silent member-skipping limits, disclosed in exports; selected member browsing uses its separately bounded ZIP reader. Device verification (including actual 16 KiB page operation), broad Qt comparison, directory jobs/report comparison/full Qt profile compatibility and a complete license audit remain pending. Consult the [current parity matrix](FEATURE-PARITY.md) and [continuation point](continuation.md).

## License

Frontend GPL-3.0-only. Commercial redistribution permitted under GPL conditions. Upstream CDiE is MIT; database and dependencies retain their original licenses. See generated notices and upstream revisions; audit before distributing a linked APK.
Upstream: https://github.com/horsicq/CDiE and https://github.com/horsicq/Detect-It-Easy

## Analysis workbench (0.3)
File 101, APK metadata and permission explanations, exported components, archive inventory, canonical LibChecker exact/regex rules and UAD-NG package knowledge. The [captured package-set workbench](package-set-investigation.md) follows original base/split declarations, dependency/type candidates, full version bits and verified signer differences from installed-package paths or a selected APK, with complete owned exports and explicit failed/unknown scopes. The [APK signing workbench](apk-signing-investigation.md) exposes all returned scheme signers, exact certificates/DER/SPKI/extensions, scoped rotation capabilities, signing digests, source stamps and diagnostics. Explicit local `.idsig` selection verifies v4/v4.1, preserves the captured pair across recreation/source expiry and supports exact byte exports. A reproduced upstream v4 aggregate-status omission is guarded. Optional offline Exodus JSON import now supports bounded regex search over original slash class names, a shared required-literal index with exact regex verification, frozen Unicode 15 character classes, complete inspectable metadata/diagnostics and exact snapshot export. See [tracker evidence and limits](tracker-investigation.md). See [integration scope and limitations](analysis-workbench.md).

DEX identifiers now navigate to every exact captured class/method/field
declaration across APK DEX members and 041 entries. Owned CRC/SHA-256-checked
snapshots keep target bytes/smali available after parent expiry; duplicate and
failed scopes stay explicit. See [multidex evidence and limits](dex-definitions.md).

### Expert-first interface
Technical detail is the default. Contextual help explains the actual data without replacing it with a simplified view. Raw evidence remains accessible.

## Core workbench expansion
Native Tools exposes upstream CDiE data structures, memory maps, imports, exports, symbols, resource metadata, disassembly, hashes, Unicode strings and paged hex. Explore exposes local package inventory, declared services and OS-permitted files. QUERY_ALL_PACKAGES is declared for this local inventory; no INTERNET permission. See [design goals](DESIGN-GOALS.md) and [feature parity](FEATURE-PARITY.md). Hardware testing and full parity remain pending.

### Verification and community development
Run `scripts/test.sh` for the generic inspector, custom databases and archive acquisition, `scripts/test-models.sh` for evidence/settings/range/export fixtures, `scripts/test-apk.sh` for real AOSP signing verification, and `scripts/test-native.sh` after `scripts/fetch-engine.sh` for Linux-host JNI fixtures (JDK 17, CMake, C compiler and Python 3). `JSON_JAR`, `APKSIG_JAR` and `APKSIG_SOURCES_JAR` accept cached, checksum-verified host dependencies for offline test runs. `NATIVE_BUILD_DIR` optionally retains a CMake build between native test runs.

`scripts/test-workbench-ui.sh` builds host JNI/fixtures and runs the Android UI/lifecycle suite with Robolectric API 28, including fourteen native-workbench cases with actual JNI results and five archive cases with real ZIP streams. Additional engine/viewer/profile/batch/comparison/export cases are detailed in the parity checklist. A 360×640 layout case checks that maps and disassembly retain usable result space. The SDK/JDK and fetched engine are required; JUnit 4.13.2, Robolectric 4.14.1 and its Android runtime are build-time test dependencies only. The test JVM keeps its download cache under `app/build/test-runtime-home`; retain it and the Gradle cache for offline reruns. The test provider captures export streams; real SAF permissions and handset behavior need device checks. Android CI runs these tests and independently cross-compiles/lints/verifies the ARM64 APK. No connected-device verification is claimed.

That command also runs four DEX report-integration cases, seven DEX viewer cases, six string viewer cases, six metadata cases, two binary-worker boundary cases, four MPV viewer cases, five compiled-resource viewer cases, three hash cases, four canonical-knowledge cases and three primary-report export cases, with full-suite counts and verification gaps tracked in the feature-parity checklist. MPV/resource/hash viewer checks substitute only OS worker transport; production worker IPC remains unverified. `scripts/test-dex.sh`, `scripts/test-strings.sh`, `scripts/test-metadata.sh`, `scripts/test-binwalk.sh`, `scripts/test-mpv.sh`, `scripts/test-resources.sh`, `scripts/test-hashes.sh` and `scripts/test-knowledge.sh` check actual engine/encoding/location/value/range contracts on the host. Resource checks use independent AAPT2 references when SDK platform/build-tools 35 exist; `DIE_REQUIRE_AAPT2=true` requires them. The binary engine requires Rust 1.90.0; media host tests require the `libmpv2` runtime and FFmpeg (retained headers are bundled), and independent MD4 host references use OpenSSL 3. Gradle rebuilds the ARM64 adapters and prepares the source-built resource parser when inputs change. `DEX_DEPS_DIR`, `METADATA_DEPS_DIR`, `RESOURCE_DEPS_DIR`, Cargo/toolchain caches, `BINARY_BUILD_DIR`, `MPV_DEPS_DIR`, `MPV_SOURCE_DIR` and `MPV_HOST_LIBRARY` support cached/offline reruns. See the linked contracts above.

Gallerize now supplies a content map, labeled gallery, owned local previews and exact configurable byte/entropy grids. See [implemented scope and verification](gallerize-implementation.md); broader archive engines and handset decoding remain active work.

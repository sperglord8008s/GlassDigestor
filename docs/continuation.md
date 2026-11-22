# Development continuation point

The workbench is on main through PR #20 (`1b0a44b`) in **sperglord8008s/GlassDigestor** (the owner's rename of DiE-Android-, repository ID 1460636999). **0.6.0-pre-alpha.16 / code 22**, source `04e93c0`, is published. The pinned offline host-test-image follow-up is accepted at `95e46b3`; final source/runtime gates are recorded below. User priority is the actual recovered files: PhotoRec/Binwalk/archive galleries, usable in-app previews and exact file exports. Preserve production `org.diemobile`, existing signing and immutable checkpoint APKs. Full desktop parity and production handset verification remain incomplete. Read AGENTS.md and the three design/parity documents before changes. The latest continuation entry is at the end; older entries retain historical verification scope.

## Implemented and fixture-verified in this work

- Primary captures now export JSON/XML/CSV/TSV through the same immutable prepared ownership path. Typed XML and reversible tabular metadata/native rows preserve all UTF-16 units, unknown fields and failed-scan scope; independent standard-library parser reconstruction/large-row/budget cases and two additional primary-view cases pass (110 full-suite checks). Real providers/storage/process death and Qt serialization identity remain unverified. See [format contracts](report-formats.md).

- Captured native tree comparison is available from batch row Actions. Exact fields/ancestry/duplicate occurrences, checked report identities, relevant native versus preview-only conditions, original-row links and complete delayed exports retain every returned record and explicit failed-scan unknown coverage. Independent random/deep/field/budget fixtures and three additional real-CDiE viewer cases pass (108 full-suite checks). Cross-batch/import/program/package semantics and real provider/device/storage behavior remain incomplete. See [scan comparison contracts](scan-comparison.md).

- Exact aligned binary comparison: every paired byte/run/tail, independently verified whole-input identities, two owned snapshots, fixed side/range coordinates, atomic candidate replacement, stale-pair rejection and complete prepared exports. Independent random/seam/100,000-run/mutation/publication/retention fixtures and five comparator/widget/native-hex cases pass (105 full-suite checks). A shared viewer fix refreshes accepted input after later provider-close failure and tolerates unavailable metadata queries. Semantic/report/package diff, alignment/background jobs and handset/process-death/durability flows remain incomplete. See [comparison contracts](binary-comparison.md).

- Desktop document batches now capture complete plans/options/database revisions, privately acquire each full input, run real CDiE/facts/inspection and checkpoint every outcome. Source/report identity, incomplete/cancelled scopes, exact byte ownership, complete staged exports, configuration worker retention and active/damaged-session deletion have host and seven Android cases (100 full-suite checks). Directory/background/resumable jobs, diff/manual formats/profile attribution and device/provider/process-death/power-loss behavior remain incomplete. See [batch contracts](batch-scanning.md).

- Saved scan option profiles: all 14 shared flags, tag filters and minimum; immutable UUIDs/options, strict lossless bounded imports/exports, source hashes, revision-checked sync/atomic publication, CRUD/apply and pending-export ownership. Host file/malformed/metadata cases, shared tracker references and six widget/real-CDiE-request cases pass (93 full-suite checks). The real request exposed a nested-relative-asset installation issue; leaf parents are now created before copying. Manual format/tool settings, Qt compatibility, named batch/report profile attribution and diff, import-approval process death, damaged-private-catalog recovery and handset durability/providers remain incomplete. See [profile contracts](scan-profiles.md).

- PhotoRec 7.2 now compiles all 358 unmodified format/support sources: 349 registered groups / 774 catalog patterns, 581 searched at every byte and 193 explicit one-byte/all-zero exclusions. Header/data-only evidence preserves every searched occurrence, overlaps, negative/relative coordinates, raw statuses and unknown extents; bounded data-stop intervals enable exact immutable carving. Host naive-oracle/media/CRC/truncation/FD/budget/export fixtures and four actual-JNI viewer cases pass, bringing the full local suite to 87 (zero failures/errors/skips), plus ARM64 assembly/lint and static APK checks. The deterministic corresponding-source ZIP includes the original pinned archive and standalone adapter/build inputs. Full file checks, weak-pattern anchored scopes, codec decoding, fragment/filesystem recovery and real isolated UID/SAF/handset behavior remain incomplete/unverified. See [PhotoRec contracts](photorec-investigation.md).

- Captured package sets: the installed-package explorer hands off every exposed base/split path, and Scan opens a one-file scope. Whole APK/expanded manifest identity and CRC checks precede original field/attribute selection, exact package/version/split candidate graphs, split-type associations and verified DER-set comparisons. Source failures/duplicates/unknown values remain explicit; owned metadata/APK/manifest/certificate exports survive parent expiry/recreation. Three manifest, four document/model and five viewer cases bring the suite to 83 with independent AAPT2/real signatures; real installed paths/SAF/worker/handset and full targeting/signing/framework/runtime policy remain incomplete. See [package-set contracts](package-set-investigation.md).

- DEX: pinned Google smali/dexlib2 + baksmali 3.0.10 and Android Guava, complete licensing/source notices and standalone dependency checksums. The main report uses Unicode/MUTF-8 indexing, table counts, Adler32/SHA-1 consistency and DEX SHA-256; ZIP duplicate-name/size/CRC/budget failures are explicit. APK / SDKs opens lazy typed metadata/bytecode/debug/exception/payload/reference tables, real method smali and physical byte actions. Complete scoped exports, 64-location Back/recreation, captured versus draft filters, classic versions and 041 shared containers have host fixtures. Four report and six viewer Robolectric cases join the previous fourteen UI cases. See [DEX investigation](dex-investigation.md).
- Exact multidex declarations: a separate owned index hashes the complete parent, CRC/size-checks all named DEX candidates and retains class/source metadata independently of live parsers. Type/method/field/handle links preserve every exact declared-owner/signature candidate, including duplicates/041 shared addresses; wrong identifier owners, failed scopes and limits remain explicit. Jumps select the correct captured file/table before byte actions; full staged JSON and source-byte exports survive parent expiry. Host fixtures cover overloads, encoded duplicates, CRC/parent/cache mutation, 128-source/96 MiB limits, late cancellation and recovery. Four actual-engine API 28 cases bring the suite to 67; inheritance/framework/split/runtime linking, process isolation and handset flows remain incomplete. See [multidex contracts](dex-definitions.md).
- String investigation: 43 built-in rules, 16 overlapping groups and full rule catalog/context/identity. Whole-input UTF-8/anchored UTF-16 scanning and exact DEX MUTF-8 classification preserve physical match/range evidence; private byte/text snapshots keep exports and hex independent of source expiry. All-rule host fixtures, six string viewer cases and one added DEX viewer case pass (31 Android checks total). See [string contracts](string-investigation.md).
- Collected metadata: source-built metadata-extractor 2.22.0 (Apache-2.0) plus Adobe XMP Core 5.1.0-jc003 from pinned AOSP BSD source, with a complete compatibility/XML gate patch. Directory/tag/error/XMP views retain raw types/relationships and stream complete values to JSON; exact binary/text values are exportable. Host JPEG/EXIF/GPS/ICC/XMP/PNG/WAV fixtures and six metadata Android cases pass (37 Android checks total). See [metadata contracts](metadata-investigation.md).
- Binwalk: unmodified upstream source 26713972 (3.1.1), 111 signatures/251 patterns and actual validators, with an explicit full-blob/overlap adapter, complete unresolved/candidate/catalog evidence and exact bounded stored-byte recovery. Linux JNI fixtures cover nested media, JPEG/ICC false EOI, unknown extents, descriptor ownership and immutable carving. ARM64 builds and both native-library 16 KiB checks pass. The private isolated worker is wired; two manifest/UID-refusal Robolectric cases bring the suite to 39, but real Binder/isolated UID/crash/cancellation flow is not device verified. See [Binwalk contracts](binwalk-investigation.md).
- MPV: actual libmpv/FFmpeg runtime from the hash-pinned official mpv-android 2026-09-17 ARM64 release, with source-built descriptor/typed-node adapter and complete source/dependency/submodule/license locks. Fifty-three audited file/engine getters, every descendant/error, full runtime property names, decoder/demuxer/protocol catalogs, guarded options and offline upstream references are exposed. Real host WAV/Matroska/PCM/FFV1/SubRip/Unicode/long-tag/chapters/ownership/export/reference-HTTP fixtures and four actual-JNI viewer cases pass (43 Android checks). OS transport is substituted in these viewer tests. Source layout preparation passes; the complete upstream NDK r30 runtime source rebuild and actual ARM64/isolated UID/Binder/device flows remain unverified. See [media contracts](media-investigation.md) and [source acquisition/build](../media-engine/README.md).
- Compiled Android resources: source-built Google binary-resources 32.5.1 plus documented Java 8/UTF-16/style-hole/legacy-package patch and bounded wire inventory. Fourteen tables, local ID/string/configuration links, every supported stored entry/map/slot, whole-parent APK identity and complete JSON/encoded-byte/UTF-16LE exports are implemented. Actual source-engine/report/ownership/boundary tests and independent AAPT2 ordinary/sparse/collapsed/deduplicated references pass; five viewer cases bring the suite to 48. Worker transport is substituted in viewer tests. Opaque overlays/staged aliases, canonical XML reconstruction, framework/split resolution, real Binder/isolated UID/platform acceptance and handset flows remain incomplete/unverified. See [resource contracts](android-resources-investigation.md) and [source build](../resource-engine/README.md).
- Remaining additional user scope: full PhotoRec/JPEG Digger/fragment recovery, anchored weak-signature mode, full file/integrity checks and exhaustive media coverage. Continue worker-device validation where a device exists, Android split/framework correlation and the desktop gap list. Never claim strings/signature candidates or MPV initialization prove behavior or complete decoding.
- Whole-file hashing: all eight CRC32/MD4/MD5/SHA1/SHA224/SHA256/SHA384/SHA512 algorithms, using existing pinned MIT xxfclib plus unmodified 0BSD RHash MD4. A separate small `libdiehash` descriptor bridge, owned input and native/acquisition SHA-256 agreement supply typed File info records and the expanded Native Tools hash entry with selectors/text/JSON and fresh identity. RFC/JCA/OpenSSL/descriptor/budget/ownership fixtures and three actual-JNI cases pass (51 Android checks). Original CDiE C report API remains four algorithms. OS transport/ARM64 device behavior remains unverified. See [hash contracts](hash-investigation.md) and [engine source](../hash-engine/README.md).
- Canonical SDK/library matching: the actual pinned LibChecker v5 compiler retains all 3,053 active matchers and 2,934 effective locale payloads. Every one of its 123 regex rules compiles in the complete source-built RE2/J 1.8 engine, with all 19 upstream shared contract cases passing. The report matches native/component names, defined DEX package ancestors and original compiled-manifest component/library/permission/metadata/action names. Complete catalog/UUID/locale/original-record exports, source/compiler/content hashes and source-dirty state are exposed. Host/AAPT2/four Android cases pass (55 Android checks). Real worker/device behavior, framework/split resolution, SDK version/fingerprint providers and full tracker compatibility remain incomplete. See [SDK contracts](sdk-investigation.md).
- Offline Exodus: an independent source-built RE2/J regex-search adapter replaces dotted-literal matching, using original defined-class descriptors, the upstream short-signature gate and frozen Unicode 15 classes. It retains every imported rule/unknown field/top-level metadata, exact original numeric/Unicode JSON and SHA-256-identified snapshot bytes; strict imports reject duplicate keys, invalid UTF-8/depth/size errors and preserve the prior snapshot on failure. Unsupported syntax/names and budget-exhausted searches are explicit partial coverage. Host fixtures compare 924 searches to actual pinned upstream methods and exhaust Unicode scalar properties; five actual-engine/DEX/archive/import/export Android cases pass (63 checks total). Full Python syntax, dexdump/nested-APK equivalence, separate isolation and handset behavior remain incomplete. See [tracker contracts](tracker-investigation.md).
- Primary report exports now stage the complete captured scan/options/identity before the document picker, preserving exact Unicode and pending bytes across later state mutation, input expiry and activity recreation. Picker cancellation and destination failures discard staging; the original source remains unchanged. A direct streamed encoder preserves all 65,536 UTF-16 units in keys/values without substitution and avoids a second rendered report string. Three main-view cases bring the suite to 58; shared resource/metadata/MPV regressions pass. Real provider/process-death/copy-in-progress and handset behavior remain unverified. See [report contracts](report-schema.md).

- The main Scan workflow uses the pinned desktop CDiE backend. Scan/laboratory settings share one migrated preferences store. Results retain exact source paths, hierarchy, parser facts and diagnostics; 50-row searchable pages retain the full model. Physical entry offsets open hex/disassembly. Export schema v1 captures the original input SHA-256, scan options, engine/database identities and native JSON.
- Bundled databases install into revision-specific private snapshots. Custom ZIP imports enforce path, duplicate, CRC, compressed/expanded size, member size and count limits. Original ZIPs, helpers, tags and license notices are retained. Native load checks precede atomic active-pointer changes; immutable paths invalidate native caches when switching/rolling back. Archive SHA-256 and enabled state are exported.
- APK signing: unmodified build-verified AOSP apksig 8.9.2 now has full typed signer/certificate/DER/SPKI/extensions/scoped rotation/digest/source-stamp/diagnostic evidence, API range, explicitly selected detached v4 inputs and complete exports. Private snapshots plus an atomic latest-pair pointer survive recreation/source expiry, with failed copy/cancel preserving the previous pair. A real corrupt-tree fixture reproduces upstream v4 aggregate-status/error omissions; the adapter exposes raw flags while refusing combined success. Actual RSA/EC/DSA, rotation at APIs 28/33/35, v3.1/v4/v4.1, stamp/malformed/budget/ownership fixtures and four API 28 cases pass (71 suite checks). Real SAF/platform/handset, exhaustive algorithms, split policy and hard isolation remain incomplete. See [signing contracts](apk-signing-investigation.md).
- Request-owned cancellation prevents a queued request from losing a cancel signal or cancelling another scan. Java acquisition/inspection can observe cancellation. Deterministic queued-cancel/recovery and streaming-inspector fixtures pass. Active native interruption remains cooperative; some provider/metadata/script operations must return first.
- Native cache keys include the snapshot path as well as content/options/database identity. Repeated laboratory scans can reuse a retained snapshot; primary rescans deliberately reacquire into a new private path and normally miss that cache. Cache efficiency across primary snapshots remains future work.
- Linux JNI fixtures validate known-rule matches, unmodified bundled GLIBC library detection/exact source, edited-rule execution, custom-DB gates, imported tag include/exclude precedence, cache hit/content invalidation, imported snapshot activation and database reload/rollback. Generated PE32/PE64/thin-Mach-O fixtures validate structures, supported maps, entry mapping and instruction decoding. Truncated headers fail explicitly. DEX/ZIP fixtures validate identification only.
- Typed structures/entropy/maps/imports/exports/symbols/resources/instructions reuse the pinned native readers. All fields and exact unsigned values survive into detached models and exports. Host fixtures cover named/ordinal imports, IAT versus record offsets, export forwarders, COFF/UTF-8/absolute symbols, a known resource payload, 10,000 records and over-limit errors, high Mach-O VAs, virtual tails and disassembly domains/continuation. See [native-records.md](native-records.md) for the schema, budgets and limitations.
- Native Tools implements searchable 50-row tables, per-field contextual details, physical/VA hex/disassembly links, 64-location Back history, exact range entropy/SHA-256/CRC32, resource-byte extraction and full-table versioned JSON reports. Requests are captured independently of draft controls. Byte/report staging precedes the document picker and a pending export survives activity recreation. Table cancellation is checked before/after native reads; it does not interrupt an active parser.
- Nine API 28 Robolectric UI/lifecycle cases use the actual Linux JNI: 10,000-row paging/search/Back; full filtered-table export; disassembly row/block pages and manual architecture; unsigned VA navigation; range digest export; draft-versus-displayed mode preservation; queued cancellation/recovery; resource export across picker delay and recreation; and usable map/disassembly result space at 360×640. Parameters/actions now scroll while analysis/cancel/paging/search remain outside the scroll area. These use a test provider output stream. They do not verify real SAF grants, a handset layout, ARM64 execution or API 26/35 behavior. A separate JVM checks unavailable-JNI failures.
- `ArchiveStore` separates bounded ZIP inventory/extraction from the browser. Nested browsing restores parent folders/filters/pages and survives recreation, with explicit depth/retained-byte/text budgets. Complete-directory revalidation, expanded length/CRC32 checks, SHA-256, duplicate-name rejection and generated private output paths have host fixtures. Five additional Robolectric cases exercise real ZIP streams, Back/recreation, leaf ownership, failure/cancellation recovery and the 16-level boundary. This brings the UI suite to fourteen cases. See [archive-acquisition.md](archive-acquisition.md); cross-scan parent history, complete ZIP fields/provenance and abandoned snapshot cleanup remain pending.

## Reproduce checks and build

Use Linux, JDK 17, Python 3, a C/C++ compiler, CMake 3.22.1, Android SDK platform/build-tools 35 and NDK 28.2.13676358. Set `JAVA_HOME` and `ANDROID_HOME`. The wrapper pins Gradle 8.11.1 and its distribution SHA-256.

```sh
bash scripts/test.sh
bash scripts/test-apk.sh
bash scripts/test-models.sh
bash scripts/test-dex.sh
bash scripts/test-strings.sh
bash scripts/test-metadata.sh
bash scripts/test-resources.sh
bash scripts/test-binwalk.sh
bash scripts/test-mpv.sh
bash scripts/fetch-engine.sh
bash scripts/fetch-knowledge.sh
bash scripts/fetch-metadata.sh
bash scripts/fetch-binwalk.sh
bash scripts/build-binwalk.sh android
bash scripts/build-mpv.sh android
python3 scripts/fetch-mpv-sources.py --bundle
python3 scripts/mpv-notices.py --check
python3 scripts/mpv-references.py --check
bash scripts/test-native.sh
bash scripts/test-hashes.sh
bash scripts/test-knowledge.sh
bash scripts/test-trackers.sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
bash scripts/verify-apk.sh
bash scripts/test-workbench-ui.sh
```

For offline host tests, `APKSIG_JAR`, `APKSIG_SOURCES_JAR` and `JSON_JAR` accept cached dependencies but still verify their pinned checksums. `NATIVE_BUILD_DIR` retains an incremental native build directory; `test-native.sh` defaults to a disposable build and `test-workbench-ui.sh` defaults to `app/build/host-native`. Once engine/knowledge sources, Gradle/Maven artifacts and SDK components are cached, use `./gradlew --offline` for the build. The UI test script generates fixtures under `app/build/host-ui-fixtures`; to rerun without rebuilding them, invoke `./gradlew --offline :app:testDebugUnitTest` with the same `-PhostNativeLibraryDir`/`-PnativeFixturesDir` properties if their defaults were overridden. The host JNI library and fixture directory are declared Gradle test inputs, so native/fixture changes invalidate an old passing result. They must exist before invoking the test task directly; the script builds them first. JUnit 4.13.2/Robolectric 4.14.1 and its API 28 Android runtime are test-only dependencies. Retain both the Gradle cache and `app/build/test-runtime-home` (Robolectric runtime cache) for offline test runs. First-time dependency acquisition is build-time network activity; the app has no INTERNET permission and performs no automatic database downloads.

The workflow has separate Android/lint/package/UI, Linux JNI, APK/model, Binwalk and media jobs. The `die-android-arm64-debug` artifact contains the APK, adjacent SHA-256 file and `build-manifest.txt` with the source revision, bundled data revisions and signer identity. Packaging checks verify the APK signature, application ID/API levels, absence of INTERNET permission, bundled revision/license assets, ELF LOAD segment alignment and ZIP library placement for 16 KiB pages. Static alignment is **not** an Android 16 KiB runtime test. Debug keys, build paths and timestamps can differ between environments; bit-identical signed artifacts are not claimed. No release-signing identity was replaced. Preserve the last successful build's APK as fallback when doing device work.

Host dependency acquisition now uses a shared checksum-verifying staging helper
for DEX/runtime dependencies, host JSON, apksig and resource-test processors.
Transient HTTP failures get bounded retries; incomplete or wrong-checksum files
are removed before publication, while existing mismatched cache bytes fail
without being replaced. `DIE_OFFLINE=true` refuses missing inputs without a
download attempt. CI shares public host inputs through ignored `.ci-cache/`
paths and an Actions cache keyed by dependency scripts/locks; every use still
checks its pin. This prevents each test step from redownloading the same JARs.
`bash tests/test-dependency-fetch.sh` exercises reuse, interrupted/incorrect
downloads, edited-cache preservation and offline no-network behavior. Actual
offline DEX, signing and required AAPT2 fixtures validate the helper's integration.

Engine: `f055daa56fd3feef1db4d6dbc187cb292de24ea9`; signature data: `7ef816ee64ee36b4884da6b930a11542742affb7`. Knowledge versions remain pinned by `scripts/fetch-knowledge.sh`. No vendor checkout edits are required.

## Next priorities

Tracker adapter 2 adds shared required-literal indexing without changing RE2/J
matching authority. All active patterns in the audited public snapshot are
indexed; complex patterns fall back to ordinary search. The 191-pattern
differential and 400-rule / 30,003-name workload fixtures preserve results/spans
and make only three actual regex calls in the large synthetic case. The API 28
tracker cases also check exposed index counts and real DEX report work. Retain
the existing scope/unsupported/deadline/device limitations; see tracker docs.

1. Inspect the newest PR-head Actions jobs first. Fix regressions before adding features. Run the required host and Android checks for any changed native contract.
2. Verify handset flows: SAF/share/rescan, database activation/failed-import preservation/restart/rollback/export, native/evidence paging and help, range/resource export with real providers, offset/VA navigation, cancellation at each phase and recreation. Repeat on API 26 and current Android, including a real/emulated 16 KiB environment. No connected-device flow was exercised in this session. Review the dense Native Tools layout on small displays, rotation and screen readers. Current parameter/page/filter and pending export survive recreation; the full Back history does not.
3. Continue inherited/framework/cross-split DEX resolution beyond exact captured declarations, framework/split resource relationships, tracker compatibility and SDK fingerprint/version providers. Canonical LibChecker matching now retains all active rules, including regex and all ten evidence type adapters; this does not prove code identity/use or implement separate SDK version providers. Binary XML/resources.arsc has source-built parser, wire evidence and navigation fixtures; opaque overlays/staged aliases and canonical XML reconstruction remain incomplete. PackageManager summary metadata, the compared Exodus regex subset, and native DEX/ZIP recognition remain partial despite separate smali/resource fixtures. Prefer pinned, license-audited upstream APIs/engines over duplicate parsers. Add package/split relationships and cross-scan parent history alongside the fixture-tested nested archive browser. The resource provider already hashes and owns its complete parent APK.
4. Extend the existing typed structures/entropy and dedicated string/hex models with broader native format/field coverage, maintaining native limits and exact domains. Whole-file hashes now have a dedicated typed adapter/viewer; string investigation already has a separate classified model. Review any new contract against pinned `cdie_*_data` headers. Add broader format/malformed and resource-language/overlap fixtures and detailed native field references without claiming complete DataStructs parity.
5. Extend the implemented captured base/split declaration graph into full targeting/signing policy across package sets, broader desktop heuristic/reference comparisons and malformed-input coverage. Separate APK rotation/v3.1/v4/v4.1 signing fixtures and typed ownership/export now pass; platform acceptance and exhaustive algorithms remain unverified. Follow the detailed gaps in FEATURE-PARITY.md (archive families, directory/background batches and diff/full Qt profiles, format-specific/fuzzy hashes and complete per-field guidance).
6. Verify the Binwalk/PhotoRec/MPV/compiled-resource/hash isolated-worker OS boundary on a device, then migrate the other native/parser engines with cancellation/resource budgets. Do not infer arbitrary-hostile-input suitability from the current manifest/host checks. Add interrupted-import/stale-staging cleanup, power-loss recovery and snapshot storage reclamation. Existing atomic rename tests do not prove power-loss durability. Native script testing still has no cancellation scope.

The optional privileged process/service bridge remains absent. Do not infer live service state from manifest declarations or bypass ordinary UID restrictions. The complete linked/data license audit and reproducible release signing remain pending despite the bundled notices.

The binary engine additionally requires Rust 1.90.0 and `aarch64-linux-android`. `DIE_RUST_ROOT` can point to a standalone toolchain and `BINARY_BUILD_DIR` retain Cargo output; Cargo's cache/source checkout and toolchain must be retained for offline use. Gradle forwards `--offline` to its native build. The worker is private and unprivileged; it is unrelated to the still-absent Shizuku/root bridge. Its isolated-process design does not migrate the other parsers or imply handset verification.

Media host/UI tests require the libmpv2 runtime and FFmpeg (or MPV_HOST_LIBRARY for a real compatible host runtime). MPV_DEPS_DIR caches the exact official Android runtime; MPV_SOURCE_DIR caches all 23 dependency/submodule archives. The CI component-source artifact retains these archives and the source-build preparation recipe. Source layout preparation is verified; the complete upstream NDK r30 engine source build is not executed locally. The app build uses verified official runtime bytes and rebuilds the descriptor adapter from source. See media-engine/README.md for offline flags and exact scope.

Resource source/host tests use `RESOURCE_DEPS_DIR` for the pinned Google source JAR, AutoValue and build-only annotations. Gradle prepares/verifies all managed sources and rejects local vendor edits rather than overwriting them. `DIE_REQUIRE_AAPT2=true bash scripts/test-resources.sh` requires SDK platform/build-tools 35 and independent packaging/dump comparisons; without that flag/toolchain the script explicitly reports the reference-fixture omission. Source and all Maven caches must be retained for an offline rebuild. See resource-engine/README.md and docs/android-resources-investigation.md for source identities, exact limits and supported wire layouts.

Whole-file hash host references additionally require OpenSSL 3 with its legacy provider for development-only MD4 comparison; the app does not invoke or link that reference command. Both `diemobile` and `diehash` host targets are built by test-workbench-ui.sh. Gradle declares both host libraries as test inputs, so direct test-task reruns need both files. RHash source is retained/checksummed in Git; no extra source download is needed for the new digest engine. The existing engine checkout supplies the other pinned crypto contexts. See hash-engine/README.md.

Canonical knowledge is regenerated by fetch-knowledge.sh through the actual pinned upstream compiler. Keep generated-assets/knowledge/libchecker-v5.json and its SHA-256, or rebuild from the pinned source checkout with compile-libchecker.py; no runtime network is used. All 19 RE2/J Java sources/full licenses are retained in regex-engine and verified before compilation, so this module needs no extra source/runtime dependency download. The catalog is a local source build of dataVersion 45, not activation of an upstream online release. Source revision, compiler/content hashes and relevant source-dirty state are recorded. test-knowledge.sh builds a temporary canonical reference; test-workbench-ui.sh now also requires/generates AAPT2 manifest fixtures under app/build/host-ui-fixtures/android-resources. See docs/sdk-investigation.md.

Tracker reference checks require Python 3.12 / Unicode 15.0.0 (selected explicitly in CI), and fetch only the SHA-256-pinned Exodus Python source and Unicode 15 source for development. `TRACKER_DEPS_DIR` caches these inputs and `EXODUS_REFERENCE_SOURCE` accepts a checked local source path. The Android artifact bundles no Exodus core/database or Python runtime; the independent adapter, full Unicode notice, frozen ranges and source/generation locks are retained in Git. Regenerate the frozen table with `python3 scripts/tracker-unicode.py --source /path/to/UnicodeData-15.0.0.txt`; ordinary builds check its retained identity without a Unicode download. See docs/tracker-investigation.md.

The optional `DEX_REFERENCE_APK=/path/to/file.apk bash scripts/test-dex.sh` audit indexes a locally provided real APK without uploading it, printing its exact SHA-256 and source/class coverage. Keep it sequential with APK assembly so the audited file cannot be overwritten during acquisition. The standard synthetic tests remain self-contained and do not need a downloaded APK. Definitions run in process, have separate 30-second cooperative acquisition/lookup deadlines, and use private generated snapshot names. See docs/dex-definitions.md for source/record/text/byte budgets and limits.

APK signing builds strictly pin and SHA-256 verify the published binary through `:app:verifyApksig`. Host `scripts/test-apk.sh` also checks the exact source archive and reviewed public/input contracts from `signing-engine/source-lock.json`. Keep `APKSIG_JAR`, `APKSIG_SOURCES_JAR` (or the sibling `*-sources.jar` cache) and `JSON_JAR` for a fully offline run. Fixture keys are generated with the JDK keytool only in development; no inspected code is executed. The signing provider remains in process with cooperative read/deadline boundaries.

The workbench UI script also generates/requires SDK 35 AAPT2 package references under `app/build/host-ui-fixtures/packages`. `:app:verifyPackageContracts` checks retained AOSP reference identities/SDK selector IDs without network activity. To audit separately retrieved originals as well, run `python3 scripts/check-package-contract.py /directory/containing/the/two/source-files` using names in `package-engine/source-lock.json`. Package set acquisition/signing/graphs retain explicit cooperative budgets; compiled XML uses the existing descriptor worker. The set scope supplies no detached idsig and does not implement APK-set archive/device targeting or cross-split DEX/resource linking.

PhotoRec source builds require CMake/JDK 17 and NDK 28.2.13676358 for ARM64. `PHOTOREC_DEPS_DIR` caches the SHA-256-pinned `testdisk-7.2.tar.gz`; the default is `.ci-cache/photorec`. `DIE_OFFLINE=true` refuses downloads. Run `bash scripts/test-photorec.sh`, `bash scripts/build-photorec.sh android` and `python3 scripts/package-photorec-sources.py`; the source ZIP/checksum are in `app/build/photorec-sources`. The CI artifact is `photorec-upstream-and-adapter-sources`. Preserve the original archive and source ZIP for FOSS redistribution. All 358 translation units are built without optional JPEG/zlib/GUI/filesystem libraries, with path opens/renames denied. See carving-engine/README.md for the exact contract and standalone bundle rebuild.

PhotoRec adapter 2 resolves the reproduced 50,000-hit transport failure with
lossless rejected-occurrence/state dictionaries and lazy expansion. All 109,023
occurrences of a local 50,223,887-byte APK complete; its 256 MiB-heap host audit
streams 176,292,786 expanded characters or about 2,305,975 complete compact
capture characters. Source SHA-256 and exact scope are in the PhotoRec contract.
The 75,000-hit fixture checks every coordinate/original ordinal/full export,
dictionary rejection and exact reconstruction; four real-JNI viewer cases
include compact-capture source expiry/picker recreation. Large/random inputs
can still exceed 250,000 records / 24 MiB output; failure remains explicit.
Anchored weak patterns, full file checks, fragmentation and handset validation
remain the next carving priorities.

Profile host checks run with `bash scripts/test-profiles.sh`; `scripts/test-trackers.sh` covers the shared `CatalogJson` extraction through the existing Exodus facade. Catalog JSON retains opaque raw number tokens via the shared streaming encoder. Android tests use actual catalog files and native engine output; the operating-system document provider is substituted. Continue batch/report comparison against captured request/input identities rather than mutable current settings.

Batch host checks run with `bash scripts/test-batch.sh`; CI also runs them in the APK/model job. `BatchScanWorkbenchTest` uses actual CDiE JNI and the existing ELF fixture. Private batches remain under `filesDir/desktop-batches`, capped at eight sessions; preserve them when testing interruption and explicitly delete through the tool when unneeded. Complete reports can outlive input bytes, but byte actions require whole-source revalidation. Never resume an incomplete journal against a newly activated database or mutable preferences.

Comparison checks run with `bash scripts/test-comparison.sh` and `ComparisonWorkbenchTest`; the existing native fixture setup provides real hex JNI. Comparison source state lives in private cache sessions, with both 256 MiB inputs and an optional temporary replacement candidate; account for up to 768 MiB source storage before separate byte/export staging. Normal close removes owned state, configuration recreation retains it, and failed restoration preserves diagnostics. Do not treat a one-sided tail as proof of insertion/deletion or apply an old range index to a replaced content-pair ID.

During the batch CI run, Android assembly/lint/100 checks and native/binary/APK-model jobs passed while the media job remained in its external apt setup for more than 20 minutes. Host media dependency steps now set explicit connection/retry limits and a 12-minute step ceiling; setup failures remain visible and can be retried without claiming engine verification. The packaged MPV runtime/source pins are unchanged.

The bounded media CI setup then exposed the actual Ubuntu 26.04 issue: `libmpv-dev` pulled the complete unused development/graphics dependency tree through a slow regional mirror and timed out. The adapter already compiles against retained pinned headers. Host setup now installs only `libmpv2 ffmpeg --no-install-recommends`; shared runtime discovery accepts an explicit library, pkg-config, or the compiler-resolved versioned SONAME without the development symlink. Runtime-only discovery with pkg-config disabled exercises the real JNI/media/C-boundary suite locally, and the helper is included in the MPV corresponding-source bundle. Preserve the 2a49de3 fallback while this isolated CI fix is verified; scan-report comparison is under development separately.

The runtime-only CI fix passed all five jobs in run 44290253981 at head 1f41a7d, including the 105-test APK and actual MPV JNI/C-boundary/source checks. A checked exact CI APK/checksum/original clean manifest is preserved locally under `/workspace/work/fallback/1f41a7d-ci`; GitHub PR checkout used synthetic merge faf806a806fec5d88812e63ac5da3b5c64da1b32, whose complete source tree equals 1f41a7d. The run/artifact/head/merge/tree/APK mapping is retained at `/workspace/work/ci-build-provenance-1f41a7d.json`. This is source identity evidence, not a claim of bit-identical debug signing. Superseded 2a49de3/57c4a72 fallbacks were subsequently reclaimed for build space; the current 912e05d local, 1f41a7d CI, 398ec18 regression and both original fallbacks remain available.

Native report comparison host checks run with `bash scripts/test-scan-comparison.sh`; `scripts/test-batch.sh` also compiles the shared model. Batch evidence caches one pair and rebuilds from checked original reports after recreation; complete prepared exports are independent. Do not collapse duplicate parent instances, turn failed scans into absent detections, use a preview minimum as a native option, or treat a disabled custom archive as an effective native rule change.

Report serialization host checks run with `bash scripts/test-report-formats.sh`; Python standard-library XML/CSV readers reconstruct the complete JSON values and exact UTF-16 identity. These schemas intentionally use JSON-encoded strings/cells, not replacement of XML-forbidden code units. Keep first-row metadata and native ordinal records together; empty/failed/null/missing scan rows are distinct. The primary Save report remains JSON, with other complete formats under Reports / Log.

The first complete format-suite run hit a database asset parent-directory creation failure with constrained local workspace storage; the record is `/workspace/work/report-formats-release-build.log`. Directory creation now uses `Files.createDirectories`, retaining precise filesystem errors and accepting concurrent directory creation. Redundant owned fallbacks were reclaimed and a fresh complete 110-test/assembly/lint/APK run passes (`report-formats-release-build-2.log`). This does not establish device low-storage/power-loss behavior. Keep heavy host fixture generation sequential with tight-disk full builds.

Native structure continuation: adapter/reference policy v1 is implemented over
`cdie_file_info_visit_slice`, without another format parser. Run
`scripts/test-native.sh` for real PE/ELF/thin/fat fixtures, independent Python
byte expectations and the exact C variant serializer. Twelve NativeWorkbench
cases include three structure cases; the combined suite has 113 checks. Keep
reviewed field domains conservative: PE data-directory addresses include the
security/certificate file-offset exception, fat offsets need a captured slice
base, and zero-fill sections have no stored range. Additional format families,
semantic relationships/per-field references, resource limits under memory
pressure and real device/parser isolation remain pending. See
[native-structures.md](native-structures.md).

Native entropy continuation: `cdie_entropy_read` supplies exact whole-input
counts, all eligible regions and a complete contiguous block partition.
`scripts/test-native.sh` additionally compares the original report and a Python
byte Counter/log2/IEEE oracle across 18 inputs/612 measurements/4,608 bins;
callback exceptions and cancellation recover cleanly. Fourteen NativeWorkbench
cases include two entropy cases; the combined suite has 115 checks. The shared
native request observer serves structures/entropy; other native tools still
lack active cancellation/isolation. Interactive zoom/charts, more region/fat
semantics, real SAF/ARM64/crash/memory-pressure and hard resource verification
remain pending. See [native-entropy.md](native-entropy.md).

Primary interpretation continuation: `ScanTarget` is an immutable input-specific
request/catalog/capture model. Main reacquires bytes, reads the real native
compatible catalog and applies one resolved selector to both facts and scan.
It attaches source/engine/selector-checked context to captured reports without
changing the profile catalog contract or batch Automatic policy. Run
`scripts/test-models.sh` and `scripts/test-native.sh` for duplicate/malformed/
identity/report and actual known-rule/All-types/cache cases. Three new real
MainActivity cases bring the combined suite to 118. Full Qt format forcing,
profile/batch interpretation policy, broader raw/malformed fixture coverage and
real device/SAF/process-death behavior remain pending. See
[scan-interpretations.md](scan-interpretations.md).

PhotoRec continuation after the v0.5.0-alpha save point: adapter 3 / native schema
2 replaces file-sized native allocation with owned pread FD windows, raises
PhotoRec acquisition to 256 MiB and leaves Binwalk at 64 MiB. `blob_input.h`
contains the parser-independent IO/lease/padding/identity layer. Run
`scripts/test-photorec.sh` for old naive/dense/callback fixtures plus standalone
reader tests and sparse/fully written 80 MiB and 256 MiB scale/cap cases. The
input-read buffer payload bound is 1.25 MiB, not a file-size or whole-process RSS
cap. Old compact captures remain accepted under their original limit. The new
corresponding-source ZIP includes both new tests and is rebuilt offline. Actual
device behavior and all prior full-file/weak-signature/fragment-recovery gaps
remain pending. See [PhotoRec contracts](photorec-investigation.md).

The immutable `v0.5.0-alpha` save point remains at `55ebdde`; subsequent development is labeled `0.6.0-alpha-dev` / code 6 to distinguish its APKs from that checkpoint.

Binwalk adapter 2 continuation: `binary-engine/src/input.rs` separates owned
CLOEXEC positional acquisition from upstream contiguous-slice parsers. It fixes
a reproduced caller seek-position change, refuses non-data/non-regular FDs,
checks ordinary source changes and reports exact copy/read/deadline contracts.
`scripts/test-binwalk.sh` now runs six Rust IO/budget tests and actual JNI
position/legacy/accounting/64 MiB read-seam/EOF cases. The original upstream and
Cargo pins remain unchanged. Keep its file-sized-copy limit distinct from
PhotoRec's 1.25 MiB input-read windows; callbacks still require the isolated
worker for external cancellation. See [Binwalk contracts](binwalk-investigation.md).

PhotoRec adapter 3 at `1b6596b` passed all five CI jobs in run 44468890115,
including 118 Android host checks, source packaging and versioned checkpoint
packaging. Its CI savepoint artifact is 14503528311; the immutable local
`0.6.0-alpha-dev-1b6596b` checkpoint records the matching source/byte identities.
The later Binwalk adapter-2 local run also passes all 118 checks, ARM64 assembly,
lint, signing/offline/identity/fourteen-library 16 KiB checks, required core/native
suites and the shared PhotoRec streaming regressions. Real device verification
remains pending.

Desktop batch interpretation continuation: schema/adapter 2 captures a separate
`ScanTarget.Request` and resolves the exact name against every acquired input's
complete catalog. `DesktopBatchAnalyzer` uses one per-file selector for native
facts/scanning; unresolved names store explicit failed reports and retain bytes.
`ScanTarget.Capture.read` checks saved source/engine/resolution/selector claims
without rewriting original/unknown evidence. Batch-wide `options.typeId` is null
for named policy; actual per-file selectors are in nested reports. Schema-1
Automatic journals stay readable. Run `scripts/test-batch.sh`,
`scripts/test-models.sh` and `scripts/test-scan-comparison.sh`; three new actual
native Android cases bring the complete suite to 121. Cancellation ownership
starts before a queued catalog call; do not move it behind engine serialization.
Per-file overrides, arbitrary forcing, saved-profile attribution and real device
flows remain incomplete. See [batch contracts](batch-scanning.md).

The first 121-test combined run hit workspace disk exhaustion while the profile
fixture copied the bundled rule database; 120 checks passed. The owned generated
MPV source ZIP was reclaimed for test headroom, retaining every pinned source
archive, and is regenerated offline after verification. This is a local storage
constraint, not a skipped check or evidence of device low-storage handling.
Comparison schema 2 retains the interpretation contexts and distinguishes null
unknown selector-dependent conditions from known flag/rule/engine differences;
the exact tree-path algorithm remains version 1.

A fresh combined run passes all 121 checks with zero failures/errors/skips,
ARM64 assembly/lint and signing/identity/offline/fourteen-library static 16 KiB
verification. The complete MPV source bundle was regenerated and all original
source pins verified. Binwalk adapter 2 at `e676f91` also passed all five CI jobs
in run 44470495585. The newest verified local development checkpoint supersedes
the older owned `0.6.0-alpha-dev-1b6596b` copy for disk space; its earlier CI
artifact remains available. Keep the frozen 0.5.0-alpha checkpoint, original
fallbacks and current source/checkpoint manifests.

Binder registry continuation: fixed normal-UID command capture is separate from
parsing and the viewer. `BoundedProcessOutput` owns IO/deadlines/cleanup;
`BinderServiceCapture` writes a new source-identified envelope; `BinderRegistry`
retains raw syntax/diagnostics/duplicates/unknowns and explicit visibility
uncertainty; `BinderRegistryBackend` owns separate envelope/output coordinates.
Recreation checks the original capture identity without rerunning the command,
and prepared exports outlive source expiry. The exact AOSP reference and full
Apache-2.0 notice are pinned, packaged and verified at build/APK time. Run
`scripts/test-system-services.sh`; three Android host cases bring the complete
suite to 124. The original normal-UID service explorer used to display all
output/error lines as registrations; that evidence error is corrected. Actual
Android command/UID/SELinux/provider/handset behavior and privileged diagnostics
remain pending. See [Binder contracts](binder-registry.md).

The per-tool [input budget/verified scale matrix](input-budgets.md) separates
implemented limits from actual sparse/fully written/real-APK host checks and
from device acceptance. PhotoRec's 1.25 MiB input-read buffer payload is not a
file-size or whole-process memory cap. A fresh full Binder integration run passes
all 124 tests (zero failures/errors/skips), ARM64 assembly/lint and APK signature,
offline, identity, source-reference and fourteen-library static 16 KiB checks.
The corresponding MPV component-source archive was regenerated with all pins
verified. Batch interpretations at `24e8b2b` passed all five CI jobs in run
44473098270, including checkpoint/source packaging.

The user has expanded the active scope to a content-first **Gallerize** view,
collapsed/expandable definitions, prioritized file/composition summaries, grouped
tools, labeled in-app media/text/contents previews, configurable byte/entropy
grids with intelligent defaults and full overrides, and broader content-based
archive recognition/decompression including embedded/disguised cases. Preserve
all existing functions. The acceptance fixture is multiple concatenated media
files plus text, visibly identified with exact source ranges. See
[gallerize-workbench.md](gallerize-workbench.md); that document records requested
work and is not an implementation claim.

Presentation foundation continuation: `ExpandableHelp` owns persistent individual
and global definition state, separate from facts/tool groups and scan settings.
`ToolCatalog` keeps all native IDs 0–10 and prior provider/expert workflows behind
purpose-based groups. Main prioritizes type/identity, removes the redundant
Reference tab, and restores old page 2 to File info; `ReferenceSections` retains
the guide with observed facts expanded. Generic/native views also collapse only
help; `statusSummary` keeps MPV/resource/carving incomplete/partial facts visible.
A targeted 27-case run exposed then verified the MPV status separation; the
PhotoRec host contract now explicitly checks the factual status API. Run
`scripts/test.sh` (which includes test-presentation), native tests and the full
127-case Android suite. See [content-presentation.md](content-presentation.md).
Gallerize/media rendering/grids and the broader archive engine remain active
next work; prototype header recognition is not that archive implementation.
The pinned libarchive release selected for investigation is 3.8.11, source
commit 03036ffa409a2aa1c5bbad35be2202641a040fda; no libarchive integration is yet
claimed. Keep the f56e397 working Binder checkpoint while this slice is verified.
The f56e397 CI package-install timeout was infrastructure-related; rerunning the
job passed all five checks in run 44477726846.

The presentation foundation passes all 127 Android host checks with zero
failures/errors/skips, ARM64 assembly and lint, required core/native suites and
PhotoRec streaming regressions. Coverage assertions now check the visible
status API independently of optional help. Source/reference/signature/offline/
static alignment verification and corresponding-source archives accompany the
checkpoint; real device behavior remains unverified. Local build headroom was
increased by reclaiming only the owned extracted-CMake installer ZIP and unused
NDK LLDB debugger library; compiler/linker/runtime inputs and original pinned
engine source archives remain retained.

Gallerize continuation: `ContentMap` maps accepted/native-row identities, bounded
artifacts, unassigned unions/gaps and strict gap UTF-8 spans; unknown extents
stay null. `GallerizeSession` owns source/capture SHA-256 identities and restores
the original capture without rerunning native work. Its PhotoRec backend borrows
the existing input, preserving caller ownership on failure/close. `ByteGrid`
streams every byte into exact manual/automatic row-major cells and exports all
values/ranges/IEEE representations. `ContentGalleryAdapter` owns one 24-card
page and deduplicates bounded thumbnails; GallerizeActivity handles navigation/
staged exports, and ContentPreviewActivity owns separately acquired exact ranges
for first-frame images, text/hex, local playback controls and paged PDF wiring.
The main tab uses tag 8, preserving old Reference/page mappings and every native
or provider action. APK verification checks both new activities remain private.

Run `scripts/test-gallerize.sh` with the existing PhotoRec/JSON dependencies. It
checks four concatenated media files plus Unicode text, exact derived bytes,
full native/catalog exports, capture corruption/refusal/restoration, UTF-8 IO
seams, independent means/log2 entropy/partitions/IEEE exports and a fully written
80 MiB + 17-byte input with a PNG beyond 64 MiB and Unicode tail. Seven Android
host cases bring the full suite to **134, zero failures/errors/skips**. The
combined ARM64 assembly/lint, required core/native, PhotoRec streaming, shared
Binwalk and APK signature/offline/identity/source-reference/fourteen-library
static 16 KiB checks pass. MPV/PhotoRec source ZIPs were regenerated and verified.
Platform media/PDF decoding, actual ARM64/isolated UID/Binder/SAF, process death,
performance and 16 KiB runtime remain separate unverified acceptance work. See
[gallerize-implementation.md](gallerize-implementation.md) for exact scope,
budgets, missing animation/video thumbnails and other remaining requirements.

Presentation at `46f95c0` passed all five CI jobs in run 44546813328; its savepoint
artifact is 14543560216. The verified local content-presentation checkpoint
supersedes the older owned Binder checkpoint for space; the frozen 0.5.0-alpha,
original fallback APKs and current checkpoint remain preserved. Binder's CI
savepoint 14538634930 remains available in run 44477726846.

Broader archive investigation: original libarchive 3.8.11 source commit
03036ffa409a2aa1c5bbad35be2202641a040fda, codeload archive SHA-256
87d05df74305b7f16b784852e49f434e09ce2b3bb29423461f717fc988742a78.
A host-only source-library prototype exists under
`/workspace/work/archive-integration/host-library`, with logs and the original
archive alongside it. This is **not Android archive integration**. The host
prototype currently found zlib/zstd, not bzip2/liblzma/lz4. The existing Cargo
cache includes liblzma-sys 0.4.9's original XZ 5.8.4 library sources. Pin/build
all selected codecs for both host/Android; do not claim missing codecs work.
Register only reviewed format/filter functions; never use external-program
filters, path extraction or disk readers. The mtree reader has an optional
`checkfs` path-opening mode (default false); exclude it or explicitly keep all
filesystem resolution disabled. An all-formats/all-filters helper can clear
warnings or register external-program fallbacks, so it is not the intended
adapter contract. Next: descriptor-only bounded acquisition, typed complete
archive entries, safe generated-name member bytes and content-based/nested/
embedded browsing, then richer rendering and the sourced knowledge cards.

Archive-result correction: the first Gallerize release opened the ZIP browser
without receiving its selected-member result. Both Gallerize and ContentPreview
now use a result request and the shared `ArchivePreviewLauncher` /
`ArchiveMemberHandoff` boundary. It rejects paths outside the browser's owned
leaf directory, acquires the exact expected SHA-256 into a separate preview,
releases the old temporary, and labels original physical offsets as unavailable
for decompressed members. A real outer ZIP → nested ZIP → Unicode text case
exercises both callers and parent closure, with forged-path refusal. Printable
strict UTF-8 headers now select text automatically; manual overrides remain.
Unused grid cells are magenta to distinguish them from valid grayscale data.
The targeted 13 checks and complete **135-case suite** pass with zero failures,
errors or skips, plus core/80 MiB host fixtures, ARM64 assembly/lint and static
APK checks. Actual handset/SAF/media/worker behavior remains unverified.
Gallerize at `11b5af9` passed all five CI jobs in run 44552943212; its immutable
CI savepoint artifact is 14545440333 and APK artifact is 14545510241.

Broader archive core now exists in `archive-engine/`, with fully pinned original
libarchive/bzip2/XZ/LZ4/Zstandard source archives, complete API/license contracts,
verified notices and reproducible host/ARM64 builds. `ArchiveEngine` is the JNI
contract; `archive_reader.cpp` owns regular positional input, explicit format/
filter registration, complete or explicitly failed metadata scopes and exact
selected-member logical output to a separate empty writable FD. No sample
pathname, disk reader, path extraction or external-program filter is accepted.
Source metadata/xattr/ACL/sparse/link/Mac state and raw/UTF-8 accessor bytes remain
separate. Public digest getters return buffers even when unset; their presence
is unknown rather than a claimed declared checksum.

`test-archive-engine.sh` passes **25 captures and 37 exact acquisitions**, including
independent TAR/PAX/ZIP and gzip/bzip2/XZ/LZMA/Zstandard/LZ4 frames, pinned 7z,
RAR5, CAB STORE/MSZIP/LZX and compressed ISO references, a **16 MiB + 17-byte**
member, embedded scopes, duplicate names, link/CRC/refusal/FD/size checks and a
direct unmodified CAB header-position oracle. CAB positions vary when data is
read versus skipped; both original observations are retained, excluded from
stable identity and never promoted to physical offsets. LZMA memory estimates/
decoder limits and Zstandard windows are capped at 64 MiB through linker
wrappers; valid excessive-dictionary/window inputs are independently decoded
then refused here. Those caps do not bound whole worker memory. Android
cross-compilation/static 16 KiB alignment and direct program/path-import checks
pass. The source ZIP was unpacked separately and rebuilt offline with the same
host fixtures successfully; original archives, corresponding-source bundle and
logs remain retained. The owned temporary standalone rebuild is reclaimed after
verification. The unused NDK libclang-cpp tool-support library was reclaimed;
compiler/linker/libclang C API inputs, sources and APK checkpoints remain intact.

**Next integration is still required:** add descriptor-only archive inspect/
selected-output IPC to the isolated worker/client, source-bound typed inventory
and acquisition validation, hierarchy/nested Back/owned byte transfer, exports/
preview links and APK notice/source packaging. Current app archive UI remains
ZIP-family; do not claim the new core is exposed in the APK yet. New CI steps
compile the ARM64 core, run real host fixtures and upload corresponding sources.
See [module contracts](../archive-engine/README.md). Keep the current working
Gallerize APK while this is wired. No device runtime, worker UID/Binder/cancel/
crash/SAF, decoder/PDF/media or 16 KiB runtime behavior is verified.

## GlassDigestor identity and numbered pre-release (20 November 2026)

The project display name is now GlassDigestor. The repository URL and
`org.diemobile` application ID remain stable; Detect It Easy/CDiE is credited as
an engine. The maintainer selected the generated lens/layers logo. Android's
adaptive icon uses the unchanged original artwork; provenance/license are in
`docs/branding/README.md`.

`version.properties` now supplies the numbered `0.6.0-pre-alpha.1` / code 7
checkpoint. Debug versions append a Git revision and optional `.dirty`; the APK,
installed version, build details, verifier and checkpoint packager share that
contract. Scripts resolve AGP's actual output metadata instead of expecting
`app-debug.apk`. See `docs/build-versioning.md` and release notes. The prior
`v0.5.0-alpha` tag remains immutable.

Archive-core commit `455ec6a7b204e5e224b879311fa397f8f424c4c1` passed all five
jobs in run `44565494822` (#117), including the actual archive host fixtures and
ARM64 core build. Its saved checkpoint is artifact `14553815003`; archive
corresponding sources are artifact `14553021058`. The old execution workspace
was replaced; its local scratch/APKs/signing key are no longer present here.
Pushed source and retained CI artifacts are the recovery points. The current
workspace was restored from the remote branch and pinned dependencies. Debug
signing configuration is unchanged; independent keys must not be presented as
the same signer.

The unfinished archive worker/browser edits from that replaced workspace were
not committed. Reapply the descriptor-only `ArchiveRunner`/worker acquisition
protocol, source-bound metadata/payload store and broader archive tree as the
next functional slice. Preserve the existing ZIP browser and its checked
member handoff. The broader libarchive core is source/host/ARM64 verified,
**not yet an exposed app feature** in this pre-release. Device/SAF/isolation/
playback verification and remaining desktop parity work remain outstanding.

The numbered/branding slice passed local ARM64 assembly, lint and the updated
APK verifier, including the installed label/version and all 14 native libraries.
A cold Gradle build also reproduced a preparer refusal of an empty precreated
output directory. The preparer now adopts only an empty unmanaged directory;
unknown files, source edits and symlinks still fail, with a regression fixture.

## Default branch and repeatable publication

The verified GlassDigestor checkpoint was integrated through PR #1 into main
`1fa7bb39cde2342049112d0648ed83bb93899758`. Its tree equals development commit
`a30a1646677cbcc86a2dbe2f65d1358f14499edc`. All five CI jobs in run
`44606318262` (#119) passed, and the same development tree passed 135 local
Android host tests with zero failures/errors/skips.

The captured CI source is the generated merge commit
`119e255a9150099f6c54b09a9a6f161e0d68c1c2`; the pre-release uses that exact
revision, not a relabeled rebuild. Its APK SHA-256 is
`2b47ab8c3787cba625cc3939275649e50dfdd24145a8f83e0f1beb5a0d8d60de`.
All four artifact digests/inner inventories and the downloaded APK's static
signature/version/offline/library/alignment checks were independently verified.
The environment can create release metadata but its credential route cannot
upload to uploads.github.com. Publication therefore uses the dedicated
repository workflow with GitHub's normal token; see `docs/build-versioning.md`.
The workflow leaves failures in a draft and never overwrites assets or tags.

Continue the Binwalk/PhotoRec checked exports and gallery/owned-preview handoff
with dense, consistent formatting, then complete the broader native archive
worker/tree. Preserve complete raw evidence and unknown extents. Hardware,
media runtime, actual isolated UID/SAF and full desktop parity remain pending.

## Checked recovery preview/export slice

The working recovery slice connects actual Binwalk/PhotoRec bounded findings to
independent previews and checked named exports, adds source-independent preview
byte exports with picker recreation, and hands captured whole inputs to
Gallerize through validated private ownership. Four new API 28 actual-JNI
cases and the existing twelve carving/gallery cases pass. Shared acquisition
checks, the fully written 80 MiB gallery/grid fixture, generic/native scanner
checks and complete SDK/tracker host contracts pass. The full 139-case Android host suite passes with zero failures/errors/skips;
ARM64 assembly, lint and APK signature/version/offline/static-alignment checks
also pass. CI for this slice is the next gate. See
`docs/recovered-content.md` for implemented scope and unverified runtime limits.

The public release is https://github.com/sperglord8008s/DiE-Android-/releases/tag/v0.6.0-pre-alpha.1
with exact source `119e255a9150099f6c54b09a9a6f161e0d68c1c2`, APK SHA-256
`2b47ab8c3787cba625cc3939275649e50dfdd24145a8f83e0f1beb5a0d8d60de`
and all 14 verified assets. The publisher now streams directly to the checked
draft ID, preserves every metadata identity field on updates, validates the
returned public tag/source and can verify an already published release without
rewriting it. Six contract cases and successful publication/idempotence runs
`44614616127` / `44615704588` document these paths. The fix is merged through PR #3 at
`f6ea298e61b82a1c0cc296ab372b57d2048e7d65`; the published artifact bytes remain
unchanged. All five jobs in fix CI run `44616666002` (#126) passed.

## Published recovery checkpoint and broader archive integration

Recovery commit `3d9e963edfde4855cd0842ba455f7e4be4091b4f` passed all five
jobs in run `44620743518` (#129), merged through PR #4 to main
`998fd8914626aa177ce7868773d917d5c9e8232b`. Pre-alpha.2 is published from
exact generated CI merge `e049c2a37fe3a7de65eb231c665ca79b6c610e80`;
publisher run `44622664581` succeeded. Original savepoint artifact
`14577816242`, APK SHA-256
`64932fbee9ce2d755954ab3b89e6991cf8a357df510fd5d8d8a4df1f8354c3b9`.
The downloaded artifact/checkpoint checksums, public asset digests/sizes and
exact-source APK signature/offline/version/14-library/alignment checks pass.
Local clean source checkpoint `3d9e963` is separate, with local debug signer
and APK SHA-256
`50550a3b7b6817c22085c36062d13514d561a2283add14bb55b2fb2b46ce6553`.

Current source advances to 0.6.0-pre-alpha.3 / code 9 for the broader archive
APK integration. `ArchiveRunner` extends the isolated descriptor protocol with
explicit offset/length/member index and an optional separate empty payload FD.
`NativeArchiveStore` verifies pinned native schema/safety/source/scope/indexed
metadata and independently hashes complete output. `NativeArchiveSession`
retains bounded owned input/report files, current nested chain and failed
attempts; finished saved navigation restores without native rescanning. The
broader browser supports virtual folders, complete indexed entries, nested
Back, explicit embedded intervals, raw metadata, checked media/byte previews,
member return and current-chain exports. Main's old ZIP-only availability gate
is removed; the existing classic ZIP browser remains available.

The model passes all 25 native fixture captures / 37 independent payload
expectations, including indexed duplicate names, a 16 MiB + 17-byte member,
source expiry, cancellation, failures and tamper refusal. Five real-JNI
API 28 browser cases, Main’s actual-CDiE handoff boundary and four worker
manifest/refusal/descriptor cases pass. The batch recreation test now completes
its independent cold database preparation before blocking the native scan; its
ownership assertion and real scan are preserved.
Generic and required scanner/native checks pass. The complete **147-case** Android host suite passes with zero failures/errors/
skips; ARM64 assembly, lint and fifteen-library APK signature/offline/version/
notice/static-alignment checks also pass. Source CI remains the next gate.
The regenerated archive source ZIP is verified with SHA-256
`d5d9c26354e79120f2ef084b48263e0abc49779df80075e1d6b1e41deda74cea`.
See `docs/native-archives.md` for exact implementation/verification limits.

Next after that gate: persistent per-preview native-origin sidecars and
cross-scan parent history, smarter archive/media gallery presentation, then the
sourced practical/deep package knowledge cards and remaining desktop parity.
Actual device UID/Binder/SAF/media/PDF/cancel/crash/cache-pressure and 16 KiB
runtime are not verified by these host or compile checks.

## Archive checkpoint published; embedded finding links

Archive commit `021622632859c743264ab653234dea53ec5a994c` passed all five
jobs in run `44650839550` (#132), merged through PR #5 to main
`cb21238b7ddadd9b40fc303510ee592ceb7afb43`. Pre-alpha.3 is public from
exact generated CI merge `78edded6db49efdfb9bc3e823182c18e7d6854ee`;
publisher run `44652686204` succeeded. CI savepoint `14590878892`, APK SHA-256
`4f7ecc417b2138a23e5089cedd96c3a98e367cc029bc997867ec7c2c337c200d`.
Downloaded checkpoint, all three exact CI component bundles and all public
sizes/digests agree; the exact-source fifteen-library static APK verifier passes.
The separate clean local 0.6.0-pre-alpha.3 checkpoint at `0216226` has local
signer/bytes and SHA-256
`6492b74c77cf34fd97d483898132f9730a2c613e2d7b589b6f94ad91a7ca2426`.

The next source slice advances to **0.6.0-pre-alpha.4 / code 10** on
`codex/embedded-archive-links`. `ArchiveLaunch` captures complete original
finding/provenance/source identity in a bounded, UTF-16-preserving typed
request. Binwalk/PhotoRec row actions can try a native parsing tail even with
unknown reported extent; they do not change the original range or enable its
automatic stored-byte recovery. Main/Gallerize/preview caller expectations are
verified before native parsing. Original launch context survives manual scope
changes and restoration and is included in complete native archive exports.
Selected-member results carry root source identity; the investigation viewer
uses the checked private handoff to preview them even after its parent cache
expires. Classic ZIP forwarding preserves those root fields too.

Standalone actual Binwalk → embedded TAR and PhotoRec unknown-extent gzip →
libarchive chains pass independent decoded-byte expectations and source-expiry,
restoration-without-rescan, exact Unicode/provenance, type/identity/budget
refusals. Four new Android host cases bring the full suite to **151**, with
zero failures/errors/skips. Generic/required scanner-native, 25/37 archive model,
ARM64 assembly/lint and fifteen-library APK static checks pass. Source CI is
the next gate; see `docs/archive-launches.md`. Persistent per-preview native
origin sidecars/all-visited-node history, richer gallery presentation, sourced
practical/deep package knowledge and remaining desktop parity are still next.
Device UID/Binder/SAF/cancel/crash/media/cache/16 KiB runtime is unverified.


## Published embedded links; large text and report paging

Embedded-link commit `53935a2f3850247d89ce8dae0d31721052ba8c69` passed all
five jobs in run `44654569828` (#135), merged through PR #6 to main
`82ef741006dcf6f9e89c756065bd48ae40ad7c91`. Pre-alpha.4 is public from
exact generated CI source `97ba888a9284719f1e399b05a330fef1db2aadc4`;
publisher run `44657520167` succeeded. APK SHA-256
`2debb181b15dc486dfdb3a876c64ebfa51688fb7f355d21907befd340e939e42`.
The downloaded checkpoint/public inventory and exact-source static APK verifier
pass. The publisher checked all three corresponding-source bundles; the local
independent audit did not redownload those .4 component archives. The clean
local .4 fallback at `53935a2` is distinct, SHA-256
`41d42e8c928902c3bc06dc170c61a106b051872977835bcdbe643cd0f36b547e`.

The next slice replaces the one-MiB UTF-8 / first-page Hex preview with exact
bounded pages, byte offsets, history and Tail. `Utf8Pages` preserves codepoint
seams and explicit failures; source identity checks surround preview page IO.
`PagedTextDialog` reads complete staged rendered reports, retaining the full
export and allowing Close before the first frame without a busy-state leak.
Five added Android host cases bring the full suite to **156** with zero
failures/errors/skips. Independent host tests reconstruct 161,892 pages and a
fully written 24,117,266-byte text file. Required generic/native checks, ARM64
assembly/lint and fifteen-library APK checks pass; source CI is the next gate.
See [text paging](text-paging.md) for exact contracts, commands and limits.
Selected-record fact dialogs retain the explicit 128 KiB display cap; UTF-8
paging does not imply other encoding support or whole-input text validation.

Next: persistent per-preview native-origin sidecars and full witness export,
richer useful gallery presentation, then sourced practical/deep package cards
and remaining desktop parity. The existing pinned UAD-NG catalog already
contains 5,399 exact package records; reuse it with provenance and typed
relationships rather than adding another unattributed catalog. Real device
UID/Binder/SAF/media/PDF/cancel/crash/process-death/16 KiB runtime remains
unverified. Preserve the working local and public APK fallbacks.


## Text checkpoint source accepted; independent preview origin captures

Text-paging commit `df082d961a06a705e68dd268ccaabf7c74b32c50` passed all
five jobs in run `44661425550` (#138), merged through PR #7 to main
`7f1cae35b61eec96878841969719bb70fa0d31ca`. Pre-alpha.5 is published from
exact CI source `37e7feea2ce3ede151cd5b29ccea499255f943e8`; publisher run
`44664099702` succeeded. Public APK SHA-256
`55262ca8e5f7260be53cc39a2bb16c7074a987785d73e181196dffa5d45ad51f`. Clean local .5 checkpoint SHA-256
`e0d8e86287221c0227dd67ebbe142ba9b6f640ec8168398f5e277655891acc82`.

The .6 slice adds `ContentOrigin`: independently owned, exact-subject-bound
JSON Lines captures, size/SHA display, paged full viewing, checked complete
exports, recreation/picker lifetime and strict private-leaf cleanup. Binwalk/
PhotoRec preview captures retain full selected row/provenance/metadata; gallery
cards retain exact scope/native row; native previews preserve current nested
raw reports/acquisitions/failures/launch context plus selected member evidence.
Logical members do not gain physical offsets. A new streaming native trail
writer avoids a second complete parsed report tree.

Four new viewer cases and one actual nested ZIP → TAR → member case bring the
full Android host suite to **161**, with zero failures/errors/skips. Existing
actual Binwalk/PhotoRec/Gallerize cases assert their origin outputs. Independent
128 MiB-JVM/Python JSON Lines/byte oracles cover all 65,536 UTF-16 units, unknown
values, lexical/raw data, source expiry and mutation/path/symlink/type/budget/
cancel/cleanup refusal. Required generic/native/archive model, ARM64 assembly/
lint and fifteen-library static APK checks pass; source CI is the next gate.
See [origin contracts](content-origins.md) for scope and commands. Main return-
and-rescan, Classic ZIP, cross-Activity inherited origin chains and all-visited
archive history remain incomplete; real device acceptance remains unverified.

Continue origin ancestry integration, richer useful gallery presentation,
sourced package knowledge and remaining parity. A source-attributed typed UAD
model is being prototyped outside the repository before integration; do not
claim it as implemented until it is committed and tested. Reuse the bundled
5,399-record UAD-NG snapshot with full unknown fields and explicit community
claims, exact IDs, directed dependency occurrences and source revision/hash.


## Origin checkpoint published; sourced package knowledge

Origin commit `ae4f87b655b15a9d4b42bc1ca037e6e7ad5ddc54` passed all five
jobs in run `44664429934` (#141), merged through PR #8 to main
`9aba6076e7db448c6ecc876f2c54e4eaec34651e`. Pre-alpha.6 is public from
exact CI source `92c8851204836d3a65182e78438af5f88bd53d75`; publisher run
`44718456325` succeeded. Public APK SHA-256
`aad652601fa005848d5cc2826b1b57965c46537ae0709d70900aaee3579074b3`.
The separate clean local .6 checkpoint at `ae4f87b` has SHA-256
`cac6d38ca6a49b89499b4ee0c085c2753f6f7a993dba6f827efdf93200e85db6`.
Public .5's downloaded checkpoint/public inventory and exact-source static APK
verifier passed; .6's complete checkpoint/public inventory and exact-source
static APK verifier also pass. The .6 audit did not independently redownload the
three component source archives; the publisher verified them.
Publisher gates include all three corresponding-source artifacts. Original
component pins and packaged identities/signing configuration remain unchanged.

The .7 source slice replaces one-value package lookup with a lossless typed
UAD-NG catalog and owned investigation backend. The original file actually
contains **5,402 record occurrences / 5,399 distinct IDs**: three duplicate-ID
pairs carry differing descriptions/categories. The new reader exposes every
occurrence and uses original indices for exact record/value actions; it never
selects a preferred source claim. The optional collection-duplicate mode leaves
existing tracker/default duplicate rejection unchanged. APK summaries now
preserve these duplicate community records too.

File-independent catalog/main options and exact installed-ID links expose
practical descriptions first, every original/unknown field with context, full
search, category/coverage groups, directed dependency occurrences/reverse
references, source identity/licensing and complete record/value/source exports.
Private source snapshots survive recreation; pending exports survive expiry.
Claims remain separate from APK/signer/static observations and runtime behavior;
no package removal/disabling/execution or network operation is provided.

Six new API 28 host cases bring the complete suite to **167**, with zero
failures/errors/skips. Independent 128 MiB-JVM/Python oracles reconstruct every
actual source record and UTF-16 unit, exact numeric/raw/unknown values, graph
occurrences, source restoration without reopening, pin/type/index/budget/cancel
refusals and prepared-export ownership. Required generic/native checks, 924
tracker pinned-reference/strict-import and shared prefilter regressions, ARM64
assembly/lint and fifteen-library APK verification pass. Source CI is next; see
[knowledge contracts](package-knowledge.md) and the .7 release note.

Next: gallery per-card embedded archive parsing and explicit manual inspection
ranges, then inherited origin chains, broader useful media presentation and
remaining desktop parity. Knowledge follow-up includes captured APK/signer
correlation, more attributed sources, user overlays and version/device scopes.
Real handset/UID/Binder/SAF/media/PDF/process-death/heap/cache/16 KiB runtime
acceptance remains unverified. Preserve current/fallback APKs and exact sources.


## Package knowledge published; gallery archive/range actions

Knowledge commit `facd03afe76e7b9cb813f71d970440c3c4349551` passed all five
jobs in run `44719613831` (#144), merged through PR #9 to main
`3c17379d9d0019ffdc5f0101b8f6d718f3df151c`. Pre-alpha.7 is public from
exact CI source `9b750e6cb0796d0d4fe7506f4d8dbe89f83a1a48`; publisher run
`44721996928` succeeded. Public APK SHA-256
`55428bea8f5617c5e42489980d50e2142d72b81a5bb44969cb009475618cc4c8`.
The separate clean local .7 checkpoint at `facd03a` has SHA-256
`e502195626865e30b65ce294a6d51fc0583cd14ba192839b087b9571601a8dd7`.

The .8 slice gives header-only gallery cards real actions and exposes direct
source-bound archive parsing plus decimal/hex inspection intervals. Original
card geometry is checked against its native witness before launch. Original
known/null extents remain unchanged; manual/grid origins state their selected
byte scope and preserve associated native findings without claiming a recovered
file boundary. `galleryContext` retains complete card/provenance/row evidence
through native restoration, scope changes, reports and member-preview origins.
Gallery header/card JSON now uses the exact UTF-16 writer. Existing tools and
checked byte exports remain available.

Three new API 28 viewer cases bring the full suite to **170**, with zero
failures/errors/skips. Actual PhotoRec → unknown-extent embedded gzip gallery
card → libarchive tests pass exact independent payload/hash expectations, full
UTF-16 context/origins, source expiry/no-rescan restoration and stale/cancel/
budget/geometry refusals. The real four-file/text and fully written 80 MiB + 17
byte gallery/grid regression remains green. Required generic/native checks,
ARM64 assembly/lint and fifteen-library APK checks pass; source CI is next.
See [gallery range contracts](gallery-inspection-ranges.md).

Next focus: real Android worker/descriptor/UID acceptance where an Android
runtime can be supplied, then inherited origin chains, broader useful media
presentation and remaining parity. The current environment has no KVM/device;
a separate x86 Android runtime lab using the actual production worker/hash/
archive sources is under consideration, not implemented or verified yet. Keep
production ARM64 identity/signing and immutable checkpoint fallbacks unchanged.
Handset/SAF/Binder/UID/media/PDF/process-death/16 KiB acceptance remains unverified.

## Android worker runtime laboratory under development

Source advances to **0.6.0-pre-alpha.9 / code 15** for a separate test-only
`org.diemobile.runtimecheck` x86_64 module. Production remains ARM64
`org.diemobile` with the existing signing configuration. The laboratory copies
exact production worker/client/bridge sources, builds real hash/archive JNI,
and retains original notices without copying the large signature database.
The archive build scripts now isolate x86 codec prefixes, objects and JNI
outputs from their existing ARM64/host defaults.

Five actual Android instrumented contracts and API 26/35 KVM emulator jobs are
implemented. Static APK/source/manifest/version/signature/notice checks,
local test-APK assembly, required generic/native checks, 25 archive captures/37
payloads, 170 Android host tests, ARM64 assembly/lint and fifteen-library APK
verification pass. Actual emulator jobs and the five-job .9 source CI gate
are pending; do not mark runtime verification complete before
inspecting their evidence. See [reproduction and limits](../runtime-worker/README.md).

Next: run and correct the emulator matrix, inspect the captured native/worker
reports and retain exact source identities; run required host/native/ARM64/lint
and existing five-job CI gates; commit working slices and publish only verified
checkpoints. Then continue origin inheritance, media presentation and parity.
The environment has no local KVM/device, so local execution cannot substitute
for CI evidence. Earlier published/frozen APKs remain unchanged.

Gallery PR #10 merged to main `3ea7529b92a8959eeb4c68e54f9ac78534adeaef`
after all five jobs in `44724357792` passed (native dependency HTTP failure
passed on retry). Publisher `44729105874` published pre-alpha.8 from exact CI
source `e32161f8b8ff97deb0c6316bf3432f9b14a56e56`, code 14. Public APK SHA-256
`669e60fe3ba312126b73188337881a517f241a3aebf8fa50d3b155eb2d761ea3`.
The original local .8 frozen APK and preceding public checkpoints are retained.

## Worker runtime verified; gallery media preview slice

Original five contracts passed on actual x86 Android API 26 and 35 in runtime
run `44729667760` at exact source `26ad7d6a2b5f0e7f676d4f8373b6dcf9845eebba`.
Downloaded complete evidence, APK/shared-source hashes and artifact digests were
independently checked. Normal app UIDs were 10077/10209; first isolated worker
UID was 99000. Both read all 16,777,233 bytes, decoded the embedded gzip, refused
the client alias, survived wrong-token cancellation, disconnected on the correct
token with a 65,536-byte full report pipe, and recovered with a fresh client.
See [retained verification summary](verification/android-worker-runtime-26ad7d6.json).
The local clean .9 APK at that source is frozen with SHA-256
`8eb18dcb055d1a867a45510b7e99e14a236130b5403ab9941ad113203571027e`.
Its source CI release gate is still pending the build job; the other four passed.

Current source advances to **0.6.0-pre-alpha.10 / code 16**. `MediaFrame` adds
source-checked exact-range platform metadata/scaled video frames to bounded
Gallerize audio/video cards, preserves null/raw/errors and keeps observations
separate from native finding bounds. API 26 has no unbounded frame fallback.
All 175 Android host tests, final frame-budget guard rerun, required generic/
native checks, ARM64 assembly/lint/fifteen-library APK checks and runtime APK
compilation/static verification pass. Source CI is pending.
A sixth instrumented case now uses generated green MPEG-4/PCM clips to verify
actual Android codecs and exact prefix/trailer intervals; execution is pending.
The runtime workflow now also runs on main and pull requests, preserving its
separate scope from the existing five-job production gate. See
[media preview contracts](gallery-media-previews.md).

Next: inspect/correct the new Android codec evidence and full host/ARM64 gates;
commit and publish only passing checkpoints, preserving earlier APKs. Continue
origin inheritance, wider preview support and desktop parity after these gates.

The first .10 runtime attempt (`44732427853`) stopped before building/running
tests because the pinned host did not supply FFmpeg. The 991-byte owned
generated video is now retained with a checked SHA-256 and optional exact
generation recipe; Python generates PCM. An empty-PATH fixture test proves
o external codec is needed and edited canonical bytes refuse before output.
The complete clean 175-case/ARM64/lint/APK gate at `2662f86` passed; its local
.10 checkpoint is frozen with SHA-256
`a26e251e8ec60fb4cc9a8d911743f81123da2260f621f67e9c67c2c9637f76a2`.
The follow-up changes test assets/setup only; actual new codec execution remains
pending until the next emulator jobs pass.

## Gallery checkpoint published; captured APK community context

PR #11 merged to `d17476d30979eaab04a82c62b00537977a0eb48b`; all five
jobs in `44731816686` passed after the host platform pin. Pre-alpha.9 is public
from exact CI source `5d4bafb16310b0ba5a61c42a9a657cb7c053d7ec`; publisher
`44733302689` succeeded. Public APK SHA-256
`65fe04b9cab27ddc762de39cd231c953d1ed78f0272830d9b2f84580b10a2c25`.
Its downloaded complete checkpoint, matching public digests and exact-source
APK verifier were independently checked.

Gallery PR #12 merged to `965d43a28ea6d80d028a8e81b3cc102b96e6fdf9`.
All five source jobs passed in PR run `44733375708` and exact push run
`44733208727`. Six actual emulator contracts passed on API 26/35 in
`44733208678` at `8f2ab38af319a02c85618a0f398d654c1b48c15a`; downloaded
reports, APK/shared-source hashes and artifact digests were independently
checked. API 35 decoded a 24×18 frame with center `ff018001`; both APIs reported
1000/500 ms video/audio durations, and API 26 kept its explicit frame limit.
[Retained evidence](verification/android-worker-runtime-8f2ab38.json).

Publisher `44735638728` correctly rejected the PR capture's tree differing
from its reported head by an inherited documentation file. No guard was weakened.
Publisher `44736203614` succeeded using exact push run `44733208727` instead:
pre-alpha.10 is public from `8f2ab38`, code 16, APK SHA-256
`ca32f1fef93a9cdfefe885c5b6a0c08ac306cf2a8490b95dccd2b5f3168161f9`.
Public .10 checkpoint download/independent inventory/static verification is the
next publication audit. Local clean .10 at `2662f86` remains frozen with SHA-256
`a26e251e8ec60fb4cc9a8d911743f81123da2260f621f67e9c67c2c9637f76a2`.
Only verified duplicate outer download wrappers .7–.9 were reclaimed; every
original inner APK/checkpoint is retained. Audit is outside the repo at
`/workspace/work/reclaimed-checkpoint-wrappers-7-through-9.json`.

Current branch `codex/apk-community-context` advances to **pre-alpha.11 / code
17**. Selected-APK and per-source handoffs now retain a strict independently
owned 1 MiB `PackageKnowledgeSubject`, original literal attribute and complete
input/parser/signer/error identities. Community provenance/full exports include
that observation while preserving all duplicates and raw source tokens. Failed
restores preserve existing owners. Main's direct route captures the selected
input first; automatic handoff is attempted once per parent state. Three new
actual AAPT2/signing/widget flows pass, with 18 focused cases total. Standalone
Java/Python ownership/strict-schema/budget/cancellation/path/all-UTF-16/raw-number
oracles pass, alongside the complete real knowledge-source oracle.

The full 178-case host/ARM64/lint/APK gate is running. A seventh instrumented
case using actual `resources` worker/Binder/PFD parsing and unchanged
`PackageManifest` is implemented; local test-APK compile and actual API 26/35
execution remain pending. See [association contracts](apk-community-context.md).
Next: inspect/correct full gates, incorporate current main without losing WIP,
compile/test the lab, commit coherent sources, run both CI workflows and publish
passing immutable checkpoints. Keep observed exact-ID association separate from
behavior/publisher/device/version/removal conclusions and earlier origin gaps.

The full .11 local gate passed: **178 tests, zero failures/errors/skips**,
required generic/native checks, ARM64 assembly/lint and fifteen-library APK
verification. A follow-up actual-widget assertion verifies parent recreation
does not repeat its automatic handoff; all seven package-set cases pass.
The seven-case runtime target/instrumentation APKs compile and pass static
source/manifest/version/signature/native/notice checks. Actual seventh-case
Android execution remains pending. Current main `965d43a` was fast-forwarded
into this branch without losing WIP, retaining its inherited CI-platform doc
and keeping future PR trees aligned. Commit/push and inspect both workflows
next, then audit/download/publish passing checkpoints.


## Actual recovered-file gallery — pre-alpha.12

PR #13 was merged after all five source checks passed. Source `85f28c5` passed
all seven actual x86 Android API 26/35 lab contracts (run 44738875535), including
installed-target compiled manifest parsing. Main `faa3272` also passes both
workflows. The .11 publisher run 44768862555 succeeded. The renamed repository
has the same verified ID; existing owner URL redirects. No production identity
or signing migration occurred.

The user redirected development to **actual PhotoRec/Binwalk files and their
gallery/export flows**, explicitly ahead of more File info/header-location or
system/package knowledge work. New gallery sessions now scan both engines over
one owned input. Duplicate file cards retain both original witnesses; unknown
and alternative boundaries remain distinct in evidence. Binwalk failures or
its >64 MiB input scope leave PhotoRec files available, with explicit status.
Legacy retained captures never silently acquire new scans.

Default Files, direct previews/archive browsing, per-tile typed saves and a
real-payload ZIP across all selected-group pages are implemented. The preview's
own save and native finding saves preserve extension/MIME. Complete evidence
and grid exports remain under Tools; long-press retains all existing actions.
Native/Java/Python checks cover exact JPEG/PNG/WAV/GIF/gzip/MP4/text payloads,
duplicates, unknown/conflicting extents, per-engine failures, changed captures
and independent source/picker lifetime. Eighteen focused UI/media tests pass,
including 29 images and their nested compressed payloads over multiple pages.

Next: complete required generic/native/80 MiB/full-host/ARM64/lint gates, correct
any failures, freeze the numbered working APK, commit, inspect five-job source
CI, merge and publish an immutable passing checkpoint. Keep host, actual x86
runtime and production ARM64/device/SAF claims separate. Then continue actual
file render/recovery coverage (including PDF/other common formats and archive
member gallery workflows), rather than File info work. See
[recovered-file contracts](recovered-file-gallery.md).

The complete .12 local gate passed: **180 host tests, no failures/errors/skips**;
required generic/native checks; original unknown-header/archive/manual-range
regressions; actual seven-payload Java/Python native fusion and ZIP checks; the
fully written 80 MiB tail/PNG/grid case now also checks correct PNG bytes/name/
MIME on export. ARM64 assembly/lint and APK signature/offline/identity/notices/
fifteen-library alignment verification pass. Commit clean source, rebuild/freeze
its exact numbered APK, push and inspect source CI before merge/publication.


## Recovery gallery published; actual PDF file checks

Source 123193d passed all five source CI jobs (44776359039) and seven x86 Android
runtime lab contracts (44776359034). PR #14's five source jobs passed
(44776456230), and it merged as **0493c5b**. Publisher **44778247741** succeeded
for immutable **v0.6.0-pre-alpha.12**. Its clean local checkpoint APK SHA-256 is
`ff3ee4cf4be8adbf31a8bbcb0d02e7df4a7cd18996e006e426ad70682bd75674`
(52,803,429 bytes, source 123193d). Public CI-signed APK bytes are separate.

New branch `codex/pdf-file-recovery` incorporates main 0493c5b and advances to
**pre-alpha.13 / code 19**. Adapter 4 enables the original PDF module's file-check
only, via a bounded read-only source-relative FILE view. Successful source-
bounded results become actual PDF gallery/save/preview payloads. Other full
file checks and all renames remain disabled. Java validates the explicit new
scope/counters before publishing an extent. Original adapter 2/3 captures remain
readable under their own contracts.

Actual embedded PDF bytes/xref/native boundary/export, truncation/10 MiB
footerless work cap and altered callback claims pass Java/Python checks. Native
read/seek/EOF/write-denial/budget/exception/FD-ownership cases pass. Original
PhotoRec 80/256 MiB and known media regressions pass; focused gallery/recovery/
PhotoRec Android host cases pass, including the new actual PDF handoff/save.
The new stdio buffer is 64 KiB: maximum input-read payload buffers become
**1.3125 MiB**, excluding callback scratch and total process memory.

Next: finish full .13 host/native/ARM64/lint gates and correct failures, bundle
matching sources, freeze clean-source APK, commit/push and inspect CI before
merge/publication. Do not claim actual Bionic FILE or PDF renderer execution.
Continue file coverage: separate optional PDF date-reading work from native
footer recovery for larger valid PDFs, then broaden reviewed common-format
file checks and recovered archive-member gallery/export flows. Keep actual
files as the priority. See [PDF contracts](pdf-file-recovery.md).


The complete .13 local functional gate passed: **181 host tests with zero
failures/errors/skips**, existing 80/256 MiB native PhotoRec cases, actual
seven-payload combined gallery oracles, new PDF byte/xref/callback-scope/C stdio
guards, required generic/native checks, full ARM64 compilation/lint and APK
verification. The source lock now names adapter 4, pins the reviewed `file_pdf.c`
and declares correct buffer/callback policies. Notices and matching standalone
corresponding sources are regenerated and checked.

An eighth lab contract now executes actual PhotoRec/Bionic PDF recovery and
missing-footer refusal via the production isolated service. It packages only
the additional source-built x86 PhotoRec library and notices, plus a trusted
fixture factory in instrumentation. APKs compile/static-verify, including exact
NDK strip equivalence and native source/fixture hashes; real API 26/35 execution
remains pending. Runtime-only `-g0` avoids unused DWARF; production flags are
unchanged.

Workspace disk filled during a first runtime build. Only regenerable compiler
intermediates were reclaimed after verifying original checkpoint APKs; no
pinned source/toolchain/immutable APK was removed. Current local packaging reuses
already fully compiled and verified **unchanged CDiE/hash** ARM64 libraries,
with native input/library hashes in `/workspace/work/pdf-unchanged-native-reuse.json`,
using `-x ':app:buildCMakeDebug[arm64-v8a][diehash,diemobile]'`. PhotoRec is built
normally. Clean CI retains normal full native builds. Root main/native source
and production compiler settings were not changed to work around storage.

Next: commit clean source, rebuild/freeze exact numbered APK with the same
verified native-cache reuse, compile/static-check clean runtime APKs, push and
inspect both CI workflows. Fix failures before merge/publication. Public .12
APK observed GitHub digest is
`2f3b010c12e7d7024f497615e6328b321e3a4e191a93db180614a6c236444f5b`
(52,819,813 bytes); its independent download audit remains pending. Keep that
separate from the retained local .12 signer/hash above.


Source **531dff4** is committed/pushed in PR #15. Its local clean APK SHA-256 is
`c10afe676f42a94e93e408649f36b1ed0739ee4fea5e2ed662254243fdc5a175`
(52,803,465 bytes), with packaged adapter-4/validator presence checked. Push
source CI 44784016413 hit a transient JSON dependency HTTP 404 after Rust tests;
PR binary integration passed. A rerun is deferred until the overall push run
completes; the reporting fix below creates a fresh source run.

Both Android jobs in **44784016230** completed all eight assertions and wrote
successful complete reports, but Instrumentation.finish failed when its copied
pretty report exceeded Binder. Downloaded API 26 artifact 14653906176 (SHA-256
`02994a675111b189a9c9a91e3ae291f4d68b2d5edc5256bbac486d9692a16752`)
independently confirms all eight cases/source531dff4 and the actual failure:
`TransactionTooLargeException`, 1,777,520-byte result parcel. Native PDF recovery
was successful; the overall workflow is correctly failed. The harness now
keeps the entire report on disk and returns only count/size/SHA-256/status/path
through Binder. The runner checks that completion identity against the captured
full file without weakening any success/source/UID/SDK/check assertions. Commit,
compile/static-check and rerun both workflows before publication.

The downloaded .12 CI checkpoint 14650283390 (outer SHA
`342f3cca1a88d7e40571912e8bf97c650df00fe1c55912210935e46c281601f1`)
now passes independent original six-file inventory/checksums/public digests,
source123193d identity and exact-source APK static verification. Original CI APK
and manifest are retained separately from locally generated audit sidecars in
`/workspace/work/releases/0.6.0-pre-alpha.12`. Its public SHA above is confirmed
against the actual downloaded APK bytes; component-source payloads remain
publisher-gated rather than newly independently downloaded here.


## PDF checkpoint published; retain larger recovered PDFs

Source **431f98d** passed source five-job CI **44786668067** and PR CI
**44786680158**, plus eight actual x86 Android API 26/35 contracts in
**44786667682**. The completion fix passes without weakening report/source/
UID/SDK assertions. Both downloaded artifacts pass independent source, native
adapter/fixture, APK/library and completion/file SHA/count/size checks. API 26
artifact 14653733902 has SHA
`f05c20a97db5d0570b0414e43522a9b2073d77a0f82fb8d1d771395f297d2eae`;
API 35 artifact 14655266595 has SHA
`7a29058b0b6d0377ed1c4485803b09331705227e45f2331a25ce2f73787e5491`.
The repo summary is `docs/verification/android-worker-runtime-431f98d.json`.

PR #15 merged as **d03f90dfa900a6fd498b12c7b43d28ecb52d93c3**. Publisher
**44788478653** succeeded for immutable **v0.6.0-pre-alpha.13**. Local final
source431f98d APK is 52,803,469 bytes, SHA
`8be8d1d124e0706898db4b7d24946119441d851294fce9fb5e8e11684777221f`
in `/workspace/work/checkpoints/0.6.0-pre-alpha.13-local-final`; initial source
531dff4 and every earlier checkpoint are retained. Root origin now uses the
owner's canonical GlassDigestor URL. Release audit worktree is clean at source
123193d for the completed .12 audit.

New branch `codex/pdf-footer-budgets` is based on main d03f90d, version
**pre-alpha.14/code20**. Adapter 5 preserves a positive original PDF footer size
when only optional post-footer newline/date reads hit a classified work limit.
Missing footer, source IO and deadline errors stay unresolved. Scope is based
on reviewed/pinned file_pdf.c + filegen.c behavior; no boundary comes from a
next header/EOF. Separate native boundary/partial-metadata flags, counters,
error/work/raw-time fields and strict Java/legacy-scope validation remain.
The input/read/aggregate caps are unchanged.

Actual >9 MiB PDF gallery/file export and independent Python exact-byte/xref
checks pass, including invalid error/flag/count/legacy refusals. Full native
PhotoRec 80/256 MiB and C stdio guard regressions pass. The existing Android
host gallery case now uses >9 MiB, and the eight-case actual Android lab adds a
large PDF with byte comparisons. Full host/ARM64/runtime compile/CI gates are
pending. Next finish those gates, correct failures, freeze/commit/push and
publish only passing checkpoints. Continue actual file galleries/previews/
exports over File info work. Actual PDF rendering and other common-format
recovery coverage remain useful following priorities.


The .14 local gate is now passing: all **181 host cases** (the existing PDF
viewer/save handoff case now uses >9 MiB), required generic/native checks,
original 80/256 MiB PhotoRec/C stdio guards, embedded archive/manual-range and
combined media payload/ZIP regressions, ARM64 PhotoRec build plus app assembly/
lint/APK verification, matching corresponding-source bundle and x86 runtime
APK compilation/static identity/strip verification. The extended large-PDF
Bionic case has not yet run on Android. Keep that acceptance claim pending.
Local packaging reuses the verified unchanged CDiE/hash libraries using the
previous recorded task exclusions; PhotoRec builds normally.

A separate publisher regression was found: its release title variable was
reused for asset names, making the final title RELEASE-SHA256SUMS. Commit
**d2e901e** separates release/asset names and tests resumed-draft publication
through actual update payloads; all seven publisher contract tests pass. The
published .13 title/notes were corrected through an identity-complete metadata
PATCH, with all 14 asset IDs/names/sizes/digests and tag/source/channel verified
unchanged. Preserve this publisher fix when merging .14, then dispatch the
publisher from updated main so the fix is used.

Next commit the coherent .14 file-recovery change, rebuild/freeze the exact
clean-source APK/runtime artifacts, push, run both CI workflows and fix failures.
Merge only after passing gates; publish the immutable .14 checkpoint afterward
from the corrected publisher. Actual files/preview/exports remain the priority.


## Published pre-alpha.14; archive member files and store preparation

Source **8adac7bd2a8ed81caabf7d919b751859dc804590** passed both five-job source
CI runs **44915200962 / 44915262364** and eight actual x86 API 26/35 contracts
in **44915200949**. Complete downloaded reports/source/APK/library/completion
hashes/counts are checked in `docs/verification/android-worker-runtime-8adac7b.json`.
Both SDKs recover and compare the exact 9,437,636-byte PDF, SHA-256
`0c37bd4faef8269249dfd6dba12d3aa6cfd4bb1f71dfa5a36b0e947a8b01de9c`.
Optional metadata-work exhaustion remains explicit; no PDF rendering claim.

PR #16 merged as **e2b8bf30926a5a904a8184253d9a47c024feeb70**. Publisher
**44916538888** succeeded from updated main, including the display-title fix.
All 14 public asset identities and the correct title/source/tag/channel were
checked. Public `.14` APK: **52,836,245 bytes**, SHA-256
`8f14ca18938bb07fbba6d679d72827a73c3cc37d8e14ab9a4137779e23fe15a9`.
The downloaded original six-file CI checkpoint matches public hashes; exact-
source static APK verification passes in the separate clean release-source
worktree at 8adac7b. Original CI files remain in
`/workspace/work/releases/0.6.0-pre-alpha.14/ci`; only the verified redundant
outer ZIP was reclaimed. All frozen local/earlier APKs remain intact.

Current branch **codex/archive-file-gallery**, pre-alpha.15/code21, adds a
24-card native archive gallery, checked image/media/text observations, direct
preview, typed saves and complete scoped actual-file ZIPs. `ArchiveListing`
shares tree/gallery/export selection; `ArchiveFiles` handles safe names and
checked payload/report staging independently of UI. Original indices/names,
duplicate and unusual entries, excluded links and current native/finding-chain
evidence remain explicit. No automatic recursive decompression or all-codec
claim. See `docs/archive-file-gallery.md`.

All **187 host tests** (six new archive cases) and required `scripts/test.sh` /
`scripts/test-native.sh` pass. The initial whole-suite local attempts exhausted
disk and one Robolectric Looper stalled after that failure; its stack/logs are
retained and that attempt is not acceptance evidence. The successful complete
rerun uses a local Gradle init script setting `forkEvery = 1` and
`maxParallelForks = 1`, so every class runs and temporary engine copies are
removed at JVM exit. Log: `/workspace/work/archive-gallery-full-ui-forked.log`;
summary: `/workspace/work/archive-gallery-host-test-summary.json`. No tests
were skipped or assertions weakened. CI retains its ordinary full-suite mode.

Disk remains constrained. Regenerable PhotoRec Android object directories and
unchanged host xxfclib compiler objects were reclaimed with library/fallback
SHA audits; compiled JNI libraries remain. Do not run host native rebuilds while
the test JVM is active. For local app packaging, verify the unchanged source/
ARM64 library audit and reuse the documented CDiE/hash task exclusions; other
engines keep their normal build tasks. Clean CI rebuilds everything.

The user also requested GitHub-page polish after the UI and F-Droid guidance.
README now prioritizes a concrete file workflow, installation and one precise
published verification baseline, removing contradictory old counts/branches.
Fastlane descriptions and the identical licensed icon are present.
`docs/fdroid.md` cites current official guidance and audits actual blockers: the
nine MPV/FFmpeg runtime libraries are still copied from upstream binaries;
complete source rebuilding, release-variant acceptance, dependency/recipe and
signing/reproducibility work remain. No F-Droid submission, external repo or
private signing identity has been created. Genuine Android screenshots remain
a following presentation task.

Next: commit this coherent source/docs slice, compile/lint/check/freeze its
exact clean ARM64 APK, push and run source/Android CI; fix failures before
merging/publication. Continue actual file viewing/export/recovery coverage.


The first .15 slice is committed as **9c077e67cac0684a11069c45afb37a036772621d**
in PR #17. Its clean ARM64 assembly/lint/static APK and freeze pass; local
52,803,473-byte APK SHA-256 is
`df56f56cf2ad512335fe7590628627ec2abe2c3fee0c226250189ca0b8e4743f`
in `/workspace/work/checkpoints/0.6.0-pre-alpha.15-local`. Source CI
44939977631 and actual API 26/35 run 44939977547 are for that source; the latter
passed. Do not publish that source as the final .15 after the correction below.

A new compact-phone case caught a real presentation failure: only 4 px remained
for the archive gallery at 360×640. Secondary actions are now in Tools / evidence,
the full status opens on tap/Status / limits, and source/folder headings/status
are compact. The fixed case requires ≥200 px and all existing tool buttons; all
**15 focused old/new archive cases pass**. A complete **188-case** run is in
progress using the same one-class-per-JVM init script. Log:
`/workspace/work/archive-gallery-full-ui-layout.log`. This is an additional
functional usability gate, not a weakened assertion. Commit/push the correction
and rebuild/freeze the new clean source, preserving the earlier frozen .15.

The duplicated mutable .15 APK output and generated x86 archive static-dependency
cache were reclaimed after APK/JNI SHA audits, keeping both compiled archive
JNI libraries and every frozen APK. The test-only runtime module will need that
static dependency cache regenerated if rebuilt locally; CI does its normal
source build. Final app ARM64 packaging keeps the previously documented
unchanged CDiE/hash task exclusions and normal engine tasks.


## Published pre-alpha.15; next source-build and file-viewing work

Final source **2dcfce358c6d82a0a76116a5c3142f3ebb3b7227** passed all **188**
host tests, both five-job source CI runs **44940694548 / 44940700294** and
eight selected actual x86 API 26/35 contracts in **44940694581** (PR runtime
44940700258 also green). Downloaded full report/source/APK/library/completion
identities pass; summary is `docs/verification/android-worker-runtime-2dcfce3.json`.
The new seven archive gallery cases are host/widget checks; actual ARM64/SAF/
gallery/codec/PDF rendering remains unverified.

PR #17 merged as **d7a31d14525ddb286957ec7374678f0576f1ead1**. Publisher
**44941636216** succeeded for immutable **v0.6.0-pre-alpha.15**. Public APK:
**52,836,245 bytes**, SHA-256
`e96547fc2b7349048c5cb02d5fe7477004cdf6eddeac9b8231adb2dd5a16150e`.
All original six checkpoint files match public asset hashes; exact-source APK
static checks pass in the clean release-source worktree at 2dcfce3. Original
CI files stay at `/workspace/work/releases/0.6.0-pre-alpha.15/ci`; its checked
outer ZIP wrapper alone was reclaimed. All 14 public asset identities/title/
source/tag/channel are verified; component-source payloads were publisher-gated.

Final clean local .15 is frozen in
`/workspace/work/checkpoints/0.6.0-pre-alpha.15-local-phone-layout`: 52,803,473
bytes, SHA-256 `3efca78d275db01b642a23a9267a731a70e5f204d96c7e8e4f3a25f2b32a1230`.
The earlier 9c077e6 local freeze and every older checkpoint remain. Source/
ARM64-library reuse hashes were checked; clean source CI rebuilt all engines.
The final full local suite used the external one-class-per-JVM init script;
log `/workspace/work/archive-gallery-full-ui-layout.log`, summary
`/workspace/work/archive-gallery-final-host-test-summary.json`.

The user also asked whether all GitHub repositories are accessible. The plugin
listed 14 owned repositories, including private ones, with push/admin repository
permissions; no other repository was modified. Effective token scopes are
narrower for settings: the optional About-description PATCH returned HTTP 403
(Resource not accessible by integration). README/code/PR/release work succeeded.
No alternate account or permission escalation was attempted for that cosmetic
field. README, published build and F-Droid preparation remain available.

F-Droid now has a concrete next reference: its existing is.xyz.mpv recipe for
2026-09-17/code48 uses our exact upstream recipe/MPV/FFmpeg pins and NDK r30.
See `docs/fdroid.md` and the local observed-reference JSON at
`/workspace/work/fdroid-matching-mpv-recipe-reference.json`. Adapt and test its
source-library preparation/runtime build, add a reviewed source-build receipt
path without weakening existing official-binary hashes, and run the media
reference/ownership/budget/platform/packaging checks. Build/test a real release
variant and complete the F-Droid recipe/dependency/signing review before any
submission. No engine is disabled for store eligibility, and no store request
or signing identity change has been made.

Continue actual file work: platform PDF/image viewing proof and suitable
thumbnails, remaining common PhotoRec file checks, and eventually a bounded
single-pass native batch extraction so large archive galleries/bundles avoid
repeated full-directory reads. Manual nested member galleries now work; one
automatically recursively recovered member map remains incomplete. Genuine
Android screenshots can follow the layout work; do not present generated/mock
images as handset runtime proof.

Local root disk has roughly 113 MiB free after preserving the final CI APK.
Complete MPV sources/NDK r30 builds need a larger environment or a reviewed CI
source-build job. Keep frozen APKs/pinned sources/toolchains; reclaim only
verified duplicate wrappers or regenerable compiler caches. The generated x86
archive dependency cache must be regenerated for a new local lab build; compiled
JNI libraries remain. Do not mix native recompilation with a loaded test JVM.


## Active PDF page viewing and thumbnail slice

Starting main **836f463** has green five-job CI **44942054750** and eight-case
API 26/35 runtime **44942054708**. Branch **codex/pdf-page-previews** is now
pre-alpha.16/code22. No production native source, dependency, application ID,
signing or permissions change. Prior frozen APKs remain intact.

`PdfPagePreview` provides source-checked, read-only-descriptor page rendering,
logical point/raster pixel geometry, bounded output and private embedded-range
staging. Gallerize and native archive PDFs get thumbnails, and the full viewer
uses the same helper. Fit/Actual pixels reuses a bitmap without reparsing;
two-axis pan, page/view/position recreation and stale navigation guards are
implemented. Full source identity expands from two display lines. Reports are
transient and do not relabel original native findings or prove full validity.

Six new renderer-shadow/model/widget cases and one actual-libarchive PDF case
pass; existing gallery/recovery cases pass. The first full **195-case** suite
passes before the final Actual pixels extension; the final full suite is now
running in `/workspace/work/pdf-page-full-ui-final.log`. Required generic/native
and original PhotoRec >9 MiB PDF native/Python regressions pass. Logs:
`pdf-page-generic.log`, `pdf-page-required-native.log`, `pdf-page-native-pdf.log`.
Independent original-440-byte/two-page xref/color checks are retained in
`/workspace/work/pdf-page-oracle/verification.json`; colored PDF is 692 bytes,
SHA-256 `f3961ece0dc19dfc3cbe2250a819f677721ae3184799cbc1237f576f799756f1`.

A combined focused run stalled in Robolectric's animation clock while setting
up a later Gallerize case (not the real PDF engine). Its JVM was stopped after
capturing `/workspace/work/pdf-page-pixel-view-stack.txt`; it is not acceptance
evidence. App host-test configuration now uses serial `forkEvery = 1` JVMs,
matching the previously successful local full-suite isolation. All assertions
and real native backends remain; production builds/actual Android execution are
unaffected. The 28-case focused rerun passes, including Actual pixels, source
expansion and view/scroll restoration. No source edit occurs during compilation.

The ninth Android contract `exact-recovered-platform-pdf-pages` and runner's
exact nine-name/count gate are implemented. It performs actual native recovery
then real PdfRenderer calls for independent red/green vectors, target sizes,
source/UID/range/bytes and cleanup. Instrumentation Java compilation passes;
actual Android execution remains pending. The original eight contracts remain.
The shared helper/ownership/export classes are added to exact source staging
and static verification; notices and three native libraries are unchanged.

To fit disk, only generated runtime merge/strip caches and host archive static
dependency cache were reclaimed, after fallback/JNI SHA checks. Receipt:
`/workspace/work/reclaimed-preview-compiler-and-merge-caches.json`. Required
native tests rebuilt host xxfclib; its object cache was reclaimed afterward
with working JNI preserved (`reclaimed-host-objects-after-pdf-page-native.json`).
Do not rebuild/overwrite native libraries while a host test JVM has them loaded.

Next finish the final 195-case suite, correct failures, compile/lint/static-
check ARM64, assemble/static-check the lab from clean committed source, freeze
unique .16 checkpoints, push/PR and run both CI workflows. Publish only after
all five source jobs and all nine actual API 26/35 contracts pass, preserving
exact source/asset guards and immutable old releases. Use the existing audited
unchanged CDiE/hash native task exclusions for local packaging if needed; clean
CI continues full native builds. Keep actual renderer/host/ARM64/handset scopes
separate in the parity checklist and release notes.

The final local 195-case suite passed with zero failures/errors/skips; clean
ARM64 assembly/lint/static APK verification and fresh runtime APK/static checks
also pass. The immutable local .16 checkpoint is under
`/workspace/work/checkpoints/0.6.0-pre-alpha.16-local`, source `db132b2`, APK
SHA-256 `3f7f54176e4811d5e191a084e452285d66c44ca66be77d8194382f1ab9a02a47`.
All nine actual x86 API 26/35 contracts pass in push run **44966758145** and PR
run **44966805137**. Independently downloaded report/APK/source/library hashes,
exact nine names/completion counts and red/green PDF geometry/pixels pass the
audit at `/workspace/work/runtime-evidence/db132b2/verification.json`.

Source CI **44966758141** passed native/binary/media/signature jobs but its host
suite failed during Robolectric SDK artifact acquisition (195 setup failures,
`MavenArtifactFetcher` / `HttpURLConnection`). The short default log omitted
the HTTP cause. Full exception logging and failure-only XML/HTML artifacts are
now enabled before retrying; no assertion, dependency check or release gate is
removed. Publication remains blocked on a successful exact-source five-job run.

Host Android SDK test images are now explicit pinned build inputs rather than
downloads inside each Robolectric JVM. `scripts/robolectric-sdk.sha256` records
the exact API 26/28/35 instrumentation-i7 images used by Robolectric 4.14.1;
their full SHA-512 identities were independently compared with Maven Central
before recording SHA-256 pins. `prepareRobolectricSdks` verifies every cached
image, and test JVMs use Robolectric's offline dependency directory. The default
cache is `.ci-cache/robolectric`; `ROBOLECTRIC_SDK_DIR` overrides it and
`DIE_OFFLINE=true` refuses a missing image without network access. CI acquires
these inputs before native compilation so failures are immediate and explicit.
Images are build-only dependencies and are not packaged in the app.

The shared atomic fetch helper retains its 16 MiB default and permits an
explicit checked limit up to 256 MiB; SDK acquisition requests 200 MiB per image.
Downloaded and reused file sizes are checked as well as SHA-256. Added fetch
fixtures cover custom limits, oversize cache/download rejection, cleanup and
invalid limits; original no-network/edited-cache/partial-download cases remain.
Local verified SDK bytes are hard-linked into the cache to avoid duplicate
storage. Focused API 26/28 renderer/media cases pass with in-test downloading
disabled; the complete 195-case rerun is recorded separately.

## Published pre-alpha.16; pinned offline host test images

PR #19 merged as **d76307c**, source **04e93c0**, code **22**. All 195 host
cases and required generic/native/ARM64/lint/static APK checks pass. Push source
CI **44967525630** and PR source CI **44967529622** pass every job. All nine
actual x86 API 26/35 contracts pass in **44967525584 / 44967529605**. Downloaded
source/APK/native-library/full-report/completion identities and exact known
red/green PDF pixels, page/point/raster geometry and parent coordinates are
independently checked; [evidence](verification/android-worker-runtime-04e93c0.json).
Audit scripts/artifacts remain under `/workspace/work/runtime-evidence/04e93c0`.

Publisher **44968611004** succeeded. [Pre-alpha.16](releases/0.6.0-pre-alpha.16.md)
is public with all fourteen assets; the downloaded original six-file checkpoint
matches its inventory and public asset digests. Exact-source APK static checks
pass in `/workspace/release-source` at 04e93c0. The original CI files and separate
hard-linked static audit are under `/workspace/work/releases/0.6.0-pre-alpha.16`.
APK: **52,852,629 bytes**, SHA-256
`e8b1d6a3f49c8540bad82a95325597df7f04c8e335614c2ee3e8240661e82b26`.
Corresponding-source archives were publisher-verified, not all downloaded again.
The verified outer checkpoint ZIP was reclaimed after all original files were
retained. All earlier frozen APKs remain unchanged.

The separate host SDK follow-up passes all **195** cases with Robolectric
network acquisition disabled, plus the bounded-fetch regressions. The first
rerun had six batch setup failures with only 71 MiB free. Reclaiming 199,811,188
bytes of regenerable app JNI merge/strip copies, while hash-checking retained
compiled libraries and APKs, restored all thirteen batch cases unchanged and
then the full 195-case suite. No budget/assertion was relaxed. Logs:
`pdf-page-offline-sdk-all-host-final.log`, `pdf-page-offline-sdk-host-summary.json`,
`pdf-page-restored-space-batch.log`; reclaim receipt:
`/workspace/work/reclaimed-app-merge-after-pdf16.json`. Keep at least 250 MiB
free for full host fixtures; native recompilation/assembly needs more. The
pinned SDK cache hard-links previously verified jars and does not duplicate
400 MiB locally. New-source CI for this follow-up remains the next gate.

Continue with actual recovered files: audit additional PhotoRec file-check
modules against read-only/bounded descriptor semantics before enabling them;
retain unresolved extents when no native boundary is established. Improve
viewing of currently unsupported media through the pinned MPV engine, keeping
native findings, exact file bytes and transient render observations distinct.
The MPV source-build/F-Droid blocker and matching upstream recipe remain in
[fdroid.md](fdroid.md). Preserve offline operation, licensing and explicit
host/x86-helper/production-ARM64/SAF verification scopes.

After the offline SDK change, required `scripts/test.sh`, a fresh
`scripts/test-native.sh` rebuild/regression run, bounded dependency acquisition
fixtures and all seven publication guard regressions also pass. No inspected
application was executed. The release APK's exact-source static audit passes;
current main preparation preserves that immutable checkpoint and all old APKs.

PR #20's first PR build confirmed HTTP **429** from Maven Central on every
bounded retry, before any native compilation. Source push 44969089853 passed
SDK acquisition on a different runner; this was a hosted-runner rate limit,
not an image hash change. SDK acquisition now tries the Maven Central Google
storage mirror, then canonical Central, with the **same** SHA-256 and 200 MiB
limit at both hosts. Edited caches and missing offline inputs fail before any
fallback/network request. Partial and wrong downloads are discarded.

New isolated shell fixtures verify mirror success, limited/corrupt mirror
fallback, both-source failures, unchanged verified reuse, edited-cache refusal
and missing-offline no-network behavior. A complete **106,130,412-byte** API 28
image was independently streamed from the mirror and matches the existing pin
`dbb1c88987b54cef3132695a1b4d2f4851ba06286a6fa7d15772f98fd1f0cdf7`;
receipt `/workspace/work/offline-sdk-mirror-stream-verification.json`. Both
fetch suites and offline verification of all three real cached images pass.
The immutable pre-alpha.16 release is unaffected; inspect the newest PR #20
source/runtime CI before accepting the build-only follow-up.


## Accepted offline build follow-up and next media integration

PR #20 merged as **1b0a44b**, accepted source **95e46b3**. Push source CI
**44969620792** and PR source CI **44969624052** pass all five jobs, including
all **195** host cases, full ARM64 assembly/lint/static APK and corresponding-
source checks. All **nine** actual x86 API 26/35 contracts pass again in
**44969620798 / 44969624033**. The merge tree equals accepted 95e46b3 exactly.
Subsequent main/doc builds are separate source identities; inspect their current
Actions status before the next functional change. No release asset was replaced:
pre-alpha.16 remains immutable source **04e93c0**, independently audited above.

Only regenerable compiler/static-library caches were reclaimed after the final
native rebuild/regressions. **119,180,107 bytes** were removed from
`app/build/host-native/xxfclib` and `app/build/archive-libraries-android`, while
all **40** checked compiled JNI/frozen APK hashes remain unchanged. Receipt:
`/workspace/work/reclaimed-compiler-caches-after-alpha16-release.json`.
Approximately 270 MiB free remains. Rebuilding those caches requires additional
space; keep every actual compiled library, pinned input and immutable APK.
Clean CI performs full builds without the local unchanged-native exclusions.

A next-feature feasibility experiment is retained outside production at
`/workspace/work/mpv-software-render-probe`. The actual external host libmpv
software renderer, existing bounded read-only stream callbacks and mandatory
reference/config/script/sidecar/network guards render authored **64×48 TIFF
and PPM** fixtures with exact red/green pixels and unchanged input hashes.
`verification.json` records source/header/library/fixture identities, dimensions,
pixels, byte counters and zero ungranted URIs. Its README and C source reproduce
the experiment. The pinned render header came from MPV 0b7ed670; its client
header matches the production header exactly. The original pinned MPV source
archive is retained in `/workspace/work/mpv-integration/sources/mpv.tar`.

This is **host feasibility only**, not an implemented APK fallback, Android
rendering acceptance or full playback. Before integration, replace the fixture-
color completion oracle with reviewed native present-frame semantics; retain
pixel/stride bounds and initialize opaque alpha (rgb0's fourth byte is undefined).
Use the production isolated worker and checked output descriptors, all-path
context/engine/resource cleanup, cancellation/stale-result ownership and exact
source/pixel/report identities. Verify software-render support in the exact
ARM64 runtime, add the pinned licensed header/source-bundle inputs, and run
native/widget/ARM64/CI checks. Do not copy the disposable prototype's early-exit
cleanup shortcuts into the app. Original native findings and actual file exports
must remain independent of transient rendered pixels. This is a concrete next
route to viewing more recovered media; MPV playback and F-Droid source-building
remain separate work.

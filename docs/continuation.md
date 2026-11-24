# Development continuation

Read [status](STATUS.md), [project map](PROJECT-MAP.md), [AGENTS.md](../AGENTS.md)
and required design/parity/workbench documents first. The checklist now contains
current capabilities and gaps; historical release stories are archived.

## Integrated and published baseline

Canonical repository: `sperglord8008s/GlassDigestor`. Main `a42b01b` merges PR #24.
Published **pre-alpha.20 / code 26**, exact source `691a4b1`, passes both five-job
source/PR gates (`45484857179` / `45484869594`), **241 host Android tests** and
**13 actual x86 API 26/35 contracts** (`45484857222` / `45484869326`). Combined
app/laboratory/instrumentation assembly and static APK checks pass. All 14 public
assets, six original CI files and 118 source-ZIP members passed independent
hash/source audits. [Release](releases/0.6.0-pre-alpha.20.md)
· [runtime receipt](verification/android-worker-runtime-691a4b1.json)
· [asset receipt](verification/pre-alpha20-release.json).

Preserve `org.diemobile`, signing configuration, immutable assets and fallback
APK bytes. Debug keys from CI runners can differ. Host checks, selected actual
x86 fixtures, production ARM64 and full handset/SAF/UI remain separate scopes.
Binwalk transport/MPV native Android execution are not established by the lab.

## Recovery state

Actual native archive acquisition → both FD engines → production Gallerize →
actual recovered-file ZIP scores **6/6 L0_Video reference payloads**, with a
duplicate exact AVI and all 79 additional unscored exports retained. All 28,890
PhotoRec occurrences/order/header acceptance remain. Native callback work is
47,972,352 bytes with no data-budget exhaustion; data/PDF/aggregate bounds are
64/8/256 MiB. The clean actual export receipt is source `832f6af`; release691
retains its exact production pipeline hashes. [Receipt](verification/nist-L0-video-native-export.json)
· [corpus and reproduction](../tests/fixtures/nist/README.md).
Only this one of 30 images is acquired/pinned/scored. Fragmented/missing-data
cases need separate reconstruction scoring; expected coordinates never guide
production discovery. Continue the remaining corpus after media stability.

## Active MPV still-image pass

The next pass is isolated in `/workspace/mpv-work`, branch
`codex/mpv-image-fallback`, PR #25, **pre-alpha.21 / code 27**. It restores the
archived frame foundation with SAR-aware allocation bounds, strict
node/raster/event guards and a separate raster-descriptor worker protocol.
Platform-first image/video/archive previews and explicit MPV stills retain
separate observations. [Contract](media-still-previews.md).

All 274 host Android cases pass at `3976f94`; real host MPV/native-core tests,
61 independent model checks, ARM64 JNI and complete source-bundle verification
pass. Review fixes for error eligibility and report aliases are committed.
The fortified-compiler rollback correction at `c692a49` passes real C/JNI/pixel
and both fortification-level object checks. `512f032` orders accepted slot
release before report EOF while retaining blocked-write cancellation; all 31
focused host cases pass, including two deterministic ordering regressions.
Fresh complete source/PR/API 26/35 gates remain required before publication.

PR runtime run 45492217492 passes all 14 on API 26/35 at `cb434d6`, whose full Git
tree equals head `3976f94`; downloaded sources/APKs/libraries/reports/transfer
and alias-byte/offset facts were independently audited. Push run 45492123756
API26 fails the immediate same-connection hash assertion; its rejected response
was not retained by the old harness, so the exact failure cause is unknown.
The independently identified EOF-before-queued-release gap is corrected without
retries. Main docs-only run 45490329463 API 35 fails before clipboard access:
current window focus was absent; Launcher ANR and boot pressure are observed,
while an interfering overlay remains unproven. Keep both failed captures.
The new harness preserves bounded replies before assertions and samples current
resumed/attached/shown/focus state plus bounded failure-only window diagnostics.
It retains the focus requirement and performs each clipboard/hash operation once.

Root's local logs/receipts and original ZIPs are under
`/workspace/work/runtime-evidence/{2209322,3976f94}` and `/workspace/attachments`.
The clean native core is unchanged; local ARM64 Binwalk/archive/PhotoRec/MPV
rebuilds also pass. Large regenerable CMake/IDE plans and Rust targets were
reclaimed after preserving generated JNI/source identities and logs; clean CI
must perform full assembly. The earlier working pre-alpha.20 APK remains loose.

Retain test fixes splitting API26 into a fresh JNI fork, delegating framework
drawables to the stock bitmap shadow and using readAllBytes for Android APIs.
OS-fact tests substitute Robolectric's placeholder fstat/fcntl explicitly;
they do not establish real kernel behavior. Actual Android MPV/isolated decoder,
ARM64 handset/full UI/SAF/playback remain unverified. The x86 lab has no MPV
runtime. Serialize edits/builds and never overwrite loaded JNI. The original
experiment remains at `savepoint/mpv-frame-foundation-0ef94d3`.

## Reproduce clean builds

Pinned JDK17, Gradle8.11.1, SDK/build-tools35, NDK28.2.13676358, CMake3.22.1,
Rust1.90.0. Normal CI rebuilds native code from pinned inputs.

```sh
bash scripts/fetch-engine.sh
bash scripts/test.sh
bash scripts/test-native.sh
bash scripts/test-workbench-ui.sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug :runtime-worker:assembleDebug :runtime-worker:assembleDebugAndroidTest
bash scripts/verify-apk.sh
python3 scripts/verify-worker-runtime.py
```

The laboratory owns its generated JNI/archive-assets outputs independently of
the app. Actual execution uses disposable x86 API 26/35 emulators in the separate
[runtime laboratory](../runtime-worker/README.md). Its report runner verifies
unique completion length/SHA before parsing, retries only immutable reads and
preserves failed-check reports or last partials. Publication consumes an exact
successful source capture; keep original CI sidecars separate from verifier
output and independently download/audit every public asset.

## Managed workspace

The 7.9 GiB workspace retains pinned inputs and immutable APK payloads. Fifty
older paths are losslessly stored at
`/workspace/work/saved-apks/signed-apks-before-pre-alpha18.tar.xz`, SHA-256
`7f52a27bc1dc254f2e6e5716779e2ff3e020e95a1dc2936ce7027e5d0a390ca3`.
All original payloads were decompressed/hashed before loose copies were removed;
a real pre-alpha.17 restore matched its frozen identity. List/restore:

```sh
python3 /workspace/work/saved-apks/restore.py
python3 /workspace/work/saved-apks/restore.py releases/0.6.0-pre-alpha.17/ci/GlassDigestor-0.6.0-pre-alpha.17-dev.g7c803225ff70-arm64-v8a-debug.apk
```

Restore refuses an existing path; `--output /new/path.apk` makes a separate copy.
Recent18/19/20 APKs remain loose. Reclaim only documented regenerable outputs or
verified duplicates. The216MiB Rust target was reclaimed after preserving exact
host/generated JNI; source/binaries/APKs remain unchanged. Local unchanged-native
reuse is a disk workaround; clean CI must rebuild. The next worktree links
existing pinned inputs, but the checked resource parser is prepared independently
because its preparer correctly refuses symlink targets. Runtime report-transfer
failure receipts and all current gates are retained under `/workspace/work`.
Earlier commands remain in [history](history/development-through-pre-alpha18.md).

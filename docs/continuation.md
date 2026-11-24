# Development continuation

Read [status](STATUS.md), [project map](PROJECT-MAP.md), [AGENTS.md](../AGENTS.md)
and the required design/parity/workbench documents first.

## Integrated baseline

Canonical repository: `sperglord8008s/GlassDigestor`. Main `7f171ad` merges PR #23.
Published **pre-alpha.19 / code 25**, exact source `d91e39f`, passes all five
source/PR jobs, **241 host Android tests** and **12 actual x86 API 26/35
contracts**. All 14 public assets, six original CI checkpoint files and 116
component-source ZIP members passed independent audits.
[Release](releases/0.6.0-pre-alpha.19.md)
· [runtime receipt](verification/android-worker-runtime-d91e39f.json)
· [public asset receipt](verification/pre-alpha19-release.json).

Preserve `org.diemobile`, signing configuration, immutable release assets and
fallback APK bytes. Distinguish host checks, actual x86 fixtures, production
ARM64 and handset/SAF/UI acceptance. The new platform contract tests settings/
Parcel/private-log behavior; it does not run Binwalk JNI or full interface.
[Operational controls](workbench-usability.md).

## Active pass

`codex/nist-mpeg-recovery` is the current pass. During parallel work, native
recovery/models/tests and neutral UI labels have disjoint ownership; root owns
release/docs/version/build integration and actual corpus export/scoring. Do not
compile changing sources or overwrite a library while a JVM has it loaded.
Commit coherent slices and inspect clean CI before another release.

The independently pinned NIST L0_Video archive decodes exactly 62,578,688 bytes
through host JNI. The new actual host native/export run scores **6/6** reference payloads,
with 79 additional unscored exports retained. Full pre-alpha.20 gates remain
pending. Implemented: separate 64 MiB streaming data work from 8 MiB PDF reads;
record only positively bounded MPEG owners; preserve every raw pack header and
SCR=0 independent start; skip redundant optional callbacks only inside valid
owners with explicit source relationships. Keep aggregate/resource bounds,
legacy captures, PDF behavior, cancellation and source checks. Validate actual
native acquisition → gallery → exported ZIP against independent hashes, rather
than replay or header coordinates. Remaining 29 images/fragments are unscored.
[Corpus/method](../tests/fixtures/nist/README.md)
· [current actual export](verification/nist-L0-video-native-export.json)
· [historical baseline](verification/nist-L0-video-export-baseline.json).

The unfinished MPV raw-frame foundation remains remotely at
`savepoint/mpv-frame-foundation-0ef94d3` (`0ef94d3`). Host pixels/ownership passed;
worker/UI/Android integration remains pending. Resume after recovery stability.
[Branch consolidation](history/branch-consolidation-2026-11-25.json).

## Reproduce

Pinned JDK 17, Gradle 8.11.1, SDK/build-tools 35, NDK 28.2.13676358 and CMake
3.22.1. Clean CI rebuilds native code normally from pinned inputs.

```sh
bash scripts/fetch-engine.sh
bash scripts/test.sh
bash scripts/test-native.sh
bash scripts/test-workbench-ui.sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
bash scripts/verify-apk.sh
./gradlew --no-daemon :runtime-worker:assembleDebug :runtime-worker:assembleDebugAndroidTest
python3 scripts/verify-worker-runtime.py
```

Actual platform execution uses disposable x86 API 26/35 emulators in the
separate [worker runtime laboratory](../runtime-worker/README.md). Publication
consumes an exact successful source capture; audit original sidecars separately
from APK-verifier output, and independently download/audit public assets.

## Managed workspace continuation

The 7.9 GiB workspace retains pinned toolchains/dependencies and immutable APK
payloads. Fifty older APK paths are losslessly stored at
`/workspace/work/saved-apks/signed-apks-before-pre-alpha18.tar.xz`, SHA-256
`7f52a27bc1dc254f2e6e5716779e2ff3e020e95a1dc2936ce7027e5d0a390ca3`.
Every original payload was decompressed/hashed before loose copies were removed;
an actual pre-alpha.17 restore matched its frozen identity. List or restore:

```sh
python3 /workspace/work/saved-apks/restore.py
python3 /workspace/work/saved-apks/restore.py releases/0.6.0-pre-alpha.17/ci/GlassDigestor-0.6.0-pre-alpha.17-dev.g7c803225ff70-arm64-v8a-debug.apk
```

Manifest/README record original names/hashes/aliases. Restore refuses an existing
destination; `--output /new/path.apk` makes a separate copy. Pre-alpha.18 and .19
stay loose and public releases are unchanged. Historical commands referring to
older loose paths require restoration first. Reclaim only audited regenerable
outputs or verified duplicate downloads; local unchanged-native reuse is a disk
workaround, never a substitute for clean CI. Earlier receipts remain in the
[historical continuation](history/development-through-pre-alpha18.md).

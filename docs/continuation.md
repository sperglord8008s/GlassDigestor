# Development continuation

Read [current status](STATUS.md), [project map](PROJECT-MAP.md), [AGENTS.md](../AGENTS.md)
and the required design/parity/workbench documents first.

## Integrated baseline

Canonical repository: `sperglord8008s/GlassDigestor`. Main `c2ecc2f` merges PR #22.
Published **pre-alpha.18 / code 24**, exact source `47bde0b`, has all five source
jobs, 213 host Android tests and 11 actual x86 API 26/35 contracts passing.
Both main runs also pass. The 14 public assets, six original CI files and
116 component-source ZIP members were independently audited.
[Release](releases/0.6.0-pre-alpha.18.md)
· [runtime receipt](verification/android-worker-runtime-47bde0b.json)
· [public asset receipt](verification/pre-alpha18-release.json).

Preserve `org.diemobile`, signing configuration, immutable release assets and
fallback APKs. Keep actual Android/host/production ARM64 claims separate.

## Current integration

`codex/workbench-improvements` is the one active working branch. It contains
the independent NIST ZIP scorer/oracle foundation (`d9aeafc`) and current parallel
usability work. Root owns Git/docs/builds; assigned agents own time limits,
strings, grids, common controls and diagnostics/About without overlapping edits.
Do not compile sources while another agent is still changing those sources.
Record handoffs and run focused checks, then full required checks and clean CI.

The requested pass includes configurable Binwalk/worker deadlines, Strings
minimum/search/Categories, functional drag scrollbars, compact explanations,
zoom/range selection, local diagnostics/About and neutral information language.
Current implementation/verification state belongs in STATUS and feature parity;
compilation alone never becomes runtime verification.

## Recovery continuation

The exact NIST L0_Video archive identity and all 62,578,688 decoded bytes pass
host JNI. Independent actual ZIP scoring gives **5/6** reference files and
79 retained additional exports; MPEG reaches the 8 MiB callback budget.
The offline oracle regenerates byte-identically and nine scorer contracts pass.
[Corpus/method](../tests/fixtures/nist/README.md)
· [baseline](verification/nist-L0-video-export-baseline.json).
Keep larger streaming checks/repeated MPEG packs and the remaining 29 images
on the queue. Fragmented/missing-data cases are unscored; production discovery
must never consume expected layout coordinates/hashes.

The unfinished MPV raw-frame foundation is preserved remotely at
`savepoint/mpv-frame-foundation-0ef94d3` (commit `0ef94d3`). Host pixel/ownership
checks passed; worker/UI/Android integration remains pending. Resume after the
current usability/time-limit pass. [Branch consolidation](history/branch-consolidation-2026-11-25.json).

## Reproduce

Use pinned JDK 17, Gradle 8.11.1, SDK/build-tools 35, NDK 28.2.13676358 and
CMake 3.22.1. The normal clean CI builds native code from its pinned inputs.

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

Runtime execution needs disposable x86 API 26/35 emulators; use the separate
worker-runtime workflow. [Runtime laboratory](../runtime-worker/README.md).
Release publication consumes an exact successful source capture; verify both
source jobs and the separate runtime run, then independently download/audit.

## Workspace constraints

The managed workspace has about 7.9 GiB total. Current cached toolchains/deps and
all frozen APKs are retained. Reclaim only documented regenerable build copies
or independently verified duplicate temporary downloads. No JNI library may be
overwritten while a test process has it loaded. Serialize native builds/tests.
Local audited unchanged-native reuse is a disk workaround; clean CI must rebuild
normally. Full earlier receipts/commands remain in
[historical continuation](history/development-through-pre-alpha18.md).

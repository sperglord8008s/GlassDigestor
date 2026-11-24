# Current project status

**GlassDigestor** is the canonical product and repository name. Start here;
[project map](PROJECT-MAP.md) explains the code and
[continuation](continuation.md) records reproducible commands and next steps.

## Published save point

[**0.6.0-pre-alpha.19 / code 25**](https://github.com/sperglord8008s/GlassDigestor/releases/tag/v0.6.0-pre-alpha.19),
exact source `d91e39f`, merged in PR #23 as `7f171ad`.

- Binwalk native time limits are visible/configurable from 1–1800 seconds
  (default 120), with a captured 30-second worker allowance.
- Strings has minimum length, live search/count and complete Categories;
  explanations are compact, generated lists/details have functional thumbs.
- Byte/entropy grids support bounded zoom/pan and contiguous range actions.
- Options provides About and a private bounded debug log. Interface labels
  use Information, Details and Source while stored schemas remain compatible.
- All **241 host Android cases**, five source/PR jobs, ARM64 assembly/lint and
  APK signature/offline/identity/static alignment pass.
- All **12 actual x86 API 26/35 contracts** pass. New platform coverage verifies
  captured settings/Bundle/Parcel and private diagnostic storage/privacy/
  retention; it does not execute Binwalk JNI, full UI or SAF.
- All **14 public assets**, six original CI files and 116 component-source ZIP
  members were independently audited: 87 exact repository files and 29 pinned
  upstream archives. [Release receipt](verification/pre-alpha19-release.json)
  · [Android receipt](verification/android-worker-runtime-d91e39f.json)
  · [controls and scope](workbench-usability.md).

Production ARM64/full UI/SAF and complete desktop parity remain separate,
incomplete acceptance targets. [Feature checklist](FEATURE-PARITY.md).
Pre-alpha.18 and all previous release payloads remain immutable fallbacks.

## Current work: recovery and terminology

Main is the integrated codebase. The completed workbench branch is consolidated;
one active pass, `codex/nist-mpeg-recovery`, tackles the remaining L0_Video file
and residual interface terminology. The earlier 19 fully merged remote branches
were removed with exact-tip leases; all commits remain in main. Unfinished MPV
frames remain at `savepoint/mpv-frame-foundation-0ef94d3`.
[Branch record](history/branch-consolidation-2026-11-25.json).

The test-only NIST CFReDS catalog contains all 30 related file-carving images,
534 original layout rows and seven disclosed inconsistencies. Only L0_Video is
currently acquired/hash-pinned. Exact exported-payload scoring finds **5/6**
reference files on host, including two exact AVI copies; 79 additional exports
remain unscored. The 22.6 MB MPEG hits the current 8 MiB callback-work budget.
[Baseline](verification/nist-L0-video-export-baseline.json)
· [offline corpus/scoring commands](../tests/fixtures/nist/README.md).

The next pass separates bounded data work from PDF metadata reads and preserves
all repeated MPEG pack observations without re-running already covered optional
callbacks. Its implementation and exact exported-byte acceptance are pending.
The other 29 images, fragmented recovery, MPV decoded-frame integration, broader
format support and package knowledge remain on the queue. Oracles never guide
production discovery. Historical narratives live under [history](history/development-through-pre-alpha18.md).

# Current project status

**GlassDigestor** is the canonical product and repository name. Start here for
current state; [project map](PROJECT-MAP.md) explains the code and
[continuation](continuation.md) records the next development steps.

## Published save point

[**0.6.0-pre-alpha.18 / code 24**](https://github.com/sperglord8008s/GlassDigestor/releases/tag/v0.6.0-pre-alpha.18),
exact source `47bde0b`, fixes trusted Android archive-cache path aliases and adds
direct archive Browse, decoded-file Open/Save and preview → Gallerize actions.
The NIST BZip2 sample in the report decodes all 62,578,688 bytes with the
independently pinned SHA-256.

- All **213 host Android tests** and all five source CI jobs pass.
- All **11 actual x86 Android contracts** pass on API 26 and 35. These include
  exact 20 MiB decoding, cache aliases, retained origins, isolated worker
  behavior, PDF pages and focused clipboard checks.
- All **14 public assets** and all six original CI files were independently
  verified. Component sources contain 87 exact repository files and 29 pinned
  upstream archives. [Release receipt](verification/pre-alpha18-release.json)
  · [Android receipt](verification/android-worker-runtime-47bde0b.json).
- Production ARM64/full UI/SAF acceptance and complete desktop parity remain
  separate, incomplete acceptance targets. [Feature checklist](FEATURE-PARITY.md).

## Current working pass: pre-alpha.19

One integration branch, `codex/workbench-improvements`, consolidates the current
work. Nineteen fully merged remote branches were removed with exact-tip leases;
all commit history remains in `main`. Unfinished MPV frame work is preserved in
`savepoint/mpv-frame-foundation-0ef94d3`.
[Branch record](history/branch-consolidation-2026-11-25.json).

The user has authorized parallel implementation of:

1. Configurable Binwalk native and worker time limits, with captured settings
   and cancellation retained.
2. Strings minimum length beside Strings, clear Categories, and search flowing
   into the complete classified list.
3. Compact explanation controls and genuinely draggable list/detail scrollbars.
4. Zoomable byte/entropy grids and contiguous multi-block selection/actions.
5. A bounded private debug log and About page with actual version/source/links.
6. Consistent Information / Details / Source wording throughout the interface.

These changes are implemented on the working branch. **62 focused host
Android cases**, eight Rust checks and Binwalk JNI/64 MiB/configured-limit
fixtures pass; its ARM64 bridge compiles. Full-suite/build/CI/actual Android
regression gates remain pending. [Controls and precise scope](workbench-usability.md). Parallel agents have disjoint file ownership; integration and builds
are serialized on the one working branch.

## Recovery work continues

The test-only NIST CFReDS catalog contains all 30 related file-carving images,
534 original layout rows and seven disclosed reference inconsistencies. Only
L0_Video is currently acquired/hash-pinned. Exact exported-payload scoring finds
**5/6** reference video files on host, including two exact AVI copies, and
retains 79 additional unscored exports. The 22.6 MB MPEG candidate hits the
current 8 MiB streaming callback budget. [Baseline](verification/nist-L0-video-export-baseline.json)
· [offline scoring commands](../tests/fixtures/nist/README.md).

Larger bounded streaming checks/repeated MPEG pack handling, the remaining
corpus, MPV decoded-frame integration, broader format support and package
knowledge remain on the queue. Expected layouts/hashes are test oracles only.

Older development narratives and verification counts are retained under
[history](history/development-through-pre-alpha18.md), rather than presented as
current pending work.

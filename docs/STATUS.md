# Current project status

**GlassDigestor** is the canonical product and repository. [Project map](PROJECT-MAP.md)
explains the code; [continuation](continuation.md) records reproducible commands.

## Published save point

[**0.6.0-pre-alpha.20 / code 26**](https://github.com/sperglord8008s/GlassDigestor/releases/tag/v0.6.0-pre-alpha.20),
exact source `691a4b1`, merged through PR #24 as `a42b01b`.

- PhotoRec adapter 6 recovers larger MPEG streams with separate 64 MiB data /
  8 MiB PDF read budgets. Every original header remains; continuation omissions
  need independently bounded owners and never invent inner file extents.
- The real NIST L0_Video pipeline exports **6/6 reference files byte-for-byte**,
  improved from 5/6. All 79 additional exports and 28,890 original PhotoRec
  occurrences remain. Only this corpus case is acquired/pinned/scored.
- Remaining labels use Information, Details, Source or Associations. Legacy
  report/type identities and raw values remain compatible.
- Report transfers validate completion length/SHA before parsing and retry only
  immutable reads. App and laboratory JNI/assets outputs are independently owned;
  combined builds pass.
- All **241 host Android cases**, both five-job source/PR gates, required native/
  generic/80/256 MiB/PDF checks, assembly/lint and static APK checks pass.
- All **13 actual x86 API 26/35 contracts** pass, including exact generated 9 MiB
  MPEG bytes, all 4,608 pack rows, restart/truncation and descriptor/source checks.
- All **14 public assets**, six original CI files and 118 component-source ZIP
  members passed independent audits: 89 exact repository files and 29 pinned
  upstream archives. [Release receipt](verification/pre-alpha20-release.json)
  · [Android receipt](verification/android-worker-runtime-691a4b1.json)
  · [NIST actual export](verification/nist-L0-video-native-export.json).

Pre-alpha.19's configurable Binwalk limits, Strings minimum/search/Categories,
compact definitions, draggable scrollbars, zoom/range grids and About/private
log remain available. [Controls](workbench-usability.md).
Production ARM64/full UI/SAF/all-codec/fragmented recovery and full desktop parity
remain separate incomplete acceptance targets. [Current checklist](FEATURE-PARITY.md).
Previous release assets remain immutable fallbacks.

## Active pass: MPV still-image fallback

The next pass uses platform decoders first, then bounded isolated MPV stills for
eligible refusals, with separate source-bound platform/MPV information. Host
native video/SAR/pixel checks, 61 independent model/raster cases, 24 new host
Android cases, ARM64 JNI compilation and source-bundle verification pass.
The full 265-case host suite passes at `b839189`; review corrections and final
build/CI remain in progress. Actual
ARM64 MPV, isolated native execution, full UI/SAF/playback remain unverified;
the x86 laboratory does not package MPV. [Contract](media-still-previews.md).

Main is integrated; the completed recovery branch is removed after ancestry
verification. MPV work is isolated from the released capture. Earlier merged
branch heads were consolidated without losing commits, and the original
experiment remains at `savepoint/mpv-frame-foundation-0ef94d3`.
[Branch record](history/branch-consolidation-2026-11-25.json).

The remaining 29 CFReDS images, fragmented reconstruction, broader format/
knowledge coverage and desktop comparison stay on the queue. The catalog
preserves 534 reference rows and seven disclosed inconsistencies; oracles never
guide production discovery. [Corpus/scoring](../tests/fixtures/nist/README.md).
Historical release narratives are kept under [history](history/development-through-pre-alpha18.md).

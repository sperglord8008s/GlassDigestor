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

Pre-alpha.21 / code 27 is under integration on `codex/mpv-image-fallback`.
Platform decoders run first; bounded isolated MPV stills and an explicit mode
extend image/video/archive previews while keeping observations separate.
Actual host FFV1/TIFF/PPM pixels, display/sample-aspect bounds, 61 independent
model/raster checks, ARM64 JNI and corresponding-source checks pass. All 274
host Android cases passed at `3976f94`; all 31 focused cases pass after the
worker completion fix at `512f032`. Fresh full CI remains required.

Review corrections reject non-decoder media errors and unsafe report aliases.
Fortified host compilation exposed an ignored rollback result; checked cleanup
and real-file failure tests now pass at both fortification levels. Worker report
EOF is now ordered after main-thread ownership release and data cleanup, with
deterministic blocked-write/same-token/cancellation tests. Runtime failures retain
bounded replies and current focus snapshots without retrying the operation.

Both API 26/35 PR captures pass all 14 contracts at an exact-tree-equivalent
source, but a separate API 26 push fails immediate recovery and a main API 35
capture fails its real-focus prerequisite. Both failed artifacts are retained;
release acceptance requires fresh gates after the corrections. Actual Android
MPV, ARM64 handset/full UI/SAF/playback remain unverified. The x86 laboratory
contains no native MPV. [Contract](media-still-previews.md).

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

# Project map

The repository and Android product are **GlassDigestor**. `main` holds integrated
work; `codex/mpv-image-fallback` is the current working branch. Named
`savepoint/…` tags preserve unfinished experiments. Released APK/source tags are
immutable numbered checkpoints. [Current status](STATUS.md) is the entry point.

## Source modules

| Location | Responsibility |
| --- | --- |
| `app/` | Production Android UI, input ownership, typed information and workflow models |
| `binary-engine/` | Pinned Binwalk integration and descriptor-based Rust JNI |
| `carving-engine/` | Pinned PhotoRec format callbacks and bounded whole-blob discovery |
| `archive-engine/` | Pinned libarchive/codecs and checked member acquisition |
| `media-engine/` | Pinned MPV/FFmpeg metadata integration and source/notice contracts |
| `hash-engine/` | Native streaming hashes |
| `resource-engine/` | Source-built Android manifest/resource parsing |
| `metadata-engine/` | Source-built metadata/EXIF parsing |
| `regex-engine/` | Source-built bounded regex compatibility for imported knowledge |
| `signing-engine/` | Pinned AOSP APK signing inspection |
| `runtime-worker/` | Separate test-only x86 Android runtime laboratory |
| `tests/`, `app/src/test/` | Independent fixtures, host checks and Android widget/model regressions |
| `scripts/` | Pinned dependency acquisition, builds, tests, packaging and release verification |
| `docs/` | Current contracts, feature checklist and continuation; older narratives in `docs/history/` |
| `vendor/`, `generated-assets/`, build directories | Acquired/generated inputs; reproducible from pinned recipes |

Within `app/src/main/java/org/diemobile`, `*Activity` files own screens;
`*Backend` / `*Document` files expose information; `InputSnapshot`, `OwnedContent`
and `PreparedExport` own bytes and delayed exports. `BinaryWorkerClient` and
`BinaryWorkerService` define isolated native transport. `WorkbenchUi`,
`ExpandableHelp` and scrolling/grid helpers share presentation behavior.

## Naming and compatibility

The stable application ID / Java-JNI namespace remains **`org.diemobile`** so
existing installs, native entry points and saved data keep their identity.
The old checkout folder name and some internal `Evidence…` model/schema names
are compatibility/history, not separate products. UI wording uses Information,
Details, Source and contextual explanations. Original file text, raw returned
fields and published report formats retain their exact bytes/meaning.

Detect It Easy, Binwalk, PhotoRec, MPV and other upstream names identify their
original projects and licensing/provenance. They are not alternative app names.

## Contributor entry points

Read [AGENTS.md](../AGENTS.md), [design goals](DESIGN-GOALS.md),
[feature checklist](FEATURE-PARITY.md) and [workbench contract](analysis-workbench.md).
Build/test instructions are in [README](../README.md) and
[continuation](continuation.md). Keep parsing, acquisition, transport, models and
UI separate; never execute inspected applications or upload selected contents.

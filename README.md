<p align="center">
  <img src="app/src/main/res/drawable-nodpi/brand_mark.png" width="144" alt="GlassDigestor: a lens over file layers" />
</p>
<h1 align="center">GlassDigestor</h1>
<p align="center"><strong>Open a file. Discover its contents. Follow the evidence.</strong><br/>An offline, free and open-source investigation workbench for Android.</p>
<p align="center">
  <a href="https://github.com/sperglord8008s/GlassDigestor/releases"><strong>Download a pre-alpha</strong></a> ·
  <a href="#recover-and-view-files">File recovery walkthrough</a> ·
  <a href="docs/FEATURE-PARITY.md">Verified capabilities</a> ·
  <a href="docs/continuation.md">Build & contribute</a>
</p>
<p align="center">
  <a href="https://github.com/sperglord8008s/GlassDigestor/actions/workflows/android.yml"><img alt="Android and host checks" src="https://github.com/sperglord8008s/GlassDigestor/actions/workflows/android.yml/badge.svg?branch=main" /></a>
  <img alt="Android 8.0+; ARM64" src="https://img.shields.io/badge/Android-8.0%2B_%C2%B7_ARM64-59e1ca?labelColor=101920" />
  <a href="LICENSE"><img alt="GPL-3.0-only frontend" src="https://img.shields.io/badge/license-GPL--3.0--only-ffbc66?labelColor=101920" /></a>
</p>

GlassDigestor investigates files, embedded media, archives, executables and
Android packages on your device. Technical facts, original evidence and
expandable explanations are available together. Dense views, search, paging
and complete exports support investigations beyond the first screen of results.

**No account, ads, telemetry, cloud upload or INTERNET permission.** Selected
applications are inspected as data and never executed. Android's normal
permissions determine which files and system information are accessible.

## Recover and view files

1. **Select a file → Gallerize.** PhotoRec and Binwalk inspect the owned input;
   **Files** shows bounded recovered payloads, with images, audio/video,
   documents and candidate text. Nested and overlapping findings stay available.
2. **Tap a file to preview it.** Use **Save file** for an individual payload,
   or **Export files (ZIP)** for actual files across every page in the selected
   group. Extensions, MIME types, source coordinates and original findings are
   retained. Hold a card for evidence and expert actions.
3. **Open an archive to explore its contents.** The native browser identifies
   supported archives from bytes, offers virtual folders and nested Back, and
   acquires members by exact index. Use **Gallery** for
   thumbnails, direct file saves and **Export files ZIP** for archive members.

Platform image/audio/video, paged UTF-8, PDF and Hex viewers are available.
PDFs have first-page thumbnails and Fit / Actual pixels reading with two-axis
scrolling and retained page/view position.
Unsupported codecs, unknown extents and extraction limits are explicit.
Recognition alone does not establish a complete file boundary or full validity.

[Recovered file gallery](docs/recovered-file-gallery.md) ·
[Archive file gallery](docs/archive-file-gallery.md) ·
[PDF recovery](docs/pdf-file-recovery.md) ·
[PDF page previews](docs/pdf-page-previews.md) ·
[Input limits and large-file checks](docs/input-budgets.md)

## Explore deeper

| Workbench | Tools |
|---|---|
| **Files & mixed blobs** | Detect It Easy signatures; PhotoRec/Binwalk discoveries; eight whole-file digests; classified strings, complete hex and configurable byte/entropy grids. |
| **Media & metadata** | Bounded in-app previews; EXIF/XMP/raw metadata; MPV/FFmpeg codec, container, track and property inspection; exact payload and report exports. |
| **PE, ELF & Mach-O** | Typed structures, maps, imports, exports, symbols, resources and disassembly, with supported links back to original bytes. |
| **Android packages** | DEX/smali; compiled manifests/resources; APK signatures/certificates; captured base/split relationships; sourced SDK/tracker matching and practical package knowledge. |
| **System & repeatable analysis** | Visible package/file/service inventory; captured normal-UID Binder evidence; scan profiles, document batches, byte/tree comparisons and complete scoped reports. |

[Engine credits and detailed tools](docs/workbench-features.md) ·
[Feature parity and remaining work](docs/FEATURE-PARITY.md)

## Install

Get an **ARM64 APK** from [GitHub Releases](https://github.com/sperglord8008s/GlassDigestor/releases),
alongside its SHA-256, build manifest and corresponding-source bundles. Requires
**Android 8.0 / API 26 or later**. Every checkpoint has a numbered version and
source revision; tap the app title or **Options → Build details** to inspect it.

Current releases are debug testing builds. Check signer digests before updating:
keys from separate CI runners can differ. Preserve your existing installation
and data when keys differ. See [build identity and signing scope](docs/build-versioning.md).

**F-Droid:** not listed yet. [Packaging preparation](docs/fdroid.md) documents
the MPV source-build, dependency, release and signing work needed for submission.

## Verification

The published [pre-alpha.16 checkpoint](https://github.com/sperglord8008s/GlassDigestor/releases/tag/v0.6.0-pre-alpha.16)
passed all five source CI jobs and **195 Android host tests**, using real native
engines/parsers where documented. ARM64 compilation, lint, APK signatures,
offline assets and static 16 KiB alignment are checked.

**Nine actual Android contracts** passed on x86 API 26 and 35. Downloaded
reports were independently checked against source, APK/library and report
hashes. They include isolated worker transport/cancellation, archive extraction,
bounded media observations, compiled declarations and exact recovery of a
9,437,636-byte PDF, plus exact recovered two-page PDF rendering with independently
known colors and geometry. [Exact verification evidence](docs/verification/android-worker-runtime-04e93c0.json).

**Pre-alpha scope:** production ARM64 handset/SAF/UI acceptance, every media
codec, arbitrary PDF/document support, fragmented recovery and full desktop Detect It Easy
parity remain incomplete. A permission, signature or entropy measurement does
not establish malicious behavior or safety. The
[parity checklist](docs/FEATURE-PARITY.md) separates implementation, host checks
and actual Android verification.

## Build and contribute

Pinned toolchain: JDK 17, Gradle 8.11.1, SDK/build-tools 35, NDK 28.2.13676358,
CMake 3.22.1 and Rust 1.90.0. Build-time dependency acquisition is separate from
offline application use.

Start with [AGENTS.md](AGENTS.md), [design goals](docs/DESIGN-GOALS.md) and the
[complete build/check/continuation guide](docs/continuation.md). Contributions
are welcome: reproducible recovery fixtures, handset test reports, accessibility,
source-audited engines, documentation and evidence-backed package knowledge.
For bugs, include the build identifier, Android version, tool and observed error.
Share sample bytes only when you have permission.

[Report an issue](https://github.com/sperglord8008s/GlassDigestor/issues) ·
[Discuss a change in a pull request](https://github.com/sperglord8008s/GlassDigestor/pulls)

## Open-source foundations

Detect It Easy / CDiE · Binwalk · PhotoRec · libarchive · libmpv / FFmpeg ·
metadata-extractor / Adobe XMP · AOSP apksig · Google smali / baksmali ·
Google binary-resources · RE2/J · LibChecker · UAD-NG.

Frontend: **GPL-3.0-only**. Upstream components retain their own licenses;
notices and corresponding-source information accompany the builds. Imported
Exodus data has ODbL/DbCL terms. See [LICENSE](LICENSE),
[component credits](docs/workbench-features.md#license) and
[artwork provenance](docs/branding/README.md).

Previously **DiE Android**. GlassDigestor is an independent application built
around credited upstream engines. The old repository URL redirects here;
the installed application ID remains `org.diemobile`.

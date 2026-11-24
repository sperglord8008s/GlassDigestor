# GlassDigestor contributor instructions

Read docs/DESIGN-GOALS.md, docs/FEATURE-PARITY.md and docs/analysis-workbench.md before changes.

## Goal
Complete functional desktop DiE parity first, then extend Android investigation capabilities. Expert-level data is the default; contextual explanations teach the underlying mechanisms without substituting simplified conclusions. Offline, free/open-source and no telemetry.

## Development workflow
- Inspect current main and the latest GitHub Actions jobs before editing. Fix failing regression checks before adding unrelated features.
- Reuse pinned CDiE/xxfclib modules and upstream data sources where possible. Preserve licensing notices, document exact versions and verify API contracts against pinned headers.
- Separate input acquisition, native bindings, evidence/report models and UI. Favor readable, maintainable code and small coherent changes with tests.
- Do not silently drop records; make truncation, unsupported formats, untested assumptions and errors visible.
- Treat all binaries and imported databases as untrusted data. Never execute inspected applications. Default read-only, no network. Privileged diagnostics require an explicit opt-in bridge and clear scope.
- Run scripts/test.sh and scripts/test-native.sh (after scripts/fetch-engine.sh, JDK 17 and CMake), and Android CI. Compilation is not runtime verification; report device tests separately.
- Commit coherent slices, inspect Actions results, correct failures, update feature parity and public documentation. Do not declare full parity until acceptance checks pass.
- Preserve the working APK as a fallback; don't change application ID or signing identity without discussing migration.

## Priority queue
1. Keep Android and host JNI CI green; add fixtures covering desktop scanner, malformed formats, APK signature verification and library detection.
2. Promote desktop scan backend to primary scan workflow once validated, unify settings, expose real signature evidence and contextual help by default.
3. Typed structured records and efficient paginated UI; cross-link file offsets to hex/disassembly/entropy, extract resources and navigate archives with back-stack support.
4. Android binary manifest/resource inspection, DEX metadata and disassembly, package/split correlation, full SDK and tracker matcher coverage and APK signing details.
5. Custom rule database management and testing, profiles, batch scanning, binary comparison, report schema and export.
6. Explicit optional read-only privileged system diagnostics (Shizuku/root), live services and processes; separate source acquisition from analysis. Document normal UID restrictions.
7. Native worker isolation, bounded resources, cancellation, malformed-input regression tests, full per-field technical references, accessibility and release reproducibility.

Review actual upstream desktop feature list and update the parity matrix, not just these broad milestones. See current Actions for the precise status of the newest changes.


## Project navigation and active work
- Canonical repository/product: `sperglord8008s/GlassDigestor` / GlassDigestor.
- Start with `docs/STATUS.md`, `docs/PROJECT-MAP.md` and `docs/continuation.md`; the required design/parity documents remain authoritative.
- `main` is the integrated codebase; use one clearly named working branch for the current pass. Remove merged branch heads only after verifying their commits are in main. Preserve unfinished experiments in named savepoint tags before removing their branch heads.
- Keep `org.diemobile` application/JNI identity compatible with existing installs. Upstream Detect It Easy names and notices identify the engine and its original authors.
- Product UI uses Information, Details and Source rather than framing every result as evidence. Raw inspected text, upstream returned fields and existing report schemas retain their exact meaning and bytes.

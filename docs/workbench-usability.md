# Workbench controls and operational information

This pass keeps analysis/data contracts separate from UI wording. Visible labels
use Information, Details and Source; existing serialized schema and Java/JNI
identities remain compatible.

## Controls

| Where | Behavior |
| --- | --- |
| Binwalk investigation → Time limit | Default 120 seconds native analysis, configurable whole seconds 1–1800; worker allows another 30 seconds for startup/report transfer. New requests capture settings once. Current results keep their original limits. Other engines retain their existing budgets. |
| Main → Strings | Minimum length 3–64, explicit Apply to the shared setting; the quick preview identifies its captured scan minimum until Rescan. Search/count are live and the exact query carries into complete classified strings. |
| Classified strings | Categories, complete paged search, captured minimum across recreation, exact text transfer and Why this classification? remain available. |
| Definition controls | Compact individual toggles and one overflow menu for Expand all / Collapse all; state remains persistent. |
| Generated lists / details | Native draggable list/grid thumbs; a hit-tested persistent detail thumb with true content-range movement, RTL/accessibility support and ordinary text/content touches retained. Paging remains explicit; a thumb scrolls its displayed page. |
| Gallerize byte/entropy grid | Transform-only pinch/± zoom up to 512×, two-finger pan, row-major anchor→end selection, New/Extend/step controls and exact range actions. Source/layout/viewport/selection survive recreation; zoom does not allocate a larger raster. |
| Options → Debug log | Private no-backup structural journal, UTC timestamps, 256 entries / 96 KiB; refresh, exact copy, clear and immutable captured export. No inspected contents, filenames, paths, search/translation queries, exception messages or network logging. Failures can retain safe app class/method/line. |
| Options → About | Actual version/code/revision/dirty state, product purpose, offline/privacy/license information, bundled attribution and explicit fixed repository/release/document links. |

## Request completion

The native Binwalk budget is propagated through the isolated descriptor request
and reported by adapter 3. Legacy adapter 2 captures retain their fixed 30-second
meaning. Integer types/bounds/native/worker agreement are validated. Deadline
and cancellation are checked again after serialization and journaling, before
success publication; recorded termination takes precedence over pipe errors.
Blocking upstream callbacks are not preemptible in native code; the parent
watchdog can still terminate the isolated worker with its request token.

## Verification

Current local checks: **62 focused host Android cases pass**, including actual
scroll gestures, complete strings navigation, exact selected-tail export across
source expiry/recreation, safe log copy/export/alias handling, source identity,
compact definitions and deadline publication races. Eight Rust checks and all
Binwalk JNI ownership/64 MiB/configured-limit fixtures pass; the ARM64 bridge
compiles. Independent grid tests cover 81 coordinate/range/maximum-layout cases.
Full host/native/release and actual Android regression gates are pending for
this pass. These checks do not establish production ARM64 handset, real SAF,
external browser/provider, font-scale or screen-reader acceptance.

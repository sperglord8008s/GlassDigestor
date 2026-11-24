# Bounded MPV still-image previews

Candidate pre-alpha.21 extends the existing MPV metadata probe with an isolated
still-image path for platform decoder refusals. Implementation is under
integration; successful compilation does not establish Android execution.

Platform image/media observations stay separate. A shared coordinator admits
MPV only for a decoder refusal or metadata-only video, after checking exact
source identity, cancellation and policy. Unknown, empty, changed, foreign or
over-budget ranges are refused. Explicit mode is **MPV still image**, not
playback; asynchronous video errors cannot replace a newer viewer generation.

The parent independently stages selected bytes and supplies a fresh raster
descriptor plus a validated maximum edge. The private `mpv-frame` worker uses
token/target binding, existing cancellation/deadline publication gates and a
45-second request deadline. Native guards retain 256 MiB input, 32 MiB cumulative
reads, 25 seconds, a 1536-pixel edge, 9 MiB packed RGBA and a 16 MiB screenshot.
Automatic thumbnails use 256 pixels and a 32 MiB acquisition cap. Decoder-internal
allocations are not bounded by these adapter limits.

The fixed scale filter fits display aspect and normalizes sample aspect to one.
Output `w/h`, display `dw/dh` and pixel aspect are checked before screenshot
allocation: MPV converts display dimensions before allocating its screenshot.
Node maps/lists, duplicates, stride, format and failure events are validated.
Parent code verifies exact raster length/SHA, opaque `rgba8` and explicit
RGBA-to-ARGB conversion; all temporary inputs/outputs are removed before its
final cancellation gate. Original findings and source reports remain unchanged.

Host native tests pass authored FFV1/Matroska, TIFF/PPM pixels, square and
anamorphic/extreme-SAR geometry, malformed nodes, positional descriptors,
read/deadline/output limits and source changes. Sixty-one independent typed
model/raster checks and standalone production/test compilation pass. All **24 new host Android cases** pass, including real host MPV pixels after
forced platform refusals, selected-range ownership, archive acquisition,
API 26 metadata-only video, policy/cancellation/stale-generation refusals and
manual-mode recreation. Worker protocol tests use platform substitutes where
Robolectric lacks real fstat/fcntl; they do not establish Android OS/UID checks.
The complete 265-case host suite passes at `b839189`; source bundles and ARM64
JNI compilation also pass. Review corrections, final assembly/static/CI, actual isolated MPV,
handset/full UI/SAF/playback and arbitrary codecs remain separate gates. The
x86 runtime laboratory has no MPV runtime; its existing contracts do not
verify this path.

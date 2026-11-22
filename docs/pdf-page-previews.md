# PDF pages in the recovered-file workbench

Recovered PDFs now have a first-page thumbnail in Gallerize and in the native
archive member gallery. The same `PdfPagePreview` helper supplies the full
in-app PDF viewer. Native footer recovery, exact file export and platform page
rendering remain separate observations: a recovered boundary does not guarantee
that a PDF renders, and one rendered page does not verify its complete structure.

## Owned input and pixels

The helper accepts an independently owned, nonempty `InputSnapshot` within
256 MiB. It checks the snapshot SHA-256 before and after rendering, opens a
read-only descriptor and closes the page, renderer and descriptor on success or
failure. Failed/cancelled/stale results discard their bitmap. No sample filename
becomes a URL, application or executable.

`PdfRenderer` needs a seekable complete file, so an embedded PDF range is first
copied into an exact private snapshot with the existing source/range/identity
checks. The temporary file is deleted after rendering. A standalone whole PDF
can reuse its already owned snapshot. Unknown extents never reach this decoder.

Automatic thumbnails require a known range of at most **32 MiB** and a target
of **1–512 pixels**; both galleries request 256. A larger file remains available
for explicit full preview and exact export. The full viewer uses a **1536-pixel**
maximum dimension. Both dimensions and their product are checked before bitmap
allocation. Only one selected page is rasterized per call.

PDF point dimensions are logical units (1/72 inch), distinct from bitmap pixels.
A small point-size document is rasterized up to the useful target, rather than
being limited to one bitmap pixel per PDF point. Aspect ratio is preserved to
integer-pixel rounding, with a minimum one-pixel dimension. Invalid geometry,
page indices, empty inputs, changed sources and cancellation fail explicitly.

These are output pixel/input-acquisition limits. Android's native parser can
allocate other memory and spend time inside uninterruptible calls. Checks
surround those calls; this is not a hard parser deadline or total-memory bound.
Rendering runs in the normal application UID, like the existing platform media
and image helpers. Production parser isolation remains separate work.

## Reading a page

The full viewer defaults to **Fit page**. **Actual pixels** displays the same
bounded raster at one screen pixel per raster pixel, with horizontal/vertical
scrolling for small text and details. Switching layouts reuses the captured
bitmap and does not parse the PDF again. Page, chosen view and scroll position
survive recreation. Position restoration waits for layout, and stale removed
navigation controls cannot skip beyond the returned page count.

The source summary uses two display lines and expands on tap without truncating
its underlying text. A 360×640 host check retains at least 180 pixels for the
PDF pane with a long source/origin summary. Complete origin, metadata, Hex and
exact file exports remain available.

## Observations and exports

The transient `org.diemobile.platform-pdf-preview` report retains the PDF's
SHA-256/size, platform API/UID, selected zero-based page index, returned page
count, returned point geometry, raster dimensions, target and render mode.
Embedded thumbnails also identify the parent input, physical range and
thumbnail acquisition limit. A defensive JSON copy cannot alter the capture.

The report records a rendered-page count separately from whether that covers
the platform's entire returned page count. Complete structural validity remains
unverified. Original PhotoRec/Binwalk rows, optional metadata-read limitations,
archive inventories and origin exports are unchanged. Gallery details expose
the transient platform observation separately from native findings.

Thumbnails and the full viewer share the same descriptor/identity/geometry
rules. Actual files and typed individual/ZIP exports retain all original bytes;
they are not replaced by the thumbnail or rendered bitmap. Unsupported states
remain visible and the existing Hex, metadata, evidence and export tools remain
available. Failed thumbnails do not reserve a blank image pane.

## Verification

Host tests substitute the platform renderer while checking exact embedded
descriptor bytes, source/report ownership, point-to-pixel sizing, resource
closure, render failures, cancellation, source mutation, malformed geometry,
page/range/budget refusals and temporary-file cleanup. Gallery tests check one
request per known PDF, unknown extents never decoding, a real-libarchive PDF
member with a misleading audio suffix, exact `.pdf`/MIME save bytes, full-view
page navigation and recreation. These tests do not count as PDF engine runtime
verification.

A ninth actual Android lab contract is implemented. It recovers an owned
two-page PDF from a mislabeled prefixed blob through the real isolated PhotoRec
worker, then uses the unchanged production helper in the normal app UID. The
fixture has independently known red and green vector pages, 144 × 96 points.
The case requires 24 × 16 thumbnails, exact center colors, a 1536 × 1024 full-view
raster, exact recovered bytes/SHA-256, page-bound refusal, source/UID/range
identity and private-stage cleanup. Independent Python checks the fixture's
xref/object offsets and original 440-byte recovery fixture identity.

Instrumentation compilation passes; actual API 26/35 execution and the final
source/build gates are recorded separately in the release checklist. No actual
PDF rendering result is claimed until that new contract passes. Production
ARM64/handset/gallery/SAF acceptance, arbitrary PDFs, text selection/search,
forms/annotations/passwords, animation and all document formats remain outside
this verification.

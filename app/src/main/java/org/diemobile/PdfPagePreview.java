// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Bounded platform PDF pages from independently owned bytes; native observations stay separate. */
final class PdfPagePreview {
    static final int MAX_TARGET = 1536;
    static final long MAX_THUMBNAIL_BYTES = 32L * 1024 * 1024;

    /** Caller owns the bitmap. The immutable report never owns an input or descriptor. */
    static final class Result {
        final Bitmap bitmap;
        final int pageIndex, pageCount, widthPoints, heightPoints;
        private final String evidence;

        private Result(
                Bitmap bitmap, int index, int count, int width, int height, JSONObject report) {
            this.bitmap = bitmap;
            pageIndex = index;
            pageCount = count;
            widthPoints = width;
            heightPoints = height;
            evidence = report.toString();
        }

        JSONObject report() throws Exception {
            return new JSONObject(evidence);
        }

        String description() {
            return "PDF page "
                    + (pageIndex + 1)
                    + " / "
                    + pageCount
                    + " · "
                    + widthPoints
                    + " × "
                    + heightPoints
                    + " points\n"
                    + "Platform rendered "
                    + bitmap.getWidth()
                    + " × "
                    + bitmap.getHeight()
                    + " pixels · single-page render";
        }
    }

    static Result read(InputSnapshot input, int pageIndex, int target, BooleanSupplier stop)
            throws Exception {
        if (input == null || input.size <= 0 || input.size > FileRange.MAX_OPERATION_BYTES)
            throw new IOException("PDF preview needs a nonempty owned input within 256 MiB");
        if (pageIndex < 0 || target < 1 || target > MAX_TARGET)
            throw new IOException("PDF page must be nonnegative; pixel target must be 1–1536");
        input.checkIdentity(stop);
        Bitmap bitmap = null;
        boolean success = false;
        try {
            int count, width, height;
            try (ParcelFileDescriptor file =
                            ParcelFileDescriptor.open(
                                    input.file, ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer renderer = new PdfRenderer(file)) {
                EvidenceRows.check(stop);
                count = renderer.getPageCount();
                if (count <= 0 || pageIndex >= count)
                    throw new IOException("PDF page is outside the returned page count: " + count);
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    EvidenceRows.check(stop);
                    width = page.getWidth();
                    height = page.getHeight();
                    if (width <= 0 || height <= 0)
                        throw new IOException("PDF renderer returned invalid page geometry");
                    // PDF points are logical units. Rasterize to the useful target even for a
                    // small point-size document; no screen-size/native-size allocation occurs.
                    double scale = (double) target / Math.max(width, height);
                    int pixelsWide = Math.max(1, (int) Math.round(width * scale));
                    int pixelsHigh = Math.max(1, (int) Math.round(height * scale));
                    if (pixelsWide > target
                            || pixelsHigh > target
                            || (long) pixelsWide * pixelsHigh > (long) target * target)
                        throw new IOException("PDF page exceeds its pixel budget");
                    bitmap = Bitmap.createBitmap(pixelsWide, pixelsHigh, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    EvidenceRows.check(stop);
                }
            }
            input.checkIdentity(stop);
            JSONObject report =
                    new JSONObject()
                            .put("schema", "org.diemobile.platform-pdf-preview")
                            .put("schemaVersion", 1)
                            .put("sourceSha256", input.sha256)
                            .put("sourceSizeBytes", input.size)
                            .put("sdk", Build.VERSION.SDK_INT)
                            .put("applicationUid", android.os.Process.myUid())
                            .put("pageIndex", pageIndex)
                            .put("pageCount", count)
                            .put("widthPoints", width)
                            .put("heightPoints", height)
                            .put("renderedWidthPixels", bitmap.getWidth())
                            .put("renderedHeightPixels", bitmap.getHeight())
                            .put("maximumDimensionPixels", target)
                            .put("renderMode", "RENDER_MODE_FOR_DISPLAY")
                            .put("renderedPageCount", 1)
                            .put("allReturnedPagesRendered", count == 1)
                            .put("completeFileValidityVerified", false)
                            .put(
                                    "meaning",
                                    "Transient platform rendering of one page from owned PDF bytes."
                                        + " Point geometry and bitmap pixels use different units."
                                        + " Native parse time/internal memory and other"
                                        + " page/structural validity are not established by a"
                                        + " successful bounded bitmap.");
            Result result = new Result(bitmap, pageIndex, count, width, height, report);
            success = true;
            return result;
        } finally {
            if (!success && bitmap != null) bitmap.recycle();
        }
    }

    /** PdfRenderer requires a seekable whole file; embedded ranges get exact private snapshots. */
    static Result thumbnail(
            InputSnapshot input, FileRange range, File directory, int target, BooleanSupplier stop)
            throws Exception {
        if (input == null || range == null || range.fileSize != input.size || range.length <= 0)
            throw new IOException(
                    "PDF thumbnail range is absent, empty or belongs to another input");
        if (range.length > MAX_THUMBNAIL_BYTES)
            throw new IOException(
                    "Automatic PDF thumbnails are limited to 32 MiB; open the full file"
                            + " explicitly");
        if (target < 1 || target > 512)
            throw new IOException("PDF thumbnail target must be 1–512 pixels");
        Result result = null;
        boolean success = false;
        try {
            if (range.offset == 0 && range.length == input.size)
                result = read(input, 0, target, stop);
            else
                try (InputSnapshot pdf = OwnedContent.stageRange(input, range, directory, stop)) {
                    result = read(pdf, 0, target, stop);
                }
            JSONObject report =
                    result.report()
                            .put("parentSourceSha256", input.sha256)
                            .put("parentSourceSizeBytes", input.size)
                            .put("physicalOffset", range.offset)
                            .put("storedSizeBytes", range.length)
                            .put("maximumThumbnailBytes", MAX_THUMBNAIL_BYTES);
            Result linked =
                    new Result(
                            result.bitmap,
                            result.pageIndex,
                            result.pageCount,
                            result.widthPoints,
                            result.heightPoints,
                            report);
            success = true;
            return linked;
        } finally {
            if (!success && result != null) result.bitmap.recycle();
        }
    }

    private PdfPagePreview() {}
}

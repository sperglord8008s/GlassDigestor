// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

/**
 * Independent coordinate, exact interval and scale checks; no Android or native engine required.
 */
public final class ByteGridViewportTest {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ByteGrid.Viewport viewport = new ByteGrid.Viewport(7, 5);
        viewport.resize(320, 240);
        double scale = 320.0 / 7;
        require(
                viewport.cellAt(6.5 * scale, (240 - 5 * scale) / 2 + 1.5 * scale) == 13,
                "Letterboxed hit testing lost original row-major coordinates");
        require(
                viewport.cellAt(1, 1) == -1
                        && viewport.cellAt(320, 120) == -1
                        && viewport.cellAt(-0.01, 120) == -1,
                "Outside-view/letterbox point selected bytes");
        viewport.zoomBy(4, 160, 120);
        require(
                viewport.cellAt(160, 120) == 17 && viewport.zoom == 4,
                "Zoom lost the center block");
        viewport.pan(100000, 100000);
        require(viewport.cellAt(0, 0) == 0, "Panning crossed the source start");
        viewport.pan(-100000, -100000);
        require(viewport.cellAt(319.99, 239.99) == 34, "Panning lost the final block");
        viewport.fit();
        require(viewport.zoom == 1 && viewport.cellAt(160, 120) == 17, "Fit lost full-grid origin");
        require(
                !viewport.restore(Double.NaN, 1, 1)
                        && !viewport.restore(2, Double.POSITIVE_INFINITY, 1)
                        && !viewport.restore(2, -1, 1),
                "Non-finite/foreign viewport state accepted");

        // Focus is deliberately away from the edges, so no clamping can move it during zoom.
        ByteGrid.Viewport square = new ByteGrid.Viewport(100, 100);
        square.resize(400, 400);
        square.zoomBy(2, 160, 120);
        int fixed = square.cellAt(160, 120);
        square.zoomBy(3, 160, 120);
        require(
                square.cellAt(160, 120) == fixed && fixed == 3040,
                "Pinch focus changed the underlying source block");
        double originalZoom = square.zoom, originalCenter = square.centerX;
        square.zoomBy(Double.NaN, 160, 120);
        square.pan(Double.POSITIVE_INFINITY, 0);
        require(
                square.zoom == originalZoom && square.centerX == originalCenter,
                "Invalid gesture changed the viewport");

        ByteGrid.Layout layout = new ByteGrid.Layout(103, 4, 2);
        for (int anchor = 0; anchor < 8; anchor++) {
            for (int end = 0; end < 8; end++) {
                FileRange range = layout.selection(anchor, end);
                long expectedStart = 0, expectedLength = 0;
                for (int cell = 0; cell < 8; cell++) {
                    long cellLength = ((cell + 1L) * 103 / 8) - (cell * 103L / 8);
                    if (cell < Math.min(anchor, end)) expectedStart += cellLength;
                    if (cell >= Math.min(anchor, end) && cell <= Math.max(anchor, end))
                        expectedLength += cellLength;
                }
                require(
                        range.offset == expectedStart
                                && range.length == expectedLength
                                && range.fileSize == 103,
                        "Forward/reverse selection changed exact bytes");
            }
        }
        FileRange tail = layout.selection(6, 7);
        require(tail.offset + tail.length == 103, "Final remainder not included exactly once");
        ByteGrid.Layout partlyEmpty = new ByteGrid.Layout(3, 4, 2);
        try {
            partlyEmpty.selection(0, 3);
            throw new AssertionError("Unused block became an exact selection");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new ByteGrid.Layout(0, 4, 2).selection(0, 0);
            throw new AssertionError("Empty input became a selection");
        } catch (IllegalArgumentException expected) {
            checks++;
        }

        ByteGrid.Layout large = new ByteGrid.Layout(256L * 1024 * 1024, 2048, 512);
        ByteGrid.Viewport largeView = new ByteGrid.Viewport(large.width, large.height);
        largeView.resize(360, 240);
        largeView.zoomBy(Double.MAX_VALUE, 180, 120);
        require(
                largeView.zoom == ByteGrid.Viewport.MAX_ZOOM && largeView.scale() >= 48,
                "Maximum supported grid cannot zoom to usable touch blocks");
        largeView.pan(-Double.MAX_VALUE, -Double.MAX_VALUE);
        require(
                largeView.cellAt(359.99, 239.99) == large.used - 1,
                "Large-grid tail cannot be reached");
        FileRange whole = large.selection(large.used - 1, 0);
        require(
                whole.offset == 0 && whole.length == large.size,
                "Million-block reverse selection lost bytes");
        ByteGrid.Viewport restored = new ByteGrid.Viewport(2048, 512);
        require(
                restored.restore(largeView.zoom, largeView.centerX, largeView.centerY),
                "Valid viewport state refused before layout");
        restored.resize(360, 240);
        require(
                restored.cellAt(359.99, 239.99) == large.used - 1,
                "Deferred layout changed retained large-grid position");
        System.out.println("Byte grid viewport/range: " + checks + " independent checks pass");
    }
}

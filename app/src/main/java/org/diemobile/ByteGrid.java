// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Exact row-major byte partitions. Display resolution never samples away source bytes. */
final class ByteGrid {
    static final int MAX_CELLS = 1024 * 1024, MAX_DIMENSION = 2048;

    enum Mode {
        BYTES("Mean byte value · black 0 → white 255"),
        ENTROPY("Local entropy · blue 0 → yellow 8 bits/byte");

        final String legend;

        Mode(String legend) {
            this.legend = legend;
        }
    }

    static final class Layout {
        final int width, height, used;
        final long size;

        Layout(long size, int width, int height) {
            if (size < 0
                    || size > PhotoRecReport.MAX_INPUT_BYTES
                    || width < 1
                    || height < 1
                    || width > MAX_DIMENSION
                    || height > MAX_DIMENSION
                    || (long) width * height > MAX_CELLS)
                throw new IllegalArgumentException(
                        "Grid: dimensions 1–2048, at most 1,048,576 cells; input at most 256 MiB");
            this.size = size;
            this.width = width;
            this.height = height;
            used = (int) Math.min(size, (long) width * height);
        }

        FileRange range(int cell) {
            if (cell < 0 || cell >= width * height)
                throw new IllegalArgumentException("Cell outside grid");
            if (cell >= used) return new FileRange(size, size, 0);
            long start = (long) cell * size / used;
            return new FileRange(size, start, (long) (cell + 1) * size / used - start);
        }

        /** Every populated cell between the anchor and endpoint, in original byte order. */
        FileRange selection(int anchor, int end) {
            if (anchor < 0 || end < 0 || anchor >= used || end >= used)
                throw new IllegalArgumentException("Selection requires populated grid cells");
            FileRange first = range(Math.min(anchor, end));
            FileRange last = range(Math.max(anchor, end));
            return new FileRange(size, first.offset, last.offset + last.length - first.offset);
        }
    }

    /** Viewport coordinates only; zoom never changes byte partitions or raster allocation. */
    static final class Viewport {
        static final double MAX_ZOOM = 512;
        final int columns, rows;
        private int width, height;
        double zoom = 1, centerX, centerY;

        Viewport(int columns, int rows) {
            if (columns < 1 || rows < 1) throw new IllegalArgumentException("Empty grid");
            this.columns = columns;
            this.rows = rows;
            centerX = columns / 2.0;
            centerY = rows / 2.0;
        }

        void resize(int width, int height) {
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            clamp();
        }

        double scale() {
            return Math.min((double) width / columns, (double) height / rows) * zoom;
        }

        double left() {
            return width / 2.0 - centerX * scale();
        }

        double top() {
            return height / 2.0 - centerY * scale();
        }

        int cellAt(double x, double y) {
            double scale = scale();
            if (scale <= 0
                    || !Double.isFinite(x)
                    || !Double.isFinite(y)
                    || x < 0
                    || y < 0
                    || x >= width
                    || y >= height) return -1;
            double column = (x - left()) / scale, row = (y - top()) / scale;
            if (column < 0 || row < 0 || column >= columns || row >= rows) return -1;
            return (int) row * columns + (int) column;
        }

        void zoomBy(double factor, double focusX, double focusY) {
            if (!(factor > 0)
                    || !Double.isFinite(factor)
                    || !Double.isFinite(focusX)
                    || !Double.isFinite(focusY)
                    || scale() <= 0) return;
            double column = (focusX - left()) / scale();
            double row = (focusY - top()) / scale();
            zoom = Math.max(1, Math.min(MAX_ZOOM, zoom * factor));
            centerX = column - (focusX - width / 2.0) / scale();
            centerY = row - (focusY - height / 2.0) / scale();
            clamp();
        }

        void pan(double x, double y) {
            if (scale() <= 0 || !Double.isFinite(x) || !Double.isFinite(y)) return;
            centerX -= x / scale();
            centerY -= y / scale();
            clamp();
        }

        void centerOn(int cell) {
            if (cell < 0 || cell >= (long) columns * rows) return;
            centerX = cell % columns + 0.5;
            centerY = cell / columns + 0.5;
            clamp();
        }

        void fit() {
            zoom = 1;
            centerX = columns / 2.0;
            centerY = rows / 2.0;
        }

        boolean restore(double zoom, double centerX, double centerY) {
            if (!Double.isFinite(zoom)
                    || zoom < 1
                    || zoom > MAX_ZOOM
                    || !Double.isFinite(centerX)
                    || !Double.isFinite(centerY)
                    || centerX < 0
                    || centerX > columns
                    || centerY < 0
                    || centerY > rows) return false;
            this.zoom = zoom;
            this.centerX = centerX;
            this.centerY = centerY;
            clamp();
            return true;
        }

        private void clamp() {
            double scale = scale();
            if (scale <= 0) return;
            double halfWidth = width / (2 * scale), halfHeight = height / (2 * scale);
            centerX =
                    halfWidth >= columns / 2.0
                            ? columns / 2.0
                            : Math.max(halfWidth, Math.min(columns - halfWidth, centerX));
            centerY =
                    halfHeight >= rows / 2.0
                            ? rows / 2.0
                            : Math.max(halfHeight, Math.min(rows - halfHeight, centerY));
        }
    }

    static Layout automatic(long size, Mode mode) {
        long desired =
                mode == Mode.ENTROPY
                        ? Math.max(1, (size + 1023) / 1024)
                        : Math.max(1, Math.min(size, MAX_CELLS));
        // An exact familiar raster is useful for byte-sized inputs, without claiming an image
        // format.
        for (int width : new int[] {16, 32, 64, 128, 256, 320, 512, 640, 1024})
            if (desired == (long) width * width) return new Layout(size, width, width);
        int width = Math.min(1024, Math.max(1, (int) Math.ceil(Math.sqrt(desired))));
        return new Layout(size, width, (int) ((desired + width - 1) / width));
    }

    static final class Result {
        final Layout layout;
        final Mode mode;
        final String sha256;
        final double[] values;
        final int[] pixels;

        Result(Layout layout, Mode mode, String sha256, double[] values, int[] pixels) {
            this.layout = layout;
            this.mode = mode;
            this.sha256 = sha256;
            this.values = values;
            this.pixels = pixels;
        }
    }

    static Result measure(InputSnapshot input, Layout layout, Mode mode, BooleanSupplier stop)
            throws Exception {
        if (layout.size != input.size)
            throw new IOException("Grid input size differs from captured source");
        EvidenceRows.check(stop);
        double[] values = new double[layout.width * layout.height];
        int[] pixels = new int[values.length];
        Arrays.fill(values, Double.NaN);
        Arrays.fill(pixels, 0xffa000a0);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long[] histogram = new long[256];
        byte[] buffer = new byte[65536];
        int cell = 0;
        long position = 0, sum = 0;
        FileRange range = layout.used == 0 ? null : layout.range(0);
        try (InputStream stream = new FileInputStream(input.file)) {
            if (input.file.length() != input.size)
                throw new IOException("Grid source length changed");
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                EvidenceRows.check(stop);
                if (count > input.size - position) throw new IOException("Grid source grew");
                digest.update(buffer, 0, count);
                for (int i = 0; i < count; i++) {
                    int value = buffer[i] & 255;
                    if (mode == Mode.ENTROPY) histogram[value]++;
                    else sum += value;
                    position++;
                    if (position == range.offset + range.length) {
                        double result =
                                mode == Mode.ENTROPY
                                        ? Inspector.entropy(histogram, range.length)
                                        : (double) sum / range.length;
                        values[cell] = result;
                        pixels[cell] = color(result, mode);
                        if (mode == Mode.ENTROPY) Arrays.fill(histogram, 0);
                        sum = 0;
                        cell++;
                        range = cell < layout.used ? layout.range(cell) : null;
                    }
                }
            }
        }
        String sha = Inspector.toHex(digest.digest());
        if (position != input.size
                || input.file.length() != input.size
                || !sha.equals(input.sha256)) throw new IOException("Grid source identity changed");
        EvidenceRows.check(stop);
        return new Result(layout, mode, sha, values, pixels);
    }

    private static int color(double value, Mode mode) {
        if (mode == Mode.BYTES) {
            int c = (int) Math.round(value);
            return 0xff000000 | c << 16 | c << 8 | c;
        }
        double t = Math.max(0, Math.min(1, value / 8));
        return 0xff000000
                | (int) Math.round(t * 255) << 16
                | (int) Math.round(t * 235) << 8
                | (int) Math.round((1 - t) * 180 + 35);
    }

    static void writeReport(Result grid, String sourceName, Writer out, BooleanSupplier stop)
            throws Exception {
        org.json.JSONObject header =
                new org.json.JSONObject()
                        .put("schema", "org.diemobile.byte-grid")
                        .put("schemaVersion", 1)
                        .put("sourceName", sourceName)
                        .put("sha256", grid.sha256)
                        .put("sizeBytes", grid.layout.size)
                        .put("width", grid.layout.width)
                        .put("height", grid.layout.height)
                        .put("mode", grid.mode.name())
                        .put("legend", grid.mode.legend)
                        .put("populatedCells", grid.layout.used)
                        .put(
                                "mapping",
                                "Row-major; used=min(input size,cells); populated cell"
                                    + " i=[floor(i*size/used),floor((i+1)*size/used)); remaining"
                                    + " cells have no bytes")
                        .put("sampling", false)
                        .put("entropyIsFormatVerdict", false);
        String encoded = header.toString();
        out.write(encoded, 0, encoded.length() - 1);
        out.write(",\"cells\":[");
        for (int i = 0; i < grid.values.length; i++) {
            EvidenceRows.check(stop);
            if (i > 0) out.write(',');
            FileRange range = grid.layout.range(i);
            out.write(
                    new org.json.JSONObject()
                            .put("index", i)
                            .put(
                                    "physicalOffset",
                                    range.length == 0 ? org.json.JSONObject.NULL : range.offset)
                            .put("sizeBytes", range.length)
                            .put(
                                    "value",
                                    range.length == 0 ? org.json.JSONObject.NULL : grid.values[i])
                            .put(
                                    "valueIeee754Hex",
                                    range.length == 0
                                            ? org.json.JSONObject.NULL
                                            : Double.toHexString(grid.values[i]))
                            .toString());
        }
        out.write("]}");
    }

    private ByteGrid() {}
}

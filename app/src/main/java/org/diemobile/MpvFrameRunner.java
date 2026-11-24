// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Separate read-only input, fresh raster output and report transport for one bounded image. */
interface MpvFrameRunner extends AutoCloseable {
    int MAXIMUM_DIMENSION_PIXELS = 1536;
    long MAXIMUM_INPUT_BYTES = 256L * 1024 * 1024;

    static void requireMaximum(int maximumDimensionPixels) throws IOException {
        if (maximumDimensionPixels < 1 || maximumDimensionPixels > MAXIMUM_DIMENSION_PIXELS)
            throw new IOException("MPV image maximum dimension must be 1–1536 pixels");
    }

    String capture(File input, File output, int maximumDimensionPixels, BooleanSupplier stop)
            throws Exception;

    @Override
    default void close() throws Exception {}
}

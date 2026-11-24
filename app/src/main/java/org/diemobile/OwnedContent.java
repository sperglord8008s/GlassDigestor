// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.function.BooleanSupplier;

/** Exact stored-byte acquisition shared by galleries, recovery tools and previews. */
final class OwnedContent {
    static InputSnapshot stageRange(
            InputSnapshot source, FileRange range, File directory, BooleanSupplier stop)
            throws Exception {
        requireSource(source, range);
        source.checkIdentity(stop);
        directory = directory.getCanonicalFile();
        InputSnapshot staged = null;
        try {
            try (RandomAccessFile file = new RandomAccessFile(source.file, "r")) {
                file.seek(range.offset);
                InputStream slice =
                        new InputStream() {
                            long left = range.length;

                            @Override
                            public int read() throws IOException {
                                if (left == 0) return -1;
                                int value = file.read();
                                if (value < 0) throw new EOFException("Selected bytes shortened");
                                left--;
                                return value;
                            }

                            @Override
                            public int read(byte[] bytes, int offset, int length)
                                    throws IOException {
                                if (length == 0) return 0;
                                if (left == 0) return -1;
                                int count = file.read(bytes, offset, (int) Math.min(left, length));
                                if (count < 0) throw new EOFException("Selected bytes shortened");
                                left -= count;
                                return count;
                            }
                        };
                staged = InputSnapshot.acquire(slice, directory, range.length, stop);
            }
            source.checkIdentity(stop);
            InputSnapshot result = staged;
            staged = null;
            return result;
        } finally {
            if (staged != null) staged.close();
        }
    }

    /** Only generated, direct private handoffs are eligible for receiver cleanup. */
    static void discardHandoff(File input, File directory) {
        try {
            File canonical = input.getCanonicalFile();
            if (input.getAbsoluteFile().equals(canonical)
                    && canonical.getParentFile().equals(directory.getCanonicalFile())
                    && canonical.getName().matches("analysis-input-[A-Za-z0-9._-]+\\.snapshot"))
                canonical.delete();
        } catch (IOException ignored) {
            // A failed cleanup never authorizes deletion through a different path.
        }
    }

    static PreparedExport exportRange(
            InputSnapshot source,
            FileRange range,
            File directory,
            String name,
            BooleanSupplier stop)
            throws Exception {
        requireSource(source, range);
        source.checkIdentity(stop);
        PreparedExport staged = null;
        try {
            staged = PreparedExport.range(directory, name, source.file, range, stop);
            source.checkIdentity(stop);
            PreparedExport result = staged;
            staged = null;
            return result;
        } finally {
            if (staged != null) staged.discard();
        }
    }

    private static void requireSource(InputSnapshot source, FileRange range) throws IOException {
        if (source == null || range.fileSize != source.size)
            throw new IOException("Stored range belongs to another source");
        if (range.length > FileRange.MAX_OPERATION_BYTES)
            throw new IOException("Stored range exceeds the 256 MiB acquisition budget");
    }

    private OwnedContent() {}
}

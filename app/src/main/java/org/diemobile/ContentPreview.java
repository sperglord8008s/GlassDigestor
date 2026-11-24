// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.*;

/** Local bounded image decoding; unknown extents are never passed to a decoder. */
final class ContentPreview {
    /** Only a platform decoder refusal admits a different decoder; IO/policy failures do not. */
    static final class DecoderUnavailableException extends IOException {
        DecoderUnavailableException(String message) { super(message); }
        DecoderUnavailableException(String message, RuntimeException cause) { super(message, cause); }
    }
    static boolean looksLikeText(byte[] header) {
        return looksLikeText(header, true);
    }

    static boolean looksLikeText(byte[] header, boolean completeInput) {
        try {
            java.nio.CharBuffer output = java.nio.CharBuffer.allocate(header.length);
            java.nio.charset.CoderResult decoded =
                    java.nio.charset.StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(header), output, completeInput);
            if (decoded.isError()) return false;
            output.flip();
            String value = output.toString();
            if (value.isEmpty()) return false;
            boolean visible = false;
            for (int i = 0; i < value.length(); ) {
                int code = value.codePointAt(i);
                i += Character.charCount(code);
                if (Character.isISOControl(code) && code != '\n' && code != '\r' && code != '\t')
                    return false;
                visible |= !Character.isWhitespace(code);
            }
            return visible;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    static Bitmap image(File source, FileRange range, int target) throws IOException {
        if (target < 1 || target > 2048)
            throw new IOException("Image target must be 1–2048 pixels");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = stream(source, range)) {
            decode(stream, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0)
            throw new DecoderUnavailableException("Platform image decoder could not read these bytes");
        int sample = 1;
        while ((options.outWidth + (long) sample - 1) / sample > target
                || (options.outHeight + (long) sample - 1) / sample > target) {
            if (sample > (1 << 29))
                throw new IOException("Image dimensions exceed the sampling budget");
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream stream = stream(source, range)) {
            bitmap = decode(stream, options);
        }
        if (bitmap == null) throw new DecoderUnavailableException("Platform image decoder returned no image");
        if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0
                || bitmap.getWidth() > target || bitmap.getHeight() > target
                || (long) bitmap.getWidth() * bitmap.getHeight() > (long) target * target) {
            bitmap.recycle();
            throw new IOException("Decoded image exceeded its pixel budget");
        }
        return bitmap;
    }

    private static Bitmap decode(InputStream stream, BitmapFactory.Options options)
            throws DecoderUnavailableException {
        try { return BitmapFactory.decodeStream(stream, null, options); }
        catch (RuntimeException refusal) {
            if (refusal instanceof android.os.OperationCanceledException
                    || refusal instanceof SecurityException) throw refusal;
            throw new DecoderUnavailableException("Platform image decoder refused these bytes", refusal);
        }
    }

    static InputStream stream(File source, FileRange range) throws IOException {
        range.requireCurrentLength(source.length());
        FileInputStream input = new FileInputStream(source);
        boolean success = false;
        try {
            input.getChannel().position(range.offset);
            InputStream bounded =
                    new FilterInputStream(input) {
                        long remaining = range.length;

                        @Override
                        public int read() throws IOException {
                            if (remaining == 0) return -1;
                            int value = in.read();
                            if (value < 0) throw new EOFException("Preview source shortened");
                            remaining--;
                            return value;
                        }

                        @Override
                        public int read(byte[] bytes, int offset, int count) throws IOException {
                            if (count == 0) return 0;
                            if (remaining == 0) return -1;
                            int read = in.read(bytes, offset, (int) Math.min(count, remaining));
                            if (read < 0) throw new EOFException("Preview source shortened");
                            remaining -= read;
                            return read;
                        }

                        @Override
                        public long skip(long count) throws IOException {
                            long skipped = in.skip(Math.max(0, Math.min(count, remaining)));
                            remaining -= skipped;
                            return skipped;
                        }

                        @Override
                        public int available() throws IOException {
                            return (int) Math.min(remaining, in.available());
                        }
                    };
            success = true;
            return bounded;
        } finally {
            if (!success) input.close();
        }
    }

    private ContentPreview() {}
}

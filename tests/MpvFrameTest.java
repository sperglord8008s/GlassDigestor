// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

/** Real libmpv pixels compared with authored source pixels, not renderer-generated expectations. */
public final class MpvFrameTest {
    private static int sequence;

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static int fd(FileDescriptor descriptor) throws Exception {
        Field value = FileDescriptor.class.getDeclaredField("fd");
        value.setAccessible(true);
        return value.getInt(descriptor);
    }

    private static byte[] pixels(int width, int height, boolean black) {
        byte[] pixels = new byte[width * height * 3];
        if (!black)
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    pixels[(y * width + x) * 3 + (x < width / 2 ? 0 : 1)] = (byte) 255;
        return pixels;
    }

    private static byte[] ppm(int width, int height, boolean black) throws Exception {
        byte[] header =
                ("P6\n" + width + " " + height + "\n255\n")
                        .getBytes(StandardCharsets.US_ASCII);
        byte[] pixels = pixels(width, height, black);
        byte[] bytes = Arrays.copyOf(header, header.length + pixels.length);
        System.arraycopy(pixels, 0, bytes, header.length, pixels.length);
        return bytes;
    }

    private static byte[] tiff() {
        int width = 64, height = 48, pixelOffset = 140;
        byte[] pixels = pixels(width, height, false);
        ByteBuffer bytes =
                ByteBuffer.allocate(pixelOffset + pixels.length).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8).putShort((short) 10);
        int[][] tags = {
            {256, 4, 1, width}, {257, 4, 1, height}, {258, 3, 3, 134},
            {259, 3, 1, 1}, {262, 3, 1, 2}, {273, 4, 1, pixelOffset},
            {277, 3, 1, 3}, {278, 4, 1, height}, {279, 4, 1, pixels.length}, {284, 3, 1, 1}
        };
        for (int[] tag : tags)
            bytes.putShort((short) tag[0]).putShort((short) tag[1]).putInt(tag[2]).putInt(tag[3]);
        bytes.putInt(0).putShort((short) 8).putShort((short) 8).putShort((short) 8).put(pixels);
        return bytes.array();
    }

    private static JSONObject capture(File input, File output, int target) throws Exception {
        byte[] original = Files.readAllBytes(input.toPath());
        try (FileInputStream source = new FileInputStream(input);
                RandomAccessFile destination = new RandomAccessFile(output, "rw")) {
            source.getChannel().position(5);
            destination.seek(11);
            JSONObject report =
                    new JSONObject(
                            MpvEngine.captureFd(fd(source.getFD()), fd(destination.getFD()), target));
            require(source.getChannel().position() == 5, "Native code moved the borrowed source");
            require(destination.getFilePointer() == 11, "Native code moved the borrowed output");
            require(Arrays.equals(original, Files.readAllBytes(input.toPath())), "Source changed");
            return report;
        }
    }

    private static File next(File root) {
        return new File(root, "mpv-frame-" + sequence++ + ".raw");
    }

    private static void valid(
            File root, byte[] source, int target, int width, int height, boolean black)
            throws Exception {
        File input = next(root), output = next(root);
        Files.write(input.toPath(), source);
        JSONObject report = capture(input, output, target);
        require(report.getBoolean("ok"), "Actual frame capture failed: " + report);
        require(report.getString("schema").equals("org.diemobile.mpv-frame"), "Wrong schema");
        require(report.getInt("widthPixels") == width && report.getInt("heightPixels") == height,
                "Raster geometry differs: " + report);
        require(report.getInt("rowBytes") == width * 4, "Output padding leaked");
        require(report.getLong("pixelBytes") == (long) width * height * 4, "Wrong byte count");
        require(report.getBoolean("decodedCurrentFrameConfirmed"), "No decoded-image observation");
        require(!report.getBoolean("sourceAlphaPreserved"), "Opaque bgr0 was called source alpha");
        require(!report.getBoolean("completeFileValidityVerified")
                        && !report.getBoolean("everyFrameRendered")
                        && !report.getBoolean("frameTimestampVerified"), "Invented verification");
        require(report.getString("engineBinaryOrigin").equals("external-host-runtime"),
                "Host library was described as Android execution");
        require(!report.getBoolean("referenceAccessEnabled") && !report.getBoolean("scriptsEnabled")
                        && !report.getBoolean("sidecarLoadingEnabled")
                        && !report.getBoolean("networkPermissionRequired"), "Input guards changed");
        require(report.getLong("bytesRead") > 0
                        && report.getLong("bytesRead") <= report.getLong("maxReadBytes"),
                "Read coverage/budget is inconsistent");
        byte[] rgba = Files.readAllBytes(output.toPath());
        require(rgba.length == width * height * 4, "Raster length differs");
        for (int i = 3; i < rgba.length; i += 4)
            require((rgba[i] & 255) == 255, "Undefined native alpha escaped");
        for (int x : new int[] {width / 4, width * 3 / 4}) {
            int start = (height / 2 * width + x) * 4;
            int red = rgba[start] & 255, green = rgba[start + 1] & 255, blue = rgba[start + 2] & 255;
            require(blue == 0 && (black ? red == 0 && green == 0
                            : x < width / 2 ? red == 255 && green == 0 : red == 0 && green == 255),
                    "Decoded RGBA differs from independent source pixels");
        }
        if (target >= 64 && !black)
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int i = (y * width + x) * 4;
                    require((rgba[i] & 255) == (x < width / 2 ? 255 : 0)
                                    && (rgba[i + 1] & 255) == (x < width / 2 ? 0 : 255)
                                    && rgba[i + 2] == 0, "Full raster source oracle differs");
                }
        require(MessageDigest.isEqual(
                        MessageDigest.getInstance("SHA-256").digest(source),
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input.toPath()))),
                "Input identity changed after successful output");
    }

    public static void main(String[] arguments) throws Exception {
        File root = new File(arguments[0]);
        require(MpvEngine.AVAILABLE, "Real MPV JNI unavailable: " + MpvEngine.ERROR);
        valid(root, tiff(), 1536, 64, 48, false);
        valid(root, ppm(64, 48, false), 24, 24, 18, false);
        valid(root, ppm(64, 48, true), 128, 64, 48, true);
        valid(root, ppm(1, 1, true), 24, 1, 1, true);

        File input = next(root), output = next(root);
        Files.write(input.toPath(), ppm(64, 48, false));
        for (int target : new int[] {-1, 0, 1537, Integer.MAX_VALUE}) {
            JSONObject refused = capture(input, output, target);
            require(!refused.getBoolean("ok") && output.length() == 0, "Target refusal wrote bytes");
        }
        byte[] saved = "existing output".getBytes(StandardCharsets.US_ASCII);
        Files.write(output.toPath(), saved);
        require(!capture(input, output, 24).getBoolean("ok")
                        && Arrays.equals(saved, Files.readAllBytes(output.toPath())),
                "Nonempty output refusal replaced prior bytes");
        require(!capture(input, input, 24).getBoolean("ok"), "Source alias was accepted");
        try (FileInputStream source = new FileInputStream(input);
                FileInputStream readOnly = new FileInputStream(output);
                RandomAccessFile writableInput = new RandomAccessFile(input, "rw");
                RandomAccessFile empty = new RandomAccessFile(next(root), "rw");
                FileInputStream device = new FileInputStream("/dev/null")) {
            require(!new JSONObject(MpvEngine.captureFd(fd(source.getFD()), fd(readOnly.getFD()), 24))
                            .getBoolean("ok"), "Read-only output accepted");
            require(!new JSONObject(MpvEngine.captureFd(fd(writableInput.getFD()), fd(empty.getFD()), 24))
                            .getBoolean("ok"), "Writable source descriptor accepted");
            require(!new JSONObject(MpvEngine.captureFd(fd(device.getFD()), fd(empty.getFD()), 24))
                            .getBoolean("ok"), "Non-regular input accepted");
            require(!new JSONObject(MpvEngine.captureFd(-1, fd(empty.getFD()), 24)).getBoolean("ok")
                            && !new JSONObject(MpvEngine.captureFd(fd(source.getFD()), -1, 24))
                                    .getBoolean("ok"), "Invalid descriptors accepted");
            require(empty.length() == 0 && Arrays.equals(saved, Files.readAllBytes(output.toPath())),
                    "Descriptor refusals damaged output");
        }
        Files.write(input.toPath(), "This is not an image or video".getBytes(StandardCharsets.UTF_8));
        File failedOutput = next(root);
        require(!capture(input, failedOutput, 24).getBoolean("ok") && failedOutput.length() == 0,
                "Unsupported data returned partial pixels");
        Files.write(input.toPath(), new byte[0]);
        require(!capture(input, failedOutput, 24).getBoolean("ok"), "Empty input accepted");
        System.out.println("MPV decoded-frame JNI passed: TIFF/PPM, exact RGBA/opaque alpha, black/1px, bounded scaling, source/output identities and positions, descriptor/alias/invalid/partial-output refusals.");
    }
}

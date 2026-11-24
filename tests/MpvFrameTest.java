// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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
                ("P6\n" + width + " " + height + "\n255\n").getBytes(StandardCharsets.US_ASCII);
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
            {256, 4, 1, width},
            {257, 4, 1, height},
            {258, 3, 3, 134},
            {259, 3, 1, 1},
            {262, 3, 1, 2},
            {273, 4, 1, pixelOffset},
            {277, 3, 1, 3},
            {278, 4, 1, height},
            {279, 4, 1, pixels.length},
            {284, 3, 1, 1}
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
                            MpvEngine.captureFd(
                                    fd(source.getFD()), fd(destination.getFD()), target));
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
        verify(input, output, report, target, width, height, black, target >= 64 && !black);
        require(
                MessageDigest.isEqual(
                        MessageDigest.getInstance("SHA-256").digest(source),
                        MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(input.toPath()))),
                "Input identity changed after successful output");
    }

    private static void verify(
            File input,
            File output,
            JSONObject report,
            int target,
            int width,
            int height,
            boolean black,
            boolean exactPixels)
            throws Exception {
        require(report.getBoolean("ok"), "Actual frame capture failed: " + report);
        require(report.getString("schema").equals("org.diemobile.mpv-frame"), "Wrong schema");
        require(
                report.getInt("widthPixels") == width && report.getInt("heightPixels") == height,
                "Raster geometry differs: " + report);
        require(report.getInt("rowBytes") == width * 4, "Output padding leaked");
        require(report.getLong("pixelBytes") == (long) width * height * 4, "Wrong byte count");
        require(report.getBoolean("decodedCurrentFrameConfirmed"), "No decoded-image observation");
        require(!report.getBoolean("sourceAlphaPreserved"), "Opaque bgr0 was called source alpha");
        require(
                !report.getBoolean("completeFileValidityVerified")
                        && !report.getBoolean("everyFrameRendered")
                        && !report.getBoolean("frameTimestampVerified"),
                "Invented verification");
        require(
                report.getString("engineBinaryOrigin").equals("external-host-runtime"),
                "Host library was described as Android execution");
        require(
                !report.getBoolean("referenceAccessEnabled")
                        && !report.getBoolean("scriptsEnabled")
                        && !report.getBoolean("sidecarLoadingEnabled")
                        && !report.getBoolean("networkPermissionRequired"),
                "Input guards changed");
        require(
                report.getLong("bytesRead") > 0
                        && report.getLong("bytesRead") <= report.getLong("maxReadBytes"),
                "Read coverage/budget is inconsistent");
        require(
                report.getInt("maximumDimensionPixels") == target
                        && report.getBoolean("displayGeometryPreflightPassed")
                        && report.getBoolean("squarePixelRaster"),
                "Requested maximum edge or square-pixel preallocation guard lost");
        byte[] rgba = Files.readAllBytes(output.toPath());
        require(rgba.length == width * height * 4, "Raster length differs");
        for (int i = 3; i < rgba.length; i += 4)
            require((rgba[i] & 255) == 255, "Undefined native alpha escaped");
        for (int x : new int[] {width / 4, width * 3 / 4}) {
            int start = (height / 2 * width + x) * 4;
            int red = rgba[start] & 255,
                    green = rgba[start + 1] & 255,
                    blue = rgba[start + 2] & 255;
            require(
                    blue == 0
                            && (black
                                    ? red == 0 && green == 0
                                    : x < width / 2
                                            ? red == 255 && green == 0
                                            : red == 0 && green == 255),
                    "Decoded RGBA differs from independent source pixels");
        }
        if (exactPixels)
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int i = (y * width + x) * 4;
                    require(
                            (rgba[i] & 255) == (x < width / 2 ? 255 : 0)
                                    && (rgba[i + 1] & 255) == (x < width / 2 ? 0 : 255)
                                    && rgba[i + 2] == 0,
                            "Full raster source oracle differs");
                }
        MpvFrameReport model =
                new MpvFrameReport(report.toString(), input.length(), target, () -> false);
        MpvFrameReport.Raster raster = model.readRaster(output, () -> false);
        require(
                raster.argb.length == width * height
                        && raster.sha256.equals(
                                Inspector.toHex(MessageDigest.getInstance("SHA-256").digest(rgba))),
                "Typed frame/raster ownership or raw pixel SHA-256 differs");
        for (int i = 0; i < raster.argb.length; i++) {
            int expected =
                    0xff000000
                            | (rgba[i * 4] & 255) << 16
                            | (rgba[i * 4 + 1] & 255) << 8
                            | (rgba[i * 4 + 2] & 255);
            require(raster.argb[i] == expected, "Typed RGBA-to-ARGB channel mapping differs");
            if (exactPixels)
                require(
                        raster.argb[i] == (i % width < width / 2 ? 0xffff0000 : 0xff00ff00),
                        "Typed video/image pixels differ from the authored color oracle");
        }
    }

    private static JSONObject property(JSONObject report, String name) throws Exception {
        var properties = report.getJSONArray("properties");
        for (int i = 0; i < properties.length(); i++)
            if (properties.getJSONObject(i).getString("name").equals(name))
                return properties.getJSONObject(i).getJSONObject("node");
        throw new AssertionError("Missing actual MPV property " + name);
    }

    private static JSONObject field(JSONObject node, String key) throws Exception {
        var children = node.getJSONArray("children");
        for (int i = 0; i < children.length(); i++) {
            JSONObject entry = children.getJSONObject(i);
            if (entry.getString("key").equals(key)) return entry.getJSONObject("node");
        }
        throw new AssertionError("Missing actual MPV map field " + key);
    }

    private static void video(
            File root,
            String sampleAspect,
            double expectedAspect,
            int target,
            int width,
            int height,
            boolean exactPixels)
            throws Exception {
        File authored = next(root),
                encoded = next(root),
                diagnostics = next(root),
                output = next(root);
        try (OutputStream bytes = Files.newOutputStream(authored.toPath())) {
            // Identical authored frames permit exact pixel checks without claiming a timestamp.
            for (int i = 0; i < 3; i++) bytes.write(pixels(64, 48, false));
        }
        Process process =
                new ProcessBuilder(
                                "/usr/bin/ffmpeg",
                                "-v",
                                "error",
                                "-nostdin",
                                "-y",
                                "-f",
                                "rawvideo",
                                "-pixel_format",
                                "rgb24",
                                "-video_size",
                                "64x48",
                                "-framerate",
                                "2",
                                "-i",
                                authored.getAbsolutePath(),
                                "-frames:v",
                                "3",
                                "-vf",
                                "setsar=" + sampleAspect + ":max=1000000",
                                "-c:v",
                                "ffv1",
                                "-level",
                                "3",
                                "-pix_fmt",
                                "bgr0",
                                "-an",
                                "-f",
                                "matroska",
                                encoded.getAbsolutePath())
                        .redirectErrorStream(true)
                        .redirectOutput(diagnostics)
                        .start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Owned FFV1 fixture generation timed out");
        }
        require(process.exitValue() == 0, Files.readString(diagnostics.toPath()));
        JSONObject report = capture(encoded, output, target);
        verify(encoded, output, report, target, width, height, false, exactPixels);
        require(
                property(report, "file-format").getString("value").contains("matroska"),
                "Real FFV1 video did not use its generated container");
        require(
                field(
                                property(report, "track-list")
                                        .getJSONArray("children")
                                        .getJSONObject(0),
                                "codec")
                        .getString("value")
                        .equals("ffv1"),
                "Actual video codec was not FFV1");
        double observedAspect = field(property(report, "video-params"), "par").getDouble("value");
        require(
                Math.abs(observedAspect - expectedAspect) < 0.000001,
                "Anamorphic source aspect differs: expected "
                        + expectedAspect
                        + ", observed "
                        + observedAspect);
        require(
                field(property(report, "video-out-params"), "par").getDouble("value") == 1.0,
                "Display normalization happened after the potentially large screenshot allocation");
        authored.delete();
        diagnostics.delete();
    }

    public static void main(String[] arguments) throws Exception {
        File root = new File(arguments[0]);
        require(MpvEngine.AVAILABLE, "Real MPV JNI unavailable: " + MpvEngine.ERROR);
        valid(root, tiff(), 1536, 64, 48, false);
        valid(root, ppm(64, 48, false), 24, 24, 18, false);
        valid(root, ppm(64, 48, true), 128, 64, 48, true);
        valid(root, ppm(1, 1, true), 24, 1, 1, true);
        video(root, "1/1", 1, 128, 64, 48, true);
        video(root, "2/1", 2, 64, 64, 24, false);
        video(root, "1/2", 0.5, 64, 32, 48, false);
        // This ratio remains exact in Matroska's bounded display-ratio fields.
        // Without display normalization, the tiny coded image expands past 100 MiB.
        video(root, "10000/1", 10000, 64, 64, 1, false);

        File input = next(root), output = next(root);
        Files.write(input.toPath(), ppm(64, 48, false));
        for (int target : new int[] {-1, 0, 1537, Integer.MAX_VALUE}) {
            JSONObject refused = capture(input, output, target);
            require(
                    !refused.getBoolean("ok") && output.length() == 0,
                    "Target refusal wrote bytes");
        }
        byte[] saved = "existing output".getBytes(StandardCharsets.US_ASCII);
        Files.write(output.toPath(), saved);
        require(
                !capture(input, output, 24).getBoolean("ok")
                        && Arrays.equals(saved, Files.readAllBytes(output.toPath())),
                "Nonempty output refusal replaced prior bytes");
        require(!capture(input, input, 24).getBoolean("ok"), "Source alias was accepted");
        try (FileInputStream source = new FileInputStream(input);
                FileOutputStream append = new FileOutputStream(output, true)) {
            JSONObject refused =
                    new JSONObject(MpvEngine.captureFd(fd(source.getFD()), fd(append.getFD()), 24));
            require(
                    !refused.getBoolean("ok")
                            && refused.getString("error").contains("non-append")
                            && Arrays.equals(saved, Files.readAllBytes(output.toPath())),
                    "Append output was accepted or modified");
        }
        try (FileInputStream source = new FileInputStream(input);
                FileInputStream readOnly = new FileInputStream(output);
                RandomAccessFile writableInput = new RandomAccessFile(input, "rw");
                RandomAccessFile empty = new RandomAccessFile(next(root), "rw");
                FileInputStream device = new FileInputStream("/dev/null")) {
            require(
                    !new JSONObject(
                                    MpvEngine.captureFd(
                                            fd(source.getFD()), fd(readOnly.getFD()), 24))
                            .getBoolean("ok"),
                    "Read-only output accepted");
            require(
                    !new JSONObject(
                                    MpvEngine.captureFd(
                                            fd(writableInput.getFD()), fd(empty.getFD()), 24))
                            .getBoolean("ok"),
                    "Writable source descriptor accepted");
            require(
                    !new JSONObject(MpvEngine.captureFd(fd(device.getFD()), fd(empty.getFD()), 24))
                            .getBoolean("ok"),
                    "Non-regular input accepted");
            require(
                    !new JSONObject(MpvEngine.captureFd(-1, fd(empty.getFD()), 24)).getBoolean("ok")
                            && !new JSONObject(MpvEngine.captureFd(fd(source.getFD()), -1, 24))
                                    .getBoolean("ok"),
                    "Invalid descriptors accepted");
            require(
                    empty.length() == 0
                            && Arrays.equals(saved, Files.readAllBytes(output.toPath())),
                    "Descriptor refusals damaged output");
        }
        Files.write(
                input.toPath(), "This is not an image or video".getBytes(StandardCharsets.UTF_8));
        File failedOutput = next(root);
        long failedAt = System.nanoTime();
        require(
                !capture(input, failedOutput, 24).getBoolean("ok") && failedOutput.length() == 0,
                "Unsupported data returned partial pixels");
        require(
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - failedAt) < 10,
                "Immediate load failure waited for the entire native deadline");
        Files.write(input.toPath(), MetadataFixtureFactory.wav());
        require(
                !capture(input, failedOutput, 24).getBoolean("ok") && failedOutput.length() == 0,
                "Audio-only data returned an invented video frame");
        Files.write(input.toPath(), new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 0, 0});
        require(
                !capture(input, failedOutput, 24).getBoolean("ok") && failedOutput.length() == 0,
                "Malformed Matroska returned a partial raster");
        Files.write(input.toPath(), new byte[0]);
        require(!capture(input, failedOutput, 24).getBoolean("ok"), "Empty input accepted");
        try (RandomAccessFile oversized = new RandomAccessFile(input, "rw");
                RandomAccessFile pixels = new RandomAccessFile(failedOutput, "rw")) {
            oversized.setLength(256L * 1024 * 1024 + 1);
            try (FileInputStream source = new FileInputStream(input)) {
                require(
                        !new JSONObject(
                                                MpvEngine.captureFd(
                                                        fd(source.getFD()), fd(pixels.getFD()), 24))
                                        .getBoolean("ok")
                                && pixels.length() == 0,
                        "Oversized sparse input bypassed the pre-acquisition limit");
            }
        }
        input.delete();
        System.out.println(
                "MPV decoded-frame JNI passed: authored FFV1/Matroska, TIFF/PPM, exact"
                    + " RGBA/ARGB/opaque alpha, black/1px, square/anamorphic/extreme-SAR bounded"
                    + " geometry, captured source/guards/nodes,"
                    + " descriptor/append/alias/oversize/malformed/partial-output refusals.");
    }
}

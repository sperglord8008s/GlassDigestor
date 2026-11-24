// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Authored model/raster oracles; no Android runtime or native image decoder is involved. */
public final class MpvFrameReportTest {
    private static int checks;

    private interface Mutation {
        void apply(JSONObject report) throws Exception;
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void refused(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Malformed MPV image scope or raster was accepted");
        } catch (Exception expected) {
            checks++;
        }
    }

    public static void main(String[] arguments) throws Exception {
        File directory =
                Files.createTempDirectory(new File(arguments[0]).toPath(), "frame-model-").toFile();
        File raster = new File(directory, "pixels.rgba");
        try {
            JSONObject original = MpvFrameFixture.report(103, 24);
            MpvFrameReport model = new MpvFrameReport(original.toString(), 103, 24, () -> false);
            Files.write(raster.toPath(), MpvFrameFixture.RGBA);
            MpvFrameReport.Raster pixels = model.readRaster(raster, () -> false);
            require(
                    Arrays.equals(MpvFrameFixture.ARGB, pixels.argb),
                    "RGBA channels changed in Bitmap integer colors");
            require(
                    pixels.sha256.equals(
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(MpvFrameFixture.RGBA))),
                    "Pixel digest differs from the authored raw-byte oracle");
            require(
                    model.json().similar(original),
                    "Native report discarded raw fields or unknowns");
            model.json().getJSONArray("unknownRaw").put(0, "changed");
            require(
                    model.json().getJSONArray("unknownRaw").getString(0).equals("retained"),
                    "Native report was not detached");
            byte[] changed = MpvFrameFixture.RGBA.clone();
            changed[0] ^= 1;
            Files.write(raster.toPath(), changed);
            refused(() -> model.verifyRaster(raster, pixels.sha256, () -> false));
            changed[3] = 0;
            Files.write(raster.toPath(), changed);
            refused(() -> model.readRaster(raster, () -> false));
            Files.write(raster.toPath(), Arrays.copyOf(MpvFrameFixture.RGBA, 15));
            refused(() -> model.readRaster(raster, () -> false));
            Files.write(raster.toPath(), Arrays.copyOf(MpvFrameFixture.RGBA, 17));
            refused(() -> model.readRaster(raster, () -> false));
            Files.write(raster.toPath(), MpvFrameFixture.RGBA);
            try {
                model.readRaster(raster, () -> true);
                throw new AssertionError("Raster cancellation ignored");
            } catch (InterruptedIOException expected) {
                checks++;
            }

            List<Mutation> mutations =
                    List.of(
                            x -> x.put("schemaVersion", 2),
                            x -> x.put("adapterVersion", 2),
                            x -> x.put("headersSourceRevision", "foreign"),
                            x -> x.put("headersApiVersion", 131076),
                            x -> x.put("runtimeApiVersion", 196608),
                            x -> x.put("engineBinaryOrigin", "unknown"),
                            x -> x.put("engineSourceRevision", "invented-host-revision"),
                            x -> x.put("fileSizeBytes", 104),
                            x -> x.put("maximumDimensionPixels", 1536),
                            x -> x.put("widthPixels", 24.5),
                            x -> x.put("heightPixels", "2"),
                            x -> x.put("rowBytes", 9),
                            x -> x.put("pixelBytes", 15),
                            x -> x.put("pixelFormat", "bgra8"),
                            x -> x.put("alphaMode", "source-alpha"),
                            x -> x.put("sourceAlphaPreserved", true),
                            x -> x.put("squarePixelRaster", false),
                            x -> x.put("displayGeometryPreflightPassed", false),
                            x -> x.put("decodedCurrentFrameConfirmed", false),
                            x -> x.put("everyFrameRendered", true),
                            x -> x.put("frameTimestampVerified", true),
                            x -> x.put("completeFileValidityVerified", true),
                            x -> x.put("scriptsEnabled", true),
                            x -> x.put("networkPermissionRequired", true),
                            x -> x.put("nativeDeadlineMs", 45000),
                            x -> x.put("maxReadBytes", 33554433),
                            x -> x.put("bytesRead", 33554433),
                            x -> x.put("bytesRead", -1),
                            x -> x.put("ungrantedCallbackUris", 1),
                            x -> x.put("streamOpenCalls", 0),
                            x -> x.put("maxPixelBytes", 9437185),
                            x -> x.put("maxNativeRasterBytes", 16777217),
                            x -> x.put("maxNodes", 100001),
                            x -> x.put("nodeCount", 2),
                            x -> x.put("propertyCount", 6),
                            x -> x.put("maxOutputBytes", 33554433),
                            x ->
                                    x.getJSONArray("properties")
                                            .getJSONObject(1)
                                            .put("name", "mpv-version"),
                            x -> x.getJSONArray("properties").getJSONObject(0).put("errorCode", 0),
                            x ->
                                    x.getJSONArray("properties")
                                            .getJSONObject(2)
                                            .getJSONObject("node")
                                            .put("format", 9),
                            x -> x.getJSONArray("options").getJSONObject(0).put("value", "yes"),
                            x -> x.getJSONArray("options").getJSONObject(1).put("name", "config"),
                            x ->
                                    x.getJSONArray("options")
                                            .getJSONObject(26)
                                            .put("value", "a different filter"));
            for (Mutation mutation : mutations) {
                JSONObject forged = new JSONObject(original.toString());
                mutation.apply(forged);
                refused(() -> new MpvFrameReport(forged.toString(), 103, 24, () -> false));
            }
            JSONObject edge =
                    MpvFrameFixture.report(103, 4)
                            .put("widthPixels", 5)
                            .put("heightPixels", 1)
                            .put("rowBytes", 20)
                            .put("pixelBytes", 20);
            refused(() -> new MpvFrameReport(edge.toString(), 103, 4, () -> false));
            JSONObject maximum =
                    MpvFrameFixture.report(103, 1536)
                            .put("widthPixels", 1536)
                            .put("heightPixels", 1536)
                            .put("rowBytes", 6144)
                            .put("pixelBytes", 9437184);
            require(
                    new MpvFrameReport(maximum.toString(), 103, 1536, () -> false).pixelBytes
                            == 9437184,
                    "Valid maximum edge was treated as an area budget");
            for (int limit : new int[] {-1, 0, 1537, Integer.MAX_VALUE})
                refused(() -> new MpvFrameReport(original.toString(), 103, limit, () -> false));
            refused(() -> new MpvFrameReport(original.toString(), 0, 24, () -> false));
            refused(() -> new MpvFrameReport(original.toString(), 268435457, 24, () -> false));
            refused(
                    () ->
                            new MpvFrameReport(
                                    "{\"ok\":false,\"error\":\"unavailable codec\"}",
                                    103,
                                    24,
                                    () -> false));
            JSONObject duplicates = new JSONObject(original.toString());
            JSONObject value =
                    new JSONObject()
                            .put("type", "map")
                            .put("format", 8)
                            .put("children", new JSONArray().put(entry(1)).put(entry(2)));
            duplicates.getJSONArray("properties").getJSONObject(2).put("node", value);
            duplicates.put("nodeCount", 3);
            MpvFrameReport preserved =
                    new MpvFrameReport(duplicates.toString(), 103, 24, () -> false);
            require(
                    preserved
                                    .json()
                                    .getJSONArray("properties")
                                    .getJSONObject(2)
                                    .getJSONObject("node")
                                    .getJSONArray("children")
                                    .length()
                            == 2,
                    "Duplicate MPV map entries were flattened");
            System.out.println(
                    "MPV frame report: " + checks + " independent pixel/scope/refusal checks pass");
        } finally {
            Files.deleteIfExists(raster.toPath());
            Files.deleteIfExists(directory.toPath());
        }
    }

    private static JSONObject entry(int value) throws Exception {
        return new JSONObject()
                .put("key", "same")
                .put("keyUtf8Hex", "73616d65")
                .put(
                        "node",
                        new JSONObject()
                                .put("type", "int64")
                                .put("format", 4)
                                .put("value", value)
                                .put("decimal", Integer.toString(value)));
    }
}

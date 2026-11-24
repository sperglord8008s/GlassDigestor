// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.JSONArray;
import org.json.JSONObject;

/** Authored transport data and exact pixel bytes, independent of Android/native rendering. */
final class MpvFrameFixture {
    static final byte[] RGBA = {
        (byte) 0xff,
        0,
        0,
        (byte) 0xff,
        0,
        (byte) 0xff,
        0,
        (byte) 0xff,
        0x11,
        0x22,
        0x33,
        (byte) 0xff,
        0,
        0,
        0,
        (byte) 0xff
    };
    static final int[] ARGB = {0xffff0000, 0xff00ff00, 0xff112233, 0xff000000};

    static JSONObject report(long size, int maximum) throws Exception {
        JSONObject report =
                new JSONObject()
                        .put("ok", true)
                        .put("schema", "org.diemobile.mpv-frame")
                        .put("schemaVersion", 1)
                        .put("engine", "libmpv")
                        .put("adapterVersion", 1)
                        .put("headersSourceRevision", "0b7ed670f7c353dd3dd4f8ae0fc788a181a15aa6")
                        .put("headersApiVersion", 131077)
                        .put("runtimeApiVersion", 131075)
                        .put("engineBinaryOrigin", "external-host-runtime")
                        .put("engineSourceRevision", JSONObject.NULL)
                        .put("fileSizeBytes", size)
                        .put("maximumDimensionPixels", maximum)
                        .put("widthPixels", 2)
                        .put("heightPixels", 2)
                        .put("rowBytes", 8)
                        .put("pixelBytes", 16)
                        .put("pixelFormat", "rgba8")
                        .put("alphaMode", "opaque")
                        .put("nativeScreenshotFormat", "bgr0")
                        .put("audioVideoOutput", "null/paused")
                        .put("maxInputBytes", 268435456)
                        .put("maxReadBytes", 33554432)
                        .put("nativeDeadlineMs", 25000)
                        .put("maxNativeRasterBytes", 16777216)
                        .put("maxPixelBytes", 9437184)
                        .put("maxOutputBytes", 33554432)
                        .put("maxNodes", 100000)
                        .put("nodeCount", 1)
                        .put("bytesRead", Math.min(size, 20))
                        .put("seekCalls", 1)
                        .put("streamOpenCalls", 1)
                        .put("ungrantedCallbackUris", 0);
        for (String name :
                new String[] {
                    "referenceAccessEnabled",
                    "configEnabled",
                    "scriptsEnabled",
                    "sidecarLoadingEnabled",
                    "networkPermissionRequired",
                    "sourceAlphaPreserved",
                    "everyFrameRendered",
                    "completeFileValidityVerified",
                    "frameTimestampVerified"
                }) report.put(name, false);
        for (String name :
                new String[] {
                    "decodedCurrentFrameConfirmed",
                    "displayGeometryPreflightPassed",
                    "squarePixelRaster"
                }) report.put(name, true);
        JSONArray properties = new JSONArray();
        for (String name :
                new String[] {
                    "mpv-version",
                    "ffmpeg-version",
                    "file-format",
                    "video-params",
                    "video-out-params",
                    "video-frame-info",
                    "track-list"
                }) {
            JSONObject property = new JSONObject().put("name", name);
            if (name.equals("file-format"))
                property.put("errorCode", 0)
                        .put(
                                "node",
                                new JSONObject()
                                        .put("format", 1)
                                        .put("type", "string")
                                        .put("value", "pnm")
                                        .put("utf8Hex", "706e6d")
                                        .put("byteLength", 3));
            else property.put("errorCode", -10).put("node", JSONObject.NULL);
            properties.put(property);
        }
        report.put("properties", properties).put("propertyCount", 7);
        String filter = "lavfi=[scale=2:2,setsar=1]";
        JSONArray options = new JSONArray();
        String[][] guards = {
            {"config", "no"},
            {"load-scripts", "no"},
            {"load-auto-profiles", "no"},
            {"autoload-files", "no"},
            {"access-references", "no"},
            {"ytdl", "no"},
            {"osc", "no"},
            {"input-default-bindings", "no"},
            {"input-vo-keyboard", "no"},
            {"input-terminal", "no"},
            {"terminal", "no"},
            {"hwdec", "no"},
            {"vo", "null"},
            {"ao", "null"},
            {"pause", "yes"},
            {"idle", "yes"},
            {"keep-open", "always"},
            {"sid", "no"},
            {"cache", "no"},
            {"demuxer", "+lavf"},
            {"demuxer-lavf-o", "protocol_whitelist=dieblob"},
            {"demuxer-max-bytes", "16MiB"},
            {"demuxer-max-back-bytes", "0"},
            {"demuxer-readahead-secs", "0"},
            {"demuxer-lavf-probesize", "8388608"},
            {"demuxer-lavf-analyzeduration", "10"},
            {"vf", filter},
            {"screenshot-sw", "yes"}
        };
        for (String[] guard : guards)
            options.put(new JSONObject().put("name", guard[0]).put("value", guard[1]));
        return report.put("filter", filter)
                .put("options", options)
                .put("unknownRaw", new JSONArray().put("retained").put(JSONObject.NULL));
    }

    private MpvFrameFixture() {}
}

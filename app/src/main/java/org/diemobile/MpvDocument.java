// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Validated detached libmpv nodes and an immutable source, independent of Android. */
final class MpvDocument implements AutoCloseable {
    static final long MAX_INPUT_BYTES = 256L * 1024 * 1024;
    static final String SOURCE = "0b7ed670f7c353dd3dd4f8ae0fc788a181a15aa6";
    final InputSnapshot snapshot;
    final String name;
    final JSONObject header, catalog;
    final JSONArray properties, diagnostics, options;
    private final MpvNodeValidator nodeValidator = new MpvNodeValidator();

    private MpvDocument(InputSnapshot snapshot, String name, JSONObject response, BooleanSupplier stop) throws Exception {
        this.snapshot = snapshot;
        this.name = name;
        if (!response.optBoolean("ok")) throw new IOException(response.optString("error","MPV returned no complete report"));
        if (!response.getString("schema").equals("org.diemobile.mpv-evidence") || response.getInt("schemaVersion") != 1
            || response.getInt("adapterVersion") != 1 || response.getLong("fileSizeBytes") != snapshot.size
            || !response.getString("headersSourceRevision").equals(SOURCE)) throw new IOException("MPV report schema/source/input mismatch");
        String origin = response.getString("engineBinaryOrigin");
        if (origin.equals("mpv-android-2026-09-17-arm64")) {
            if (!SOURCE.equals(response.getString("engineSourceRevision"))) throw new IOException("MPV Android engine source differs from its runtime lock");
        } else if (!origin.equals("external-host-runtime") || !response.isNull("engineSourceRevision")) throw new IOException("Unrecognized MPV runtime origin");
        for (String flag : Arrays.asList("referenceAccessEnabled","configEnabled","scriptsEnabled","sidecarLoadingEnabled","networkPermissionRequired"))
            if (response.getBoolean(flag)) throw new IOException("MPV scope guard is not active: "+flag);
        if (response.getLong("ungrantedCallbackUris") != 0 || response.getLong("bytesRead") < 0
            || response.getLong("bytesRead") > 32L*1024*1024) throw new IOException("MPV stream crossed its granted URI/read budget");
        properties = response.getJSONArray("properties");
        diagnostics = response.getJSONArray("diagnostics");
        options = response.getJSONArray("options");
        catalog = response.getJSONObject("propertyCatalog");
        if (properties.length() < 1 || properties.length() > 4096 || properties.length() != response.getInt("propertyCount")
            || diagnostics.length() > 1000 || options.length() > 1000) throw new IOException("MPV returned incomplete or over-budget property/diagnostic/option counts");
        validateNode(catalog,0,stop);
        if (!catalog.getString("type").equals("array") || catalog.getJSONArray("children").length() != response.getInt("catalogCount")) throw new IOException("MPV property catalog count/type mismatch");
        for (int i = 0; i < catalog.getJSONArray("children").length(); i++)
            if (!catalog.getJSONArray("children").getJSONObject(i).getString("type").equals("string")) throw new IOException("MPV catalog contains a non-string property name");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < properties.length(); i++) {
            EvidenceRows.check(stop);
            JSONObject property = properties.getJSONObject(i);
            if (!names.add(property.getString("name"))) throw new IOException("MPV returned a duplicate property capture");
            if (property.getString("status").equals("available")) {
                if (property.getInt("errorCode") != 0) throw new IOException("Available MPV property has an error status");
                validateNode(property.getJSONObject("node"),0,stop);
            } else if (!property.getString("status").equals("unavailable") || property.getInt("errorCode") >= 0 || !property.has("error") || property.has("node"))
                throw new IOException("MPV property has an ambiguous capture status");
        }
        if (nodeValidator.count() != response.getInt("nodeCount")) throw new IOException("MPV node capture is incomplete or inconsistent");
        response.remove("properties"); response.remove("diagnostics"); response.remove("options"); response.remove("propertyCatalog");
        header = response;
    }
    static MpvDocument read(File input, File directory, String name, BinaryRunner runner, BooleanSupplier stop) throws Exception {
        InputSnapshot snapshot = InputSnapshot.acquire(input,directory,MAX_INPUT_BYTES,stop);
        boolean success = false;
        try {
            String encoded = runner.scan(snapshot.file,stop);
            EvidenceRows.check(stop);
            if (encoded == null || encoded.length() > 32*1024*1024) throw new IOException("MPV worker returned no report or exceeded 32 Mi-characters");
            MpvDocument result = new MpvDocument(snapshot,name,new JSONObject(encoded),stop);
            success = true;
            return result;
        } finally { if (!success) snapshot.close(); }
    }
    private void validateNode(JSONObject node, int depth, BooleanSupplier stop) throws Exception {
        nodeValidator.validate(node, depth, stop);
    }
    static void validateHex(String encoded, int bytes, BooleanSupplier stop) throws Exception {
        if (bytes < 0 || bytes > 8*1024*1024 || encoded.length() != 2L*bytes) throw new IOException("MPV binary/string length mismatch");
        for (int i = 0; i < encoded.length(); i++) {
            if ((i & 4095) == 0) EvidenceRows.check(stop);
            char c = encoded.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) throw new IOException("MPV value is not canonical hexadecimal");
        }
    }
    JSONObject node(int property, JSONArray path) throws Exception {
        if (property < 0 || property >= properties.length() || path.length() > 64) throw new IOException("MPV node selector outside its scope");
        JSONObject selected = properties.getJSONObject(property).getJSONObject("node");
        for (int i = 0; i < path.length(); i++) {
            JSONArray children = selected.getJSONArray("children");
            int index = path.getInt(i);
            if (index < 0 || index >= children.length()) throw new IOException("MPV child selector outside its scope");
            JSONObject child = children.getJSONObject(index);
            selected = selected.getString("type").equals("map") ? child.getJSONObject("node") : child;
        }
        return selected;
    }
    JSONObject provenance() throws Exception {
        return new JSONObject(header.toString()).put("input",new JSONObject().put("name",name).put("sizeBytes",snapshot.size).put("sha256",snapshot.sha256))
            .put("scope","Actual libmpv file/stream metadata, engine capabilities and unavailable property information from an audited getter set. Full property names are cataloged without invoking unrelated device/system/clipboard getters.")
            .put("interpretation","Container/codec facts are library observations, not proof of authenticity, file safety, complete decoding or playback support on this device. Some properties are runtime state rather than stored metadata; initialization can decode initial media packets into null outputs.")
            .put("locationPolicy","MPV nodes supply decoded values, not physical file offsets. No tag, stream, chapter or attachment offset is inferred.")
            .put("stringPolicy","Values/keys retain exact engine-supplied UTF-8 hex. Invalid UTF-8 is displayed with replacements; libmpv C strings cannot expose bytes beyond an embedded NUL. These bytes are decoded engine values, not original stored file ranges.");
    }
    static byte[] decodeHex(String encoded, BooleanSupplier stop) throws Exception {
        validateHex(encoded,encoded.length()/2,stop);
        byte[] bytes = new byte[encoded.length()/2];
        for (int i = 0; i < bytes.length; i++) {
            if ((i & 4095) == 0) EvidenceRows.check(stop);
            bytes[i] = (byte)((Character.digit(encoded.charAt(i*2),16)<<4) | Character.digit(encoded.charAt(i*2+1),16));
        }
        return bytes;
    }
    @Override public void close() { snapshot.close(); }
}

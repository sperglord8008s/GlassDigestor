// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import org.json.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.function.BooleanSupplier;

/** Shared validation of exact libmpv values; duplicate ordered map entries stay intact. */
final class MpvNodeValidator {
    private int visited;

    int count() {
        return visited;
    }

    void validate(JSONObject node, int depth, BooleanSupplier stop) throws Exception {
        EvidenceRows.check(stop);
        if (depth > 64 || ++visited > 100000)
            throw new IOException("MPV node tree exceeds its depth/count budget");
        String type = node.getString("type");
        int format = node.getInt("format");
        switch (type) {
            case "none":
                if (format != 0 || !node.isNull("value"))
                    throw new IOException("Invalid empty MPV value");
                break;
            case "string":
                if (format != 1 || !(node.get("value") instanceof String))
                    throw new IOException("Invalid MPV string");
                MpvDocument.validateHex(node.getString("utf8Hex"), node.getInt("byteLength"), stop);
                break;
            case "flag":
                if (format != 3 || !(node.get("value") instanceof Boolean))
                    throw new IOException("Invalid MPV flag");
                break;
            case "int64":
                if (format != 4
                        || !(node.get("value") instanceof Number)
                        || new BigDecimal(node.get("value").toString()).longValueExact()
                                != Long.parseLong(node.getString("decimal")))
                    throw new IOException("Invalid exact MPV int64");
                break;
            case "double":
                if (format != 5 || !node.isNull("value") && !(node.get("value") instanceof Number))
                    throw new IOException("Invalid MPV floating value");
                MpvDocument.validateHex(node.getString("ieee754Bits"), 8, stop);
                break;
            case "bytes":
                if (format != 9) throw new IOException("Invalid MPV byte-array format");
                MpvDocument.validateHex(node.getString("dataHex"), node.getInt("byteLength"), stop);
                break;
            case "array":
            case "map":
                if (format != (type.equals("map") ? 8 : 7))
                    throw new IOException("Invalid MPV container format");
                JSONArray children = node.getJSONArray("children");
                if (children.length() > 100000)
                    throw new IOException("MPV child count exceeds its budget");
                for (int i = 0; i < children.length(); i++) {
                    JSONObject child = children.getJSONObject(i);
                    if (type.equals("map")) {
                        if (!(child.get("key") instanceof String))
                            throw new IOException("MPV map contains a non-string key");
                        String encoded = child.getString("keyUtf8Hex");
                        MpvDocument.validateHex(encoded, encoded.length() / 2, stop);
                        child = child.getJSONObject("node");
                    }
                    validate(child, depth + 1, stop);
                }
                break;
            default:
                throw new IOException("Unknown MPV value type: " + type);
        }
    }
}

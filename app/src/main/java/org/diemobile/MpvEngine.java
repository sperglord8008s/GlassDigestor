// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

import java.nio.charset.StandardCharsets;

/** Actual libmpv API bridge. Production invocation belongs to the isolated service. */
final class MpvEngine {
    static final boolean AVAILABLE;
    static final String ERROR;
    static {
        boolean available = false;
        String error = "";
        try { System.loadLibrary("diempv"); available = true; }
        catch (LinkageError failure) { error = failure.toString(); }
        AVAILABLE = available;
        ERROR = error;
    }
    private static native byte[] probeFd(int descriptor);
    private static native byte[] frameFd(int descriptor, int output, int maximum);
    static String scanFd(int descriptor) {
        byte[] bytes = probeFd(descriptor);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
    static String captureFd(int descriptor, int output, int maximum) {
        byte[] bytes = frameFd(descriptor, output, maximum);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
    private MpvEngine() {}
}

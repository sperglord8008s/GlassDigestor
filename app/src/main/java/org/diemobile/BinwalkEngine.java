// SPDX-License-Identifier: GPL-3.0-only
package org.diemobile;

/** JNI data parsing only. Android calls scanFd inside the isolated worker. */
final class BinwalkEngine {
  static final boolean AVAILABLE;
  static final String ERROR;

  static {
    boolean available = false;
    String error = "";
    try {
      System.loadLibrary("diebinwalk");
      available = true;
    } catch (LinkageError failure) {
      error = failure.toString();
    }
    AVAILABLE = available;
    ERROR = error;
  }

  private BinwalkEngine() {}

  /** Legacy host/consumer API retains its original 30-second adapter-2 contract. */
  static native String scanFd(int descriptor);

  /** Production descriptor-only request, bounded to whole seconds from 1 to 1800. */
  static native String scanFdWithDeadline(int descriptor, long analysisDeadlineMs);

  /** Host fixture entry point; production Android dispatch uses granted descriptors. */
  static native String scanPath(String path);
}

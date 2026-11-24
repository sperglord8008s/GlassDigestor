#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:-android}"
if [[ "$mode" = host ]]; then
  java_home="${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
  source scripts/host-mpv.sh
  library=$(die_host_mpv_library)
  fortify_level=$(die_host_mpv_fortify_level)
  mkdir -p app/build/host-mpv
  "${CC:-cc}" -std=c11 -O2 -fPIC -shared -Wall -Wextra -Werror \
    -U_FORTIFY_SOURCE "-D_FORTIFY_SOURCE=$fortify_level" \
    -I"$java_home/include" -I"$java_home/include/linux" -Imedia-engine/include \
    media-engine/probe.c media-engine/frame.c "$library" -Wl,-z,defs -Wl,-rpath,"$(dirname "$library")" \
    -o app/build/host-mpv/libdiempv.so
elif [[ "$mode" = android ]]; then
  python3 scripts/fetch-mpv.py
  sdk="${ANDROID_HOME:?Set ANDROID_HOME}"
  ndk="${ANDROID_NDK_HOME:-$sdk/ndk/28.2.13676358}"
  llvm="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
  output=app/build/generated-mpv-jni/arm64-v8a
  mkdir -p "$output"
  "$llvm/aarch64-linux-android26-clang" -std=c11 -O2 -fPIC -shared -Wall -Wextra -Werror \
    -DDIE_MPV_ANDROID_BUNDLE=1 -Imedia-engine/include \
    media-engine/probe.c media-engine/frame.c -L"$output" -lmpv \
    -Wl,-z,defs -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 \
    -o "$output/libdiempv.so"
else echo 'Usage: bash scripts/build-mpv.sh [android|host]' >&2; exit 1; fi

#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:-android}"
bash scripts/build-archive-libraries.sh "$mode"
python3 scripts/archive-notices.py --check
abi="${DIE_ANDROID_ABI:-arm64-v8a}"
profile="$mode"
if [[ "$mode" = android ]]; then
  case "$abi" in arm64-v8a|x86_64) ;; *) echo 'Supported Android archive ABIs: arm64-v8a, x86_64' >&2; exit 1;; esac
  if [[ "$abi" != arm64-v8a ]]; then profile="$mode-$abi"; fi
fi
build="app/build/archive-$profile"
flags=(-DCMAKE_BUILD_TYPE=Release)
if [[ "$mode" = android ]]; then
  ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:?Set ANDROID_HOME}/ndk/28.2.13676358}"
  flags+=(-DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake"
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static)
  output="${DIE_ARCHIVE_JNI_ROOT:-app/build/generated-archive-jni}/$abi"
else
  output=app/build/host-archive
fi
cmake -S archive-engine -B "$build" "${flags[@]}"
cmake --build "$build" --parallel 2
mkdir -p "$output"
cp "$build/libdiearchive.so" "$output/"

cp archive-engine/{NOTICE.txt,source-headers.txt} "$output/"

if [[ "$mode" = android ]]; then
  assets="${DIE_ARCHIVE_ASSETS_DIR:-app/build/generated-archive-assets}/notices"
  mkdir -p "$assets"
  cp archive-engine/NOTICE.txt "$assets/archive-codecs.txt"
  cp archive-engine/source-headers.txt "$assets/archive-source-headers.txt"
fi

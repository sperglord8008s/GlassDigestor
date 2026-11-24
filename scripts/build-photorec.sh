#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/fetch-photorec.sh
python3 scripts/photorec-notices.py --check
mode="${1:-android}"
abi="${DIE_ANDROID_ABI:-arm64-v8a}"
if [[ "$abi" != arm64-v8a && "$abi" != x86_64 ]]; then
  echo 'PhotoRec Android builds support arm64-v8a and the x86_64 runtime laboratory.' >&2
  exit 1
fi
build="app/build/photorec-$mode"
if [[ "$mode" = android && "$abi" != arm64-v8a ]]; then build="$build-$abi"; fi
if [[ "$mode" = host ]]; then
  output=app/build/host-photorec
  cmake -S carving-engine -B "$build" -DCMAKE_BUILD_TYPE=Release
elif [[ "$mode" = android ]]; then
  ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:?Set ANDROID_HOME}/ndk/28.2.13676358}"
  output="${DIE_PHOTOREC_JNI_ROOT:-app/build/generated-photorec-jni}/$abi"
  cmake -S carving-engine -B "$build" -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static
else
  echo 'Usage: bash scripts/build-photorec.sh [host|android]' >&2
  exit 1
fi
cmake --build "$build" --parallel 4
mkdir -p "$output"
cp "$build/libdiephotorec.so" "$output/"

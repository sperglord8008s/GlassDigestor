#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Build-only Android 26/28/35 test images for Robolectric 4.14.1 (instrumentation i7).
# Fetch once through bounded, hash-checked acquisition; test JVMs never use the network.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/fetch-verified.sh
sdk_directory="${ROBOLECTRIC_SDK_DIR:-$PWD/.ci-cache/robolectric}"
while read -r checksum name; do
  if [[ ! "$name" =~ ^android-all-instrumented-[a-zA-Z0-9._-]+\.jar$ ]]; then
    echo 'Invalid pinned Robolectric SDK filename.' >&2; exit 1
  fi
  version="${name#android-all-instrumented-}"
  version="${version%.jar}"
  fetch_verified_dependency "$sdk_directory/$name" "$checksum" \
    "https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/$version/$name" \
    209715200
done < scripts/robolectric-sdk.sha256

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
  destination="$sdk_directory/$name"
  relative="org/robolectric/android-all-instrumented/$version/$name"
  if [[ -e "$destination" || -L "$destination" || "${DIE_OFFLINE:-false}" = true ]]; then
    # A bad existing cache or missing offline input must fail without replacement/network.
    fetch_verified_dependency "$destination" "$checksum" \
      "https://repo.maven.apache.org/maven2/$relative" 209715200
    continue
  fi
  acquired=false
  # Some hosted runners receive HTTP 429 from Central. Both locations must supply
  # the same independently pinned bytes; no mirror can change the accepted image.
  for base in https://maven-central.storage-download.googleapis.com/maven2 \
              https://repo.maven.apache.org/maven2; do
    if fetch_verified_dependency "$destination" "$checksum" "$base/$relative" 209715200; then
      acquired=true
      break
    fi
  done
  if [[ "$acquired" != true ]]; then echo "Pinned Robolectric SDK unavailable: $name" >&2; exit 1; fi
done < scripts/robolectric-sdk.sha256

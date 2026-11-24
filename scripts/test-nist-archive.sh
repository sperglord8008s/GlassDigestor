#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Optional external corpus input. No downloads, inspected-code execution or app assets.
set -euo pipefail
if [[ $# != 2 ]]; then
    echo 'Usage: scripts/test-nist-archive.sh /path/to/L0_Video.dd.bz2 /path/to/receipt.json' >&2
    exit 2
fi
image=$(realpath "$1")
receipt=$(realpath -m "$2")
cd "$(dirname "$0")/.."
bash scripts/build-archive.sh host
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
source scripts/test-dependencies.sh
load_json_test_dependency "$tmp"
javac -cp "$json_test_jar" -d "$tmp/classes" \
    app/src/main/java/org/diemobile/{ArchiveEngine,ArchiveRunner,ArchiveLaunch,EvidenceJson,NativeArchiveStore,NativeArchiveSession,InputSnapshot,EvidenceRows,RangeAnalysis,FileRange,Inspector}.java \
    tests/{NativeArchiveStoreTest,NistArchiveTest}.java
timeout 180s java --add-opens=java.base/java.io=ALL-UNNAMED \
    -Djava.library.path="$PWD/app/build/host-archive" \
    -cp "$tmp/classes:$json_test_jar" org.diemobile.NistArchiveTest "$image" "$receipt"

#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/build-photorec.sh host
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
source scripts/test-dependencies.sh
load_json_test_dependency "$tmp"
javac -cp "$json_test_jar" -d "$tmp/classes" \
  app/src/main/java/org/diemobile/{Inspector,EvidenceRows,EvidenceJson,InputSnapshot,InvestigationBackend,FileRange,RangeAnalysis,PreparedExport,PhotoRecEngine,PhotoRecReport,BinaryRunner,CarvingBackend,PhotoRecFileChecks,PhotoRecBackend}.java \
  tests/fixtures/metadata/MetadataFixtureFactory.java tests/PhotoRecMpegTest.java
java -Xmx256m --add-opens=java.base/java.io=ALL-UNNAMED -Djava.library.path="$PWD/app/build/host-photorec" \
  -cp "$tmp/classes:$json_test_jar" org.diemobile.PhotoRecMpegTest "$tmp"

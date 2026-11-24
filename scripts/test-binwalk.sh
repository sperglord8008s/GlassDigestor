#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${DIE_RUST_ROOT:-}" ]]; then export PATH="$DIE_RUST_ROOT/bin:$PATH"; fi
bash scripts/build-binwalk.sh host
CARGO_TARGET_DIR="${BINARY_BUILD_DIR:-$PWD/app/build/binwalk-rust}" cargo test --manifest-path binary-engine/Cargo.toml --locked --release --lib
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
source scripts/test-dependencies.sh
load_json_test_dependency "$tmp"
javac -cp "$json_test_jar" -d "$tmp/classes" \
  app/src/main/java/org/diemobile/{Inspector,EvidenceRows,EvidenceJson,InputSnapshot,InvestigationBackend,FileRange,RangeAnalysis,PreparedExport,BinwalkEngine,BinaryRunner,CarvingBackend,PhotoRecFileChecks,PhotoRecReport,BinwalkBackend}.java \
  tests/fixtures/metadata/MetadataFixtureFactory.java tests/{BinwalkInvestigationTest,BinwalkInputTest,BinwalkDeadlineTest}.java
java --add-opens=java.base/java.io=ALL-UNNAMED -Djava.library.path="$PWD/app/build/host-binwalk" -cp "$tmp/classes:$json_test_jar" org.diemobile.BinwalkInvestigationTest "$tmp"
java -Xmx128m --add-opens=java.base/java.io=ALL-UNNAMED -Djava.library.path="$PWD/app/build/host-binwalk" -cp "$tmp/classes:$json_test_jar" org.diemobile.BinwalkInputTest "$tmp"
java -Xmx128m --add-opens=java.base/java.io=ALL-UNNAMED -Djava.library.path="$PWD/app/build/host-binwalk" -cp "$tmp/classes:$json_test_jar" org.diemobile.BinwalkDeadlineTest "$tmp"

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
javac -d "$tmp" app/src/main/java/org/diemobile/{Inspector,CustomRuleStore,ArchiveStore}.java tests/{InspectorTest,CustomRuleStoreTest,ArchiveStoreTest}.java
java -cp "$tmp" org.diemobile.InspectorTest
java -cp "$tmp" org.diemobile.CustomRuleStoreTest
java -cp "$tmp" org.diemobile.ArchiveStoreTest
bash scripts/test-presentation.sh
python3 tests/test-build-version.py
python3 tests/test-nist-catalog.py
python3 tests/test-nist-recovery-score.py

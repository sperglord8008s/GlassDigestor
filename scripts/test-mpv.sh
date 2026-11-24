#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/build-mpv.sh host
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
source scripts/test-dependencies.sh
load_json_test_dependency "$tmp"
source scripts/host-mpv.sh
library=$(die_host_mpv_library)
"${CC:-cc}" -std=c11 -O2 -Wall -Wextra -Werror -pthread \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -Imedia-engine/include \
  tests/mpv-boundaries.c "$library" -Wl,-rpath,"$(dirname "$library")" -lm -o "$tmp/mpv-boundaries"
"$tmp/mpv-boundaries"
"${CC:-cc}" -std=c11 -O2 -Wall -Wextra -Werror -pthread \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -Imedia-engine/include \
  tests/mpv-frame-boundaries.c "$library" -Wl,-rpath,"$(dirname "$library")" -lm -o "$tmp/mpv-frame-boundaries"
"$tmp/mpv-frame-boundaries"
command -v ffmpeg >/dev/null || { echo 'Install ffmpeg to generate the real media fixtures.' >&2; exit 1; }
javac -cp "$json_test_jar" -d "$tmp/classes" \
  app/src/main/java/org/diemobile/{Inspector,EvidenceRows,EvidenceJson,InputSnapshot,InvestigationBackend,FileRange,RangeAnalysis,PreparedExport,BinaryRunner,MpvEngine,MpvReference,MpvNodeValidator,MpvDocument,MpvTables,MpvBackend,MpvFrameRunner,MpvFrameReport}.java \
  tests/fixtures/metadata/{MetadataFixtureFactory,MpvFrameFixture}.java tests/{MpvInvestigationTest,MpvFrameTest,MpvFrameReportTest}.java
java --add-opens=java.base/java.io=ALL-UNNAMED -Djava.library.path="$PWD/app/build/host-mpv" \
  -cp "$tmp/classes:$json_test_jar:$PWD/app/src/main/resources" org.diemobile.MpvInvestigationTest "$tmp"
timeout 90s java --add-opens=java.base/java.io=ALL-UNNAMED -Djava.library.path="$PWD/app/build/host-mpv" \
  -cp "$tmp/classes:$json_test_jar" org.diemobile.MpvFrameTest "$tmp"
java -Xmx128m -cp "$tmp/classes:$json_test_jar" org.diemobile.MpvFrameReportTest "$tmp"

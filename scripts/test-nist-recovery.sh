#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Optional external corpus; no downloads or third-party media in the application.
set -euo pipefail
if [[ $# != 2 ]]; then
    echo 'Usage: scripts/test-nist-recovery.sh /path/to/L0_Video.dd.bz2 /new/output-directory' >&2
    exit 2
fi
image=$(realpath "$1")
output=$(realpath -m "$2")
if [[ -e "$output" ]]; then
    echo 'Output directory already exists; choose a new path to preserve prior results.' >&2
    exit 2
fi
cd "$(dirname "$0")/.."
# Serialize builds; no JVM may have one of these libraries loaded while rebuilding.
bash scripts/build-archive.sh host
bash scripts/build-photorec.sh host
bash scripts/build-binwalk.sh host
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
source scripts/test-dependencies.sh
load_json_test_dependency "$tmp"
javac -cp "$json_test_jar" -d "$tmp/classes" \
    app/src/main/java/org/diemobile/{ArchiveEngine,ArchiveRunner,NativeArchiveStore,NativeArchiveSession,ArchiveLaunch,Inspector,EvidenceRows,EvidenceJson,InputSnapshot,InvestigationBackend,FileRange,RangeAnalysis,PreparedExport,PhotoRecEngine,PhotoRecReport,BinwalkEngine,BinaryRunner,CarvingBackend,PhotoRecFileChecks,PhotoRecBackend,BinwalkBackend,ContentMap,RecoveredFiles,OwnedContent,GalleryDiscovery,GallerizeSession}.java \
    tests/NistRecoveryIntegrationTest.java
mkdir -p "$(dirname "$output")"
mkdir "$output"
timeout 600s java -Xmx512m --add-opens=java.base/java.io=ALL-UNNAMED \
    -Djava.library.path="$PWD/app/build/host-archive:$PWD/app/build/host-photorec:$PWD/app/build/host-binwalk" \
    -cp "$tmp/classes:$json_test_jar" org.diemobile.NistRecoveryIntegrationTest "$image" "$output"
python3 - "$output" <<'PY'
from pathlib import Path
import hashlib, json, subprocess, sys
root = Path.cwd()
output = Path(sys.argv[1])
def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()
files = ['tests/NistRecoveryIntegrationTest.java', 'scripts/test-nist-recovery.sh',
         'carving-engine/probe.cpp', 'carving-engine/source-lock.json',
         'app/src/main/java/org/diemobile/PhotoRecReport.java',
         'app/src/main/java/org/diemobile/PhotoRecFileChecks.java',
         'app/src/main/java/org/diemobile/CarvingBackend.java',
         'app/src/main/java/org/diemobile/GallerizeSession.java',
         'app/src/main/java/org/diemobile/ContentMap.java',
         'app/src/main/java/org/diemobile/RecoveredFiles.java',
         'app/build/host-archive/libdiearchive.so',
         'app/build/host-binwalk/libdiebinwalk.so',
         'app/build/host-photorec/libdiephotorec.so']
receipt = {'sourceRevision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
           'sourceWorkingTreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], text=True)),
           'inputFiles': {name: sha(root / name) for name in files},
           'outputFiles': {p.name: sha(p) for p in sorted(output.iterdir()) if p.is_file()}}
(output / 'identities.json').write_text(json.dumps(receipt, indent=2) + '\n')
PY
python3 scripts/score-nist-recovery.py "$output/recovered-files.zip" --output "$output/score.json"

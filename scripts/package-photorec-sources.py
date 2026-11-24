#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Bundle exact upstream and complete standalone adapter/build sources offline."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
lock = json.loads((root / 'carving-engine/source-lock.json').read_text())
archive = Path(os.environ.get('PHOTOREC_DEPS_DIR', root / '.ci-cache/photorec')) / 'testdisk-7.2.tar.gz'
if hashlib.sha256(archive.read_bytes()).hexdigest() != lock['archiveSha256']:
    raise SystemExit('PhotoRec source archive does not match lock')
subprocess.run(['python3', str(root / 'scripts/photorec-notices.py'), '--check'], check=True)
files = {'.ci-cache/photorec/testdisk-7.2.tar.gz': archive}
for path in sorted((root / 'carving-engine').iterdir()):
    if path.is_file():
        files[path.relative_to(root).as_posix()] = path
for name in ['LICENSE', 'scripts/build-photorec.sh', 'scripts/fetch-photorec.sh',
             'scripts/prepare-photorec.py', 'scripts/fetch-verified.sh',
             'scripts/photorec-notices.py', 'scripts/package-photorec-sources.py',
             'scripts/test-photorec.sh', 'scripts/test-photorec-mpeg.sh', 'scripts/test-dependencies.sh',
             'tests/PhotoRecInvestigationTest.java', 'tests/PhotoRecStreamingTest.java', 'tests/PhotoRecMpegTest.java',
             'tests/blob_input_test.cpp', 'tests/read_only_file_test.cpp', 'tests/fixtures/carving/CarvingFixture.java',
             'tests/fixtures/metadata/MetadataFixtureFactory.java',
             'app/src/main/assets/notices/photorec-7.2.txt',
             'app/src/main/assets/notices/photorec-source-headers.txt']:
    files[name] = root / name
for name in ['Inspector', 'EvidenceRows', 'EvidenceJson', 'InputSnapshot', 'InvestigationBackend',
             'FileRange', 'RangeAnalysis', 'PreparedExport', 'PhotoRecEngine',
             'PhotoRecReport', 'PhotoRecFileChecks', 'BinaryRunner', 'CarvingBackend', 'PhotoRecBackend']:
    path = 'app/src/main/java/org/diemobile/' + name + '.java'
    files[path] = root / path
directory = root / 'app/build/photorec-sources'
directory.mkdir(parents=True, exist_ok=True)
output = directory / 'photorec-7.2-diemobile-sources.zip'
with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
    for name, path in sorted(files.items()):
        info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        target.writestr(info, path.read_bytes())
checksum = hashlib.sha256(output.read_bytes()).hexdigest()
output.with_suffix('.zip.sha256').write_text(checksum + '  ' + output.name + '\n')
with zipfile.ZipFile(output) as check:
    assert check.namelist() == sorted(files)
    for name, path in files.items():
        assert check.read(name) == path.read_bytes(), name
print('PhotoRec corresponding-source ZIP verified:', output, checksum)

#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Freeze a verified clean-source APK as an immutable, versioned save point."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('build_version', ROOT / 'scripts/build-version.py')
build_version = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build_version)


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, help='Defaults to the single debug output in AGP metadata')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'app/build/alpha-checkpoint')
    arguments = parser.parse_args()
    apk = (arguments.apk or build_version.apk_path()).resolve()
    output = arguments.output_dir.resolve()
    if output.exists():
        raise SystemExit('Checkpoint already exists; retain it and choose a new output directory.')
    manifest_path = apk.parent / 'build-manifest.txt'
    manifest = manifest_path.read_bytes()
    text = manifest.decode('utf-8')
    source = re.search(r'^Source revision: ([0-9a-f]{40}|[0-9a-f]{64})$', text, re.M)
    version = re.search(r'^Application version: ([A-Za-z0-9][A-Za-z0-9._+\-]{0,63}) \(version code ([0-9]+)\)$', text, re.M)
    if source is None or version is None or 'Uncommitted source entries at verification: 0\n' not in text:
        raise SystemExit('Run verify-apk.sh from a clean committed source tree before packaging.')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    changes = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True)
    if head != source.group(1) or changes:
        raise SystemExit('Current source does not match the clean verification manifest.')
    identity = build_version.identity()
    if version.group(1) != identity['versionName'] or int(version.group(2)) != identity['versionCode']:
        raise SystemExit('Verified application version does not match the current version/source identity.')
    expected = apk.with_name(apk.name + '.sha256').read_text().split()[0]
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or digest(apk) != expected:
        raise SystemExit('APK bytes differ from their verified checksum.')
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='.alpha-', dir=output.parent))
    try:
        name = identity['apkName']
        shutil.copyfile(apk, staging / name)
        if digest(staging / name) != expected:
            raise SystemExit('APK changed during checkpoint acquisition; candidate discarded.')
        (staging / (name + '.sha256')).write_text(expected + '  ' + name + '\n', encoding='ascii')
        (staging / 'build-manifest.txt').write_bytes(manifest)
        metadata = {
            'schema': 'org.diemobile.alpha-checkpoint', 'schemaVersion': 1,
            'applicationId': 'org.diemobile', 'versionName': version.group(1),
            'productName': identity['productName'],
            'versionCode': int(version.group(2)), 'sourceRevision': head,
            'apk': name, 'apkSha256': expected, 'apkSizeBytes': (staging / name).stat().st_size,
            'variant': 'debug', 'abi': 'arm64-v8a', 'minSdk': 26, 'targetSdk': 35,
            'deviceRuntimeVerified': False,
        }
        (staging / 'checkpoint.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
        (staging / 'CHECKPOINT.md').write_text(
            '# ' + identity['productName'] + ' ' + version.group(1) + '\n\n'
            'Source: `' + head + '`\n\n'
            'Install `' + name + '` on an ARM64 Android API 26+ device. '
            'Application ID is `org.diemobile`; version code is ' + version.group(2) + '.\n\n'
            'Check `SHA256SUMS` and retain the original build manifest with the APK. '
            'The manifest records source/engine pins and the public signer certificate. '
            'Existing installations require a matching signer for an update. '
            'The debug signing configuration is unchanged; independently built CI debug keys may differ.\n\n'
            'Build, host simulation and static alignment checks do not establish handset/SAF/'
            'isolated-UID/crash/cancellation/16 KiB runtime behavior or full desktop parity.\n\n'
            '[Source and build instructions](https://github.com/sperglord8008s/GlassDigestor/tree/' + head + ')\n\n'
            '[Feature parity](https://github.com/sperglord8008s/GlassDigestor/blob/' + head + '/docs/FEATURE-PARITY.md)\n',
            encoding='utf-8')
        entries = sorted(staging.iterdir(), key=lambda item: item.name)
        (staging / 'SHA256SUMS').write_text(''.join(digest(item) + '  ' + item.name + '\n' for item in entries), encoding='utf-8')
        staging.rename(output)
        staging = None
        print(json.dumps({'checkpoint': str(output), **metadata}, indent=2))
    finally:
        if staging is not None:
            shutil.rmtree(staging)


if __name__ == '__main__':
    main()

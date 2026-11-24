#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Pin complete MPV runtime sources, including build-time submodules."""
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
import argparse
import json
import os
from pathlib import Path
import tempfile
from urllib.request import urlopen
from zipfile import ZipFile, ZIP_DEFLATED

parser = argparse.ArgumentParser()
parser.add_argument('--bundle', action='store_true', help='also stage a redistributable MPV component-source bundle')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
lock = json.loads((root / 'media-engine/source-lock.json').read_text())
cache = Path(os.environ.get('MPV_SOURCE_DIR', root / 'vendor/mpv-source-cache'))
cache.mkdir(parents=True, exist_ok=True)

def digest(path):
    result = sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()

def acquire(item):
    if Path(item['file']).name != item['file']:
        raise ValueError('Invalid source archive name in the trusted lock')
    target = cache / item['file']
    if target.exists():
        if digest(target) != item['sha256']:
            raise ValueError('Cached MPV source differs; preserve/remove it before retrying: ' + item['name'])
    else:
        if os.environ.get('DIE_OFFLINE') == 'true':
            raise ValueError('Offline build needs the pinned source: ' + item['name'])
        with tempfile.NamedTemporaryFile(dir=cache, delete=False) as temporary:
            path = Path(temporary.name)
            try:
                with urlopen(item['url'], timeout=60) as source:
                    size = 0
                    for block in iter(lambda: source.read(1024 * 1024), b''):
                        size += len(block)
                        if size > 64 * 1024 * 1024:
                            raise ValueError('MPV component source exceeds its download budget')
                        temporary.write(block)
                temporary.close()
                if digest(path) != item['sha256']:
                    raise ValueError('MPV source checksum mismatch: ' + item['name'])
                path.replace(target)
            finally:
                path.unlink(missing_ok=True)
    print(item['name'] + ': source archive verified', flush=True)

with ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(acquire, lock['sources']))
if args.bundle:
    output = root / 'app/build/mpv-sources'
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output, delete=False) as temporary:
        temporary_path = Path(temporary.name)
    try:
        with ZipFile(temporary_path, 'w', compression=ZIP_DEFLATED, compresslevel=1) as bundle:
            for item in lock['sources']:
                bundle.write(cache / item['file'], 'sources/' + item['file'])
            integration_files = ('source-lock.json', 'runtime-lock.json', 'README.md', 'probe.c', 'frame.c', 'mpv_support.h')
            for name in integration_files:
                bundle.write(root / 'media-engine' / name, 'integration/' + name)
            for source_path in sorted((root / 'media-engine/include').rglob('*')):
                if source_path.is_file():
                    bundle.write(source_path, 'integration/include/' + source_path.relative_to(root / 'media-engine/include').as_posix())
            for source_path in sorted((root / 'media-engine/licenses').glob('*')):
                if source_path.is_file():
                    bundle.write(source_path, 'integration/licenses/' + source_path.name)
            bundle.write(root / 'LICENSE', 'integration/DiE-Android-LICENSE.txt')
            for name in ('prepare-mpv-source-build.py', 'fetch-mpv-sources.py', 'fetch-mpv.py', 'build-mpv.sh', 'host-mpv.sh', 'mpv-notices.py', 'mpv-references.py'):
                bundle.write(root / 'scripts' / name, 'scripts/' + name)
        # Check the distribution itself, including every complete source payload,
        # before replacing a previously valid component-source artifact.
        with ZipFile(temporary_path) as bundle:
            names = bundle.namelist()
            if len(names) != len(set(names)):
                raise ValueError('Duplicate component-source bundle entries')
            for item in lock['sources']:
                result = sha256()
                with bundle.open('sources/' + item['file']) as stream:
                    for block in iter(lambda: stream.read(1024 * 1024), b''):
                        result.update(block)
                if result.hexdigest() != item['sha256']:
                    raise ValueError('Staged source bundle payload differs: ' + item['name'])
            if bundle.read('integration/licenses/llvm-license-lock.json') != (root / 'media-engine/licenses/llvm-license-lock.json').read_bytes():
                raise ValueError('Staged source bundle lost its NDK runtime license identity')
            for name in integration_files:
                if bundle.read('integration/' + name) != (root / 'media-engine' / name).read_bytes():
                    raise ValueError('Staged MPV adapter source differs: ' + name)
        target = output / 'mpv-android-2026-09-17-component-sources.zip'
        temporary_path.replace(target)
        target.with_suffix(target.suffix + '.sha256').write_text(digest(target) + '  ' + target.name + '\n')
    finally:
        temporary_path.unlink(missing_ok=True)
    print('Complete pinned MPV component-source archive staged alongside build artifacts.')

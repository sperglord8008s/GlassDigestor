#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check the test-only APK, exact shared sources and x86 native architecture."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def apk(folder):
    directory = ROOT / folder
    metadata = json.loads((directory / "output-metadata.json").read_text())
    assert len(metadata["elements"]) == 1
    filename = metadata["elements"][0]["outputFile"]
    assert Path(filename).name == filename
    result = directory / filename
    assert result.is_file()
    return result


def verify(sdk):
    target = apk("runtime-worker/build/outputs/apk/debug")
    tests = apk("runtime-worker/build/outputs/apk/androidTest/debug")
    tools = sdk / "build-tools/35.0.0"
    for source in (target, tests):
        subprocess.run([str(tools / "apksigner"), "verify", str(source)], check=True)
    badging = subprocess.check_output(
        [str(tools / "aapt2"), "dump", "badging", str(target)], text=True
    )
    assert "package: name='org.diemobile.runtimecheck'" in badging
    assert "minSdkVersion:'26'" in badging and "targetSdkVersion:'35'" in badging
    identity = json.loads(subprocess.check_output(
        [sys.executable, str(ROOT / "scripts/build-version.py")], cwd=ROOT, text=True
    ))
    package_line = next(line for line in badging.splitlines() if line.startswith("package: "))
    for key in ["versionName", "versionCode"]:
        assert f"{key}='{identity[key]}'" in package_line, (
            "Runtime APK differs from current version/source; rebuild it"
        )
    permissions = subprocess.check_output(
        [str(tools / "aapt2"), "dump", "permissions", str(target)], text=True
    )
    assert "android.permission.INTERNET" not in permissions
    production = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    production_service = next(
        service for service in production.findall("application/service")
        if service.get(ANDROID + "name") in [".BinaryWorkerService", "org.diemobile.BinaryWorkerService"]
    )
    lab = ET.parse(ROOT / "runtime-worker/src/main/AndroidManifest.xml").getroot()
    application = lab.find("application")
    service = application.find("service")
    assert application.get(ANDROID + "testOnly") == "true"
    for key in ["exported", "isolatedProcess", "process", "stopWithTask"]:
        assert service.get(ANDROID + key) == production_service.get(ANDROID + key)
    assert service.get(ANDROID + "name") == "org.diemobile.BinaryWorkerService"
    activity = application.find("activity")
    assert activity.get(ANDROID + "name") == "org.diemobile.TextActionRuntimeActivity"
    assert activity.get(ANDROID + "exported") == "false"
    assert not activity.findall("intent-filter")
    assert "launchable-activity:" not in badging
    compiled_manifest = subprocess.check_output([
        str(tools / "aapt2"), "dump", "xmltree", str(target),
        "--file", "AndroidManifest.xml"
    ], text=True)
    assert "org.diemobile.BinaryWorkerService" in compiled_manifest
    for attribute, value in [("isolatedProcess", "true"), ("exported", "false"), ("testOnly", "true")]:
        assert any(
            ":" + attribute + "(" in line and line.rstrip().endswith("=" + value)
            for line in compiled_manifest.splitlines()
        ), attribute
    sources = {}
    directory = ROOT / "runtime-worker/build/shared-worker-sources"
    for file in sorted(directory.rglob("*.java")):
        relative = file.relative_to(directory)
        original = ROOT / "app/src/main/java" / relative
        assert original.read_bytes() == file.read_bytes(), f"Shared production source changed: {relative}"
        sources[str(relative)] = sha256(file)
    for name in ["BinaryWorkerClient", "BinaryWorkerService", "ArchiveEngine", "HashEngine", "AndroidResourceProbe", "PackageManifest", "PdfPagePreview", "OwnedContent", "PreparedExport", "TextActions", "WorkbenchUi", "NativeArchiveSession", "ArchiveLaunch", "ContentOrigin"]:
        assert "org/diemobile/" + name + ".java" in sources
    text_resources = {}
    for name in ["values/colors.xml", "values/text_action_ids.xml"]:
        original = ROOT / "app/src/main/res" / name
        staged = ROOT / "runtime-worker/build/shared-text-resources" / name
        assert original.read_bytes() == staged.read_bytes(), f"Shared resource changed: {name}"
        text_resources[name] = sha256(staged)
    with zipfile.ZipFile(target) as archive:
        native = [name for name in archive.namelist() if name.endswith(".so")]
        assert set(native) == {"lib/x86_64/libdiearchive.so", "lib/x86_64/libdiehash.so", "lib/x86_64/libdiephotorec.so"}, native
        for name in native:
            data = archive.read(name)
            if name.endswith("libdiephotorec.so"):
                built = ROOT / "app/build/generated-photorec-jni/x86_64/libdiephotorec.so"
                strip = sdk / "ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
                with tempfile.TemporaryDirectory(prefix="photorec-packaging-") as temporary:
                    stripped = Path(temporary) / "libdiephotorec.so"
                    subprocess.run([str(strip), "--strip-unneeded", "-o", str(stripped), str(built)], check=True)
                    assert data == stripped.read_bytes(), "Packaged PhotoRec differs from the NDK-stripped source build"
                packaged_pdf_sha256 = hashlib.sha256(data).hexdigest()
            assert data[:6] == b"\x7fELF\x02\x01" and int.from_bytes(data[18:20], "little") == 62
        for name in ["DiE-Android-LICENSE.txt", "rhash-md4-1.4.5.txt",
                     "android-resources-32.5.1.txt", "dex-dependencies.txt",
                     "photorec-7.2.txt", "photorec-source-headers.txt"]:
            assert archive.read("assets/notices/" + name) == (
                ROOT / "app/src/main/assets/notices" / name
            ).read_bytes()
        for name in ["CDiE-LICENSE.txt", "dep/xxfclib/LICENSE"]:
            assert archive.read("assets/notices/" + name) == (
                ROOT / "generated-assets/notices" / name
            ).read_bytes()
        assert archive.read("assets/notices/runtime-scope.txt")
        assert archive.read("assets/notices/archive-codecs.txt")
        assert archive.read("assets/test-fixtures/green-64x48.mp4") == (
            ROOT / "tests/fixtures/media/green-64x48.mp4"
        ).read_bytes()
        large_archive = archive.read("assets/test-fixtures/archive-session-20m.bz2")
        assert 19 * 1024 * 1024 < len(large_archive) < 22 * 1024 * 1024
        assert large_archive == (
            ROOT / "runtime-worker/build/generated-runtime-assets/test-fixtures/archive-session-20m.bz2"
        ).read_bytes()
    native_pdf_inputs = [
        "carving-engine/CMakeLists.txt", "carving-engine/config.h", "carving-engine/probe.cpp",
        "carving-engine/blob_input.h", "carving-engine/read_only_file.h", "carving-engine/deny-files.c",
        "carving-engine/exports.map", "carving-engine/source-lock.json", "scripts/build-photorec.sh",
        "scripts/fetch-photorec.sh", "scripts/prepare-photorec.py", "scripts/photorec-notices.py",
        "tests/fixtures/metadata/MetadataFixtureFactory.java",
        "tests/fixtures/media/generate-preview.py",
    ]
    return {
        "schema": "org.diemobile.worker-runtime-build", "schemaVersion": 1,
        "applicationId": "org.diemobile.runtimecheck", "abi": "x86_64", "testOnly": True,
        "sourceRevision": identity["sourceRevision"], "sourceDirty": identity["sourceDirty"],
        "versionName": identity["versionName"], "versionCode": identity["versionCode"],
        "sharedProductionSources": sources,
        "sharedTextActionResources": text_resources,
        "textActionTestActivitySha256": sha256(
            ROOT / "runtime-worker/src/main/java/org/diemobile/TextActionRuntimeActivity.java"
        ),
        "nativePhotoRecSources": {name: sha256(ROOT / name) for name in native_pdf_inputs},
        "nativePhotoRecX86Sha256": sha256(ROOT / "app/build/generated-photorec-jni/x86_64/libdiephotorec.so"),
        "nativePhotoRecPackagedSha256": packaged_pdf_sha256,
        "nativePhotoRecPackaging": "NDK 28.2.13676358 llvm-strip --strip-unneeded",
        "instrumentationSourceSha256": sha256(
            ROOT / "runtime-worker/src/androidTest/java/org/diemobile/WorkerRuntimeInstrumentation.java"
        ),
        "targetApk": str(target), "instrumentationApk": str(tests),
        "targetApkSha256": sha256(target), "instrumentationApkSha256": sha256(tests),
        "androidRuntimeVerified": False,
    }


if __name__ == "__main__":
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    result = verify(sdk)
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "runtime-worker/build/runtime-build.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n")
    print("Test-only x86_64 APKs, private isolated manifest, unchanged shared worker/bridge sources, "
          "native ELF architecture, notices and signatures verified. Runtime execution is a separate check.")

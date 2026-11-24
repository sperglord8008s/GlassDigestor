#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Install trusted test APKs on an emulator, run actual worker contracts and save evidence."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CHECKS = {
    "isolated-native-hash-vectors",
    "fully-written-large-file-through-real-isolated-fd",
    "real-isolated-archive-read-write-descriptors",
    "exact-range-platform-media-preview",
    "real-isolated-compiled-manifest-declaration",
    "isolated-photorec-pdf-recovery",
    "exact-recovered-platform-pdf-pages",
    "token-scoped-kill-with-blocked-report-pipe",
    "normal-client-recovers-after-worker-death",
    "exact-text-actions-platform-clipboard-and-uris",
}


def main():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    adb = str(sdk / "platform-tools/adb")
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "runtime-worker/build/runtime-results"
    output.mkdir(parents=True, exist_ok=True)

    def run(args, timeout=60):
        return subprocess.run(
            [adb, *args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout
        )

    build = json.loads((ROOT / "runtime-worker/build/runtime-build.json").read_text())
    assert build["sourceDirty"] is False, "CI runtime evidence requires a committed source checkout"
    try:
        for key in ["targetApk", "instrumentationApk"]:
            assert hashlib.sha256(Path(build[key]).read_bytes()).hexdigest() == build[key + "Sha256"], (
                "Runtime APK identity changed after static checks"
            )
            installed = run(["install", "-r", "-t", build[key]], timeout=120)
            (output / (key + "-install.log")).write_bytes(installed.stdout)
            if installed.returncode or b"Success" not in installed.stdout:
                raise RuntimeError("Trusted runtime APK install failed; see install log")
        invocation = run([
            "shell", "am", "instrument", "-w", "-r",
            "org.diemobile.runtimecheck.test/org.diemobile.WorkerRuntimeInstrumentation"
        ], timeout=180)
        (output / "instrumentation.log").write_bytes(invocation.stdout)
        captured = run([
            "exec-out", "run-as", "org.diemobile.runtimecheck", "cat", "files/worker-runtime-report.json"
        ])
        (output / "worker-runtime-report.json").write_bytes(captured.stdout)
        if captured.returncode:
            raise RuntimeError("No complete runtime evidence report; see instrumentation log")
        evidence = json.loads(captured.stdout)
        assert evidence["schema"] == "org.diemobile.android-worker-runtime"
        assert evidence["schemaVersion"] == 1
        assert evidence["passed"] is True, evidence.get("failure", "Runtime checks failed")
        assert evidence["sourceRevision"] == build["sourceRevision"]
        assert evidence["sourceDirty"] == build["sourceDirty"]
        assert evidence["applicationUid"] == evidence["instrumentationUid"]
        assert evidence["sdk"] in [26, 35]
        if "RUNTIME_EXPECTED_API" in os.environ:
            assert evidence["sdk"] == int(os.environ["RUNTIME_EXPECTED_API"])
        assert "x86_64" in evidence["abis"]
        assert len(evidence["checks"]) == len(CHECKS)
        assert {check["name"] for check in evidence["checks"]} == CHECKS
        assert all(check["passed"] is True for check in evidence["checks"])
        # The Binder completion message is bounded; the complete file remains authoritative.
        for key, value in {
            "runtimeCheckCount": len(CHECKS),
            "runtimeReportBytes": len(captured.stdout),
            "runtimeReportSha256": hashlib.sha256(captured.stdout).hexdigest(),
        }.items():
            expected = f"INSTRUMENTATION_RESULT: {key}={value}".encode()
            assert expected in invocation.stdout, f"Completion/file identity differs: {key}"
        assert invocation.returncode == 0
        assert b"INSTRUMENTATION_RESULT: runtimePassed=true" in invocation.stdout
        assert b"INSTRUMENTATION_CODE: -1" in invocation.stdout
        assert b"INSTRUMENTATION_FAILED" not in invocation.stdout
        print("Actual Android worker checks passed:", evidence["sdk"], evidence["abis"],
              "normal UID", evidence["applicationUid"])
    finally:
        for name, command in [
            ("emulator-properties.txt", ["shell", "getprop"]),
            ("android-process-log.txt", ["logcat", "-d", "-s", "AndroidRuntime", "ActivityManager", "libc"]),
        ]:
            try:
                result = run(command)
                (output / name).write_bytes(result.stdout)
            except Exception as failure:
                (output / (name + ".capture-error")).write_text(str(failure) + "\n")


if __name__ == "__main__":
    main()

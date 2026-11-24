#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Install trusted test APKs on an emulator, run actual worker contracts and save evidence."""
import hashlib
import json
import os
from pathlib import Path
import re
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
    "aliased-cache-bzip2-session-and-exact-large-file",
    "captured-analysis-limits-and-private-diagnostics",
    "isolated-photorec-large-mpeg-pack-retention",
}
MAX_REPORT_BYTES = 64 * 1024 * 1024
REPORT_READ_ATTEMPTS = 3
REPORT_COMMAND = [
    "exec-out", "run-as", "org.diemobile.runtimecheck", "cat", "files/worker-runtime-report.json"
]


class ReportTransferError(RuntimeError):
    """The immutable device report could not be captured with its completed identity."""


def completion_identity(log):
    """Read exact, unique fields; a completed failed check still has a useful report."""
    lines = log.splitlines()

    def field(name, prefix=b"INSTRUMENTATION_RESULT: "):
        key = prefix + name.encode("ascii")
        separator = b"=" if prefix == b"INSTRUMENTATION_RESULT: " else b" "
        matches = [line for line in lines if line.startswith(key)]
        if len(matches) != 1:
            raise ReportTransferError(f"missing-or-duplicate-{name}")
        if not matches[0].startswith(key + separator):
            raise ReportTransferError(f"malformed-{name}")
        return matches[0][len(key + separator):]

    passed = field("runtimePassed")
    count = field("runtimeCheckCount")
    size = field("runtimeReportBytes")
    digest = field("runtimeReportSha256")
    code = field("INSTRUMENTATION_CODE:", prefix=b"")
    if passed not in (b"true", b"false"):
        raise ReportTransferError("malformed-runtimePassed")
    if not re.fullmatch(rb"0|[1-9][0-9]{0,3}", count) or int(count) > len(CHECKS):
        raise ReportTransferError("out-of-bound-runtimeCheckCount")
    if not re.fullmatch(rb"[1-9][0-9]{0,8}", size) or not 2 <= int(size) <= MAX_REPORT_BYTES:
        raise ReportTransferError("out-of-bound-runtimeReportBytes")
    if not re.fullmatch(rb"[0-9a-f]{64}", digest):
        raise ReportTransferError("malformed-runtimeReportSha256")
    if code not in (b"-1", b"0"):
        raise ReportTransferError("malformed-INSTRUMENTATION_CODE:")
    return {
        "runtimePassed": passed == b"true",
        "runtimeCheckCount": int(count),
        "runtimeReportBytes": int(size),
        "runtimeReportSha256": digest.decode("ascii"),
        "instrumentationCode": int(code),
    }


def capture_completed_report(run, output, log):
    """Retry only the fixed report read, publishing bytes after length and SHA-256 match."""
    output.mkdir(parents=True, exist_ok=True)
    report = output / "worker-runtime-report.json"
    partial = output / "worker-runtime-report.partial"
    pending = output / "worker-runtime-report.pending"
    receipt_path = output / "report-transfer.json"
    for old in (report, partial, pending):
        old.unlink(missing_ok=True)
    receipt = {
        "schema": "org.diemobile.runtime-report-transfer",
        "schemaVersion": 1,
        "attemptLimit": REPORT_READ_ATTEMPTS,
        "reportByteLimit": MAX_REPORT_BYTES,
        "attempts": [],
    }

    def save_receipt():
        receipt_path.write_text(json.dumps(receipt, indent=2) + "\n")

    try:
        identity = completion_identity(log)
    except ReportTransferError as failure:
        receipt.update(status="invalid-completion", failureReason=str(failure))
        save_receipt()
        raise
    receipt["completion"] = identity
    receipt["status"] = "reading"
    save_receipt()
    for number in range(1, REPORT_READ_ATTEMPTS + 1):
        data, return_code, error = b"", None, None
        try:
            captured = run(REPORT_COMMAND)
            data, return_code = captured.stdout, captured.returncode
        except subprocess.TimeoutExpired as failure:
            data = failure.output if isinstance(failure.output, bytes) else b""
            error = "timeout"
        except OSError:
            error = "adb-launch-failed"
        digest = hashlib.sha256(data).hexdigest()
        length_matches = len(data) == identity["runtimeReportBytes"]
        digest_matches = digest == identity["runtimeReportSha256"]
        accepted = return_code == 0 and length_matches and digest_matches
        attempt = {
            "attempt": number,
            "returnCode": return_code,
            "receivedBytes": len(data),
            "receivedSha256": digest,
            "lengthMatched": length_matches,
            "sha256Matched": digest_matches,
            "accepted": accepted,
        }
        if error is not None:
            attempt["error"] = error
        receipt["attempts"].append(attempt)
        if accepted:
            try:
                pending.write_bytes(data)
                pending.replace(report)
            finally:
                pending.unlink(missing_ok=True)
            partial.unlink(missing_ok=True)
            receipt["status"] = "complete"
            save_receipt()
            return data, identity
        # Preserve only the last unverified read, never as the canonical JSON report.
        retained = data[:MAX_REPORT_BYTES]
        partial.write_bytes(retained)
        attempt["retainedPartialBytes"] = len(retained)
        attempt["partialCapped"] = len(retained) != len(data)
        save_receipt()
    receipt["status"] = "transfer-failed"
    save_receipt()
    raise ReportTransferError(
        "Runtime report length/SHA-256/exit status did not match completion after "
        f"{REPORT_READ_ATTEMPTS} reads; see report-transfer.json and worker-runtime-report.partial"
    )


def main():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    adb = str(sdk / "platform-tools/adb")
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "runtime-worker/build/runtime-results"
    output.mkdir(parents=True, exist_ok=True)
    # Reused artifact directories must not present a previous run as the current capture.
    for name in ["worker-runtime-report.json", "worker-runtime-report.partial",
                 "worker-runtime-report.pending", "report-transfer.json"]:
        (output / name).unlink(missing_ok=True)

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
        report_bytes, completion = capture_completed_report(run, output, invocation.stdout)
        evidence = json.loads(report_bytes)
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
        # Transfer identity was checked before parsing; semantic success remains mandatory.
        assert completion["runtimeCheckCount"] == len(CHECKS)
        assert completion["runtimePassed"] is True
        assert completion["instrumentationCode"] == -1
        assert invocation.returncode == 0
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

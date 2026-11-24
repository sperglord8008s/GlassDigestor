#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Immutable report transfer regression: fake ADB, real bytes/hashes, no Android execution."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

sys.dont_write_bytecode = True
SOURCE = Path(__file__).resolve().parents[1] / "scripts/run-worker-runtime.py"


class FakeAdb:
    def __init__(self, runner, log, reads):
        self.runner = runner
        self.log = log
        self.reads = list(reads)
        self.commands = []
        self.install_exit = 0
        self.instrumentation_exit = 0

    def run(self, command, **options):
        args = command[1:]
        self.commands.append(args)
        if args[0] == "install":
            return subprocess.CompletedProcess(command, self.install_exit, b"Success\n")
        if args[:3] == ["shell", "am", "instrument"]:
            return subprocess.CompletedProcess(command, self.instrumentation_exit, self.log)
        if args == self.runner.REPORT_COMMAND:
            if not self.reads:
                raise AssertionError("Unexpected additional immutable report read")
            result = self.reads.pop(0)
            if isinstance(result, Exception):
                raise result
            code, data = result
            return subprocess.CompletedProcess(command, code, data)
        if args == ["shell", "getprop"] or args[0] == "logcat":
            return subprocess.CompletedProcess(command, 0, b"owned emulator diagnostics fixture\n")
        raise AssertionError("Unexpected fake ADB command: " + repr(args))

    def read(self, args):
        return self.run(["fake-adb", *args])

    def count(self, prefix):
        return sum(command[:len(prefix)] == prefix for command in self.commands)


class RuntimeReportTransferTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location("runtime_report_transfer_fixture", SOURCE)
        self.runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.runner)
        self.temporary = tempfile.TemporaryDirectory(prefix="runtime-report-transfer-")
        self.root = Path(self.temporary.name)
        self.output = self.root / "results"
        self.output.mkdir()
        self.revision = "b64724c" + "0" * 33
        apk = self.root / "owned.apk"
        apk.write_bytes(b"owned trusted test APK identity fixture")
        build = {
            "sourceDirty": False,
            "sourceRevision": self.revision,
            "targetApk": str(apk),
            "instrumentationApk": str(apk),
            "targetApkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "instrumentationApkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        }
        directory = self.root / "runtime-worker/build"
        directory.mkdir(parents=True)
        (directory / "runtime-build.json").write_text(json.dumps(build))

    def tearDown(self):
        self.temporary.cleanup()

    def report(self):
        return {
            "schema": "org.diemobile.android-worker-runtime",
            "schemaVersion": 1,
            "passed": True,
            "sourceRevision": self.revision,
            "sourceDirty": False,
            "applicationUid": 12345,
            "instrumentationUid": 12345,
            "sdk": 35,
            "abis": ["x86_64"],
            "checks": [{"name": name, "passed": True} for name in sorted(self.runner.CHECKS)],
        }

    def encoded(self, report=None, size=None):
        report = self.report() if report is None else report
        if size is not None:
            report["ownedTransferPadding"] = ""
            count = size - len(json.dumps(report, separators=(",", ":")).encode())
            self.assertGreaterEqual(count, 0)
            report["ownedTransferPadding"] = "x" * count
        result = json.dumps(report, separators=(",", ":")).encode()
        if size is not None:
            self.assertEqual(size, len(result))
        return result

    def completion(self, data, passed=True, count=None, code=-1):
        return (
            f"INSTRUMENTATION_RESULT: runtimePassed={str(passed).lower()}\n"
            f"INSTRUMENTATION_RESULT: runtimeCheckCount={len(self.runner.CHECKS) if count is None else count}\n"
            f"INSTRUMENTATION_RESULT: runtimeReportBytes={len(data)}\n"
            f"INSTRUMENTATION_RESULT: runtimeReportSha256={hashlib.sha256(data).hexdigest()}\n"
            f"INSTRUMENTATION_CODE: {code}\n"
        ).encode()

    def main(self, fake):
        with patch.object(self.runner, "ROOT", self.root), \
                patch.object(self.runner.subprocess, "run", fake.run), \
                patch.object(sys, "argv", [str(SOURCE), str(self.output)]), \
                patch.dict(os.environ, {"ANDROID_HOME": str(self.root / "sdk"),
                                        "RUNTIME_EXPECTED_API": "35"}), \
                redirect_stdout(io.StringIO()):
            self.runner.main()

    def receipt(self):
        return json.loads((self.output / "report-transfer.json").read_text())

    def test_successful_exit_truncation_then_complete_eight_mib_report_does_not_rerun_checks(self):
        # Reproduce the exact observed successful-exit transfer lengths.
        data = self.encoded(size=8_430_418)
        fake = FakeAdb(self.runner, self.completion(data), [(0, data[:2_948_608]), (0, data)])
        self.main(fake)
        self.assertEqual(data, (self.output / "worker-runtime-report.json").read_bytes())
        self.assertFalse((self.output / "worker-runtime-report.partial").exists())
        self.assertFalse((self.output / "worker-runtime-report.pending").exists())
        receipt = self.receipt()
        self.assertEqual("complete", receipt["status"])
        self.assertEqual([False, True], [attempt["accepted"] for attempt in receipt["attempts"]])
        self.assertEqual(2_948_608, receipt["attempts"][0]["receivedBytes"])
        self.assertEqual(8_430_418, receipt["completion"]["runtimeReportBytes"])
        self.assertEqual(2, fake.count(self.runner.REPORT_COMMAND))
        self.assertEqual(2, fake.count(["install"]))
        self.assertEqual(1, fake.count(["shell", "am", "instrument"]))

    def test_three_truncated_reads_keep_last_partial_and_structural_receipt_without_parsing(self):
        data = self.encoded()
        reads = [(0, data[:13]), (0, data[:27]), (0, data[:91])]
        fake = FakeAdb(self.runner, self.completion(data), reads)
        with self.assertRaises(self.runner.ReportTransferError):
            self.main(fake)
        self.assertFalse((self.output / "worker-runtime-report.json").exists())
        self.assertEqual(data[:91], (self.output / "worker-runtime-report.partial").read_bytes())
        receipt = self.receipt()
        self.assertEqual("transfer-failed", receipt["status"])
        self.assertEqual([13, 27, 91], [attempt["receivedBytes"] for attempt in receipt["attempts"]])
        self.assertEqual(3, fake.count(self.runner.REPORT_COMMAND))
        self.assertEqual(1, fake.count(["shell", "am", "instrument"]))
        self.assertLess(len(json.dumps(receipt)), 10000)

    def test_same_length_corruption_never_publishes(self):
        data = self.encoded()
        corrupt = bytes([data[0] ^ 1]) + data[1:]
        fake = FakeAdb(self.runner, self.completion(data), [(0, corrupt)] * 3)
        with self.assertRaises(self.runner.ReportTransferError):
            self.runner.capture_completed_report(fake.read, self.output, fake.log)
        self.assertFalse((self.output / "worker-runtime-report.json").exists())
        self.assertEqual(corrupt, (self.output / "worker-runtime-report.partial").read_bytes())
        for attempt in self.receipt()["attempts"]:
            self.assertTrue(attempt["lengthMatched"])
            self.assertFalse(attempt["sha256Matched"])
            self.assertFalse(attempt["accepted"])

    def test_nonzero_read_exit_cannot_pass_even_with_matching_bytes_and_sha(self):
        data = self.encoded()
        fake = FakeAdb(self.runner, self.completion(data), [(1, data), (-9, data), (2, data)])
        with self.assertRaises(self.runner.ReportTransferError):
            self.runner.capture_completed_report(fake.read, self.output, fake.log)
        self.assertFalse((self.output / "worker-runtime-report.json").exists())
        for attempt in self.receipt()["attempts"]:
            self.assertTrue(attempt["lengthMatched"] and attempt["sha256Matched"])
            self.assertFalse(attempt["accepted"])

    def test_nonzero_exit_and_timeout_can_retry_only_the_report(self):
        data = self.encoded()
        timeout = subprocess.TimeoutExpired("fake-adb", 60, output=data[:17])
        fake = FakeAdb(self.runner, self.completion(data), [(1, data), timeout, (0, data)])
        captured, identity = self.runner.capture_completed_report(fake.read, self.output, fake.log)
        self.assertEqual(data, captured)
        self.assertEqual(len(data), identity["runtimeReportBytes"])
        self.assertEqual("timeout", self.receipt()["attempts"][1]["error"])
        self.assertEqual(3, len(fake.commands))
        self.assertTrue(all(command == self.runner.REPORT_COMMAND for command in fake.commands))

    def test_launch_failures_and_timeouts_retain_the_last_available_partial(self):
        data = self.encoded()
        fake = FakeAdb(self.runner, self.completion(data), [
            OSError("owned fake ADB launch failure"),
            subprocess.TimeoutExpired("fake-adb", 60, output=data[:11]),
            subprocess.TimeoutExpired("fake-adb", 60, output=data[:29]),
        ])
        with self.assertRaises(self.runner.ReportTransferError):
            self.runner.capture_completed_report(fake.read, self.output, fake.log)
        self.assertFalse((self.output / "worker-runtime-report.json").exists())
        self.assertEqual(data[:29], (self.output / "worker-runtime-report.partial").read_bytes())
        receipt = self.receipt()
        self.assertEqual("transfer-failed", receipt["status"])
        self.assertEqual(["adb-launch-failed", "timeout", "timeout"],
                         [attempt["error"] for attempt in receipt["attempts"]])

    def test_missing_duplicate_malformed_or_unbounded_completion_refuses_before_read(self):
        data = self.encoded()
        good = self.completion(data)
        lines = good.splitlines(keepends=True)
        bad = []
        for index, line in enumerate(lines):
            bad.extend([b"".join(lines[:index] + lines[index + 1:]), good + line])
        replacements = {
            b"runtimePassed=true": [b"runtimePassed=TRUE", b"runtimePassed=true junk"],
            f"runtimeCheckCount={len(self.runner.CHECKS)}".encode(): [
                b"runtimeCheckCount=-1", b"runtimeCheckCount=0.0", b"runtimeCheckCount=9999"],
            f"runtimeReportBytes={len(data)}".encode(): [
                b"runtimeReportBytes=0", b"runtimeReportBytes=-1", b"runtimeReportBytes=+2",
                b"runtimeReportBytes=2 ", b"runtimeReportBytes=1.5",
                f"runtimeReportBytes={self.runner.MAX_REPORT_BYTES + 1}".encode(),
                b"runtimeReportBytes=" + b"9" * 5000,
                b"runtimeReportBytes:2"],
            hashlib.sha256(data).hexdigest().encode(): [b"a" * 63, b"g" * 64, b"A" * 64],
            b"INSTRUMENTATION_CODE: -1": [b"INSTRUMENTATION_CODE: -2", b"INSTRUMENTATION_CODE: -1 junk"],
        }
        for original, values in replacements.items():
            bad.extend(good.replace(original, value) for value in values)
        for log in bad:
            with self.subTest(log=log[:130]):
                (self.output / "worker-runtime-report.json").write_bytes(b"stale prior success")
                fake = FakeAdb(self.runner, log, [])
                with self.assertRaises(self.runner.ReportTransferError):
                    self.runner.capture_completed_report(fake.read, self.output, log)
                self.assertEqual([], fake.commands)
                self.assertFalse((self.output / "worker-runtime-report.json").exists())
                receipt = self.receipt()
                self.assertEqual("invalid-completion", receipt["status"])
                self.assertEqual([], receipt["attempts"])

    def test_actual_oversized_read_retains_only_a_capped_partial(self):
        data = b"{}"
        with patch.object(self.runner, "MAX_REPORT_BYTES", 512):
            fake = FakeAdb(self.runner, self.completion(data), [(0, b"x" * 700)] * 3)
            with self.assertRaises(self.runner.ReportTransferError):
                self.runner.capture_completed_report(fake.read, self.output, fake.log)
        self.assertFalse((self.output / "worker-runtime-report.json").exists())
        self.assertEqual(b"x" * 512, (self.output / "worker-runtime-report.partial").read_bytes())
        self.assertTrue(all(attempt["partialCapped"] for attempt in self.receipt()["attempts"]))

    def test_complete_failed_native_run_is_retained_but_never_passes_success_gates(self):
        report = self.report()
        report.update(passed=False, failure="owned native failure fixture", checks=report["checks"][:3])
        data = self.encoded(report)
        fake = FakeAdb(self.runner, self.completion(data, passed=False, count=3, code=0), [(0, data)])
        with self.assertRaises(AssertionError):
            self.main(fake)
        self.assertEqual(data, (self.output / "worker-runtime-report.json").read_bytes())
        self.assertEqual("complete", self.receipt()["status"])
        self.assertFalse(self.receipt()["completion"]["runtimePassed"])
        self.assertEqual(3, self.receipt()["completion"]["runtimeCheckCount"])
        self.assertEqual(1, fake.count(["shell", "am", "instrument"]))

    def test_semantic_source_check_and_instrumentation_gates_remain_required(self):
        cases = []
        for key, value in [
            ("sourceRevision", "wrong-source"), ("sourceDirty", True),
            ("applicationUid", 54321), ("sdk", 26), ("abis", ["arm64-v8a"]),
        ]:
            report = self.report()
            report[key] = value
            cases.append((report, {}, 0, b""))
        for mutation in ["wrong-name", "duplicate-name", "check-failed"]:
            report = self.report()
            if mutation == "wrong-name":
                report["checks"][0]["name"] = "wrong-check"
            elif mutation == "duplicate-name":
                report["checks"][0]["name"] = report["checks"][1]["name"]
            else:
                report["checks"][0]["passed"] = False
            cases.append((report, {}, 0, b""))
        cases.extend([
            (self.report(), {"count": 12}, 0, b""),
            (self.report(), {"passed": False}, 0, b""),
            (self.report(), {"code": 0}, 0, b""),
            (self.report(), {}, 1, b""),
            (self.report(), {}, 0, b"INSTRUMENTATION_FAILED: owned fixture\n"),
        ])
        for report, completion, invocation_exit, suffix in cases:
            with self.subTest(report=report, completion=completion, invocation_exit=invocation_exit):
                data = self.encoded(report)
                fake = FakeAdb(self.runner, self.completion(data, **completion) + suffix, [(0, data)])
                fake.instrumentation_exit = invocation_exit
                with self.assertRaises(AssertionError):
                    self.main(fake)
                self.assertEqual(data, (self.output / "worker-runtime-report.json").read_bytes())
                self.assertEqual(1, fake.count(["shell", "am", "instrument"]))
                self.assertEqual(1, fake.count(self.runner.REPORT_COMMAND))

    def test_identity_matched_invalid_json_is_retained_then_refused_by_parser(self):
        data = b"not a JSON report"
        fake = FakeAdb(self.runner, self.completion(data), [(0, data)])
        with self.assertRaises(json.JSONDecodeError):
            self.main(fake)
        self.assertEqual(data, (self.output / "worker-runtime-report.json").read_bytes())
        self.assertEqual("complete", self.receipt()["status"])

    def test_early_install_failure_cannot_leave_a_stale_canonical_report(self):
        (self.output / "worker-runtime-report.json").write_bytes(b"stale prior success")
        (self.output / "report-transfer.json").write_text('{"status":"complete"}')
        data = self.encoded()
        fake = FakeAdb(self.runner, self.completion(data), [])
        fake.install_exit = 1
        with self.assertRaises(RuntimeError):
            self.main(fake)
        self.assertFalse((self.output / "worker-runtime-report.json").exists())
        self.assertFalse((self.output / "report-transfer.json").exists())
        self.assertEqual(0, fake.count(["shell", "am", "instrument"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)

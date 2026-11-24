#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest
import warnings
import zipfile

spec = importlib.util.spec_from_file_location("scorer", Path(__file__).resolve().parents[1] / "scripts/score-nist-recovery.py")
scorer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scorer)


class RecoveryScoringTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.zip = Path(self.temp.name) / "recovered.zip"
        self.payloads = {"not-the-original-name.bin": b"alpha payload", "other.bin": b"beta payload"}
        self.oracle = {"sourceSha256": "a" * 64, "sourceSizeBytes": 100, "files": [
            {"name": "reference.mp4", "offset": 10, "sizeBytes": 13,
             "sha256": hashlib.sha256(b"alpha payload").hexdigest()},
            {"name": "reference.wav", "offset": 40, "sizeBytes": 12,
             "sha256": hashlib.sha256(b"beta payload").hexdigest()}]}
        self.manifest = {"schema": "org.diemobile.recovered-files", "schemaVersion": 1,
                         "sourceSha256": "a" * 64, "sourceSizeBytes": 100, "files": [
            {"file": name, "physicalOffset": offset, "sizeBytes": len(data),
             "sha256": hashlib.sha256(data).hexdigest(), "mime": "application/octet-stream"}
            for (name, data), offset in zip(self.payloads.items(), [10, 40])]}

    def write(self, extra=None):
        with zipfile.ZipFile(self.zip, "w") as archive:
            for name, payload in self.payloads.items():
                archive.writestr(name, payload)
            if extra:
                extra(archive)
            archive.writestr("_recovery.json", json.dumps(self.manifest))

    def test_exact_bytes_not_original_filenames_or_mime(self):
        self.write()
        result = scorer.score(self.zip, self.oracle)
        self.assertTrue(result["allReferenceFilesRecovered"])
        self.assertEqual(result["exactReferenceFiles"], 2)
        self.assertEqual(result["referenceFiles"][0]["exactExportMatches"], ["not-the-original-name.bin"])

    def test_header_only_is_missing(self):
        del self.payloads["other.bin"]
        self.manifest["files"].pop()
        self.write()
        result = scorer.score(self.zip, self.oracle)
        self.assertFalse(result["allReferenceFilesRecovered"])
        self.assertEqual(result["referenceFiles"][1]["status"], "missing")

    def test_false_manifest_digest_cannot_replace_actual_payload(self):
        self.payloads["not-the-original-name.bin"] = b"wrong payload"
        self.write()
        with self.assertRaises(ValueError):
            scorer.score(self.zip, self.oracle)

    def test_wrong_source_and_wrong_offsets_refused_or_missing(self):
        self.manifest["sourceSha256"] = "b" * 64
        self.write()
        with self.assertRaises(ValueError):
            scorer.score(self.zip, self.oracle)
        self.manifest["sourceSha256"] = "a" * 64
        self.manifest["files"][0]["physicalOffset"] = 11
        self.write()
        self.assertEqual(scorer.score(self.zip, self.oracle)["exactReferenceFiles"], 1)

    def test_unindexed_and_duplicate_entries_refused(self):
        self.write(lambda archive: archive.writestr("unindexed.bin", b"extra"))
        with self.assertRaises(ValueError):
            scorer.score(self.zip, self.oracle)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            self.write(lambda archive: archive.writestr("other.bin", b"duplicate"))
        with self.assertRaises(ValueError):
            scorer.score(self.zip, self.oracle)

    def test_additional_and_duplicate_exact_exports_are_retained(self):
        item = copy.deepcopy(self.manifest["files"][0])
        item["file"] = "duplicate.bin"
        self.payloads[item["file"]] = b"alpha payload"
        self.manifest["files"].append(item)
        extra = {"file": "extra.bin", "physicalOffset": 75, "sizeBytes": 5,
                 "sha256": hashlib.sha256(b"extra").hexdigest()}
        self.payloads[extra["file"]] = b"extra"
        self.manifest["files"].append(extra)
        self.write()
        result = scorer.score(self.zip, self.oracle)
        self.assertEqual(len(result["referenceFiles"][0]["exactExportMatches"]), 2)
        self.assertEqual([item["file"] for item in result["additionalExports"]], ["extra.bin"])

    def test_symlink_and_unsafe_name_refused(self):
        def symlink(archive):
            entry = zipfile.ZipInfo("link.bin")
            entry.create_system = 3
            entry.external_attr = (stat.S_IFLNK | 0o777) << 16
            archive.writestr(entry, b"outside")
        self.manifest["files"].append({"file": "link.bin", "physicalOffset": 80,
                "sizeBytes": 7, "sha256": hashlib.sha256(b"outside").hexdigest()})
        self.write(symlink)
        with self.assertRaises(ValueError):
            scorer.score(self.zip, self.oracle)
        self.manifest["files"][-1]["file"] = "../outside.bin"
        self.write()
        with self.assertRaises(ValueError):
            scorer.score(self.zip, self.oracle)

    def test_crc_corruption_refused(self):
        self.write()
        data = self.zip.read_bytes()
        self.zip.write_bytes(data.replace(b"alpha payload", b"wrong payload", 1))
        with self.assertRaises(zipfile.BadZipFile):
            scorer.score(self.zip, self.oracle)

    def test_duplicate_json_keys_refused(self):
        with self.assertRaises(ValueError):
            json.loads('{"sizeBytes": 1, "sizeBytes": 2}', object_pairs_hook=scorer.unique_object)


if __name__ == "__main__":
    unittest.main()

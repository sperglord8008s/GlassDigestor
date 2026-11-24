#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise wrapper output ownership with fake CMake; no native code or dependencies are built."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
OUTPUT_VARIABLES = ("DIE_ARCHIVE_JNI_ROOT", "DIE_ARCHIVE_ASSETS_DIR", "DIE_PHOTOREC_JNI_ROOT")


class NativeOutputOwnershipTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="native-output-ownership-")
        self.root = Path(self.temporary.name)
        scripts = self.root / "scripts"
        scripts.mkdir()
        for name in ("build-archive.sh", "build-photorec.sh"):
            shutil.copy2(ROOT / "scripts" / name, scripts / name)
        for name in ("build-archive-libraries.sh", "fetch-photorec.sh"):
            (scripts / name).write_text("#!/usr/bin/env bash\nset -euo pipefail\n")
        for name in ("archive-notices.py", "photorec-notices.py"):
            (scripts / name).write_text("import sys\nassert sys.argv[1:] == ['--check']\n")
        (self.root / "archive-engine").mkdir()
        (self.root / "carving-engine").mkdir()
        (self.root / "archive-engine/NOTICE.txt").write_bytes(b"owned app archive notices\n")
        (self.root / "archive-engine/source-headers.txt").write_bytes(b"owned original source headers\n")
        self.bin = self.root / "fake tools"
        self.bin.mkdir()
        cmake = self.bin / "cmake"
        cmake.write_text(
            f"#!{sys.executable}\n"
            "import json,sys\n"
            "from pathlib import Path\n"
            "args=sys.argv[1:]\n"
            "if args[0]=='--build':\n"
            "    directory=Path(args[1])\n"
            "    state=json.loads((directory/'owned-fixture-configuration.json').read_text())\n"
            "    name='libdiearchive.so' if state['source']=='archive-engine' else 'libdiephotorec.so'\n"
            "    (directory/name).write_bytes(('owned-fake:'+name+':'+state['abi']).encode())\n"
            "else:\n"
            "    directory=Path(args[args.index('-B')+1])\n"
            "    directory.mkdir(parents=True,exist_ok=True)\n"
            "    abi=next((arg.split('=',1)[1] for arg in args if arg.startswith('-DANDROID_ABI=')),'host')\n"
            "    state={'source':args[args.index('-S')+1],'abi':abi,'arguments':args}\n"
            "    (directory/'owned-fixture-configuration.json').write_text(json.dumps(state))\n"
        )
        cmake.chmod(0o755)

    def tearDown(self):
        self.temporary.cleanup()

    def run_wrapper(self, engine, mode="android", abi="arm64-v8a", overrides=None):
        environment = os.environ.copy()
        for variable in OUTPUT_VARIABLES:
            environment.pop(variable, None)
        environment.update({
            "PATH": str(self.bin) + os.pathsep + environment.get("PATH", ""),
            "ANDROID_NDK_HOME": str(self.root / "owned NDK fixture"),
            "DIE_ANDROID_ABI": abi,
        })
        environment.update(overrides or {})
        subprocess.run(
            ["bash", str(self.root / f"scripts/build-{engine}.sh"), mode],
            cwd=self.root, env=environment, check=True, capture_output=True,
        )

    def library(self, engine, abi, root=None):
        root = root or self.root / f"app/build/generated-{engine}-jni"
        name = "diearchive" if engine == "archive" else "diephotorec"
        return root / abi / f"lib{name}.so"

    def test_archive_app_and_lab_libraries_and_notices_have_independent_owners(self):
        self.run_wrapper("archive")
        app = self.library("archive", "arm64-v8a")
        app_bytes = app.read_bytes()
        app_notices = self.root / "app/build/generated-archive-assets/notices/archive-codecs.txt"
        original_notices = app_notices.read_bytes()
        runtime_jni = self.root / "runtime worker build/generated archive jni"
        runtime_assets = self.root / "runtime worker build/generated archive assets"
        (self.root / "archive-engine/NOTICE.txt").write_bytes(b"owned runtime archive notices\n")
        self.run_wrapper("archive", abi="x86_64", overrides={
            "DIE_ARCHIVE_JNI_ROOT": str(runtime_jni),
            "DIE_ARCHIVE_ASSETS_DIR": str(runtime_assets),
        })
        lab = self.library("archive", "x86_64", runtime_jni)
        self.assertIn(b":x86_64", lab.read_bytes())
        self.assertIn(b":arm64-v8a", app_bytes)
        self.assertEqual(app_bytes, app.read_bytes())
        self.assertEqual(original_notices, app_notices.read_bytes())
        self.assertEqual(b"owned runtime archive notices\n",
                         (runtime_assets / "notices/archive-codecs.txt").read_bytes())
        self.assertEqual((self.root / "archive-engine/source-headers.txt").read_bytes(),
                         (runtime_assets / "notices/archive-source-headers.txt").read_bytes())
        self.assertEqual((self.root / "archive-engine/NOTICE.txt").read_bytes(),
                         (lab.parent / "NOTICE.txt").read_bytes())
        self.assertFalse(self.library("archive", "x86_64").exists())
        self.assertFalse(self.library("archive", "arm64-v8a", runtime_jni).exists())
        self.assertTrue((self.root / "app/build/archive-android").is_dir())
        self.assertTrue((self.root / "app/build/archive-android-x86_64").is_dir())

    def test_photorec_lab_output_cannot_replace_the_app_abi_or_enter_app_packaging(self):
        self.run_wrapper("photorec")
        app = self.library("photorec", "arm64-v8a")
        app_bytes = app.read_bytes()
        relative_root = Path("runtime worker build/generated photorec jni")
        self.run_wrapper("photorec", abi="x86_64", overrides={
            "DIE_PHOTOREC_JNI_ROOT": str(relative_root),
        })
        lab_root = self.root / relative_root
        self.assertIn(b":x86_64", self.library("photorec", "x86_64", lab_root).read_bytes())
        self.assertEqual(app_bytes, app.read_bytes())
        self.assertFalse(self.library("photorec", "x86_64").exists())
        self.assertFalse(self.library("photorec", "arm64-v8a", lab_root).exists())
        self.assertTrue((self.root / "app/build/photorec-android").is_dir())
        self.assertTrue((self.root / "app/build/photorec-android-x86_64").is_dir())

    def test_android_destination_overrides_do_not_redirect_host_outputs(self):
        forbidden = self.root / "unused Android output"
        overrides = {variable: str(forbidden / variable) for variable in OUTPUT_VARIABLES}
        for engine, library in [("archive", "libdiearchive.so"), ("photorec", "libdiephotorec.so")]:
            with self.subTest(engine=engine):
                self.run_wrapper(engine, mode="host", abi="x86_64", overrides=overrides)
                output = self.root / f"app/build/host-{engine}" / library
                self.assertIn(b":host", output.read_bytes())
        self.assertFalse(forbidden.exists())
        self.assertFalse((self.root / "app/build/generated-archive-assets").exists())

    def test_empty_optional_destinations_keep_existing_android_defaults(self):
        overrides = {variable: "" for variable in OUTPUT_VARIABLES}
        for engine in ("archive", "photorec"):
            with self.subTest(engine=engine):
                self.run_wrapper(engine, overrides=overrides)
                self.assertIn(b":arm64-v8a", self.library(engine, "arm64-v8a").read_bytes())
        self.assertTrue((self.root / "app/build/generated-archive-assets/notices/archive-codecs.txt").is_file())


if __name__ == "__main__":
    unittest.main(verbosity=2)

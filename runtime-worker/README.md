# Android worker runtime laboratory

This module exercises the actual production `BinaryWorkerService`,
`BinaryWorkerClient`, hash/archive/PhotoRec JNI and descriptor acquisition on Android.
It is a separate **test-only x86_64** application,
`org.diemobile.runtimecheck`, with no launcher or network permission. The
production ARM64 application remains `org.diemobile` with its existing signing
configuration. This module is not an alternative user build.

`stageWorkerSources` copies an explicit set of unchanged production files into
an isolated source root. The static verifier compares every staged byte to its
original. There is no substitute service, fake native engine or test opcode in
production. Unexercised Binwalk/MPV Java bridge references are retained;
the laboratory packages the real hash, archive and PhotoRec native libraries. Google
resource-parser Java dependencies are compiled because the shared service
references them. The new seventh case exercises the actual resource worker
and package-declaration model. An eighth case now checks original PhotoRec PDF
file recovery through actual Bionic read-only relative stdio.

Actual worker source `26ad7d6a2b5f0e7f676d4f8373b6dcf9845eebba` passed
all five original contracts on API 26 and 35 in run
[`44729667760`](https://github.com/sperglord8008s/DiE-Android-/actions/runs/44729667760).
Downloaded reports, source/APK identities and artifact digests were independently
checked; [retained summary](../docs/verification/android-worker-runtime-26ad7d6.json).
All six contracts also passed at `8f2ab38af319a02c85618a0f398d654c1b48c15a`
in run [`44733208678`](https://github.com/sperglord8008s/DiE-Android-/actions/runs/44733208678);
[download-verified summary](../docs/verification/android-worker-runtime-8f2ab38.json).
All nine contracts passed at `04e93c0` on actual API 26/35 in run
44967525584, including >9 MiB native PDF recovery and exact recovered two-page
rendering. [Download-verified summary](../docs/verification/android-worker-runtime-04e93c0.json).
Pre-alpha.17 source `7c80322` passes all ten contracts in run `45387599711`.
[Download-verified clipboard/URI and native evidence](../docs/verification/android-worker-runtime-7c80322.json).

## Reproduce

Use JDK 17, Gradle 8.11.1, SDK/build-tools 35, NDK 28.2.13676358 and CMake
3.22.1. Owned pinned video and Python-generated PCM fixtures need no FFmpeg
installation; the optional [regeneration recipe](../tests/fixtures/media/README.md)
retains its original FFmpeg command/version. Fetches use the existing
pinned source/patch/checksum contracts. Retain
the dependency caches and toolchains for later `--offline` builds.

```sh
bash scripts/fetch-engine.sh
./gradlew --no-daemon :runtime-worker:assembleDebug :runtime-worker:assembleDebugAndroidTest
python3 scripts/verify-worker-runtime.py
# Select exactly one running, disposable x86_64 API 26 or API 35 emulator.
# ANDROID_SERIAL can select it when more than one device is connected.
RUNTIME_EXPECTED_API=35 python3 scripts/run-worker-runtime.py
```

Runtime evidence requires a clean committed source checkout; local dirty
compilation/static inspection is supported but is not accepted as CI runtime
evidence. `adb install -t` installs only the two generated trusted test APKs;
the laboratory does not install or execute an inspected application.

The separate [runtime workflow](../.github/workflows/android-worker-runtime.yml)
executes both API levels with KVM on disposable GitHub runners. Its emulator
action is pinned to source commit
`a421e43855164a8197daf9d8d40fe71c6996bb0d` (Apache-2.0, build-only). Emulator
system images are SDK-managed test tools; captured `getprop` records the actual
Android build. This is separate from the existing five-job ARM64/source release
gate. A workflow file or successful APK compilation is not runtime evidence.

## What is checked

The first nine below passed at the exact recorded sources. The sixth exercises
the production gallery helper in the normal target UID, separately from the
isolated worker. No host codec shadow counts as platform runtime verification.

1. A real normal-UID client binds the private isolated service, passes a
   read-only descriptor and receives all eight native digests. UID and engine
   identity, complete byte counts, MD4 and CRC32 known vectors are asserted.
2. A fully written **16 MiB + 17 byte** patterned file crosses that same
   boundary. Six native MD/SHA digests and CRC32 are compared with independent
   platform JCA/CRC implementations; no sparse/truncated fixture substitutes for
   the full input.
3. A gzip behind a 37-byte prefix and a misleading `.jpeg` name is inventoried
   and extracted through actual read/write descriptors and libarchive. Every
   decoded byte and an independent payload digest are compared. The normal
   client's input/output alias guard must refuse before modifying the source;
   the source identity is rechecked. Host native fixtures separately cover the
   JNI alias guard.
4. A real report pipe is filled to `EAGAIN`, returned to blocking mode and sent
   to the actual worker. A wrong request token must leave the worker alive;
   the correct token must disconnect the blocked worker while the parent
   application survives. No oversized report or timing-sensitive large parse
   is needed to keep the request active.
5. A fresh ordinary client completes another native hash request after worker
   death, demonstrating recovery.
6. Generated solid-green 64×48 MPEG-4 and 500 ms PCM clips are embedded behind
   a prefix and before a trailer. The unchanged production `MediaFrame` helper
   must pass only the selected descriptor interval, retain real metadata and
   null timestamp semantics, decode a bounded independently expected green
   frame on API 35, and use metadata without unbounded frame decoding on API 26.
   Audio-only input must not acquire an invented frame. Both API jobs passed;
   API 35 returned a 24×18 frame with independently expected green center
   pixel `ff018001`, and both reported 1000/500 ms video/audio duration.
7. The installed trusted laboratory APK supplies its real compiled manifest.
   ZIP size/CRC are independently checked, then actual `resources` worker
   Binder/PFD parsing and unchanged `PackageManifest` interpretation must match
   the known target package ID and API 26/35 SDK declarations. The complete
   resource report and original literal attributes are retained. This case is
   passed on source 123193d in run 44776359034.
8. The actual PhotoRec JNI library receives a descriptor for an owned PDF behind
   unrelated prefix/suffix bytes and a misleading image name. Adapter/mode/read
   bounds and isolated worker identity are checked; its native PDF extent must
   recover every original payload byte. A missing-footer variant must remain
   unresolved without crashing the worker. This exercises Bionic `funopen`,
   relative seek/read and the original callback; it does not execute a PDF renderer.

9. A two-page red/green PDF is recovered from a prefixed mixed blob through the
   actual isolated PhotoRec worker, compared byte-for-byte, and rendered by the
   unchanged production `PdfPagePreview` helper. Known page colors, point/pixel
   geometry, exact descriptor scope, cleanup and out-of-range refusal are checked.
10. A non-exported test-only foreground window satisfies Android clipboard focus
    policy. Unchanged production `TextActions` copies exact UTF-16 including NUL,
    bidi controls, supplementary text and an isolated surrogate. The platform
    clipboard must return identical units; API 35 must retain the sensitive flag.
    Oversized refusal preserves the previous clip. All seven browser-engine URI
    encodings must round-trip the valid query and use English translation targets;
    isolated surrogates are refused. The test never launches a browser or contacts
    a provider. No full production UI/service-availability claim follows. Shared
    helper sources and required resources are statically compared byte-for-byte.

The instrumentation writes a complete private JSON report even on failure.
The runner retains that report, instrumentation/install logs, emulator
properties and Android process logs. `runtime-build.json` records source-file
and APK SHA-256 identities; the runner rechecks the APKs before installation.
CI uploads these with the signed test APKs under
`android-worker-runtime-api-26` and `android-worker-runtime-api-35`.

## Limits and licensing

The original five checks verify the stated x86 Android worker contracts at the
exact recorded source. Additional cases need their own successful execution.
They do not verify production ARM64, a physical handset, SAF grants/providers,
full UI/accessibility, external browser/translation services, other engines, arbitrary PDF or playback, hostile-input
resilience, all crash modes or 16 KiB-page execution. See
[feature parity](../docs/FEATURE-PARITY.md) for current results and gaps.

The laboratory and adapters are GPL-3.0-only. The APK includes the original
CDiE/xxfclib, RHash, libarchive/codec, Google parser and Guava notices. Source
pins, unmodified upstream files, reviewed patches and build recipes remain in
the same repository modules as production. Compiler caches and build outputs
are ignored and are not repository source.


PhotoRec's x86 build uses the same pinned sources and default adapter as production
ARM64, selected with `DIE_ANDROID_ABI=x86_64 bash scripts/build-photorec.sh android`.
The static verifier binds every adapter/contract/fixture input hash, proves the
packaged library equals the NDK-stripped source build, and retains both library
hashes. PhotoRec notices are included. Runtime-only C/C++ Debug flags use `-g0`
to avoid large unused DWARF intermediates; production native build flags are
unchanged. Assertions and optimization defaults otherwise retain Debug behavior.


The full evidence report stays in the target's private file and is captured by
`run-as`. Instrumentation's Binder result returns only a small status, count,
byte size, SHA-256 and report path. The runner checks this identity against the
complete captured bytes. Source 531dff4's eight checks completed on Android, but
its previous duplicate pretty-report return caused TransactionTooLargeException;
that workflow remains failed, and the bounded completion fix must pass anew.


All eight contracts and bounded completion passed on actual API 26/35 in run
44786667682 at source 431f98d. Complete downloaded evidence and every source/
APK/native-library identity were independently checked;
[summary](../docs/verification/android-worker-runtime-431f98d.json).
The next adapter-5 source extends the PDF case to a file larger than 9 MiB,
checking its exact native bytes and explicit optional-date work limit. That large-file contract later passed at `04e93c0`, as recorded above.


## PDF rasterization extension (published pre-alpha.16)

The existing eight contracts passed at source 2dcfce3;
[downloaded evidence](../docs/verification/android-worker-runtime-2dcfce3.json).
A ninth `exact-recovered-platform-pdf-pages` contract now passes. It recovers
an owned 692-byte, two-page red/green PDF from a prefixed mislabeled blob through
the real isolated PhotoRec service, then calls the unchanged production
`PdfPagePreview` helper in the normal UID. It requires exact bytes/SHA, 144×96
point geometry, 24×16 thumbnails, red/green center pixels, 1536×1024 full-view
pixels, original parent/range identities, out-of-range refusal and stage cleanup.
The shared helper/OwnedContent/PreparedExport source bytes are included in the
static identity check. All nine contracts pass on actual x86 API 26/35 at source
`04e93c0`, push run 44967525584 and PR run 44967529605. Downloaded source, both
APKs, native PhotoRec library, full report and bounded completion identities,
plus expected page colors/geometry, were independently verified;
[evidence](../docs/verification/android-worker-runtime-04e93c0.json). This tests the rendering
helper, not a full gallery/SAF/handset UI.

# MPV file investigation integration

The app uses actual **libmpv**, copied without modification from the official mpv-android **2026-09-17 ARM64 release**. Its runtime string is `mpv v0.41.0-1049-g0b7ed670f`; the published mpv source revision is `0b7ed670f7c353dd3dd4f8ae0fc788a181a15aa6`. The upstream build recipes are pinned at `fdf74f6830c47dbaa8a22ac79726e8303f1db5af` and use NDK r30. The app-owned descriptor/typed-node adapter is compiled from [probe.c](probe.c) and [frame.c](frame.c) using the project's existing NDK 28.2.13676358 and API 26 target. The copied runtime was built for API 23 and its nine native libraries have static 16 KiB ELF alignment. Actual Android execution remains device-unverified.

`runtime-lock.json` pins the complete official APK SHA-256, each of the nine copied libraries and exact upstream client/stream API headers. The official player app, its Kotlin code, `libplayer.so`, permissions and network UI are not imported. The copied engine/runtime libraries are libmpv, libavcodec, libavdevice, libavfilter, libavformat, libavutil, libswresample, libswscale and libc++_shared. The application remains offline and requests no INTERNET permission.

## Build and offline reuse

With the regular JDK 17 / SDK 35 / NDK 28.2.13676358 toolchain:

```sh
bash scripts/build-mpv.sh android
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

The Gradle `buildMpvAndroid` task also builds the adapter and verifies/acquires the pinned runtime when native inputs change. `MPV_DEPS_DIR` chooses the runtime archive cache. After acquisition, `./gradlew --offline` forwards `DIE_OFFLINE=true`; missing/corrupt cached inputs fail explicitly. Downloads are build-time operations, never app behavior. Unmodified runtime library bytes are checked again inside the packaged APK. The probe library is built locally from the checked-in C source; the larger MPV/FFmpeg runtime is the upstream prebuilt artifact. Bit-identical source reconstruction of that upstream artifact is not claimed.

## Complete component sources and licenses

`source-lock.json` pins all fifteen published source components, plus eight exact Git submodules needed by the original build recipes. This includes FFmpeg `094a2f8a2a5e7fa64736e067de224ce28fdf5979`, dav1d, mbedTLS, libxml2, FreeType/dlg, Fontconfig, FriBidi, HarfBuzz, libunibreak, libass, Lua, libplacebo and curl. Supporting libplacebo generator/header/demo submodules are retained as source even when they are not linked into this Android runtime. Complete upstream license files and leading source attribution comments are bundled in `assets/notices/mpv-runtime.txt`; exact libc++/ABI/unwind/compiler-runtime license files from the NDK r30 LLVM source revision are retained separately under `licenses/` and included in that notice. The Android FFmpeg binary uses `--enable-gpl --enable-version3`; the combined distribution follows the app's GPL-3.0-only terms. These are FOSS engines; no proprietary codec package is added.

```sh
python3 scripts/fetch-mpv-sources.py --bundle
python3 scripts/mpv-notices.py --check
python3 scripts/mpv-references.py --check
```

`MPV_SOURCE_DIR` chooses the source-archive cache. CI publishes `mpv-android-2026-09-17-component-sources.zip` beside its build artifacts. The bundle retains the exact source archives, submodule locks, integration instructions and original source-build preparation scripts. Existing app/CDiE/Binwalk source and their acquisition instructions remain in the public repository. This component-source artifact is specific to the new MPV runtime.

To prepare the original engine's source build in a **fresh directory**:

```sh
python3 scripts/prepare-mpv-source-build.py /path/to/new-mpv-source-build
cd /path/to/new-mpv-source-build/mpv-android/buildscripts
# Supply the upstream-required NDK r30 / SDK and Linux build prerequisites.
# Read buildscripts/README.md and buildscripts/include/{depinfo,path}.sh.
./buildall.sh --arch arm64 mpv
```

For a downloaded component bundle, pass `--cache /path/to/bundle/sources --lock /path/to/bundle/integration/source-lock.json` to the preparation script. It verifies every archive, strips each archive's single wrapper directory and populates dependency/submodule paths without overwriting a nonempty destination. No source fetch or engine execution occurs in the Android app. Source layout preparation is checked independently; recompiling the complete upstream runtime with NDK r30 has not been executed in this workspace. The tested APK build uses the exact official runtime payloads.

## Probe contract

Production calls run in the existing private isolated-UID service. The worker receives an engine enum and read-only input/report descriptors, no input path or command text. Native callbacks serve only `dieblob://selected` with `pread`; caller descriptor position/ownership is preserved. Configuration, scripts, automatic profiles, sidecars, referenced files and hardware decoding are disabled. A fixed libavformat demuxer and `protocol_whitelist=dieblob` add reference restrictions; the manifest supplies no network permission. Audio/video outputs are null and initialization is paused, but initial media packets can be software-decoded to initialize stream parameters.

The adapter captures **53 audited file/engine properties**, every typed descendant and every unavailable-property error, the full runtime property-name catalog, decoder/demuxer/protocol capability catalogs, exact guarded options and pre-load warnings/errors. It avoids invoking unrelated device, clipboard and system getters. Maps are arrays of keyed entries, preserving order/duplicate keys; integers retain decimal int64 identity, doubles retain IEEE bits, strings/keys retain engine UTF-8 hex and byte arrays retain every byte. No physical file offset is invented for decoded metadata. Pinned upstream property mechanisms/units/limitations are included in the offline reference.

Budgets: input snapshot 256 MiB, cumulative callback reads 32 MiB including rereads, native probe/property/teardown deadline 25 seconds, parent request deadline 45 seconds, 4,096 catalog names, 100,000 typed nodes, depth 64, 8 MiB per text/byte value, 32 MiB encoded report and 1,000/256 KiB pre-load diagnostic events. Hard budget failures return errors rather than successful truncated reports. The same normal-UID guard, token-scoped cancellation and private pipe transport used by Binwalk apply. Real isolated UID/Binder transfer/process death/cancellation timing require handset tests.

## Bounded still-image contract

The `mpv-frame` worker accepts an independently staged exact interval, a fresh
separate raster descriptor and an integer maximum edge. It produces one opaque
`rgba8` image plus a typed report; it does not implement playback. The native
adapter bounds the **display** aspect before MPV allocates a screenshot, using
a fixed scale graph and square output pixels. It then validates dimensions,
stride, format, alpha, returned nodes and decoder failure events. Limits are
25 seconds, 32 MiB cumulative reads, 256 MiB input, a 1536-pixel edge, 9 MiB
packed raster and 16 MiB native screenshot; decoder-internal allocations are
outside these adapter limits.

The parent checks source identity before and after capture, exact raster
length/SHA-256 and RGBA-to-ARGB conversion, then removes temporary inputs and
outputs before publication. Automatic thumbnails use a 256-pixel edge and
32 MiB acquisition cap. Platform decoders run first; explicit MPV stills and
eligible decoder refusals expose their own observations without rewriting
platform metadata or native file boundaries. IO, timeout and media-service
failures do not justify automatic fallback. Timestamp is unknown and source
alpha is not retained. [Application contract](../docs/media-still-previews.md).

Actual host tests cover authored FFV1/Matroska, TIFF/PPM, exact pixels and
square/anamorphic/extreme sample aspect ratios, plus malformed nodes, budgets,
source changes and descriptor ownership. Pinned FFmpeg and MPV header/API
contracts are checked independently. ARM64 compilation and source-bundle
validation pass; actual Android MPV/isolated decoder execution remains pending.
The x86 runtime laboratory does not package this ARM64 engine.

## Verification

`bash scripts/test-mpv.sh` builds the adapter against a real host libmpv (`libmpv2`, or `MPV_HOST_LIBRARY`) and uses host FFmpeg only to generate benign fixtures. `JAVA_HOME` and the normal JSON test-dependency cache apply. Host runtime versions are reported truthfully and can differ from the separately pinned Android engine. The fixtures validate real WAV/Matroska and PCM/FFV1/SubRip information, Unicode and large tags, chapters/catalogs, complete typed exports, descriptor/snapshot ownership, unsupported inputs, budgets and blocked referenced HTTP access. Host Android cases exercise the actual JNI and shared preview widgets, including owned ranges, lifecycle changes and platform decoder refusals. OS worker transport and unavailable Robolectric descriptor facts use explicit test substitutes; these checks do not establish production Android IPC or codec support. The current counts and acceptance gates are recorded in [the parity checklist](../docs/FEATURE-PARITY.md).

See [user-facing scope and limitations](../docs/media-investigation.md). MPV probing, metadata-extractor tags and whole-blob Binwalk carving provide different observations. MPV does not verify every media frame or reconstruct fragmented PhotoRec-style files.

Host builds use the hash-pinned retained client/stream headers in `include/mpv`; a distribution development package is unnecessary. `scripts/host-mpv.sh` accepts an explicit real `MPV_HOST_LIBRARY`, an installed pkg-config development path, or the compiler-resolved `libmpv.so.2` runtime path. Debian/Ubuntu CI installs only `libmpv2 ffmpeg --no-install-recommends`, avoiding the large unused graphics/codec development dependency tree. Runtime-only discovery with pkg-config disabled, actual JNI/media/C-boundary tests and source-bundle helper inclusion are verified locally; current CI remains authoritative for its runner image. The Android runtime bytes and source pins are unchanged.

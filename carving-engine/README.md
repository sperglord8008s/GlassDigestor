# PhotoRec format adapter

This builds **actual unmodified PhotoRec 7.2 format sources** at
`281be432dd79121e08e0898887a9ee1f30fb3e96`. The full upstream archive and reviewed
API/registration/reference files are SHA-256 pinned in `source-lock.json`.
PhotoRec sources are GPL-2.0-or-later; this adapter is GPL-3.0-only. Full copyright
headers and GPL text are bundled in the app. The result is part of the GPL-3.0
Android application.

`prepare-photorec.py` extracts only the verified archive, rejects unsafe archive
members and generates the source list from the original `src/Makefile.am`.
All 358 format/support translation units are compiled without optional JPEG,
compression, GUI or filesystem libraries. Upstream sources are not patched.
`config.h` documents the fixed POSIX/API-26 capabilities and compiler byte order.

`probe.cpp` calls the original registration, standalone safe-header and streaming
data callbacks. Its shared Aho-Corasick index searches every byte and preserves
every occurrence for searched patterns, including relative-offset, short,
overlapping, nested, rejected and negative-start magic. The original 774-pattern
catalog is always retained; 193 one-byte/all-zero patterns are explicitly marked
unsearched because their whole-blob expansion requires a separate anchored scope.
There are 581 searched patterns and 349 registered upstream format groups.
These are catalog counts, not a claim of complete validation of 349 file formats.

Adapter 4 enables only the reviewed upstream PDF `file_check` through a bounded
read-only relative FILE view; other file-check modules, every `file_rename` and
the PhotoRec recovery program remain disabled. See [PDF recovery](../docs/pdf-file-recovery.md). Auxiliary signature acquisition is disabled. Every
upstream translation unit redirects path-based `fopen`/`rename` to denying
functions; timestamp-changing capabilities are not configured. The app supplies
read-only input bytes. No command, codec subprocess or inspected app is executed.

A positive, source-bounded `calculated_file_size` after `DC_STOP`, or the reviewed
PDF file-check's successful bounded size, enables stored-byte carving only within
the upstream minimum/maximum and without an offset error.
An extent remains an upstream data-check report, not full integrity verification:
PNG CRC errors, for example, do not prevent its chunk-boundary callback stopping.
See [evidence contracts](../docs/photorec-investigation.md).

```sh
# JDK 17 and CMake on Linux
export JAVA_HOME=/path/to/jdk17
bash scripts/test-photorec.sh
# Android SDK / NDK 28.2.13676358; static C++ runtime and 16 KiB alignment
export ANDROID_HOME=/path/to/android-sdk
bash scripts/build-photorec.sh android
# Archive plus all adapter/build/notice sources, deterministic ZIP and checksum
python3 scripts/package-photorec-sources.py
```

After the verified tarball exists in `.ci-cache/photorec` (or
`PHOTOREC_DEPS_DIR`), set `DIE_OFFLINE=true` to prohibit downloads. Missing or
edited cache inputs fail explicitly. The source ZIP carries that verified tarball
under its default cache path and the complete standalone adapter/build inputs;
extract it, set `JAVA_HOME`, and run `DIE_OFFLINE=true bash scripts/build-photorec.sh host`.
The JSON test dependency and Android toolchains are separate build prerequisites.

Adapter 2 uses native evidence schema 2: rejected occurrences are lossless
`[patternIndex, magicOffset, recoveryStateIndex]` tuples referencing exact
shared raw states. Header-accepted and negative-start records remain objects.
Original row ordinals and every occurrence remain intact; the limit is now
250,000 occurrences / 24 MiB native report. `PhotoRecReport` validates and lazily
expands the tuples; complete compact captures carry source identity and the
entire indexed report. A 75,000-rejection/streaming/reconstruction fixture and
an actual 48 MiB APK audit cover the former 50,000-hit failure. These transport
changes do not broaden the separately documented format/validation scope.


Adapter 3 replaces the file-sized native input vector with a duplicated readable
FD, 1 MiB matcher chunks and two 128 KiB header caches. The input limit is now
256 MiB with 1.25 MiB of bounded input-read payload buffers, plus separately
bounded callback windows/registry/evidence/JVM memory. Every-byte state crosses
chunks; cached header padding and source reads retain the original callback
semantics. Native metadata exposes the IO mode/dimensions and read accounting;
source identity/timestamps are checked after analysis. Native schema 2 and old
adapter-2 captures remain supported. See the investigation contract for mutable
FD, total-memory and device-verification limits.

`test-photorec.sh` includes the trusted standalone reader test, sparse and fully
written 80 MiB blobs with exact media across buffer/old-limit/EOF seams, a complete
256 MiB sparse scan, descriptor lifetime/truncation tests and legacy decoding.
The complete source ZIP includes these test inputs/generators and the new
`blob_input.h`; its independent offline rebuild is checked separately.

Adapter 4 adds a fixed 64 KiB relative-stdio buffer for PDF file checks. Maximum
input-read payload buffers are now 1.3125 MiB, excluding separately documented
upstream callback scratch/records/JNI/process overhead. PDF reads have an 8 MiB
per-candidate work cap and share the existing 256 MiB aggregate callback budget.
Read/write/seek/budget/exception/descriptor guards have host checks; actual
Bionic callback execution is not established by host tests or ARM64 compilation.


Adapter 5 keeps a positive, source-bounded native PDF footer size when only
optional post-footer newline/date read work is limited. Typed work-limit errors
remain distinct from source/deadline failures. Original failure/work/time fields,
separate boundary/partial-metadata flags and complete counts remain in evidence.
Java validates the new policy while older captures retain their own semantics.
No input/work buffer limit is increased; see the PDF recovery contract.

Adapter 6 separates a **64 MiB per-candidate streaming data-work cap** from
the unchanged **8 MiB per-PDF file-read cap**, sharing the existing 256 MiB
aggregate callback budget. The input/callback window sizes remain unchanged.
After a successful source-bounded upstream MPEG data stop, later accepted pack
headers can omit repeated optional data walks only when the upstream active-MPEG
header check ignores them. SCR=0 headers still run independently. Structured
candidate results establish covering intervals without reparsing generated JSON.
Every standalone header/raw occurrence remains present; an inner pack keeps a
null independent extent and records its mode/earlier owner ordinal. Counts and
the exact policy are captured and validated, and adapters 2–5 remain readable.
See the [adapter 6 protocol](../docs/photorec-investigation.md#adapter-6-larger-streaming-checks-and-repeated-mpeg-packs).

`bash scripts/test-photorec-mpeg.sh` independently generates packet streams and
checks exact >8 MiB payload exports, SCR=0/continuation/suffix/boundary behavior,
truncation, 64 MiB work exhaustion, malformed relationships, legacy reading and
descriptor/cancellation ownership. It is included in `test-photorec.sh` and the
standalone corresponding-source archive. Expected NIST layouts/hashes never
enter production discovery.

# Build and checkpoint versions

`version.properties` is the reviewed version source. Published checkpoints use
`major.minor.patch-pre-alpha.sequence` (the published checkpoint is `0.6.0-pre-alpha.16`; current development is `0.6.0-pre-alpha.17`). Increase
the sequence and Android `version.code` for each newly published checkpoint.
The numeric code must always exceed the previous published code; the version
name itself does not determine Android update ordering.

Debug builds append `-dev.g<12-character Git revision>` and, when applicable,
`.dirty`. A source tree without Git metadata uses `-dev.unknown` and cannot
produce a clean verified checkpoint. The identifier is stable for the same
source state; it does not depend on a clock, a shallow-clone commit count or a
GitHub run number. This is an identity convention, not a promise of identical
APK bytes or signing keys.

For example, a clean debug build produces
`GlassDigestor-0.6.0-pre-alpha.5-dev.g0123456789ab-arm64-v8a-debug.apk`.
The installed version name includes the same suffix. Tap the main title or
open **Options → Build details** to inspect the version code, build variant,
full source revision and source status.

`scripts/build-version.py` supplies the same contract to Gradle, APK verification
and checkpoint packaging. `scripts/verify-apk.sh` and `scripts/package-alpha.py`
resolve the single actual APK through Android Gradle Plugin output metadata;
they no longer depend on `app-debug.apk`. Verification rejects an APK built
from a different version/source state. Checkpoints require a clean committed
tree and retain the filename, SHA-256, source/engine manifest and metadata.

The application ID and signing configuration are unchanged. CI uses Android's
debug signer; independently created runners can have different certificates.
Check the public signer digest in each build manifest before replacing an
existing installation. A GitHub pre-release distributes this explicitly labeled
debug APK for testing; it is not a production-key release or evidence of device
acceptance. Keep exported reports and the prior APK when testing updates.

## Publishing a verified GitHub pre-release

Run **Actions → Publish verified pre-release** with the successful Android/host
workflow run ID and its numbered tag. This separate workflow requests only
`actions: read` and `contents: write`, using GitHub's normal workflow token.
No personal token, signing-key upload or new signing configuration is needed.

The publisher requires all five reviewed jobs to pass, downloads the original
checkpoint and all three component-source artifacts, validates their GitHub
SHA-256 digests and complete internal checksums, and compares the captured Git
tree with the successful workflow head. The version/tag/code/filename and build
manifest must agree with that exact source. CI may build a generated PR merge
commit; the tag points to that precise revision so the installed version and
source manifest remain reproducible.

Uploads remain in a draft until every remote asset's size and SHA-256 agrees.
An interrupted draft can resume. Existing different assets and tags are never
removed, replaced or retargeted; a published release is immutable to this
publisher. The APK is copied unchanged from CI, including its debug signer.
`RELEASE-SHA256SUMS` covers every supplied asset except itself.

Use `python3 tests/test-prerelease-publication.py` for local contract checks.
The script inspects archives as data and never executes APKs or sample files.

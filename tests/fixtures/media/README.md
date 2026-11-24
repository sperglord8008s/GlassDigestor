# Owned platform-preview fixtures

`green-64x48.mp4` is a **991-byte**, one-second 64×48 solid-green MPEG-4 clip
created for this project. SHA-256:
`65d9570c01189231926f8a85779d02a224f6782ec5bc6d4c192d7dd84745463e`.
It contains five video frames, no audio and no third-party media. This repository's
GPL-3.0-only license applies to the test source/data.

Ordinary test builds copy and hash-check the retained clip and generate a 500 ms
silent mono 8 kHz signed 16-bit PCM WAV with Python's standard library. They need
no FFmpeg installation or network. The manifest records exact fixture identities
and distinguishes retained video from regenerated audio.

The complete video generation command is in `generate-preview.py`. The canonical
bytes were generated with Debian FFmpeg `7.1.5-0+deb13u1`. To reproduce them:

```sh
python3 tests/fixtures/media/generate-preview.py /tmp/glassdigestor-media-fixtures --regenerate-video
```

The optional regeneration path requires FFmpeg and checks the same SHA-256. A
byte difference is an explicit failure and leaves a candidate in the requested
output directory for inspection; it never updates the canonical repository file.
Other FFmpeg versions may produce different container/codec bytes. The owned
canonical fixture and recipe remain available for offline builds.

The independent Android assertions expect the known geometry/duration and green
center pixel; an asset's presence or a host codec shadow does not prove Android
platform decoding. Every test embeds the clip behind a prefix and before a
trailer, exercising the production descriptor interval rather than filename-based
playback. API 26 must retain metadata without unbounded frame decoding.


The generator also writes an owned `archive-session-20m.bz2` runtime-lab fixture:
20 MiB of deterministic SHA-256 blocks, BZip2-compressed to more than 20 MiB,
with a fixed independent decoded digest. This full, non-sparse large input
exercises archive sessions/cache aliases through the actual worker. It is
included only in the separate runtime laboratory assets, not the production APK.

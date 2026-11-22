#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/project/scripts" "$fixture/bin"
cp scripts/{fetch-robolectric-sdks,fetch-verified}.sh "$fixture/project/scripts/"
name=android-all-instrumented-fixture-i7.jar
checksum=$(printf 'known SDK fixture\n' | sha256sum | cut -d' ' -f1)
printf '%s  %s\n' "$checksum" "$name" > "$fixture/project/scripts/robolectric-sdk.sha256"
cat > "$fixture/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -eu
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o) destination="$2"; shift 2 ;;
    https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
case "$url" in
  https://maven-central.storage-download.googleapis.com/maven2/*) host=mirror ;;
  https://repo.maven.apache.org/maven2/*) host=central ;;
  *) echo 'Unexpected SDK download host.' >&2; exit 1 ;;
esac
printf '%s\n' "$host" >> "$SDK_FETCH_CALLS"
if [[ "$SDK_FETCH_MODE" = both-fail || "$SDK_FETCH_MODE" = limited && "$host" = mirror ]]; then
  printf partial > "$destination"; exit 22
fi
if [[ "$SDK_FETCH_MODE" = corrupt || "$SDK_FETCH_MODE" = mirror-corrupt && "$host" = mirror ]]; then
  printf 'wrong image\n' > "$destination"
else
  printf 'known SDK fixture\n' > "$destination"
fi
MOCK
chmod +x "$fixture/bin/curl"
export PATH="$fixture/bin:$PATH" SDK_FETCH_CALLS="$fixture/calls"
unset DIE_OFFLINE
sdk_script="$fixture/project/scripts/fetch-robolectric-sdks.sh"
for scenario in valid limited mirror-corrupt; do
  export ROBOLECTRIC_SDK_DIR="$fixture/$scenario" SDK_FETCH_MODE="$scenario"
  : > "$SDK_FETCH_CALLS"
  bash "$sdk_script"
  printf '%s  %s\n' "$checksum" "$ROBOLECTRIC_SDK_DIR/$name" | sha256sum -c -
  if [[ "$scenario" = valid ]]; then expected=1; else expected=2; fi
  test "$(wc -l < "$SDK_FETCH_CALLS")" -eq "$expected"
  test "$(head -n 1 "$SDK_FETCH_CALLS")" = mirror
  test -z "$(find "$ROBOLECTRIC_SDK_DIR" -name '*.download.*' -print)"
  bash "$sdk_script"
  test "$(wc -l < "$SDK_FETCH_CALLS")" -eq "$expected"
done
for scenario in both-fail corrupt; do
  export ROBOLECTRIC_SDK_DIR="$fixture/$scenario" SDK_FETCH_MODE="$scenario"
  : > "$SDK_FETCH_CALLS"
  if bash "$sdk_script"; then echo 'Invalid SDK acquisition succeeded.' >&2; exit 1; fi
  test ! -e "$ROBOLECTRIC_SDK_DIR/$name"
  test "$(wc -l < "$SDK_FETCH_CALLS")" -eq 2
  test -z "$(find "$ROBOLECTRIC_SDK_DIR" -name '*.download.*' -print)"
done
export ROBOLECTRIC_SDK_DIR="$fixture/offline" DIE_OFFLINE=true
: > "$SDK_FETCH_CALLS"
if bash "$sdk_script"; then echo 'Missing offline SDK was accepted.' >&2; exit 1; fi
test ! -s "$SDK_FETCH_CALLS"
unset DIE_OFFLINE
export ROBOLECTRIC_SDK_DIR="$fixture/edited"
mkdir -p "$ROBOLECTRIC_SDK_DIR"
printf 'edited cache\n' > "$ROBOLECTRIC_SDK_DIR/$name"
if bash "$sdk_script"; then echo 'Edited SDK cache was accepted/replaced.' >&2; exit 1; fi
test "$(cat "$ROBOLECTRIC_SDK_DIR/$name")" = 'edited cache'
test ! -s "$SDK_FETCH_CALLS"
echo 'SDK acquisition: pinned mirror/fallback, wrong/partial-byte cleanup, verified reuse and offline/edited-cache no-network refusals passed.'

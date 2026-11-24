#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
cat > "$tmp/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -eu
limit=''
while [[ $# -gt 0 ]]; do
  if [[ "$1" = -o ]]; then destination="$2"; shift 2
  elif [[ "$1" = --max-filesize ]]; then limit="$2"; shift 2
  else shift; fi
done
printf 'called %s\n' "$limit" >> "$DEPENDENCY_FETCH_CALLS"
case "$DEPENDENCY_FETCH_MODE" in
  valid) printf 'known complete bytes\n' > "$destination" ;;
  checksum) printf 'wrong complete bytes\n' > "$destination" ;;
  interrupted) printf 'partial' > "$destination"; exit 22 ;;
esac
MOCK
chmod +x "$tmp/bin/curl"
export PATH="$tmp/bin:$PATH" DEPENDENCY_FETCH_CALLS="$tmp/calls" DEPENDENCY_FETCH_MODE=valid
checksum=$(printf 'known complete bytes\n' | sha256sum | cut -d' ' -f1)
source scripts/fetch-verified.sh
fetch_verified_dependency "$tmp/cache/dependency.jar" "$checksum" https://fixture.invalid/public.jar
test "$(cat "$tmp/cache/dependency.jar")" = 'known complete bytes'
test "$(wc -l < "$tmp/calls")" -eq 1
fetch_verified_dependency "$tmp/cache/dependency.jar" "$checksum" https://fixture.invalid/public.jar
test "$(wc -l < "$tmp/calls")" -eq 1
export DEPENDENCY_FETCH_MODE=interrupted
if fetch_verified_dependency "$tmp/cache/interrupted.jar" "$checksum" https://fixture.invalid/public.jar; then echo 'Interrupted download was published.' >&2; exit 1; fi
test ! -e "$tmp/cache/interrupted.jar"
test -z "$(find "$tmp/cache" -name '*.download.*' -print)"
export DEPENDENCY_FETCH_MODE=checksum
if fetch_verified_dependency "$tmp/cache/wrong.jar" "$checksum" https://fixture.invalid/public.jar; then echo 'Wrong checksum was published.' >&2; exit 1; fi
test ! -e "$tmp/cache/wrong.jar"
test -z "$(find "$tmp/cache" -name '*.download.*' -print)"
printf 'edited cached bytes\n' > "$tmp/cache/edited.jar"
if fetch_verified_dependency "$tmp/cache/edited.jar" "$checksum" https://fixture.invalid/public.jar; then echo 'Edited cache was accepted.' >&2; exit 1; fi
test "$(cat "$tmp/cache/edited.jar")" = 'edited cached bytes'
test "$(wc -l < "$tmp/calls")" -eq 3
export DIE_OFFLINE=true DEPENDENCY_FETCH_MODE=valid
fetch_verified_dependency "$tmp/cache/dependency.jar" "$checksum" https://fixture.invalid/public.jar
if fetch_verified_dependency "$tmp/cache/missing.jar" "$checksum" https://fixture.invalid/public.jar; then echo 'Missing offline input was accepted.' >&2; exit 1; fi
test "$(wc -l < "$tmp/calls")" -eq 3
unset DIE_OFFLINE
fetch_verified_dependency "$tmp/cache/custom.jar" "$checksum" https://fixture.invalid/public.jar 64
test "$(tail -n 1 "$tmp/calls")" = 'called 64'
if fetch_verified_dependency "$tmp/cache/oversize.jar" "$checksum" https://fixture.invalid/public.jar 16; then echo 'Oversize download was published.' >&2; exit 1; fi
test ! -e "$tmp/cache/oversize.jar"
test -z "$(find "$tmp/cache" -name '*.download.*' -print)"
if fetch_verified_dependency "$tmp/cache/custom.jar" "$checksum" https://fixture.invalid/public.jar 16; then echo 'Oversize cache was accepted.' >&2; exit 1; fi
for invalid in 0 -1 268435457 99999999999999999999 invalid; do
  if fetch_verified_dependency "$tmp/cache/invalid.jar" "$checksum" https://fixture.invalid/public.jar "$invalid"; then echo 'Invalid byte limit was accepted.' >&2; exit 1; fi
done
test "$(wc -l < "$tmp/calls")" -eq 5
printf '%s\n' 'Verified dependency acquisition: reuse, interrupted/incorrect download cleanup, unchanged edited cache and offline no-network behavior passed.'

# Exercise the public fallback helper with authored bytes and no real network.
cat > "$tmp/bin/curl" <<'MOCK'
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
  *) echo 'Unexpected dependency origin.' >&2; exit 1 ;;
esac
printf '%s\n' "$host" >> "$DEPENDENCY_FETCH_CALLS"
if [[ "$DEPENDENCY_FETCH_MODE" = both-fail || "$DEPENDENCY_FETCH_MODE" = limited && "$host" = mirror ]]; then
  printf partial > "$destination"; exit 22
fi
if [[ "$DEPENDENCY_FETCH_MODE" = corrupt || "$DEPENDENCY_FETCH_MODE" = mirror-corrupt && "$host" = mirror ]]; then
  printf 'wrong bytes\n' > "$destination"
else
  printf 'known complete bytes\n' > "$destination"
fi
MOCK
chmod +x "$tmp/bin/curl"
for scenario in valid limited mirror-corrupt; do
  export DEPENDENCY_FETCH_MODE="$scenario"
  : > "$DEPENDENCY_FETCH_CALLS"
  maven_fixture_path="$tmp/maven/$scenario.jar"
  fetch_verified_maven_dependency "$maven_fixture_path" "$checksum" org/fixture/version/fixture.jar
  if [[ "$scenario" = valid ]]; then expected=1; else expected=2; fi
  test "$(wc -l < "$DEPENDENCY_FETCH_CALLS")" -eq "$expected"
  test "$(head -n 1 "$DEPENDENCY_FETCH_CALLS")" = mirror
  fetch_verified_maven_dependency "$maven_fixture_path" "$checksum" org/fixture/version/fixture.jar
  test "$(wc -l < "$DEPENDENCY_FETCH_CALLS")" -eq "$expected"
done
for scenario in both-fail corrupt; do
  export DEPENDENCY_FETCH_MODE="$scenario"
  : > "$DEPENDENCY_FETCH_CALLS"
  maven_fixture_path="$tmp/maven/$scenario.jar"
  if fetch_verified_maven_dependency "$maven_fixture_path" "$checksum" org/fixture/version/fixture.jar; then
    echo 'Invalid Maven acquisition succeeded.' >&2; exit 1
  fi
  test ! -e "$maven_fixture_path"
  test "$(wc -l < "$DEPENDENCY_FETCH_CALLS")" -eq 2
done
: > "$DEPENDENCY_FETCH_CALLS"
export DIE_OFFLINE=true
fetch_verified_maven_dependency "$tmp/maven/valid.jar" "$checksum" org/fixture/version/fixture.jar
if fetch_verified_maven_dependency "$tmp/maven/missing.jar" "$checksum" org/fixture/version/fixture.jar; then
  echo 'Missing offline Maven input was accepted.' >&2; exit 1
fi
unset DIE_OFFLINE
printf 'edited cache\n' > "$tmp/maven/edited.jar"
if fetch_verified_maven_dependency "$tmp/maven/edited.jar" "$checksum" org/fixture/version/fixture.jar; then
  echo 'Edited Maven cache was replaced.' >&2; exit 1
fi
test "$(cat "$tmp/maven/edited.jar")" = 'edited cache'
test ! -s "$DEPENDENCY_FETCH_CALLS"
test -z "$(find "$tmp/maven" -name '*.download.*' -print)"
echo 'Maven acquisition: identical pinned mirror/fallback bytes, partial/corrupt cleanup and strict offline/cache refusals passed.'

#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Source this helper. The retained mpv headers do not require a development package.
die_host_mpv_fortify_level() {
  # Level 3 needs both compiler support and glibc headers that enable it.
  # The syntax-only probe writes no objects or libraries. Older hosts use 2.
  if printf '%s\n' '#include <features.h>' \
      '#if !defined(__GLIBC__) || !defined(__USE_FORTIFY_LEVEL) || __USE_FORTIFY_LEVEL != 3' \
      '#error Fortification level 3 is unavailable' '#endif' 'int main(void) { return 0; }' |
      "${CC:-cc}" -std=c11 -O2 -Wall -Wextra -Werror -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=3 \
        -x c -fsyntax-only - >/dev/null 2>&1; then
    printf '%s\n' 3
  else
    printf '%s\n' 2
  fi
}

die_host_mpv_library() {
  local candidate="${MPV_HOST_LIBRARY:-}" libdir
  if [[ -n "$candidate" ]]; then
    if [[ ! -f "$candidate" ]]; then
      echo 'MPV_HOST_LIBRARY does not name an available host libmpv shared library.' >&2
      return 1
    fi
    printf '%s\n' "$candidate"
    return
  fi
  if command -v "${PKG_CONFIG:-pkg-config}" >/dev/null &&
      libdir=$("${PKG_CONFIG:-pkg-config}" --variable=libdir mpv 2>/dev/null); then
    candidate="$libdir/libmpv.so"
    if [[ -f "$candidate" ]]; then printf '%s\n' "$candidate"; return; fi
  fi
  # GCC/Clang know their platform's multiarch/runtime search paths. Link the
  # real SONAME file directly; libmpv-dev's unversioned symlink is unnecessary.
  candidate=$("${CC:-cc}" -print-file-name=libmpv.so.2)
  if [[ -f "$candidate" ]]; then printf '%s\n' "$candidate"; return; fi
  echo 'Install the libmpv2 runtime package or set MPV_HOST_LIBRARY to its real shared library.' >&2
  return 1
}

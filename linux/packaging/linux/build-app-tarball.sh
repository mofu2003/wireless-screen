#!/usr/bin/env bash
# Build the unified WirelessScreen Linux tarball from a completed cargo build.
#
#   OD_APP_LICENSES=<third-party-licenses.txt> \
#     ./build-app-tarball.sh <version> [<arch>]
#
# Release builds link the static FFmpeg from
# linux/packaging/ffmpeg/build-static.sh and pass the licence report generated
# by linux/packaging/third-party-licenses.py.
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
version="${1:?usage: $0 <version> [<arch>]}"
arch="${2:-$(uname -m)}"
binary="${OD_APP_BIN:-${root}/linux/target/release/wireless-screen}"
licenses="${OD_APP_LICENSES:-${root}/dist/THIRD-PARTY-LICENSES.txt}"
packaging="${root}/linux/packaging/linux"
name="WirelessScreen-${version}-linux-${arch}"
stage="${root}/dist/${name}"

[[ -x "$binary" ]] || {
  echo "build-app-tarball.sh: binary not found: $binary" >&2
  exit 1
}
actual_version="$("$binary" --version | awk '{print $2}')"
[[ "$actual_version" == "$version" ]] || {
  echo "build-app-tarball.sh: binary reports '$actual_version', expected '$version'" >&2
  echo "rebuild with OD_APP_VERSION=$version" >&2
  exit 1
}
[[ -f "$licenses" ]] || {
  echo "build-app-tarball.sh: licence report not found: $licenses" >&2
  echo "set OD_APP_LICENSES to THIRD-PARTY-LICENSES.txt" >&2
  exit 1
}
for f in wireless-screen.desktop wireless-screen-send@.service \
         wireless-screen-receive.service install-app.sh \
         wireless-screen.png; do
  [[ -f "${packaging}/${f}" ]] || {
    echo "build-app-tarball.sh: missing ${packaging}/${f}" >&2
    exit 1
  }
done

rm -rf "$stage"
mkdir -p "$stage"
install -m755 "$binary" "${stage}/wireless-screen"
install -m755 "${packaging}/install-app.sh" "${stage}/install.sh"
install -m644 "${packaging}/wireless-screen.desktop" "$stage/"
install -m644 "${packaging}/wireless-screen-send@.service" "$stage/"
install -m644 "${packaging}/wireless-screen-receive.service" "$stage/"
install -m644 "${packaging}/wireless-screen.png" "$stage/"
install -m644 "${root}/linux/README-APP.md" "${stage}/README.md"
install -m644 "$licenses" "${stage}/THIRD-PARTY-LICENSES.txt"
install -m644 "${root}/LICENSE" "$stage/LICENSE"
install -m644 "${root}/NOTICE" "$stage/NOTICE"
if [[ -f "${root}/MODIFICATIONS.md" ]]; then
  install -m644 "${root}/MODIFICATIONS.md" "$stage/MODIFICATIONS.md"
fi

git_revision="${OD_GIT_COMMIT:-$(git -C "$root" rev-parse HEAD 2>/dev/null || echo unknown)}"
{
  echo "WirelessScreen ${version} linux ${arch}"
  echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "git: ${git_revision}"
  echo
  echo "dynamic dependencies:"
  ldd "$binary" || true
  echo
  echo "build host:"
  ldd --version 2>/dev/null | head -1 || true
  pkg-config --modversion gtk4 2>/dev/null || echo "gtk4: not linked"
  pkg-config --modversion gstreamer-1.0 2>/dev/null || true
  pkg-config --modversion libpipewire-0.3 2>/dev/null || true
} > "${stage}/BUILDINFO.txt"

(cd "${root}/dist" && tar -czf "${name}.tar.gz" "${name}")
(cd "${root}/dist" && sha256sum "${name}.tar.gz" > "${name}.tar.gz.sha256")
echo "${root}/dist/${name}.tar.gz"

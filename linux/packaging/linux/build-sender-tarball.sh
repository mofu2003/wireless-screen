#!/usr/bin/env bash
# Build an installable Linux sender tarball from a completed cargo build.
#
#   OD_SENDER_LICENSES=<third-party-licenses.txt> \
#     ./build-sender-tarball.sh <version> [<arch>]
#
# OD_SENDER_BIN can point at the binary; otherwise linux/target/release/od-sender
# is used. Release builds should link the static FFmpeg from
# linux/packaging/ffmpeg/build-static.sh and pass the licence report generated
# by linux/packaging/third-party-licenses.py.
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
version="${1:?usage: $0 <version> [<arch>]}"
arch="${2:-$(uname -m)}"
binary="${OD_SENDER_BIN:-${root}/linux/target/release/od-sender}"
licenses="${OD_SENDER_LICENSES:-${root}/dist/THIRD-PARTY-LICENSES.txt}"
packaging="${root}/linux/packaging/linux"
name="WirelessScreenSender-${version}-linux-${arch}"
stage="${root}/dist/${name}"

[[ -x "$binary" ]] || {
  echo "build-sender-tarball.sh: binary not found: $binary" >&2
  exit 1
}
actual_version="$("$binary" --version | awk '{print $2}')"
[[ "$actual_version" == "$version" ]] || {
  echo "build-sender-tarball.sh: binary reports '$actual_version', expected '$version'" >&2
  echo "rebuild with OD_SENDER_VERSION=$version" >&2
  exit 1
}
[[ -f "$licenses" ]] || {
  echo "build-sender-tarball.sh: licence report not found: $licenses" >&2
  echo "set OD_SENDER_LICENSES to THIRD-PARTY-LICENSES.txt" >&2
  exit 1
}
for f in wireless-screen-sender.desktop wireless-screen-sender@.service \
         install-sender.sh wireless-screen-receiver.png; do
  [[ -f "${packaging}/${f}" ]] || {
    echo "build-sender-tarball.sh: missing ${packaging}/${f}" >&2
    exit 1
  }
done

rm -rf "$stage"
mkdir -p "$stage"
install -m755 "$binary" "${stage}/wireless-screen-sender"
install -m755 "${packaging}/install-sender.sh" "${stage}/install.sh"
install -m644 "${packaging}/wireless-screen-sender.desktop" "$stage/"
install -m644 "${packaging}/wireless-screen-sender@.service" "$stage/"
install -m644 "${packaging}/wireless-screen-receiver.png" \
  "${stage}/wireless-screen-sender.png"
install -m644 "${root}/linux/README-SENDER.md" "${stage}/README.md"
install -m644 "$licenses" "${stage}/THIRD-PARTY-LICENSES.txt"
install -m644 "${root}/LICENSE" "$stage/LICENSE"
install -m644 "${root}/NOTICE" "$stage/NOTICE"
if [[ -f "${root}/MODIFICATIONS.md" ]]; then
  install -m644 "${root}/MODIFICATIONS.md" "$stage/MODIFICATIONS.md"
fi

git_revision="${OD_GIT_COMMIT:-$(git -C "$root" rev-parse HEAD 2>/dev/null || echo unknown)}"
{
  echo "WirelessScreenSender ${version} linux ${arch}"
  echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "git: ${git_revision}"
  echo
  echo "dynamic dependencies:"
  ldd "$binary" || true
  echo
  echo "build host:"
  ldd --version 2>/dev/null | head -1 || true
} > "${stage}/BUILDINFO.txt"

(cd "${root}/dist" && tar -czf "${name}.tar.gz" "${name}")
(cd "${root}/dist" && sha256sum "${name}.tar.gz" > "${name}.tar.gz.sha256")
echo "${root}/dist/${name}.tar.gz"

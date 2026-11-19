#!/usr/bin/env bash
# Build an installable Linux receiver tarball from a completed cargo build.
#
#   ./build-receiver-tarball.sh <version> [<arch>] [full|compat]
#
# `full` needs GTK 4.14 and is the normal desktop receiver. `compat` is for
# older images (Ubuntu 22.04 / Debian 12) and starts waylandsink instead of
# the GTK input window. OD_RECEIVER_BIN can point at the binary; otherwise
# linux/target/release/od-receiver is used.
set -euo pipefail

root="$(cd "$(dirname "$0")/../../.." && pwd)"
version="${1:?usage: $0 <version> [<arch>] [full|compat]}"
arch="${2:-$(uname -m)}"
variant="${3:-full}"
case "$variant" in
  full|compat) ;;
  *)
    echo "build-receiver-tarball.sh: unknown variant '$variant' (full|compat)" >&2
    exit 2
    ;;
esac
binary="${OD_RECEIVER_BIN:-${root}/linux/target/release/od-receiver}"
packaging="${root}/linux/packaging/linux"
suffix=""
[[ "$variant" == compat ]] && suffix="-compat"
name="WirelessScreenReceiver-${version}-linux-${arch}${suffix}"
stage="${root}/dist/${name}"

[[ -x "$binary" ]] || {
  echo "build-receiver-tarball.sh: binary not found: $binary" >&2
  exit 1
}
actual_version="$("$binary" --version | awk '{print $2}')"
[[ "$actual_version" == "$version" ]] || {
  echo "build-receiver-tarball.sh: binary reports '$actual_version', expected '$version'" >&2
  echo "rebuild with OD_RECEIVER_VERSION=$version" >&2
  exit 1
}
for f in wireless-screen-receiver.desktop wireless-screen-receiver.service \
         install.sh wireless-screen-receiver.png; do
  [[ -f "${packaging}/${f}" ]] || {
    echo "build-receiver-tarball.sh: missing ${packaging}/${f}" >&2
    exit 1
  }
done

rm -rf "$stage"
mkdir -p "$stage"
install -m755 "$binary" "${stage}/wireless-screen-receiver"
install -m755 "${packaging}/install.sh" "$stage/"
install -m644 "${packaging}/wireless-screen-receiver.png" "$stage/"
install -m644 "${root}/linux/README-RK3576.md" "$stage/README-RK3576.md"
install -m644 "${root}/LICENSE" "$stage/LICENSE"
install -m644 "${root}/NOTICE" "$stage/NOTICE"
if [[ -f "${root}/MODIFICATIONS.md" ]]; then
  install -m644 "${root}/MODIFICATIONS.md" "$stage/MODIFICATIONS.md"
fi

if [[ "$variant" == compat ]]; then
  # The compatibility build has no GTK input window. waylandsink can still
  # fullscreen on the selected output, so make both launchers choose it.
  sed 's#^Exec=/usr/bin/wireless-screen-receiver$#Exec=/usr/bin/wireless-screen-receiver --sink wayland#' \
    "${packaging}/wireless-screen-receiver.desktop" > "$stage/wireless-screen-receiver.desktop"
  sed 's#^ExecStart=/usr/bin/wireless-screen-receiver$#ExecStart=/usr/bin/wireless-screen-receiver --sink wayland#' \
    "${packaging}/wireless-screen-receiver.service" > "$stage/wireless-screen-receiver.service"
else
  install -m644 "${packaging}/wireless-screen-receiver.desktop" "$stage/"
  install -m644 "${packaging}/wireless-screen-receiver.service" "$stage/"
fi
chmod 644 "$stage/wireless-screen-receiver.desktop" "$stage/wireless-screen-receiver.service"
printf '%s\n' "$variant" > "$stage/PACKAGE_VARIANT"
git_revision="${OD_GIT_COMMIT:-$(git -C "$root" rev-parse HEAD 2>/dev/null || echo unknown)}"

{
  echo "WirelessScreenReceiver ${version} linux ${arch}"
  echo "variant: ${variant}"
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
} > "${stage}/BUILDINFO.txt"

(cd "${root}/dist" && tar -czf "${name}.tar.gz" "${name}")
(cd "${root}/dist" && sha256sum "${name}.tar.gz" > "${name}.tar.gz.sha256")
echo "${root}/dist/${name}.tar.gz"

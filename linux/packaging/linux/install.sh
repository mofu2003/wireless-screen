#!/usr/bin/env bash
# Install the Linux receiver from this release tarball.
#
#   ./install.sh                 current user: ~/.local
#   sudo ./install.sh --system   all users: /usr/local
#   ./install.sh --uninstall     remove the installed files
#
# The desktop entry and user service are rewritten to point at the installed
# binary. State (the receiver id) is kept on uninstall.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
variant="$(cat "${here}/PACKAGE_VARIANT" 2>/dev/null || echo full)"
case "$variant" in
  full|compat) ;;
  *)
    echo "install.sh: unsupported package variant '$variant'" >&2
    exit 1
    ;;
esac

system=0
uninstall=0
for arg in "$@"; do
  case "$arg" in
    --system) system=1 ;;
    --uninstall) uninstall=1 ;;
    -h|--help) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "install.sh: unknown argument '$arg' (see --help)" >&2; exit 2 ;;
  esac
done

if [[ $system == 1 ]]; then
  [[ $EUID -eq 0 ]] || {
    echo "install.sh: --system writes to /usr/local; run it with sudo" >&2
    exit 1
  }
  prefix=/usr/local
  unitdir=/usr/local/lib/systemd/user
else
  [[ $EUID -ne 0 ]] || {
    echo "install.sh: as root this would install into root's home; use --system" >&2
    exit 1
  }
  prefix="${HOME}/.local"
  unitdir="${XDG_CONFIG_HOME:-${HOME}/.config}/systemd/user"
fi

bindir="${prefix}/bin"
appdir="${prefix}/share/applications"
iconroot="${prefix}/share/icons/hicolor"
bin="${bindir}/wireless-screen-receiver"
desktop="${appdir}/wireless-screen-receiver.desktop"
icon="${iconroot}/256x256/apps/wireless-screen-receiver.png"
service="${unitdir}/wireless-screen-receiver.service"

if [[ $uninstall == 1 ]]; then
  rm -fv "$bin" "$desktop" "$icon" "$service"
  if [[ $system == 0 ]] && command -v systemctl >/dev/null; then
    systemctl --user daemon-reload 2>/dev/null || true
  fi
  exit 0
fi

for f in wireless-screen-receiver wireless-screen-receiver.desktop \
         wireless-screen-receiver.service wireless-screen-receiver.png; do
  [[ -f "${here}/${f}" ]] || {
    echo "install.sh: ${f} is missing next to this script" >&2
    exit 1
  }
done

install -Dm755 "${here}/wireless-screen-receiver" "$bin"
sed "s#^Exec=/usr/bin/wireless-screen-receiver#Exec=${bin}#" \
  "${here}/wireless-screen-receiver.desktop" | install -Dm644 /dev/stdin "$desktop"
sed "s#^ExecStart=/usr/bin/wireless-screen-receiver#ExecStart=${bin}#" \
  "${here}/wireless-screen-receiver.service" | install -Dm644 /dev/stdin "$service"
install -Dm644 "${here}/wireless-screen-receiver.png" "$icon"

if command -v update-desktop-database >/dev/null; then
  update-desktop-database "$appdir" >/dev/null 2>&1 || true
fi
if command -v gtk-update-icon-cache >/dev/null; then
  gtk-update-icon-cache -q -t "$iconroot" >/dev/null 2>&1 || true
fi
if [[ $system == 0 ]] && command -v systemctl >/dev/null; then
  systemctl --user daemon-reload 2>/dev/null || true
fi

echo "installed ${bin}"
echo "          ${desktop}"
echo "          ${service}"
echo "variant:   ${variant}"
echo
if [[ "$variant" == compat ]]; then
  echo "run now:       wireless-screen-receiver --sink wayland"
  echo "note: this compatibility build has no GTK window, so touch and scroll"
  echo "      are not sent back to the Mac. Use the full package on GTK 4.14+."
else
  echo "run now:       wireless-screen-receiver"
fi
echo "start at login: systemctl --user enable --now wireless-screen-receiver"

missing="$(ldd "${here}/wireless-screen-receiver" 2>/dev/null | awk '/not found/{print $1}' || true)"
if [[ -n "$missing" ]]; then
  echo "warning: missing shared libraries: ${missing//$'\n'/,}" >&2
fi

if ! command -v gst-inspect-1.0 >/dev/null; then
  echo "note: gst-inspect-1.0 not found; install GStreamer tools to debug decoding" >&2
else
  if [[ "$variant" == compat ]] && ! gst-inspect-1.0 waylandsink >/dev/null 2>&1; then
    echo "warning: waylandsink is missing; install gstreamer1.0-plugins-bad" >&2
  fi
  echo
  echo "H.264 decoders found:"
  for dec in mppvideodec v4l2slh264dec v4l2h264dec avdec_h264; do
    if gst-inspect-1.0 "$dec" >/dev/null 2>&1; then
      echo "  yes  $dec"
    else
      echo "  no   $dec"
    fi
  done
fi

case ":${PATH}:" in
  *":${bindir}:"*) ;;
  *) echo "note: ${bindir} is not on PATH; add it or log out and in again" >&2 ;;
esac

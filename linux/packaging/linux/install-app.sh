#!/usr/bin/env bash
# Install the unified Linux WirelessScreen app from this release tarball.
#
#   ./install.sh                 current user: ~/.local
#   sudo ./install.sh --system   all users: /usr/local
#   ./install.sh --uninstall     remove installed files
#
# Configuration and receiver identity are kept on uninstall. The installer
# also removes the old separate sender/receiver launchers it replaces.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

system=0
uninstall=0
for arg in "$@"; do
  case "$arg" in
    --system) system=1 ;;
    --uninstall) uninstall=1 ;;
    -h|--help) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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
bin="${bindir}/wireless-screen"
desktop="${appdir}/wireless-screen.desktop"
icon="${iconroot}/256x256/apps/wireless-screen.png"
send_service="${unitdir}/wireless-screen-send@.service"
receive_service="${unitdir}/wireless-screen-receive.service"

legacy=(
  "${bindir}/wireless-screen-sender"
  "${bindir}/wireless-screen-receiver"
  "${appdir}/wireless-screen-sender.desktop"
  "${appdir}/wireless-screen-receiver.desktop"
  "${iconroot}/256x256/apps/wireless-screen-sender.png"
  "${iconroot}/256x256/apps/wireless-screen-receiver.png"
  "${unitdir}/wireless-screen-sender@.service"
  "${unitdir}/wireless-screen-receiver.service"
)

if [[ $uninstall == 1 ]]; then
  rm -fv -- "$bin" "$desktop" "$icon" "$send_service" "$receive_service" \
    "${legacy[@]}"
  if [[ $system == 0 ]] && command -v systemctl >/dev/null; then
    systemctl --user daemon-reload 2>/dev/null || true
  fi
  exit 0
fi

for f in wireless-screen wireless-screen.desktop \
         wireless-screen-send@.service wireless-screen-receive.service \
         wireless-screen.png; do
  [[ -f "${here}/${f}" ]] || {
    echo "install.sh: ${f} is missing next to this script" >&2
    exit 1
  }
done

install -Dm755 "${here}/wireless-screen" "$bin"
sed "s#/usr/bin/wireless-screen#${bin}#g" \
  "${here}/wireless-screen.desktop" | install -Dm644 /dev/stdin "$desktop"
sed "s#/usr/bin/wireless-screen#${bin}#g" \
  "${here}/wireless-screen-send@.service" | install -Dm644 /dev/stdin "$send_service"
sed "s#/usr/bin/wireless-screen#${bin}#g" \
  "${here}/wireless-screen-receive.service" | install -Dm644 /dev/stdin "$receive_service"
install -Dm644 "${here}/wireless-screen.png" "$icon"

# One app means no stale sender/receiver launchers from 0.1.x.
rm -fv -- "${legacy[@]}"

if command -v update-desktop-database >/dev/null; then
  update-desktop-database "$appdir" >/dev/null 2>&1 || true
fi
if command -v gtk-update-icon-cache >/dev/null; then
  gtk-update-icon-cache -q -t "$iconroot" >/dev/null 2>&1 || true
fi
if command -v systemctl >/dev/null; then
  systemctl --user daemon-reload 2>/dev/null || true
fi

echo "installed ${bin}"
echo "          ${desktop}"
echo "          ${send_service}"
echo "          ${receive_service}"
echo
echo "open menu:       wireless-screen"
echo "send screen:     wireless-screen send"
echo "receive display: wireless-screen receive"
echo "find receivers:  wireless-screen list"
echo "start receiving: systemctl --user enable --now wireless-screen-receive"
echo "send at login:   systemctl --user enable --now 'wireless-screen-send@<receiver>.service'"

missing="$(ldd "${here}/wireless-screen" 2>/dev/null | awk '/not found/{print $1}' || true)"
if [[ -n "$missing" ]]; then
  echo "warning: missing shared libraries: ${missing//$'\n'/,}" >&2
fi

if ! command -v gst-inspect-1.0 >/dev/null; then
  echo "note: gst-inspect-1.0 not found; install GStreamer tools to debug receiving" >&2
else
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

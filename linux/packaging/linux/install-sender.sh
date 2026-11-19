#!/usr/bin/env bash
# Install the Linux sender from this release tarball.
#
#   ./install.sh                 current user: ~/.local
#   sudo ./install.sh --system   all users: /usr/local
#   ./install.sh --uninstall     remove installed files
#
# The desktop entry and user service are rewritten to point at the installed
# binary. Receiver and sender settings are kept on uninstall.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

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
bin="${bindir}/wireless-screen-sender"
desktop="${appdir}/wireless-screen-sender.desktop"
icon="${iconroot}/256x256/apps/wireless-screen-sender.png"
service="${unitdir}/wireless-screen-sender@.service"

if [[ $uninstall == 1 ]]; then
  rm -fv "$bin" "$desktop" "$icon" "$service"
  if [[ $system == 0 ]] && command -v systemctl >/dev/null; then
    systemctl --user daemon-reload 2>/dev/null || true
  fi
  exit 0
fi

for f in wireless-screen-sender wireless-screen-sender.desktop \
         wireless-screen-sender@.service wireless-screen-sender.png; do
  [[ -f "${here}/${f}" ]] || {
    echo "install.sh: ${f} is missing next to this script" >&2
    exit 1
  }
done

install -Dm755 "${here}/wireless-screen-sender" "$bin"
sed "s#^Exec=/usr/bin/wireless-screen-sender#Exec=${bin}#" \
  "${here}/wireless-screen-sender.desktop" | install -Dm644 /dev/stdin "$desktop"
sed "s#^ExecStart=/usr/bin/wireless-screen-sender#ExecStart=${bin}#" \
  "${here}/wireless-screen-sender@.service" | install -Dm644 /dev/stdin "$service"
install -Dm644 "${here}/wireless-screen-sender.png" "$icon"

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
echo
echo "find receivers:  wireless-screen-sender list"
echo "extend desktop:  wireless-screen-sender run"
echo "pick a receiver: wireless-screen-sender run --to <name>"
echo "mirror an output: wireless-screen-sender run --output <output-name>"
echo "start at login:  systemctl --user enable --now 'wireless-screen-sender@<receiver>.service'"

missing="$(ldd "${here}/wireless-screen-sender" 2>/dev/null | awk '/not found/{print $1}' || true)"
if [[ -n "$missing" ]]; then
  echo "warning: missing shared libraries: ${missing//$'\n'/,}" >&2
fi

if [[ "${XDG_SESSION_TYPE:-}" == "x11" ]]; then
  echo "warning: X11 sessions are not supported; use a Wayland desktop session" >&2
elif [[ -z "${WAYLAND_DISPLAY:-}" ]]; then
  echo "note: WAYLAND_DISPLAY is unset; run from a terminal inside the graphical session" >&2
fi

case ":${PATH}:" in
  *":${bindir}:"*) ;;
  *) echo "note: ${bindir} is not on PATH; add it or log out and in again" >&2 ;;
esac

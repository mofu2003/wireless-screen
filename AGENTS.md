# Local macOS development

Keep the development app identities stable. macOS TCC associates Screen
Recording and Accessibility grants with the bundle identifier and signing
requirement; changing either creates stale or duplicate permission entries.

- Sender Debug bundle ID: `io.github.mofu2003.wirelessscreen.mac.debug`
- The standalone receiver target has been retired. Receiver mode runs inside
  the same Debug app and uses the sender's bundle ID.
- Keep Debug builds separate from the release bundle IDs.
- Build the sender into `build/Build/Products/Debug/WirelessScreen Dev.app` and
  launch it with `./run.sh`. The script normalizes an ad hoc build's designated
  requirement before launch. Do not rebuild or re-sign it after granting Screen
  Recording during a test session.
- If the Debug sender's permission row is stale, quit it, run
  `tccutil reset ScreenCapture io.github.mofu2003.wirelessscreen.mac.debug`, launch the
  unchanged app, use its Grant button, and restart it once.

The Intel receiver test host is available as `ssh imac`. Deploy the same
x86_64 Debug app to `~/Applications/WirelessScreen Dev.app`, switch it to
“Receive”, and use its fullscreen video window for end-to-end latency,
presentation, scaling, and visual-quality measurements.

# Linux development (`linux/` workspace)

The Rust workspace builds natively per architecture; see `linux/README.md`.

- Omarchy build/runtime packages: `pkgconf gst-libav gst-plugins-good
  gst-plugins-bad gst-plugin-va gst-plugin-pipewire libva-utils` (headers ship
  with the main `gstreamer`/`gst-plugins-base-libs` packages). Without
  `pkgconf`, build with `--no-default-features` and use `--sink none`.
- Protocol-only loop, no display needed:
  `od-receiver --listen 127.0.0.1:9100 --sink none` and
  `od-fake-sender --connect 127.0.0.1:9100 --clip linux/testdata/clip-320x180-30.h264 --fps 60`.
- Hyprland >= 0.55 is Lua-configured: `hyprctl keyword` is rejected; use
  `hyprctl eval 'hl.monitor({...})'` and verify with `hyprctl monitors -j`.
- The dev VM is behind QEMU user-mode NAT (`10.0.2.x`): a Mac sender on the LAN
  needs a UTM port forward to guest 9000 plus the sender's `-host`/`-port`
  manual endpoint, or the VM switched to bridged networking for Bonjour.

# Release workflow

The inherited upstream release workflows are disabled. Do not re-enable them
until this repository has its own Sparkle keys, Android signing key, update
manifests, rollback test and public source location. See `RELEASING.md`.

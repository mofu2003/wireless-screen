#!/bin/zsh
# Start the 无线副屏 development app. The same app can extend, mirror or enter
# receiver mode; USB connectivity goes through macOS's built-in usbmuxd.
set -e
cd "$(dirname "$0")"

APP="build/Build/Products/Debug/WirelessScreen Dev.app"
if [[ ! -d "$APP" ]]; then
  echo "Mac app not built — run: xcodegen generate && xcodebuild -project WirelessScreen.xcodeproj -scheme WirelessScreenMac -configuration Debug -derivedDataPath build build"
  exit 1
fi

# With no Apple Development identity installed, Xcode signs each build ad hoc.
# Its default designated requirement is the binary's changing cdhash, which
# makes macOS forget Screen Recording after every rebuild. Give the Debug app
# one stable requirement based on its already-distinct bundle identifier.
# A real development signature is left untouched.
STABLE_REQUIREMENT='designated => identifier "io.github.mofu2003.wirelessscreen.mac.debug"'
if codesign -dv "$APP" 2>&1 | grep -q 'Signature=adhoc' \
    && ! codesign -d -r- "$APP" 2>&1 | grep -Fq "$STABLE_REQUIREMENT"; then
  codesign --force --sign - \
    --requirements "=$STABLE_REQUIREMENT" \
    --entitlements Mac/OpenSidecarMac.entitlements \
    "$APP"
  codesign --verify --deep --strict "$APP"
fi

open "$APP"
echo "无线副屏开发版已启动 — 日志位于 ~/Library/Logs/WirelessScreen Dev/wirelessscreen.log。"

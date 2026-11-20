# 无线副屏

“无线副屏”是一个本地优先、无需账号的 Mac 副屏工具。同一个 Mac App 可以
扩展或镜像本机屏幕，也可以接收 Android、Linux 或另一台 Mac 发来的投屏；
支持 USB 与 WiFi 连接。

本仓库是 [OpenDisplay](https://github.com/peetzweg/opendisplay) 的
GPL-3.0 修改版本。当前版本已经更名、更换应用身份并开始独立维护；原始项目
及其作者信息见 `NOTICE`、`LICENSE` 和 `MODIFICATIONS.md`。

## 当前范围

当前发布范围：

- macOS 全能端：`WirelessScreen`（发送扩展屏、发送镜像、接收投屏）
- Android 接收端：`io.github.mofu2003.wirelessscreen`
- Linux 接收端（aarch64/x86_64）：`wireless-screen-receiver`
- Linux 发送端（aarch64/x86_64）：`wireless-screen-sender`

iOS 代码继续保留并可构建，但不是当前发布重点。RK3576 的安装和硬解说明
见 [`linux/README-RK3576.md`](linux/README-RK3576.md)。

## 已有能力

- macOS 真实扩展屏、镜像屏幕，以及在主 App 内接收投屏。
- USB 与 WiFi 自动发现、手动地址连接。
- Android 主动请求 Mac 回连，无需先操作 Mac。
- 触控板式输入：单击、双击、三击、拖动、双指右键、双指滚动。
- Android 悬浮键盘与中文输入法提交。
- Android 支持跟随设备旋转，也可在设置中固定横屏或竖屏。
- 自动、30、60、90、120 fps 帧率选择。
- macOS 与 Android 自动检查并安装已签名的 GitHub Release 更新。
- Linux 发送端和接收端支持 `update --check` 与 `update`：从 GitHub Release
  下载对应架构/变体、校验 SHA-256，并调用现有安装脚本保留配置地覆盖安装。
- Linux 完整包支持 GTK 4 全屏、触摸/鼠标输入回传；兼容包保证旧镜像
  可以显示。两者都支持 Rockchip MPP / mainline V4L2 硬件 H.264 解码优先。
- Linux 发送端支持 Hyprland、Sway、KDE Plasma、GNOME 扩展屏；GNOME
  可用 `--mode mirror` 直接镜像现有桌面，其他 Wayland 合成器可走
  `--mode mirror --output <名称>`。
- 本地画面直连，不上传屏幕内容到服务端。

## 构建

### macOS

需要 macOS 14+ 与 Xcode。工程由 XcodeGen 生成：

```sh
xcodegen generate
xcodebuild test \
  -project WirelessScreen.xcodeproj \
  -scheme WirelessScreenMac \
  -destination 'platform=macOS'
```

本地开发启动：

```sh
xcodegen generate
xcodebuild build \
  -project WirelessScreen.xcodeproj \
  -scheme WirelessScreenMac \
  -configuration Debug \
  -derivedDataPath build
./run.sh
```

接收投屏无需单独构建：启动主 App，在“屏幕模式”里选择“接收投屏”。

### Android

需要 JDK 17 与 Android SDK 36：

```sh
cd Android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK 输出在 `Android/app/build/outputs/apk/debug/`。

### Linux

Linux 发送端使用同一工程的 Rust、Wayland 采集、FFmpeg/x264 编码和 mDNS
发现链路。中文安装、镜像模式、配置和排错见
[`linux/README-SENDER.md`](linux/README-SENDER.md)。

Linux 接收端复用 `linux/` 下的 Rust、GTK 4 和 GStreamer 工程，发布两个包：

- 完整包：Ubuntu 24.04+ / GTK 4.14+，支持 GTK 全屏、触控回传和 DMABuf。
- 兼容包：Ubuntu 22.04 / Debian 12，使用 `waylandsink` 全屏显示和硬解，
  但暂不支持触控回传。

完整包的构建命令：

```sh
cd linux
OD_RECEIVER_VERSION=0.1.5 \
  cargo build --locked --release -p od-receiver
OD_RECEIVER_VERSION=0.1.5 \
  ./packaging/linux/build-receiver-tarball.sh 0.1.5 "$(uname -m)" full
```

兼容包的构建命令：

```sh
cd linux
OD_RECEIVER_VERSION=0.1.5 cargo build --locked --release -p od-receiver \
  --no-default-features --features gstreamer,logind
OD_RECEIVER_VERSION=0.1.5 \
  ./packaging/linux/build-receiver-tarball.sh 0.1.5 "$(uname -m)" compat
```

兼容包不能带 `gtk` 特性；上面的示例已经给出正确的特性组合。RK3576 的
安装、显示和硬解排错见
[`linux/README-RK3576.md`](linux/README-RK3576.md)。

## 更新与发布

发布由 `.github/workflows/release.yml` 负责：

- 推送 `v0.1.5` 这样的 tag，工作流会构建并签名统一的 macOS App、
  Android APK，以及 Linux aarch64/x86_64 接收端和发送端压缩包。
- macOS 使用 Sparkle 的 EdDSA 签名 appcast，默认每 6 小时检查一次。
- Android 使用 `public/android-version.json`，自动下载 APK、校验 SHA-256，
  然后交给 Android 系统安装器确认。客户端使用 `refs/heads/main` 形式的
  raw 地址，避开短路径 CDN 可能返回的陈旧 404。
- Linux 使用 `public/linux-version.json`，为发送端、接收端完整包和兼容包
  分别提供 aarch64/x86_64 的下载地址与 SHA-256。用户运行
  `wireless-screen-sender update` 或 `wireless-screen-receiver update`
  即可检查并安装。
- 更新文件和更新清单必须从公网匿名可读；因此 GitHub 仓库需要是公开仓库。

macOS 目前使用 ad-hoc 签名，未经过 Apple 公证。首次打开或在更新后，macOS
可能要求重新授予屏幕录制、辅助功能和本地网络权限。

## 兼容性

为兼容已有接收端，线上协议继续使用 `_opensidecar._tcp` 和现有 `pv`。
产品改名不会自动破坏旧客户端互连。

## 隐私

画面、触控和键盘数据只在当前连接的两台设备之间传输。应用不包含账号系统、
云端中转或屏幕内容分析。诊断日志只记录连接和错误事件，不记录屏幕内容。

## 许可证与来源

- 许可证：GNU General Public License v3.0，见 `LICENSE`。
- 上游项目：<https://github.com/peetzweg/opendisplay>
- 本产品来源：<https://github.com/mofu2003/wireless-screen>
- 修改说明：`MODIFICATIONS.md`
- 版权与归属：`NOTICE`

分发本产品的二进制或安装包时，必须同时提供对应完整源码，并保留许可证、
版权声明和修改记录。

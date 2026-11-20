# 无线副屏 Linux 接收端（RK3576）

这个接收端把 RK3576 开发板连接的 HDMI/eDP 屏幕变成 Mac 的无线扩展屏
或镜像屏。Mac 运行 `WirelessScreen.app`，RK3576 运行本接收端。

Linux 接收端复用原有 OpenDisplay 协议、GStreamer 解码和 GTK 输入窗口，
不是另做一套投屏协议。板和 Mac 必须在同一局域网，防火墙放行 TCP 9000、
UDP 9001 和 mDNS 5353。

## 先选安装包

Linux 的 GTK/GStreamer ABI 不兼容所有发行版，因此发布两个包：

| 安装包 | 适用范围 | 能力 |
| --- | --- | --- |
| `...-linux-aarch64.tar.gz` | Ubuntu 24.04+、Debian 13+ 或带 GTK 4.14 / GStreamer 1.24 的 RK3576 镜像 | GTK 全屏窗口、鼠标/触摸点击回传、双指风格滚动、硬解、DMABuf |
| `...-linux-aarch64-compat.tar.gz` | Ubuntu 22.04、Debian 12 等较旧镜像 | `waylandsink` 全屏显示、硬解；**不支持触控和滚动回传** |

完整包在 Ubuntu 24.04 上构建，需要目标系统的 glibc、GTK 和 GStreamer
不低于构建基线。兼容包在 Ubuntu 22.04 上构建，不链接 GTK，因此更适合
厂商提供的旧版 RK3576 BSP 镜像。

如果你需要把板端触摸操作传回 Mac，必须使用完整包；旧镜像可以先升级
用户空间，或在板端自行编译 GTK 4.14+ 版本。

## 安装

把对应架构的压缩包传到板子，例如：

```sh
tar xf WirelessScreenReceiver-0.1.5-linux-aarch64.tar.gz
cd WirelessScreenReceiver-0.1.5-linux-aarch64
./install.sh
```

旧镜像使用兼容包：

```sh
tar xf WirelessScreenReceiver-0.1.5-linux-aarch64-compat.tar.gz
cd WirelessScreenReceiver-0.1.5-linux-aarch64-compat
./install.sh
```

安装到当前用户：

```sh
wireless-screen-receiver
```

兼容包没有 GTK 输入窗口，默认启动参数已经写入桌面入口和 systemd 服务；
手动运行请使用：

```sh
wireless-screen-receiver --sink wayland
```

安装到系统目录：

```sh
sudo ./install.sh --system
```

登录后自动全屏：

```sh
systemctl --user enable --now wireless-screen-receiver
```

如果板端镜像没有 systemd 用户会话，或者桌面环境不会启动
`graphical-session.target`，请把启动命令加入桌面自启动，不要直接依赖这个
服务。卸载：

```sh
./install.sh --uninstall
```

## 更新

接收端可以从官方 GitHub Release 检查并安装更新；完整包和兼容包会按当前
编译特性自动选择，不会把兼容包误升级成缺少 GTK 的版本。更新会校验
SHA-256，保留接收端 ID 和配置，并重新写入桌面入口与 systemd 服务。

```sh
wireless-screen-receiver update --check
wireless-screen-receiver update
```

系统级安装使用：

```sh
sudo wireless-screen-receiver update
```

更新需要 `curl`、`tar` 和 `bash`。更新后重新打开接收端，或重启服务：

```sh
systemctl --user restart wireless-screen-receiver
```

当前不会在后台自动安装。如果接收端由发行版、Flatpak 或只读镜像管理，请
使用对应系统方式更新。

## 运行环境

推荐 Wayland 会话。兼容包使用的 `waylandsink` 不能在纯 X11 下工作；
完整包的 X11 路径也没有经过实机测试。

常见 Debian/Ubuntu 运行依赖：

```sh
sudo apt install \
  gstreamer1.0-tools gstreamer1.0-plugins-base \
  gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
  gstreamer1.0-libav libwayland-client0
```

完整包还需要目标系统提供 GTK 4 运行库，例如 Ubuntu 24.04 的
`libgtk-4-1`。安装脚本会检查缺失的共享库和 H.264 解码器。

## RK3576 硬解

程序启动日志会打印板型、可用 H.264 解码器以及最终使用的解码路径。
`decodebin3` 会优先选择硬解；如果硬解协商失败，会自动降级到软件解码。

常见两种 Rockchip 路径：

- **厂商 BSP 镜像**：需要 `gstreamer-rockchip` / Rockchip MPP，元素名是
  `mppvideodec`。检查：

  ```sh
  gst-inspect-1.0 mppvideodec
  ```

- **主线内核镜像**：需要内核中的 `rockchip,rk3576-vdec` V4L2 驱动，以及
  带 `v4l2slh264dec` 的 GStreamer（通常需要 1.28+）。检查：

  ```sh
  gst-inspect-1.0 v4l2slh264dec
  ```

如果两个都没有，安装 `gstreamer1.0-libav` 可退回 `avdec_h264`，但
1080p60 会更吃 CPU。可以强制测试某一条路径：

```sh
wireless-screen-receiver --decoder rockchip
wireless-screen-receiver --decoder mppvideodec
wireless-screen-receiver --decoder v4l2slh264dec
wireless-screen-receiver --decoder software
```

发布包不包含 Rockchip 厂商的 MPP 插件；需要从开发板 BSP、系统包源或
Rockchip 的 `gstreamer-rockchip` 构建链单独安装。

## 60 Hz 与全屏

接收端会把当前显示器的物理分辨率和刷新率报告给 Mac。先确认 HDMI/eDP
输出确实是 60 Hz：

```sh
wlr-randr                 # wlroots
# 或桌面自己的显示设置
```

需要指定输出或覆盖板端报告的参数时：

```sh
wireless-screen-receiver --output HDMI-A-1 --width 1920 --height 1080 --refresh 60
```

兼容包使用：

```sh
wireless-screen-receiver --sink wayland \
  --output HDMI-A-1 --width 1920 --height 1080 --refresh 60
```

窗口模式调试：

```sh
wireless-screen-receiver --windowed
```

## 排错

先看板型、显示器和 GStreamer 选路：

```sh
wireless-screen-receiver
GST_DEBUG=3 wireless-screen-receiver
```

黑屏但日志里有帧：

- 尝试 `--decoder mppvideodec` 或 `--decoder v4l2slh264dec`，确认硬解能用。
- 再用 `--decoder software`，排除硬解驱动问题。
- 完整包可用 `--sink wayland` 或 `--sink gl` 分段定位显示链路；这两个
  sink 不走 GTK 输入窗口，因此不会回传触控。
- 兼容包确认 `gst-inspect-1.0 waylandsink` 有输出，并检查
  `WAYLAND_DISPLAY`、`XDG_RUNTIME_DIR` 是否指向当前桌面会话。

Mac 找不到开发板：

```sh
ip addr
ping <mac-ip>
```

检查板子和 Mac 是否在不同 VLAN，或手动在 Mac 端输入板子的 `IP:9000`。

## 源码构建

完整包需要 GTK 4.14+：

```sh
sudo apt install build-essential pkg-config clang \
  libgtk-4-dev libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev \
  libgstreamer-plugins-bad1.0-dev libwayland-dev
curl https://sh.rustup.rs -sSf | sh
cd linux
OD_RECEIVER_VERSION=0.1.5 cargo build --locked --release -p od-receiver
OD_RECEIVER_VERSION=0.1.5 \
  ./packaging/linux/build-receiver-tarball.sh 0.1.5 "$(uname -m)" full
```

Ubuntu 22.04 / Debian 12 兼容包不链接 GTK：

```sh
OD_RECEIVER_VERSION=0.1.5 cargo build --locked --release -p od-receiver \
  --no-default-features --features gstreamer,logind
OD_RECEIVER_VERSION=0.1.5 \
  ./packaging/linux/build-receiver-tarball.sh 0.1.5 "$(uname -m)" compat
```

## 当前验证边界

发布包在 Ubuntu 24.04 ARM64（完整包）和 Ubuntu 22.04 ARM64（兼容包）
容器内完成编译、依赖检查和协议测试。当前环境没有连接 RK3576 实机，因此
尚未验证具体型号的 HDMI 输出、厂商 MPP 插件、Wayland 全屏行为以及触控
回传；首次在板端部署时请按上面的日志和 `gst-inspect-1.0` 步骤确认。

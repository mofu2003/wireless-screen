# 无线副屏 Linux 发送端

Linux 发送端把当前 Wayland 桌面扩展或镜像到运行“无线副屏”的 Mac，或运行
无线副屏接收功能的 Android 设备。Mac 只有一个 App，它同时负责发送和接收，
不再需要单独安装接收端。发送端复用现有 OpenDisplay v3 协议，不需要云端账号
或中转服务器。

## 运行条件

- Linux 桌面必须是 Wayland 会话，X11 暂不支持。
- 发送端和接收端必须在同一局域网；mDNS 使用 UDP 5353，视频使用 TCP 9000，
  光标侧通道使用 UDP 9001。
- 完整扩展屏自动创建虚拟显示器，支持以下桌面：

  | 桌面 | 最低版本 |
  | --- | --- |
  | Hyprland | 0.55 |
  | Sway | 1.10 |
  | KDE Plasma | 6.8 |
  | GNOME | 50 |

- 其他支持 `ext-image-copy-capture` 和 `zwlr_virtual_pointer` 的 Wayland
  合成器，可用现有显示器镜像：

  ```sh
  wireless-screen-sender run --mode mirror --output <输出名称>
  ```

- RK3576 也可以作为发送端，但板端必须运行带上述 Wayland 合成器的图形会话；
  仅有 framebuffer、Weston kiosk 或 X11 的镜像不能直接使用。

Mac 使用“无线副屏”主 App 的“接收投屏”模式，Android 使用无线副屏 APK。
两者可从[最新 Release](https://github.com/mofu2003/wireless-screen/releases/latest)
下载。

## 安装

根据 `uname -m` 下载 aarch64 或 x86_64 包。发布包内的 FFmpeg 和 x264
是静态链接，目标系统只需提供 glibc、C/C++ 运行库和 PipeWire 运行库。
Debian / Ubuntu 的 PipeWire 运行库包名通常是 `libpipewire-0.3-0`。

```sh
tar xf WirelessScreenSender-<version>-linux-$(uname -m).tar.gz
cd WirelessScreenSender-<version>-linux-$(uname -m)
./install.sh
```

安装到当前用户时，主程序位于 `~/.local/bin`，同时安装桌面入口、
256×256 图标和 `systemd --user` 服务。应用菜单里的“无线副屏发送端”
会直接镜像当前 GNOME 主显示器；扩展屏仍可用命令行 `run` 启动。
安装到系统目录：

```sh
sudo ./install.sh --system
```

卸载：

```sh
./install.sh --uninstall
```

如果 `~/.local/bin` 原本不在 `PATH`，重新登录一次。

## 使用

先打开 Mac 或 Android 上的接收端，并让它停留在前台：

```sh
wireless-screen-sender doctor
wireless-screen-sender list --timeout 10
wireless-screen-sender run
```

- `doctor` 检查桌面协议、编码器和现有输出，不创建显示器。
- `list` 列出发现的接收端名称、地址和协议版本。
- `run` 在只有一个接收端时直接连接；有多个时用 `--to` 选择：

  ```sh
  wireless-screen-sender run --to "Mac"
  wireless-screen-sender run --to "Android"
  wireless-screen-sender run --to 192.168.1.50:9000
  ```

- 默认 `--mode extend` 在桌面右侧创建扩展屏。用 `--position auto-left`、
  `auto-up` 或 `x,y` 调整位置。
- 在 GNOME 上镜像当前桌面：

  ```sh
  wireless-screen-sender run --mode mirror
  ```

  默认镜像 GNOME 主显示器。指定其他显示器：

  ```sh
  wireless-screen-sender run --mode mirror --output eDP-1
  ```

  `doctor` 会列出 Mutter 当前识别的显示器名称。GNOME 镜像使用
  `RecordMonitor`，不创建、不删除显示器；触控会映射到被镜像屏幕。
- 在 Hyprland、Sway、KDE 或其他支持现有输出采集的 Wayland 合成器上，
  `--mode mirror --output` 会走 adopt 模式；也可以继续只写
  `--output <名称>`，保持旧用法。
- 限制帧率或码率以降低 CPU 和 Wi-Fi 占用：

  ```sh
  wireless-screen-sender run --max-fps 30 --bitrate-mbps 12
  ```

- 停止发送后虚拟显示器会自动删除。异常退出留下的虚拟显示器可清理：

  ```sh
  wireless-screen-sender clean
  ```

## 开机启动

```sh
systemctl --user enable --now 'wireless-screen-sender@Mac.service'
```

实例名可使用接收端名称、ID 或 `IP:9000`。名称里的空格需要按 systemd 规则
转义：

```sh
systemctl --user enable --now "wireless-screen-sender@$(systemd-escape 'Pixel 9').service"
```

## 配置文件

默认配置位于：

```text
~/.config/wireless-screen/sender.toml
```

示例：

```toml
to = "Mac"
position = "auto-right"
bitrate-mbps = 20
max-fps = 60
encoder = "auto"
cursor = "auto"

[receiver."Android"]
bitrate-mbps = 12
scale = 2
```

`max-fps`、`encoder`、`cursor` 和 `capture` 是全局配置；只有位置、缩放、
码率和 workspace 可按接收端覆盖。命令行参数优先于接收端专用配置，接收端
配置优先于全局配置。

## 排错

找不到接收端：

```sh
wireless-screen-sender list --timeout 10
wireless-screen-sender run --to <接收端IP>:9000
```

检查双方是否在同一子网，路由器是否启用了客户端隔离，并放行 TCP 9000、
UDP 9001 和 mDNS 5353。VPN 通常不会转发 Bonjour，直接填写 IP 地址。

桌面协议或编码器有问题：

```sh
wireless-screen-sender doctor
RUST_LOG=debug wireless-screen-sender run --to <接收端>
```

KDE Plasma 6.7 及更旧版本的 KWin 只向已安装 desktop entry 对应的绝对路径
授予虚拟显示器协议；请使用本包的 `install.sh`，不要直接从源码目录运行。

## 当前限制

- 仅支持 Wayland，不支持 X11。
- 当前使用 CPU 编码；高分辨率 60 fps 会占用较多 CPU。优先尝试 1080p 和
  30–60 fps。
- 不传输音频，不支持 USB 数据线传输。
- 协议未加密，只应在可信局域网使用。
- Linux 发送端已在 Sway 1.12 上通过到 macOS 接收端的真实端到端测试；
  GNOME 镜像已在无头 Mutter 46 上以约 28 fps 投到统一 macOS App。
  实体 GNOME 桌面、RK3576 实机和实体 Android 平板仍需按实际设备复测。

## 源码构建

Ubuntu 24.04 依赖：

```sh
sudo apt install build-essential pkg-config clang libclang-dev \
  libavcodec-dev libavformat-dev libavutil-dev libswscale-dev \
  libpipewire-0.3-dev libwayland-dev nasm git curl
curl https://sh.rustup.rs -sSf | sh
```

```sh
cd linux
OD_SENDER_VERSION=<version> cargo build --locked --release -p od-sender
./target/release/od-sender doctor
```

发布包使用静态 FFmpeg：

```sh
linux/packaging/ffmpeg/build-static.sh /tmp/ws-ffmpeg
FFMPEG_DIR=/tmp/ws-ffmpeg \
RUSTFLAGS="$(cat /tmp/ws-ffmpeg/rustflags)" \
OD_SENDER_VERSION=<version> cargo build --locked --release -p od-sender
```

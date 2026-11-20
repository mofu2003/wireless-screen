# 无线副屏 Linux

Linux 版现在只有一个程序：`wireless-screen`。同一个程序既能发送 Linux
桌面，也能接收 Mac、Android 或另一台 Linux 的投屏，不再分别安装发送端和
接收端。

底层继续复用现有 WirelessScreen/OpenDisplay v3 协议。发送使用 TCP 9000、
UDP 9001 和 mDNS 5353；数据只在局域网内传输，不需要云端账号或中转。

## 安装

根据 `uname -m` 下载 aarch64 或 x86_64 安装包：

```sh
tar xf WirelessScreen-<version>-linux-$(uname -m).tar.gz
cd WirelessScreen-<version>-linux-$(uname -m)
./install.sh
```

安装到系统目录：

```sh
sudo ./install.sh --system
```

安装器会删除 0.1.x 遗留的 `wireless-screen-sender`、
`wireless-screen-receiver`、桌面入口和 systemd 服务，只保留一个
`wireless-screen`。

## 使用

直接运行会打开终端菜单：

```sh
wireless-screen
```

发送当前 Linux 桌面：

```sh
wireless-screen send
wireless-screen send --to "Mac"
wireless-screen send --to 192.168.1.50:9000
wireless-screen send --mode mirror
wireless-screen send --mode mirror --output eDP-1
```

接收另一台设备的投屏：

```sh
wireless-screen receive
wireless-screen receive --windowed
```

查找和诊断：

```sh
wireless-screen list
wireless-screen doctor
wireless-screen disconnect
wireless-screen clean
```

更多发送参数：

```sh
wireless-screen send --help
```

更多接收参数：

```sh
wireless-screen receive --help
```

## 桌面和开机启动

应用菜单只有一个“无线副屏”入口，默认打开终端菜单，并提供“发送屏幕”和
“接收投屏”两个快捷动作。

登录后自动接收：

```sh
systemctl --user enable --now wireless-screen-receive
```

登录后自动发送到指定设备：

```sh
systemctl --user enable --now 'wireless-screen-send@Mac.service'
```

设备名含空格时先转义：

```sh
systemctl --user enable --now \
  "wireless-screen-send@$(systemd-escape 'Pixel 9').service"
```

## 更新

```sh
wireless-screen update --check
wireless-screen update
sudo wireless-screen update
```

更新会按 CPU 架构选择安装包、校验 SHA-256，并继续安装为同一个
`wireless-screen`。配置、接收端 ID 和已安装的 systemd 设置会保留。

0.1.5 及更早版本把发送和接收拆成了两个程序，旧更新器无法迁移到新的统一
包，因此从旧版本升级到第一个统一版本时需要手动安装一次；之后即可使用
`wireless-screen update`。

## 运行条件

- 推荐 Wayland 桌面。X11 发送不支持；接收窗口在 X11 下未实机验证。
- 发送支持 Hyprland、Sway、KDE Plasma、GNOME 和可镜像现有输出的 Wayland
  合成器。
- 接收需要 GStreamer、GTK 4.14+ 和 H.264 解码器；RK3576 优先使用
  `mppvideodec` 或 `v4l2slh264dec` 硬解，没有硬解时退回
  `avdec_h264`。
- 目标系统需要 glibc、GTK、GStreamer、PipeWire 和 Wayland 运行库。发布包
  中的 FFmpeg/x264 编码器为静态链接。

Debian / Ubuntu 常用运行依赖：

```sh
sudo apt install \
  libgtk-4-1 libpipewire-0.3-0 \
  gstreamer1.0-tools gstreamer1.0-plugins-base \
  gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
  gstreamer1.0-libav libwayland-client0
```

## 限制

- RK3576 实机和实体 GNOME 图形会话仍需单独复测。
- 发送端主要使用 CPU 编码；V4L2 硬件编码取决于板端驱动和编码器。
- 统一包按 Ubuntu 24.04 的 GTK/GStreamer 基线构建。更旧的
  Ubuntu 22.04 / Debian 12 镜像需要先升级用户空间。

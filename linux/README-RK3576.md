# 无线副屏 Linux（RK3576）

Linux 版从 0.2.0 起只有一个 `wireless-screen` 程序：

- `wireless-screen send`：把 RK3576 的 Wayland 桌面发送到 Mac、Android
  或另一台 Linux。
- `wireless-screen receive`：让 RK3576 接收 Mac、Android 或另一台
  Linux 的投屏。
- `wireless-screen update`：更新同一个应用。

板子和接收端必须在同一局域网。防火墙放行 TCP 9000、UDP 9001 和 mDNS
5353。

## 安装

根据架构下载：

```sh
uname -m
tar xf WirelessScreen-<version>-linux-aarch64.tar.gz
cd WirelessScreen-<version>-linux-aarch64
./install.sh
```

安装到所有用户：

```sh
sudo ./install.sh --system
```

安装器会删除旧版遗留的 `wireless-screen-sender`、
`wireless-screen-receiver` 及对应桌面入口和服务，只保留一个
`wireless-screen`。

## 接收投屏

```sh
wireless-screen receive
```

窗口模式调试：

```sh
wireless-screen receive --windowed
```

登录后自动接收：

```sh
systemctl --user enable --now wireless-screen-receive
```

完整包需要 GTK 4.14+、GStreamer 1.24+ 和 Wayland 会话。Ubuntu 24.04
可以直接使用；Ubuntu 22.04、Debian 12 等旧镜像需要先升级用户空间，因为
0.2.0 不再发布无 GTK 的兼容包。

常用运行依赖：

```sh
sudo apt install \
  libgtk-4-1 libpipewire-0.3-0 \
  gstreamer1.0-tools gstreamer1.0-plugins-base \
  gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
  gstreamer1.0-libav libwayland-client0
```

指定输出或覆盖面板参数：

```sh
wireless-screen receive --output HDMI-A-1 \
  --width 1920 --height 1080 --refresh 60
```

## 发送桌面

```sh
wireless-screen list
wireless-screen doctor
wireless-screen send
wireless-screen send --to "Mac"
```

扩展屏默认创建虚拟显示器。GNOME 镜像当前桌面：

```sh
wireless-screen send --mode mirror
```

登录后自动发送：

```sh
systemctl --user enable --now 'wireless-screen-send@Mac.service'
```

Linux 发送要求 Wayland 会话，X11、framebuffer、Weston kiosk 不能直接使用。

## 更新

```sh
wireless-screen update --check
wireless-screen update
sudo wireless-screen update
```

0.1.5 及更早版本拆分发送端/接收端，旧更新器无法自动迁移；需要手动安装
一次 0.2.0，之后即可使用统一更新命令。

## RK3576 硬解

`decodebin3` 会优先选择可用硬解；失败时退回软件解码。检查：

```sh
gst-inspect-1.0 mppvideodec
gst-inspect-1.0 v4l2slh264dec
gst-inspect-1.0 avdec_h264
```

厂商 BSP 通常使用 `mppvideodec`；主线内核通常使用
`v4l2slh264dec`。强制测试：

```sh
wireless-screen receive --decoder rockchip
wireless-screen receive --decoder mppvideodec
wireless-screen receive --decoder v4l2slh264dec
wireless-screen receive --decoder software
```

发布包不包含 Rockchip 厂商的 MPP 插件；需要从 BSP 或系统软件源安装
`gstreamer-rockchip`。

## 60 Hz

接收端会把当前显示器的物理分辨率和刷新率报告给发送端。先确认 HDMI/eDP
输出确实是 60 Hz，再检查：

```sh
wireless-screen receive --output HDMI-A-1 \
  --width 1920 --height 1080 --refresh 60
```

发送端也可以显式限制：

```sh
wireless-screen send --max-fps 60
```

## 排错

```sh
wireless-screen doctor
GST_DEBUG=3 wireless-screen receive
journalctl --user -u wireless-screen-receive -n 100
```

黑屏但日志有帧时，依次尝试 `--decoder rockchip`、`--decoder software`，
以及 `--sink wayland` 或 `--sink gl` 分段定位显示链路。使用
`--sink wayland` 时不会回传触控。

Mac 或 Android 找不到板子时，检查是否在同一 VLAN，并确认 mDNS 5353
没有被防火墙拦截；也可以直接使用板子地址：

```sh
wireless-screen send --to <板子IP>:9000
```

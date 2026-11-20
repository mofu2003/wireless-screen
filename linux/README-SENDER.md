# 无线副屏 Linux 发送功能

从 0.2.0 开始，Linux 发送和接收已经合并到同一个程序
`wireless-screen`，不再安装单独的 `wireless-screen-sender`。完整说明见
[`README-APP.md`](README-APP.md)。

常用命令：

```sh
wireless-screen list
wireless-screen doctor

# 扩展屏
wireless-screen send

# GNOME 镜像当前桌面
wireless-screen send --mode mirror

# 指定接收端
wireless-screen send --to "Mac"

# 限制帧率和码率
wireless-screen send --max-fps 30 --bitrate-mbps 12
```

开机自动发送：

```sh
systemctl --user enable --now 'wireless-screen-send@Mac.service'
```

更新：

```sh
wireless-screen update --check
wireless-screen update
```

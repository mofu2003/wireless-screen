# Linux 开发

Linux 版只有一个用户程序 `wireless-screen`，发送和接收共用同一个二进制。

- 安装与使用：[`README-APP.md`](README-APP.md)
- RK3576、硬解和 60 Hz：[`README-RK3576.md`](README-RK3576.md)
- 发送命令速查：[`README-SENDER.md`](README-SENDER.md)
- 协议：[`../PROTOCOL.md`](../PROTOCOL.md)

## 代码结构

- `crates/od-sender`：Wayland 虚拟显示器、镜像采集、H.264 编码和发送会话。
- `crates/od-receiver`：TCP/UDP 接收、GStreamer 解码、GTK 显示和输入回传。
- `crates/od-update`：GitHub Release 更新清单、SHA-256 校验和覆盖安装。
- `crates/wireless-screen`：统一命令行入口，把 `send`/`receive` 分发到上面
  两套已有实现。
- `crates/opendisplay-proto`、`opendisplay-session`：协议和会话状态机。

## 构建

Ubuntu 24.04 构建依赖：

```sh
sudo apt install \
  build-essential pkg-config clang libclang-dev \
  libpipewire-0.3-dev libwayland-dev \
  libgtk-4-dev libgstreamer1.0-dev \
  libgstreamer-plugins-base1.0-dev nasm

cd linux
OD_APP_VERSION=0.2.0 cargo build --locked --release -p wireless-screen
./target/release/wireless-screen --version
```

发布包使用静态 FFmpeg/x264：

```sh
./packaging/ffmpeg/build-static.sh /tmp/ffmpeg-static
FFMPEG_DIR=/tmp/ffmpeg-static \
RUSTFLAGS="$(cat /tmp/ffmpeg-static/rustflags)" \
OD_APP_VERSION=0.2.0 \
  cargo build --locked --release -p wireless-screen
```

第三方许可证和安装包：

```sh
python3 packaging/third-party-licenses.py . /tmp/ffmpeg-static \
  > ../dist/THIRD-PARTY-LICENSES.txt
OD_APP_LICENSES=../dist/THIRD-PARTY-LICENSES.txt \
  ./packaging/linux/build-app-tarball.sh 0.2.0 "$(uname -m)"
```

## 测试

```sh
cargo fmt --all --check
cargo test --locked --workspace
cargo clippy --locked --workspace --all-targets -- -D warnings
```

`od-fake-sender` 可以无显示器测试协议和接收端；`od-receiver --sink none`
也可以作为纯协议接收端运行。

## 运行

```sh
wireless-screen                 # 终端菜单
wireless-screen send            # 发送
wireless-screen receive         # 接收
wireless-screen list            # 查找接收端
wireless-screen doctor          # 检查发送环境
wireless-screen update --check  # 检查更新
```

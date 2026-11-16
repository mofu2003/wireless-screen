# Android 接收端

Android 应用把手机或平板变成无线副屏的接收端。它可以：

- 通过 Bonjour 被发现，接受 Mac 发送的画面。
- 主动请求已发现且已授权的 Mac 发起连接。
- 通过 USB AOA 接收画面和发送触控、键盘输入。
- 使用悬浮键盘把 Android 输入法提交的文字发送到 Mac。
- 在“设置与帮助 → 显示”中选择跟随设备、固定竖屏或固定横屏。

## 构建

需要 JDK 17 和 Android SDK 36：

```sh
cd Android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug 包名带 `.debug` 后缀，可与正式包并排安装：
`io.github.mofu2003.wirelessscreen.debug`。

## 网络

- Bonjour 服务类型继续使用 `_opensidecar._tcp`，以兼容旧接收端/发送端。
- 默认监听端口为 9000，仅限当前局域网。
- 本应用不上传屏幕内容，也不依赖账号或云服务。

## USB

macOS 发送端通过 Android Open Accessory 模式发送字节流。双方必须使用同一
组 manufacturer/model：

- `WirelessScreen`
- `WirelessScreen Receiver`

Android 端定义在 `AccessoryLink.kt` 和 `res/xml/accessory_filter.xml`，macOS
端定义在 `Mac/USBAccessory.swift`，修改时必须三处同步。

## 更新

应用启动后和每次回到前台时检查上次检查时间，最多每 6 小时访问一次
`public/android-version.json`。发现更高 `versionCode` 后会下载 APK，校验
SHA-256，然后将文件交给 Android 系统安装器；系统仍会显示安装确认。

Release APK 必须使用 `wireless-screen-signing` 中的同一把密钥签名，否则
Android 会拒绝覆盖安装。更新清单和 Release 资产必须公网匿名可读。

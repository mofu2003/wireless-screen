# 修改记录

本仓库是 OpenDisplay 的 GPL-3.0 修改版本。以下记录面向用户和后续维护者，
说明当前独立产品基线相对原项目发生的主要变化。

## 独立产品基线

- 产品显示名改为“无线副屏”，构建名使用 ASCII 的 `WirelessScreen`。
- macOS Bundle ID 改为 `io.github.mofu2003.wirelessscreen.mac` 和
  `io.github.mofu2003.wirelessscreen.mac.receiver`。
- Android applicationId 改为 `io.github.mofu2003.wirelessscreen`。
- 产品更新地址改为 `mofu2003/wireless-screen`，不再访问原产品的更新清单和
  Release 资产。
- 自有签名与更新链路完成前，暂时移除 macOS Sparkle 入口并关闭 Android
  联网更新检查。
- Android Open Accessory 的 manufacturer/model 改为
  `WirelessScreen` / `WirelessScreen Receiver`，两端配对修改。
- 替换应用图标、启动页图示和主要品牌文案。
- 日志、诊断包、虚拟显示器名称和默认调试命令改用新产品身份。
- 协议发现服务 `_opensidecar._tcp`、`pv` 和兼容字段保持不变，避免破坏已有
  客户端互连。

## 继承自本仓库基线的功能修改

- 修复触摸点击偶发继承 `Command` 修饰键的问题。
- 补齐双击、三击、拖动后的点击链重置，并让手指和 Pencil 共用点击追踪。
- 支持双指轻点右键、双指滑动滚动。
- 支持 Android 主动请求 Mac 发起连接。
- 增加 Android 悬浮键盘输入，支持中文输入法提交文本和常用控制键。
- 增加 macOS 帧率选择，并优化 Android 60 fps 接收路径。
- 修复 Android 查找 Mac 列表的蓝底蓝字可读性问题。

修改日期：2026-11-16。

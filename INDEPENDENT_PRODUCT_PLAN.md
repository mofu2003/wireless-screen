# 独立产品化计划

状态：M0 已完成，M1a（身份与更新隔离）已实施，等待最终验证与提交  
基线：`v1.30.3-zh.14` / `d79f58f`  
目标：基于现有 GPL-3.0 代码，做出一个品牌、发布链路和工程质量都属于自己的独立产品，并在核心体验上超过当前版本。

## 0. 第一阶段范围（2026-11-16 确定）

- 产品只以 **macOS 发送端 + Android 接收端** 为第一优先级。
- macOS 页面继续复刻 OpenDisplay 的现有布局和交互，不另做一套视觉结构；
  第一阶段只替换品牌、中文文案、图标和必要的可读性。
- Android 作为主要接收端，优先验证触控、悬浮键盘、反向连接和 60 fps。
- Android UI 继续沿用当前设计语言和页面结构，不重做视觉体系；改动集中在
  品牌替换、文字可读性、状态反馈和触控/键盘操作完善。
- iOS 与 Linux 保持代码可构建和协议兼容，但不阻塞、不参与第一阶段发布。
- macOS 独立接收端代码暂时保留，用于协议回归和备用测试，不作为第一阶段
  发布入口。

## 1. 基本原则

- **合法二开，不做洗稿式复制。** 保留 GPL-3.0、原作者版权和修改记录；对外分发时提供对应源码。
- **先继承，再重构。** 不进行一次性全盘重写。先让现有功能在独立身份下稳定运行，再逐层替换内部实现。
- **协议兼容优先。** 新产品内部可以重构，但已发布的旧客户端应尽量还能互连。
- **一次只改变一类风险。** 品牌迁移、架构重构和功能改动分开提交、分开验证。
- **真实设备验收。** 单元测试通过不代表触控、输入法、解码器和权限恢复没有问题。
- **绝不复制账号与密钥。** Sparkle、Apple、Android 和更新服务全部使用自己的身份和密钥。

## 2. 当前基线

当前仓库是 `peetzweg/opendisplay` 的 GPL-3.0 二开分支，基线相对上游领先 24 个提交、涉及 80 个文件。

主要代码规模：

| 模块 | 规模 | 主要风险 |
| --- | ---: | --- |
| macOS / iOS Swift | 约 16,000 行 | `MacSender.swift` 3,470 行、`StreamReceiver.swift` 1,631 行、macOS UI 1,529 行 |
| Android Kotlin | 约 7,200 行 | `MainActivity.kt` 994 行，网络、解码、输入和更新状态集中在少数类 |
| Linux Rust | 约 25,000 行 | 已分层，可作为协议和会话状态机的参考实现 |

现有自动化测试：168 个 Swift 测试、57 个 Android 测试、196 个 Rust 测试。

当前需要替换的产品身份：

- macOS Bundle ID：`com.peetzweg.opensidecar.mac`、`.receiver`
- iOS Bundle ID：`com.peetzweg.opensidecar.ios`
- Android 包名：`com.peetzweg.opendisplay`
- Kotlin namespace：`app.opendisplay.android`
- 更新地址：`mofu2003/opendisplay` 的 GitHub raw 与 Release 资产
- Sparkle 公钥与签名私钥
- 图标、截图、网站、App Store / Play 链接、日志名、诊断包名

协议发现类型 `_opensidecar._tcp` 在兼容期保留。它属于线上协议，不应和产品品牌迁移混在一起改。

## 3. 产品范围

### 已确定的产品身份

- 中文显示名：**无线副屏**
- 代码与构建名：`WirelessScreen`
- GitHub 仓库名：`wireless-screen`
- GitHub 所有者：`mofu2003`
- Bundle ID 前缀：`io.github.mofu2003.wirelessscreen`
- macOS Sender：`io.github.mofu2003.wirelessscreen.mac`
- macOS Receiver：`io.github.mofu2003.wirelessscreen.mac.receiver`
- iOS Receiver：`io.github.mofu2003.wirelessscreen.ios`
- Android applicationId / namespace：`io.github.mofu2003.wirelessscreen`

对外显示名统一使用“无线副屏”。Xcode target、可执行文件、Bundle ID、Gradle namespace 和脚本路径全部使用 ASCII 名称，避免签名、路径和 CI 兼容问题。

Receiver 的显示名使用“无线副屏接收端”，构建名使用 `WirelessScreenReceiver`。

第一阶段聚焦：

- macOS Sender
- macOS Receiver
- Android Receiver

iOS 和 Linux 在第一阶段保持可构建、协议兼容，不作为新功能重点。这样可以先把用户实际使用的 Mac + Android 路径做到最好。

### M1a 已落地

- macOS Sender：`WirelessScreen` /
  `io.github.mofu2003.wirelessscreen.mac`
- macOS Receiver：`WirelessScreenReceiver` /
  `io.github.mofu2003.wirelessscreen.mac.receiver`
- Android：显示名“无线副屏” /
  `io.github.mofu2003.wirelessscreen`
- 已添加 `NOTICE`、`MODIFICATIONS.md` 和独立 README。
- 已接入自有 Sparkle EdDSA 密钥、GitHub Release appcast 和 macOS 检查更新入口。
- 已恢复 Android 联网自动更新，使用自有更新清单和签名 APK。
- 已替换三套 App 图标、公开 logo、AOA 身份、日志与诊断名称。
- 已删除继承的 Release、Pages、Linux Release 和更新清单同步工作流。

发布方式先采用：

- macOS：GitHub Release 的签名 ZIP / DMG + Sparkle 更新
- Android：签名的 GitHub Release APK
- 源码：对应 tag 的完整源码包

App Store、Google Play 和商用发行不作为第一阶段的阻塞项。

## 4. 产品定义

“更好”不能只靠功能数量判断，先固定四个指标：

1. **连接可靠**：睡眠唤醒、切换网络、拔线、对端退出后能明确恢复或失败。
2. **输入可信**：单击、双击、三击、拖动、右键、滚动、键盘和输入法行为稳定且可测试。
3. **画面稳定**：目标平板上可持续接近 60 fps，延迟和丢帧不劣于基线。
4. **更新安全**：更新可验证来源、可回滚，安装后不会因权限或签名变化失效。

建议的产品定位是“低延迟、本地优先、无需账号的 Mac 副屏”，而不是简单复制原项目全部平台的宣传页。

## 5. 里程碑

### M0：身份与合规决策

这是开始改代码前的决策门。

需要确定：

- GitHub 组织或账号
- 是否使用自有域名替代 `io.github.mofu2003`
- 官网和更新清单域名
- Apple Development Team、Sparkle 密钥、Android 签名密钥
- 是否需要从当前 `com.peetzweg.*` 应用迁移设置；默认不做跨 Bundle ID 自动迁移

默认建议：

- 产品显示名统一使用“无线副屏”，工程标识使用 `WirelessScreen`。
- 使用 GitHub 账号 `mofu2003`，Bundle ID 前缀使用 `io.github.mofu2003.wirelessscreen`。
- 新产品使用全新 Bundle ID 和 Android applicationId，与原版并排安装。
- 新产品不再使用 `OpenDisplay` 作为产品名，只在版权、来源说明和第三方声明中提及。
- 更新清单位于自己的稳定域名或仓库，不继续使用 `raw.githubusercontent.com/mofu2003/...`。

退出条件：

- 名称、IDs、仓库和密钥负责人全部确定。
- 形成一页不可再改的身份表，后续代码只引用这一份配置。

### M1：独立基线版本

目标是不改变功能，只把产品从上游身份中完整剥离。

已完成：

- 保留 `LICENSE`，新增 `NOTICE` 和 `MODIFICATIONS.md`。
- 在 README 和关于页面明确：此项目基于 OpenDisplay，当前版本已修改。
- 替换 macOS/iOS 显示名、macOS/iOS Bundle ID、Android applicationId、日志名和诊断文件名。
- 替换 macOS、macOS Receiver、iOS 和 Android 图标与公开 logo。
- 把品牌字符串、服务名、URL、版本通道集中到单一配置，禁止在业务代码里散落硬编码。
- 自动更新使用自有签名与 GitHub Release 通道；正式跨设备测试后继续保持。
- 清除产品代码中的原更新地址、Ko-fi、TestFlight、Google Group、App Store 跳转，保留来源归属链接。
- 新建独立仓库，将当前提交作为 `baseline/1.30.3-zh.14` 保存；上游只保留只读 `upstream` remote。

仍待完成：

- 将 Kotlin namespace/目录从 `app.opendisplay.android` 改为
  `io.github.mofu2003.wirelessscreen`。
- 完成 iOS、Linux、网站和文档中的剩余品牌清理。
- 建立新的 Sparkle 密钥、Android 密钥、公开更新清单和回滚链路。
- 在目标 Android 平板上复测触控、键盘、输入法和 60 fps。

退出条件：

- 产品代码中不存在 `peetzweg`、`mofu2003` 或旧品牌，白名单仅允许版权、来源和第三方兼容说明。
- 新产品可在同一台 Mac 和 Android 平板上与原版并排安装。
- macOS 和 Android 全套测试通过，两个 macOS 应用可正常启动。
- 发布包不含任何旧签名、旧密钥或旧更新地址。

### M2：质量基线

在重构前先知道“现在到底有多好”。

任务：

- 在目标 Mac 和 Android 平板上记录分辨率、刷新率、传输方式和系统版本。
- 测量连接时间、端到端 p50/p95、输入延迟、采集帧率、解码帧率、丢帧、CPU/GPU、内存和断线恢复时间。
- 为连接、重连、睡眠唤醒、权限撤销、双指点击和中文输入法建立手工回归清单。
- 把协议消息整理成跨 Swift、Kotlin、Rust 共用的 JSON fixture。
- 建立旧版 Receiver ↔ 新版 Sender、新版 Receiver ↔ 旧版 Sender 的兼容测试。

退出条件：

- 形成可重复的基线报告。
- 关键路径有自动化测试或明确的手工验收步骤。
- 后续每次升级都能判断是否发生性能回归。

### M3：核心架构拆分

原则是保持外部行为不变，只移动职责和增加测试。

macOS：

- 将 `MacSender` 拆成 `SessionStateMachine`、`CapturePipeline`、`EncoderPipeline`、`MediaTransport`、`CursorChannel`、`InputRouter` 和 `RecoveryPolicy`。
- `MacSender` 暂时保留为协调 facade，避免一次性改掉所有调用点。
- 将 `StreamReceiver` 拆成监听、传输、控制、视频解码、渲染和统计模块。
- 将 macOS 主界面中的设备发现、会话管理和视图状态从单个文件移出。

Android：

- 从 `MainActivity` 提取 `ReceiverViewModel`、触控视图、键盘协调器和更新协调器。
- 将 `ReceiverServer` 拆成 Link、ControlSession、VideoSession 和 CursorSession。
- 把触控、指针和输入法逻辑统一到一个可测试的输入状态机。

跨平台：

- `PROTOCOL.md` 和 JSON fixture 作为协议事实来源。
- Swift、Kotlin、Rust 使用同一批正常、边界和畸形消息测试。
- 不在本轮修改线上 `pv` 或最低支持版本。

退出条件：

- 原有功能行为不变。
- 新模块有明确输入输出和单元测试。
- 旧 facade 与模块边界之间没有循环依赖。
- 大型类不再同时负责网络、状态、UI 和错误恢复。

### M4：体验与性能提升

按用户价值排序，Android + macOS 优先。

P0：

- 触控板完整手势：单击、双击、三击、拖动、双指右键、滚动、取消和掌压误触处理。
- 键盘与输入法：组合输入、候选词、删除、回车、Tab、Esc 和修饰键策略。
- 连接恢复：睡眠唤醒、Wi-Fi 切换、USB/无线迁移、对端退出和端口占用。
- 自适应画质：根据延迟、队列和丢帧动态调整码率，避免无上限排队。
- 权限诊断：明确显示屏幕录制、辅助功能、本地网络和输入法缺少什么。

P1：

- 带配对和加密的可信连接。
- 多设备、多显示器的稳定命名和排列恢复。
- 剪贴板与快捷键支持。
- 音视频同步和音频转发。

每一项功能都必须附带：

- 用户可复现的验收步骤。
- 自动化测试或明确说明为何只能真机验证。
- 对旧协议客户端的行为变化说明。

退出条件：

- 目标 Android 平板在静态和滚动场景均达到 60 fps 目标或明确给出硬件上限。
- 所有 P0 输入操作连续 30 分钟压力测试无粘连、无卡键、无误触。
- 连接恢复和更新失败路径有可见、可理解的提示。

### M5：首个独立发布

任务：

- 重新设计 Release 工作流，不沿用上游 App Store/Play 的必需密钥。
- macOS 使用自己的签名、公证和 Sparkle 密钥；Android 使用自己的 APK 签名。
- 发布资产包含 macOS Sender、Receiver、Android APK、SHA-256、release notes 和源码包。
- 更新清单、下载地址和旧版本回滚全部实测。
- 在干净用户目录和干净设备上测试首次安装与覆盖升级。
- 发布说明写明上游来源、修改内容、许可证和新旧版本兼容关系。

退出条件：

- 从空环境按文档可完成构建和安装。
- 更新过程无崩溃、无权限重复登记、无旧图标或旧品牌残留。
- Release 资产可从公网重新下载并通过校验。
- 工作区干净，密钥不进入 Git。

## 6. 建议的首批代码任务

1. 建立产品身份配置文件，集中生成 Bundle ID、名称、URL、服务名和版本。
2. 新建独立仓库与只读 `upstream` remote，打基线 tag。
3. 添加 `NOTICE`、`MODIFICATIONS.md` 和许可证展示入口。
4. 用配置替换 macOS、Android、iOS 的硬编码品牌与地址。
5. 关闭自动更新，等待新密钥和新 feed 就绪。
6. 替换图标、截图、网站素材和应用内文案。
7. 重跑 Swift、Android、Rust 测试并记录基线。
8. 建立协议 fixture 和跨语言兼容测试。
9. 提取 macOS Sender 的会话状态机。
10. 提取 Android 输入状态机并补齐触控板验收测试。

## 7. 工程约束

- `main` 始终保持可构建、可发布。
- 品牌迁移使用单独分支和提交，不与架构重构混合。
- 每次改动必须更新 `CHANGELOG.md` 或独立的发布记录。
- 任何新的公网地址、隐私收集或更新机制都必须在发布说明中披露。
- 禁止提交签名密钥、更新私钥、Apple 凭据、设备标识和用户数据。
- 上游代码更新只通过人工审阅的 merge/cherry-pick 进入，不自动跟随。

## 8. 风险与控制

| 风险 | 影响 | 控制方式 |
| --- | --- | --- |
| 修改 Bundle ID 导致权限和设置丢失 | macOS 屏幕录制、辅助功能重新授权 | 新产品使用新 ID 并接受并排安装；如需迁移单独设计导入导出 |
| 协议名和产品名同时修改 | 旧版无法互连 | 第一阶段保留 `_opensidecar._tcp` 和 `pv` |
| 全盘重写引入回归 | 原有功能失效 | 先做 facade 和模块拆分，再替换实现 |
| macOS 签名与 Sparkle 配置错误 | 更新后无法启动 | 独立测试更新和回滚，不复制上游密钥 |
| Android 设备差异 | 帧率、触控和输入法表现不一致 | 固定设备矩阵并记录真实指标 |
| 上游继续演进 | 分叉积累冲突 | 上游只读，按月评估有价值的提交 |
| GPL 或商标处理不完整 | 分发风险 | 保留许可证、修改声明、替换品牌资产并附源码 |

## 9. “更好”的完成定义

当以下条件同时满足，才认为新产品超过基线：

- 目标设备上的 P0 功能全部可用，且没有点击、键盘或连接方面的已知严重问题。
- 60 fps、延迟、丢包恢复和内存占用不劣于基线，核心场景至少有一项可测提升。
- 同一套功能有跨平台协议测试和真实设备验收记录。
- 新产品可以独立构建、签名、更新、回滚，不依赖原项目密钥和地址。
- 许可证、来源说明和对应源码完整，产品品牌与上游清晰区分。

## 10. 待决策

| 项目 | 当前状态 | 默认建议 |
| --- | --- | --- |
| 产品名 | **无线副屏** | 已确定，不再增加英文公开名 |
| Repository | **mofu2003/wireless-screen** | 独立仓库，保留 upstream 只读 |
| 反向域名前缀 | **io.github.mofu2003.wirelessscreen** | 已确定，无自有域名 |
| 第一发布平台 | 未定 | macOS + Android |
| 是否公开发布 | 未定 | 先私有开发，稳定后公开源码和二进制 |
| 是否迁移旧设置 | 未定 | 第一版不自动迁移 |
| iOS / Linux | 未定 | 保持兼容，不阻塞第一版 |

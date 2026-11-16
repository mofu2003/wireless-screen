# 发布流程

“无线副屏”使用 GitHub Releases 作为 macOS Sparkle 和 Android 更新源。

## 当前状态

- macOS Sender 与 Receiver 使用 Sparkle 2.10.1，更新签名使用本仓库自己的
  EdDSA 密钥。
- Android 使用 SHA-256 校验的 APK 更新清单，最终安装仍由系统确认。
- Release 工作流由 `vX.Y.Z` tag 触发，也支持手动重跑。
- 更新清单和 Release 资产要求公网匿名可读，因此仓库必须公开。

## 发布步骤

1. 更新 `CHANGELOG.md` 中对应版本的小节。
2. 提交并推送 `main`。
3. 创建并推送 tag，例如 `git tag v0.1.0 && git push origin v0.1.0`。
4. 等待 `Release` 工作流完成：构建、签名、生成 appcast、创建 Release，
   并把 `public/android-version.json`、`public/appcast.xml` 和
   `public/appcast-receiver.xml` 提交回 `main`。
5. 下载 Release 中的 APK 和两个 macOS zip，确认地址可匿名访问。
6. 安装旧版本，验证 macOS 手动“检查更新”和 Android 自动检查；再验证一次
   同签名 APK 覆盖安装。

## 必须配置的仓库 Secrets

- `WIRELESSSCREEN_SPARKLE_PRIVATE_KEY`
- `WIRELESSSCREEN_ANDROID_KEYSTORE_BASE64`
- `WIRELESSSCREEN_ANDROID_KEYSTORE_PASSWORD`
- `WIRELESSSCREEN_ANDROID_KEY_ALIAS`
- `WIRELESSSCREEN_ANDROID_KEY_PASSWORD`

签名材料和密码保存在本机
`/Users/fansicheng/Projects/wireless-screen-signing`，不要提交到 Git。
macOS 当前没有 Apple Developer ID，Release 使用 ad-hoc 签名；这不会阻止
Sparkle 的 EdDSA 更新验证，但系统权限可能需要在更新后重新授予，且应用未经过
Apple 公证。

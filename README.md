# HyperOS Passkey Fix

一个面向小米 HyperOS 的小型 Android 工具：通过 [Shizuku](https://shizuku.rikka.app/) 在无需电脑连接 ADB 的情况下，读取或写入 Android Credential Manager 使用的 `Settings.Secure` 凭据服务设置。

默认目标是 1Password 的 Android Credential Provider：

```text
com.onepassword.android/.autofill.services.CredentialManagerService
```

## 要解决的问题

HyperOS 提供了自己的通行密钥（Passkey）管理器，并通过 Android Credential Manager 提供服务。公开案例显示，在部分中国大陆版 HyperOS 设备上，即使已在系统设置中选中 Bitwarden、1Password 或 KeePassDX 等第三方密码管理器，系统底层的 `credential_service` / `credential_service_primary` 仍可能保留为小米的 Credential Provider；于是第三方 Passkey 不会被正常拉起或使用。

这个项目将这两个安全设置项显式写入所选的第三方 Credential Provider，以解决“设置页面看似已选中第三方服务、实际 Passkey 仍由小米服务接管或不可用”的配置不同步问题。

相关背景：小米官方说明 HyperOS 的通行密钥服务基于 Android Credential Manager；社区排查则记录了上述第三方 Provider 配置未同步的现象。

- [小米 HyperOS 通行密钥开发指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1939)
- [HyperOS 第三方 Credential Provider 问题排查（KeePassDX）](https://blog.moo.ac/posts/keepassdx/)

## 工作方式

应用取得 Shizuku 明确授权后，启动一个 shell/root 身份的 Shizuku UserService，执行等价于以下命令的操作：

```sh
settings put secure credential_service <组件名>
settings put secure credential_service_primary <组件名>
```

它不申请也不依赖 `WRITE_SECURE_SETTINGS`；写入权限来自用户自行启动并授权给应用的 Shizuku 服务。

## 使用方法

1. 在手机上安装并启动 [Shizuku](https://shizuku.rikka.app/download/)，可使用无线调试、ADB 或 root 后端。
2. 安装本应用，点击“授权 / 连接 Shizuku”，并在 Shizuku 中授予此应用权限。
3. 确认或修改 Credential Provider 组件名，再点击“写入两个凭据服务设置”。
4. 使用“读取当前设置”确认写入值；如系统界面仍显示旧状态，可重启相关应用或设备。

## 写入前的注意事项

- 先点击“读取当前设置”并保存原值，以便需要时通过 ADB 或本工具恢复。
- 不同密码管理器、版本和分发渠道的包名/服务类名可能不同；不要照抄不属于当前已安装应用的组件名。
- 部分 HyperOS 版本会在 `credential_service` 中同时保留小米 Provider，而将第三方 Provider 置于首位；本项目按用户指定的组件名直接写入两个键。若你的系统需要保留多 Provider，请在输入框内提供以冒号分隔的完整服务列表，并先记录原始值。
- Android / HyperOS 更新可能改变这些内部设置的行为；本项目不保证适用于所有机型、区域版本或系统版本。

## 构建

```sh
./gradlew :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 开源许可

本项目采用 [WTFPL Version 2](LICENSE) 许可。

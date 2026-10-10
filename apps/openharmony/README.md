# 一餐一动：OpenHarmony / 开鸿客户端

ArkTS / ArkUI Stage 工程，最低 API 12。设备必须提供兼容 API 12 的 OpenHarmony 标准系统应用框架；不再声称兼容 API 10 / 4.1。使用 OpenHarmony SDK 独立构建。

界面与服务逻辑与 HarmonyOS 版本对应，但 **OpenHarmony HAP 不保证可以安装在 Huawei 市售 HarmonyOS 6 设备上**。市售机请构建 `apps/harmony`，并使用 Huawei 设备授权 profile 签名；开鸿终端的签名信任规则取决于设备发行版。

## 构建

安装包含 API 12 的 OpenHarmony SDK、ohpm 与 Hvigor，设置 `OHOS_SDK_HOME`、`HVIGOR_EXECUTABLE`、`OHPM_EXECUTABLE`。如安装不同 Hvigor 主版本，设置匹配的 `HVIGOR_PLUGIN_VERSION`。

```sh
node tools/build-hap.mjs --platform openharmony --version-name 1.0.1 --version-code 1000001
```

真实 release HAP 输出到 `dist/openharmony`，缺少 SDK 或编译失败会停止。签名时设置 `HAP_CERT_PATH`、`HAP_PROFILE_PATH`、`HAP_KEYSTORE_PATH`、`HAP_KEY_ALIAS`、`HAP_KEY_PASSWORD`、`HAP_STORE_PASSWORD` 并加 `--signed`；证书与 profile 必须被该设备系统信任。

```sh
hdc install dist/openharmony/health2609-openharmony-1.0.1-signed.hap
hdc shell aa start -a EntryAbility -b org.openharmony.health2609
```

午餐、手动运动、真实照片 AI 识别与汇总使用统一 API。网络故障只回退到真实缓存，无虚构菜单、运动或识别结果。家庭餐和手动运动超时会保留输入，避免不确定请求自动回放产生重复数据。其余能力限制和身份接入状态见 [HarmonyOS 说明](../harmony/README.md)。

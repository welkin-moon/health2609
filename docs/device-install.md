# 一餐一动设备安装

## Android

下载 Release 中的 `yicanyidong-android-debug.apk` 安装。包名 `uk.lunarlab.health2609`，版本 0.4.1。CI 支持私有 keystore secrets 生成 release 签名 APK；未配置时使用 runner 的 debug key，不能保证覆盖旧包升级。若 Android 提示签名冲突，应保留旧应用和数据，确认私密记录备份与恢复短语后再迁移。先保留旧版与记录，不能通过直接卸载解决而不考虑本地记录丢失。

## iPhone / iPad

Release 中的 IPA 是真实 iPhone/iPad arm64 应用包，未经分发签名。使用自己的有效企业/开发/Ad Hoc 证书和相匹配的 provisioning profile 重签名后，可以通过支持该签名方式的安装工具导入。IPA 本身不会绕过 iOS 签名验证，证书失效时仍受系统限制。

默认包无需 HealthKit entitlement，支持手动运动、菜单与餐食记录、照片识别。HealthKit 版需要启用该能力且相匹配的签名 profile；普通重签名不能保证有 HealthKit 权限。

## HarmonyOS 6 市售机

HarmonyOS 和 OpenHarmony 共享业务代码，但签名环境不能混用。市售华为设备需要华为 HarmonyOS SDK 构建与签名证书/profile，调试 profile 必须包含设备 UDID，设备应开启开发者模式。不会把源码 ZIP、结构检查通过或 OpenHarmony 签名包标为可安装的鸿蒙 6 包。

已发布真实 Hvigor release 模式构建的 unsigned HAP：

- [HarmonyOS / OpenHarmony HAP release](https://github.com/welkin-moon/health2609/releases/tag/v0.4.1-hap.d60cac8)

自己的华为市售机使用 `health2609-harmony-0.4.1-unsigned.hap`，应用 bundleName 为 `uk.lunarlab.health2609.harmony`。未签名包不能直接安装。连接手机并完成 USB 调试授权后，可用 `hdc shell bm get -udid` 读取设备 UDID；在 DevEco Studio 的 Signing Configs 中登录自己的华为开发者账户并启用自动签名，选择这台设备，或提供匹配 bundle/UDID 的手动签名材料。

[HarmonyOS 工程说明](../apps/harmony/README.md)列出本地 `--signed` 构建需要的环境变量与安装命令。私钥和密码保留在自己的电脑或私有 secrets 中，不要提交到公开仓库。缺少设备授权签名材料时，市售机可安装交付仍未完成。

## 发布边界

本版按要求仅作源码推理审查与实际编译打包，未跑测试或真机验证。Android 有本地优先 E2EE journal；iOS/鸿蒙/开鸿的完整跨设备私密记录同步尚未达到 Android 实现水平，不应将 API 名称覆盖等同于功能完成。

# 一餐一动设备安装

## Android

下载 Release 中的 `yicanyidong-android-debug.apk` 或私有签名的 `yicanyidong-android-release.apk` 安装。包名 `uk.lunarlab.health2609`，版本 0.4.2。CI 支持私有 keystore secrets 生成 release 签名 APK；未配置时使用 runner 的 debug key，不能保证覆盖旧包升级。若 Android 提示签名冲突，应保留旧应用和数据，确认私密记录备份与恢复短语后再迁移。不能直接卸载而丢失尚未同步的本地记录。

2026-10-11：已将 0.4.2 APK 在用户电脑使用既有本机 keystore 重签，通过 ADB `install -r` 成功覆盖升级 Pura 70 Ultra（当前为 HarmonyOS 4.2 / Android 12）。包管理器确认 versionCode 19、versionName 0.4.2，原安装日期保留；未卸载或清空应用数据。完整业务流程尚未在该手机验证。

## iPhone / iPad

Release 中的 IPA 是真实 iPhone/iPad arm64 应用包，未经分发签名。使用自己的有效企业/开发/Ad Hoc 证书和相匹配的 provisioning profile 重签名后，可以通过支持该签名方式的安装工具导入。IPA 本身不会绕过 iOS 签名验证，证书失效时仍受系统限制。

默认包无需 HealthKit entitlement，支持加密账号/本地日记/手动同步、手动运动、菜单与餐食记录、照片识别。HealthKit 版需要启用该能力且相匹配的签名 profile；普通重签名不能保证有 HealthKit 权限。重签时保持应用及 Keychain 的访问身份，换证书或 bundle 后原本机密钥未必可访问，先确认同步与恢复短语。

## HarmonyOS 6 市售机

HarmonyOS 和 OpenHarmony 共享业务代码，但签名环境不能混用。市售华为设备需要华为 HarmonyOS SDK 构建与签名证书/profile，调试 profile 必须包含设备 UDID，设备应开启开发者模式。不会把源码 ZIP、结构检查通过或 OpenHarmony 签名包标为可安装的鸿蒙 6 包。

已发布真实 Hvigor release 模式构建的 unsigned HAP：

- [HarmonyOS / OpenHarmony HAP release](https://github.com/welkin-moon/health2609/releases/tag/v0.4.2-hap.d431e6f)

自己的华为市售机使用 `health2609-harmony-<版本>-unsigned.hap`，应用 bundleName 为 `uk.lunarlab.health2609.harmony`。未签名包不能直接安装。连接手机并完成 USB 调试授权后，可用 `hdc shell bm get -udid` 读取设备 UDID；在 DevEco Studio 的 Signing Configs 中登录自己的华为开发者账户并启用自动签名，选择这台设备，或提供匹配 bundle/UDID 的手动签名材料。

[HarmonyOS 工程说明](../apps/harmony/README.md)列出本地 `--signed` 构建需要的环境变量与安装命令。私钥和密码保留在自己的电脑或私有 secrets 中，不要提交到公开仓库。缺少设备授权签名材料时，市售机可安装交付仍未完成。

## 发布边界

本版先通过源码推理发现并修复问题；用户随后授权测试，隔离后端测试 87/87 通过，类型检查和多端结构校验通过，Android、iOS、HarmonyOS、OpenHarmony 的实际 SDK 编译打包均成功。Android 已完成上述真机覆盖安装；iOS 重签安装与鸿蒙 6 原生 HAP 签名安装尚未完成。三端均有本机加密、账户隔离、离线个人记录与手动 E2EE 同步；新记录使用认证删除标记和来源设备的 v2 envelope，旧 v1 可读但历史元数据保护较弱。照片识别会上传主动选择的图片；学校菜单和课程仍使用演示学校身份。鸿蒙的系统历史健康数据接入、实际设备签名和安装仍未完成。

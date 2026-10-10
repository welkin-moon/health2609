# 一餐一动多端迁移状态

## 2026-10-10 状态

共享 API 路由、目录和 UI 源码并不代表每个端已完成迁移。当前各端的交付边界如下：

| 平台 | 工程与功能 | 安装包要求 | 未完成项 |
| --- | --- | --- | --- |
| Android | 主学生端，Health Connect、本地优先 E2EE journal | Gradle debug 签名 APK | 本版未做真机验证；旧账号升级可能需要恢复短语 |
| iOS/iPadOS | SwiftUI Xcode 应用，真实照片选择/相机，手动运动和菜单记录 | 默认 arm64 unsigned IPA 重签后安装；可选 HealthKit 版 | 需要有效证书/profile；完整 E2EE journal 未实现 |
| HarmonyOS | ArkUI Stage，真实照片上传，诚实的离线状态 | 华为 SDK Hvigor HAP 构建；市售机需华为设备签名 | Hvigor release 构建已成功、真实 unsigned HAP 已发布；市售机签名仍缺设备授权材料；完整 E2EE、历史健康数据适配及个人身份接入未完成 |
| OpenHarmony/开鸿 | 独立目标与相同核心业务源码 | OpenHarmony API 12 release HAP 已真实构建并发布；仍需对应签名 | 不能拿开鸿包替代市售鸿蒙签名；同样未完成全量 E2EE 和历史健康数据适配 |
| 管理端/Worker | TypeScript 与共享 contracts，学校菜单/课程/汇总 | Web 静态包与 Worker bundle | 学校侧仍为演示身份，与私密同步账号分离 |

## 发布构建

本轮按要求以源码推理发现与修复问题，不执行测试。CI 仅进行编译和打包，结构字符串检查不作为 HAP/iOS 可安装证明。

- `.github/workflows/ci.yml`：Web/Worker 类型检查与生产打包。
- `.github/workflows/device-release.yml`：Android APK、iOS Xcode device IPA 成功后发布真实资产。
- `.github/workflows/hap-build.yml`：从华为工具仓获取指定版本工具并校验下载 SHA-256，实际执行两个平台 Hvigor 构建。构建失败时不生成替代资产。
- `tools/build-hap.mjs`：支持 SDK 环境和隔离签名；签名 profile 需要授权设备与匹配 bundle。

详见 [device-install.md](device-install.md)。完整 Android 私密同步设计见 [e2ee-sync.md](e2ee-sync.md)。

## 已发布的实际产物

- [Android APK 与两种 iOS arm64 IPA（0.4.1）](https://github.com/welkin-moon/health2609/releases/tag/v0.4.1-device.14998ae)。IPA 分普通重签版与 HealthKit 版。
- [HarmonyOS / OpenHarmony unsigned release HAP（0.4.1）](https://github.com/welkin-moon/health2609/releases/tag/v0.4.1-hap.d60cac8)。[实际构建日志](https://github.com/welkin-moon/health2609/actions/runs/38033668035)确认两个平台 `BUILD SUCCESSFUL`。市售机签名及真机运行仍未验证。

后端同步修复与 D1 mutation guard 迁移已部署。上述产物仅确认编译打包成功，没有执行测试或真机验证。

# 一餐一动多端迁移状态

## 2026-10-11 状态

共享 API 路由、目录和 UI 源码并不代表每个端已完成迁移。当前各端的交付边界如下：

| 平台 | 工程与功能 | 安装包要求 | 未完成项 |
| --- | --- | --- | --- |
| Android | 主学生端，Health Connect、本地优先 E2EE journal | Gradle debug 签名 APK | Pura 70 Ultra（HarmonyOS 4.2）已本机重签覆盖升级 0.4.2；完整业务流程尚未验证 |
| iOS/iPadOS | SwiftUI Xcode 应用，真实照片选择/相机，本地加密日记、账号/恢复/设备/密钥管理与手动同步 | 默认 arm64 unsigned IPA 重签后安装；可选 HealthKit 版 | 需要有效证书/profile；未在个人设备重签安装验证 |
| HarmonyOS | ArkUI Stage，真实照片上传，HUKS 本机加密日记、账号和手动 E2EE 同步 | 华为 SDK Hvigor HAP 构建；市售机需华为设备签名 | Hvigor release 构建已成功、真实 unsigned HAP 已发布；市售机签名仍缺设备授权材料；系统历史健康数据适配未完成 |
| OpenHarmony/开鸿 | 独立目标与相同核心业务源码 | OpenHarmony API 12 release HAP 已真实构建并发布；仍需对应签名 | 不能拿开鸿包替代市售鸿蒙签名；系统历史健康数据适配和真机验证未完成 |
| 管理端/Worker | TypeScript 与共享 contracts，学校菜单/课程/汇总 | Web 静态包与 Worker bundle | 学校侧仍为演示身份，与私密同步账号分离 |

## 发布构建

本轮以源码推理发现并修复问题，用户随后授权 Luna 子代理进行测试：隔离后端测试 87/87 通过，类型检查与多端结构校验通过。CI 完成四平台实际编译打包；结构校验与编译成功不代表已完成 HAP/iOS 真机签名安装。

- `.github/workflows/ci.yml`：Web/Worker 类型检查与生产打包。
- `.github/workflows/device-release.yml`：Android APK、iOS Xcode device IPA 成功后发布真实资产。
- `.github/workflows/hap-build.yml`：从华为工具仓获取指定版本工具并校验下载 SHA-256，实际执行两个平台 Hvigor 构建。构建失败时不生成替代资产。
- `tools/build-hap.mjs`：支持 SDK 环境和隔离签名；签名 profile 需要授权设备与匹配 bundle。

详见 [device-install.md](device-install.md)。私密同步设计见 [e2ee-sync.md](e2ee-sync.md)。

## 已发布的实际产物

- [Android APK 与两种 iOS arm64 IPA（0.4.2）](https://github.com/welkin-moon/health2609/releases/tag/v0.4.2-device.bff9a0e)。IPA 分普通重签版与 HealthKit 版。
- [HarmonyOS / OpenHarmony unsigned release HAP（0.4.2）](https://github.com/welkin-moon/health2609/releases/tag/v0.4.2-hap.d431e6f)。[实际构建日志](https://github.com/welkin-moon/health2609/actions/runs/38038694974)确认两个平台 `BUILD SUCCESSFUL`。市售机签名及真机运行仍未验证。

后端同步修复、D1 mutation guard 迁移与 v2 认证元数据校验已部署。新写入使用 v2，保留历史 v1 读取。三端私密记录支持账户隔离、离线保存、完整同步确认、按字节分批及跨端活动汇总。后端测试覆盖 v1 兼容、v2 删除标记/来源设备/AAD 篡改拒绝与旧时间戳冲突排序。Android 已保留原安装覆盖升级；其他平台仍需对应签名材料和设备完成安装。

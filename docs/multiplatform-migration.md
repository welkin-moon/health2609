# 一餐一动多端迁移状态

## 目标

将 health2609 从单端 Demo 结构迁移为共享协议 + 多端原生客户端架构，并让跨端接口漂移能在 CI 中直接失败，而不是到真机阶段才发现。

## 已统一

- `packages/contracts` 作为跨端领域协议与校验边界。
- `packages/contracts/src/api.ts` 提供版本化 API manifest，覆盖学生端、管理端和同步端点。
- Android / iOS / HarmonyOS / OpenHarmony 均围绕同一 Worker API 与同一业务模型实现。
- 管理端与学生端都通过 Worker API 通信。
- 健康数据只上传业务所需聚合结果；原始运动轨迹不进入服务端。
- AI 餐食识别结果经过 schema 校验后才进入业务层。
- `tools/verify-multiplatform.mjs` 校验后端路由和各客户端关键端点是否一致。
- CI 对 Web/Worker、Android、iOS 以及 Harmony/OpenHarmony 结构分别验收。

## 迁移任务

- [x] 建立 apps 多端目录
- [x] 建立 packages/contracts 共享协议位置
- [x] 完成 API schema / endpoint manifest 定义
- [x] 完成 Android 主流程
- [x] 完成 iOS HealthKit 适配
- [x] 完成 HarmonyOS ArkUI 适配
- [x] 完成 OpenHarmony 自适应终端适配
- [x] 完成管理端统计面板
- [x] 完成 CI 多端构建/协议矩阵

## 各端当前职责

### Android
主学生端。覆盖校园餐、家庭餐、手动运动、Health Connect、设置与同步入口。

### iOS / iPadOS
SwiftUI 原生实现，使用 HealthKit 汇总步数、运动时长和活动能量，并在端侧排除在校时段。

### HarmonyOS NEXT
ArkTS / ArkUI Stage 模型实现，适配手机、平板和 2-in-1 形态，包含运动数据适配与弱网离线队列。

### OpenHarmony / 开鸿
面向校园终端与平板的 ArkUI 自适应实现，与 HarmonyOS 共享领域 DTO、API 语义和大屏交互结构。

### 管理端
Web 管理台继续维护课程时段、菜单、体育课记录和最小单元格隐私统计。

## CI 验收

- Web / Worker：TypeScript、Node 测试、跨端协议校验、管理台构建、Worker dry-run。
- Android：Gradle debug APK 构建 + Android API 合约校验。
- iOS：macOS runner 上执行 `swift build --package-path apps/ios` + API 合约校验。
- HarmonyOS / OpenHarmony：分别校验 ArkUI 主壳、DTO、网络层、设备类型与公共 API 覆盖。

Harmony/OpenHarmony 的正式 HAP 签名构建仍依赖 DevEco/OpenHarmony SDK 和签名环境；仓库 CI 当前把可在 GitHub runner 上稳定执行的跨端结构与协议检查作为门禁。

## 数据流

学生端 -> Worker API -> D1

图片餐食识别：学生端 -> Worker -> Tunnel -> AGY -> schema validation -> D1

个人跨设备数据：设备端加密 -> Worker sync API -> D1 ciphertext

## 与 E2EE 同步的边界

多端客户端迁移与账号/E2EE 同步是两层工作：本次迁移统一了客户端架构、API 边界和 CI；Issue #17 继续跟踪设备注册、密钥恢复/吊销和完整跨设备密文同步验收。

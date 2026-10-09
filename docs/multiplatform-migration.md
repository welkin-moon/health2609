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

## E2EE 多设备同步

Issue #17 的同步层已经并入本次迁移：

- [x] 账号注册 / 登录与学校 membership 解耦
- [x] Android local-first 私密 journal
- [x] AES-256-GCM versioned envelope + AAD
- [x] D1 仅保存密文、最小同步元数据与包装后的 key epoch
- [x] Bearer device token 身份认证，移除可伪造 user-id header
- [x] 新设备恢复短语 enrollment
- [x] 忘记密码后的 recovery + 全 epoch 重新包装
- [x] 设备列表、吊销和未来记录 key rotation
- [x] cursor 增量 pull 与确定性冲突顺序
- [x] Android Keystore 保护本机 token / epoch key / 待同步 journal
- [x] 清理旧版明文 passkey 缓存
- [x] AES-GCM 篡改、协议版本、冲突和吊销安全测试
- [x] 密钥层级、威胁模型、不可恢复场景文档

完整设计见 docs/e2ee-sync.md。Harmony/OpenHarmony/iOS 继续共享同一 sync API contract；Issue #17 要求的工程验收以 Android 本地模型为主实现。

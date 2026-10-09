# 一餐一动多端迁移状态

## 目标

将 health2609 从单端 Demo 结构迁移为共享领域模型 + 多端客户端架构。

## 已统一

- API contract 作为跨端边界
- Android / iOS / HarmonyOS / OpenHarmony 使用同一领域模型
- 管理端与学生端通过 Worker API 通信
- 健康数据只上传聚合结果
- AI 餐食识别结果必须经过 schema 校验后进入业务层

## 迁移任务

- [x] 建立 apps 多端目录
- [x] 建立 packages/contracts 共享协议位置
- [ ] 完成 API schema 定义
- [ ] 完成 Android 主流程
- [ ] 完成 iOS HealthKit 适配
- [ ] 完成 HarmonyOS ArkUI 适配
- [ ] 完成 OpenHarmony 自适应终端适配
- [ ] 完成管理端统计面板
- [ ] 完成 CI 多端构建矩阵

## 数据流

学生端 -> Worker API -> D1

图片餐食识别：学生端 -> Worker -> Tunnel -> AGY -> schema validation -> D1

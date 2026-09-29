# 青衡（health2609）

「青衡」是面向中学生的校园健康记录 Demo：把学校已知的食堂菜单、体育课安排与实际活动时间，与学生在校外的手机运动数据和家庭餐记录合并，形成当天的饮食、运动与建议视图。

## 核心边界

- 校内饮食：管理员提前录入菜单和标准份；学生只选择自己吃了哪些、吃了多少。
- 校内体育：按管理员维护的课程安排，并由管理员录入该节课实际活动分钟数统计。
- 校外运动：Android 端通过 Health Connect 读取运动健康数据，在本地排除在校时段后只上传每日聚合；同时支持学生手动补录运动类型、时长和强度。
- 家庭餐：学生可拍照或手动记录。图片识别由经提示词约束的常驻 AGY CLI 处理；AGY 预读任务要求，收到新任务后触发子 agent 输出结构化候选结果，仓库内不再单独维护模型服务。
- 管理端：Cloudflare Pages + Vite/React，使用自定义域名作为比赛入口，不依赖 `pages.dev`。
- 后端：Cloudflare Workers + D1，使用自定义域名作为 API 入口，不依赖 `workers.dev`；家庭餐图片不落对象存储，Worker 仅校验后经 Cloudflare Tunnel/AGY 入口触发分析任务。目标是免费层即可跑比赛 Demo。
- Android：Kotlin + Jetpack Compose + Material 3 Expressive (M3E)，固定竖屏，主导航使用底部浮动 dock；前后端和领域层解耦。

## 仓库结构

```text
apps/
  android/       Android 主作品
  admin-web/     学校管理员 Pages 前端
services/
  api/           Cloudflare Worker API
packages/
  contracts/     跨端 API schema / 类型
docs/            架构、产品与比赛技术文档
prompts/         AGY 结构化处理提示词
```

## 开发原则

1. 先完成真实主流程，再补比赛包装。
2. UI 使用 Material 3 Expressive (M3E)；不做 WebView 套壳 APK。
3. API、领域模型和持久化层分离；Android 采用 feature + clean-ish 分层。
4. 学校数据按 school tenant 隔离，管理员有统计能力。
5. AI 只负责家庭餐的结构化识别与解释，营养汇总和运动分钟计算由确定性代码完成。
6. Demo 数据只保留够演示的程度，不为“演示模式”堆额外复杂度。

详细计划见 [PLAN.md](PLAN.md)，架构见 [docs/architecture.md](docs/architecture.md)。比赛提交入口见 [docs/submission.md](docs/submission.md)，APK 构建见 [docs/release.md](docs/release.md)，AGY 常驻 CLI 接口见 [docs/agy-runner.md](docs/agy-runner.md)。

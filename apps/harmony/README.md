# health2609 ("一餐一动") - HarmonyOS NEXT 原生客户端

本工程是 health2609 ("一餐一动") 项目的原生 **HarmonyOS NEXT (鸿蒙 NEXT, API 12 / 5.0 Release)** 客户端，使用 **ArkTS & ArkUI Stage 模型** 开发，严格遵循与 Android 和 Web 端一致的 **Material 3 Expressive (M3E)** 设计系统。

---

## 📱 核心功能特性

1. **统一 Material 3 Expressive (M3E) 设计语言**
   - **M3E 调色板**：`primary`, `onPrimary`, `primaryContainer`, `secondaryContainer`, `surface`, `surfaceContainerHigh`, `tertiaryContainer` 等语义化色阶。
   - **M3E 形状系统**：卡片大圆角 `borderRadius(32)`、内容区块 `borderRadius(24)`、悬浮胶囊底部栏 `borderRadius(34)`、内部选中胶囊 `borderRadius(28)`。
   - **柔和弹性动效**：基于 ArkUI 弹簧过渡曲线与平滑尺寸缩放动效。

2. **午餐记录 (校内菜单协同)**
   - 自动获取学校今日发布的标准菜单（菜品名称、标准克数、蛋白质、脂肪、碳水化合物及能量数值）。
   - 快速分量选择芯片（没吃 0.0、¼份 0.25、半份 0.5、¾份 0.75、1份 1.0），支持展开精确克数微调。
   - 动态热量与宏量营养素实时预览与“保存午餐记录”。

3. **身体活动与运动整合 (校内外边界隔离)**
   - **校内体育**：展示学校课表与体育老师核验确认的实际活动时间，避免手机推测体育课。
   - **校外运动**：利用鸿蒙 `@ohos.sensor` 计步器与运动传感器，自动排除在校时段，按天汇总并上传校外运动。
   - **手动补记**：支持跑步、跳绳、羽毛球、篮球、自主运动等预设项目，自由设定时长与强度。

4. **家庭餐 AI 智能识别**
   - 支持拍照识别与相册选取。
   - 与 Cloudflare API / AGY AI 智能引擎对接，自动识别食物名称、估算分量并计算营养数据。
   - 坚持“用户确认原则”：识别结果需由学生核对克数与名称后方可记入当天汇总，不保留原始照片，严密保护隐私。

5. **今日汇总全景与行动建议**
   - 卡路里摄入仪表盘与教育部 120 分钟运动达标进度条。
   - 三大宏量营养素（蛋白质、脂肪、碳水化合物）分解与比例分析。
   - 基于循证营养与运动指南的“下一步建议”动态提示。
   - 每日参考能量（500 ~ 6000 kcal）自适应调节。

6. **隐私与边界设计**
   - 独立“隐私”页清晰呈现数据流转边界。
   - 阐释管理端 k-匿名抑制机制（k ≥ 3）与鸿蒙端侧沙箱防护。

---

## 📂 目录与架构规范

```text
apps/harmony
├── AppScope
│   ├── app.json5                          # 应用包名、版本及元数据配置
│   └── resources/base/element/string.json # 全局文本资源 (app_name: "一餐一动")
├── entry
│   ├── build-profile.json5                # entry 模块构建目标与 ArkTS 编译选项
│   ├── oh-package.json5                   # 鸿蒙包依赖配置
│   └── src/main
│       ├── module.json5                   # Stage 模块配置与网络/定位/运动权限声明
│       ├── resources                      # 国际化字符串、M3E 颜色及主页面路由表
│       └── ets
│           ├── entryability
│           │   └── EntryAbility.ets       # UIAbility 窗口生命周期与全屏沉浸式配置
│           ├── theme
│           │   └── M3ETheme.ets           # M3E 颜色色卡、圆角形状、动效常量
│           ├── model
│           │   └── ApiModels.ets          # 对齐 @health2609/contracts 的数据实体
│           ├── service
│           │   ├── HealthApi.ets          # @ohos.net.http 封装网络通信与 AI 分析接口
│           │   └── MotionService.ets      # @ohos.sensor 计步与校外运动窗口排除算法
│           ├── view
│           │   ├── StudentAppShell.ets    # 根布局与 M3E 悬浮底部胶囊导航
│           │   ├── TodayPage.ets          # “今天”页 (午餐、运动、家庭餐 AI、今日汇总)
│           │   └── PrivacyPage.ets        # “隐私”说明页
│           └── pages
│               └── Index.ets              # 应用入口 Page
├── build-profile.json5                    # 工程根级编译配置文件 (API 12)
├── oh-package.json5                       # 工程根级依赖
├── hvigor
│   └── hvigor-config.json5                # Hvigor 构建引擎配置
└── README.md                              # 本说明文档
```

---

## 🛠️ 开发与构建指引

### 1. 环境准备
- **DevEco Studio**：DevEco Studio 5.0 Release (Build Version 5.0.3.xxx 或以上)。
- **HarmonyOS SDK**：API Version 12 (Target: 5.0.0(12))。
- **Node.js**：v18 或 v20 LTS。
- **Hvigor**：Hvigor 5.0.0 或以上。

### 2. 打开工程
1. 启动 DevEco Studio。
2. 选择 **File -> Open...**，定位至 `apps/harmony` 目录。
3. 等待 DevEco Studio 自动同步 `oh-package.json5` 依赖及构建模型。

### 3. 配置签名与运行
1. 在 DevEco Studio 中选择 **File -> Project Structure -> Project -> Signing Configs**。
2. 勾选 **Automatically generate signature** 或配置调试证书。
3. 连接 HarmonyOS NEXT 真机或启动 HarmonyOS NEXT 模拟器。
4. 点击右上角 **Run 'entry'** (或快捷键 `Shift + F10`) 启动运行。

### 4. 命令行构建
如需使用 CLI 编译 HAP 安装包：
```bash
cd apps/harmony
hvigorw --mode module -p module=entry@default -p product=default assembleHap
```
生成的 HAP 文件位于 `entry/build/default/outputs/default/entry-default-unsigned.hap`。

---

## 🌐 接口与测试说明

客户端预置直连官方演示 API：
- **Base URL**: `https://h2609.lunarlab.uk`
- **默认请求头**：
  - `x-demo-school`: `demo-school`
  - `x-demo-participant`: `demo-student`
  - `x-demo-role`: `student`

当处于无网络或模拟器离线状态时，`HealthApi` 与 `MotionService` 会自动激活高仿真本地演示数据模式，确保午餐选择、运动补记、家庭餐识别体验顺畅无阻。

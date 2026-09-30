# health2609 OpenHarmony / 开鸿 (KaihongOS) 原生客户端

本项目为健康校园“一餐一动”（health2609）专为 **OpenHarmony 4.1/5.0 Release 标准系统** 及 **开鸿 (KaihongOS) 智慧校园终端 / 智能学生平板** 打造的原生客户端应用。

应用在交互和视觉上严格遵循全平台统一的 **Material 3 Expressive (M3E)** 设计系统，全面支持大中小屏自适应网格，针对校园弱网及离线环境提供完整的端侧安全沙箱与离线缓存恢复机制。

---

## 一、核心特性与设计规范

### 1. Material 3 Expressive (M3E) 统一设计语言
- **调色系统 (Color Tokens)**：
  - `primary`: `#006A6A`（富有活力的深水鸭青色，兼顾校园阳光高对比度）
  - `primaryContainer`: `#70F7E7` / `onPrimaryContainer`: `#002020`
  - `secondaryContainer`: `#CCE8E7` / `onSecondaryContainer`: `#051F1F`
  - `tertiaryContainer`: `#D3E4FF` / `onTertiaryContainer`: `#041C35`
  - `surfaceContainerLow`: `#F2F5F4` / `surfaceContainerHigh`: `#E7EAEA`
  - `outlineVariant`: `#BEC9C8` / `error`: `#BA1A1A`
- **M3E 形状与圆角规范 (Shapes)**：
  - 卡片外壳：`borderRadius(32)`
  - 内部功能分区：`borderRadius(24)`
  - 悬浮胶囊底栏 (Floating Capsule Dock)：`borderRadius(34)`
  - 选中项内嵌药丸：`borderRadius(28)`
  - 份量与分类标签 (Chips)：`borderRadius(18)`
  - 按钮与输入框：`borderRadius(16)`
- **高对比度与无障碍支持**：
  - 专为智慧校园食堂光线及操场户外阳光场景优化的文字层级与边框对比度，字体大小及行高遵循适老化与少儿清晰读写规范。

### 2. 响应式自适应布局 (Responsive & Adaptive)
- 基于 ArkUI `GridRow`、`GridCol` 与 `BreakpointType`：
  - **手持设备 (Handheld, `sm` < 600vp)**：单列顺畅流式排版，底部常驻 M3E 悬浮胶囊底栏；
  - **平板 / 智慧终端 (Tablet / Smart Terminal, `md` 600-840vp & `lg` ≥ 840vp)**：双列协同工作台，左侧聚合“午餐记录”与“家庭餐 AI”，右侧联动“今日汇总反馈”与“运动统计”，在大屏上实现高信息密度与舒适交互。

### 3. 功能模块架构
1. **今日午餐 (Lunch Recording)**：
   - 联动校园后勤系统的今日菜品清单；
   - 阶梯分量选择器（0 没吃、¼ 份、半份、¾ 份、1 份）；
   - 精确克数折叠输入框（满足对克数有精细要求的校医与学生）；
   - 动态实时热量（kcal）、蛋白质、脂肪、碳水化合物合计预览；
   - 提交午餐记录并即时更新今日营养大盘。
2. **校园运动 (Physical Activity)**：
   - 校内体育（依据学校课表与教师确认的实际活动时间，不拿传感器数据盲猜）；
   - 校外运动终端汇总（自动排除在校时段，仅上传当天有效运动时长）；
   - 补记运动面板（跑步、跳绳、羽毛球、篮球、自主运动），支持时长滑块与三档运动强度（轻松/中等/较累）。
3. **家庭餐 AI (Home Meal AI Recognition)**：
   - 覆盖早餐、午餐、晚餐三种就餐场景；
   - 拍摄分析与校园图库样本快速导入；
   - 开鸿端侧多模态视觉估算食物品类与分量克数；
   - 可编辑的食物识别结果清单，秉持“学生最终确认后才记入”的安全准则。
4. **今日汇总与循证反馈 (Today Overview & Evidence-Based Feedback)**：
   - 摄入热量与运动分钟双协同计量表；
   - 三大宏量营养素（蛋白质、脂肪、碳水化合物）克数与配比条；
   - 每日 120 分钟运动目标线性进度条；
   - 每日能量参考目标（默认 2000 kcal）快捷编辑；
   - **下一步建议 (Next Action Tips)**：根据当前能量差与运动缺口，动态生成科学循证的健康活动与营养摄入建议。
5. **隐私与边界屏障 (Privacy View)**：
   - 严格落实数据最小化原则；
   - 班级营养统计 $k \ge 3$ 差分隐私抑制保护，杜绝反推单个学生膳食；
   - 照片仅用于食物识别，端侧不长期存储人脸或原始影像。
6. **离线高可用与断网自愈 (Offline Resilience)**：
   - 基于 `@ohos.data.preferences` 的本地持久化与离线任务队列；
   - 操场或地下食堂断网时记录自动入队暂存，网络恢复后无缝增量回放。

---

## 二、项目代码结构

```text
apps/openharmony/
├── AppScope/
│   ├── app.json5                        # 应用基础配置 (bundleName, app_icon, label)
│   └── resources/base/element/
│       └── string.json                  # 全局应用名 ("一餐一动")
├── entry/
│   ├── build-profile.json5              # 模块编译配置 (stageMode)
│   ├── hvigorfile.ts                    # 模块任务配置
│   ├── package.json                     # 模块依赖
│   └── src/main/
│       ├── module.json5                 # Ability 与系统权限配置 (INTERNET, BACKGROUND)
│       ├── resources/
│       │   └── base/
│       │       ├── element/
│       │       │   ├── color.json       # M3E 颜色资源表
│       │       │   └── string.json      # 模块字符串资源
│       │       ├── media/
│       │       │   └── icon.png         # 应用图标
│       │       └── profile/
│       │           └── main_pages.json  # 路由主页面清单
│       └── ets/
│           ├── entryability/
│           │   └── EntryAbility.ets     # 应用生命周期主入口
│           ├── model/
│           │   └── ApiModels.ets        # 与 @health2609/contracts 对齐的 DTO 与计算逻辑
│           ├── service/
│           │   ├── HealthApi.ets        # 基于 @ohos.net.http 的网络层及离线容灾回退
│           │   └── OfflineCacheService.ets # @ohos.data.preferences 离线持久化与任务队列
│           ├── theme/
│           │   └── M3ETheme.ets         # Material 3 Expressive 颜色、圆角、排版及断点系统
│           ├── view/
│           │   ├── AdaptiveStudentAppShell.ets # M3E 自适应多端外壳与悬浮胶囊底栏
│           │   ├── TodayView.ets        # M3E 今日工作台 (午餐/家庭餐/运动/汇总/下一步建议)
│           │   └── PrivacyView.ets      # 隐私屏障、K-匿名与安全边界说明页
│           └── pages/
│               └── Index.ets            # 根页面
├── build-profile.json5                  # 工程级构建配置
├── hvigorfile.ts                        # 工程级构建入口
├── package.json                         # 工程级依赖
└── README.md                            # 本文档
```

---

## 三、开发与编译构建指南

### 1. 环境准备
- **IDE**：推荐使用 **DevEco Studio 4.1 Release** 或 **DevEco Studio 5.0 Release** (兼容开鸿 KaihongOS IDE 开发者套件)。
- **SDK 版本**：OpenHarmony SDK API 10 (Full SDK) / API 11 / API 12。
- **构建工具**：Hvigor 3.1.2+。

### 2. 命令行构建
进入 `apps/openharmony` 目录：
```bash
# 安装构建插件依赖 (如首次构建)
npm install

# 使用 hvigorw 编译生成 HAP 包
./hvigorw assembleHap --mode module -p product=default -p module=entry@default
```
编译成功后，产物位于：
`entry/build/default/outputs/default/entry-default-unsigned.hap`

### 3. 安装与运行调试 (HDC)
连接已开启开发者模式的 OpenHarmony 设备或开鸿智能教育平板：
```bash
# 查看已连接设备
hdc list targets

# 安装编译产物
hdc app install entry/build/default/outputs/default/entry-default-signed.hap

# 启动一餐一动应用
hdc shell aa start -a EntryAbility -b org.openharmony.health2609
```

### 4. 离线演示与云端配置
- 默认云端服务地址配置为：`https://h2609.lunarlab.uk`
- 租户请求头自动携带标准 Demo 身份：
  - `x-demo-school`: `demo-school`
  - `x-demo-participant`: `demo-student`
  - `x-demo-role`: `student`
- 在完全离线或受限校园局域网环境下，应用内置安全离线回退机制，保障菜谱展示、分量计算、运动补录和离线缓存功能均可流畅运行。

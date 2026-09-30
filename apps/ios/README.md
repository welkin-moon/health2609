# Health2609 ("一餐一动") - Native iOS Client

A modern native iOS application built with **Swift & SwiftUI** for iOS 17+ and iOS 18+, strictly implementing the **Material 3 Expressive (M3E)** design system unified across Android, Web, and iOS platforms.

---

## 核心特性

1. **Unified Material 3 Expressive (M3E) Design Language**
   - **Color System**: Dynamic tonal palettes (`primary`, `primaryContainer`, `secondaryContainer`, `surfaceContainerLow`, `surfaceContainerHigh`, `surfaceContainerHighest`, etc.) supporting both Light and Dark appearances.
   - **Expressive Shapes**: Continuous curve rounded rectangles (`cornerRadius: 32` for primary cards, `cornerRadius: 24` for internal sections, `cornerRadius: 34` for floating bottom dock capsule, `cornerRadius: 28` for inner dock pill).
   - **Motion Scheme**: Expressive spring physics (`MotionScheme.expressive()`) providing smooth fluid transitions.
   - **Floating Dock Navigation**: Floating bottom pill capsule dock for fast switching between "今天" (Today Dashboard) and "隐私" (Privacy & Boundaries).

2. **School Lunch Portion Logging ("午餐")**
   - Live synchronization with school cafeteria daily menus.
   - Fast portion selection chips (`0`, `¼份`, `半份`, `¾份`, `1份`).
   - Optional exact grams input mode (`需要时精确到克`).
   - Deterministic nutrient recalculation (`NutritionMath.calculateServingNutrition`) for kcal, protein, fat, and carbohydrates.

3. **Physical Activity & Apple HealthKit Sync ("运动")**
   - **Deterministic Separation**: School PE is recorded by the school administration, while outside-school activity is retrieved from personal devices.
   - **School-Time Exclusion Calculation**: Local mathematical inversion of school day windows (`[dayStart, school.start]`, `[school.end, dayEnd]`) ensuring during-school intervals are excluded from outside-school activity metrics.
   - **HealthKit Integration**: Seamlessly queries steps, active energy (kcal), and exercise minutes using Apple HealthKit.
   - **Manual Activity Logging**: Interactive duration slider (5–180 min), preset chips (跑步, 跳绳, 羽毛球, 篮球, 自主运动), and intensity levels (轻松, 中等, 较累).

4. **Home Meal AI Visual Recognition ("家庭餐")**
   - PhotosPicker and camera integration for breakfast, lunch, and dinner.
   - Async multipart upload directly to the Cloudflare API endpoint (`/v1/home-meals/analyze`).
   - Editable food names and estimated grams with real-time scaled nutrition preview.
   - Strict student confirmation before committing to daily records.

5. **Today Overview & Evidence-Based Next Action ("今日汇总")**
   - Real-time energy balance and progress towards the 120-minute daily physical activity target.
   - Macronutrient breakdown (protein, fat, carbs) with visual indicators.
   - Evidence-based next action tips ("下一步建议") conforming to Chinese school-age children dietary & activity guidelines.

6. **Privacy Sandbox & Local Processing ("隐私")**
   - Clear boundary visualization for student data.
   - Local on-device filtering without uploading GPS trajectories or raw meal images.

---

## Architecture & Directory Structure

```
apps/ios/
├── Package.swift                             # Swift Package Manager definition
├── README.md                                 # Documentation & build instructions
└── Sources/
    └── Health2609/
        ├── App/
        │   └── Health2609App.swift           # Application entrypoint (@main)
        ├── Theme/
        │   └── M3ETheme.swift                # M3E Color tokens, shapes, motion curves & modifiers
        ├── Models/
        │   └── ApiModels.swift               # Codable DTOs matching @health2609/contracts & deterministic math
        ├── Network/
        │   └── HealthApi.swift               # Async/await URLSession API client with demo school headers
        ├── Health/
        │   └── HealthKitManager.swift        # HealthKit bridge with outside-school time exclusion algorithm
        ├── ViewModels/
        │   └── TodayViewModel.swift          # Main view model handling state, portion math, sync & tips
        └── Views/
            ├── StudentAppShell.swift         # Root container with M3E floating bottom capsule dock
            ├── TodayView.swift               # Dashboard: TopBar, Lunch, Activity, HomeMeal AI, Overview
            └── PrivacyView.swift             # Privacy & data boundary documentation screen
```

---

## Prerequisites & Build Instructions

### Requirements
- **macOS Sonoma (14.0+)** or later
- **Xcode 15.0+** (Swift 5.9+) or **Xcode 16.0+** (Swift 6.0)
- **Target OS**: iOS 17.0+ / iPadOS 17.0+ / macOS 14.0+ (Mac Catalyst)

### Build via Swift Package Manager CLI
```bash
cd apps/ios
swift build
```

### Open in Xcode
You can open `Package.swift` directly in Xcode:
```bash
cd apps/ios
open Package.swift
```
Select the **Health2609** scheme and target any iOS Simulator (e.g. iPhone 15 Pro, iPhone 16) or physical iOS device, then press `Cmd + R` to build and run.

### Entitlements & Permissions
When bundling as a full `.ipa` or embedding into an Xcode application project, include the following keys in your `Info.plist`:
- `NSHealthShareUsageDescription`: "用于同步校外运动时长与卡路里消耗，并结合校内体育计算当日总运动量。"
- `NSHealthUpdateUsageDescription`: "用于记录经过校对的活动数据。"
- `NSCameraUsageDescription`: "用于拍摄家庭餐食照片以便通过智能识别估算营养成分。"
- `NSPhotoLibraryUsageDescription`: "用于从相册中选择家庭餐食照片进行营养识别。"

---

## API & Backend Contract

The iOS client communicates with the Cloudflare Worker API at `https://h2609.lunarlab.uk` with standard demo identity headers:
- `x-demo-school: demo-school`
- `x-demo-participant: demo-student`
- `x-demo-role: student`

Data structures strictly match `@health2609/contracts`.

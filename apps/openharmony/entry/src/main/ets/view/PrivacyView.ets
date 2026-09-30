/**
 * PrivacyView - Privacy & Local Data Protection Screen
 * Implements K-anonymity safeguards, data minimization & local trust boundaries
 */

import { M3EColors, M3EShapes, M3ETypography } from '../theme/M3ETheme';

@Component
export struct PrivacyView {
  build() {
    Scroll() {
      Column({ space: 20 }) {
        // Header Section
        Column({ space: 8 }) {
          Text('你的数据，边界很清楚')
            .fontSize(M3ETypography.headlineMedium)
            .fontWeight(FontWeight.Bold)
            .fontColor(M3EColors.onSurface)

          Text('学校、终端和你本人，只提供完成当天记录真正需要的那一部分。')
            .fontSize(M3ETypography.bodyLarge)
            .fontColor(M3EColors.onSurfaceVariant)
            .lineHeight(22)
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        .padding({ top: 12, bottom: 8 })

        // Section 1: School PE
        this.PrivacyCard(
          '校内体育由学校记录',
          '只用课程安排和老师确认的实际活动时间，不拿终端数据猜体育课。',
          '课间走动不会被误算成体育课。',
          '校'
        )

        // Section 2: Terminal Outside Activity
        this.PrivacyCard(
          '终端只补校外运动',
          '先在终端上排除学校设置的在校时段，再汇总当天运动。',
          '上传的是当天汇总，不是运动轨迹。',
          '动'
        )

        // Section 3: Meal Photo Confirmation
        this.PrivacyCard(
          '餐食照片由你确认',
          '照片先用来识别食物，名称和分量都可以改，确认后才会记进当天。',
          '保存确认后的餐食结果，不保存原始照片。',
          '餐'
        )

        // Section 4: K-Anonymity
        this.PrivacyCard(
          '班级隐私与 K-匿名保障 (k ≥ 3)',
          '学校管理端对班级营养指标汇总设置最小群组阈值，少于 3 人时抑制显示。',
          '严格防止根据平均值反向推算个别学生的具体饮食与营养。',
          '隐'
        )

        // Section 5: KaihongOS Local Isolation
        this.PrivacyCard(
          '开鸿系统本地安全沙箱',
          '离线缓存与待同步记录存储于终端独立沙箱中，断网环境下依然保障体验。',
          '网络恢复后单向增量同步，不收集定位或传感器多余数据。',
          '安'
        )
      }
      .width('100%')
      .padding({ left: 20, right: 20, top: 16, bottom: 120 })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
  }

  @Builder
  PrivacyCard(title: string, body: string, detail: string, badgeChar: string) {
    Row({ space: 14 }) {
      // Icon / Character Capsule
      Column() {
        Text(badgeChar)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
          .fontColor(M3EColors.onSecondaryContainer)
      }
      .width(44)
      .height(44)
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .backgroundColor(M3EColors.secondaryContainer)
      .borderRadius(M3EShapes.chipRadius)

      // Content Column
      Column({ space: 5 }) {
        Text(title)
          .fontSize(M3ETypography.titleMedium)
          .fontWeight(FontWeight.SemiBold)
          .fontColor(M3EColors.onSurface)

        Text(body)
          .fontSize(M3ETypography.bodyMedium)
          .fontColor(M3EColors.onSurface)
          .lineHeight(20)

        Text(detail)
          .fontSize(M3ETypography.bodySmall)
          .fontColor(M3EColors.onSurfaceVariant)
          .lineHeight(16)
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .padding(16)
    .backgroundColor(M3EColors.surfaceContainerLow)
    .borderRadius(M3EShapes.innerSectionRadius)
    .alignItems(VerticalAlign.Top)
  }
}
